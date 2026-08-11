# Tensa Auth Bridge Cross-Compatibility Contract

## Canonical Source

The protocol authority is the TensaProxy repository:

- `TensaProxy/bridge-core`
- `TensaProxy/docs/PROTOCOL.md`

The local package `ua.co.tensa.authbridge.protocol` is a temporary source-level
mirror used while `bridge-core` is not available as a published dependency. Its
public frame, codec, enums, constants, HMAC input, and security behavior must
remain byte-for-byte compatible with the canonical implementation. Once a
published `bridge-core` artifact is available, replace the local package rather
than maintaining two evolving codecs.

## Transport And Direction

- Channel: `tensa:auth`
- Protocol: `1.0`
- Backend to Velocity: `CHALLENGE`
- Velocity to backend: `AUTH_STATE`
- Maximum frame: 4096 bytes

Velocity marks every matching plugin message as handled before parsing. It only
accepts frames from a `ServerConnection` whose registered Velocity server name
is in `allow_from`. `source_bindings` binds that source name to exactly one
signed `backendId`; packet data cannot select or override its trusted source
identity. Multiple registered server aliases may map to the same signed
`backendId`, but every alias must be listed in both `allow_from` and
`source_bindings`.

Example:

```yaml
allow_from:
  - aero-auth
  - aeronautics
source_bindings:
  aero-auth: aero
  aeronautics: aero
heartbeat_interval_seconds: 10
authorization_lease_seconds: 30
```

The auth bridge never subscribes to or mutates LibreLogin lobby/limbo server
selection events. `source_bindings` identifies which cryptographic backend is
allowed to speak for each Velocity route; two routes sharing one backend ID are
not interchangeable routing destinations. LibreLogin remains the sole owner of
moving an unauthenticated player to auth and an authenticated player back to
gameplay. This is essential for offline accounts: keeping a gameplay connection
when LibreLogin selected auth leaves the backend gate locked and appears as a
frozen player.

The obsolete `suppress_same_backend_alias_reconnect` key is ignored when it is
present in an older config and should be removed. Duplicate-login prevention
must be solved by assigning distinct actual backend endpoints or by LibreLogin's
own routing configuration, never by overriding an explicit authentication
route.

Velocity records the final `ServerPreConnectEvent` target for a bounded handoff
window. During that window it accepts a valid challenge only from the selected
new route and rejects the previous connection, even if Velocity has not yet
updated `Player.getCurrentServer()`. After `ServerPostConnectEvent`, the handoff
record is removed and exact current-connection binding resumes. This closes the
auth-to-gameplay race without treating aliases as interchangeable or weakening
UUID, HMAC, expiry, replay, backend-ID, session, or challenge validation.

Backend adapters bind logout cleanup to the concrete auth session created for
that player connection. A late logout from a replaced connection cannot remove
the replacement session or clear its freeze state. This is required when a
same-endpoint alias transition produces `duplicate_login`: UUID-only cleanup can
otherwise delete the new session after its challenge was already issued and
leave the new player permanently locked with no session timeout owner.

The backend creates the session UUID and 32-byte challenge. A valid challenge
uses sequence `0`, state `PENDING`, and an empty reason. Velocity replies with
the same player UUID, session UUID, challenge, and backend ID, plus a fresh
message UUID, fresh 24-byte nonce, and a sequence greater than zero.

For every online player with an active challenge binding, Velocity reconciles
the LibreLogin state and publishes a fresh `AUTH_STATE` every
`heartbeat_interval_seconds`. The default is 10 seconds and validation requires
a positive value. `authorization_lease_seconds` defaults to 30 seconds and
accepts `5..300`. The configured heartbeat must fit at least twice inside the
lease (`heartbeat_interval_seconds * 2 <= authorization_lease_seconds`), which
also guarantees that it is strictly shorter than the lease. Each heartbeat uses
a fresh message ID and nonce and advances the session sequence monotonically.
Sessions without an active challenge do not trigger heartbeat lookups or
publications.

These periodic frames are required lease renewals, not duplicate transition
notifications. Protocol `1.0` has no acknowledgement or separate heartbeat
message, and the NeoForge backend extends its authorization lease only after it
accepts a fresh signed `AUTH_STATE`. Removing unchanged heartbeat frames would
therefore lock and disconnect an otherwise authorized player when the lease
expires.

