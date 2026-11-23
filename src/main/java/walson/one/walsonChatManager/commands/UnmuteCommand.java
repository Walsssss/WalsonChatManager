
package walson.one.walsonChatManager.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.gui.UnmuteGUI;
import walson.one.walsonChatManager.utils.MessageUtils;

import java.util.Map;

public class UnmuteCommand implements CommandExecutor {

    private final WalsonChatManager plugin;
    private final UnmuteGUI unmuteGUI;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public UnmuteCommand(
            WalsonChatManager plugin,
            UnmuteGUI unmuteGUI
    ) {
        this.plugin = plugin;
        this.unmuteGUI = unmuteGUI;
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
                    "<red>Usage: /unmute <player> [reason]"
            ));
            return true;
        }

        String targetName = args[0];

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!plugin.getMuteManager().isMuted(target.getUniqueId())) {
            MessageUtils.send(sender, plugin.getMessages(), "mute.not-muted");
            return true;
        }

        if (args.length == 1 && sender instanceof Player staff) {
            unmuteGUI.open(
                    staff,
                    target.getUniqueId(),
                    target.getName() == null ? targetName : target.getName()
            );
            return true;
        }

        String reason = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "Staff decision";

        String staffName = sender instanceof Player player
                ? player.getName()
                : "CONSOLE";

        boolean success = plugin.getMuteManager().unmute(
                target.getUniqueId(),
                reason,
                staffName
        );

        if (!success) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>Could not unmute this player."
            ));
            return true;
        }

        MessageUtils.send(
                sender,
                plugin.getMessages(),
                "unmute.success",
                Map.of("player", targetName)
        );

        if (target.isOnline() && target.getPlayer() != null) {
            MessageUtils.send(
                    target.getPlayer(),
                    plugin.getMessages(),
                    "unmute.received"
            );
        }

        return true;
    }
}
