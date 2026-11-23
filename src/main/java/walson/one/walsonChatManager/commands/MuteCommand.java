
package walson.one.walsonChatManager.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.gui.MuteGUI;
import walson.one.walsonChatManager.utils.MessageUtils;
import walson.one.walsonChatManager.utils.TimeUtils;

import java.util.Map;

public class MuteCommand implements CommandExecutor {

    private final WalsonChatManager plugin;
    private final MuteGUI muteGUI;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteCommand(
            WalsonChatManager plugin,
            MuteGUI muteGUI
    ) {
        this.plugin = plugin;
        this.muteGUI = muteGUI;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("walsonchatmanager.mute")) {
            MessageUtils.send(sender, plugin.getMessages(), "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>Usage: /mute <player> [duration] [reason]"
            ));
            return true;
        }

        String targetName = args[0];

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (args.length == 1) {
            if (!(sender instanceof Player staff)) {
                sender.sendMessage(miniMessage.deserialize(
                        "<red>Console usage: /mute <player> <duration> [reason]"
                ));
                return true;
            }

            muteGUI.openDurationMenu(staff, target);
            return true;
        }

        long duration;

        try {
            duration = TimeUtils.parseDuration(args[1]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>Invalid duration. Examples: 30m, 2h, 1d, permanent."
            ));
            return true;
        }

        String reason = args.length >= 3
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "Unspecified";

        String staffName = sender instanceof Player player
                ? player.getName()
                : "CONSOLE";

        if (plugin.getMuteManager().isMuted(target.getUniqueId())) {
            MessageUtils.send(sender, plugin.getMessages(), "mute.already-muted");
            return true;
        }

        boolean success = plugin.getMuteManager().mute(
                target.getUniqueId(),
                target.getName() == null ? targetName : target.getName(),
                reason,
                staffName,
                duration
        );

        if (!success) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>Could not save this mute. Check the console."
            ));
            return true;
        }

        String formattedDuration = TimeUtils.formatDurationWords(duration);

        MessageUtils.send(
                sender,
                plugin.getMessages(),
                "mute.success",
                Map.of(
                        "player", targetName,
                        "reason", reason,
                        "duration", formattedDuration,
                            "player-duration", TimeUtils.formatPlayerMuteDuration(duration)
                )
        );

        if (target.isOnline() && target.getPlayer() != null) {
            MessageUtils.send(
                    target.getPlayer(),
                    plugin.getMessages(),
                    "mute.received",
                    Map.of(
                            "reason", reason,
                            "duration", formattedDuration,
                            "player-duration", TimeUtils.formatPlayerMuteDuration(duration)
                    )
            );
        }

        return true;
    }
}
