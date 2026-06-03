/**
 * 2-LAYER AUTHORIZATION CHECK SYSTEM
 * 
 * Purpose: Optimize and secure the attendance flow by checking permissions
 * at two different stages with different data sources.
 * 
 * ============================================================================
 * LAYER 1: OFFLINE CHECK (App-side, Immediate)
 * ============================================================================
 * 
 * WHEN: Immediately after QR code is scanned
 * WHERE: QrScannerScreenV2._checkAuthorizationLayer1()
 * SOURCE: LocalDatabaseService (offline SQLite database)
 * BENEFIT: 
 *   - Fail-fast: Reject unauthorized users BEFORE they scan NFC or take photos
 *   - Saves time and device resources
 *   - Works offline if local DB is synced
 * 
 * FLOW:
 *   1. User scans QR code
 *   2. App extracts CCCD number from QR
 *   3. Query LocalDatabaseService.checkAccess(cccd)
 *   4. If NOT found in local DB → Show "Không có quyền" + Return to home
 *   5. If FOUND → Continue to NFC reading
 * 
 * ERROR CASE:
 *   - CCCD: 123456789 → Not in local DB → REJECTED IMMEDIATELY
 *   - UI: AuthorizationDeniedScreen
 *   - Auto-return to home after 3 seconds
 *   - User saves: NFC scan time + photo capture time
 * 
 * ============================================================================
 * LAYER 2: ONLINE CHECK (Server-side, Final Validation)  
 * ============================================================================
 * 
 * WHEN: When sending attendance record to backend (after face comparison)
 * WHERE: Java Backend /api/attendance/record endpoint
 * SOURCE: Server database access_permissions table
 * BENEFIT:
 *   - Security: Prevents unauthorized data from being recorded
 *   - Integrity: Double-checks in case local DB was corrupted/hacked
 *   - Audit trail: Server logs all grant/deny decisions
 * 
 * FLOW:
 *   1. Face comparison completes successfully
 *   2. App sends: {cccd, matched, score, chip_photo, selfie_photo} to server
 *   3. Server queries access_permissions table again
 *   4. If NOT in DB → Server rejects + logs incident
 *   5. If IN DB → Record attendance + trigger MQTT unlock signal
 * 
 * ERROR CASE:
 *   - CCCD: 987654321 passed Layer 1 (was in local DB)
 *   - But Layer 2: NOT in server DB (local DB out-of-sync)
 *   - Server REJECTS + logs: "Authorization mismatch for CCCD xyz"
 *   - User sees: "Lỗi server" + manual unlock required
 * 
 * ============================================================================
 * DATA FLOW DIAGRAM
 * ============================================================================
 * 
 * START
 *   ↓
 * +─────────────────────────────────────────────────────────────+
 * │ QR SCAN → Extract CCCD                                      │
 * └────────────────────┬────────────────────────────────────────┘
 *                      ↓
 * +─────────────────────────────────────────────────────────────+
 * │ LAYER 1 CHECK (Offline)                                     │
 * │ LocalDatabaseService.checkAccess(cccd)                      │
 * +──────┬────────────────────────────────────────┬──────────────┘
 *        ↓ NOT FOUND                              ↓ FOUND
 * ┌──────────────┐                         ┌──────────────┐
 * │ FORBIDDEN    │                         │ AUTHORIZED   │
 * │ Return Home  │                         │ Proceed      │
 * └──────────────┘                         └──────┬───────┘
 *                                                  ↓
 *                                    ┌─────────────────────────┐
 *                                    │ NFC Chip Read           │
 *                                    │ Face Capture            │
 *                                    │ Face Comparison         │
 *                                    └────────┬────────────────┘
 *                                             ↓
 *                                    ┌────────────────────────┐
 *                                    │ LAYER 2 CHECK (Online) │
 *                                    │ Server validates again  │
 *                                    └───┬────────────────┬────┘
 *                                        ↓                ↓
 *                                   ┌──────────┐    ┌────────────┐
 *                                   │ RECORD   │    │ LOG ERROR  │
 *                                   │ + MQTT   │    │ Reject     │
 *                                   │ UNLOCK   │    │ CCCD       │
 *                                   └──────────┘    └────────────┘
 * 
 * ============================================================================
 * CONFIGURATION & SYNC
 * ============================================================================
 * 
 * LOCAL DATABASE INITIALIZATION:
 * - Android app starts → ConfigService initializes
 * - If first time → LocalDatabaseService creates empty permissions table
 * - MQTT listens for SYNC_DATA action from server
 * - When server sends employee list → LocalDatabaseService.syncPermissions()
 * - Updates: cccd, name, is_active, start_time, end_time
 * 
 * SERVER DATABASE:
 * - Java backend maintains master access_permissions table
 * - Columns: cccd, full_name, door_id, is_active, entry_time, exit_time
 * - When admin adds/removes user → Server updates local Android via MQTT SYNC
 * 
 * SYNC PROTOCOL:
 * Server → Android (MQTT):
 * {
 *   "action": "SYNC_DATA",
 *   "employees": [
 *     {"cccd": "123456789", "full_name": "Nguyễn A", "is_active": 1},
 *     {"cccd": "987654321", "full_name": "Trần B", "is_active": 0}
 *   ]
 * }
 * 
 * ============================================================================
 * STATE TRACKING
 * ============================================================================
 * 
 * AttendanceFlowState fields for authorization:
 * 
 *   layer1AuthorizedAtQR: Bool?
 *     - TRUE if user passed Layer 1 check (found in local DB at QR scan)
 *     - FALSE if user rejected (not in local DB)
 *     - If CCCD not in QR → NULL (skipped)
 * 
 *   layer1AuthErrorMessage: String?
 *     - Error reason if Layer 1 failed
 * 
 *   layer2AuthorizedAtServer: Bool?
 *     - TRUE if server accepted in POST /api/attendance/record
 *     - FALSE if server rejected
 *     - NULL until server responds
 * 
 *   layer2AuthErrorMessage: String?
 *     - Error reason if Layer 2 failed
 * 
 *   isAuthorizedForDoor: Bool (computed property)
 *     - TRUE only if (layer1 not rejected) AND (layer2 not rejected)
 *     - Used for final decision to allow unlock
 * 
 * ============================================================================
 * ERROR HANDLING & LOGGING
 * ============================================================================
 * 
 * Log Format: [Component] [Level] [Message]
 * 
 * Layer 1 Log Examples:
 *   [QrScannerV2] INFO: Extracted CCCD: 123456789
 *   [QrScannerV2] WARNING: Authorization denied for CCCD: 123456789
 *   [AuthorizationDeniedScreen] INFO: User denied access, auto-return in 3s
 * 
 * Layer 2 Log Examples (Java):
 *   [AttendanceController] DEBUG: Validating CCCD 123456789 at server
 *   [AttendanceService] WARNING: CCCD mismatch - Layer 1 passed but Layer 2 denied
 *   [AttendanceController] INFO: Recording attendance for CCCD 123456789
 * 
 * ============================================================================
 * TESTING SCENARIOS
 * ============================================================================
 * 
 * SCENARIO 1: Authorized User (Happy Path)
 * Input: CCCD 123456789 in local DB ✓ and server DB ✓
 * Layer 1: PASS → Continue
 * Layer 2: PASS → Record attendance ✓
 * Result: Unlock signal sent, log recorded
 * 
 * SCENARIO 2: Unauthorized in Layer 1
 * Input: CCCD 999999999 NOT in local DB ✗
 * Layer 1: FAIL → Show error immediately
 * Layer 2: SKIPPED (never reached)
 * Result: Return to home, no NFC scan wasted
 * 
 * SCENARIO 3: Local DB Out of Sync
 * Input: CCCD 111111111 in local DB ✓ but removed from server DB ✗
 * Layer 1: PASS → Continue
 * Layer 2: FAIL → Server rejects, logs incident
 * Result: Manual intervention required, trigger sync
 * 
 * SCENARIO 4: Offline Mode (No Server)
 * Input: CCCD 222222222 in local DB ✓
 * Layer 1: PASS → Continue
 * Layer 2: TIMEOUT (no internet)
 * Result: App stores result locally, syncs when online
 * 
 * ============================================================================
 */

// IMPLEMENTATION FILES:
// - qr_scanner_screen_v2.dart: Layer 1 authorization
// - authorization_denied_screen.dart: Rejection UI
// - comparison_result_screen.dart: Layer 2 check before sending
// - attendance_flow_screen.dart: Flow orchestration
// - attendance_flow_state.dart: State model with layer tracking
// - Java Backend: /api/attendance/record endpoint (Layer 2 validation)
