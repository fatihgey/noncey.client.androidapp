# noncey Android App — Architecture

This document is the authoritative technical reference for the Android app and the daemon changes required to support it. Read `CONCEPT.md` for the decision log.

---

## 1. System Overview

```
┌──────────────────────────────────────────────────────┐
│  Android Device                                      │
│                                                      │
│  ┌─────────────────┐    ┌──────────────────────────┐ │
│  │  SMS Receiver   │───▶│  ForwardService          │ │
│  │  (BroadcastRcvr)│    │  (background, auto-fwd)  │ │
│  └─────────────────┘    └────────────┬─────────────┘ │
│                                      │               │
│  ┌─────────────────┐    ┌────────────▼─────────────┐ │
│  │  UI             │    │  Spool (SQLite)           │ │
│  │  - SMS Tab      │    │  retry on reconnect       │ │
│  │  - Configs Tab  │    └────────────┬─────────────┘ │
│  │  - Settings     │                 │               │
│  └────────┬────────┘                 │               │
│           │  manual forward          │ auto forward  │
│           └──────────────┬───────────┘               │
│                          │ POST /api/sms/ingest       │
└──────────────────────────┼───────────────────────────┘
                           │ HTTPS + Bearer JWT
┌──────────────────────────▼───────────────────────────┐
│  noncey Daemon                                       │
│                                                      │
│  POST /api/sms/ingest ──▶ match_sms_provider()       │
│                              │ matched                │
│                              ├──▶ extract_nonce()     │
│                              │    └──▶ nonces table   │
│                              │ unmatched              │
│                              └──▶ unmatched_items     │
│                                   (channel_type=sms)  │
│  var/archive/{user}/sms/                             │
│  {timestamp}_{sender}.json  ◀── archive copy         │
│                                                      │
│  GET /api/configs ──▶ filtered to SMS channels       │
│  POST /api/configs/<id>/activate|deactivate          │
│                                                      │
│  /auth/unmatched ──▶ unified list (EMAIL + SMS)      │
└──────────────────────────────────────────────────────┘
                           │
                    Chrome Extension
                    (unchanged; consumes nonces as before)
```

---

## 2. Daemon Changes

### 2.1 Schema Migration

#### Rename `unmatched_emails` → `unmatched_items`

Add a `channel_type` discriminator. Existing email rows default to `'email'`. The `subject` and `fwd_sender` columns are already nullable; no change needed.

```sql
-- Run once; guarded by install.sh PRAGMA table_info check
ALTER TABLE unmatched_emails RENAME TO unmatched_items;

ALTER TABLE unmatched_items
    ADD COLUMN channel_type TEXT NOT NULL DEFAULT 'email'
        CHECK(channel_type IN ('email', 'sms'));

DROP INDEX IF EXISTS idx_unmatched_user;
CREATE INDEX IF NOT EXISTS idx_unmatched_user
    ON unmatched_items(user_id, received_at);
```

#### Add `channel_type` to `providers`

```sql
ALTER TABLE providers
    ADD COLUMN channel_type TEXT NOT NULL DEFAULT 'email'
        CHECK(channel_type IN ('email', 'sms'));
```

#### Add `sender_phone` to `provider_matchers`

For SMS channels the routing key is the sender phone number (normalised, E.164). `sender_email` and `subject_pattern` are left NULL for SMS-channel matchers.

```sql
ALTER TABLE provider_matchers
    ADD COLUMN sender_phone TEXT;
```

A matcher row is valid when at least one of (`sender_email`, `sender_phone`) is non-NULL. The existing CHECK on `sender_email`/`subject_pattern` does not apply to SMS rows.

#### Updated `providers` sample column

`sample_email` is reused as `sample_body` for SMS channels (the column is not renamed to avoid unnecessary migration complexity; the UI labels it correctly per channel type).

### 2.2 `GET /api/configs` — Channel Type in Response

Each provider tag entry is extended to include the channel type, allowing the Android app to filter for SMS-channel configs client-side.

```json
{
  "id": 7,
  "name": "github-otp",
  "activated": true,
  "provider_tags": ["github"],
  "channel_types": ["email"],      // ← new; parallel array to provider_tags
  ...
}
```

The Android app uses `channel_types` to:
- Build the local sender matcher cache (keep only SMS-channel entries)
- Filter the config list shown in the Configs tab
- Filter the manual-funnel picker (SMS-channel configs only)

