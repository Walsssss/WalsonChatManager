
package walson.one.walsonChatManager.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import walson.one.walsonChatManager.utils.TimeUtils;

import walson.one.walsonChatManager.managers.ChatFilterManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.models.FilterAction;
import walson.one.walsonChatManager.models.FilterRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatFilterGUI {

    private final JavaPlugin plugin;
    private final ChatFilterManager filterManager;
    private final ChatInputManager chatInputManager;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatFilterGUI(
            JavaPlugin plugin,
            ChatFilterManager filterManager,
            ChatInputManager chatInputManager
    ) {
        this.plugin = plugin;
        this.filterManager = filterManager;
        this.chatInputManager = chatInputManager;
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.FILTER_LIST,
                null,
                null
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                miniMessage.deserialize("<dark_gray>Chat Filter <dark_gray>» <light_purple>Categories")
        );

        fill(inventory);

        List<FilterRule> rules = filterManager.getRules();

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < Math.min(rules.size(), slots.length); i++) {
            FilterRule rule = rules.get(i);
            int slot = slots[i];

            Material material = rule.enabled()
                    ? Material.LIME_DYE
                    : Material.GRAY_DYE;

            inventory.setItem(slot, createItem(
                    material,
                    "<light_purple>" + filterManager.getDisplayName(rule.id()),
                    List.of(
                            "<gray>ID: <white>" + rule.id(),
                            "<gray>Status: " + (rule.enabled()
                                    ? "<green>Enabled"
                                    : "<red>Disabled"),
                            "<gray>Priority: <white>" + rule.priority(),
                            "<gray>Words: <white>" + rule.words().size(),
                            "",
                            "<yellow>Click to edit"
                    )
            ));

            holder.addOption(slot, rule.id());
        }

        inventory.setItem(49, createItem(
                Material.EMERALD,
                "<green>Create Category",
                List.of(
                        "<gray>Create a new filter category.",
                        "",
                        "<yellow>Click to create"
                )
        ));

        holder.addOption(49, "create");

        player.openInventory(inventory);
        holder.setInventory(inventory);
    }

    public void openEditor(Player player, String ruleId) {
        FilterRule rule = filterManager.getRule(ruleId);

        if (rule == null) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>This filter category no longer exists."
            ));
            openMain(player);
            return;
        }

        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.FILTER_EDITOR,
                null,
                null,
                ruleId
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                miniMessage.deserialize(
                        "<dark_gray>Editing: <light_purple>"
                                + filterManager.getDisplayName(ruleId)
                )
        );

        fill(inventory);

        inventory.setItem(10, createItem(
                rule.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<yellow>Toggle Category",
                List.of(
                        "<gray>Current: " + (rule.enabled()
                                ? "<green>Enabled"
                                : "<red>Disabled"),
                        "",
                        "<yellow>Click to toggle"
                )
        ));
        holder.addOption(10, "toggle");

        inventory.setItem(11, createItem(
                Material.NAME_TAG,
                "<green>Add Blocked Word",
                List.of(
                        "<gray>Add a word or phrase to this category.",
                        "",
                        "<yellow>Click to add"
                )
        ));
        holder.addOption(11, "add-word");

        inventory.setItem(12, createItem(
                Material.BOOK,
                "<aqua>Manage Blocked Words",
                List.of(
                        "<gray>View or remove existing words.",
                        "<gray>Current words: <white>" + rule.words().size(),
                        "",
                        "<yellow>Click to manage"
                )
        ));
        holder.addOption(12, "words");

        addAction(inventory, holder, 14, FilterAction.BLOCK_MESSAGE,
                Material.BARRIER, "Block Message", rule);
        addAction(inventory, holder, 15, FilterAction.WARNING,
                Material.PAPER, "Warning", rule);
        addAction(inventory, holder, 16, FilterAction.MUTE,
                Material.IRON_BLOCK, "Permanent Mute", rule);
        addAction(inventory, holder, 17, FilterAction.TEMPORARY_MUTE,
                Material.CLOCK, "Temporary Mute", rule);

        inventory.setItem(22, createItem(
                Material.CLOCK,
                "<yellow>Set Mute Duration",
                List.of(
                        "<gray>Current: <white>" + filterManager.getDuration(ruleId),
                        "",
                        "<yellow>Click to change"
                )
        ));
        holder.addOption(22, "duration");

        inventory.setItem(23, createItem(
                Material.WRITABLE_BOOK,
                "<yellow>Set Punishment Reason",
                List.of(
                        "<gray>Current: <white>" + filterManager.getReason(ruleId),
                        "",
                        "<yellow>Click to change"
                )
        ));
        holder.addOption(23, "reason");

        inventory.setItem(31, createItem(
                Material.TNT,
                "<red>Delete Category",
                List.of(
                        "<gray>Permanently deletes this filter category.",
                        "",
                        "<red>Click to delete"
                )
        ));
        holder.addOption(31, "delete");

        inventory.setItem(45, createItem(
                Material.ARROW,
                "<gray>Back",
                List.of("<yellow>Return to categories")
        ));
        holder.addOption(45, "back");

        player.openInventory(inventory);
        holder.setInventory(inventory);
    }

    private void addAction(
            Inventory inventory,
            MenuHolder holder,
            int slot,
            FilterAction action,
            Material material,
            String display,
            FilterRule rule
    ) {
        boolean active = rule.hasAction(action);

        inventory.setItem(slot, createItem(
                active ? Material.LIME_DYE : material,
                "<yellow>" + display,
                List.of(
                        "<gray>Status: " + (active
                                ? "<green>Enabled"
                                : "<red>Disabled"),
                        "",
                        "<yellow>Click to toggle"
                )
        ));

        holder.addOption(slot, "action:" + action.name());
    }

    public void openWords(Player player, String ruleId) {
        FilterRule rule = filterManager.getRule(ruleId);

        if (rule == null) {
            openMain(player);
            return;
        }

        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.FILTER_WORDS,
                null,
                null,
                ruleId
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                miniMessage.deserialize("<dark_gray>Blocked Words")
        );

        fill(inventory);

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < Math.min(rule.words().size(), slots.length); i++) {
            String word = rule.words().get(i);
            int slot = slots[i];

            inventory.setItem(slot, createItem(
                    Material.PAPER,
                    "<white>" + word,
                    List.of(
                            "<gray>Click to remove this word."
                    )
            ));

            holder.addOption(slot, "remove:" + word);
        }

        inventory.setItem(49, createItem(
                Material.ARROW,
                "<gray>Back",
                List.of("<yellow>Return to category editor")
        ));
        holder.addOption(49, "back");

        player.openInventory(inventory);
        holder.setInventory(inventory);
    }

    public void handleClick(Player player, MenuHolder holder, int slot) {
        String option = holder.getOption(slot);

        if (option == null) {
            return;
        }

        switch (holder.getType()) {
            case FILTER_LIST -> {
                if (option.equals("create")) {
                    requestCategory(player);
                } else {
                    openEditor(player, option);
                }
            }

            case FILTER_EDITOR -> handleEditorClick(
                    player,
                    holder.getRuleId(),
                    option
            );

            case FILTER_WORDS -> handleWordsClick(
                    player,
                    holder.getRuleId(),
                    option
            );

            default -> {
            }
        }
    }

    private void handleEditorClick(
            Player player,
            String ruleId,
            String option
    ) {
        switch (option) {
            case "toggle" -> {
                filterManager.toggleEnabled(ruleId);
                openEditor(player, ruleId);
            }

            case "add-word" -> requestWord(player, ruleId);

            case "words" -> openWords(player, ruleId);

            case "duration" -> requestDuration(player, ruleId);

            case "reason" -> requestReason(player, ruleId);

            case "delete" -> {
                filterManager.deleteRule(ruleId);
                player.sendMessage(miniMessage.deserialize(
                        "<green>Filter category deleted."
                ));
                openMain(player);
            }

            case "back" -> openMain(player);

            default -> {
                if (option.startsWith("action:")) {
                    String actionName = option.substring("action:".length());

                    try {
                        FilterAction action = FilterAction.valueOf(actionName);
                        filterManager.toggleAction(ruleId, action);
                        openEditor(player, ruleId);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private void handleWordsClick(
            Player player,
            String ruleId,
            String option
    ) {
        if (option.equals("back")) {
            openEditor(player, ruleId);
            return;
        }

        if (option.startsWith("remove:")) {
            String word = option.substring("remove:".length());
            filterManager.removeWord(ruleId, word);

            player.sendMessage(miniMessage.deserialize(
                    "<green>Removed <white>" + word + "</white> from the filter."
            ));

            openWords(player, ruleId);
        }
    }

    private void requestCategory(Player player) {
        player.closeInventory();

        chatInputManager.request(
                player,
                "<yellow>Enter a name for the new filter category. Type cancel to stop.",
                input -> {
                    String displayName = input.trim();

                    if (displayName.isBlank() || displayName.length() > 32) {
                        player.sendMessage(miniMessage.deserialize(
                                "<red>Category names must be between 1 and 32 characters."
                        ));
                        openMain(player);
                        return;
                    }

                    String id = displayName.toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9]+", "_")
                            .replaceAll("^_+|_+$", "");

                    if (id.isBlank() || filterManager.getRule(id) != null) {
                        player.sendMessage(miniMessage.deserialize(
                                "<red>A category with that name already exists or is invalid."
                        ));
                        openMain(player);
                        return;
                    }

                    filterManager.addRule(id, displayName);

                    player.sendMessage(miniMessage.deserialize(
                            "<green>Created filter category: <white>" + displayName
                    ));

                    openEditor(player, id);
                }
        );
    }

    private void requestWord(Player player, String ruleId) {
        player.closeInventory();

        chatInputManager.request(
                player,
                "<yellow>Enter the word or phrase to block. Type cancel to stop.",
                input -> {
                    String word = input.trim();

                    if (word.isBlank() || word.length() > 128) {
                        player.sendMessage(miniMessage.deserialize(
                                "<red>Words must be between 1 and 128 characters."
                        ));
                        openEditor(player, ruleId);
                        return;
                    }

                    filterManager.addWord(ruleId, word);

                    player.sendMessage(miniMessage.deserialize(
                            "<green>Added <white>" + word + "</white> to the filter."
                    ));

                    openWords(player, ruleId);
                }
        );
    }

    private void requestDuration(Player player, String ruleId) {
        player.closeInventory();

        chatInputManager.request(
                player,
                "<yellow>Enter a duration (examples: 10m, 1h, 1d). Type cancel to stop.",
                input -> {
                    try {
                        long duration = TimeUtils.parseDuration(input.trim());

                        if (duration <= 0) {
                            throw new IllegalArgumentException("Invalid duration");
                        }

                        filterManager.setDuration(ruleId, input.trim());

                        player.sendMessage(miniMessage.deserialize(
                                "<green>Mute duration updated."
                        ));
                    } catch (Exception exception) {
                        player.sendMessage(miniMessage.deserialize(
                                "<red>Invalid duration. Examples: 10m, 1h, 1d."
                        ));
                    }

                    openEditor(player, ruleId);
                }
        );
    }

    private void requestReason(Player player, String ruleId) {
        player.closeInventory();

        chatInputManager.request(
                player,
                "<yellow>Enter the punishment reason. Type cancel to stop.",
                input -> {
                    String reason = input.trim();

                    if (reason.isBlank() || reason.length() > 128) {
                        player.sendMessage(miniMessage.deserialize(
                                "<red>Reason must be between 1 and 128 characters."
                        ));
                        openEditor(player, ruleId);
                        return;
                    }

                    filterManager.setReason(ruleId, reason);

                    player.sendMessage(miniMessage.deserialize(
                            "<green>Punishment reason updated."
                    ));

                    openEditor(player, ruleId);
                }
        );
    }

    private void fill(Inventory inventory) {
        ItemStack filler = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(miniMessage.deserialize(name));

        List<Component> components = new ArrayList<>();

        for (String line : lore) {
            components.add(miniMessage.deserialize(line));
        }

        meta.lore(components);
        item.setItemMeta(meta);

        return item;
    }
}
