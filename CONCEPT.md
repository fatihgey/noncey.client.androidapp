# noncey Android App — Concept

---

## Concept V1 *(initial architectural sketch)*

### Goal

Extend noncey with SMS as a second OTP provider channel, delivered via an Android app that forwards SMS to the daemon for matching, extraction, and archival.

### Channel Model Extension

The existing `providers` table needs a `channel_type` discriminator (`email` vs `sms`).  
For SMS channels:
- `extract_source`: always `body` (no subject in SMS)
- **Single header**: sender phone number only
- `provider_matchers` gains a `sender_phone` field

Nonce extraction modes (`auto`, `markers`, `start_length`) carry over unchanged.

### SMS Ingest Flow

The Android app calls a new REST endpoint:

```
POST /api/sms/ingest
{
  "sender": "+491234567890",
  "body": "Your GitHub code is 847291",
  "received_at": "2026-04-04T10:14:00+00:00"
}
```

The daemon processes this identically to an email ingest:
- Match → extract → store nonce, OR
- No match → store in `unmatched_sms`

### Unmatched SMS at the Daemon

Unmatched SMS appear in the existing unmatched list UI with:
- Visual `SMS` badge (vs `EMAIL`)
- **Dismiss** clears the daemon-side copy only; original SMS on the phone is untouched
- "Promote to channel" flow works the same as for email, but creates an SMS-channel provider

### Android App Capabilities (V1)

| Capability | Implementation |
|---|---|
| Mark SMS → forward to daemon | `POST /api/sms/ingest` — daemon routes or stores as unmatched |
| View Unmatched SMS at daemon | Existing unmatched UI + SMS discriminator |
| Dismiss copy ≠ delete original | Server-side only delete; no callback to phone |
| Configure nonce extraction at daemon | Existing channel config UI, SMS variant |
| Sender phone as the only header | `provider_matchers.sender_phone`, no subject |
| Android syncs only need-to-know data | `GET /api/configs` filtered to SMS-channel configs |
| Global activate/deactivate from phone | `POST /api/configs/<id>/activate` — affects all clients |
| Archive SMS copies at daemon | `var/archive/{user}/sms/{timestamp}_{sender}.json` |
| Manually funnel SMS into active config | `POST /api/sms/ingest` with optional `config_id` override |

### Deferred Items (Change #20)

Daemon `/auth/admin/users` to show long-lived session indicators (Chrome icon, Android icon per user) and a sessions management page with per-session logout. Deferred for later implementation.

---

## Concept V2 *(revised after first review)*

### Changes from V1

#### UI — SMS Tab & Forward Flow

The "mark SMS" and "manually funnel" actions are unified into a single flow:

1. Open **SMS tab** in the Android app (lists recent SMS)
2. Select an SMS, tap **Forward**
3. Choose:
   - **"Let the server choose"** — daemon attempts to match by sender; likely lands in Unmatched if no configured sender matches
   - **"Pick a configuration manually"** — shows only configurations that have at least one SMS channel; overrides sender matching; nonce extraction still happens at the daemon