### 2.3 New Endpoint: `POST /api/sms/ingest`

**Auth:** Bearer JWT (any valid session token, same `@require_auth` decorator).

**Request:**
```json
{
  "sender":      "+491234567890",
  "body":        "Your GitHub code is 847291",
  "received_at": "2026-04-04T10:14:00+00:00"
}
```

**Processing (mirrors `ingest.py` email flow):**

1. Validate fields; 400 if missing.
2. Call `match_sms_provider(conn, user_id, sender_phone)` — see §2.4.
3. If matched:
   - Call `extract_nonce(body_text, mode, start_marker, end_marker, length)` (existing function, unchanged).
   - If extracted: `INSERT INTO nonces`.
   - If extraction fails: fall through to unmatched.
4. If unmatched:
   - `INSERT INTO unmatched_items (user_id, channel_type, sender, body_text, received_at) VALUES (?, 'sms', ?, ?, ?)`.
5. Archive: write `var/archive/{username}/sms/{timestamp}_{sender}.json`.
6. Return `204 No Content` in all cases (fire-and-forget; app does not need outcome).

**Responses:**
- `204` — accepted (matched or unmatched; always succeeds if auth is valid)
- `400` — missing required fields
- `401` — invalid/expired token

### 2.4 `match_sms_provider()`

New function in `ingest.py` (or a shared module), analogous to `find_matching_provider()`:

```python
def match_sms_provider(conn, user_id: int, sender_phone: str):
    """
    Return the first providers row of channel_type='sms' whose matcher
    matches the sender phone number, or None.

    Active = same activation logic as email:
      - config_id IS NULL (unassigned), OR
      - private config, activated=1, status in (valid, valid_tested), OR
      - public config with user subscription.
    """
    providers = conn.execute(
        "SELECT p.id, p.config_id, p.extract_mode, "
        "       p.nonce_start_marker, p.nonce_end_marker, p.nonce_length "
        "FROM   providers p "
        "LEFT JOIN configurations c ON c.id = p.config_id "
        "WHERE  p.user_id = ? AND p.channel_type = 'sms' "
        "  AND (p.config_id IS NULL "
        "       OR (c.visibility = 'private' AND c.activated = 1 "
        "           AND c.status IN ('valid', 'valid_tested')) "
        "       OR (c.visibility = 'public' "
        "           AND EXISTS (SELECT 1 FROM subscriptions s "
        "                       WHERE s.user_id = ? AND s.config_id = c.id)))",
        (user_id, user_id)
    ).fetchall()

    for prov in providers:
        matchers = conn.execute(
            "SELECT sender_phone FROM provider_matchers "
            "WHERE  provider_id = ? AND sender_phone IS NOT NULL",
            (prov['id'],)
        ).fetchall()
        for m in matchers:
            if m['sender_phone'] == sender_phone:
                return prov
    return None
```

### 2.5 SMS Archive

```python
def archive_sms(archive_root: str, username: str,
                sender: str, body: str, received_at: str) -> None:
    sms_dir = Path(archive_root) / username / 'sms'
    sms_dir.mkdir(parents=True, exist_ok=True)
    ts = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%f')
    # Sanitise sender for use in filename (strip +, keep digits)
    safe_sender = re.sub(r'[^\w]', '', sender)
    path = sms_dir / f"{ts}_{safe_sender}.json"
    path.write_text(json.dumps({
        'sender':      sender,
        'body':        body,
        'received_at': received_at,
    }, ensure_ascii=False), encoding='utf-8')
```

Retention cleanup (existing cron job) must be extended to also purge files under `archive/{user}/sms/` older than `archive_retention_d` days.

### 2.6 Unmatched List UI (`/auth/unmatched`)

- Table gains a **Channel** column showing `EMAIL` or `SMS` badge.
- SMS rows omit the Subject column (or show `—`).
- Dismiss action: `DELETE FROM unmatched_items WHERE id = ? AND user_id = ?` — same as before, no phone callback.
- "Promote to channel" for SMS rows opens the channel creation wizard pre-set to `channel_type = 'sms'` and pre-fills the sender phone.

### 2.7 Config Creation/Edit UI — SMS Channel Type

