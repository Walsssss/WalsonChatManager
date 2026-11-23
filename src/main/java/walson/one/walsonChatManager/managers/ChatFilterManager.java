
package walson.one.walsonChatManager.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import walson.one.walsonChatManager.models.FilterAction;
import walson.one.walsonChatManager.models.FilterMatchType;
import walson.one.walsonChatManager.models.FilterRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ChatFilterManager {

    public record Match(FilterRule rule, String matchedWord) {}

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private final List<FilterRule> rules = new ArrayList<>();

    private boolean enabled;
    private boolean caseSensitive;
    private boolean ignoreSpaces;
    private boolean notifyStaff;

    public ChatFilterManager(
            JavaPlugin plugin,
            ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.configManager = configManager;

        reload();
    }

    // =========================================================
    // CONFIGURATION
    // =========================================================

    public synchronized void reload() {
        rules.clear();

        FileConfiguration config = configManager.get("chatfilter.yml");

        if (config == null) {
            plugin.getLogger().warning(
                    "chatfilter.yml is not loaded."
            );
            return;
        }

        enabled = config.getBoolean("settings.enabled", true);
        caseSensitive = config.getBoolean("settings.case-sensitive", false);
        ignoreSpaces = config.getBoolean("settings.ignore-spaces", false);
        notifyStaff = config.getBoolean("settings.notify-staff", true);

        ConfigurationSection section =
                config.getConfigurationSection("rules");

        if (section == null) {
            plugin.getLogger().info("No chat filter rules found.");
            return;
        }

        for (String id : section.getKeys(false)) {

            String path = "rules." + id;

            if (!config.isConfigurationSection(path)) {
                continue;
            }

            boolean ruleEnabled =
                    config.getBoolean(path + ".enabled", true);

            int priority =
                    config.getInt(path + ".priority", 100);

            FilterMatchType matchType;

            try {
                matchType = FilterMatchType.valueOf(
                        config.getString(path + ".match-type", "CONTAINS")
                                .toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(
                        "Invalid match type in filter rule: " + id
                );
                continue;
            }

            List<String> words =
                    config.getStringList(path + ".words");

            List<FilterAction> actions = new ArrayList<>();

            for (String actionName :
                    config.getStringList(path + ".actions")) {

                try {
                    actions.add(FilterAction.valueOf(
                            actionName.toUpperCase(Locale.ROOT)
                    ));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning(
                            "Invalid action '" + actionName
                                    + "' in filter rule '" + id + "'."
                    );
                }
            }

            String duration = config.getString(
                    path + ".punishment.duration",
                    "30m"
            );

            String reason = config.getString(
                    path + ".punishment.reason",
                    "Chat Filter Violation"
            );

            // Validate REGEX expressions during loading.
            if (matchType == FilterMatchType.REGEX) {
                List<String> validWords = new ArrayList<>();

                for (String word : words) {
                    try {
                        Pattern.compile(
                                word,
                                caseSensitive
                                        ? 0
                                        : Pattern.CASE_INSENSITIVE
                                          | Pattern.UNICODE_CASE
                        );

                        validWords.add(word);

                    } catch (PatternSyntaxException exception) {
                        plugin.getLogger().warning(
                                "Invalid regex '" + word
                                        + "' in filter rule '" + id + "'."
                        );
                    }
                }

                words = validWords;
            }

            rules.add(new FilterRule(
                    id,
                    ruleEnabled,
                    priority,
                    matchType,
                    words,
                    actions,
                    duration,
                    reason
            ));
        }

        // Lower priority numbers are checked first.
        rules.sort(
                Comparator.comparingInt(FilterRule::priority)
        );

        plugin.getLogger().info(
                "Loaded " + rules.size() + " chat filter rules."
        );
    }

    // =========================================================
    // MESSAGE MATCHING
    // =========================================================

    public synchronized Match findMatch(String message) {

        if (!enabled || message == null || message.isBlank()) {
            return null;
        }

        for (FilterRule rule : rules) {

            if (!rule.enabled()) {
                continue;
            }

            String candidateMessage =
                    rule.matchType() == FilterMatchType.REGEX
                            ? message
                            : normalize(message);

            for (String word : rule.words()) {

                if (word == null || word.isBlank()) {
                    continue;
                }

                String candidateWord =
                        rule.matchType() == FilterMatchType.REGEX
                                ? word
                                : normalize(word);

                if (matches(
                        candidateMessage,
                        candidateWord,
                        rule.matchType()
                )) {
                    return new Match(rule, word);
                }
            }
        }

        return null;
    }

    private boolean matches(
            String message,
            String word,
            FilterMatchType matchType
    ) {

        int flags = caseSensitive
                ? 0
                : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

        return switch (matchType) {

            case CONTAINS -> message.contains(word);

            case WORD -> {
                String expression =
                        "(?<![\\p{L}\\p{N}_])"
                                + Pattern.quote(word)
                                + "(?![\\p{L}\\p{N}_])";

                try {
                    yield Pattern.compile(expression, flags)
                            .matcher(message)
                            .find();

                } catch (PatternSyntaxException exception) {
                    yield false;
                }
            }

            case REGEX -> {
                try {
                    yield Pattern.compile(word, flags)
                            .matcher(message)
                            .find();

                } catch (PatternSyntaxException exception) {
                    yield false;
                }
            }
        };
    }

    private String normalize(String input) {

        String result = input;

        if (ignoreSpaces) {
            result = result.replaceAll("\\s+", "");
        }

        if (!caseSensitive) {
            result = result.toLowerCase(Locale.ROOT);
        }

        return result;
    }

    // =========================================================
    // RULE ACCESS
    // =========================================================

    public synchronized List<FilterRule> getRules() {
        return List.copyOf(rules);
    }

    public synchronized FilterRule getRule(String id) {

        if (id == null) {
            return null;
        }

        return rules.stream()
                .filter(rule ->
                        rule.id().equalsIgnoreCase(id)
                )
                .findFirst()
                .orElse(null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldNotifyStaff() {
        return notifyStaff;
    }

    // =========================================================
    // RULE CREATION
    // =========================================================

    public synchronized void addRule(
            String id,
            String displayName
    ) {

        FileConfiguration config = getConfig();

        if (config == null
                || id == null
                || id.isBlank()
                || config.contains("rules." + id)) {
            return;
        }

        String path = "rules." + id;

        config.set(path + ".display-name", displayName);
        config.set(path + ".enabled", true);
        config.set(path + ".priority", 100);
        config.set(path + ".match-type", "CONTAINS");

        config.set(
                path + ".words",
                new ArrayList<String>()
        );

        config.set(
                path + ".actions",
                List.of("BLOCK_MESSAGE", "WARNING")
        );

        config.set(
                path + ".punishment.duration",
                "30m"
        );

        config.set(
                path + ".punishment.reason",
                displayName
        );

        saveAndReload();
    }

    // =========================================================
    // ENABLE / DISABLE
    // =========================================================

    public synchronized void toggleEnabled(String id) {

        FileConfiguration config = getConfig();

        if (config == null || !config.contains("rules." + id)) {
            return;
        }

        String path = "rules." + id + ".enabled";

        config.set(
                path,
                !config.getBoolean(path, true)
        );

        saveAndReload();
    }

    // =========================================================
    // WORD MANAGEMENT
    // =========================================================

    public synchronized void addWord(
            String id,
            String word
    ) {

        FileConfiguration config = getConfig();

        if (config == null
                || word == null
                || word.isBlank()
                || !config.contains("rules." + id)) {
            return;
        }

        List<String> words =
                config.getStringList("rules." + id + ".words");

        boolean alreadyExists = words.stream()
                .anyMatch(existing ->
                        existing.equalsIgnoreCase(word)
                );

        if (alreadyExists) {
            return;
        }

        words.add(word);

        config.set(
                "rules." + id + ".words",
                words
        );

        saveAndReload();
    }

    public synchronized void removeWord(
            String id,
            String word
    ) {

        FileConfiguration config = getConfig();

        if (config == null || !config.contains("rules." + id)) {
            return;
        }

        List<String> words =
                config.getStringList("rules." + id + ".words");

        words.removeIf(existing ->
                existing.equalsIgnoreCase(word)
        );

        config.set(
                "rules." + id + ".words",
                words
        );

        saveAndReload();
    }

    // =========================================================
    // ACTION MANAGEMENT
    // =========================================================

    public synchronized void toggleAction(
            String id,
            FilterAction action
    ) {

        FileConfiguration config = getConfig();

        if (config == null
                || action == null
                || !config.contains("rules." + id)) {
            return;
        }

        String path = "rules." + id + ".actions";

        List<String> actions =
                config.getStringList(path);

        boolean removed = actions.removeIf(existing ->
                existing.equalsIgnoreCase(action.name())
        );

        if (!removed) {
            actions.add(action.name());
        }

        config.set(path, actions);

        saveAndReload();
    }

    // =========================================================
    // PUNISHMENT SETTINGS
    // =========================================================

    public synchronized void setDuration(
            String id,
            String duration
    ) {

        FileConfiguration config = getConfig();

        if (config == null
                || duration == null
                || duration.isBlank()
                || !config.contains("rules." + id)) {
            return;
        }

        config.set(
                "rules." + id + ".punishment.duration",
                duration
        );

        saveAndReload();
    }

    public synchronized void setReason(
            String id,
            String reason
    ) {

        FileConfiguration config = getConfig();

        if (config == null
                || reason == null
                || reason.isBlank()
                || !config.contains("rules." + id)) {
            return;
        }

        config.set(
                "rules." + id + ".punishment.reason",
                reason
        );

        saveAndReload();
    }

    // =========================================================
    // RULE DELETION
    // =========================================================

    public synchronized void deleteRule(String id) {

        FileConfiguration config = getConfig();

        if (config == null || !config.contains("rules." + id)) {
            return;
        }

        config.set("rules." + id, null);

        saveAndReload();
    }

    // =========================================================
    // RULE INFORMATION
    // =========================================================

    public synchronized String getDisplayName(String id) {

        FileConfiguration config = getConfig();

        if (config == null) {
            return id;
        }

        return config.getString(
                "rules." + id + ".display-name",
                id
        );
    }

    public synchronized String getDuration(String id) {

        FileConfiguration config = getConfig();

        if (config == null) {
            return "30m";
        }

        return config.getString(
                "rules." + id + ".punishment.duration",
                "30m"
        );
    }

    public synchronized String getReason(String id) {

        FileConfiguration config = getConfig();

        if (config == null) {
            return "Chat Filter Violation";
        }

        return config.getString(
                "rules." + id + ".punishment.reason",
                "Chat Filter Violation"
        );
    }

    // =========================================================
    // SAVE AND RELOAD
    // =========================================================

    private FileConfiguration getConfig() {
        return configManager.get("chatfilter.yml");
    }

    private synchronized void saveAndReload() {

        configManager.save("chatfilter.yml");

        reload();
    }
}
