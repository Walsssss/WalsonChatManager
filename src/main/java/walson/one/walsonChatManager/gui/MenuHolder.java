
package walson.one.walsonChatManager.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MenuHolder implements InventoryHolder {

    public enum MenuType {
        MUTE_DURATION,
        MUTE_REASON,
        UNMUTE_REASON,
        FILTER_LIST,
        FILTER_EDITOR,
        FILTER_WORDS,
        MUTE_HISTORY,
        CHAT_MANAGER,
        SOUND_SETTINGS
    }

    private final MenuType type;
    private final UUID targetUUID;
    private final String targetName;
    private final String ruleId;

    private Long durationMillis;
    private int page;
    private boolean silentAnnouncement;

    private final Map<Integer, String> options = new HashMap<>();

    private Inventory inventory;

    public MenuHolder(
            MenuType type,
            UUID targetUUID,
            String targetName
    ) {
        this(type, targetUUID, targetName, null);
    }

    public MenuHolder(
            MenuType type,
            UUID targetUUID,
            String targetName,
            String ruleId
    ) {
        this.type = type;
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.ruleId = ruleId;
    }

    public MenuType getType() {
        return type;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getRuleId() {
        return ruleId;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public boolean isSilentAnnouncement() {
        return silentAnnouncement;
    }

    public void setSilentAnnouncement(boolean silentAnnouncement) {
        this.silentAnnouncement = silentAnnouncement;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void addOption(int slot, String value) {
        options.put(slot, value);
    }

    public String getOption(int slot) {
        return options.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
