package ua.co.tensa.modules.discord;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import ua.co.tensa.config.model.YamlFileIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * One-time, idempotent migration from the separate chat and Discord modules to
 * the communications module. New-schema values always win over legacy values.
 */
public final class CommunicationsConfigMigration {
    private static final String LEGACY_CHAT_MODULE = "chat-manager";
    private static final String LEGACY_OLDER_CHAT_MODULE = "chat";
    private static final String LEGACY_DISCORD_MODULE = "discord";
    private static final String COMMUNICATIONS_MODULE = "communications";

    private CommunicationsConfigMigration() {
    }

    public static Result migrate(Path pluginDirectory) throws IOException {
        Path configPath = pluginDirectory.resolve("config.yml");
        Path chatsPath = pluginDirectory.resolve("chats.yml");
        Path discordPath = pluginDirectory.resolve("discord.yml");

        CommentedConfigurationNode app = loadIfPresent(configPath);
        CommentedConfigurationNode chats = loadIfPresent(chatsPath);
        CommentedConfigurationNode discord = loadIfPresent(discordPath);

        boolean hasLegacyModules = app != null && hasLegacyModuleState(app);
        boolean hasLegacyProxy = chats != null && !chats.node("proxy").virtual();
        if (!hasLegacyModules && !hasLegacyProxy) {
            return new Result(false);
        }

        if (discord == null) {
            discord = emptyNode(discordPath);
        }

        boolean discordChanged = migrateDiscordState(app, discord);
        discordChanged |= copyMissingProxySettings(chats, discord);

        boolean appChanged = migrateModuleState(app);
        boolean chatsChanged = removeLegacyProxy(chats);

        // Persist the destination before removing legacy source values. If a
        // later write fails, another startup can safely retry the migration.
        if (discordChanged) {
            save(discordPath, discord);
        }
        if (appChanged) {
            save(configPath, app);
        }
        if (chatsChanged) {
            save(chatsPath, chats);
        }
        return new Result(discordChanged || appChanged || chatsChanged);
    }

    private static boolean migrateDiscordState(
            CommentedConfigurationNode app,
            CommentedConfigurationNode discord
    ) throws IOException {
        if (app == null || !discord.node("enabled").virtual()) {
            return false;
        }
        Boolean legacyDiscord = booleanValue(app.node("modules", LEGACY_DISCORD_MODULE));
        if (legacyDiscord == null) {
            return false;
        }
        discord.node("enabled").set(legacyDiscord);
        return true;
    }

    private static boolean copyMissingProxySettings(
            CommentedConfigurationNode chats,
            CommentedConfigurationNode discord
    ) throws IOException {
        if (chats == null || chats.node("proxy").virtual()) {
            return false;
        }
        boolean changed = false;
        CommentedConfigurationNode source = chats.node("proxy");
        CommentedConfigurationNode destination = discord.node("proxy_chat");
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : source.childrenMap().entrySet()) {
            CommentedConfigurationNode target = destination.node(entry.getKey());
            if (!target.virtual()) {
                continue;
            }
            target.set(entry.getValue().raw());
            changed = true;
        }
        return changed;
    }

    private static boolean migrateModuleState(CommentedConfigurationNode app) throws IOException {
        if (app == null || !hasLegacyModuleState(app)) {
            return false;
        }
        CommentedConfigurationNode modules = app.node("modules");
        CommentedConfigurationNode communications = modules.node(COMMUNICATIONS_MODULE);
        if (communications.virtual()) {
            boolean enabled = Boolean.TRUE.equals(booleanValue(modules.node(LEGACY_CHAT_MODULE)))
                    || Boolean.TRUE.equals(booleanValue(modules.node(LEGACY_OLDER_CHAT_MODULE)))
                    || Boolean.TRUE.equals(booleanValue(modules.node(LEGACY_DISCORD_MODULE)));
            communications.set(enabled);
        }
        modules.node(LEGACY_CHAT_MODULE).set(null);
        modules.node(LEGACY_OLDER_CHAT_MODULE).set(null);
        modules.node(LEGACY_DISCORD_MODULE).set(null);
        return true;
    }

    private static boolean removeLegacyProxy(CommentedConfigurationNode chats) throws IOException {
        if (chats == null || chats.node("proxy").virtual()) {
            return false;
        }
        chats.node("proxy").set(null);
        return true;
    }

    private static boolean hasLegacyModuleState(CommentedConfigurationNode app) {
        CommentedConfigurationNode modules = app.node("modules");
        return !modules.node(LEGACY_CHAT_MODULE).virtual()
                || !modules.node(LEGACY_OLDER_CHAT_MODULE).virtual()
                || !modules.node(LEGACY_DISCORD_MODULE).virtual();
    }

    private static Boolean booleanValue(CommentedConfigurationNode node) {
        Object raw = node.raw();
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }

    private static CommentedConfigurationNode loadIfPresent(Path path) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        return YamlFileIO.load(YamlFileIO.loader(path));
    }

    private static CommentedConfigurationNode emptyNode(Path path) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        YamlConfigurationLoader loader = YamlFileIO.loader(path);
        return loader.load();
    }

    private static void save(Path path, CommentedConfigurationNode node) throws IOException {
        YamlFileIO.saveValidated(YamlFileIO.loader(path), node, path);
    }

    public record Result(boolean changed) {
    }
}
