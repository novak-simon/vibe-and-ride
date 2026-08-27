# Fix: BLE Discovery and Multi-Service Subscription

Improve BLE discovery and implement robust sequential subscription to multiple GATT services.

## User Review Required

> [!IMPORTANT]
> I am implementing a "subscription queue" for BLE characteristics. This allows the app to subscribe to both **Cycling Speed and Cadence** and **Cycling Power** services simultaneously if a device supports both.
>
> [!NOTE]
> I am also updating the permission request to always include Location permissions, as many Android devices (including Samsung) require Location to be ON and granted for BLE scanning to work, even on newer Android versions.

## Proposed Changes

### BLE Core Logic

#### [MODIFY] [AndroidBluetoothCadenceSource.kt](file:///C:/Users/novak/Documents/vibe-and-ride/shared/src/androidMain/kotlin/com/novaksimon/vibeandride/workout/AndroidBluetoothCadenceSource.kt)
- Implement a `descriptorWriteQueue` to handle sequential `writeDescriptor` calls.
- Update `setupNotifications` to find all available measurement characteristics (CSC and CP) and add them to the queue.
- Implement `processDescriptorQueue()` to handle the async flow of enabling notifications.
- Improve logging in `onScanResult` to confirm if ANY device is seen at all.

### Android Application Layer

#### [MODIFY] [MainActivity.kt](file:///C:/Users/novak/Documents/vibe-and-ride/androidApp/src/main/kotlin/com/novaksimon/vibeandride/MainActivity.kt)
- Update `requestBlePermissions` to always include `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` regardless of API level. This is a common requirement for BLE discovery.

## Verification Plan

### Automated Tests
- None, requires hardware.

### Manual Verification
- Deploy to device.
- Grant all permissions (BLE + Location).
- Verify "Discovered BLE" appears in Logcat for nearby devices.
- Verify "GATT onDescriptorWrite: status=0" appears for both CSC and CP if applicable.
- Verify cadence updates while pedaling.
