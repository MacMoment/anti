package com.anticheat.util;

public class TimeUtil {

    public static long now() {
        return System.currentTimeMillis();
    }

    public static long nowNanos() {
        return System.nanoTime();
    }

    /**
     * Format a duration in milliseconds as "Xh Ym Zs".
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds %= 60;
        minutes %= 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Returns true if at least {@code durationMs} milliseconds have passed since {@code timestamp}.
     */
    public static boolean hasElapsed(long timestamp, long durationMs) {
        return System.currentTimeMillis() - timestamp >= durationMs;
    }
}
