
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
import walson.one.walsonChatManager.managers.GlobalChatManager;

import walson.one.walsonChatManager.models.FilterAction;
import walson.one.walsonChatManager.models.FilterRule;
import walson.one.walsonChatManager.models.MuteData;

import walson.one.walsonChatManager.utils.MessageUtils;
import walson.one.walsonChatManager.utils.TimeUtils;

import java.util.Map;

public class ChatListener implements Listener {

    private final WalsonChatManager plugin;

    private final ChatInputManager chatInputManager;
    private final ChatFilterManager chatFilterManager;
    private final GlobalChatManager globalChatManager;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatListener(
            WalsonChatManager plugin,
            ChatInputManager chatInputManager,
            ChatFilterManager chatFilterManager,
            GlobalChatManager globalChatManager
    ) {
        this.plugin = plugin;
        this.chatInputManager = chatInputManager;
        this.chatFilterManager = chatFilterManager;
        this.globalChatManager = globalChatManager;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onChat(AsyncChatEvent event) {

        Player player = event.getPlayer();

        // 1. Handle pending GUI input first.
        if (chatInputManager.handle(event)) {
            return;
        }

        // 2. Check individual player mute.
        MuteData mute = plugin.getMuteManager()
                .getMute(player.getUniqueId());

        if (mute != null && !mute.isExpired()) {

            event.setCancelled(true);

            String remaining = TimeUtils.formatPlayerMuteDuration(
                    mute.isPermanent()
                            ? -1L
                            : mute.getRemainingTime()
            );

            Bukkit.getScheduler().runTask(plugin, () -> {

                if (!player.isOnline()) {
                    return;
                }

                MessageUtils.send(
                        player,
                        plugin.getMessages(),
                        "mute.chat-blocked",
                        Map.of(
                                "duration", remaining,
                                "reason", mute.reason()
                        )
                );
            });

            return;
        }

        // 3. Check global chat mute.
        if (globalChatManager.isMuted()
                && !globalChatManager.canBypass(player)) {

            event.setCancelled(true);

            Bukkit.getScheduler().runTask(plugin, () -> {

                if (!player.isOnline()) {
                    return;
                }

                MessageUtils.send(
                        player,
                        plugin.getMessages(),
                        "global-chat.blocked"
                );
            });

            return;
        }

        // 4. Check chat filter bypass.
        if (player.hasPermission(
                "walsonchatmanager.bypass.filter"
        )) {
            return;
        }

        // 5. Check chat filter.
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        ChatFilterManager.Match match =
                chatFilterManager.findMatch(message);

        if (match == null) {
            return;
        }

        FilterRule rule = match.rule();

        if (rule.blocksMessage()) {
            event.setCancelled(true);
        }

        Bukkit.getScheduler().runTask(
                plugin,
                () -> applyFilterActions(
                        player,
                        rule,
                        match.matchedWord()
                )
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

            MessageUtils.send(
                    player,
                    plugin.getMessages(),
                    "filter.warned-category",
                    Map.of("category", rule.id())
            );
        }

        if (rule.hasAction(FilterAction.MUTE)
                || rule.hasAction(FilterAction.TEMPORARY_MUTE)) {

            long duration;

            if (rule.hasAction(FilterAction.MUTE)) {
                duration = -1L;
            } else {

                try {
                    duration = TimeUtils.parseDuration(
                            rule.duration()
                    );

                    if (duration <= 0) {
                        plugin.getLogger().warning(
                                "Invalid temporary mute duration in filter rule: "
                                        + rule.id()
                        );
                        return;
                    }

                } catch (Exception exception) {
                    plugin.getLogger().warning(
                            "Could not parse filter duration for rule: "
                                    + rule.id()
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

                MessageUtils.send(
                        player,
                        plugin.getMessages(),
                        "mute.received",
                        Map.of(
                                "duration",
                                TimeUtils.formatDurationWords(duration),
                                "player-duration",
                                TimeUtils.formatPlayerMuteDuration(duration),
                                "reason",
                                rule.reason()
                        )
                );
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
                        staff.sendMessage(
                                miniMessage.deserialize(notification)
                        )
                );
    }
}
