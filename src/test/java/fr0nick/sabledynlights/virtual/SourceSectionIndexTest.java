package fr0nick.sabledynlights.virtual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SourceSectionIndexTest {
    @Test void lightPacketReplayFindsNeighboringSourcesOnceWithoutGlobalReplay() {
        var index = new SourceSectionIndex();
        var here = new BlockPos(1, 65, 1);
        var neighbor = new BlockPos(17, 65, -1);
        var far = new BlockPos(160, 65, 160);
        var high = new BlockPos(1, 160, 1);
        var sources = Map.of(here, 15, neighbor, 14, far, 12, high, 10);
        assertEquals(java.util.Set.of(here, neighbor), java.util.Set.copyOf(index.nearSection(sources, 0, 4, 0)));
        assertEquals(java.util.Set.of(here, neighbor, high), java.util.Set.copyOf(index.nearChunk(sources, 0, 0)));
        assertTrue(index.nearSection(Map.of(), 0, 4, 0).isEmpty());
        assertTrue(index.nearChunk(Map.of(), 0, 0).isEmpty());
    }

    @Test void lightRetentionIsBoundedToAtMostOneNeighborSection() {
        var index = new SourceSectionIndex();
        var sources = Map.of(new BlockPos(15, 64, -1), 15);
        for (int x = -1; x <= 1; x++) for (int y = 3; y <= 5; y++) for (int z = -2; z <= 0; z++)
            assertTrue(index.covers(sources, x, y, z));
        assertFalse(index.covers(sources, 2, 4, -1));
        assertFalse(index.covers(sources, 0, 6, -1));
        assertFalse(index.covers(sources, 0, 4, 1));
    }

    @Test void movingAndRemovingLastSourceReleasesFormerAirSections() {
        var index = new SourceSectionIndex();
        var old = Map.of(new BlockPos(0, 64, 0), 15);
        var moved = Map.of(new BlockPos(160, 64, 0), 15);
        assertTrue(index.covers(old, 0, 4, 0));
        assertFalse(index.covers(moved, 0, 4, 0));
        assertTrue(index.covers(moved, 10, 4, 0));
        assertFalse(index.covers(Map.of(), 10, 4, 0));
    }

    @Test void removalOfOneOverlappingSourceRetainsOtherSourceCoverage() {
        var index = new SourceSectionIndex();
        var a = new BlockPos(0, 64, 0);
        var b = new BlockPos(1, 65, 1);
        assertTrue(index.covers(Map.of(a, 15, b, 12), 0, 4, 0));
        assertTrue(index.covers(Map.of(b, 12), 0, 4, 0));
        index.clear();
        assertFalse(index.covers(Map.of(), 0, 4, 0));
    }

    @Test void wrongWorldSnapshotCannotRetainAnotherWorldsSections() {
        var lights = new ScopedLightSources<Object, BlockPos>();
        var index = new SourceSectionIndex();
        Object client = new Object(), server = new Object(), other = new Object();
        lights.bind(client, (w, p) -> fail());
        lights.put("held", Map.of(new BlockPos(0, 64, 0), 15), (w, p) -> {});
        assertTrue(index.covers(lights.sources(client), 0, 4, 0));
        assertFalse(index.covers(lights.sources(server), 0, 4, 0));
        assertFalse(index.covers(lights.sources(other), 0, 4, 0));
        assertTrue(index.covers(lights.sources(client), 0, 4, 0));
    }
}