For configuring routes and nonce extraction, the user can also enter a **manual sender phone number** (not limited to numbers already in the device's SMS history).

#### Authentication — Individual Tokens per Client

Same JWT flow as the Chrome extension, but each client (Chrome, Android) holds its own independent token. Tokens are not shared between clients. This also motivates Change #20 (session management UI per device) which is deferred.

#### SMS Reception — Auto-Forward

On SMS reception, the app forwards automatically with no user interaction required. A spooling mechanism for temporary network unavailability is a desirable plus (acceptable added complexity). The **forward-as-unmatched** and SMS list browsing features require `READ_SMS` permission.

#### Manual Funnel — SMS-Configured Channels Only

When manually funnelling an SMS into a configuration, the app shows only configurations that have at least one SMS-type channel. This is consistent with the fact that nonce extraction happens at the daemon using the channel's extraction rules.

#### Q4 — No Bidirectional Phone Flags

The Android app does not flag or decorate original SMS messages after forwarding. The app forwards and forgets. Dismissed copies at the daemon have no effect on the phone. Dropped entirely.

#### Archive Format

```
var/archive/{user}/sms/{timestamp}_{sender}.json
```

Content:
```json
{
  "sender": "+491234567890",
  "body": "Your GitHub code is 847291",
  "received_at": "2026-04-04T10:14:00+00:00"
}
```

Same retention policy as email archive (`archive_retention_d` in `noncey.conf`).

#### Phone Number Normalization (Client-Side)

Normalization is performed on the Android app before transmission:

- If the number is **8 digits or fewer**: treat as short code, send as-is
- If the number is **longer than 8 digits** and does **not** start with `+`:
  - Attempt to read the country code from the device's own SIM number
  - If unavailable: use a configurable country code in the app's settings
  - Prepend `+{countryCode}` (strip leading zeros from national format first)

### Revised Android App Capabilities (V2)

| Capability | Implementation |
|---|---|
| SMS Tab | List of recent SMS; select → Forward |
| Forward → server chooses | `POST /api/sms/ingest` (no config_id); daemon matches or stores as unmatched |
| Forward → pick config manually | `POST /api/sms/ingest` with `config_id`; shows SMS-channel configs only |
| Manual sender entry | Free-text input in configuration/routing setup |
| Auto-forward on reception | Background service; `RECEIVE_SMS` permission; spooling for offline as plus |
| SMS list browsing | `READ_SMS` permission |
| View SMS-channel configs | `GET /api/configs` filtered to SMS-channel configs |
| Activate/deactivate config | Global effect via daemon API; `POST /api/configs/<id>/activate|deactivate` |
| Archive at daemon | `var/archive/{user}/sms/{timestamp}_{sender}.json`, same retention |
| Phone number normalization | App-side; SIM country code or configurable fallback |
| Individual auth token | Same JWT flow; token independent from Chrome extension token |

### Deferred Items (Change #20)

Daemon `/auth/admin/users` to show long-lived session indicators per client type (Chrome icon, Android icon) and a sessions management page with per-session logout. Deferred for later implementation.

---

## Concept V3 *(revised after second review)*

### Changes from V2

#### Fire-and-Forget + Mark as Read

The Android app uses a fire-and-forget strategy for forwarding: once an SMS is transmitted to the daemon (or spooled), the app takes no further interest in the outcome. There is no callback, no flag, no decoration on the original SMS conversation view.

Exception: a forwarded SMS (whether auto-forwarded by sender matching or manually forwarded by the user) shall be marked as **Read** using the system-wide SMS read status that all SMS apps honour (`markMessageRead` via `ContentResolver`). This requires the `WRITE_SMS` or `MANAGE_SMS` permission on newer Android versions.

#### Spooling Parameters

If the device has no network connectivity at forwarding time, the app queues the SMS in a local spool (SQLite):

| Setting | Default | Configurable |
|---|---|---|
| Spool retention (max age before drop) | 10 minutes | Yes (app settings) |
| Retry interval | 5 seconds | Yes (app settings) |

Entries older than the retention window are silently discarded. Spooling is a best-effort plus feature; the primary path assumes active network connection.

#### Auto-Forward Scope

Auto-forward on SMS reception applies **only** to SMS whose sender matches a sender phone number in at least one active SMS-channel configuration. The app maintains a local cache of sender matchers (synced from `GET /api/configs`) to perform this check without a round-trip. SMS from unknown senders are not forwarded automatically; they appear in the SMS tab for manual action only.

#### Config Visibility on the App

The app shows the same configuration list as the daemon's `/auth` view, filtered to configs that have at least one SMS-type channel:

- **Private (owned) configurations** with ≥1 SMS channel — always shown; user can activate or deactivate
- **Subscribed public configurations** with ≥1 SMS channel — shown while subscribed; deactivation marks inactive immediately; removed from list on next full UI reload (unsubscribe semantics mirror the daemon)
- **Marketplace** is not present on the app; subscribing to public configs happens at the daemon only

#### SMS Tab Scope

The SMS tab shows **all SMS on the device** (requires `READ_SMS`). Its purpose is to let the user browse and choose which SMS to forward manually. Auto-forwarded SMS are not visually distinguished from others, consistent with fire-and-forget.

#### Nonce Delivery

The Android app is a **feeder only**. Extracted nonces are surfaced at the Chrome extension (and daemon UI) as usual. No nonce display on the phone for now.

### Revised Android App Capabilities (V3)

| Capability | Implementation |
|---|---|
| SMS Tab | Full device SMS list (`READ_SMS`); select → Forward |
| Forward → server chooses | `POST /api/sms/ingest` (no config_id); daemon matches or stores as unmatched |
| Forward → pick config manually | `POST /api/sms/ingest` with `config_id`; app shows SMS-channel configs only |
| Manual sender entry | Free-text input in routing/configuration setup |
| Auto-forward on reception | Background service (`RECEIVE_SMS`); sender must match active config; `WRITE_SMS` to mark Read |
| Mark forwarded SMS as Read | System-wide read flag via `ContentResolver`; applies to both auto and manual forwards |
| Spooling (offline) | Local SQLite queue; default 10 min retention, 5 sec retry; configurable |
| View SMS-channel configs | Private owned + subscribed public, filtered to SMS channels; same activation logic as daemon |
| Activate/deactivate config | Global effect via daemon API; unsubscribe = immediate deactivation + removal on reload |
| Archive at daemon | `var/archive/{user}/sms/{timestamp}_{sender}.json`, same retention as email |
| Phone number normalization | App-side; SIM country code or configurable fallback; short codes (≤8 digits) sent as-is |
| Individual auth token | Same JWT flow; token independent from Chrome extension token |
| Feeder-only nonce role | Nonces consumed at Chrome/daemon; no nonce display on phone |

### Deferred Items

- **Change #20**: Daemon `/auth/admin/users` — long-lived session indicators per client type (Chrome icon, Android icon) and per-session logout page.
- **Change #21**: Marketplace listing — visual channel-type indicators (email icon, SMS icon) per public configuration.

---

## Concept V4 *(final — all decisions closed)*

### Changes from V3

#### Ingest Endpoint Authentication

`POST /api/sms/ingest` requires a valid user JWT. No additional client-type scoping — any valid session token is sufficient, consistent with all other `/api/` endpoints.

#### Unmatched Items — Schema and UI

**SQL**: The existing `unmatched_emails` table is renamed to `unmatched_items` and gains a `channel_type` column (`email` | `sms`). The `subject` and `fwd_sender` columns become nullable (always NULL for SMS rows). Single table means single retention cleanup query and no duplicated logic.

```sql
-- migration sketch
ALTER TABLE unmatched_emails RENAME TO unmatched_items;
ALTER TABLE unmatched_items ADD COLUMN channel_type TEXT NOT NULL DEFAULT 'email'
    CHECK(channel_type IN ('email', 'sms'));
-- subject and fwd_sender were already nullable in practice; no change needed
```

**UI**: Single unified unmatched list at `/auth/unmatched` with a `SMS` / `EMAIL` badge per row. Filtering by type is a UI-level concern. "Promote to channel" flow is identical for both types; SMS promotion creates an SMS-channel provider. "Dismiss" on an SMS unmatched item deletes the daemon-side copy only — original SMS on phone is unaffected.

### Final Decisions Summary

| Topic | Decision |
|---|---|
| Ingest auth | Any valid JWT; no client-type restriction |
| Unmatched SQL | Unified `unmatched_items` table; `channel_type` discriminator; nullable `subject`/`fwd_sender` |
| Unmatched UI | Unified list with SMS/EMAIL badge; same promote and dismiss flows |
| Auto-forward scope | Senders matching active SMS-channel configs only; local cache of matchers on device |
| Mark as Read | System-wide SMS read flag after forward (auto or manual); `ContentResolver` |
| Spooling | SQLite local queue; 10 min retention (configurable); 5 sec retry (configurable) |
| Config list on app | Private owned + subscribed public, filtered to SMS-channel configs; no marketplace |
| Activate/deactivate | Global via daemon API; unsubscribe = immediate deactivate + removal on reload |
| Manual funnel | Shows SMS-channel configs only; overrides sender matching; daemon does extraction |
| SMS tab | All device SMS; `READ_SMS`; browse and manually forward |
| Archive format | `var/archive/{user}/sms/{timestamp}_{sender}.json`; same retention as email |
| Phone number norm | App-side; SIM country code or configurable; short codes ≤8 digits sent as-is |
| Auth token | Individual JWT per client; independent of Chrome extension token |
| Nonce delivery | Feeder only; nonces surfaced at Chrome/daemon; no display on phone |

### Deferred Items

- **Change #20**: Daemon `/auth/admin/users` — long-lived session indicators per client type (Chrome icon, Android icon) and per-session logout page.
- **Change #21**: Marketplace listing — visual channel-type indicators (email icon, SMS icon) per public configuration.
