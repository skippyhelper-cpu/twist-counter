# TwistCounter — Product Requirements Document

*Lean-based corner counting for motorcyclists. Phone in pocket or on mount. Ride hard, count corners.*

---

## 1. Concept & Vision

An Android app that silently logs a motorcycle ride using the phone's IMU (gyroscope + accelerometer) and GPS, then delivers a post-ride summary of corner count, lean angles, and ride telemetry. No distractions during the ride — all the data comes after.

**The hook:** "How many corners on the Snake Pass run?" — TwistCounter gives you the answer. Built by riders, for riders.

**Core metaphor:** A fitness tracker, but for cornering. You don't check your watch mid-run. You check the data afterward.

**Target user:** Sport/nakED riders who want to quantify their rides. WAM group, BikersHub forums, UK twisty roads enthusiasts.

---

## 2. Core Features

### 2.1 Ride Logging (During Ride — Silent)

- [ ] **IMU sensor logging** — Gyroscope (roll rate) + Accelerometer (gravity vector) sampled at 50 Hz
- [ ] **GPS logging** — Speed, heading, location every 1-2 seconds
- [ ] **Foreground Service** — Persistent notification so Android doesn't kill the app
- [ ] **Screen-off operation** — No screen wake during ride (silent mode)
- [ ] **10-20 second calibration** — Rider sits still, phone locks in "zero lean" reference angle
- [ ] **Ride auto-detect** — Start logging when motion detected, stop when stationary for 5+ minutes

### 2.2 Corner Detection Algorithm

- [ ] **Lean angle estimation** — Complementary filter (gyro + accelerometer fusion)
- [ ] **Corner start** — Lean exceeds threshold (default: 12°, tunable)
- [ ] **Corner end** — Lean returns below threshold + hysteresis applied
- [ ] **Corner count** — Increment when corner end detected
- [ ] **GPS speed filter** — Ignore corners below 20 km/h (junction protection)
- [ ] **Lean rate filter** — Reject sharp jerks (< 2 seconds) to reduce false positives
- [ ] **Body-lean compensation** — Detect and ignore lean events that correlate with acceleration/deceleration (hard braking = rider leans forward, not cornering)

### 2.3 Post-Ride Summary

- [ ] **Corner count** — Total corners detected on the ride
- [ ] **Max lean angle** — Highest lean recorded, left and right separately
- [ ] **Average lean per corner** — Mean lean angle across all corners
- [ ] **Lean angle histogram** — Distribution of corner intensities (e.g., 20 corners < 20°, 12 corners 20-30°, 5 corners 30-40°, 2 corners > 40°)
- [ ] **Ride stats** — Duration, distance, average speed, max speed
- [ ] **Top 5 corners** — Corners with highest lean angles, with approximate GPS location
- [ ] **Shareable summary** — Card image or text to share on WhatsApp/Instagram

### 2.4 Settings

- [ ] **Mode selection** — Pocket mode / Handlebar mount mode (different calibration approaches)
- [ ] **Lean threshold** — Degrees of lean required to count as a corner (default: 12°, range: 8-20°)
- [ ] **Speed threshold** — Minimum speed to count corners (default: 20 km/h)
- [ ] **Audio feedback toggle** — Off by default. Beep per corner if enabled.
- [ ] **Sensor sensitivity** — Low / Medium / High (adjusts noise filtering aggressiveness)
- [ ] **Dark mode** — Always dark (motorcycle app aesthetic)

### 2.5 Ride History

- [ ] **Ride list** — Chronological list of past rides with date, distance, corner count
- [ ] **Ride detail view** — Full post-ride summary for any saved ride
- [ ] **Delete ride** — Remove unwanted rides
- [ ] **Statistics overview** — All-time corner count, favourite routes, best corners

---

## 3. Technical Architecture

### 3.1 Android Stack

