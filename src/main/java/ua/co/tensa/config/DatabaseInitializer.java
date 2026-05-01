package ua.co.tensa.config;

public class DatabaseInitializer {

    private final Database database;

    public DatabaseInitializer(Database database) {
        this.database = database;
    }

    public void initializeTables() {
//        createPlayerTimeTable();
    }
    public boolean createPlayerTimeTable() {
        String sql = """
            id INT PRIMARY KEY AUTO_INCREMENT,
            name VARCHAR(255),
            uuid VARCHAR(255),
            play_time BIGINT
            """;
        return database.createTable("player_times", sql);
    }
}
