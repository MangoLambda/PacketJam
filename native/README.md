# PacketJam native forwarding engine

PacketJam includes a Go Mobile AAR backed by the maintained
`github.com/xjasonlyu/tun2socks/v2` gVisor TCP/IP stack. It:

- duplicates and owns the Android TUN descriptor;
- forwards TCP and UDP directly on the underlying Android network;
- schedules complete IPv4/IPv6 packets in both directions;
- applies live profile updates without rebuilding the TUN interface;
- emits traffic and impairment statistics four times per second; and
- synchronously closes the stack, queues, and descriptor.

The VPN excludes PacketJam's own application process. Consequently, direct
upstream sockets created by the Go core bypass the VPN and cannot loop back
into its TUN interface.

## Rebuild the AAR

Install Go, Android SDK 35, and an Android NDK, then run:

```bash
ANDROID_HOME=/path/to/android-sdk ./native/build-aar.sh
```

The script writes `app/libs/packetjam-tun.aar` for `armeabi-v7a`, `arm64-v8a`,
`x86`, and `x86_64`. The checked-in AAR is built from the source in this
directory; `go.mod` pins the forwarding dependency.
