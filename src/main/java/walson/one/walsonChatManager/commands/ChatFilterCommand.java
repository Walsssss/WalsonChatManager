
package walson.one.walsonChatManager.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import walson.one.walsonChatManager.gui.ChatFilterGUI;

public class ChatFilterCommand implements CommandExecutor {

    private final ChatFilterGUI chatFilterGUI;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatFilterCommand(ChatFilterGUI chatFilterGUI) {
        this.chatFilterGUI = chatFilterGUI;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("walsonchatmanager.filter")) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>You don't have permission to use this command."
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>This command can only be used by players."
            ));
            return true;
        }

        chatFilterGUI.openMain(player);
        return true;
    }
}
