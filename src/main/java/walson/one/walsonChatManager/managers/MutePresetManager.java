
package walson.one.walsonChatManager.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class MutePresetManager {

    public record Preset(String id, String display, String value) {}

    private final ConfigManager configManager;

    private final List<Preset> durations = new ArrayList<>();
    private final List<Preset> muteReasons = new ArrayList<>();
    private final List<Preset> unmuteReasons = new ArrayList<>();

    public MutePresetManager(ConfigManager configManager) {
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        durations.clear();
        muteReasons.clear();
        unmuteReasons.clear();

        FileConfiguration config = configManager.get("mutepresets.yml");

        if (config == null) {
            return;
        }

        loadPresets(config, "durations", durations);
        loadPresets(config, "mute-reasons", muteReasons);
        loadPresets(config, "unmute-reasons", unmuteReasons);
    }

    private void loadPresets(
            FileConfiguration config,
            String path,
            List<Preset> destination
    ) {
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            String display = section.getString(id + ".display", id);
            String value = section.getString(id + ".duration");

            if (value == null) {
                value = section.getString(id + ".reason", id);
            }

            destination.add(new Preset(id, display, value));
        }
    }

    public List<Preset> getDurations() {
        return List.copyOf(durations);
    }

    public List<Preset> getMuteReasons() {
        return List.copyOf(muteReasons);
    }

    public List<Preset> getUnmuteReasons() {
        return List.copyOf(unmuteReasons);
    }
}
