package ua.co.tensa.config.data;

public class ChatYAML extends BaseYAMLConfig {

    private static ChatYAML instance;

    private ChatYAML() {
        super("chats.yml");
    }

    public static ChatYAML getInstance() {
        if (instance == null) {
            instance = new ChatYAML();
        }
        return instance;
    }

    @Override
    protected void populateConfigFile() {
        yamlFile.setHeader(
                "Chat Manager \n" + "Placeholders: {player}, {server}, {message}"
        );

        yamlFile.setComment("global", "Global chat");
        setConfigValue("global.enabled", true);
        setConfigValue("global.alias", "!");
        setConfigValue("global.command", "g");
        yamlFile.setComment("global.permission", "If empty, everyone can use this chat and see the messages");
        setConfigValue("global.permission", "");
        setConfigValue("global.see_all", true);
        setConfigValue("global.format", "<dark_gray>[<gold>G</gold>]</dark_gray> <green>{player}</green> <gold>=></gold> <white>{message}</white>");

        yamlFile.setComment("staff", "Staff chat");
        setConfigValue("staff.enabled", true);
        setConfigValue("staff.alias", "@");
        setConfigValue("staff.command", "s");
        setConfigValue("staff.permission", "tensa.chat.staff");
        setConfigValue("staff.see_all", false);
        setConfigValue("staff.format", "<dark_gray>[<dark_red><bold>S</bold></dark_red>]</dark_gray> <aqua><bold>{server}</bold></aqua> <green><bold>{player}</bold></green> <gold><bold>=></bold></gold> <white><bold>{message}</bold></white>");

        yamlFile.setComment("alert", "Alert chat");
        setConfigValue("alert.enabled", true);
        setConfigValue("alert.alias", "");
        setConfigValue("alert.command", "alert");
        setConfigValue("alert.permission", "");
        setConfigValue("alert.see_all", true);
        setConfigValue("alert.format", "<dark_gray>[<dark_red>ALERT</dark_red>]</dark_gray> <white>{message}</white>");
    }
}
