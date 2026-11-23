
package walson.one.walsonChatManager.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import walson.one.walsonChatManager.WalsonChatManager;
import walson.one.walsonChatManager.managers.ChatInputManager;
import walson.one.walsonChatManager.managers.MutePresetManager;
import walson.one.walsonChatManager.utils.MessageUtils;

import java.util.List;
import java.util.Map;

public class UnmuteGUI {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private final WalsonChatManager plugin;
    private final MutePresetManager presets;
    private final ChatInputManager chatInput;

    public UnmuteGUI(
            WalsonChatManager plugin,
            MutePresetManager presets,
            ChatInputManager chatInput
    ) {
        this.plugin = plugin;
        this.presets = presets;
        this.chatInput = chatInput;
    }

    public void open(Player staff, java.util.UUID uuid, String username) {
        MenuHolder holder = new MenuHolder(
                MenuHolder.MenuType.UNMUTE_REASON,
                uuid,
                username
        );

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                MINI_MESSAGE.deserialize("<dark_gray>Unmute Player")
        );

        holder.setInventory(inventory);

        ItemStack filler = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " "
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(
                4,
                createItem(
                        Material.PLAYER_HEAD,
                        "<yellow>" + username,
                        "<gray>Select an unmute reason."
                )
        );

        List<MutePresetManager.Preset> reasons =
                presets.getUnmuteReasons();

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
                        "<gray>Enter your own unmute reason.",
                        "",
                        "<green>Click to enter"
                )
        );

        holder.addOption(49, "__CUSTOM__");

        staff.openInventory(inventory);
    }

    public void selectReason(
            Player staff,
            MenuHolder holder,
            String reason
    ) {
        if (reason.equals("__CUSTOM__")) {
            chatInput.request(
                    staff,
                    "<yellow>Enter a custom unmute reason:",
                    input -> applyUnmute(staff, holder, input)
            );

            return;
        }

        applyUnmute(staff, holder, reason);
    }

    private void applyUnmute(
            Player staff,
            MenuHolder holder,
            String reason
    ) {
        if (reason.isBlank()) {
            staff.sendMessage(
                    MINI_MESSAGE.deserialize("<red>Reason cannot be empty.")
            );
            return;
        }

        if (!staff.hasPermission("walsonchatmanager.mute")) {
            return;
        }

        boolean success = plugin.getMuteManager().unmute(
                holder.getTargetUUID(),
                reason,
                staff.getName()
        );

        if (!success) {
            staff.sendMessage(
                    MINI_MESSAGE.deserialize("<red>This player is not muted.")
            );
            return;
        }

        MessageUtils.send(
                staff,
                plugin.getMessages(),
                "unmute.success",
                Map.of("player", holder.getTargetName())
        );

        org.bukkit.OfflinePlayer target =
                Bukkit.getOfflinePlayer(holder.getTargetUUID());

        if (target.isOnline() && target.getPlayer() != null) {
            MessageUtils.send(
                    target.getPlayer(),
                    plugin.getMessages(),
                    "unmute.received"
            );
        }
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
                    java.util.Arrays.stream(lore)
                            .map(MINI_MESSAGE::deserialize)
                            .toList()
            );
        }

        item.setItemMeta(meta);
        return item;
    }
}
