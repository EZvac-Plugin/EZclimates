package com.github.blade.ezclimates.food;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Its lore line is found by what it says, so another plugin's line (EZvac's frosted over) is never taken for it. */
class FreshnessLineTest {
    @Test
    void itKnowsItsOwnLinesAndNoOthers() {
        for (Freshness.Stage stage : Freshness.Stage.values())
            for (double days : new double[] {0.5, 1, 3})
                assertTrue(FoodSpoiling.isFreshnessLine(PlainTextComponentSerializer.plainText()
                        .serialize(FoodSpoiling.line(stage, days))), stage + " " + days);
        assertFalse(FoodSpoiling.isFreshnessLine("❄ Frosted over: defrost to free it"));
        assertFalse(FoodSpoiling.isFreshnessLine("A gift from Steve"));
    }
}
