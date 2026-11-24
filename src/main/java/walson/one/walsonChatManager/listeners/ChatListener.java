
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
import walson.one.walsonChatManager.managers.AntiSpamManager;
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
    private final AntiSpamManager antiSpamManager;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatListener(
            WalsonChatManager plugin,
            ChatInputManager chatInputManager,
            ChatFilterManager chatFilterManager,
            GlobalChatManager globalChatManager,
            AntiSpamManager antiSpamManager
    ) {
        this.plugin = plugin;
        this.chatInputManager = chatInputManager;
        this.chatFilterManager = chatFilterManager;
        this.globalChatManager = globalChatManager;
        this.antiSpamManager = antiSpamManager;
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

        // 4. Check anti-spam before the chat filter.
        // The tracker is thread-safe because AsyncChatEvent runs asynchronously.
        if (!player.hasPermission("walsonchatmanager.bypass.antispam")) {
            AntiSpamManager.Result spamResult =
                    antiSpamManager.recordMessage(player.getUniqueId());

            if (spamResult.blocked()) {
                event.setCancelled(true);
            }

            // Process every blocked spam message so each one is included in
            // the Mute History chat-violation total. The warning itself is
            // still issued only once per separate spam burst.
            if (spamResult.blocked() || spamResult.warning()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        handleAntiSpamResult(player, spamResult)
                );
            }

            // Spam-triggering messages should not also be processed by filters.
            if (spamResult.blocked()) {
                return;
            }
        }

        // 5. Check chat filter bypass.
        if (player.hasPermission(
                "walsonchatmanager.bypass.filter"
        )) {
            return;
        }

        // 6. Check chat filter.
        String message = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        ChatFilterManager.Match match =
                chatFilterManager.findMatch(message);

        if (match == null) {
            return;
        }

        FilterRule rule = match.rule();

        // Block messages for BLOCK_MESSAGE, MUTE, and TEMPORARY_MUTE.
        if (rule.blocksMessage()) {
            event.setCancelled(true);
        }

        // Process the matched rule on the main server thread.
        Bukkit.getScheduler().runTask(
                plugin,
                () -> applyFilterActions(
                        player,
                        rule,
                        match.matchedWord()
                )
        );
    }

    private void handleAntiSpamResult(
            Player player,
            AntiSpamManager.Result result
    ) {
        // Every blocked message counts as one chat-filter violation.
        // This is recorded even if the player disconnects before this task runs.
        if (result.blocked()) {
            plugin.getMuteManager().recordChatFilterViolation(
                    player.getUniqueId(),
                    player.getName(),
                    "anti-spam",
                    "chat spam"
            );
        }

        // Warning counters represent warnings actually delivered to the player.
        if (!result.warning() || !player.isOnline()) {
            return;
        }

        long resetWindowMillis = resultWarningResetMillis();
        int warningCount = plugin.getMuteManager().recordAntiSpamWarning(
                player.getUniqueId(),
                player.getName(),
                resetWindowMillis
        );

        int warningLimit = antiSpamManager.getWarningsBeforeMute();

        MessageUtils.send(
                player,
                plugin.getMessages(),
                "anti-spam.warning",
                Map.of(
                        "warnings", String.valueOf(warningCount),
                        "limit", String.valueOf(warningLimit),
                        "cooldown", String.valueOf(result.cooldownSeconds())
                )
        );

        plugin.getSoundManager().play(
                player,
                walson.one.walsonChatManager.managers.SoundManager.SoundEvent.FILTER_WARNING
        );

        if (warningCount < warningLimit) {
            return;
        }

        boolean muted = plugin.getMuteManager().mute(
                player.getUniqueId(),
                player.getName(),
                result.muteReason(),
                "Anti-Spam",
                result.muteDurationMillis()
        );

        if (!muted) {
            return;
        }

        MuteData mute = plugin.getMuteManager().getMute(player.getUniqueId());
        plugin.getMuteManager().announceMute(mute);
        plugin.getMuteManager().notifyStaffMute(mute);

        MessageUtils.send(
                player,
                plugin.getMessages(),
                "mute.received",
                Map.of(
                        "duration",
                        TimeUtils.formatDurationWords(result.muteDurationMillis()),
                        "player-duration",
                        TimeUtils.formatPlayerMuteDuration(result.muteDurationMillis()),
                        "reason",
                        result.muteReason()
                )
        );

        MessageUtils.send(
                player,
                plugin.getMessages(),
                "anti-spam.muted",
                Map.of(
                        "warnings", String.valueOf(warningCount),
                        "limit", String.valueOf(warningLimit)
                )
        );
    }

    private long resultWarningResetMillis() {
        return java.util.concurrent.TimeUnit.HOURS.toMillis(
                antiSpamManager.getWarningResetHours()
        );
    }

    private void applyFilterActions(
            Player player,
            FilterRule rule,
            String matchedWord
    ) {

        /*
         * Count the violation before checking whether the player is
         * still online. This ensures that a matched message is recorded
         * even if the player disconnects before this task executes.
         *
         * One matched message = one violation, regardless of how many
         * actions are configured on the rule.
         */
        plugin.getMuteManager().recordChatFilterViolation(
                player.getUniqueId(),
                player.getName(),
                rule.id(),
                matchedWord
        );

        if (plugin.getDiscordManager() != null) {
            String actions = String.join(", ",
                    rule.actions().stream().map(Enum::name).toList());
            plugin.getDiscordManager().sendChatFilterViolation(
                    player.getName(),
                    rule.id(),
                    matchedWord,
                    actions
            );
        }

        if (!player.isOnline()) {
            return;
        }

        // WARNING action.
        if (rule.hasAction(FilterAction.WARNING)) {

            MessageUtils.send(
                    player,
                    plugin.getMessages(),
                    "filter.warned-category",
                    Map.of("category", rule.id())
            );

        }

        // MUTE or TEMPORARY_MUTE action.
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

                plugin.getMuteManager().announceMute(
                        plugin.getMuteManager().getMute(player.getUniqueId())
                );

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

        // Notify staff about the violation.
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
