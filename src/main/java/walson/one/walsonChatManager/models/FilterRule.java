
package walson.one.walsonChatManager.models;

import java.util.List;

public record FilterRule(
        String id,
        boolean enabled,
        int priority,
        FilterMatchType matchType,
        List<String> words,
        List<FilterAction> actions,
        String duration,
        String reason
) {
    public FilterRule {
        words = List.copyOf(words);
        actions = List.copyOf(actions);
    }

    public boolean hasAction(FilterAction action) {
        return actions.contains(action);
    }

    public boolean blocksMessage() {
        return hasAction(FilterAction.BLOCK_MESSAGE)
                || hasAction(FilterAction.MUTE)
                || hasAction(FilterAction.TEMPORARY_MUTE);
    }
}
