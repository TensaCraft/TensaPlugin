# Discord backend event bridge

Velocity does not expose player advancement events. TensaPlugin therefore does
not synthesize them from proxy events. A backend plugin such as TensaProxy must
publish a typed plugin message after the backend has accepted the advancement.

## Channel and trust boundary

- Default channel: `tensa:discord_events`
- Configure it with `announcements.backend_bridge.channel` in `discord.yml`.
- The proxy accepts packets only when Velocity identifies the source as a
  `ServerConnection`.
- The backend name is taken from that connection. It is not accepted from the
  packet.
- The packet UUID and player name must match the player that owns the Velocity
  `ServerConnection`; mismatched packets are dropped.
- `announcements.servers.include` and `announcements.servers.exclude` are
  applied before decoding or publishing the event. `aero-auth` is excluded by
  default.

## Advancement packet v1

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

The proxy strips control characters, escapes Discord mentions and Markdown,
deduplicates the same backend/player/advancement key for the configured window,
and applies the shared bounded event queue and rate limit. The bridge registers
only while `announcements.advancements` is enabled and unregisters on module
disable or reload.
