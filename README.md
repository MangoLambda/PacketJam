# PacketJam

PacketJam is a no-root Android network impairment UI built around `VpnService`. It provides
reproducible bad-network profiles, a deterministic packet scheduler, live runtime statistics,
and safe VPN lifecycle handling.

## Build

Requirements:

- JDK 17
- Android SDK 35
- The included multi-ABI `app/libs/packetjam-tun.aar` forwarding engine

```bash
./gradlew test
./gradlew assembleDebug
```

The included forwarding engine uses a gVisor userspace TCP/IP stack and applies the selected
impairments to complete IP packets in both directions. See `native/README.md` for reproducible
AAR build instructions.

## Included profiles

Weak 4G, Fading 3G, Fringe EDGE, One bar, Almost disconnected, Dead zone, and Offline.
These phone-network profiles focus on the edge of coverage, reaching 2.5–4 seconds of
latency and 85–98% packet loss. Profiles specify directional bandwidth/loss controls plus
latency, jitter, queue size, reordering, duplication, corruption, and optional
healthy/impaired burst windows.

PacketJam is a developer testing tool. It is not an anonymity service, does not decrypt TLS,
and does not send captured traffic to a PacketJam backend.
