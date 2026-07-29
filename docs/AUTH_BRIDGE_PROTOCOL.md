# Tensa Auth Bridge Protocol v1

The protocol is loader-neutral and is intended to be shared by the Tensa
Velocity plugin and future NeoForge/Bukkit backend adapters.

## Transport

- Minecraft plugin message channel: `tensa:auth/v1`
- Maximum payload: 2048 bytes
- Byte order: big-endian
- Authentication: HMAC-SHA256 with a dedicated secret of at least 32 bytes
- The final 32 bytes are the HMAC over every preceding byte.

The bridge secret must not reuse the Velocity forwarding secret. A matched
channel is always handled by the proxy before validation so auth payloads are
never forwarded to a client or another backend.

## Message Layout

| Field | Encoding |
| --- | --- |
| version | signed 32-bit integer; current value `1` |
| type | unsigned byte: `1=QUERY`, `2=STATE` |
| player UUID | two signed 64-bit UUID halves |
| session UUID | two signed 64-bit UUID halves |
| backend challenge length | unsigned 16-bit integer |
| backend challenge | UTF-8, maximum 512 bytes |
| sequence | signed 64-bit positive integer |
| issuedAt | signed 64-bit Unix epoch milliseconds |
| expiresAt | signed 64-bit Unix epoch milliseconds |
| state | unsigned byte: `0=LOCKED`, `1=AUTHORIZED` |
| MAC | 32-byte HMAC-SHA256 |

## Semantics

- A proxy connection always starts with a new session UUID and `LOCKED`.
- An unsolicited `STATE` publication uses the proxy session UUID and an empty
  challenge.
- A backend `QUERY` uses either the current session UUID or the zero UUID for
  initial discovery, and a random challenge of at least 16 bytes.
- A query carries `LOCKED` as its state. The proxy replies with `STATE`, the
  current proxy session UUID, and the same backend challenge.
- Sequences are strictly increasing per peer, player, session, and message
  type. Repeated or lower sequences are rejected while the replay entry lives.
- Messages outside the configured TTL and clock-skew window are rejected.
- The backend must remain locked until it receives a fresh, valid `STATE`.

## Golden Vector

Secret, interpreted as UTF-8:

```text
0123456789abcdef0123456789abcdef
```

Message:

```text
version=1
type=STATE
player=00112233-4455-6677-8899-aabbccddeeff
session=10213243-5465-7687-98a9-bacbdcedfe0f
challenge=backend-01
sequence=42
issuedAt=1700000000000
expiresAt=1700000010000
state=AUTHORIZED
```

Encoded payload, hexadecimal:

```text
000000010200112233445566778899aabbccddeeff102132435465768798a9bacbdcedfe0f000a6261636b656e642d3031000000000000002a0000018bcfe568000000018bcfe58f10012d5edf50b5591145552123a42415958be101c7f3d530f450c8dbb5381a4a06a5
```
