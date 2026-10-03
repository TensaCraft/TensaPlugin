# TensaPlugin 3.1 stability audit

Audit date: **2026-10-03**. Baseline: `715dfe3`, **205 tests passed**
on Java 25.0.2 before changes.

This pass reviewed every runtime module plus core lifecycle, storage, events,
metadata, placeholders, configuration, text rendering, command ownership,
dependencies and packaging. Findings below distinguish regression-tested fixes
from operational limitations. It is not a claim of zero defects or a live
Discord/production acceptance test.

## Platform and removed integration

- Compile API: **Velocity 4.2.0**. Java **25** remains required.
- Provided Netty **4.2.18.Final**, Gson **2.14.0**, Adventure **5.2.0** and
  Configurate **4.2.0** match the released proxy's dependency catalog.
- Runtime verification targets release **4.2.0 build 30** and current
  **4.2.1-SNAPSHOT build 36**, resolved from PaperMC on the audit date.
- LibreLogin Auth Bridge, its service provider, API dependency, authentication
  protocol and obsolete tests/fixture are removed. The root flag is pruned;
  existing user-owned `auth-bridge/` files remain untouched and unread.
- Discord linking, chat, achievements/deaths and opt-in `proxy-bridge` command
  compatibility are independent and remain supported.
- LuckPerms is declared as an optional plugin dependency so its API is available
  before the existing placeholder provider initializes.

