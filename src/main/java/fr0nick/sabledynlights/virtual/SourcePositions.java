package fr0nick.sabledynlights.virtual;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** Chunk-source discovery shared by the optional light engine adapter and its regression tests. */
public final class SourcePositions {
    private SourcePositions() { }

    public static List<BlockPos> inChunk(Collection<BlockPos> sources, int chunkX, int chunkZ, int minY, int maxY) {
        var result = new ArrayList<BlockPos>();
        for (BlockPos pos : sources) {
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ
                    && pos.getY() >= minY && pos.getY() < maxY) result.add(pos.immutable());
        }
        return result;
    }

    /** Leave the engine's list untouched, retain its order, and never seed a cell twice. */
    public static List<BlockPos> merge(List<BlockPos> blocks, List<BlockPos> virtual) {
        if (virtual.isEmpty()) return blocks;
        var result = new ArrayList<>(blocks);
        var seen = new HashSet<>(blocks);
        for (BlockPos pos : virtual) if (seen.add(pos)) result.add(pos.immutable());
        return result;
    }
}
