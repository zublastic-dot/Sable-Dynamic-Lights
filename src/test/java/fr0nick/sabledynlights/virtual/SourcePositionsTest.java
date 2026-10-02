package fr0nick.sabledynlights.virtual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourcePositionsTest {
    @Test void relightIncludesVirtualAirSourcesWithoutScanningBlocks() {
        var held = new BlockPos(7, 64, 9);
        assertEquals(List.of(held), SourcePositions.merge(List.of(),
                SourcePositions.inChunk(List.of(held), 0, 0, -64, 320)));
    }

    @Test void negativeChunkAndExclusiveHeightBoundsMatchMinecraft() {
        var min = new BlockPos(-16, -64, -1);
        var max = new BlockPos(-1, 319, -16);
        var sources = List.of(min, max, new BlockPos(0, 64, -1), new BlockPos(-17, 64, -1),
                new BlockPos(-1, 64, 0), new BlockPos(-1, 64, -17),
                new BlockPos(-1, -65, -1), new BlockPos(-1, 320, -1));
        assertEquals(List.of(min, max), SourcePositions.inChunk(sources, -1, -1, -64, 320));
    }

    @Test void plotCoordinatesAreRetainedWithoutWorldConversionOrChunkLoading() {
        var plot = new BlockPos(20_481_032, 120, 20_483_072);
        assertEquals(List.of(plot), SourcePositions.inChunk(List.of(plot), plot.getX() >> 4,
                plot.getZ() >> 4, -64, 320));
        assertTrue(SourcePositions.inChunk(List.of(plot), 0, 0, -64, 320).isEmpty());
    }

    @Test void duplicateRealAndVirtualSourcesAreSeededOnceAndInputsRemainUntouched() {
        var real = new BlockPos(1, 2, 3);
        var held = new BlockPos(4, 5, 6);
        var input = List.of(real);
        assertEquals(List.of(real, held), SourcePositions.merge(input, List.of(real, held, held)));
        assertEquals(List.of(real), input);
        assertSame(input, SourcePositions.merge(input, List.of()));
    }

    @Test void queuedMutablePositionsCannotMoveAfterSourceDiscovery() {
        var mutable = new BlockPos.MutableBlockPos(7, 64, 9);
        var sources = SourcePositions.inChunk(List.of(mutable), 0, 0, -64, 320);
        var merged = SourcePositions.merge(List.of(), List.of(mutable));
        mutable.set(10, 20, 30);
        assertEquals(List.of(new BlockPos(7, 64, 9)), sources);
        assertEquals(sources, merged);
    }
}
