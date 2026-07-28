// Package packetjamtun is the Go Mobile boundary for PacketJam's gVisor
// userspace network stack.
package packetjamtun

import (
	"container/heap"
	"encoding/json"
	"errors"
	"io"
	"math/rand"
	"os"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device/iobased"
	"github.com/xjasonlyu/tun2socks/v2/proxy"
	"github.com/xjasonlyu/tun2socks/v2/tunnel"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// Listener is implemented by the Android adapter. Calls are made from Go
// threads; gomobile marshals them onto the JVM.
type Listener interface {
	OnStats(uploadBytes, downloadBytes, uploadBps, downloadBps int64,
		delayed, dropped, duplicated, corrupted, reordered, queueOverflow int64)
	OnError(message string)
}

type limits struct {
	RateKbps         int     `json:"rateKbps"`
	LossPercent      float64 `json:"lossPercent"`
	DuplicatePercent float64 `json:"duplicatePercent"`
	CorruptPercent   float64 `json:"corruptPercent"`
	ReorderPercent   float64 `json:"reorderPercent"`
}

type burstSchedule struct {
	ImpairedSeconds int `json:"impairedSeconds"`
	HealthySeconds  int `json:"healthySeconds"`
}

type profile struct {
	LatencyMs    int            `json:"latencyMs"`
	JitterMs     int            `json:"jitterMs"`
	QueuePackets int            `json:"queuePackets"`
	Offline      bool           `json:"offline"`
	Upload       limits         `json:"upload"`
	Download     limits         `json:"download"`
	Burst        *burstSchedule `json:"burst,omitempty"`
}

type counters struct {
	uploadBytes, downloadBytes                         atomic.Int64
	delayed, dropped, duplicated, corrupted, reordered atomic.Int64
	queueOverflow                                      atomic.Int64
}

// Engine owns the duplicated TUN descriptor, schedulers, and gVisor stack.
type Engine struct {
	file      *os.File
	endpoint  *iobased.Endpoint
	netstack  *stack.Stack
	profile   atomic.Pointer[profile]
	startedAt time.Time
	stats     counters
	listener  Listener
	stop      chan struct{}
	once      sync.Once
	wg        sync.WaitGroup
}

// Start duplicates tunFD through /proc, starts the two directional packet
// schedulers, and attaches gVisor. The caller retains ownership of tunFD.
func Start(tunFD int64, profileJSON string, seed int64, listener Listener) (*Engine, error) {
	if tunFD < 0 {
		return nil, errors.New("invalid TUN file descriptor")
	}
	p, err := decodeProfile(profileJSON)
	if err != nil {
		return nil, err
	}
	// Reopening /proc/self/fd is denied by SELinux on some Android builds.
	// dup(2) creates the owned descriptor required by the engine without
	// traversing procfs.
	duplicatedFD, err := syscall.Dup(int(tunFD))
	if err != nil {
		return nil, err
	}
	f := os.NewFile(uintptr(duplicatedFD), "packetjam-tun")
	if f == nil {
		_ = syscall.Close(duplicatedFD)
		return nil, errors.New("could not own duplicated TUN descriptor")
	}
	e := &Engine{
		file: f, listener: listener, stop: make(chan struct{}), startedAt: time.Now(),
	}
	e.profile.Store(p)

	upload := newScheduler(e, true, seed)
	download := newScheduler(e, false, seed^0x5deece66d)
	rw := &scheduledTUN{file: f, upload: upload, download: download, stop: e.stop}
	endpoint, err := iobased.New(rw, 1500, 0)
	if err != nil {
		f.Close()
		return nil, err
	}
	e.endpoint = endpoint

	// Direct sockets originate in PacketJam's excluded app process, so Android
	// routes them on the underlying network rather than recursively into TUN.
	tunnel.T().SetDialer(proxy.NewDirect())
	s, err := core.CreateStack(&core.Config{
		LinkEndpoint:     &packetJamDevice{Endpoint: endpoint},
		TransportHandler: tunnel.T(),
	})
	if err != nil {
		f.Close()
		return nil, err
	}
	e.netstack = s
	e.wg.Add(3)
	go func() { defer e.wg.Done(); upload.run() }()
	go func() { defer e.wg.Done(); download.run() }()
	go func() { defer e.wg.Done(); e.reportStats() }()
	return e, nil
}

func (e *Engine) UpdateProfile(profileJSON string) error {
	p, err := decodeProfile(profileJSON)
	if err == nil {
		e.profile.Store(p)
	}
	return err
}

func (e *Engine) Close() {
	e.once.Do(func() {
		close(e.stop)
		_ = e.file.Close()
		if e.netstack != nil {
			e.netstack.Close()
			e.netstack.Wait()
		}
		e.wg.Wait()
	})
}

func decodeProfile(raw string) (*profile, error) {
	var p profile
	if err := json.Unmarshal([]byte(raw), &p); err != nil {
		return nil, err
	}
	if p.QueuePackets < 1 {
		p.QueuePackets = 1
	}
	if p.Burst != nil && (p.Burst.ImpairedSeconds < 1 || p.Burst.HealthySeconds < 1) {
		return nil, errors.New("burst windows must be at least one second")
	}
	return &p, nil
}

func (e *Engine) reportStats() {
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	var lastUp, lastDown int64
	for {
		select {
		case <-ticker.C:
			up, down := e.stats.uploadBytes.Load(), e.stats.downloadBytes.Load()
			if e.listener != nil {
				e.listener.OnStats(up, down, (up-lastUp)*4, (down-lastDown)*4,
					e.stats.delayed.Load(), e.stats.dropped.Load(),
					e.stats.duplicated.Load(), e.stats.corrupted.Load(),
					e.stats.reordered.Load(), e.stats.queueOverflow.Load())
			}
			lastUp, lastDown = up, down
		case <-e.stop:
			return
		}
	}
}

type packetJamDevice struct{ *iobased.Endpoint }

func (*packetJamDevice) Name() string { return "packetjam" }
func (*packetJamDevice) Type() string { return "fd" }

type scheduledTUN struct {
	file             *os.File
	upload, download *scheduler
	stop             <-chan struct{}
}

func (t *scheduledTUN) Read(p []byte) (int, error) {
	select {
	case b := <-t.upload.output:
		return copy(p, b), nil
	case <-t.stop:
		return 0, io.EOF
	}
}

func (t *scheduledTUN) Write(p []byte) (int, error) {
	b := append([]byte(nil), p...)
	if !t.download.submit(b) {
		return len(p), nil
	}
	return len(p), nil
}

type queuedPacket struct {
	bytes   []byte
	release time.Time
	order   uint64
}
type packetHeap []*queuedPacket

func (h packetHeap) Len() int { return len(h) }
func (h packetHeap) Less(i, j int) bool {
	if h[i].release.Equal(h[j].release) {
		return h[i].order < h[j].order
	}
	return h[i].release.Before(h[j].release)
}
func (h packetHeap) Swap(i, j int) { h[i], h[j] = h[j], h[i] }
func (h *packetHeap) Push(x any)   { *h = append(*h, x.(*queuedPacket)) }
func (h *packetHeap) Pop() any {
	old := *h
	n := len(old)
	x := old[n-1]
	*h = old[:n-1]
	return x
}

type scheduler struct {
	engine    *Engine
	upload    bool
	random    *rand.Rand
	now       func() time.Time
	input     chan []byte
	output    chan []byte
	queue     packetHeap
	available time.Time
	order     uint64
}

func newScheduler(e *Engine, upload bool, seed int64) *scheduler {
	return &scheduler{
		engine: e, upload: upload, random: rand.New(rand.NewSource(seed)),
		now:   time.Now,
		input: make(chan []byte, 4096), output: make(chan []byte, 4096),
	}
}

func (s *scheduler) submit(b []byte) bool {
	select {
	case s.input <- b:
		return true
	default:
		s.engine.stats.queueOverflow.Add(1)
		s.engine.stats.dropped.Add(1)
		return false
	}
}

func (s *scheduler) run() {
	if s.upload {
		go s.readTUN()
	} else {
		go s.writeTUN()
	}
	timer := time.NewTimer(time.Hour)
	stopTimer(timer)
	for {
		var due <-chan time.Time
		if len(s.queue) > 0 {
			delay := time.Until(s.queue[0].release)
			if delay < 0 {
				delay = 0
			}
			timer.Reset(delay)
			due = timer.C
		}
		select {
		case b := <-s.input:
			stopTimer(timer)
			s.impair(b)
		case <-due:
			s.releaseDue()
		case <-s.engine.stop:
			stopTimer(timer)
			return
		}
	}
}

func (s *scheduler) readTUN() {
	buf := make([]byte, 65535)
	for {
		n, err := s.engine.file.Read(buf)
		if err != nil {
			return
		}
		if n > 0 && !s.submit(append([]byte(nil), buf[:n]...)) {
			continue
		}
	}
}

func (s *scheduler) writeTUN() {
	for {
		select {
		case b := <-s.output:
			if _, err := s.engine.file.Write(b); err != nil {
				return
			}
		case <-s.engine.stop:
			return
		}
	}
}

func (s *scheduler) impair(b []byte) {
	s.impairAt(b, s.now())
}

func (s *scheduler) impairAt(b []byte, now time.Time) {
	p := s.engine.profile.Load()
	lim := p.Download
	if s.upload {
		lim = p.Upload
	}
	if p.Offline {
		s.engine.stats.dropped.Add(1)
		return
	}
	if len(s.queue) >= p.QueuePackets {
		s.engine.stats.queueOverflow.Add(1)
		s.engine.stats.dropped.Add(1)
		return
	}
	healthy := burstHealthy(p.Burst, s.engine.startedAt, now)
	if !healthy && s.chance(lim.LossPercent) {
		s.engine.stats.dropped.Add(1)
		return
	}
	if !healthy && len(b) > 0 && s.chance(lim.CorruptPercent) {
		i := s.random.Intn(len(b))
		b[i] ^= byte(1 << s.random.Intn(8))
		s.engine.stats.corrupted.Add(1)
	}
	release := now
	if !healthy {
		jitter := 0
		if p.JitterMs > 0 {
			jitter = s.random.Intn(p.JitterMs*2+1) - p.JitterMs
		}
		delayMs := p.LatencyMs + jitter
		if delayMs < 0 {
			delayMs = 0
		}
		release = now.Add(time.Duration(delayMs) * time.Millisecond)
	}
	if lim.RateKbps > 0 {
		if release.Before(s.available) {
			release = s.available
		}
		release = release.Add(time.Duration(len(b)*8*1_000_000/lim.RateKbps) * time.Nanosecond)
		s.available = release
	}
	if !healthy && s.chance(lim.ReorderPercent) {
		release = release.Add(time.Duration(max(1, p.JitterMs)*2) * time.Millisecond)
		s.engine.stats.reordered.Add(1)
	}
	if release.After(now) {
		s.engine.stats.delayed.Add(1)
	}
	s.order++
	heap.Push(&s.queue, &queuedPacket{b, release, s.order})
	if !healthy && s.chance(lim.DuplicatePercent) && len(s.queue) < p.QueuePackets {
		s.order++
		heap.Push(&s.queue, &queuedPacket{append([]byte(nil), b...), release.Add(time.Millisecond), s.order})
		s.engine.stats.duplicated.Add(1)
	}
}

func burstHealthy(burst *burstSchedule, startedAt, now time.Time) bool {
	if burst == nil {
		return false
	}
	impaired := time.Duration(burst.ImpairedSeconds) * time.Second
	healthy := time.Duration(burst.HealthySeconds) * time.Second
	return now.Sub(startedAt)%(impaired+healthy) >= impaired
}

func (s *scheduler) releaseDue() {
	now := time.Now()
	for len(s.queue) > 0 && !s.queue[0].release.After(now) {
		b := heap.Pop(&s.queue).(*queuedPacket).bytes
		if s.upload {
			s.engine.stats.uploadBytes.Add(int64(len(b)))
		} else {
			s.engine.stats.downloadBytes.Add(int64(len(b)))
		}
		select {
		case s.output <- b:
		case <-s.engine.stop:
			return
		}
	}
}

func (s *scheduler) chance(percent float64) bool {
	return percent > 0 && s.random.Float64()*100 < percent
}
func stopTimer(t *time.Timer) {
	if !t.Stop() {
		select {
		case <-t.C:
		default:
		}
	}
}
func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}
