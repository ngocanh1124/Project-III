# 2-Layer Authorization Implementation Guide

## Quick Summary

Your app now has **two permission checks** to ensure security and save time:

### Layer 1 (App-side, Instant)
```
QR Scan → Extract CCCD → Query Local Database
  ├─ NOT authorized? → Show error "Không có quyền" → Return to home (3 sec)
  └─ Authorized? → Continue to NFC scan
```

**File**: [qr_scanner_screen_v2.dart](lib/screens/qr_scanner_screen_v2.dart)  
**Method**: `_checkAuthorizationLayer1(String cccd)`  
**Database**: SQLite (LocalDatabaseService)  

### Layer 2 (Server-side, Final)
```
After face match → Before sending to server
  ├─ Server denies? → Log error, reject attendance
  └─ Server accepts? → Record attendance + MQTT unlock
```

**Where**: Java backend `POST /api/attendance/record`  
**Database**: MySQL/PostgreSQL access_permissions table  

---

## How to Test

### Test Case 1: Authorized User (Happy Path)
```
1. Add CCCD to LocalDatabaseService (via sync)
   - Device receives MQTT SYNC_DATA with employees list
   - CCCD 123456789 marked as active

2. Scan QR with CCCD 123456789
   - Layer 1: ✓ Found in local DB → Continue
   - Scan NFC chip, capture face, compare
   - Layer 2: ✓ Server confirms → Record attendance
```

### Test Case 2: Block Unauthorized (Layer 1 saves time)
```
1. Don't add CCCD to local DB

2. Scan QR with CCCD 987654321
   - Layer 1: ✗ NOT in local DB → STOP HERE
   - Show: AuthorizationDeniedScreen
   - Auto-return to home after 3 seconds
   - Result: Saved ~1 minute ⚡
```

### Test Case 3: Local DB Out-of-Sync (Layer 2 catches it)
```
1. Local DB has CCCD 555555555 (outdated)

2. Scan QR with CCCD 555555555
   - Layer 1: ✓ Found in local DB → Continue
   - Scan NFC, capture face, post to server
   - Layer 2: ✗ Server denies (removed last night)
   - Result: Server prevents bad attendance record
```

---

## State Tracking

The `AttendanceFlowState` now tracks both layers:

```dart
// Layer 1: Set immediately after QR scan
flowState.layer1AuthorizedAtQR = true/false;
flowState.layer1AuthErrorMessage = "Optional error reason";

// Layer 2: Set when server responds
flowState.layer2AuthorizedAtServer = true/false;
flowState.layer2AuthErrorMessage = "Optional error reason";

// Combined result
bool canUnlock = flowState.isAuthorizedForDoor;  // Both layers passed
```

---

## LocalDatabaseService Setup

The `LocalDatabaseService` provides the Layer 1 check:

```dart
// Check if CCCD is authorized for this door
bool isAuthorized = await LocalDatabaseService.checkAccess('123456789');

// Sync employee permissions from server
await LocalDatabaseService.syncPermissions([
  {'cccd': '123456789', 'full_name': 'Nguyễn A', 'is_active': 1},
  {'cccd': '987654321', 'full_name': 'Trần B', 'is_active': 0},
]);
```

**Table Structure** (SQLite):
```sql
CREATE TABLE permissions (
  cccd TEXT PRIMARY KEY,
  full_name TEXT,
  start_time TEXT,          -- e.g. "06:00:00"
  end_time TEXT,            -- e.g. "22:00:00"
  is_active INTEGER         -- 0=inactive, 1=active
);
```

---

## Backend (Java) - Layer 2 Validation

When the app sends attendance data, the backend must:

```java
@PostMapping("/api/attendance/record")
public ResponseEntity<?> recordAttendance(@RequestBody AttendanceRequest req) {
    // Layer 2: Double-check authorization
    boolean authorized = accessPermissionService.isAuthorized(
        req.getCccd(), 
        req.getDeviceCode()
    );
    
    if (!authorized) {
        // Deny: CCCD not in database or marked inactive
        logger.warn("Layer 2 denied: CCCD " + req.getCccd());
        return ResponseEntity.status(403).body("Access denied");
    }
    
    // Record attendance
    attendanceService.save(req);
    
    // Send MQTT unlock signal
    mqttService.publish("device/" + req.getDeviceCode() + "/unlock", "");
    
    return ResponseEntity.ok("Attendance recorded");
}
```

---

## Error Handling

### Layer 1 Errors
```
// User sees: AuthorizationDeniedScreen
// Auto-returns to home in 3 seconds
// Logs: "[QrScannerV2] WARNING: Authorization denied for CCCD: 987654321"
```

### Layer 2 Errors
```
// User sees: "Lỗi Server" in ComparisonResultScreen
// Can retry or report to admin
// Backend logs incident for audit
```

---

## Integration Checklist

- [ ] LocalDatabaseService has checkAccess() method (✓ already exists)
- [ ] QrScannerScreenV2 replaces old QR scanner
- [ ] AttendanceFlowScreen uses QrScannerScreenV2
- [ ] Java backend validates CCCD in POST /api/attendance/record
- [ ] MQTT SYNC_DATA populates local permissions table
- [ ] Test with authorized CCCD → passes both layers
- [ ] Test with unauthorized CCCD → blocked at Layer 1
- [ ] Test with out-of-sync CCCD → blocked at Layer 2

---

## Key Files

| File | Purpose |
|------|---------|
| [qr_scanner_screen_v2.dart](lib/screens/qr_scanner_screen_v2.dart) | Layer 1 check + QR extraction |
| [authorization_denied_screen.dart](lib/screens/authorization_denied_screen.dart) | Error UI when denied |
| [comparison_result_screen.dart](lib/screens/comparison_result_screen.dart) | Layer 2 check before sending |
| [attendance_flow_state.dart](lib/models/attendance_flow_state.dart) | Tracks both layer results |
| [AUTHORIZATION_2LAYER.md](AUTHORIZATION_2LAYER.md) | Full technical documentation |
| LocalDatabaseService | Operations: checkAccess(), syncPermissions() |

---

## Performance Improvement

**Before (No Layer 1 check):**
- Unauthorized user: Scan QR → NFC read (15s) → Face capture (10s) → Compare (5s) → Denied
- **Total time wasted: 30 seconds**

**After (With Layer 1 check):**
- Unauthorized user: Scan QR → Check local DB (0.1s) → Show error → Return
- **Total time: < 1 second** ⚡

