
package walson.one.walsonChatManager.gui;

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
import walson.one.walsonChatManager.managers.MutePresetManager;
import walson.one.walsonChatManager.utils.MessageUtils;
import walson.one.walsonChatManager.utils.TimeUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MuteGUI {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final WalsonChatManager plugin;
    private final MutePresetManager presets;
    private final ChatInputManager chatInput;

    public MuteGUI(
            WalsonChatManager plugin,
            MutePresetManager presets,
            ChatInputManager chatInput
    ) {
        this.plugin = plugin;
        this.presets = presets;
        this.chatInput = chatInput;
    }

    public void openDurationMenu(Player staff, OfflinePlayer target) {
        String username = target.getName() == null
                ? "Unknown"
                : target.getName();

        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.MUTE_DURATION,
                target.getUniqueId(),
                username
        );

        Inventory inventory = createInventory(
                holder,
                "<dark_gray>Mute Player <gray>- <white>" + username
        );

        List<MutePresetManager.Preset> durations = presets.getDurations();

        int index = 0;

        for (MutePresetManager.Preset preset : durations) {
            if (index >= SLOTS.length) break;

            int slot = SLOTS[index++];

            Material material = preset.value().equalsIgnoreCase("PERMANENT")
                    ? Material.BARRIER
                    : Material.CLOCK;

            inventory.setItem(
                    slot,
                    createItem(
                            material,
                            "<yellow>" + preset.display(),
                            "<gray>Duration: <white>" + preset.value(),
                            "",
                            "<green>Click to select"
                    )
            );

            holder.addOption(slot, preset.value());
        }

        inventory.setItem(
                49,
                createItem(
                        Material.NAME_TAG,
                        "<light_purple>Custom Duration",
                        "<gray>Enter your own duration.",
                        "<gray>Example: <white>45m",
                        "",
                        "<green>Click to enter"
                )
        );

        holder.addOption(49, "__CUSTOM__");

        staff.openInventory(inventory);
    }

    public void openReasonMenu(
            Player staff,
            MenuHolder previous,
            long duration
    ) {
        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.MUTE_REASON,
                previous.getTargetUUID(),
                previous.getTargetName()
        );

        holder.setDurationMillis(duration);

        Inventory inventory = createInventory(
                holder,
                "<dark_gray>Select Mute Reason"
        );

        inventory.setItem(
                4,
                createItem(
                        Material.CLOCK,
                        "<yellow>Selected Duration",
                        "<gray>" + TimeUtils.formatDuration(duration)
                )
        );

        List<MutePresetManager.Preset> reasons = presets.getMuteReasons();

        int index = 0;

        for (MutePresetManager.Preset preset : reasons) {
            if (index >= SLOTS.length) break;

            int slot = SLOTS[index++];

            inventory.setItem(
                    slot,
                    createItem(
                            Material.PAPER,
                            "<yellow>" + preset.display(),
                            "<gray>Reason: <white>" + preset.value(),
                            "",
                            "<green>Click to select"
                    )
            );

            holder.addOption(slot, preset.value());
        }

        inventory.setItem(
                49,
                createItem(
                        Material.WRITABLE_BOOK,
                        "<light_purple>Custom Reason",
                        "<gray>Enter your own reason.",
                        "",
                        "<green>Click to enter"
                )
        );

        holder.addOption(49, "__CUSTOM__");

        staff.openInventory(inventory);
    }

    public void selectDuration(
            Player staff,
            MenuHolder holder,
            String value
    ) {
        if (value.equals("__CUSTOM__")) {
            chatInput.request(
                    staff,
                    "<yellow>Enter a custom mute duration (e.g. 45m):",
                    input -> {
                        try {
                            long duration = TimeUtils.parseDuration(input);
                            openReasonMenu(staff, holder, duration);
                        } catch (IllegalArgumentException exception) {
                            staff.sendMessage(
                                    MINI_MESSAGE.deserialize(
                                            "<red>Invalid duration. Try 30m, 2h, 1d or permanent."
                                    )
                            );
                        }
                    }
            );
            return;
        }

        try {
            long duration = TimeUtils.parseDuration(value);
            openReasonMenu(staff, holder, duration);
        } catch (IllegalArgumentException exception) {
            staff.sendMessage(
                    MINI_MESSAGE.deserialize("<red>Invalid duration preset.")
            );
        }
    }

    public void selectReason(
            Player staff,
            MenuHolder holder,
            String reason
    ) {
        if (reason.equals("__CUSTOM__")) {
            chatInput.request(
                    staff,
                    "<yellow>Enter a custom mute reason:",
                    input -> applyMute(staff, holder, input)
            );
            return;
        }

        applyMute(staff, holder, reason);
    }

    private void applyMute(
            Player staff,
            MenuHolder holder,
            String reason
    ) {
        if (!staff.hasPermission("walsonchatmanager.mute")) {
            return;
        }

        if (reason.isBlank() || holder.getDurationMillis() == null) {
            staff.sendMessage(
                    MINI_MESSAGE.deserialize("<red>Invalid mute information.")
            );
            return;
        }

        long duration = holder.getDurationMillis();

        if (plugin.getMuteManager().isMuted(holder.getTargetUUID())) {
            staff.sendMessage(
                    MINI_MESSAGE.deserialize("<red>This player is already muted.")
            );
            return;
        }

        boolean success = plugin.getMuteManager().mute(
                holder.getTargetUUID(),
                holder.getTargetName(),
                reason,
                staff.getName(),
                duration
        );

        if (!success) {
            staff.sendMessage(
                    MINI_MESSAGE.deserialize(
                            "<red>Could not save the mute. Check the console."
                    )
            );
            return;
        }

        MessageUtils.send(
                staff,
                plugin.getMessages(),
                "mute.success",
                Map.of("player", holder.getTargetName())
        );

        OfflinePlayer target = Bukkit.getOfflinePlayer(holder.getTargetUUID());

        if (target.isOnline() && target.getPlayer() != null) {
            Player online = target.getPlayer();

            MessageUtils.send(
                    online,
                    plugin.getMessages(),
                    "mute.received"
            );

            MessageUtils.send(
                    online,
                    plugin.getMessages(),
                    "mute.received-reason",
                    Map.of("reason", reason)
            );

            MessageUtils.send(
                    online,
                    plugin.getMessages(),
                    "mute.received-duration",
                    Map.of("duration", TimeUtils.formatDuration(duration))
            );
        }
    }

    private Inventory createInventory(MenuHolder holder, String title) {
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                MINI_MESSAGE.deserialize(title)
        );

        holder.setInventory(inventory);

        ItemStack filler = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " "
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        return inventory;
    }

    private ItemStack createItem(
            Material material,
            String name,
            String... lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(MINI_MESSAGE.deserialize(name));

        if (lore.length > 0) {
            meta.lore(
                    Arrays.stream(lore)
                            .map(MINI_MESSAGE::deserialize)
                            .toList()
            );
        }

        item.setItemMeta(meta);

        return item;
    }
}
