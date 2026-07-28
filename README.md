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

PacketJam includes a progression from normal 4G through increasingly difficult 4G, 3G, and
EDGE conditions. The selectable catalog stops at Fringe EDGE; Offline is the only explicit
failure preset.
Directional values use `rateKbps / loss% / duplicate% / corrupt% / reorder%`. Latency is
`base ± jitter` in milliseconds. A burst is `impaired seconds / healthy seconds`; healthy
windows remove delay and random failures but retain the configured bandwidth cap.

| Profile | Latency | Download | Upload | Queue | Burst | Offline |
| --- | ---: | --- | --- | ---: | ---: | :---: |
| Normal 4G | 45 ± 15 ms | 12000 / 0.3 / 0.05 / 0.01 / 0.5 | 5000 / 0.4 / 0.05 / 0.01 / 0.5 | 256 | — | no |
| Busy 4G | 80 ± 25 ms | 6000 / 1.5 / 0.1 / 0.02 / 1.5 | 2000 / 2 / 0.1 / 0.02 / 1.5 | 192 | — | no |
| Weak 4G | 150 ± 50 ms | 3000 / 3 / 0.15 / 0.03 / 2 | 1000 / 4 / 0.15 / 0.03 / 2 | 192 | — | no |
| Rural 4G | 220 ± 80 ms | 2000 / 5 / 0.2 / 0.05 / 3 | 512 / 7 / 0.2 / 0.05 / 3 | 160 | — | no |
| Fading 4G | 280 ± 120 ms | 1500 / 6 / 0.25 / 0.05 / 3 | 512 / 8 / 0.25 / 0.05 / 3 | 160 | 20 / 5 | no |
| Good 3G | 180 ± 60 ms | 1800 / 4 / 0.15 / 0.03 / 2 | 512 / 6 / 0.15 / 0.03 / 2 | 160 | — | no |
| Typical 3G | 300 ± 100 ms | 1000 / 7 / 0.25 / 0.05 / 3 | 384 / 9 / 0.25 / 0.05 / 3 | 128 | — | no |
| Slow 3G | 450 ± 160 ms | 768 / 12 / 0.4 / 0.1 / 4 | 256 / 14 / 0.4 / 0.1 / 4 | 128 | 20 / 5 | no |
| Fading 3G | 650 ± 240 ms | 256 / 18 / 0.6 / 0.15 / 6 | 96 / 22 / 0.6 / 0.15 / 6 | 96 | 12 / 3 | no |
| Good EDGE | 550 ± 180 ms | 256 / 8 / 0.3 / 0.05 / 3 | 96 / 12 / 0.3 / 0.05 / 3 | 128 | — | no |
| Normal EDGE | 700 ± 220 ms | 160 / 12 / 0.4 / 0.08 / 4 | 64 / 16 / 0.4 / 0.08 / 4 | 112 | — | no |
| Fading EDGE | 850 ± 300 ms | 128 / 16 / 0.6 / 0.12 / 5 | 48 / 20 / 0.6 / 0.12 / 5 | 96 | 10 / 4 | no |
| Fringe EDGE | 950 ± 350 ms | 96 / 20 / 0.8 / 0.2 / 7 | 32 / 24 / 0.8 / 0.2 / 7 | 80 | 8 / 4 | no |
| Offline | 0 ± 0 ms | 0 / 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 / 0 | 256* | — | yes |

`*` The Offline queue size is ignored. The UI exposes the complete characteristics for the
selected profile, including all directional impairment fields and burst behavior.

PacketJam is a developer testing tool. It is not an anonymity service, does not decrypt TLS,
and does not send captured traffic to a PacketJam backend.
