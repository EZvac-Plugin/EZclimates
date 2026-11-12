package com.github.blade.ezclimates.food;

/**
 * How fresh food is (EZclimates 0.3.0), worked out lazily: a stack keeps
 * when it would be spoiled ({@code spoilAt}, in world ticks) at the rate it
 * was last kept at ({@code rateStep}), and is re-anchored whenever it is
 * looked at again somewhere else. Pure.
 *
 * <p>Life is counted in room ticks: ticks at 70 F. A rate is how many room
 * ticks pass per tick where the food is kept: 1 at 70 F, more when warmer,
 * less in a fridge, much less frozen.
 */
public final class Freshness {
    private Freshness() {}

    /** One Minecraft day, in ticks. */
    public static final long DAY = 24_000L;
    /** Rates are kept in quarter-octave steps, so food kept in the same place has the same step. */
    static final double STEPS_PER_DOUBLING = 4.0;
    /** {@code spoilAt} is rounded down to a twentieth of the food's life where it is kept, so stacks match. */
    static final int ROUNDING = 20;

    public enum Stage { FRESH, STALE, SPOILED, GONE }

    /**
     * How fast food spoils at a temperature: 1 at 70 F, doubling every 18 F
     * warmer; from 70 F down to a fridge's 38 F it falls to 1/{@code fridge}
     * (easing evenly on a log scale); from 38 F to 28 F on to 1/{@code freezer};
     * and at 28 F and colder it is frozen, at 1/{@code freezer}.
     */
    public static double rate(double temperatureF, double fridge, double freezer) {
        if (!Double.isFinite(temperatureF)) return 1.0;
        double fridgeRate = 1.0 / Math.max(1.0, fridge), frozenRate = 1.0 / Math.max(1.0, freezer);
        if (temperatureF >= 70.0) return Math.pow(2.0, Math.min(120.0, temperatureF - 70.0) / 18.0);
        if (temperatureF >= 38.0) return Math.exp(Math.log(fridgeRate) * (70.0 - temperatureF) / 32.0);
        if (temperatureF > 28.0)
            return Math.exp(Math.log(fridgeRate) + (Math.log(frozenRate) - Math.log(fridgeRate)) * (38.0 - temperatureF) / 10.0);
        return frozenRate;
    }

    /** A rate as its step, and back. */
    public static int step(double rate) {
        return (int) Math.round(Math.log(Math.max(1e-6, rate)) / Math.log(2.0) * STEPS_PER_DOUBLING);
    }

    public static double rateOf(int step) {
        return Math.pow(2.0, step / STEPS_PER_DOUBLING);
    }

    /** What a stack keeps: when it would be spoiled at its stored rate, and that rate's step. */
    public record Kept(long spoilAt, int rateStep) {}

    /** Room ticks of life left at {@code now} (below 0 once spoiled). */
    public static double leftRoomTicks(Kept kept, long now) {
        return (kept.spoilAt() - now) * rateOf(kept.rateStep());
    }

    /** Fresh food, just made or first seen, kept at this rate. */
    public static Kept fresh(double lifeRoomTicks, long now, double rate) {
        return anchor(lifeRoomTicks, lifeRoomTicks, now, rate);
    }

    /** The same stack, from now on kept at another rate: its life left carried over. */
    public static Kept reanchor(Kept kept, double lifeRoomTicks, long now, double rate) {
        return anchor(leftRoomTicks(kept, now), lifeRoomTicks, now, rate);
    }

    /**
     * Anchors this much life left at this rate: {@code spoilAt} is rounded
     * down to a twentieth of the food's life at that rate (never generous),
     * so two stacks of about the same age kept in the same place end up the
     * same and still stack.
     */
    static Kept anchor(double leftRoomTicks, double lifeRoomTicks, long now, double rate) {
        int step = step(rate);
        double stepRate = rateOf(step);
        double bucket = Math.max(1.0, lifeRoomTicks / ROUNDING / stepRate);
        double spoilAt = now + leftRoomTicks / stepRate;
        return new Kept((long) (Math.floor(spoilAt / bucket) * bucket), step);
    }

    /** Fresh while over half its life is left, stale to the end, spoiled for half a life more, then gone. */
    public static Stage stage(double leftRoomTicks, double lifeRoomTicks) {
        double share = leftRoomTicks / lifeRoomTicks;
        if (share > 0.5) return Stage.FRESH;
        if (share > 0.0) return Stage.STALE;
        if (share > -0.5) return Stage.SPOILED;
        return Stage.GONE;
    }

    /** Days until it is spoiled, kept where it is now; 0 once spoiled. */
    public static double daysLeft(Kept kept, long now) {
        return Math.max(0.0, (kept.spoilAt() - now) / (double) DAY);
    }
}
