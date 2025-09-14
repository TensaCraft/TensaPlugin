package ua.co.tensa.config;

import org.simpleyaml.configuration.file.YamlConfiguration;
import ua.co.tensa.config.data.ConfigYAML;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ua.co.tensa.Tensa.database;

public class Config extends YamlConfiguration {

	private static YamlConfiguration config;
	public static DatabaseInitializer databaseInitializer;

	public static void initialise() {
		config = ConfigYAML.getInstance().getReloadedFile();
	}

	public static void reload() {
		initialise();
	}

	public static List<String> getModules() {
		Set<String> keys = config.getConfigurationSection("modules").getKeys(false);
		return new ArrayList<>(keys);
	}

	public static boolean getModules(String identifier) {
		return config.getConfigurationSection("modules").getBoolean(identifier);
	}

	public static String getLang() {
		return config.getString("language");
	}

	// Database
	public static boolean databaseEnable() {
		return config.getBoolean("database.enable");
	}
	public static String getDatabaseType() {
		return config.getString("database.type");
	}

	public static String getDatabaseName() {
		return config.getString("database.name");
	}

	public static String getDatabaseUser() {
		return config.getString("database.user");
	}

	public static String getDatabasePassword() {
		return config.getString("database.password");
	}

	public static String getDatabaseHost() {
		return config.getString("database.host");
	}

	public static int getDatabasePort() {
		return config.getInt("database.port");
	}

	public static boolean getSsl() {
		return config.getBoolean("database.use_ssl");
	}

	public static String getDatabaseTablePrefix() {
		return config.getString("database.table_prefix");
	}

	public static boolean useUUID() {
		return config.getBoolean("use_uuid");
	}

	public static void databaseInitializer(){
		if (Config.databaseEnable()){
			database = new Database();
			if (database.connect()) {
				databaseInitializer = new DatabaseInitializer(database);
				databaseInitializer.initializeTables();
			}
		}
	}
}
