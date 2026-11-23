
package walson.one.walsonChatManager;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import walson.one.walsonChatManager.commands.ChatFilterCommand;
import walson.one.walsonChatManager.commands.MuteCommand;
import walson.one.walsonChatManager.commands.UnmuteCommand;
import walson.one.walsonChatManager.commands.WalsonChatManagerCommand;
import walson.one.walsonChatManager.database.DatabaseManager;
import walson.one.walsonChatManager.gui.ChatFilterGUI;
import walson.one.walsonChatManager.gui.GUIListener;
import walson.one.walsonChatManager.gui.MuteGUI;
import walson.one.walsonChatManager.gui.UnmuteGUI;
import walson.one.walsonChatManager.listeners.ChatListener;
import walson.one.walsonChatManager.listeners.PlayerJoinListener;
import walson.one.walsonChatManager.managers.ChatFilterManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.managers.ConfigManager;
import walson.one.walsonChatManager.managers.MuteManager;
import walson.one.walsonChatManager.managers.MutePresetManager;

import java.util.Objects;

public final class WalsonChatManager extends JavaPlugin {

    private static WalsonChatManager instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MuteManager muteManager;
    private MutePresetManager mutePresetManager;
    private ChatInputManager chatInputManager;
    private ChatFilterManager chatFilterManager;

    private MuteGUI muteGUI;
    private UnmuteGUI unmuteGUI;
    private ChatFilterGUI chatFilterGUI;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("======================================");
        getLogger().info("       WalsonChatManager");
        getLogger().info("======================================");

        configManager = new ConfigManager(this);
        configManager.loadFiles();

        databaseManager = new DatabaseManager(this);

        if (!databaseManager.connect()) {
            getLogger().severe("Database initialization failed!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        muteManager = new MuteManager(this, databaseManager);
        muteManager.loadMutes();

        mutePresetManager = new MutePresetManager(configManager);
        chatInputManager = new ChatInputManager(this);

        chatFilterManager = new ChatFilterManager(this, configManager);

        muteGUI = new MuteGUI(
                this,
                mutePresetManager,
                chatInputManager
        );

        unmuteGUI = new UnmuteGUI(
                this,
                mutePresetManager,
                chatInputManager
        );

        chatFilterGUI = new ChatFilterGUI(
                this,
                chatFilterManager,
                chatInputManager
        );

        registerCommands();

        getServer().getPluginManager().registerEvents(
                new ChatListener(
                        this,
                        chatInputManager,
                        chatFilterManager
                ),
                this
        );

        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, chatInputManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new GUIListener(
                        muteGUI,
                        unmuteGUI,
                        chatFilterGUI
                ),
                this
        );

        getServer().getScheduler().runTaskTimer(
                this,
                () -> muteManager.expireMutes(),
                20L,
                20L
        );

        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            getLogger().info("DiscordSRV detected.");
        } else {
            getLogger().info("DiscordSRV not installed. Integration skipped.");
        }

        getLogger().info("WalsonChatManager enabled successfully!");
        getLogger().info("Loaded active mutes: " + muteManager.getActiveMutes().size());
        getLogger().info("Loaded chat filter rules: " + chatFilterManager.getRules().size());
    }

    private void registerCommands() {
        PluginCommand muteCommand = Objects.requireNonNull(
                getCommand("mute"),
                "Command 'mute' is missing from plugin.yml"
        );
        muteCommand.setExecutor(new MuteCommand(this, muteGUI));

        PluginCommand unmuteCommand = Objects.requireNonNull(
                getCommand("unmute"),
                "Command 'unmute' is missing from plugin.yml"
        );
        unmuteCommand.setExecutor(new UnmuteCommand(this, unmuteGUI));

        PluginCommand managerCommand = Objects.requireNonNull(
                getCommand("walsonchatmanager"),
                "Command 'walsonchatmanager' is missing from plugin.yml"
        );
        managerCommand.setExecutor(
                new WalsonChatManagerCommand(
                        this,
                        configManager,
                        mutePresetManager,
                        chatFilterManager
                )
        );

        PluginCommand chatFilterCommand = Objects.requireNonNull(
                getCommand("chatfilter"),
                "Command 'chatfilter' is missing from plugin.yml"
        );
        chatFilterCommand.setExecutor(
                new ChatFilterCommand(chatFilterGUI)
        );
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        getLogger().info("WalsonChatManager disabled.");
        instance = null;
    }

    public static WalsonChatManager getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public MuteManager getMuteManager() {
        return muteManager;
    }

    public MutePresetManager getMutePresetManager() {
        return mutePresetManager;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public ChatFilterManager getChatFilterManager() {
        return chatFilterManager;
    }

    public MuteGUI getMuteGUI() {
        return muteGUI;
    }

    public UnmuteGUI getUnmuteGUI() {
        return unmuteGUI;
    }

    public ChatFilterGUI getChatFilterGUI() {
        return chatFilterGUI;
    }

    public org.bukkit.configuration.file.FileConfiguration getMessages() {
        return configManager.get("messages.yml");
    }
}
