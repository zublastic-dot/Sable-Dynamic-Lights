package fr0nick.sabledynlights.virtual;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Client-thread writes, immutable snapshots for lighting readers. World identity matters, not dimension names. */
public final class ScopedLightSources<W, P> {
    private record Snapshot<W, P>(W world, Map<P, Integer> levels) { }
    private volatile Snapshot<W, P> snapshot = new Snapshot<>(null, Map.of());
    private final Map<Object, Map<P, Integer>> groups = new HashMap<>();

    public boolean owns(Object world) { return world != null && snapshot.world == world; }

    public int get(W world, P position) {
        var current = snapshot;
        return world != null && current.world == world ? current.levels.getOrDefault(position, 0) : 0;
    }

    /** One immutable, world-scoped publication, also used when a light engine relights a chunk. */
    public Map<P, Integer> sources(W world) {
        var current = snapshot;
        return world != null && current.world == world ? current.levels : Map.of();
    }

    public boolean bind(W world, BiConsumer<W, P> invalidate) {
        var old = snapshot;
        if (old.world == world) return false;
        groups.clear();
        snapshot = new Snapshot<>(world, Map.of());
        // Publish removal before asking the old world's engine to recompute it.
        old.levels.keySet().forEach(position -> invalidate.accept(old.world, position));
        return true;
    }

    public void put(Object contributor, Map<P, Integer> positions, BiConsumer<W, P> invalidate) {
        if (snapshot.world == null) return;
        Map<P, Integer> copy = new HashMap<>();
        positions.forEach((position, level) -> {
            if (level != null && level > 0) copy.put(Objects.requireNonNull(position), Math.min(15, level));
        });
        if (copy.equals(groups.getOrDefault(contributor, Map.of()))) return;
        if (copy.isEmpty()) groups.remove(contributor);
        else groups.put(contributor, Map.copyOf(copy));
        Map<P, Integer> merged = new HashMap<>();
        groups.values().forEach(group -> group.forEach((position, level) -> merged.merge(position, level, Math::max)));
        var old = snapshot;
        snapshot = new Snapshot<>(old.world, Map.copyOf(merged));
        var changed = new HashSet<>(old.levels.keySet());
        changed.addAll(merged.keySet());
        changed.removeIf(position -> Objects.equals(old.levels.get(position), merged.get(position)));
        changed.forEach(position -> invalidate.accept(old.world, position));
    }

    public void remove(Object contributor, BiConsumer<W, P> invalidate) { put(contributor, Map.of(), invalidate); }
}
