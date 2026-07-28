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

PacketJam includes a progression from ordinary degraded networks to deliberate failure cases.
Directional values use `rateKbps / loss% / duplicate% / corrupt% / reorder%`. Latency is
`base ± jitter` in milliseconds. A burst is `impaired seconds / healthy seconds`; healthy
windows remove delay and random failures but retain the configured bandwidth cap.

| Profile | Latency | Download | Upload | Queue | Burst | Offline |
| --- | ---: | --- | --- | ---: | ---: | :---: |
| Stable Wi-Fi | 25 ± 8 ms | 25000 / 0.2 / 0.05 / 0.01 / 0.5 | 10000 / 0.2 / 0.05 / 0.01 / 0.5 | 256 | — | no |
| Congested Wi-Fi | 90 ± 35 ms | 8000 / 2 / 0.1 / 0.02 / 2 | 2000 / 3 / 0.1 / 0.02 / 2 | 192 | — | no |
| Weak 4G | 180 ± 60 ms | 3000 / 4 / 0.2 / 0.05 / 2 | 1000 / 5 / 0.2 / 0.05 / 2 | 192 | — | no |
| Rural 4G | 240 ± 100 ms | 1500 / 7 / 0.3 / 0.05 / 3 | 384 / 9 / 0.3 / 0.05 / 3 | 160 | — | no |
| Slow 3G | 450 ± 180 ms | 768 / 12 / 0.5 / 0.1 / 4 | 256 / 14 / 0.5 / 0.1 / 4 | 128 | 15 / 5 | no |
| Fading 3G | 650 ± 240 ms | 256 / 25 / 1 / 0.2 / 8 | 96 / 28 / 1 / 0.2 / 8 | 96 | 12 / 3 | no |
| Fading EDGE | 800 ± 250 ms | 128 / 15 / 0.5 / 0.1 / 5 | 48 / 18 / 0.5 / 0.1 / 5 | 96 | 8 / 4 | no |
| One bar | 1000 ± 500 ms | 64 / 35 / 1 / 0.3 / 8 | 24 / 40 / 1 / 0.3 / 8 | 64 | 12 / 3 | no |
| Almost disconnected | 2000 ± 900 ms | 16 / 70 / 2 / 0.5 / 12 | 8 / 75 / 2 / 0.5 / 12 | 24 | 18 / 2 | no |
| Dead zone | 4000 ± 1800 ms | 1 / 98 / 1 / 1 / 20 | 1 / 98 / 1 / 1 / 20 | 8 | — | no |
| Offline | 0 ± 0 ms | 0 / 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 / 0 | 256* | — | yes |

`*` The Offline queue size is ignored. The UI exposes the complete characteristics for the
selected profile, including all directional impairment fields and burst behavior.

PacketJam is a developer testing tool. It is not an anonymity service, does not decrypt TLS,
and does not send captured traffic to a PacketJam backend.
