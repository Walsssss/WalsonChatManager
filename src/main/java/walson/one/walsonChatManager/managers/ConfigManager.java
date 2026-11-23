
package walson.one.walsonChatManager.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;

    private final Map<String, FileConfiguration> configurations = new HashMap<>();

    private final String[] files = {
            "messages.yml",
            "chatfilter.yml",
            "mutepresets.yml",
            "discord.yml"
    };

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadFiles() {
        plugin.saveDefaultConfig();

        for (String name : files) {
            File file = new File(plugin.getDataFolder(), name);

            if (!file.exists()) {
                plugin.saveResource(name, false);
            }

            configurations.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }

    public FileConfiguration get(String name) {
        return configurations.get(name);
    }

    public void reloadFiles() {
        // Reload the main config.yml.
        plugin.reloadConfig();

        // Reload all additional YAML files.
        for (String name : files) {
            File file = new File(plugin.getDataFolder(), name);

            if (!file.exists()) {
                plugin.getLogger().warning(
                        name + " was not found. Restoring the default resource."
                );
                plugin.saveResource(name, false);
            }

            configurations.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }

    public void save(String name) {
        FileConfiguration configuration = configurations.get(name);

        if (configuration == null) {
            plugin.getLogger().warning("Cannot save " + name + ": configuration is not loaded.");
            return;
        }

        try {
            File file = new File(plugin.getDataFolder(), name);
            configuration.save(file);
        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Could not save " + name + ": " + exception.getMessage()
            );
        }
    }
}
