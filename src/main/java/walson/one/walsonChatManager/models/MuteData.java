package walson.one.walsonChatManager.models;

import java.util.UUID;

/**
 * Represents an active mute. A pending-join mute has no expiry timestamp yet;
 * its configured duration starts when the player next joins the server.
 */
public record MuteData(
        UUID uuid,
        String username,
        String reason,
        String mutedBy,
        long issuedAt,
        Long expiresAt,
        boolean silentAnnouncement,
        boolean startsOnJoin,
        long durationMillis
) {

    public MuteData(
            UUID uuid,
            String username,
            String reason,
            String mutedBy,
            long issuedAt,
            Long expiresAt
    ) {
        this(uuid, username, reason, mutedBy, issuedAt, expiresAt,
                false, false, expiresAt == null ? -1L : expiresAt - issuedAt);
    }

    public MuteData(
            UUID uuid,
            String username,
            String reason,
            String mutedBy,
            long issuedAt,
            Long expiresAt,
            boolean silentAnnouncement
    ) {
        this(uuid, username, reason, mutedBy, issuedAt, expiresAt,
                silentAnnouncement, false,
                expiresAt == null ? -1L : expiresAt - issuedAt);
    }

    public boolean isPendingJoin() {
        return startsOnJoin;
    }

    public boolean isPermanent() {
        if (startsOnJoin) {
            return durationMillis < 0;
        }
        return expiresAt == null;
    }

    public boolean isExpired() {
        return !startsOnJoin
                && expiresAt != null
                && System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingTime() {
        if (isPermanent()) {
            return -1L;
        }

        if (startsOnJoin) {
            return Math.max(0L, durationMillis);
        }

        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
