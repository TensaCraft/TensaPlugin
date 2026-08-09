# Communications v2 migration notes

Communications v2 is an intentional reset, not an in-place value migration.
Read these notes before replacing a live TensaPlugin JAR.

## Automatic behavior

When either root/module communications file has no `config_version` or has a
version below `2`, startup/reload performs one transaction-like filesystem sequence:

1. Create `backups/communications-<UTC timestamp>/`.
2. Copy each existing `discord.yml`, `chats.yml` and legacy
   `discord/links.json` into that directory.
3. Compute and compare SHA-256 for every source and copy.
4. Atomically install clean schema-v2 seeds in `communications/`, then populate
   annotated defaults.
5. Remove legacy `links.json` only after its verified archive exists.

No old Discord ID, webhook setting, relay format, limit, manual setting or
account link is imported. If any copy or hash verification fails, original
files stay in place and clean v2 files are not installed. A schema version above
`2` is rejected without any file mutation.

An existing schema-v2 root `discord.yml` or `chats.yml` is backed up,
SHA-256-verified and relocated byte-for-byte to `communications/`. If the
module destination already exists, it wins; the obsolete root copy is still
archived before removal.

The separate `config.yml` module switch is migrated safely: an existing
`modules.communications` value wins. Otherwise old `chat-manager`, `chat` and
`discord` flags are OR-combined into `communications`, then removed.

## Data ownership after reset

- `communications/chats.yml`: `global`, `staff`, `alert`, `private`, `reply`
  and their formats.
- `communications/discord.yml`: bot/webhooks, IDs, relay, announcements,
  achievements, linking, `proxy_chat` and embeds. Advanced validated overrides
  are supported but omitted from clean public defaults.
- `<table_prefix>discord_links`: the sole persistent link source through
  `CoreStorageService`.
- `<table_prefix>discord_webhook_bindings`: managed route/webhook ID/token
  fingerprint metadata. It never stores the webhook URL or token.
- In-memory only: short-lived link codes, relay deduplication windows and
  runtime metrics.

If `storage.type` selects the external database, make sure it is reachable and
the configured user can create/use the link table. Otherwise use the local H2
backend. Do not manually copy legacy JSON rows into the database.

## Operator checklist

- Back up the old JAR and entire plugin directory independently of the automatic
  archive.
- Record hashes and permissions without displaying tokens, webhook URLs, keys
  or database passwords.
- Prepare clean v2 values from the generated annotated files. Prefer environment
  overrides for Discord secrets.
- Leave `proxy_chat.webhook.auto_create` disabled unless the bot has
  `MANAGE_WEBHOOKS` in each configured target channel. Missing permission safely
  keeps bot-message fallback.
- Keep `linking.guard`, announcements, nickname sync and the TensaProxy
  advancement producer disabled for the first receiver canary.
- Tell users that links must be recreated; preserve the verified JSON archive
  only for rollback/audit, not import.
- Validate `/discord link`, the clipboard button, Discord `/link`, JDBC
  persistence, webhook identity/avatar and `/tensainfo communications` before
  enabling the guard. A rising `clickable_urls` count proves that the plugin
  emitted URL click actions; remaining non-clickable behavior is client-side.

## Rollback boundary

Roll back producers before the receiver: return TensaProxy to TDE1, stop
AeroProxy, restore the previous TensaPlugin JAR and its matching legacy config
set, then start once. The new JDBC table can remain unused. Never combine an old
receiver with schema-v2 files or hot-replace the plugin inside a running proxy.
