
package walson.one.walsonChatManager.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import walson.one.walsonChatManager.utils.MessageUtils;

import java.util.Map;

public class GlobalChatManager {

    private final JavaPlugin plugin;

    private volatile boolean muted;
    private volatile boolean staffBypass;
    private volatile boolean announce;

    public GlobalChatManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads global chat settings from config.yml.
     */
    public void reload() {
        muted = plugin.getConfig().getBoolean(
                "chat.global-mute.enabled",
                false
        );

        staffBypass = plugin.getConfig().getBoolean(
                "chat.global-mute.staff-bypass",
                true
        );

        announce = plugin.getConfig().getBoolean(
                "chat.global-mute.announce",
                true
        );

        plugin.getLogger().info(
                "Global chat status: " + (muted ? "MUTED" : "ENABLED")
        );
    }

    /**
     * Returns whether global chat is currently muted.
     */
    public boolean isMuted() {
        return muted;
    }

    /**
     * Returns whether staff bypass is enabled in config.yml.
     */
    public boolean isStaffBypassEnabled() {
        return staffBypass;
    }

    /**
     * Checks whether a player can bypass global chat mute.
     */
    public boolean canBypass(Player player) {
        return staffBypass
                && player.hasPermission(
                "walsonchatmanager.bypass.globalmute"
        );
    }

    /**
     * Enables or disables global chat mute.
     *
     * @param muted true = global chat disabled, false = enabled
     * @param staff the name of the staff member performing the action
     * @return true if the state changed, false if it was already set
     */
    public boolean setMuted(boolean muted, String staff) {

        if (this.muted == muted) {
            return false;
        }

        this.muted = muted;

        // Save the state so it persists after a restart.
        plugin.getConfig().set(
                "chat.global-mute.enabled",
                muted
        );

        plugin.saveConfig();

        plugin.getLogger().info(
                "Global chat was " + (muted ? "disabled" : "enabled")
                        + " by " + staff + "."
        );

        if (announce) {
            broadcast(muted, staff);
        }

        return true;
    }

    /**
     * Toggles global chat mute.
     *
     * @param staff the staff member performing the action
     * @return the new muted state
     */
    public boolean toggle(String staff) {
        boolean newState = !muted;
        setMuted(newState, staff);
        return newState;
    }

    /**
     * Announces the global chat state to all online players.
     */
    private void broadcast(boolean muted, String staff) {

        String path = muted
                ? "global-chat.muted"
                : "global-chat.unmuted";

        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtils.send(
                    player,
                    getMessages(),
                    path,
                    Map.of("staff", staff)
            );
        }

        // Also log the announcement for console visibility.
        plugin.getLogger().info(
                "Global chat " + (muted ? "disabled" : "enabled")
                        + " by " + staff + "."
        );
    }

    private org.bukkit.configuration.file.FileConfiguration getMessages() {
        return plugin instanceof walson.one.walsonChatManager.WalsonChatManager wcm
                ? wcm.getMessages()
                : null;
    }
}
