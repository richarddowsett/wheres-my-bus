# Backend — Kotlin + Ktor BODS Route 21 Monitor

Kotlin + Ktor backend service that polls the UK DfT **Bus Open Data Service (BODS)** for **NIBS Route 21 (`NOC: NIBS`, `lineRef: 21`)**:
- **Timetable (`TransXChange` XML)**: Stop sequences (`Brentwood Railway Station` $\leftrightarrow$ `Ongar Four Wantz`), inter-stop timing links, and school-day departure times.
- **Live Vehicle Location (`SIRI-VM` XML)**: Real-time GPS coordinates, bearing, and ticket-machine delay offsets.
- **Disruptions (`SIRI-SX` XML)**: Official cancellations, diversions, and stop suspensions.

## Quality Gates (Linting, Unit/Integration Tests, $\ge 80\%$ Coverage)

```bash
./gradlew ktlintCheck detekt test koverVerify
```