The channel creation wizard (`/auth/configs/<id>/channels/new`) gains a **Channel type** selector (`Email` / `SMS`). When SMS is selected:
- `extract_source` is hidden and locked to `body`.
- Header section shows a **Sender phone** field instead of Sender email + Subject pattern.
- Sample input label changes to "Sample SMS body".

The `_auto_update_status()` function requires no changes — it checks for ≥1 channel with ≥1 matcher, which holds for SMS channels too.

---

## 3. Android App

### 3.1 Permissions

| Permission | Required for |
|---|---|
| `RECEIVE_SMS` | Background auto-forward on reception |
| `READ_SMS` | SMS tab (list all device SMS) |
| `WRITE_SMS` (API ≤ 18) / `MANAGE_SMS` (API 19+) | Mark forwarded SMS as Read |
| `INTERNET` | All daemon API calls |
| `ACCESS_NETWORK_STATE` | Spool: detect connectivity for retry |

### 3.2 Module Structure

```
app/
├── receiver/
│   └── SmsReceiver.kt          BroadcastReceiver — RECEIVE_SMS
├── service/
│   └── ForwardService.kt       Foreground service — spool + retry loop
├── data/
│   ├── SpoolDb.kt              Room database — spool table
│   ├── SpoolDao.kt
│   ├── ConfigCache.kt          In-memory/SharedPrefs sender matcher cache
│   └── ApiClient.kt            Retrofit/OkHttp client; injects Bearer token
├── ui/
│   ├── MainActivity.kt         BottomNav: SMS tab | Configs tab
│   ├── sms/
│   │   ├── SmsListFragment.kt  READ_SMS cursor → RecyclerView
│   │   └── ForwardDialog.kt    "Server chooses" / "Pick config" bottom sheet
│   ├── configs/
│   │   └── ConfigsFragment.kt  SMS-channel config list; activate/deactivate
│   └── settings/
│       └── SettingsFragment.kt Daemon URL, credentials, spool params, country code
└── util/
    └── PhoneNormalizer.kt      E.164 normalisation logic
```

### 3.3 Authentication

Same JWT flow as Chrome extension. Login screen collects daemon base URL, username, password. Token and `expires_at` stored in `EncryptedSharedPreferences`. On every API call, check `expires_at`; if expired, redirect to login. No silent refresh — re-login required.

Token is independent of the Chrome extension's token (separate session row in the daemon's `sessions` table).

### 3.4 Background Auto-Forward (`SmsReceiver` + `ForwardService`)

```
SMS arrives
    └── SmsReceiver.onReceive()
            │
            ├── Normalise sender (PhoneNormalizer)
            ├── Check ConfigCache: sender in any active SMS-channel config?
            │       no  → do nothing
            │      yes  ↓
            ├── Mark SMS as Read (ContentResolver)
            ├── Build SmsPayload(sender, body, received_at)
            └── Enqueue to Spool (SpoolDao.insert)
                    └── ForwardService picks up
                            ├── POST /api/sms/ingest
                            │       success → delete from spool
                            │       network error → leave in spool, retry in 5s
                            └── Purge spool entries older than retention window
```

`ForwardService` runs as a foreground service (persistent notification) while there are pending spool entries; stops itself when the spool is empty.

### 3.5 Spool (SQLite via Room)

```kotlin
@Entity(tableName = "spool")
data class SpoolEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender:      String,
    val body:        String,
    val receivedAt:  String,   // ISO-8601
    val enqueuedAt:  Long,     // System.currentTimeMillis()
    val configId:    Int? = null  // non-null for manual funnel with explicit config
)
```

Retention check (run on each ForwardService cycle):
```kotlin
val cutoff = System.currentTimeMillis() - retentionMs   // default 10 min
spoolDao.deleteBefore(cutoff)
```

### 3.6 Sender Matcher Cache (`ConfigCache`)

On login and periodically (every 60 s while app is foregrounded, or on demand):

1. `GET /api/configs`
2. Filter to entries where `channel_types` contains `'sms'`
3. For each such config, fetch its SMS-channel provider matchers (sender phones) — **Note:** The current `GET /api/configs` response includes `provider_tags` but not matcher details. Either:
   - Add a lightweight `GET /api/configs/sms-senders` endpoint that returns `{config_id, activated, sender_phones[]}`, or
   - Include `sms_senders` in the existing `GET /api/configs` response for SMS-channel providers.
   
   Preferred: extend `GET /api/configs` with an `sms_senders` array on each config entry. The Android app is the only consumer that needs this; the Chrome extension ignores the field.

