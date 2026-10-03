# Documentation

## Operation and upgrades

- [Stability audit and rollout](PLUGIN_AUDIT.md) — verified fixes, test evidence,
  remaining limits and rollback.
- [Communications v2 migration](COMMUNICATIONS_V2_MIGRATION.md) — configuration
  ownership, verified archives and the legacy reset/relink boundary.
- [LibreLogin bridge removal](AUTH_BRIDGE_REMOVAL.md) — retirement notice and
  the backend auth-guard prerequisite.
- [Command scheduler](COMMAND_SCHEDULER.md) — scheduled command configuration.
- [Discord link verification](DISCORD_LINK_VERIFICATION.md) — operator checks
  for linking and role assignment.

## Integration contracts

- [Discord backend events](DISCORD_BACKEND_EVENTS.md) — achievement/death packets.
- [Legacy proxy command compatibility](PROXY_BRIDGE_COMPATIBILITY.md) — the
  separate opt-in console-command bridge, not an authentication bridge.

## Historical records

- [Communications implementation checklist](history/COMMUNICATIONS_V2_CHECKLIST.md)
  records previous work and superseded requirements. It is not the current spec.

For building and local testing, see the [project README](../README.md) and
[Velocity test tools](../.run/README.md).
