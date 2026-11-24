package walson.one.walsonChatManager.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static long parseDuration(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be empty.");
        }

        String value = input.trim().toLowerCase(Locale.ROOT);

        if (value.equals("permanent") || value.equals("perm")) {
            return -1L;
        }

        if (value.length() < 2) {
            throw new IllegalArgumentException("Invalid duration.");
        }

        try {
            long amount = Long.parseLong(
                    value.substring(0, value.length() - 1)
            );

            if (amount <= 0) {
                throw new IllegalArgumentException("Duration must be positive.");
            }

            char unit = value.charAt(value.length() - 1);

            long multiplier = switch (unit) {
                case 's' -> 1_000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> throw new IllegalArgumentException("Unknown duration unit.");
            };

            return Math.multiplyExact(amount, multiplier);

        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
    }

    public static String formatDuration(long milliseconds) {
        return formatDurationWords(milliseconds);
    }

    public static String formatDurationWords(long milliseconds) {
        if (milliseconds < 0) {
            return "Permanent";
        }

        long totalSeconds = Math.max(0L, milliseconds / 1_000L);
        if (milliseconds > 0 && totalSeconds == 0) {
            totalSeconds = 1;
        }

        long days = totalSeconds / 86_400;
        totalSeconds %= 86_400;
        long hours = totalSeconds / 3_600;
        totalSeconds %= 3_600;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        List<String> parts = new ArrayList<>();

        if (days > 0) {
            parts.add(days + (days == 1 ? " day" : " days"));
        }
        if (hours > 0) {
            parts.add(hours + (hours == 1 ? " hour" : " hours"));
        }
        if (minutes > 0 || days > 0 || hours > 0) {
            parts.add(minutes + (minutes == 1 ? " min" : " mins"));
        }
        // Always show seconds alongside minutes/hours so staff and players
        // can see the full remaining duration (for example, "9 mins, 59 secs").
        if (seconds > 0 || parts.isEmpty() || minutes > 0 || hours > 0 || days > 0) {
            parts.add(seconds + (seconds == 1 ? " sec" : " secs"));
        }

        return String.join(", ", parts);
    }

    public static String formatPlayerMuteDuration(long milliseconds) {
        if (milliseconds < 0) {
            return "permanently";
        }

        return "for " + formatDurationWords(milliseconds);
    }
}
