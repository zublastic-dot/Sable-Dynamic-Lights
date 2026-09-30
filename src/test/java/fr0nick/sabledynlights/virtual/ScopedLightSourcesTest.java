package fr0nick.sabledynlights.virtual;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScopedLightSourcesTest {
    @Test void identicalCoordinatesCannotLeakToServerOrAnotherDimension() {
        var lights = new ScopedLightSources<Object, String>();
        Object client = new Object(), server = new Object(), nether = new Object();
        lights.bind(client, (world, pos) -> fail());
        lights.put("held", Map.of("18,49,30", 15), (world, pos) -> {});
        assertEquals(15, lights.get(client, "18,49,30"));
        assertEquals(0, lights.get(server, "18,49,30"));
        assertEquals(0, lights.get(nether, "18,49,30"));
        assertEquals(0, lights.get(null, "18,49,30"));
    }

    @Test void sameDimensionNameAfterReloadIsADifferentWorld() {
        var lights = new ScopedLightSources<String, String>();
        String old = new String("minecraft:the_nether"), replacement = new String("minecraft:the_nether");
        lights.bind(old, (world, pos) -> fail());
        lights.put("held", Map.of("p", 15), (world, pos) -> {});
        assertEquals(0, lights.get(replacement, "p"));
        var invalidated = new ArrayList<String>();
        assertTrue(lights.bind(replacement, (world, pos) -> {
            assertSame(old, world);
            assertEquals(0, lights.get(old, pos));
            invalidated.add(pos);
        }));
        assertEquals(java.util.List.of("p"), invalidated);
        assertEquals(0, lights.get(replacement, "p"));
    }

    @Test void sourceRemovalIsVisibleBeforeEngineInvalidation() {
        var lights = new ScopedLightSources<Object, String>(); Object world = new Object();
        lights.bind(world, (w, p) -> fail());
        lights.put("held", Map.of("p", 15), (w, p) -> assertEquals(15, lights.get(w, p)));
        var checks = new ArrayList<String>();
        lights.remove("held", (w, p) -> {
            assertSame(world, w); assertEquals(0, lights.get(w, p)); checks.add(p);
        });
        assertEquals(java.util.List.of("p"), checks);
    }

    @Test void movingHeldLightInvalidatesBothOldAndNewPositions() {
        var lights = new ScopedLightSources<Object, String>(); Object world = new Object();
        lights.bind(world, (w, p) -> fail());
        lights.put("held", Map.of("old", 15), (w, p) -> {});
        var checks = new ArrayList<String>();
        lights.put("held", Map.of("new", 15), (w, p) -> checks.add(p));
        assertEquals(java.util.Set.of("old", "new"), java.util.Set.copyOf(checks));
        assertEquals(0, lights.get(world, "old")); assertEquals(15, lights.get(world, "new"));
    }

    @Test void removingOneContributorKeepsTheOtherLight() {
        var lights = new ScopedLightSources<Object, String>(); Object world = new Object();
        lights.bind(world, (w, p) -> fail());
        lights.put("ship", Map.of("p", 12), (w, p) -> {});
        lights.put("held", Map.of("p", 15), (w, p) -> {});
        lights.remove("held", (w, p) -> assertEquals(12, lights.get(w, p)));
        assertEquals(12, lights.get(world, "p"));
        lights.remove("ship", (w, p) -> assertEquals(0, lights.get(w, p)));
    }

    @Test void unchangedAndHiddenWeakerSourcesDoNotTriggerRebuilds() {
        var lights = new ScopedLightSources<Object, String>(); Object world = new Object();
        lights.bind(world, (w, p) -> fail());
        lights.put("bright", Map.of("p", 15), (w, p) -> {});
        lights.put("bright", Map.of("p", 15), (w, p) -> fail());
        lights.put("weak", Map.of("p", 3), (w, p) -> fail());
        lights.remove("weak", (w, p) -> fail());
        assertFalse(lights.bind(world, (w, p) -> fail()));
    }

    @Test void disconnectClearsAllContributorsInTheirOwningWorld() {
        var lights = new ScopedLightSources<Object, String>(); Object old = new Object();
        lights.bind(old, (w, p) -> fail());
        lights.put("held", Map.of("a", 15), (w, p) -> {});
        lights.put("ship", Map.of("b", 12), (w, p) -> {});
        var checks = new ArrayList<String>();
        lights.bind(null, (w, p) -> { assertSame(old, w); checks.add(p); assertEquals(0, lights.get(w, p)); });
        assertEquals(2, checks.size()); assertFalse(lights.owns(old));
        lights.bind(new Object(), (w, p) -> fail());
        assertEquals(0, lights.get(old, "a"));
    }

    @Test void publishedValuesCannotBeMutatedByTheContributor() {
        var lights = new ScopedLightSources<Object, String>(); Object world = new Object();
        lights.bind(world, (w, p) -> fail());
        var input = new HashMap<>(Map.of("p", 15));
        lights.put("held", input, (w, p) -> {}); input.clear();
        assertEquals(15, lights.get(world, "p"));
        lights.put("held", input, (w, p) -> {});
        assertEquals(0, lights.get(world, "p"));
    }

    @Test void rejectsInactiveWorldAndBoundsEmission() {
        var lights = new ScopedLightSources<Object, String>(); Object world = new Object();
        lights.put("held", Map.of("p", 15), (w, p) -> fail());
        lights.bind(world, (w, p) -> fail());
        assertEquals(0, lights.get(world, "p"));
        lights.put("held", Map.of("p", 99, "zero", 0, "negative", -2), (w, p) -> {});
        assertEquals(15, lights.get(world, "p"));
        assertEquals(0, lights.get(world, "zero")); assertEquals(0, lights.get(world, "negative"));
    }
}
