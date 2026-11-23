
package walson.one.walsonChatManager.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.managers.GlobalChatManager;
import walson.one.walsonChatManager.utils.MessageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatManagerGUI {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final WalsonChatManager plugin;
    private final MuteGUI muteGUI;
    private final UnmuteGUI unmuteGUI;
    private final MuteHistoryGUI muteHistoryGUI;
    private final ChatFilterGUI chatFilterGUI;
    private final ChatInputManager chatInputManager;
    private final GlobalChatManager globalChatManager;

    public ChatManagerGUI(
            WalsonChatManager plugin,
            MuteGUI muteGUI,
            UnmuteGUI unmuteGUI,
            MuteHistoryGUI muteHistoryGUI,
            ChatFilterGUI chatFilterGUI,
            ChatInputManager chatInputManager,
            GlobalChatManager globalChatManager
    ) {
        this.plugin = plugin;
        this.muteGUI = muteGUI;
        this.unmuteGUI = unmuteGUI;
        this.muteHistoryGUI = muteHistoryGUI;
        this.chatFilterGUI = chatFilterGUI;
        this.chatInputManager = chatInputManager;
        this.globalChatManager = globalChatManager;
    }

    // =========================================================
    // MAIN MENU
    // =========================================================

    public void openMain(Player player) {

        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.CHAT_MANAGER,
                null,
                null
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                MINI_MESSAGE.deserialize(
                        "<dark_gray>WalsonChatManager <gray>- <light_purple>Control Panel"
                )
        );

        holder.setInventory(inventory);

        fill(inventory);

        // Mute player
        inventory.setItem(20, createItem(
                Material.ANVIL,
                "<light_purple>Mute Player",
                List.of(
                        "<gray>Issue a mute punishment.",
                        "",
                        "<yellow>Click to select a player"
                )
        ));
        holder.addOption(20, "mute");

        // Unmute player
        inventory.setItem(21, createItem(
                Material.MILK_BUCKET,
                "<green>Unmute Player",
                List.of(
                        "<gray>Remove an active mute.",
                        "",
                        "<yellow>Click to select a player"
                )
        ));
        holder.addOption(21, "unmute");

        // Mute history
        inventory.setItem(22, createItem(
                Material.WRITABLE_BOOK,
                "<aqua>Mute History",
                List.of(
                        "<gray>View a player's punishment history.",
                        "",
                        "<yellow>Click to select a player"
                )
        ));
        holder.addOption(22, "history");

        // Chat filter
        inventory.setItem(23, createItem(
                Material.SHIELD,
                "<light_purple>Chat Filter",
                List.of(
                        "<gray>Manage filter categories and rules.",
                        "",
                        "<yellow>Click to open"
                )
        ));
        holder.addOption(23, "filter");

        // Global chat
        boolean muted = globalChatManager.isMuted();

        inventory.setItem(24, createItem(
                muted ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                "<yellow>Global Chat",
                List.of(
                        "<gray>Status: " + (muted
                                ? "<red>Disabled"
                                : "<green>Enabled"),
                        "",
                        "<gray>Click to " + (muted
                                ? "enable"
                                : "disable") + " global chat.",
                        "",
                        "<yellow>Click to toggle"
                )
        ));
        holder.addOption(24, "global");

        // Reload
        inventory.setItem(31, createItem(
                Material.COMPASS,
                "<gold>Reload Configuration",
                List.of(
                        "<gray>Reload plugin configuration files.",
                        "<gray>Refresh filter rules and presets.",
                        "",
                        "<yellow>Click to reload"
                )
        ));
        holder.addOption(31, "reload");

        // Close
        inventory.setItem(49, createItem(
                Material.BARRIER,
                "<red>Close",
                List.of("<gray>Close this menu.")
        ));
        holder.addOption(49, "close");

        player.openInventory(inventory);
    }

    // =========================================================
    // CLICK HANDLER
    // =========================================================

    public void handleClick(
            Player player,
            MenuHolder holder,
            String option
    ) {
        switch (option) {
            case "mute" -> requestMuteTarget(player);

            case "unmute" -> requestUnmuteTarget(player);

            case "history" -> requestHistoryTarget(player);

            case "filter" -> {
                if (!checkPermission(
                        player,
                        "walsonchatmanager.filter"
                )) {
                    return;
                }

                chatFilterGUI.openMain(player);
            }

            case "global" -> toggleGlobalChat(player);

            case "reload" -> reloadConfigurations(player);

            case "close" -> player.closeInventory();

            default -> {
            }
        }
    }

    // =========================================================
    // MUTE PLAYER
    // =========================================================

    private void requestMuteTarget(Player staff) {

        if (!checkPermission(staff, "walsonchatmanager.mute")) {
            return;
        }

        chatInputManager.request(
                staff,
                "<yellow>Enter the username of the player you want to mute:",
                input -> {

                    OfflinePlayer target = Bukkit.getOfflinePlayer(input);

                    if (!target.isOnline() && !target.hasPlayedBefore()) {
                        staff.sendMessage(MINI_MESSAGE.deserialize(
                                "<red>Player not found. They must have joined the server before."
                        ));
                        openMain(staff);
                        return;
                    }

                    muteGUI.openDurationMenu(staff, target);
                }
        );
    }

    // =========================================================
    // UNMUTE PLAYER
    // =========================================================

    private void requestUnmuteTarget(Player staff) {

        if (!checkPermission(staff, "walsonchatmanager.mute")) {
            return;
        }

        chatInputManager.request(
                staff,
                "<yellow>Enter the username of the player you want to unmute:",
                input -> {

                    UUID uuid = plugin.getMuteManager()
                            .findPlayerUUID(input);

                    if (uuid == null
                            || !plugin.getMuteManager().isMuted(uuid)) {

                        staff.sendMessage(MINI_MESSAGE.deserialize(
                                "<red>That player does not have an active mute."
                        ));

                        openMain(staff);
                        return;
                    }

                    String username = plugin.getMuteManager()
                            .findPlayerName(uuid);

                    if (username == null) {
                        username = input;
                    }

                    unmuteGUI.open(staff, uuid, username);
                }
        );
    }

    // =========================================================
    // MUTE HISTORY
    // =========================================================

    private void requestHistoryTarget(Player staff) {

        if (!checkPermission(
                staff,
                "walsonchatmanager.mutehistory"
        )) {
            return;
        }

        chatInputManager.request(
                staff,
                "<yellow>Enter the username of the player whose history you want to view:",
                input -> {

                    UUID uuid = plugin.getMuteManager()
                            .findPlayerUUID(input);

                    if (uuid == null
                            || plugin.getMuteManager()
                            .getMuteHistoryCount(uuid) == 0) {

                        staff.sendMessage(MINI_MESSAGE.deserialize(
                                "<red>No mute history found for <white>"
                                        + input + "</white>."
                        ));

                        openMain(staff);
                        return;
                    }

                    String username = plugin.getMuteManager()
                            .findPlayerName(uuid);

                    if (username == null) {
                        username = input;
                    }

                    muteHistoryGUI.open(
                            staff,
                            uuid,
                            username,
                            0
                    );
                }
        );
    }

    // =========================================================
    // GLOBAL CHAT
    // =========================================================

    private void toggleGlobalChat(Player staff) {

        if (!checkPermission(
                staff,
                "walsonchatmanager.muteglobal"
        )) {
            return;
        }

        globalChatManager.toggle(staff.getName());

        // Refresh the menu so the status is immediately updated.
        openMain(staff);
    }

    // =========================================================
    // RELOAD
    // =========================================================

    private void reloadConfigurations(Player staff) {

        if (!checkPermission(
                staff,
                "walsonchatmanager.reload"
        )) {
            return;
        }

        try {
            plugin.getConfigManager().reloadFiles();

            plugin.getMutePresetManager().reload();
            plugin.getChatFilterManager().reload();
            plugin.getGlobalChatManager().reload();

            MessageUtils.send(
                    staff,
                    plugin.getMessages(),
                    "general.reload-success"
            );

            plugin.getLogger().info(
                    staff.getName()
                            + " reloaded configuration from the Chat Manager GUI."
            );

            openMain(staff);

        } catch (Exception exception) {

            plugin.getLogger().severe(
                    "GUI configuration reload failed: "
                            + exception.getMessage()
            );

            staff.sendMessage(MINI_MESSAGE.deserialize(
                    "<red>Configuration reload failed. Check the console."
            ));
        }
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    private boolean checkPermission(
            Player player,
            String permission
    ) {
        if (player.hasPermission(permission)) {
            return true;
        }

        MessageUtils.send(
                player,
                plugin.getMessages(),
                "general.no-permission"
        );

        return false;
    }

    // =========================================================
    // INVENTORY HELPERS
    // =========================================================

    private void fill(Inventory inventory) {

        ItemStack filler = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(MINI_MESSAGE.deserialize(name));

        List<Component> components = new ArrayList<>();

        for (String line : lore) {
            components.add(MINI_MESSAGE.deserialize(line));
        }

        meta.lore(components);

        item.setItemMeta(meta);

        return item;
    }
}
