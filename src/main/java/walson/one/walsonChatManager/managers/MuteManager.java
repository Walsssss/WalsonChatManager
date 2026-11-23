package walson.one.walsonChatManager.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.database.DatabaseManager;
import walson.one.walsonChatManager.models.MuteData;
import walson.one.walsonChatManager.models.MuteHistoryEntry;
import walson.one.walsonChatManager.utils.MessageUtils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MuteManager {

    private final WalsonChatManager plugin;
    private final DatabaseManager database;

    private final Map<UUID, MuteData> activeMutes = new ConcurrentHashMap<>();

    public MuteManager(WalsonChatManager plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void loadMutes() {
        activeMutes.clear();
        List<MuteData> expiredMutes = new ArrayList<>();

        try (PreparedStatement statement = database.getConnection()
                .prepareStatement("SELECT * FROM active_mutes");
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
                        expiresAt,
                        result.getInt("silent_announcement") != 0
                );

                if (mute.isExpired()) {
                    expiredMutes.add(mute);
                } else {
                    activeMutes.put(uuid, mute);
                }
            }

        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load mutes: " + exception.getMessage());
        }

        for (MuteData mute : expiredMutes) {
            expireRecord(mute, false);
        }

        plugin.getLogger().info("Loaded " + activeMutes.size() + " active mutes.");
    }

    public boolean mute(
            UUID uuid,
            String username,
            String reason,
            String staff,
            long duration
    ) {
        return mute(uuid, username, reason, staff, duration, false);
    }

    public boolean mute(
            UUID uuid,
            String username,
            String reason,
            String staff,
            long duration,
            boolean silentAnnouncement
    ) {
        if (duration == 0 || activeMutes.containsKey(uuid)) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long expiresAt;

        try {
            expiresAt = duration < 0 ? null : Math.addExact(now, duration);
        } catch (ArithmeticException exception) {
            return false;
        }

        MuteData mute = new MuteData(
                uuid, username, reason, staff, now, expiresAt,
                silentAnnouncement
        );

        Connection connection = database.getConnection();

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO active_mutes
                    (uuid, username, reason, muted_by, issued_at, expires_at, silent_announcement)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement history = connection.prepareStatement("""
                    INSERT INTO mute_history
                    (uuid, username, action, reason, staff, duration, timestamp, silent_announcement)
                    VALUES (?, ?, 'MUTE', ?, ?, ?, ?, ?)
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
                statement.setInt(7, silentAnnouncement ? 1 : 0);

                statement.executeUpdate();

                history.setString(1, uuid.toString());
                history.setString(2, username);
                history.setString(3, reason);
                history.setString(4, staff);
                history.setString(5, duration < 0 ? "PERMANENT" : String.valueOf(duration));
                history.setLong(6, now);
                history.setInt(7, silentAnnouncement ? 1 : 0);
                history.executeUpdate();

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
            plugin.getLogger().severe("Could not mute player: " + exception.getMessage());
            return false;
        }
    }

    public boolean unmute(UUID uuid, String reason, String staff) {
        MuteData mute = activeMutes.get(uuid);
        if (mute == null) return false;

        Connection connection = database.getConnection();

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM active_mutes WHERE uuid = ?"
            );
                 PreparedStatement history = connection.prepareStatement("""
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
            plugin.getLogger().severe("Could not unmute player: " + exception.getMessage());
            return false;
        }
    }

    public MuteData getMute(UUID uuid) {
        return activeMutes.get(uuid);
    }

    /**
     * Announces a staff-issued mute to Minecraft global chat.
     * Silent mutes remain stored in the database for Discord moderation
     * logging, but are intentionally omitted from public announcements.
     */
    public void announceMute(MuteData mute) {
        if (mute == null || mute.silentAnnouncement()) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "chat.mute-announcements.enabled", true
        )) {
            return;
        }

        String duration = mute.isPermanent()
                ? "Permanent"
                : walson.one.walsonChatManager.utils.TimeUtils
                        .formatDurationWords(mute.expiresAt() - mute.issuedAt());

        Map<String, String> placeholders = Map.of(
                "player", mute.username(),
                "staff", mute.mutedBy(),
                "duration", duration,
                "reason", mute.reason()
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            MessageUtils.send(
                    online,
                    plugin.getMessages(),
                    "mute.announcement",
                    placeholders
            );
        }

        plugin.getLogger().info(
                mute.username() + " was muted for " + duration
                        + " by " + mute.mutedBy() + ". Reason: "
                        + mute.reason()
        );
    }

    public boolean isMuted(UUID uuid) {
        MuteData mute = activeMutes.get(uuid);
        if (mute == null) return false;

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

        List<MuteData> expired = activeMutes.values().stream()
                .filter(mute -> mute.expiresAt() != null && mute.expiresAt() <= now)
                .toList();

        for (MuteData mute : expired) {
            removeExpiredMute(mute.uuid());
        }
    }

    private void removeExpiredMute(UUID uuid) {
        MuteData mute = activeMutes.get(uuid);
        if (mute == null || !mute.isExpired()) return;

        if (expireRecord(mute, true)) {
            activeMutes.remove(uuid, mute);
        }
    }

    private boolean expireRecord(MuteData mute, boolean notifyPlayer) {
        Connection connection = database.getConnection();

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement history = connection.prepareStatement("""
                    INSERT INTO mute_history
                    (uuid, username, action, reason, staff, duration, timestamp)
                    VALUES (?, ?, 'EXPIRE', ?, 'SYSTEM', NULL, ?)
                    """);
                 PreparedStatement delete = connection.prepareStatement(
                         "DELETE FROM active_mutes WHERE uuid = ?"
                 )) {

                history.setString(1, mute.uuid().toString());
                history.setString(2, mute.username());
                history.setString(3, "Mute expired");
                history.setLong(4, System.currentTimeMillis());
                history.executeUpdate();

                delete.setString(1, mute.uuid().toString());
                delete.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }

            if (notifyPlayer) {
                Player player = Bukkit.getPlayer(mute.uuid());
                if (player != null) {
                    MessageUtils.send(player, plugin.getMessages(), "mute.expired");
                }
            }

            return true;

        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not expire mute: " + exception.getMessage());
            return false;
        }
    }

    public UUID findPlayerUUID(String username) {
        String sql = """
                SELECT uuid FROM mute_history
                WHERE username = ? COLLATE NOCASE
                ORDER BY timestamp DESC LIMIT 1
                """;

        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return UUID.fromString(result.getString("uuid"));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not find mute history player: " + exception.getMessage());
        }

        return null;
    }

    public String findPlayerName(UUID uuid) {
        MuteData active = activeMutes.get(uuid);
        if (active != null) return active.username();

        try (PreparedStatement statement = database.getConnection().prepareStatement("""
                SELECT username FROM mute_history
                WHERE uuid = ? ORDER BY timestamp DESC LIMIT 1
                """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return result.getString("username");
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not find player name: " + exception.getMessage());
        }

        return "Unknown";
    }

    public int getMuteHistoryCount(UUID uuid) {
        try (PreparedStatement statement = database.getConnection().prepareStatement("""
                SELECT COUNT(*) AS total FROM mute_history
                WHERE uuid = ? AND action = 'MUTE'
                """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("total") : 0;
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not count mute history: " + exception.getMessage());
            return 0;
        }
    }

    public List<MuteHistoryEntry> getMuteHistory(UUID uuid, int limit, int offset) {
        List<MuteHistoryEntry> entries = new ArrayList<>();

        String sql = """
                SELECT h.id, h.uuid, h.username, h.reason, h.staff,
                       h.duration, h.timestamp,
                       (
                         SELECT later.action
                         FROM mute_history later
                         WHERE later.uuid = h.uuid
                           AND later.id > h.id
                           AND later.action IN ('UNMUTE', 'EXPIRE')
                         ORDER BY later.id ASC
                         LIMIT 1
                       ) AS resolution
                FROM mute_history h
                WHERE h.uuid = ? AND h.action = 'MUTE'
                ORDER BY h.timestamp DESC, h.id DESC
                LIMIT ? OFFSET ?
                """;

        MuteData active = activeMutes.get(uuid);

        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, Math.max(1, limit));
            statement.setInt(3, Math.max(0, offset));

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long timestamp = result.getLong("timestamp");
                    String resolution = result.getString("resolution");

                    String status;
                    if (active != null && active.issuedAt() == timestamp) {
                        status = "ACTIVE";
                    } else if ("UNMUTE".equalsIgnoreCase(resolution)) {
                        status = "UNMUTED";
                    } else if ("EXPIRE".equalsIgnoreCase(resolution)) {
                        status = "EXPIRED";
                    } else {
                        status = "ENDED";
                    }

                    entries.add(new MuteHistoryEntry(
                            result.getLong("id"),
                            UUID.fromString(result.getString("uuid")),
                            result.getString("username"),
                            result.getString("reason"),
                            result.getString("staff"),
                            result.getString("duration"),
                            timestamp,
                            status
                    ));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not load mute history: " + exception.getMessage());
        }

        return entries;
    }
}
