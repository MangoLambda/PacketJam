package packetjamtun

import (
	"testing"
	"time"
)

func TestBurstHealthyPhase(t *testing.T) {
	start := time.Unix(100, 0)
	b := &burstSchedule{ImpairedSeconds: 8, HealthySeconds: 4}

	if burstHealthy(b, start, start.Add(7*time.Second)) {
		t.Fatal("burst should still be impaired before the healthy window")
	}
	if !burstHealthy(b, start, start.Add(8*time.Second)) {
		t.Fatal("burst should enter the healthy window at the boundary")
	}
	if burstHealthy(b, start, start.Add(12*time.Second)) {
		t.Fatal("burst should return to impairment at the cycle boundary")
	}
}

func TestHealthyBurstRetainsRateLimit(t *testing.T) {
	start := time.Unix(100, 0)
	e := &Engine{startedAt: start}
	e.profile.Store(&profile{
		LatencyMs:    1000,
		QueuePackets: 4,
		Download:     limits{RateKbps: 8, LossPercent: 100},
		Burst:        &burstSchedule{ImpairedSeconds: 1, HealthySeconds: 1},
	})
	s := newScheduler(e, false, 1)

	s.impairAt([]byte{1}, start)
	if len(s.queue) != 0 {
		t.Fatal("impaired phase should drop a 100% loss packet")
	}

	s.impairAt([]byte{2}, start.Add(time.Second))
	if len(s.queue) != 1 {
		t.Fatalf("healthy phase queued %d packets, want 1", len(s.queue))
	}
	want := start.Add(time.Second + time.Millisecond)
	if !s.queue[0].release.Equal(want) {
		t.Fatalf("healthy packet released at %s, want %s", s.queue[0].release, want)
	}
}

func TestDecodeProfileRejectsInvalidBurst(t *testing.T) {
	_, err := decodeProfile(`{"queuePackets": 4, "burst": {"impairedSeconds": 0, "healthySeconds": 2}}`)
	if err == nil {
		t.Fatal("invalid burst duration should be rejected")
	}
}
