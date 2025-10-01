package ua.co.tensa.config;

public class DatabaseInitializer {

    private final Database database;

    public DatabaseInitializer(Database database) {
        this.database = database;
    }

    public void initializeTables() {
//        createPlayerTimeTable();
    }
    public void createPlayerTimeTable() {
        String sql = """
            id INT PRIMARY KEY AUTO_INCREMENT,
            name VARCHAR(255),
            uuid VARCHAR(255),
            play_time BIGINT
            """;
        database.createTableAsync("player_times", sql);
    }
}
