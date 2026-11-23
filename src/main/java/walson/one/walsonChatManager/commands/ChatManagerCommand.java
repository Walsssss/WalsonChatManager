
package walson.one.walsonChatManager.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import walson.one.walsonChatManager.gui.ChatManagerGUI;
import walson.one.walsonChatManager.utils.MessageUtils;

public class ChatManagerCommand implements CommandExecutor {

    private final ChatManagerGUI chatManagerGUI;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatManagerCommand(ChatManagerGUI chatManagerGUI) {
        this.chatManagerGUI = chatManagerGUI;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("walsonchatmanager.gui")) {
            MessageUtils.send(
                    sender,
                    walson.one.walsonChatManager.WalsonChatManager
                            .getInstance()
                            .getMessages(),
                    "general.no-permission"
            );
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(miniMessage.deserialize(
                    "<red>This command can only be used by players."
            ));
            return true;
        }

        chatManagerGUI.openMain(player);

        return true;
    }
}
