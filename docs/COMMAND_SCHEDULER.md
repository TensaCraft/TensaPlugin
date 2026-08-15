# Command Scheduler

The optional `scheduler` module runs recurring commands as the Velocity console.
Its configuration is generated at `plugins/tensa/scheduler/config.yml` when the
module is first enabled. The generated example is disabled and executes nothing
until explicitly enabled.

Enable or disable the whole module in the root `config.yml`:

```yaml
modules:
  scheduler: true
```

## Configuration

```yaml
config_version: 1
tasks:
  random-tips:
    enabled: true
    initial_delay: 1m
    interval: 10m..20m
    mode: random
    commands:
      - "alert <gold>Welcome to Aeronautics!</gold>"
      - "alert <yellow>Read the rules with /rules.</yellow>"

  hourly-maintenance:
    enabled: true
    initial_delay: 5m
    interval: 1h
    mode: all
    commands:
      - "rcon all save-all"
      - "alert <gray>Hourly maintenance completed.</gray>"

  rotating-status:
    enabled: true
    initial_delay: 30s
    interval: 5m
    mode: round_robin
    commands:
      - "status public"
      - "status staff"
```

Each task has only five keys:

- `enabled`: whether this named task is active.
- `initial_delay`: delay before the first occurrence. `0s` runs immediately.
- `interval`: delay after each occurrence. A range such as `10m..20m` chooses a
  new random delay for every occurrence.
- `mode`: `all` executes every command in order; `random` executes one random
  command; `shuffle` executes every command once in random order;
  `round_robin` executes the next command on each occurrence.
- `commands`: console commands. A leading `/` is accepted and removed.

Durations support `ms`, `s`, `m`, `h`, and `d`. Recurring intervals must be at
least one second and no delay may exceed 365 days. Task IDs use lowercase Latin
letters, numbers, `_`, or `-`. Fixed implementation bounds prevent unbounded
task and command lists; those technical bounds are intentionally not exposed in
the file.

Global Tensa placeholders such as `%tensa_name%`, `%tensa_version%`,
`<tensa_name>`, and `<tensa_version>` are resolved before dispatch. Scheduled
commands have no player context, so player-specific PAPI placeholders are left
unchanged. MiniMessage remains part of the command argument and is rendered by
the receiving Tensa command, not by the scheduler itself.

Commands execute through Velocity's console command manager. To run a command on
a backend server, call a proxy-side bridge command such as `rcon <server> <command>`
with the RCON manager configured; the scheduler does not impersonate a backend
console.

## Safe reload

Use:

```text
/tensa reload scheduler
```

The permission is `tensa.reload.scheduler` or the broader `tensa.reload`. YAML
syntax, schema version, task keys, durations, modes and commands are validated
before lifecycle replacement. Invalid configuration leaves the previous runtime
and its jobs active. A successful reload closes every old timer/worker before the
new set starts, so repeated reloads do not duplicate executions.

The command first re-reads the root `modules.scheduler` flag. If the flag is
`true` while the runtime is currently disabled because an earlier startup or
validation failed, targeted reload validates `scheduler/config.yml` and attempts
to enable it. If the flag is `false`, only Scheduler is disabled and reported as
disabled. A failed enable leaves the module disabled and reports failure instead
of claiming that configuration was applied.

This targeted reload does not reload Communications, Discord, authentication,
storage, connected players or auth sessions.
