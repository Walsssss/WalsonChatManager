
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
                        expires_at INTEGER,
                        silent_announcement INTEGER NOT NULL DEFAULT 0,
                        starts_on_join INTEGER NOT NULL DEFAULT 0,
                        duration_millis INTEGER NOT NULL DEFAULT -1
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
                        timestamp INTEGER NOT NULL,
                        silent_announcement INTEGER NOT NULL DEFAULT 0
                    );
                """);

                // Every message matching a chat-filter rule is recorded once.
                // This is separate from mute_history because warnings and
                // blocked messages are violations even when no mute is issued.
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS chat_filter_violations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        username TEXT NOT NULL,
                        rule_id TEXT NOT NULL,
                        matched_word TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    );
                """);

                statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_chat_filter_violations_uuid
                    ON chat_filter_violations(uuid);
                """);
            }

            // Migrate databases created by earlier WalsonChatManager versions.
            addColumnIfMissing("active_mutes", "silent_announcement",
                    "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing("active_mutes", "starts_on_join",
                    "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing("active_mutes", "duration_millis",
                    "INTEGER NOT NULL DEFAULT -1");
            addColumnIfMissing("mute_history", "silent_announcement",
                    "INTEGER NOT NULL DEFAULT 0");

            plugin.getLogger().info("SQLite connected successfully.");
            return true;

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "SQLite connection failed: " + exception.getMessage()
            );

            return false;
        }
    }

    private void addColumnIfMissing(
            String table,
            String column,
            String definition
    ) throws SQLException {
        boolean exists = false;

        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }

        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "ALTER TABLE " + table + " ADD COLUMN "
                                + column + " " + definition
                );
            }
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