```json
{
  "id": 7,
  "activated": true,
  "channel_types": ["sms"],
  "sms_senders": ["+491234567890", "+15005550006"],
  ...
}
```

Cache stored in memory (refreshed on connectivity restore). Used by `SmsReceiver` to gate auto-forward without a network round-trip.

### 3.7 Phone Number Normalisation (`PhoneNormalizer`)

Applied before any sender comparison or transmission.

```kotlin
object PhoneNormalizer {
    fun normalize(raw: String, simCountryCode: String?): String {
        val digits = raw.filter { it.isDigit() }
        // Short codes: 8 digits or fewer → send as-is
        if (digits.length <= 8) return raw.trim()
        // Already E.164
        if (raw.trim().startsWith("+")) return raw.trim()
        // National format: strip leading zeros, prepend country code
        val countryCode = simCountryCode ?: DEFAULT_COUNTRY_CODE  // from settings
        val national = digits.trimStart('0')
        return "+$countryCode$national"
    }
}
```

`simCountryCode` is read from `TelephonyManager.getSimCountryIso()` → mapped to a calling code via a bundled ISO-3166 → country-calling-code table. If the SIM returns empty or the mapping fails, fall back to the user-configured value in Settings.

### 3.8 SMS Tab (`SmsListFragment`)

- Queries the device SMS `content://sms/inbox` cursor via `ContentResolver`.
- Displays sender, body preview, timestamp in a `RecyclerView`.
- Long-press or checkbox → **Forward** button activates.
- **Forward** opens `ForwardDialog`:
  - Option A: **"Let the server choose"** — enqueues to spool with `configId = null`. Daemon matches or stores as unmatched.
  - Option B: **"Pick a configuration"** — shows bottom sheet listing active SMS-channel configs (from ConfigCache). User selects one; enqueued with `configId = selectedId`. Daemon uses that config's provider for extraction, bypassing sender matching.
- After forwarding: SMS is marked Read; no further decoration.

### 3.9 Configs Tab (`ConfigsFragment`)

Displays private owned + subscribed public configurations that have at least one SMS-type channel. Mirrors daemon `/auth` semantics:

| Config type | Shown while | Activate/Deactivate |
|---|---|---|
| Private (owned) | Always | Yes; calls `POST /api/configs/<id>/activate` or `/deactivate` |
| Subscribed public | Until unsubscribed | Deactivate = unsubscribe (`DELETE /api/subscriptions/<config_id>`); immediate UI mark; removed on next reload |

No marketplace view. Subscribing to public configs is done at the daemon web UI.

Activation is global — calling activate/deactivate from the phone affects all clients (Chrome extension, daemon UI) because it writes `activated` on the configuration row.

### 3.10 Settings Screen

| Setting | Default | Notes |
|---|---|---|
| Daemon base URL | — | e.g. `https://noncey.example.com` |
| Username | — | |
| Spool retention | 10 minutes | Integer, minutes |
| Retry interval | 5 seconds | Integer, seconds |
| Country calling code | (from SIM) | Numeric, e.g. `49`; used when SIM country unreadable |
| Auto-forward enabled | on | Global kill switch |

---

## 4. REST API Additions Summary

| Method | Path | Who calls | Purpose |
|---|---|---|---|
| `POST` | `/api/sms/ingest` | Android app | Submit SMS for matching/archival |
| `GET` | `/api/configs` | Android app | Extended: `channel_types`, `sms_senders` fields added |
| `POST` | `/api/configs/<id>/activate` | Android app | Already exists; called for global activate |
| `POST` | `/api/configs/<id>/deactivate` | Android app | Already exists; called for global deactivate |
| `DELETE` | `/api/subscriptions/<config_id>` | Android app | Unsubscribe from public config (if not already an endpoint, add it) |

### `POST /api/sms/ingest` — Full Spec

```
POST /api/sms/ingest
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "sender":      "+491234567890",   // required; E.164 or short code
  "body":        "...",             // required
  "received_at": "2026-04-04T10:14:00+00:00",  // required; ISO-8601
  "config_id":   7                 // optional; overrides sender matching
}

Responses:
  204  Accepted (matched, extracted, or stored as unmatched)
  400  Missing required field
  401  Unauthenticated
```

