package ua.co.tensa.placeholders;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import ua.co.tensa.Message;
import ua.co.tensa.Tensa;
import ua.co.tensa.placeholders.providers.PAPIProxyBridgeProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PlaceholderManager {

    private static final Map<String, Function<Player, String>> custom = new ConcurrentHashMap<>();
    private static final Map<String, BiFunction<Player, String, String>> rawPrefixResolvers = new ConcurrentHashMap<>();
    private static final Map<String, BiFunction<Player, String, String>> anglePrefixResolvers = new ConcurrentHashMap<>();

    private static PlaceholderProvider papiProvider;

    public static void initialise() {
        registerDefaults();
        // lazy load providers
        papiProvider = new PAPIProxyBridgeProvider();
    }

    private static TagResolver buildCustomTagResolver(Player player) {
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, Function<Player, String>> e : custom.entrySet()) {
            String id = e.getKey();
            builder.resolver(TagResolver.resolver(id, (args, ctx) -> {
                String out = Optional.ofNullable(e.getValue()).map(f -> f.apply(player)).orElse("");
                return Tag.selfClosingInserting(net.kyori.adventure.text.Component.text(out));
            }));
            // Also support namespaced form <tensa_id> in fallback path
            String namespaced = "tensa_" + id;
            builder.resolver(TagResolver.resolver(namespaced, (args, ctx) -> {
                String out = Optional.ofNullable(e.getValue()).map(f -> f.apply(player)).orElse("");
                return Tag.selfClosingInserting(net.kyori.adventure.text.Component.text(out));
            }));
        }
        return builder.build();
    }

    public static void register(String key, Function<Player, String> resolver) {
        custom.put(key, resolver);
    }

    public static void unregister(String key) {
        if (key != null) custom.remove(key);
    }

    private static void registerDefaults() {
        // Standard plugin info placeholders (available as %tensa_*% and <tensa_*>)
        register("tensa_name", p -> safePluginName());
        register("tensa_version", p -> safePluginVersion());
        register("tensa_id", p -> safePluginId());
        register("tensa_authors", p -> safePluginAuthors());
    }

    private static String safePluginName() {
        try { return Tensa.pluginContainer != null ? Tensa.pluginContainer.getDescription().getName().orElse("TENSA") : "TENSA"; } catch (Throwable ignored) {}
        return "TENSA";
    }

    private static String safePluginVersion() {
        try { return Tensa.pluginContainer != null ? Tensa.pluginContainer.getDescription().getVersion().orElse("unknown") : "unknown"; } catch (Throwable ignored) {}
        return "unknown";
    }

    private static String safePluginId() {
        try { return Tensa.pluginContainer != null ? Tensa.pluginContainer.getDescription().getId() : "tensa"; } catch (Throwable ignored) {}
        return "tensa";
    }

    private static String safePluginAuthors() {
        try { return Tensa.pluginContainer != null ? String.join(", ", Tensa.pluginContainer.getDescription().getAuthors()) : ""; } catch (Throwable ignored) {}
        return "";
    }

    public static String resolveRaw(Player player, String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        // Custom placeholders in PAPI style: %key%
        for (Map.Entry<String, Function<Player, String>> e : custom.entrySet()) {
            String token = "%" + e.getKey() + "%";
            if (out.contains(token)) {
                out = out.replace(token, Optional.ofNullable(e.getValue()).map(f -> f.apply(player)).orElse(""));
            }
            // namespaced tensa_ form
            String token2 = "%tensa_" + e.getKey() + "%";
            if (out.contains(token2)) {
                out = out.replace(token2, Optional.ofNullable(e.getValue()).map(f -> f.apply(player)).orElse(""));
            }
        }
        // Apply raw prefix resolvers, e.g. %meta_key%
        for (Map.Entry<String, BiFunction<Player, String, String>> e : rawPrefixResolvers.entrySet()) {
            String prefix = e.getKey();
            BiFunction<Player, String, String> fn = e.getValue();
            out = replaceDelimited(out, "%" + prefix, "%", k -> Optional.ofNullable(fn.apply(player, k)).orElse(""));
        }
        // Delegate to PAPI provider if available
        if (papiProvider != null && papiProvider.isAvailable()) {
            out = papiProvider.resolveRaw(player, out);
        }
        return out;
    }

    public static Component resolveComponent(Player player, String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        // First resolve custom and PAPI-style into a raw string
        String raw = resolveRaw(player, input);

        // Replace our custom <tags>, then render with legacy/MiniMessage auto-detect
        String replaced = replaceAnglePlaceholders(player, raw);
        return Message.convert(replaced);
    }

    public static TagResolver getCustomTagResolver(Player player) {
        return buildCustomTagResolver(player);
    }

    // Async resolve that leverages PAPI async API when available
    public static java.util.concurrent.CompletableFuture<Component> resolveComponentAsync(Player player, String input) {
        if (input == null || input.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(Component.empty());
        }
        String raw = applyCustomPlaceholdersOnly(player, input);
        boolean mayHavePapi = raw.indexOf('%') >= 0;
        if (papiProvider instanceof PAPIProxyBridgeProvider papi && papi.isAvailable() && player != null && mayHavePapi) {
            return papi.formatComponentAsync(player, raw).thenApply(comp -> {
                // Serialize PAPI-resolved component back to MiniMessage so we can still resolve MiniPlaceholders tags
                String serialized = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(comp);
                String replaced = replaceAnglePlaceholders(player, serialized);
                return Message.convert(replaced);
            });
        }
        return java.util.concurrent.CompletableFuture.completedFuture(resolveComponent(player, input));
    }

    private static String replaceAnglePlaceholders(Player player, String input) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        java.util.function.Function<String, String> val = key -> {
            java.util.function.Function<Player, String> fn = custom.get(key);
            return fn != null ? java.util.Optional.ofNullable(fn.apply(player)).orElse("") : "";
        };
        // replace standard keys and namespaced variants
        for (String key : custom.keySet()) {
            out = out.replace("<" + key + ">", val.apply(key));
            out = out.replace("<tensa_" + key + ">", val.apply(key));
        }
        // replace registered angle prefix placeholders, e.g. <meta_key>
        for (Map.Entry<String, BiFunction<Player, String, String>> e : anglePrefixResolvers.entrySet()) {
            String prefix = e.getKey();
            BiFunction<Player, String, String> fn = e.getValue();
            out = replacePattern(out, "<" + prefix, ">", (k) -> Optional.ofNullable(fn.apply(player, k)).orElse(""));
        }
        return out;
    }

    private static String replacePattern(String input, String prefix, String suffix, java.util.function.Function<String, String> resolver) {
        String out = input;
        int idx = 0;
        while ((idx = out.indexOf(prefix, idx)) >= 0) {
            int end = out.indexOf(suffix, idx + prefix.length());
            if (end < 0) break;
            String token = out.substring(idx, end + suffix.length());
            String key = out.substring(idx + prefix.length(), end);
            String val = resolver.apply(key);
            out = out.replace(token, val);
            idx += val.length();
        }
        return out;
    }

    private static String applyCustomPlaceholdersOnly(Player player, String input) {
        String out = input;
        for (Map.Entry<String, Function<Player, String>> e : custom.entrySet()) {
            String token = "%" + e.getKey() + "%";
            if (out.contains(token)) {
                out = out.replace(token, Optional.ofNullable(e.getValue()).map(f -> f.apply(player)).orElse(""));
            }
            String token2 = "%tensa_" + e.getKey() + "%";
            if (out.contains(token2)) {
                out = out.replace(token2, Optional.ofNullable(e.getValue()).map(f -> f.apply(player)).orElse(""));
            }
        }
        // Apply raw prefix resolvers only (no PAPI)
        for (Map.Entry<String, BiFunction<Player, String, String>> e : rawPrefixResolvers.entrySet()) {
            String prefix = e.getKey();
            BiFunction<Player, String, String> fn = e.getValue();
            out = replaceDelimited(out, "%" + prefix, "%", (k) -> Optional.ofNullable(fn.apply(player, k)).orElse(""));
        }
        return out;
    }

    // Registration API for modules to contribute dynamic placeholders
    public static void registerRawPrefixResolver(String prefix, BiFunction<Player, String, String> resolver) {
        if (prefix != null && resolver != null) rawPrefixResolvers.put(prefix, resolver);
    }

    public static void unregisterRawPrefixResolver(String prefix) {
        rawPrefixResolvers.remove(prefix);
    }

    public static void registerAnglePrefixResolver(String prefix, BiFunction<Player, String, String> resolver) {
        if (prefix != null && resolver != null) anglePrefixResolvers.put(prefix, resolver);
    }

    public static void unregisterAnglePrefixResolver(String prefix) {
        anglePrefixResolvers.remove(prefix);
    }

    private static String replaceDelimited(String input, String prefix, String suffix, Function<String, String> resolver) {
        if (input == null || input.isEmpty()) return input;
        String out = input;
        int idx = 0;
        while ((idx = out.indexOf(prefix, idx)) >= 0) {
            int end = out.indexOf(suffix, idx + 1);
            if (end < 0) break;
            String token = out.substring(idx, end + suffix.length());
            String key = out.substring(idx + prefix.length(), end);
            String val = resolver.apply(key);
            out = out.replace(token, val);
            idx += Math.max(1, val.length());
        }
        return out;
    }
}
