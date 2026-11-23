
package walson.one.walsonChatManager.models;

import java.util.UUID;

public record MuteData(
        UUID uuid,
        String username,
        String reason,
        String mutedBy,
        long issuedAt,
        Long expiresAt
) {

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isExpired() {
        return expiresAt != null &&
                System.currentTimeMillis() >= expiresAt;
    }

    public long getRemainingTime() {
        if (isPermanent()) {
            return -1L;
        }

        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }
}
