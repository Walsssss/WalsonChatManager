
package walson.one.walsonChatManager.database;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            File databaseFile = new File(
                    plugin.getDataFolder(),
                    plugin.getConfig().getString("database.file", "mutes.db")
            );

            Class.forName("org.sqlite.JDBC");

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + databaseFile.getAbsolutePath()
            );

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("PRAGMA busy_timeout = 5000;");

                // Current active mutes
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS active_mutes (
                        uuid TEXT PRIMARY KEY,
                        username TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        muted_by TEXT NOT NULL,
                        issued_at INTEGER NOT NULL,
                        expires_at INTEGER
                    );
                """);

                // Complete moderation history
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS mute_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        username TEXT NOT NULL,
                        action TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        staff TEXT NOT NULL,
                        duration TEXT,
                        timestamp INTEGER NOT NULL
                    );
                """);
            }

            plugin.getLogger().info("SQLite connected successfully.");
            return true;

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "SQLite connection failed: " + exception.getMessage()
            );

            return false;
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void disconnect() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
            plugin.getLogger().info("SQLite disconnected.");
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "SQLite disconnect failed: " + exception.getMessage()
            );
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }
}
