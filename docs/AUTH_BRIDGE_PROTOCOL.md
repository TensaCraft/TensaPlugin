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
identity.

Example:

```yaml
allow_from:
  - aero
source_bindings:
  aero: aero
```

The backend creates the session UUID and 32-byte challenge. A valid challenge
uses sequence `0`, state `PENDING`, and an empty reason. Velocity replies with
the same player UUID, session UUID, challenge, and backend ID, plus a fresh
message UUID, fresh 24-byte nonce, and a sequence greater than zero.

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

Lookup failures remain fail-closed as `PENDING`.

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
