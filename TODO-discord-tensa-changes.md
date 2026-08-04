# Communications v2 implementation checklist

Status date: 2026-08-04. This replaces the obsolete value-preserving migration
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

- [x] TensaPlugin Java 25: clean test (151/151), dependency analysis without
  problems and clean package.
- [x] TensaProxy Java 21 toolchain: clean test build (45/45).
- [x] Secret scan of the final diff and implementation commit range.
- [x] Record final test totals and artifact SHA-256 values in the handoff.
- [ ] Live rollout/canary — intentionally blocked until separate operator
  approval; do not deploy, reload or restart automatically.
