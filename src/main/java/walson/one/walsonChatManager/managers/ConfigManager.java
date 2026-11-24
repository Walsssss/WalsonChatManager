package walson.one.walsonChatManager.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

            if ("messages.yml".equals(name)) {
                mergeMissingMessageDefaults(file);
            }

            configurations.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }

    public FileConfiguration get(String name) {
        return configurations.get(name);
    }

    public void reloadFiles() {
        plugin.reloadConfig();

        for (String name : files) {
            File file = new File(plugin.getDataFolder(), name);

            if (!file.exists()) {
                plugin.getLogger().warning(
                        name + " was not found. Restoring the default resource."
                );
                plugin.saveResource(name, false);
            }

            if ("messages.yml".equals(name)) {
                mergeMissingMessageDefaults(file);
            }

            configurations.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }

    /**
     * Adds newly introduced message keys to existing messages.yml files
     * without replacing any values customized by the server owner.
     */
    private void mergeMissingMessageDefaults(File file) {
        try (InputStream resource = plugin.getResource("messages.yml")) {
            if (resource == null) {
                return;
            }

            FileConfiguration current = YamlConfiguration.loadConfiguration(file);
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );

            if (mergeSection(current, defaults, "")) {
                current.save(file);
                plugin.getLogger().info(
                        "Added missing default message keys to messages.yml."
                );
            }
        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Could not merge default messages.yml keys: "
                            + exception.getMessage()
            );
        }
    }

    private boolean mergeSection(
            FileConfiguration target,
            ConfigurationSection defaults,
            String parentPath
    ) {
        boolean changed = false;

        for (String key : defaults.getKeys(false)) {
            String path = parentPath.isEmpty()
                    ? key
                    : parentPath + "." + key;

            if (defaults.isConfigurationSection(key)) {
                if (!target.contains(path) || target.isConfigurationSection(path)) {
                    changed |= mergeSection(
                            target,
                            defaults.getConfigurationSection(key),
                            path
                    );
                }
            } else if (!target.contains(path)) {
                target.set(path, defaults.get(path));
                changed = true;
            }
        }

        return changed;
    }

    public void save(String name) {
        FileConfiguration configuration = configurations.get(name);

        if (configuration == null) {
            plugin.getLogger().warning(
                    "Cannot save " + name + ": configuration is not loaded."
            );
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
