# Phase 5: BLE Integration

Implement read-only cadence support from compatible smart trainers via Bluetooth Low Energy.

## User Review Required

> [!IMPORTANT]
> This integration requires **Bluetooth** and **Location** permissions on Android. The app will prompt for these when scanning starts.
>
> [!NOTE]
> For the initial implementation, the app will automatically attempt to connect to the first device broadcasting the **Cycling Speed and Cadence (CSC)** or **Cycling Power (CP)** service.

## Proposed Changes

### Domain Interfaces

#### [NEW] [CadenceSource.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/CadenceSource.kt)
- Define `CadenceSource` interface with a `Flow<Double?>` for live cadence.

### Platform Implementations

#### [NEW] [AndroidBluetoothCadenceSource.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/androidMain/kotlin/com/novaksimon/vibeandride/workout/AndroidBluetoothCadenceSource.kt)
- Implement `CadenceSource` using Android's `BluetoothAdapter` and `BluetoothGatt`.
- Handle scanning for CSC (UUID `0x1816`) and Cycling Power (UUID `0x1818`) services.
- Parse GATT notifications for cadence (RPM).

### Workout Logic Integration

#### [MODIFY] [WorkoutScheduler.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/WorkoutScheduler.kt)
- Update constructor to accept a `CadenceSource`.
- In `start()`, begin observing the `CadenceSource` flow and update `liveCadence` automatically.

#### [MODIFY] [WorkoutViewModel.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/commonMain/kotlin/com/novaksimon/vibeandride/workout/WorkoutViewModel.kt)
- Expose methods to trigger Bluetooth scanning and connection status.

### Android Application Layer

#### [MODIFY] [MainActivity.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/MainActivity.kt)
- Add permission requests for `Manifest.permission.BLUETOOTH_SCAN` and `Manifest.permission.BLUETOOTH_CONNECT` (Android 12+).
- Inject the real `AndroidBluetoothCadenceSource` into `WorkoutViewModel`.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/AndroidManifest.xml)
- Add necessary Bluetooth and Location `<uses-permission>` tags.

## Verification Plan

### Automated Tests
- Create `FakeCadenceSource` in `commonTest`.
- Update `WorkoutSchedulerTest` to verify that `liveCadence` is updated when the source emits values.

### Manual Verification
- Deploy to a physical Android device.
- Grant Bluetooth permissions.
- Turn on a smart trainer/cadence sensor.
- Verify "Free Ride" or "Structured Workout" displays live cadence from the device.
