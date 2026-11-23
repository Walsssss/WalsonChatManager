package walson.one.walsonChatManager.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.managers.SoundManager;
import walson.one.walsonChatManager.models.MuteData;
import walson.one.walsonChatManager.utils.MessageUtils;
import walson.one.walsonChatManager.utils.TimeUtils;

import java.util.Map;

public class PlayerJoinListener implements Listener {

    private final WalsonChatManager plugin;
    private final ChatInputManager chatInput;

    public PlayerJoinListener(
            WalsonChatManager plugin,
            ChatInputManager chatInput
    ) {
        this.plugin = plugin;
        this.chatInput = chatInput;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // A pending offline mute becomes active now, so its duration starts
        // at this join rather than when staff originally issued the mute.
        MuteData activated = plugin.getMuteManager()
                .activatePendingMute(player.getUniqueId());

        MuteData mute = activated != null
                ? activated
                : plugin.getMuteManager().getMute(player.getUniqueId());

        if (mute == null || mute.isExpired()) {
            return;
        }

        String duration = mute.isPermanent()
                ? "Permanent"
                : TimeUtils.formatDuration(mute.getRemainingTime());

        MessageUtils.send(
                player,
                plugin.getMessages(),
                "mute.received",
                Map.of(
                        "reason", mute.reason(),
                        "duration", duration,
                        "player-duration", TimeUtils.formatPlayerMuteDuration(
                                mute.isPermanent() ? -1L : mute.getRemainingTime()
                        )
                )
        );

        // Only newly activated offline mutes play the mute sound on join.
        // Existing mutes do not replay their sound on every reconnect.
        if (activated != null && plugin.getSoundManager() != null) {
            plugin.getSoundManager().play(player, SoundManager.SoundEvent.MUTE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatInput.cancel(event.getPlayer().getUniqueId());
    }
}
