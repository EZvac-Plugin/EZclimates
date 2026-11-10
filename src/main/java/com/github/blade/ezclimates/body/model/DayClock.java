package com.github.blade.ezclimates.body.model;

/**
 * When a world's day turns, for sickness. Normally that's when its calendar
 * day changes: EZvac's, or the world's own day count. A clock that doesn't
 * move (doDaylightCycle off, or EZvac's calendar paused) would never turn a
 * day, and an illness would never end, so a full day's worth of game time
 * also counts as a day. Game time keeps running whatever the clock does. Pure.
 */
public final class DayClock {
    public static final long TICKS_PER_DAY = 24_000L;

    private long key;
    private long turnedAt;
    private boolean started;

    /**
     * Looks at the world once.
     *
     * @param dayKey   the calendar day the world is on
     * @param gameTime the world's game time, which never stops or goes back
     * @return true when a day has turned since the last look
     */
    public boolean turned(long dayKey, long gameTime) {
        if (!started) {
            started = true;
            key = dayKey;
            turnedAt = gameTime;
            return false;
        }
        if (dayKey != key || gameTime - turnedAt >= TICKS_PER_DAY || gameTime < turnedAt) {
            key = dayKey;
            turnedAt = gameTime;
            return true;
        }
        return false;
    }
}
