# Discord backend event bridge

Velocity does not expose backend advancement or death events. TensaPlugin
therefore does not synthesize them from proxy events. A backend plugin such as
TensaProxy must publish a typed plugin message after the backend confirms the
event.

## Channel and trust boundary

- Default channel: `tensa:discord_events`
- The channel and transport bounds are internal protocol constants rather than
  public Discord configuration.
- The proxy accepts packets only when Velocity identifies the source as a
  `ServerConnection`.
- The backend name is taken from that connection. It is not accepted from the
  packet.
- The packet UUID and player name must match the player that owns the Velocity
  `ServerConnection`; mismatched packets are dropped.
- `announcements.servers.include` and `announcements.servers.exclude` are
  applied before decoding or publishing the event. `aero-auth` is excluded by
  default.

## Advancement packet v1 (TDE1)

All integers are big-endian. Text is strict UTF-8 and is prefixed by an unsigned
16-bit byte length.

| Field | Type | Limit |
| --- | --- | --- |
| Magic | `int32` | `0x54444531` (`TDE1`) |
| Event type | `uint8` | `1` for advancement |
| Player UUID MSB | `int64` | |
| Player UUID LSB | `int64` | |
| Player name | length + UTF-8 | 64 bytes; Minecraft name syntax |
| Advancement key | length + UTF-8 | 256 bytes; namespaced key syntax |
| Display title | length + UTF-8 | 1024 bytes; 200 Unicode code points |

The complete packet is limited to 2048 bytes and trailing bytes are rejected.
`DiscordBackendEventCodec.encodeAdvancement(...)` is the reference producer API.
Backend implementations should send the returned bytes on the configured
channel through the player connection that earned the advancement.

## Advancement packet v2 (TDE2)

TDE2 keeps the TDE1 identity fields and adds producer-side localization. It is
accepted on the same channel while TDE1 remains byte-compatible and supported.

| Field | Type | Limit |
| --- | --- | --- |
| Magic | `int32` | `0x54444532` (`TDE2`) |
| Event type | `uint8` | `1` for advancement |
| Player UUID MSB | `int64` | |
| Player UUID LSB | `int64` | |
| Player name | length + UTF-8 | 64 bytes; Minecraft name syntax |
| Advancement key | length + UTF-8 | 256 bytes; namespaced key syntax |
| Locale | length + UTF-8 | 16 bytes; lowercase `ll_cc` syntax |
| Localized title | length + UTF-8 | 1024 bytes; 200 Unicode code points |
| Description present | `uint8` | exactly `0` or `1` |
| Localized description | length + UTF-8 | only when present; 2048 bytes and 500 code points |

The complete TDE2 packet is limited to 4096 bytes. Invalid UTF-8, invalid
optional flags, truncation, unknown event types, and trailing bytes are rejected.
`DiscordBackendEventCodec.encodeAdvancementV2(...)` is the reference layout.
The localized description is passed to the typed advancement embed; TDE1 uses
an empty description.

## Death packet v2 (TDE2)

Death events use the same TDE2 envelope and channel. TDE1 remains advancement
only.

| Field | Type | Limit |
| --- | --- | --- |
| Magic | `int32` | `0x54444532` (`TDE2`) |
| Event type | `uint8` | `2` for player death |
| Player UUID MSB | `int64` | |
| Player UUID LSB | `int64` | |
| Player name | length + UTF-8 | 64 bytes; Minecraft name syntax |
| Locale | length + UTF-8 | 16 bytes; lowercase `ll_cc` syntax |
| Localized death message | length + UTF-8 | 2048 bytes and 1000 Unicode code points |

The producer must send Minecraft's final localized death component rather than
reconstructing combat text. This preserves modded messages, killer names and
item context. `DiscordBackendEventCodec.encodeDeathV2(...)` is the receiver's
reference layout. Empty messages, malformed UTF-8, invalid identity, truncation,
unknown types and trailing bytes are rejected.

The proxy strips control characters, escapes Discord mentions and Markdown,
deduplicates the same backend/player/event identity for a bounded window,
and applies the shared bounded event queue and rate limit. The bridge registers
while advancement or death embeds are active and unregisters on module disable
or reload. Death announcements are enabled with `embeds.death.enabled`.
