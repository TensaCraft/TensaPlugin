# Discord Link Verification

Use this checklist after installing a verified build. It does not require a
proxy restart beyond the separately approved deployment procedure.

1. In Minecraft run `/discord link` and copy the generated code using the
   visible copy control.
2. In the configured Discord guild run `/link code:<code>` exactly once.
3. Confirm the Discord success embed appears and `/discord status` reports the
   linked Discord account in Minecraft.
4. Confirm the configured linked role is present and the Discord server
   nickname matches the Minecraft name (Discord's 32-character limit applies).
5. Disconnect and reconnect the player, then run `/discord status` again. Both
   events request an idempotent role/nickname reconciliation, so the stored link
   must remain available without generating another code.

Safe failure logs use a bounded reason instead of credentials or account IDs:

- `MISSING_PERMISSION`: the bot lacks nickname permission or is below the member
  in the Discord role hierarchy;
- `MEMBER_NOT_FOUND`: the linked Discord account is not a member of the
  configured guild or the member cache/API scope is stale;
- `GUILD_UNAVAILABLE`: JDA is not ready for the configured guild;
- `INVALID_NAME`: the Minecraft name could not produce a valid Discord nickname;
- `RATE_LIMITED` or `TRANSIENT_FAILURE`: Discord temporarily rejected the
  operation;
- `FAILED`: another permanent Discord API failure occurred.

Nickname failure is best-effort: it never rolls back an already committed link
or its successfully assigned role. Role assignment remains transactional with
link creation, as configured by `discord_ids.linked_role_id`.