- **Language:** Kotlin (preferred) or Java
- **Min SDK:** 26 (Android 8.0) — covers 95%+ of devices
- **Target SDK:** 34 (Android 14)
- **Architecture:** MVVM with Clean Architecture layers
- **DI:** Hilt (Dagger) or manual DI for simplicity
- **Local DB:** Room — store rides, corner events, lean samples
- **Background:** Foreground Service with persistent notification
- **Sensors:** `SensorManager` (TYPE_GYROSCOPE, TYPE_ACCELEROMETER, TYPE_ROTATION_VECTOR)
- **Location:** FusedLocationProviderClient

### 3.2 Data Model

```
Ride
  - id: UUID
  - startTime: DateTime
  - endTime: DateTime
  - distanceKm: Float
  - durationSeconds: Int
  - avgSpeedKmh: Float
  - maxSpeedKmh: Float
  - cornerCount: Int
  - maxLeanLeft: Float (degrees)
  - maxLeanRight: Float (degrees)
  - avgLean: Float

CornerEvent
  - id: UUID
  - rideId: UUID
  - startTime: DateTime
  - peakLeanAngle: Float
  - direction: LEFT | RIGHT
  - durationSeconds: Float
  - gpsLat: Float (approx location)
  - gpsLng: Float

LeanSample (stored in batches, not individually)
  - rideId: UUID
  - timestamp: DateTime
  - leanAngle: Float
  - speedKmh: Float
```

### 3.3 Key Algorithms

**Complementary Filter (lean estimation):**
```
alpha = 0.98  // weight for gyro
gyroRate = gyroZ.read()  // rad/s
accelRoll = atan2(accelY, accelZ)  // radians
angle = alpha * (angle + gyroRate * dt) + (1-alpha) * accelRoll
```

**Corner Detection State Machine:**
```
STRAIGHT → (lean > threshold) → CORNER_START
CORNER_START → (lean peaks) → CORNER_PEAK
CORNER_PEAK → (lean < threshold for 0.5s) → CORNER_END → count++
CORNER_END → (lean < threshold for 1s) → STRAIGHT (ready for next)
```

### 3.4 File Structure

```
app/
├── data/
│   ├── local/
│   │   ├── RideDatabase (Room)
│   │   ├── RideDao
│   │   └── CornerEventDao
│   └── repository/
│       └── RideRepository
├── domain/
│   ├── model/
│   │   ├── Ride
│   │   ├── CornerEvent
│   │   └── LeanReading
│   ├── usecase/
│   │   ├── StartRideUseCase
│   │   ├── ProcessSensorDataUseCase
│   │   ├── DetectCornerUseCase
│   │   └── GenerateRideSummaryUseCase
│   └── service/
│       └── RideForegroundService
├── presentation/
│   ├── MainActivity
│   ├── RideListFragment
│   ├── RideDetailFragment
│   ├── SettingsFragment
│   └── viewmodel/
└── sensor/
    ├── SensorFusionProcessor
    ├── LeanAngleCalculator
    └── CornerDetector
```

---

## 4. Non-Functional Requirements

- [ ] **Battery** — < 5% battery per hour of active logging
- [ ] **Accuracy** — 70-85% corner detection on clear twisty roads
- [ ] **Cold start** — App ready to calibrate within 3 seconds of launch
- [ ] **Offline** — Full functionality without internet. No cloud dependency.
- [ ] **Crash-free** — No crashes on sensor disconnects, GPS loss, low memory
- [ ] **Privacy** — All data stored locally. No accounts, no data leaves the phone.
- [ ] **Small APK** — Target < 10 MB

---

## 5. UI/UX Design Direction

### Aesthetic
Dark theme, high contrast. Motorcycle cockpit / racing telemetry inspired. Sharp, minimal, purposeful.

