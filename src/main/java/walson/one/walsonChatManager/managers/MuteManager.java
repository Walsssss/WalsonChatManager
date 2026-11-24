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
                        result.getInt("silent_announcement") != 0,
                        result.getInt("starts_on_join") != 0,
                        result.getLong("duration_millis")
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
                silentAnnouncement, false, duration
        );

        Connection connection = database.getConnection();

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO active_mutes
                    (uuid, username, reason, muted_by, issued_at, expires_at, silent_announcement, starts_on_join, duration_millis)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
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
                statement.setLong(8, duration);

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
                Player target = Bukkit.getPlayer(uuid);
                if (target != null && plugin.getSoundManager() != null) {
                    plugin.getSoundManager().play(target,
                            walson.one.walsonChatManager.managers.SoundManager.SoundEvent.MUTE);
                }
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

    /**
     * Creates a mute for an offline player. Its timer does not begin until
     * activatePendingMute() is called when that player next joins.
     */
    public boolean mutePendingUntilJoin(
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
        MuteData mute = new MuteData(
                uuid, username, reason, staff, now, null,
                silentAnnouncement, true, duration
        );

        Connection connection = database.getConnection();
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO active_mutes
                    (uuid, username, reason, muted_by, issued_at, expires_at,
                     silent_announcement, starts_on_join, duration_millis)
                    VALUES (?, ?, ?, ?, ?, NULL, ?, 1, ?)
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
                statement.setInt(6, silentAnnouncement ? 1 : 0);
                statement.setLong(7, duration);
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
            plugin.getLogger().severe("Could not create pending offline mute: " + exception.getMessage());
            return false;
        }
    }

    /**
     * Starts an offline player's mute timer at their first join after the
     * mute was issued. Returns the activated mute, or null if none was pending.
     */
    public MuteData activatePendingMute(UUID uuid) {
        MuteData pending = activeMutes.get(uuid);
        if (pending == null || !pending.isPendingJoin()) {
            return null;
        }

        long now = System.currentTimeMillis();
        Long expiresAt = null;
        if (pending.durationMillis() >= 0) {
            try {
                expiresAt = Math.addExact(now, pending.durationMillis());
            } catch (ArithmeticException exception) {
                plugin.getLogger().severe("Could not calculate offline mute expiry for " + pending.username());
                return null;
            }
        }

        MuteData activated = new MuteData(
                pending.uuid(), pending.username(), pending.reason(),
                pending.mutedBy(), now, expiresAt,
                pending.silentAnnouncement(), false, pending.durationMillis()
        );

        Connection connection = database.getConnection();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE active_mutes
                    SET issued_at = ?, expires_at = ?, starts_on_join = 0
                    WHERE uuid = ? AND starts_on_join = 1
                    """);
                 PreparedStatement history = connection.prepareStatement("""
                    UPDATE mute_history SET timestamp = ?
                    WHERE uuid = ? AND action = 'MUTE' AND timestamp = ?
                    """)) {

                update.setLong(1, now);
                if (expiresAt == null) {
                    update.setNull(2, Types.BIGINT);
                } else {
                    update.setLong(2, expiresAt);
                }
                update.setString(3, uuid.toString());
                if (update.executeUpdate() == 0) {
                    connection.rollback();
                    return null;
                }

                history.setLong(1, now);
                history.setString(2, uuid.toString());
                history.setLong(3, pending.issuedAt());
                history.executeUpdate();
                connection.commit();
                activeMutes.put(uuid, activated);
                return activated;

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not activate pending mute for " + pending.username() + ": " + exception.getMessage());
            return null;
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
                Player target = Bukkit.getPlayer(uuid);
                if (target != null && plugin.getSoundManager() != null) {
                    plugin.getSoundManager().play(target,
                            walson.one.walsonChatManager.managers.SoundManager.SoundEvent.UNMUTE);
                }
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
                : walson.one.walsonChatManager.utils.TimeUtils.formatDurationWords(
                        mute.isPendingJoin()
                                ? mute.durationMillis()
                                : mute.expiresAt() - mute.issuedAt()
                );

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

    /**
     * Sends an additional moderation alert to staff when an automatic
     * anti-spam mute is issued. Public announcements are handled separately
     * by announceMute().
     */
    public void notifyStaffMute(MuteData mute) {
        if (mute == null) {
            return;
        }

        String duration = mute.isPermanent()
                ? "Permanent"
                : walson.one.walsonChatManager.utils.TimeUtils.formatDurationWords(
                        mute.isPendingJoin()
                                ? mute.durationMillis()
                                : mute.expiresAt() - mute.issuedAt()
                );

        Map<String, String> placeholders = Map.of(
                "player", mute.username(),
                "staff", mute.mutedBy(),
                "duration", duration,
                "reason", mute.reason()
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("walsonchatmanager.mute.notify")) {
                MessageUtils.send(
                        online,
                        plugin.getMessages(),
                        "mute.staff-notification",
                        placeholders
                );
            }
        }
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
                    if (plugin.getSoundManager() != null) {
                        plugin.getSoundManager().play(player,
                                walson.one.walsonChatManager.managers.SoundManager.SoundEvent.UNMUTE);
                    }
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

    /**
     * Returns the number of anti-spam warning records currently stored
     * across all players.
     */
    public int getAllAntiSpamWarningCount() {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "SELECT COUNT(*) AS total FROM anti_spam_warnings");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt("total") : 0;
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not count global anti-spam warnings: " + exception.getMessage()
            );
            return 0;
        }
    }

    /**
     * Deletes accumulated anti-spam warning records for every player.
     * This does not remove chat-filter violations or active mutes.
     *
     * @return number of warning records removed, or -1 if the reset failed
     */
    public int resetAllAntiSpamWarnings() {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "DELETE FROM anti_spam_warnings")) {
            return statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not reset global anti-spam warnings: " + exception.getMessage()
            );
            return -1;
        }
    }

    /**
     * Records one delivered anti-spam warning and returns the player's
     * warning count inside the supplied rolling time window.
     * The count survives server restarts.
     */
    public int recordAntiSpamWarning(
            UUID uuid,
            String username,
            long resetWindowMillis
    ) {
        if (uuid == null || username == null || resetWindowMillis <= 0L) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - resetWindowMillis;
        Connection connection = database.getConnection();

        try {
            // Remove this player's expired warnings so the counter resets
            // to zero once the configured period has elapsed.
            try (PreparedStatement cleanup = connection.prepareStatement("""
                    DELETE FROM anti_spam_warnings
                    WHERE uuid = ? AND timestamp < ?
                    """)) {
                cleanup.setString(1, uuid.toString());
                cleanup.setLong(2, cutoff);
                cleanup.executeUpdate();
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO anti_spam_warnings (uuid, username, timestamp)
                    VALUES (?, ?, ?)
                    """)) {
                insert.setString(1, uuid.toString());
                insert.setString(2, username);
                insert.setLong(3, now);
                insert.executeUpdate();
            }

            try (PreparedStatement count = connection.prepareStatement("""
                    SELECT COUNT(*) AS total
                    FROM anti_spam_warnings
                    WHERE uuid = ? AND timestamp >= ?
                    """)) {
                count.setString(1, uuid.toString());
                count.setLong(2, cutoff);

                try (ResultSet result = count.executeQuery()) {
                    return result.next() ? result.getInt("total") : 0;
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not record anti-spam warning for "
                            + username + ": " + exception.getMessage()
            );
            return 0;
        }
    }

    /**
     * Records one chat-filter violation for a matched chat message.
     * A single message is counted once, even if its rule has multiple actions.
     */
    public void recordChatFilterViolation(
            UUID uuid,
            String username,
            String ruleId,
            String matchedWord
    ) {
        if (uuid == null || username == null || ruleId == null || matchedWord == null) {
            return;
        }

        try (PreparedStatement statement = database.getConnection().prepareStatement("""
                INSERT INTO chat_filter_violations
                (uuid, username, rule_id, matched_word, timestamp)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, ruleId);
            statement.setString(4, matchedWord);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not record chat-filter violation for "
                            + username + ": " + exception.getMessage()
            );
        }
    }

    /**
     * Returns the player's lifetime number of messages matched by the
     * chat filter, including warnings, blocked messages, and filter mutes.
     */
    public int getChatFilterViolationCount(UUID uuid) {
        if (uuid == null) {
            return 0;
        }

        try (PreparedStatement statement = database.getConnection().prepareStatement("""
                SELECT COUNT(*) AS total
                FROM chat_filter_violations
                WHERE uuid = ?
                """)) {
            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("total") : 0;
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe(
                    "Could not count chat-filter violations: " + exception.getMessage()
            );
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