Challenge responses, heartbeat renewals, and explicit post-connect resyncs are
always eligible to publish. Delayed login reconciliation and LibreLogin state
callbacks publish only when the resolved state differs from the last
successfully published state for the active challenge binding. A failed send is
not recorded as published and remains eligible for a later retry. A new backend
challenge resets the publication state and receives an immediate response,
which preserves reconnect and backend-restart resynchronization.

An unanswered backend challenge may be retried with the same player, session,
and challenge binding but a fresh message ID, nonce, time window, and signature.
Velocity accepts that retry idempotently and advances its response sequence;
an exact packet replay is still rejected. This covers both initial login and
authorization-lease renewal without allowing a dropped packet to leave the
player frozen until timeout.

When `log_transitions` is enabled, the first successful state publication for a
challenge and later successful state changes are logged. Unchanged heartbeat
renewals and resync frames are intentionally not logged.

LibreLogin 0.24.0 exposes no logout/revoke or 2FA-transition event. The bridge
subscribes to `authenticated`, `wrongPassword`, and `premiumLoginSwitch` for
low-latency reconciliation where possible, while the heartbeat remains the
required authority for revoke and 2FA transitions. Module reload closes and
unsubscribes all state API handlers before opening and scanning the new runtime.

## Binary Layout

All integers use Java `DataOutputStream` big-endian encoding.

| Field | Encoding |
| --- | --- |
| magic | `int`, `0x54415042` |
| major, minor | unsigned byte each |
| message type | unsigned byte: `1=CHALLENGE`, `2=AUTH_STATE` |
| auth state | unsigned byte: `1=PENDING`, `2=AWAITING_SECOND_FACTOR`, `3=AUTHORIZED`, `4=REVOKED` |
| flags | unsigned byte, currently `0` |
| message ID | two `long` values |
| player UUID | two `long` values |
| session UUID | two `long` values |
| sequence | `long` |
| issued at | epoch milliseconds, `long` |
| expires at | epoch milliseconds, `long` |
| nonce | unsigned-byte length plus exactly 24 bytes |
| challenge | unsigned-byte length plus exactly 32 bytes |
| backend ID | unsigned-short UTF-8 byte length plus bytes |
| reason | unsigned-short UTF-8 byte length plus bytes |
| signature | unsigned-byte length plus exactly 32 HMAC bytes |

The signature length and signature bytes are omitted from the canonical
unsigned bytes used as HMAC-SHA256 input. The bridge key is a dedicated Base64
secret containing at least 32 decoded bytes. HMAC comparison is constant-time.

## State Mapping

LibreLogin state maps as follows:

| LibreLogin result | AuthState |
| --- | --- |
| not authorized, not waiting for 2FA | `PENDING` |
| waiting for 2FA | `AWAITING_SECOND_FACTOR` |
| authorized and not waiting for 2FA | `AUTHORIZED` |
| explicit future revocation signal | `REVOKED` |

Lookup failures and null results remain fail-closed as `PENDING`; they never
reuse a previously authorized state for a heartbeat response.

Premium and offline players use the same protocol binding. TensaProxy signs the
UUID visible on the backend and Velocity requires it to equal the transport
player UUID. With Velocity modern forwarding these UUIDs must already match;
an observed mismatch is a forwarding/identity configuration failure and is
rejected rather than papered over with a premium/offline exception.

## Golden Vector

Both repositories must test the same fixture and expected hexadecimal payload.
The TensaPlugin fixture is
`src/test/resources/authbridge/golden-auth-state-v1.hex`. Any protocol change
must update the canonical TensaProxy fixture, this fixture, and both protocol
documents in the same coordinated change.

Fixture:

```text
key=0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20
major=1
minor=0
type=AUTH_STATE
state=AUTHORIZED
messageId=11111111-2222-3333-4444-555555555555
playerId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
sessionId=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb
sequence=7
issuedAt=1800000000000
expiresAt=1800000010000
nonce=0a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2021
challenge=28292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4041424344454647
backendId=aero-test
reason=authenticated
```

Expected signed frame:

```text
54415042010002030011111111222233334444555555555555aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb0000000000000007000001a3185c5000000001a3185c7710180a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20212028292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464700096165726f2d74657374000d61757468656e74696361746564209f5af557a395d88cf506ca13af4f19deb7fd65377ef8d175dfa25b58ce71651d
```
