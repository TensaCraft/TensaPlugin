# Communications v2 implementation checklist

Status date: 2026-08-09. This replaces the obsolete value-preserving migration
proposal. The approved v2 contract is a verified archive followed by clean
defaults and user relinking.

## TensaPlugin

- [x] Merge chat and Discord lifecycle ownership into module id
  `communications`, title `Communications`.
- [x] Remove runtime `chat-manager`/`chat`/`discord` module entries and safely
  fold their root enable flags into `modules.communications`.
- [x] Add `/tensareload <module-id>|all`, completion and module-scoped
  permissions without replacing auth sessions or connected players.
- [x] Validate communications candidates before atomic replacement; retain the
  old runtime when validation or activation fails.
- [x] Reject wrong YAML value types instead of silently applying Java defaults.
- [x] Keep one JDA runtime, listener, slash registrar, retry scheduler,
  reply-deletion scheduler and post-link worker across repeated reloads.
- [x] Reconcile and verify guild-scoped `/link` after Ready, Resume and recreated
  JDA sessions; coalesce concurrent attempts and log safe failure classes.
- [x] Defer matching interactions immediately and enforce one process-local bot
  lease to prevent duplicate consumption/Error 10062.
- [x] Show `/discord link` code with a visible `COPY_TO_CLIPBOARD` control whose
  payload is only the raw code; test the real localized template.
- [x] Merge missing bundled language keys into runtime locale files without
  overwriting manual translations.
- [x] Add schema-v2 verified archive/reset; reject future schemas and never
  import legacy `links.json`.
- [x] Store links transactionally through `CoreStorageService` in
  `<prefix>discord_links`, with bounded two-way index and specific conflicts.
- [x] Add bounded once-only post-link announcement and best-effort nickname
  synchronization without link rollback.
- [x] Restore Minecraft→Discord chat formatting to webhook-based delivery
  (with player avatar + in-game name display) as the preferred path.
- [x] Investigate why chat formatting is sometimes bypassed (example `pm`):
  - validate `to_format`/`from_format` parsing and null/default substitution for all
    chat types (`pm`, `global`, etc.);
  - fix relay path so format is always applied to every chat route using `chats.yml`
    definitions and not only legacy/system-only chat handlers;
  - add regression test for a command-style/private (`pm`) chat and raw-text
    passthrough prevention.
- [x] Investigate why URLs are not clickable in chat:
  - trace message rendering path in plugin-only relay flow;
  - verify no stripping/escaping of link-like tokens occurs before send;
  - check whether plugin changes message style components affecting `Component` URL click actions;
  - add diagnostics to distinguish plugin formatting issues from upstream modpack chat behavior.
- [x] Add `proxy_chat.webhook.auto_create` with safe runtime checks for
  `MANAGE_WEBHOOKS` and fallback rules when auto-create is unavailable.
- [x] Add webhook provisioning flow: create/fetch webhook on startup or first use,
  rotate ID/token binding in storage and recover from revoked/invalidated tokens.
- [x] Add minimal Discord webhook config contract and remove non-essential/system
  tuning fields from public `discord.yml`; keep only operationally needed options.
- [x] Move all Communications module configuration files into a module subdirectory
  (mirroring other modules), and update loader/lookup paths accordingly so module
  defaults/examples/docs are co-located with module-owned assets.
- [x] Investigate size/regression in runtime resource usage:
  - capture startup vs live heap baselines;
  - audit cache/map growth (`links`, achievements, locale cache, message queues);
  - check for listener/task retention and stale scheduled jobs;
  - add guarded object caps/TTL and close leaking channels/services;
  - add periodic resource-usage telemetry with alert thresholds.
- [x] Add dedicated modular scheduler service:
  - support job definitions with condition predicates (online, channel, link status,
    command/route availability, cooldown checks);
  - support per-job delay, interval, retry count, max attempts, backoff, timeout,
    jitter and dead-letter behavior;
  - support pause/resume, cancellation, idempotent dedupe keys and scoped queues;
  - expose admin-configured runtime defaults + per-command overrides;
  - reuse scheduler for Discord link tasks, announcements, cleanup and recovery jobs.
- [x] Move shared relay ownership to `discord.yml` `proxy_chat`; leave only
  Minecraft channel definitions in `chats.yml`.
- [x] Add bidirectional link guard (`only|except`), local Minecraft delivery,
  rate-limited Discord feedback/deletion and echo prevention.
- [x] Add validated typed embeds, bounded delivery pipelines, definite-only
  fallback/retry rules and safe communications diagnostics.
- [x] Add strict TDE1/TDE2 receiver with localized optional descriptions.
- [x] Audit and fix auth alias-transfer freezes and stale route binding.
- [x] Preserve fail-closed auth while accepting bounded fresh challenge retries.
- [x] Document findings, migration, rollout and rollback.

## TensaProxy

- [x] Retry unanswered initial and renewal auth challenges with fresh replay
  material and a maximum five-second interval.
- [x] Generate SHA-1-verified pinned `uk_ua`/`en_us` vanilla catalogs during the
  NeoForge build.
- [x] Load bounded active-mod language catalogs with preferred locale, English
  and safe component-text fallback.
- [x] Produce strict TDE2 packets while retaining byte-compatible TDE1 tests and
  rolling receiver compatibility.

## Final gates

- [x] TensaPlugin Java 25: clean test (168/168), dependency analysis without
  problems and clean package.
- [x] TensaProxy Java 21 toolchain: clean test build (45/45).
- [x] Secret scan of the final diff and implementation commit range.
- [x] Record final test totals and artifact SHA-256 values in the handoff.
- [ ] Live rollout/canary — intentionally blocked until separate operator
  approval; do not deploy, reload or restart automatically.
