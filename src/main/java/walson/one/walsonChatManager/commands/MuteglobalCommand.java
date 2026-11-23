
package walson.one.walsonChatManager.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.managers.GlobalChatManager;
import walson.one.walsonChatManager.utils.MessageUtils;

public class MuteglobalCommand implements CommandExecutor {

    private final GlobalChatManager globalChatManager;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteglobalCommand(GlobalChatManager globalChatManager) {
        this.globalChatManager = globalChatManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("walsonchatmanager.muteglobal")) {
            MessageUtils.send(
                    sender,
                    getMessages(),
                    "general.no-permission"
            );
            return true;
        }

        if (args.length > 1) {
            sendUsage(sender);
            return true;
        }

        String staff = sender.getName();

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            globalChatManager.toggle(staff);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "on" -> {
                boolean changed = globalChatManager.setMuted(true, staff);

                if (!changed) {
                    MessageUtils.send(
                            sender,
                            getMessages(),
                            "global-chat.already-muted"
                    );
                }
            }

            case "off" -> {
                boolean changed = globalChatManager.setMuted(false, staff);

                if (!changed) {
                    MessageUtils.send(
                            sender,
                            getMessages(),
                            "global-chat.already-unmuted"
                    );
                }
            }

            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(
                "<red>Usage: <white>/muteglobal [on|off|toggle]"
        ));
    }

    private org.bukkit.configuration.file.FileConfiguration getMessages() {
        return WalsonChatManager.getInstance().getMessages();
    }
}
