
package walson.one.walsonChatManager.utils;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtils() {
    }

    public static void send(
            CommandSender sender,
            FileConfiguration messages,
            String path
    ) {
        send(sender, messages, path, Map.of());
    }

    public static void send(
            CommandSender sender,
            FileConfiguration messages,
            String path,
            Map<String, String> placeholders
    ) {
        String message = messages.getString(path);

        if (message == null) {
            return;
        }

        String prefix = messages.getString("prefix", "");

        message = prefix + " " + message;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(
                    "%" + entry.getKey() + "%",
                    entry.getValue()
            );
        }

        sender.sendMessage(MINI_MESSAGE.deserialize(message));
    }
}
