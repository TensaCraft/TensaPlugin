# Retired LibreLogin Auth Bridge

Removed in TensaPlugin 3.1 (2026-10-03).

TensaPlugin no longer contains the LibreLogin API dependency, module provider,
listeners, tasks, session registry, HMAC/replay implementation or `tensa:auth`
plugin channel. It does not authenticate players or grant backend authorization
leases.

## Existing installations

- `modules.librelogin-auth-bridge` is pruned as an unsupported module key when
  root configuration is loaded. All supported module enable flags are preserved.
- Existing `auth-bridge/config.yml` and secret files remain untouched and unread.
  Operators may archive them separately; the update does not delete user files.
- A backend still running the old TensaProxy auth guard must have that guard
  disabled or removed **before** this receiver update. Otherwise it can keep
  waiting for authorization and freeze/kick players. Updating the Velocity
  plugin alone cannot disable code in a different backend process.
- Backend configuration, LibreLogin player data, and live services are not
  modified by this source change.

Discord linking and the advancement/death event receiver do not depend on
LibreLogin and remain supported. The optional `proxy-bridge` module is a
separate trusted console-command compatibility channel; it is not an auth
replacement.

The former wire specification and test fixtures remain recoverable from Git
history at commit `715dfe3`. Do not configure that protocol for the current
plugin. See [the current audit](PLUGIN_AUDIT.md) for verification and rollout.