### Color Palette
- Background: Near-black (#0D0D0D)
- Primary accent: Electric blue (#00B4FF) — speedometer/tech feel
- Secondary: Warning amber (#FFB800) — for alerts
- Text: White (#FFFFFF) and grey (#888888)
- Lean indicators: Blue (left lean) and orange/red (right lean)

### Typography
- Monospace for numbers (lean angles, speeds, corner counts) — feels technical
- Sans-serif for labels and body text

### Key Screens

**1. Home / Ride List**
- "Start Ride" button (prominent)
- List of past rides below
- Minimal — this is a tool, not a social app

**2. Calibration Screen** (shown at ride start)
- Instructions: "Sit on bike. Hold steady."
- Live lean angle display (should show ~0° when still)
- Progress bar (10-20 second countdown)
- "Calibrated ✓" confirmation

**3. Post-Ride Summary** (shown when ride ends)
- Large corner count (hero number)
- Max lean left / right
- Distance, duration, avg speed
- Lean histogram (bar chart)
- "Share" button
- "Save Ride" / "Discard" buttons

**4. Ride Detail** (tap on past ride)
- Full stats breakdown
- Map of route (if GPS available)
- Corner list with lean angles

**5. Settings**
- Mode: Pocket / Mount
- Lean threshold slider
- Speed threshold slider
- Audio toggle
- Sensor sensitivity
- About / Privacy policy

---

## 6. Testing Requirements

- [ ] **Sensor testing** — Log real rides, validate lean angle accuracy against known lean angles
- [ ] **Junction filtering** — Test at known junctions, roundabouts, traffic lights
- [ ] **Battery test** — Measure battery drain over 3-hour ride
- [ ] **Pocket vs mount comparison** — Log same route in both modes, compare accuracy
- [ ] **Device compatibility** — Test on Pixel, Samsung, OnePlus, Xiaomi (varying sensor quality)
- [ ] **Background execution** — Verify app survives screen-off, 30-minute idle, phone call interruption

---

## 7. Launch Plan

### Phase 1 — Private Beta (Weeks 1-4)
- [ ] Core sensor logging working
- [ ] Basic corner detection
- [ ] Post-ride summary
- [ ] No settings UI (hard-coded thresholds)
- [ ] Test with Filip on 5+ real rides
- [ ] Tune thresholds based on real data

### Phase 2 — Feature Complete (Weeks 5-8)
- [ ] Settings UI
- [ ] Ride history
- [ ] Junction filtering algorithm refined
- [ ] Pocket + Mount mode support
- [ ] Share functionality

### Phase 3 — Polish (Weeks 9-10)
- [ ] Lean histogram
- [ ] Top corners feature
- [ ] Battery optimization
- [ ] Privacy policy
- [ ] App Store listing (F-Droid? Google Play?)
- [ ] Beta testing with WAM members

---

## 8. Open Questions

- [ ] **App name** — "TwistCounter" / "LeanLog" / "CornerHawk" / something else?
- [ ] **Monetization** — Free (MVP), paid with extra features, or subscription?
- [ ] **Distribution** — Google Play, F-Droid, or both?
- [ ] **Community feature** — Leaderboards by route? (post-MVP)
- [ ] **GPX export** — Export ride data as GPX for Strava compatibility?
- [ ] **Dark mode only** — Is light mode needed?
- [ ] **Audio feedback default** — On or off by default?

---

## 9. Competitor Analysis

| App | Platform | Corner Count | Lean Angle | Post-Ride Only | Notes |
|-----|----------|-------------|-----------|----------------|-------|
|lean-alarm|N/A|Not real|Not real|Not real|Parody concept|
|MyRide|iOS/Android|❌ No|❌ No|✅ Yes|GPS tracking, no IMU|
|Endurance|iOS|❌ No|❌ No|✅ Yes|Track time, not lean|
|Cargear|iOS/Android|❌ No|❌ No|✅ Yes|GPS + video, no IMU|
|Ural MC|iOS/Android|❌ No|❌ No|✅ Yes|Russian, minimal features|

**Gap:** No app on Google Play specifically counts corners using IMU lean angle. This is genuinely uncharted territory.

---

## 10. Dependencies

```
// Android
androidx.core:core-ktx:1.12.0
androidx.lifecycle:lifecycle-runtime-ktx:2.7.0
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1
com.google.android.gms:play-services-location:21.1.0

// DI
com.google.dagger:hilt-android:2.50

// UI
com.google.android.material:material:1.11.0
androidx.compose:compose-bom:2024.01.00  // or XML layouts for simplicity

// Async
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
```
