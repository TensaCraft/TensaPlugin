package ua.co.tensa.modules.rcon.server;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.translation.GlobalTranslator;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

import static com.velocitypowered.api.permission.PermissionFunction.ALWAYS_TRUE;

public class RconCommandSource implements CommandSource {

	private final StringBuffer buffer = new StringBuffer();
	private final PermissionFunction permissionFunction = ALWAYS_TRUE;
	private final Locale locale;

	public RconCommandSource(ProxyServer server) {
		this.locale = Locale.getDefault();
	}

	private void addToBuffer(Component message) {
		Component rendered = GlobalTranslator.render(message, locale);
		if (rendered instanceof TranslatableComponent) {
			Component fallback = GlobalTranslator.render(message, Locale.ENGLISH);
			if (!(fallback instanceof TranslatableComponent)) {
				rendered = fallback;
			}
		}
		String txt = LegacyComponentSerializer.legacySection().serialize(rendered);
		txt = RconServerModule.stripMcColor(txt);
		if (buffer.length() != 0)
			buffer.append("\n");
		buffer.append(txt);
	}

	@Override
	public void sendMessage(@NotNull Identity source, @NotNull Component message) {
		addToBuffer(message);
	}

	@Override
	public void sendMessage(@NonNull Component message) {
		addToBuffer(message);
	}

	@Override
	public @NonNull Tristate getPermissionValue(@NonNull String permission) {
		return this.permissionFunction.getPermissionValue(permission);
	}

	public String flush() {
		String result = buffer.toString();
		buffer.setLength(0);
		return result;
	}
}
