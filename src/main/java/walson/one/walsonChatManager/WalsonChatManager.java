
package walson.one.walsonChatManager;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import walson.one.walsonChatManager.commands.ChatFilterCommand;
import walson.one.walsonChatManager.commands.ChatManagerCommand;
import walson.one.walsonChatManager.commands.MuteCommand;
import walson.one.walsonChatManager.commands.MuteHistoryCommand;
import walson.one.walsonChatManager.commands.MuteglobalCommand;
import walson.one.walsonChatManager.commands.UnmuteCommand;
import walson.one.walsonChatManager.commands.WalsonChatManagerCommand;

import walson.one.walsonChatManager.database.DatabaseManager;

import walson.one.walsonChatManager.gui.ChatFilterGUI;
import walson.one.walsonChatManager.gui.ChatManagerGUI;
import walson.one.walsonChatManager.gui.GUIListener;
import walson.one.walsonChatManager.gui.MuteGUI;
import walson.one.walsonChatManager.gui.MuteHistoryGUI;
import walson.one.walsonChatManager.gui.UnmuteGUI;

import walson.one.walsonChatManager.listeners.ChatListener;
import walson.one.walsonChatManager.listeners.PlayerJoinListener;

import walson.one.walsonChatManager.managers.ChatFilterManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.managers.ConfigManager;
import walson.one.walsonChatManager.managers.GlobalChatManager;
import walson.one.walsonChatManager.managers.MuteManager;
import walson.one.walsonChatManager.managers.MutePresetManager;
import walson.one.walsonChatManager.managers.SoundManager;

import java.util.Objects;

public final class WalsonChatManager extends JavaPlugin {

    private static WalsonChatManager instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MuteManager muteManager;
    private MutePresetManager mutePresetManager;
    private ChatInputManager chatInputManager;
    private ChatFilterManager chatFilterManager;
    private GlobalChatManager globalChatManager;
    private SoundManager soundManager;

    private MuteGUI muteGUI;
    private UnmuteGUI unmuteGUI;
    private ChatFilterGUI chatFilterGUI;
    private MuteHistoryGUI muteHistoryGUI;
    private ChatManagerGUI chatManagerGUI;

    @Override
    public void onEnable() {

        instance = this;

        getLogger().info("======================================");
        getLogger().info("       WalsonChatManager");
        getLogger().info("======================================");

        // Load configuration files.
        configManager = new ConfigManager(this);
        configManager.loadFiles();

        // Initialize global chat and notification sound management.
        globalChatManager = new GlobalChatManager(this);
        soundManager = new SoundManager(this);

        // Initialize database.
        databaseManager = new DatabaseManager(this);

        if (!databaseManager.connect()) {
            getLogger().severe("Database initialization failed!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize mute management.
        muteManager = new MuteManager(this, databaseManager);
        muteManager.loadMutes();

        // Initialize managers.
        mutePresetManager = new MutePresetManager(configManager);
        chatInputManager = new ChatInputManager(this);
        chatFilterManager = new ChatFilterManager(this, configManager);

        // Initialize feature GUIs.
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

        muteHistoryGUI = new MuteHistoryGUI(this);

        // Initialize central management GUI.
        chatManagerGUI = new ChatManagerGUI(
                this,
                muteGUI,
                unmuteGUI,
                muteHistoryGUI,
                chatFilterGUI,
                chatInputManager,
                globalChatManager,
                soundManager
        );

        // Register commands.
        registerCommands();

        // Register chat listener.
        getServer().getPluginManager().registerEvents(
                new ChatListener(
                        this,
                        chatInputManager,
                        chatFilterManager,
                        globalChatManager
                ),
                this
        );

        // Register player join listener.
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, chatInputManager),
                this
        );

        // Register GUI listener.
        getServer().getPluginManager().registerEvents(
                new GUIListener(
                        muteGUI,
                        unmuteGUI,
                        chatFilterGUI,
                        muteHistoryGUI,
                        chatManagerGUI
                ),
                this
        );

        // Check expired mutes every second.
        getServer().getScheduler().runTaskTimer(
                this,
                () -> muteManager.expireMutes(),
                20L,
                20L
        );

        // DiscordSRV detection.
        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            getLogger().info("DiscordSRV detected.");
        } else {
            getLogger().info("DiscordSRV not installed. Integration skipped.");
        }

        getLogger().info("WalsonChatManager enabled successfully!");
        getLogger().info(
                "Loaded active mutes: "
                        + muteManager.getActiveMutes().size()
        );
        getLogger().info(
                "Loaded chat filter rules: "
                        + chatFilterManager.getRules().size()
        );
        getLogger().info(
                "Global chat: "
                        + (globalChatManager.isMuted() ? "MUTED" : "ENABLED")
        );
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
                        chatFilterManager,
                        globalChatManager
                )
        );

        PluginCommand chatFilterCommand = Objects.requireNonNull(
                getCommand("chatfilter"),
                "Command 'chatfilter' is missing from plugin.yml"
        );
        chatFilterCommand.setExecutor(
                new ChatFilterCommand(chatFilterGUI)
        );

        PluginCommand muteHistoryCommand = Objects.requireNonNull(
                getCommand("mutehistory"),
                "Command 'mutehistory' is missing from plugin.yml"
        );
        muteHistoryCommand.setExecutor(
                new MuteHistoryCommand(this, muteHistoryGUI)
        );

        PluginCommand globalChatCommand = Objects.requireNonNull(
                getCommand("muteglobal"),
                "Command 'muteglobal' is missing from plugin.yml"
        );
        globalChatCommand.setExecutor(
                new MuteglobalCommand(globalChatManager)
        );

        PluginCommand chatManagerCommand = Objects.requireNonNull(
                getCommand("chatmanager"),
                "Command 'chatmanager' is missing from plugin.yml"
        );
        chatManagerCommand.setExecutor(
                new ChatManagerCommand(chatManagerGUI)
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

    // =========================================================
    // GETTERS
    // =========================================================

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

    public GlobalChatManager getGlobalChatManager() {
        return globalChatManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
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

    public MuteHistoryGUI getMuteHistoryGUI() {
        return muteHistoryGUI;
    }

    public ChatManagerGUI getChatManagerGUI() {
        return chatManagerGUI;
    }

    public org.bukkit.configuration.file.FileConfiguration getMessages() {
        return configManager.get("messages.yml");
    }
}
