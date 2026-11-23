
package walson.one.walsonChatManager.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GUIListener implements Listener {

    private final MuteGUI muteGUI;
    private final UnmuteGUI unmuteGUI;
    private final ChatFilterGUI chatFilterGUI;
    private final MuteHistoryGUI muteHistoryGUI;
    private final ChatManagerGUI chatManagerGUI;

    public GUIListener(
            MuteGUI muteGUI,
            UnmuteGUI unmuteGUI,
            ChatFilterGUI chatFilterGUI,
            MuteHistoryGUI muteHistoryGUI,
            ChatManagerGUI chatManagerGUI
    ) {
        this.muteGUI = muteGUI;
        this.unmuteGUI = unmuteGUI;
        this.chatFilterGUI = chatFilterGUI;
        this.muteHistoryGUI = muteHistoryGUI;
        this.chatManagerGUI = chatManagerGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getInventory().getHolder(false)
                instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        if (slot < 0
                || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        String option = holder.getOption(slot);

        if (option == null) {
            return;
        }

        switch (holder.getType()) {

            case MUTE_DURATION ->
                    muteGUI.selectDuration(player, holder, option);

            case MUTE_REASON ->
                    muteGUI.selectReason(player, holder, option);

            case UNMUTE_REASON ->
                    unmuteGUI.selectReason(player, holder, option);

            case FILTER_LIST, FILTER_EDITOR, FILTER_WORDS ->
                    chatFilterGUI.handleClick(player, holder, slot);

            case MUTE_HISTORY ->
                    muteHistoryGUI.handleClick(player, holder, option);

            case CHAT_MANAGER ->
                    chatManagerGUI.handleClick(player, holder, option);

            case SOUND_SETTINGS ->
                    chatManagerGUI.handleSoundSettingsClick(player, holder, option);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {

        if (event.getInventory().getHolder(false)
                instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
