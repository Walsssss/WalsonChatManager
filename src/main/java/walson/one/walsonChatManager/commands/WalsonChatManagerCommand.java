
package walson.one.walsonChatManager.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import walson.one.walsonChatManager.managers.ChatFilterManager;
import walson.one.walsonChatManager.managers.ConfigManager;
import walson.one.walsonChatManager.managers.MutePresetManager;

public class WalsonChatManagerCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MutePresetManager mutePresetManager;
    private final ChatFilterManager chatFilterManager;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public WalsonChatManagerCommand(
            JavaPlugin plugin,
            ConfigManager configManager,
            MutePresetManager mutePresetManager,
            ChatFilterManager chatFilterManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mutePresetManager = mutePresetManager;
        this.chatFilterManager = chatFilterManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        String subcommand = args.length == 0
                ? "help"
                : args[0].toLowerCase();

        switch (subcommand) {
            case "help" -> {
                if (!sender.hasPermission("walsonchatmanager.help")) {
                    sendMessage(sender, "<red>You don't have permission to use this command.");
                    return true;
                }

                sendHelp(sender);
            }

            case "reload" -> {
                if (!sender.hasPermission("walsonchatmanager.reload")) {
                    sendMessage(sender, "<red>You don't have permission to use this command.");
                    return true;
                }

                reload(sender);
            }

            default -> sendMessage(
                    sender,
                    "<red>Unknown subcommand. Use <white>/walsonchatmanager help</white>."
            );
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "");
        sendMessage(sender, "<gradient:#E879F9:#A855F7><bold>WalsonChatManager</bold></gradient> <dark_gray>» <gray>Commands");
        sendMessage(sender, "");
        sendMessage(sender, "<light_purple>/walsonchatmanager help</light_purple> <gray>- Shows this help menu.");
        sendMessage(sender, "<light_purple>/walsonchatmanager reload</light_purple> <gray>- Reloads plugin configuration files.");
        sendMessage(sender, "<light_purple>/mute <player></light_purple> <gray>- Opens the mute menu.");
        sendMessage(sender, "<light_purple>/mute <player> <duration> [reason]</light_purple> <gray>- Mutes a player.");
        sendMessage(sender, "<light_purple>/unmute <player></light_purple> <gray>- Opens the unmute menu.");
        sendMessage(sender, "<light_purple>/unmute <player> [reason]</light_purple> <gray>- Removes a player's mute.");
        sendMessage(sender, "<light_purple>/chatfilter</light_purple> <gray>- Opens the Chat Filter GUI.");
        sendMessage(sender, "");
        sendMessage(sender, "<gray>Shortcut: <white>/wcm</white>");
        sendMessage(sender, "");
    }

    private void reload(CommandSender sender) {
        try {
            configManager.reloadFiles();
            mutePresetManager.reload();
            chatFilterManager.reload();

            sendMessage(sender,
                    "<green>WalsonChatManager configuration files reloaded successfully.");

            plugin.getLogger().info(
                    sender.getName() + " reloaded the plugin configuration files."
            );

        } catch (Exception exception) {
            sendMessage(sender,
                    "<red>An error occurred while reloading configuration files.");

            plugin.getLogger().severe(
                    "Configuration reload failed: " + exception.getMessage()
            );

            exception.printStackTrace();
        }
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(miniMessage.deserialize(message));
    }
}
