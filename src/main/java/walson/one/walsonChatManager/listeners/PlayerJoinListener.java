
package walson.one.walsonChatManager.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
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
        MuteData mute = plugin.getMuteManager()
                .getMute(event.getPlayer().getUniqueId());

        if (mute == null) {
            return;
        }

        if (mute.isExpired()) {
            return;
        }

        MessageUtils.send(
                event.getPlayer(),
                plugin.getMessages(),
                "mute.received"
        );

        MessageUtils.send(
                event.getPlayer(),
                plugin.getMessages(),
                "mute.received-reason",
                Map.of("reason", mute.reason())
        );

        MessageUtils.send(
                event.getPlayer(),
                plugin.getMessages(),
                "mute.received-duration",
                Map.of(
                        "duration",
                        mute.isPermanent()
                                ? "Permanent"
                                : TimeUtils.formatDuration(mute.getRemainingTime())
                )
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatInput.cancel(event.getPlayer().getUniqueId());
    }
}
