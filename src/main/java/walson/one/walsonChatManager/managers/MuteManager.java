
package walson.one.walsonChatManager.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.database.DatabaseManager;
import walson.one.walsonChatManager.models.MuteData;
import walson.one.walsonChatManager.utils.MessageUtils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    private final WalsonChatManager plugin;
    private final DatabaseManager database;

    private final Map<UUID, MuteData> activeMutes =
            new ConcurrentHashMap<>();

    public MuteManager(
            WalsonChatManager plugin,
            DatabaseManager database
    ) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadMutes() {
        activeMutes.clear();

        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(
                             "SELECT * FROM active_mutes"
                     );
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                UUID uuid = UUID.fromString(result.getString("uuid"));

                long expiration = result.getLong("expires_at");
                Long expiresAt = result.wasNull() ? null : expiration;

                MuteData mute = new MuteData(
                        uuid,
                        result.getString("username"),
                        result.getString("reason"),
                        result.getString("muted_by"),
                        result.getLong("issued_at"),
                        expiresAt
                );

                if (mute.isExpired()) {
                    deleteExpiredRecord(uuid);
                    continue;
                }

                activeMutes.put(uuid, mute);
            }

            plugin.getLogger().info(
                    "Loaded " + activeMutes.size() + " active mutes."
            );

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not load mutes: " + exception.getMessage()
            );
        }
    }

    public boolean mute(
            UUID uuid,
            String username,
            String reason,
            String staff,
            long duration
    ) {
        if (duration == 0 || activeMutes.containsKey(uuid)) {
            return false;
        }

        long now = System.currentTimeMillis();

        Long expiresAt;

        try {
            expiresAt = duration < 0
                    ? null
                    : Math.addExact(now, duration);
        } catch (ArithmeticException exception) {
            return false;
        }

        MuteData mute = new MuteData(
                uuid,
                username,
                reason,
                staff,
                now,
                expiresAt
        );

        Connection connection = database.getConnection();

        try {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement statement =
                             connection.prepareStatement("""
                                INSERT INTO active_mutes
                                (uuid, username, reason, muted_by, issued_at, expires_at)
                                VALUES (?, ?, ?, ?, ?, ?)
                                """);
                     PreparedStatement history =
                             connection.prepareStatement("""
                                INSERT INTO mute_history
                                (uuid, username, action, reason, staff, duration, timestamp)
                                VALUES (?, ?, 'MUTE', ?, ?, ?, ?)
                                """)) {

                    statement.setString(1, uuid.toString());
                    statement.setString(2, username);
                    statement.setString(3, reason);
                    statement.setString(4, staff);
                    statement.setLong(5, now);

                    if (expiresAt == null) {
                        statement.setNull(6, Types.BIGINT);
                    } else {
                        statement.setLong(6, expiresAt);
                    }

                    statement.executeUpdate();

                    history.setString(1, uuid.toString());
                    history.setString(2, username);
                    history.setString(3, reason);
                    history.setString(4, staff);
                    history.setString(
                            5,
                            duration < 0 ? "PERMANENT" : String.valueOf(duration)
                    );
                    history.setLong(6, now);

                    history.executeUpdate();
                }

                connection.commit();
                activeMutes.put(uuid, mute);
                return true;

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not mute player: " + exception.getMessage()
            );
            return false;
        }
    }

    public boolean unmute(
            UUID uuid,
            String reason,
            String staff
    ) {
        MuteData mute = activeMutes.get(uuid);

        if (mute == null) {
            return false;
        }

        Connection connection = database.getConnection();

        try {
            connection.setAutoCommit(false);

            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     "DELETE FROM active_mutes WHERE uuid = ?"
                             );
                     PreparedStatement history =
                             connection.prepareStatement("""
                                INSERT INTO mute_history
                                (uuid, username, action, reason, staff, duration, timestamp)
                                VALUES (?, ?, 'UNMUTE', ?, ?, NULL, ?)
                                """)) {

                    statement.setString(1, uuid.toString());
                    statement.executeUpdate();

                    history.setString(1, uuid.toString());
                    history.setString(2, mute.username());
                    history.setString(3, reason);
                    history.setString(4, staff);
                    history.setLong(5, System.currentTimeMillis());

                    history.executeUpdate();
                }

                connection.commit();
                activeMutes.remove(uuid);

                return true;

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not unmute player: " + exception.getMessage()
            );
            return false;
        }
    }

    public MuteData getMute(UUID uuid) {
        return activeMutes.get(uuid);
    }

    public boolean isMuted(UUID uuid) {
        MuteData mute = activeMutes.get(uuid);

        if (mute == null) {
            return false;
        }

        if (mute.isExpired()) {
            removeExpiredMute(uuid);
            return false;
        }

        return true;
    }

    public Collection<MuteData> getActiveMutes() {
        return Collections.unmodifiableCollection(activeMutes.values());
    }

    public void expireMutes() {
        long now = System.currentTimeMillis();

        List<UUID> expired = activeMutes.values()
                .stream()
                .filter(mute -> mute.expiresAt() != null
                        && mute.expiresAt() <= now)
                .map(MuteData::uuid)
                .toList();

        for (UUID uuid : expired) {
            removeExpiredMute(uuid);
        }
    }

    private void removeExpiredMute(UUID uuid) {
        MuteData mute = activeMutes.get(uuid);

        if (mute == null || !mute.isExpired()) {
            return;
        }

        try {
            deleteExpiredRecord(uuid);

            activeMutes.remove(uuid, mute);

            Player player = Bukkit.getPlayer(uuid);

            if (player != null) {
                MessageUtils.send(
                        player,
                        plugin.getMessages(),
                        "mute.expired"
                );
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not expire mute: " + exception.getMessage()
            );
        }
    }

    private void deleteExpiredRecord(UUID uuid) throws SQLException {
        try (PreparedStatement statement =
                     database.getConnection().prepareStatement(
                             "DELETE FROM active_mutes WHERE uuid = ?"
                     )) {

            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }
}
