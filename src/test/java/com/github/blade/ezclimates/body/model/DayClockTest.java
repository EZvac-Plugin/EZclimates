package com.github.blade.ezclimates.body.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Days turn for sickness, even where the clock stands still. */
class DayClockTest {
    private static final long DAY = DayClock.TICKS_PER_DAY;

    @Test
    void aRunningClockTurnsOncePerCalendarDay() {
        DayClock clock = new DayClock();
        assertFalse(clock.turned(5, 1000), "the first look only starts it");
        assertFalse(clock.turned(5, 1000 + DAY - 100));
        assertTrue(clock.turned(6, 1000 + DAY - 50), "the calendar turned");
        assertFalse(clock.turned(6, 1000 + DAY), "and the game time doesn't count it twice");
        assertFalse(clock.turned(6, 1000 + 2 * DAY - 100));
    }

    @Test
    void sleepingTurnsTheDayEarly() {
        DayClock clock = new DayClock();
        clock.turned(5, 0);
        assertTrue(clock.turned(6, DAY / 2), "skipping the night");
        assertFalse(clock.turned(6, DAY - 1));
    }

    @Test
    void aFrozenClockStillTurnsADayEveryDayOfGameTime() {
        // doDaylightCycle off, or EZvac's calendar paused: the calendar day never changes.
        DayClock clock = new DayClock();
        clock.turned(22, 0);
        assertFalse(clock.turned(22, DAY - 1));
        assertTrue(clock.turned(22, DAY), "a fever still ends");
        assertFalse(clock.turned(22, DAY + 100));
        assertTrue(clock.turned(22, 2 * DAY));
    }
}
