package fr0nick.sabledynlights.virtual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cached coverage of an immutable source publication. Radius 15 reaches at most one adjacent section. */
public final class SourceSectionIndex {
    private record Snapshot(Map<BlockPos, Integer> sources, Set<SectionPos> sections,
                            Map<SectionPos, List<BlockPos>> emitters) { }
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Set.of(), Map.of());

    private Snapshot snapshot(Map<BlockPos, Integer> sources) {
        var current = snapshot;
        return current.sources == sources ? current : rebuild(sources);
    }

    public boolean covers(Map<BlockPos, Integer> sources, int x, int y, int z) {
        return snapshot(sources).sections.contains(SectionPos.of(x, y, z));
    }

    /** A server light packet can overwrite client light without changing the held source itself. */
    public List<BlockPos> nearSection(Map<BlockPos, Integer> sources, int x, int y, int z) {
        var current = snapshot(sources);
        var result = new ArrayList<BlockPos>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            result.addAll(current.emitters.getOrDefault(SectionPos.of(x + dx, y + dy, z + dz), List.of()));
        }
        return result;
    }

    public List<BlockPos> nearChunk(Map<BlockPos, Integer> sources, int x, int z) {
        var result = new ArrayList<BlockPos>();
        snapshot(sources).emitters.forEach((section, positions) -> {
            if (Math.abs((long) section.x() - x) <= 1 && Math.abs((long) section.z() - z) <= 1)
                result.addAll(positions);
        });
        return result;
    }

    private synchronized Snapshot rebuild(Map<BlockPos, Integer> sources) {
        if (snapshot.sources == sources) return snapshot;
        var emitters = new HashMap<SectionPos, List<BlockPos>>();
        sources.keySet().forEach(pos -> emitters.computeIfAbsent(SectionPos.of(pos), ignored -> new ArrayList<>()).add(pos));
        var covered = new HashSet<SectionPos>();
        for (var emitter : emitters.keySet()) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                covered.add(SectionPos.of(emitter.x() + dx, emitter.y() + dy, emitter.z() + dz));
            }
        }
        emitters.replaceAll((section, positions) -> List.copyOf(positions));
        return snapshot = new Snapshot(sources, Set.copyOf(covered), Map.copyOf(emitters));
    }

    public void clear() { snapshot = new Snapshot(Map.of(), Set.of(), Map.of()); }
}
