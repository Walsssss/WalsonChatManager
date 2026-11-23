
package walson.one.walsonChatManager.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.managers.ChatFilterManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.models.FilterAction;
import walson.one.walsonChatManager.models.FilterRule;
import walson.one.walsonChatManager.models.MuteData;
import walson.one.walsonChatManager.utils.TimeUtils;

public class ChatListener implements Listener {

    private final WalsonChatManager plugin;
    private final ChatInputManager chatInputManager;
    private final ChatFilterManager chatFilterManager;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatListener(
            WalsonChatManager plugin,
            ChatInputManager chatInputManager,
            ChatFilterManager chatFilterManager
    ) {
        this.plugin = plugin;
        this.chatInputManager = chatInputManager;
        this.chatFilterManager = chatFilterManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Chat input for GUI prompts must not be processed by the filter.
        if (chatInputManager.handle(event)) {
            return;
        }

        // Existing mute check.
        MuteData mute = plugin.getMuteManager().getMute(player.getUniqueId());

        if (mute != null && !mute.isExpired()) {
            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(miniMessage.deserialize(
                            "<red>You are muted and cannot send messages."
                    ))
            );

            return;
        }

        // Staff can bypass chat filtering.
        if (player.hasPermission("walsonchatmanager.bypass.filter")) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        ChatFilterManager.Match match = chatFilterManager.findMatch(message);

        if (match == null) {
            return;
        }

        FilterRule rule = match.rule();

        // Cancel only when the configured actions require blocking or punishment.
        if (rule.blocksMessage()) {
            event.setCancelled(true);
        }

        // Bukkit actions and database operations are handled on the main thread.
        Bukkit.getScheduler().runTask(plugin, () ->
                applyFilterActions(player, rule, match.matchedWord())
        );
    }

    private void applyFilterActions(
            Player player,
            FilterRule rule,
            String matchedWord
    ) {
        if (!player.isOnline()) {
            return;
        }

        if (rule.hasAction(FilterAction.WARNING)) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>Please watch your language. <gray>Category: <white>"
                            + rule.id()
            ));
        }

        if (rule.hasAction(FilterAction.MUTE)
                || rule.hasAction(FilterAction.TEMPORARY_MUTE)) {

            long duration;

            // Permanent MUTE takes precedence if both punishment actions are configured.
            if (rule.hasAction(FilterAction.MUTE)) {
                duration = -1L;
            } else {
                try {
                    duration = TimeUtils.parseDuration(rule.duration());

                    if (duration <= 0) {
                        plugin.getLogger().warning(
                                "Invalid temporary mute duration in filter rule: "
                                        + rule.id()
                        );
                        return;
                    }
                } catch (Exception exception) {
                    plugin.getLogger().warning(
                            "Could not parse filter duration for rule: " + rule.id()
                    );
                    return;
                }
            }

            boolean muted = plugin.getMuteManager().mute(
                    player.getUniqueId(),
                    player.getName(),
                    rule.reason(),
                    "Chat Filter",
                    duration
            );

            if (muted) {
                player.sendMessage(miniMessage.deserialize(
                        "<red>You have been muted by the chat filter."
                ));

                player.sendMessage(miniMessage.deserialize(
                        "<gray>Reason: <white>" + rule.reason()
                ));
            }
        }

        if (chatFilterManager.shouldNotifyStaff()) {
            notifyStaff(player, rule, matchedWord);
        }
    }

    private void notifyStaff(
            Player player,
            FilterRule rule,
            String matchedWord
    ) {
        String notification =
                "<dark_gray>[<light_purple>ChatFilter<dark_gray>] "
                        + "<white>" + player.getName()
                        + " <gray>triggered <light_purple>" + rule.id()
                        + " <gray>(matched: <white>" + matchedWord
                        + "<gray>).";

        Bukkit.getOnlinePlayers().stream()
                .filter(staff -> staff.hasPermission(
                        "walsonchatmanager.filter.notify"
                ))
                .forEach(staff ->
                        staff.sendMessage(miniMessage.deserialize(notification))
                );
    }
}
