
package walson.one.walsonChatManager.managers;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputManager {

    private final JavaPlugin plugin;

    private final Map<UUID, Consumer<String>> pendingInputs =
            new ConcurrentHashMap<>();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ChatInputManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(
            Player player,
            String prompt,
            Consumer<String> callback
    ) {
        pendingInputs.put(player.getUniqueId(), callback);

        player.closeInventory();
        player.sendMessage(miniMessage.deserialize(prompt));
        player.sendMessage(
                miniMessage.deserialize("<gray>Type <red>cancel</red> to cancel.")
        );
    }

    public boolean handle(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Consumer<String> callback = pendingInputs.remove(uuid);

        if (callback == null) {
            return false;
        }

        event.setCancelled(true);

        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message())
                .trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();

            if (!player.isOnline()) {
                return;
            }

            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                        miniMessage.deserialize("<red>Input cancelled.")
                );
                return;
            }

            if (input.isBlank()) {
                player.sendMessage(
                        miniMessage.deserialize("<red>Input cannot be empty.")
                );
                return;
            }

            callback.accept(input);
        });

        return true;
    }

    public void cancel(UUID uuid) {
        pendingInputs.remove(uuid);
    }

    public boolean isWaiting(UUID uuid) {
        return pendingInputs.containsKey(uuid);
    }
}
