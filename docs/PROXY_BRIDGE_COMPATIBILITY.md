# ProxyBridge Compatibility Mode

`proxy-bridge` preserves the legacy `token:command` plugin-message protocol for
explicit compatibility only. It is not the authentication bridge and does not
use the canonical `tensa:auth` wire format.

The module is disabled by default. Enabling it also requires an explicit,
validated configuration:

```yaml
compatibility_mode: true
channel: tensa:exec
token: replace-with-a-dedicated-secret
allow_from:
  - aero
log: true
```

Security requirements:

- `token` must be non-empty and dedicated to this bridge.
- The Velocity forwarding secret is never read or accepted.
- `allow_from` must contain at least one exact registered backend name.
- Empty allowlists, `*`, and `all` are rejected during module startup.
- Matching-channel events are marked handled before source or payload parsing.
- Messages are accepted only when the source is a Velocity
  `ServerConnection`.
- The token comparison is constant-time.

The payload remains the legacy UTF-8 `token:command` format. This compatibility
module intentionally does not add another wire protocol. Use the separate
LibreLogin auth bridge for signed authentication state synchronization.