Primary platform references:
[PaperMC build catalog](https://fill.papermc.io/v3/projects/velocity),
[Velocity plugin development](https://docs.papermc.io/velocity/dev/creating-your-first-plugin/),
[4.2.0 runtime dependencies](https://github.com/PaperMC/Velocity/blob/c10b49255447694ad42c3c189d7a3f4ee3e73e5e/gradle/libs.versions.toml).

## Confirmed issues and corrections

| Area / severity | Reproducible problem | Correction |
| --- | --- | --- |
| Root config — high | Invalid YAML was recovered into another file during reload; invalid module flags silently changed behavior. | Read-only preflight, strict scalar/flag validation, separately loaded candidate and volatile publication. Rejected input and previous in-memory settings remain intact. |
| Lifecycle — high | Full reload, targeted reload and shutdown did not share the same coordination lock. Late queued reload could recreate resources after shutdown. | One lifecycle lock covers these paths; shutdown rejects subsequent reloads. Concurrent lifecycle regression uses real public entry points. |
| Command ownership — high | Registration overwrote another plugin's alias; cleanup could remove an alias that a different plugin had since taken over. | Check all aliases before registering; unregister only metadata still owned by this registration. |
| Database — high | A connection exception replayed arbitrary SQL, including writes that might already have committed, and replaced the pool borrowed by core storage. | No ambiguous SQL replay or pool replacement; Hikari handles broken connections. Closed databases reject late queued work. |
| User persistence — high | Multi-worker execution reordered login, disconnect and metadata operations. | Bounded FIFO execution preserves submission order. Existing JDBC mutation serialization means extra writer threads gave no useful write parallelism. |
| User metadata — high | Pending loads overwrote newer sets/deletes, treated partial maps as complete and revived caches after disconnect. | Explicit loaded state, pending mutations and identity-checked publication; forgotten/closed loads cannot restore a cache. |
| Connection events — high | Old login completions or disconnects acted on a replacement connection with the same UUID. | Track the actual Player connection; stale callbacks cannot preload metadata, run first-join commands or clear current-session state. |
| Worker shutdown — high | Accepted operations discarded by forced shutdown left callers' futures pending forever. | Resolve/cancel outstanding operations and reject work after retirement; scoped worker tests cover forced shutdown. |
| Scheduler — high | Cancellation did not interrupt an active worker; timeout could start another attempt while an interruption-ignoring task still ran. | Publish worker/timeout handles together, cancel queued/active work and retain the execution slot until the real task exits. Conditions run on the bounded worker and failures are contained. |
| HTTP — high | Blocking send and HttpClient callbacks shared a saturated fixed pool, preventing network progress. | Separate transport execution from the bounded request workers; terminate the owned HttpClient during shutdown. |
| Request callbacks — high | Responses from a retired configuration could execute its console commands after reload. | Generation checks at request admission, completion and scheduled response handling. |
| Queue persistence — high | In-memory deletion preceded durable deletion; failed SQL lost entries until restart. | Persist deletion before publishing the mutation and serialize admissions/dispatch. Capacity is enforced by the manager, delay arithmetic is checked, UUID targets cannot fall back to a namesake account. |
| RCON — high | An empty pre-auth response was accepted as authentication, leaving the actual auth response to corrupt the next command result. | Consume and validate both auth packets, including failure and request IDs. Idle sockets are closed by the handler. |
| Discord linking — high | Startup reconciliation could re-grant a role after unlink. A JDBC cleanup exception after commit reported failure and could trigger wrong role compensation. | Serialize reconciliation with link mutations; preserve committed link/unlink outcomes and cache state after cleanup failure. |
| Discord lifetime — high | Recovery could publish a runtime after deactivation; reconnect/registration callbacks could overwrite closed state; failed storage initialization leaked a webhook transport. | Coordinate shutdown with recovery and gateway activation, reject stale callbacks, initialize storage before allocating transports, terminate owned HTTP resources. |
| Chat policy — medium | Native relay ignored denied/filtered events and the configured send permission. | Observe the final allowed message and channel permission at late listener priority without cancelling signed chat. |
| Playtime — medium | Immediately completed/rejected lookups removed their own ConcurrentHashMap entry while it was still being computed, leaving futures unresolved. | Publish pending lookups before attaching completion callbacks. |
| Metadata permissions — medium | Tab completion disclosed another user's metadata key names without admin permission. | Apply the same target permission to completion as command execution. |
| Legacy command bridge — high | Closing the bridge zeroed its secret but a captured callback could authenticate against the now-zero token. | Closed state rejects every subsequent message. |
| Placeholders — medium | Registered resolvers were evaluated for unrelated text and could run twice for one template. | Lazy, one-pass resolution for actual tokens; preserves async provider fallback. |
| Storage validation — medium | SQL prefixes were unchecked and read failures became empty results that were cached as valid data. | Validate identifiers before creating pools; propagate safe failures without raw SQL/values. |
| Localization — medium | Language changes retained the old singleton file; English synchronization filled Ukrainian keys with English text. | Rebind by locale/path, fill supported catalogs with their own defaults, preserve manual values and reject corrupt translations without replacement. |
| Diagnostics/build — medium | Generic exception text could carry configuration or command values; package pruned test reports. | Safe failure-class diagnostics; preserve build/test evidence and bound local test JVM memory. |

New behavior is covered by failing-before/passing-after tests where a defect was
reproduced. Broader permission, migration, content-preservation and shutdown
checks are retained as acceptance tests rather than presented as bug reproductions.

## Performance and resource ownership

- No new periodic production jobs or configuration knobs were introduced.
- Internal scheduled work, HTTP requests, user-data writes, Discord work and
  RCON execution keep bounded admission. Cancellation does not create parallel
  replacement work for a task that has ignored interruption.
- Root configuration publishes a complete candidate rather than modifying
  live fields one by one. Reload serialization also prevents overlapping I/O
  bursts and resource replacement.
- Localization refresh and config validation occur on startup/reload paths.
  Minecraft text continues to use the single `TextPipeline`.
- Core user data uses a single bounded FIFO worker to preserve mutation order.
  This is a correctness tradeoff, not a throughput benchmark claim.
- SQL, Discord REST and network/socket work retain finite timeouts. No network
  credentials or message bodies are added to diagnostics.
- Maven tests and local smoke processes have explicit memory bounds; this pass
  observed a Windows native-memory allocation failure before tests, then reran
  the checks under bounded JVM settings.

## Verification record

- Java **25.0.2**, Maven **3.9.14**: `mvn -B clean verify dependency:analyze`
  completed successfully: **239 tests, 0 failures, 0 errors, 0 skipped**.
  Dependency analysis reported **No dependency problems found**.
  This change adds **61 regression/acceptance tests** and removes **27 tests**
  for the retired auth bridge (baseline 205, final 239).
- `.run/smoke-velocity.ps1 -SkipBuild -VelocityVersion 4.2.0` — **passed**
  with the official release build **30**.
- `.run/smoke-velocity.ps1 -SkipBuild -VelocityVersion 4.2.1-SNAPSHOT` — **passed**
  with official build **36** (`7fc49913`). Both used Java 25.0.2.
- Packaged `Tensa.jar`: **19,899,096 bytes**, plugin version **3.1.0**.
  SHA-256: `280b7a2f3f7fe9422619555428559d393a3af4e5680d139ed0187c6a974d5508`.
- JAR inspection confirmed no retired auth classes/LibreLogin dependency and no
  bundled Velocity API, Adventure or Netty classes. Verification wrote test
  reports under `target/surefire-reports` and logs under `.run/audit-*.log`.
  These generated files are not versioned and may be archived during local cleanup.
- Changed source and staged diff passed credential-pattern checks without printing
  secret values. This is a targeted scan, not proof that every possible secret
  format is detectable.
- An independent read-only review of `715dfe3..161c92d` found no concrete
  introduced correctness/security regressions in the root lifecycle, config,
  localization, database and command-ownership changes or checked cross-module
  lock paths. Deferred operational limitations below remain explicit.
- Verified artifact staged on Finland as
  `/var/lib/calagopus/codex/Aeronautics/mainMods/TensaPlugin-3.1.0-161c92d.jar`.
  Remote SHA-256 and byte size match the above; mode is `0644`; the temporary
  upload file is absent. No live JAR/config replacement, reload or restart.

The smoke harness uses localhost, strips Discord credentials, disables telemetry,
loads local modules, checks MiniMessage components and configuration generation,
performs five Scheduler and five Communications reloads plus a full reload, checks
for duplicated scheduler ticks and retired auth artifacts, and verifies shutdown.

## Remaining operational limits

1. Live Discord credentials were not used. Guild permissions, role hierarchy,
   reconnect and real delivery still need an operator canary. Discord REST role
   changes and SQL cannot form a single atomic transaction; ambiguous network
   outcomes remain observable failures rather than a guarantee of delivery.
2. The Discord bot lease and link cache are process-local. One runtime must own
   the bot; multiple proxies sharing mutable link state are not a supported
   distributed-cache consistency model.
3. Command queue delivery remains at-most-once: durable removal precedes external
   command acceptance. A crash or command failure in that interval can lose work.
   Cross-proxy exactly-once execution requires a separate durable claim/ack design.
   `tensa.queue` authorizes console-command execution and must be trusted.
4. Thread interruption is cooperative. A job that ignores interruption can occupy
   its bounded worker until it returns; the plugin must not forcibly kill JVM threads.
5. RCON multipart response heuristics cannot promise arbitrary remote-server
   protocol behavior. Real remote RCON servers need a canary beyond the local
   socket/authentication regressions.
6. After an unclean proxy crash, persisted online-session timestamps can include
   downtime in playtime. Blindly resetting all rows could affect another proxy
   sharing the database; a session-owner/lease migration is a separate change.
7. Database/storage connection configuration changes still require a controlled
   proxy restart. Full reload is ordered, not one distributed transaction across
   every independent module and external service.
8. HTTP response bodies and text-reader files have no byte-size cap. Treat their
   configured endpoints/files as trusted. Text-reader work still uses Velocity's
   scheduler rather than an owned bounded worker; bounded streaming reads would
   be a separate resource-hardening change.
9. Metadata loaded for offline administrative queries has no TTL/size cap;
   disconnects clear normal online-session caches. Optimistic metadata writes are
   not durability acknowledgements. Safe outer SQL messages retain original
   exception causes for debugging, so do not publish raw driver stack traces.
10. After publishing the updated default branch on 2026-10-03, GitHub reported
    four open dependency advisories for bundled MariaDB Connector/J **3.5.7**
    (three medium, one low), with **3.5.9** listed as the first patched version.
    See [Dependabot alerts](https://github.com/TensaCraft/TensaPlugin/security/dependabot).
    The Maven dependency-analysis success above is not a vulnerability scan.
    A driver update and JDBC verification remain required; the documentation-only
    cleanup did not rebuild or replace the already published v3.1.0 JAR.

## Safe rollout and rollback

1. Keep the tested JAR in Finland
   `/var/lib/calagopus/codex/Aeronautics/mainMods` with its SHA-256. Uploading there
   does not install it or restart a game/proxy process.
2. Verify no backend still depends on the retired signed auth lease. If one does,
   disable/remove its TensaProxy auth guard first. See
   [auth retirement notes](AUTH_BRIDGE_REMOVAL.md).
3. Back up the installed Tensa JAR and plugin data. In an explicitly authorized
   maintenance window, stop the actual Velocity process, replace only the
   receiver JAR, then start it. Do not hot-swap the JAR.
4. Confirm module status, enabled commands, storage, Discord role/link operations,
   chat permissions, backend achievements/deaths and normal player server changes.
5. Roll back by stopping the proxy and restoring the previous JAR and configuration
   backup. User auth files are retained; no auth database/player records are deleted
   by this release. No database schema migration was required for these audit fixes.

Production installation/restart is outside this source-audit turn. Historical
Communications-v2 and auth implementation findings are retained in Git history.