---

## 5. End-to-End Data Flows

### 5.1 Auto-Forward (Happy Path)

```
1. SMS arrives on device from +491234567890
2. SmsReceiver: normalize sender → "+491234567890"
3. ConfigCache lookup: sender in active config #7 (github-otp)? yes
4. Mark SMS as Read (ContentResolver)
5. SpoolDao.insert(sender, body, received_at, configId=null)
6. ForwardService: POST /api/sms/ingest {sender, body, received_at}
7. Daemon: match_sms_provider() → provider #3 (github, sms channel)
8. Daemon: extract_nonce(body, 'auto', ...) → "847291"
9. Daemon: INSERT INTO nonces (user_id=1, provider_id=3, nonce_value='847291', ...)
10. Daemon: archive_sms(archive_root, 'alice', sender, body, received_at)
11. Daemon: return 204
12. ForwardService: SpoolDao.delete(entry)
```

### 5.2 Auto-Forward (Offline Spool)

```
Steps 1–5 as above
6. ForwardService: POST fails (no network)
7. Entry remains in spool; ForwardService retries every 5 s
8. After 10 min (default): SpoolDao.deleteBefore(cutoff) purges entry silently
   — OR —
8. Network restores before cutoff: retry succeeds → step 7 in flow 5.1 onward
```

### 5.3 Manual Forward — Server Chooses

```
1. User opens SMS tab, selects SMS from unknown sender
2. Taps Forward → "Let the server choose"
3. SpoolDao.insert(sender, body, received_at, configId=null)
4. ForwardService: POST /api/sms/ingest (no config_id)
5. Daemon: match_sms_provider() → None
6. Daemon: INSERT INTO unmatched_items (channel_type='sms', sender, body_text, received_at)
7. Daemon: archive_sms(...)
8. Returns 204; user can inspect at daemon /auth/unmatched
```

### 5.4 Manual Forward — Pick Config (Funnel)

```
1. User selects SMS, taps Forward → "Pick a configuration"
2. ForwardDialog shows SMS-channel configs from ConfigCache
3. User picks config #7 (github-otp)
4. SpoolDao.insert(sender, body, received_at, configId=7)
5. ForwardService: POST /api/sms/ingest {sender, body, received_at, config_id: 7}
6. Daemon: skip sender matching; use provider attached to config #7
7. Daemon: extract_nonce(body, ...) → nonce or unmatched (if extraction fails)
8. Archive + 204 as usual
```

### 5.5 Global Deactivate from Phone

```
1. User opens Configs tab, taps deactivate on config #7
2. App: POST /api/configs/7/deactivate
3. Daemon: UPDATE configurations SET activated=0 WHERE id=7
4. Config is now inactive for all clients (Chrome extension stops matching,
   daemon stops processing email/sms for this config's providers)
5. App: updates local ConfigCache; removes config #7's senders from auto-forward set
```

---

## 6. File & Directory Layout

### Daemon additions

```
noncey.daemon/
├── app.py              + POST /api/sms/ingest endpoint
│                       + sms_senders / channel_types in GET /api/configs
├── ingest.py           + match_sms_provider(), archive_sms()
├── admin.py            + unmatched_items UI (SMS badge, SMS promote flow)
│                       + channel type selector in channel creation wizard
├── schema.sql          + ALTER TABLE migrations (guarded, idempotent)
└── var/
    └── archive/
        └── {user}/
            └── sms/
                └── {timestamp}_{sender}.json
```

### Android app (new repo)

```
noncey.client.androidapp/
├── CONCEPT.md
├── ARCHITECTURE.md      (this file)
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/noncey/android/
│           ├── receiver/SmsReceiver.kt
│           ├── service/ForwardService.kt
│           ├── data/
│           │   ├── SpoolDb.kt
│           │   ├── SpoolDao.kt
│           │   ├── ConfigCache.kt
│           │   └── ApiClient.kt
│           ├── ui/
│           │   ├── MainActivity.kt
│           │   ├── sms/SmsListFragment.kt
│           │   ├── sms/ForwardDialog.kt
│           │   ├── configs/ConfigsFragment.kt
│           │   └── settings/SettingsFragment.kt
│           └── util/PhoneNormalizer.kt
└── build.gradle
```
