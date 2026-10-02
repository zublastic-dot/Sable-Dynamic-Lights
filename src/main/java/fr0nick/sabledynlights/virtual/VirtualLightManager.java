package fr0nick.sabledynlights.virtual;

import dev.lambdaurora.lambdynlights.DynamicLightSource;
import fr0nick.sabledynlights.lamb.AbstractLambDynamicLightSource;
import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks "virtual" light-emitting positions inside the shared client {@link LevelLightEngine}.
 * <p>
 * A virtual position doesn't hold a real light-emitting block. Instead:
 * <ul>
 *     <li>{@code BlockLightEngineEmissionMixin} reports an emission value for it whenever the vanilla block light
 *     engine's BFS propagation asks for the emission at that position, and</li>
 *     <li>{@link LevelLightEngine#checkBlock(BlockPos)} is used to make the engine (re)compute real propagation
 *     from/through that position, exactly as if a real light-emitting block had been placed/removed there.</li>
 * </ul>
 * Because this goes through the real light engine, falloff and occlusion (walls, floors, etc.) are handled
 * correctly "for free" by vanilla's own BFS, and the resulting light values get baked into chunk meshes normally
 * the next time the affected section rebuilds.
 * <p>
 * Sable sub-level plots share the exact same {@link LevelLightEngine} instance as the main level (their chunks
 * just live at unusual "plot grid" coordinates), so this same mechanism transparently lights the interior of
 * sub-levels too &mdash; {@link VirtualSableLightManager} feeds it positions that have already been transformed
 * into that coordinate space via each sub-level's pose.
 */
@SuppressWarnings({"deprecation", "removal"})
public final class VirtualLightManager {
    private static final Object WORLD_SOURCES = new Object();
    private static final ScopedLightSources<ClientLevel, BlockPos> SOURCES = new ScopedLightSources<>();
    private static final SourceSectionIndex SOURCE_SECTIONS = new SourceSectionIndex();

    private VirtualLightManager() {}

    public static boolean isCurrentLevel(BlockGetter level) {
        return level instanceof ClientLevel && SOURCES.owns(level);
    }

    public static int getVirtualEmission(ClientLevel level, BlockPos pos) {
        return pos == null ? 0 : SOURCES.get(level, pos);
    }

    /** The optional engine adapter uses the same client-only source map as vanilla. */
    public static int withVirtualEmission(BlockGetter level, BlockPos pos, int blockEmission) {
        if (!(level instanceof ClientLevel clientLevel)) return blockEmission;
        return Math.max(blockEmission, getVirtualEmission(clientLevel, pos));
    }

    /** No block/chunk lookups: relighting must also discover virtual emitters in all-air sections. */
    public static List<BlockPos> getVirtualSources(BlockGetter level, ChunkPos chunk, int minY, int maxY) {
        if (!(level instanceof ClientLevel clientLevel)) return List.of();
        return SourcePositions.inChunk(SOURCES.sources(clientLevel).keySet(), chunk.x, chunk.z, minY, maxY);
    }

    /** Keep only sections reached by this world's client sources available to ScalableLux. */
    public static boolean needsLightSection(BlockGetter level, int x, int y, int z) {
        return level instanceof ClientLevel clientLevel
                && SOURCES.owns(clientLevel)
                && SOURCE_SECTIONS.covers(SOURCES.sources(clientLevel), x, y, z);
    }

    public static void recheckSectionSources(BlockGetter level, SectionPos section) {
        if (!(level instanceof ClientLevel clientLevel) || !SOURCES.owns(clientLevel)) return;
        for (var pos : SOURCE_SECTIONS.nearSection(SOURCES.sources(clientLevel), section.x(), section.y(), section.z()))
            invalidate(clientLevel, pos);
    }

    public static void recheckChunkSources(BlockGetter level, ChunkPos chunk) {
        if (!(level instanceof ClientLevel clientLevel) || !SOURCES.owns(clientLevel)) return;
        for (var pos : SOURCE_SECTIONS.nearChunk(SOURCES.sources(clientLevel), chunk.x, chunk.z))
            invalidate(clientLevel, pos);
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) onClientTick(collectActiveLightCandidates(mc));
    }

    public static void onClientTick(List<LightCandidate> candidates) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (SOURCES.bind(level, VirtualLightManager::invalidate)) {
            SOURCE_SECTIONS.clear();
            // Shell samples and transformed sources belong to the previous world too.
            VirtualSableLightManager.clearAll();
        }
        Map<BlockPos, Integer> current = new HashMap<>();
        for (LightCandidate candidate : candidates) {
            if (candidate.entity() != null && candidate.entity().level() != level) continue;
            current.merge(candidate.pos().immutable(), candidate.luminance(), Math::max);
        }
        SOURCES.put(WORLD_SOURCES, current, VirtualLightManager::invalidate);
    }

    public static void syncExternalPositions(Object owner, Map<BlockPos, Integer> positions) {
        if (!isCurrentLevel(Minecraft.getInstance().level)) return;
        Map<BlockPos, Integer> stable = new HashMap<>();
        positions.forEach((pos, light) -> stable.put(pos.immutable(), light));
        SOURCES.put(owner, stable, VirtualLightManager::invalidate);
    }

    public static void clearExternalPositions(Object owner) {
        SOURCES.remove(owner, VirtualLightManager::invalidate);
    }

    public static void clearAll() {
        SOURCES.bind(null, VirtualLightManager::invalidate);
        SOURCE_SECTIONS.clear();
    }

    private static void invalidate(ClientLevel level, BlockPos pos) {
        // Invalidate the owning level, including after Minecraft switches its main level.
        if (level != null && level.hasChunkAt(pos)) level.getLightEngine().checkBlock(pos);
    }

    /**
     * A single active dynamic light candidate: its current world-space position, luminance, and (if known) the
     * entity it's tied to. The entity is used by {@link VirtualSableLightManager} to check whether the position is
     * already local to a Sable sub-level's plot (as opposed to a normal world position) - see that class' docs.
     */
    public record LightCandidate(BlockPos pos, int luminance, Entity entity) {}

    /**
     * Collects every currently active dynamic light: LambDynamicLights sources (including this mod's own
     * outward-projected Create/Sable lights - a moving contraption or another sub-level's light is a perfectly
     * legitimate light source for a nearby sub-level too) plus the local player's held-item light. Shared between
     * the main-world tracker below and {@link VirtualSableLightManager}.
     */
    public static List<LightCandidate> collectActiveLightCandidates(Minecraft mc) {
        List<LightCandidate> current = new ArrayList<>();

        if (LambDynLightsDelegate.isAvailable()) {
            Collection<?> sources = LambDynLightsDelegate.getActiveSources();
            if (sources != null && !sources.isEmpty()) {
                for (Object obj : sources) {
                    if (obj instanceof DynamicLightSource source && source.isDynamicLightEnabled() && source.getLuminance() > 0) {
                        BlockPos pos = BlockPos.containing(source.getDynamicLightX(), source.getDynamicLightY(), source.getDynamicLightZ());
                        Entity entity = resolveEntity(obj);
                        if (entity == null || entity.level() == mc.level)
                            current.add(new LightCandidate(pos, source.getLuminance(), entity));
                    }
                }
            }
        }

        if (mc.player != null) {
            int pLight = Math.max(getLightEmission(mc.player.getMainHandItem()), getLightEmission(mc.player.getOffhandItem()));
            if (pLight > 0) {
                current.add(new LightCandidate(mc.player.blockPosition().above(), pLight, mc.player));
            }
        }

        return current;
    }

    /**
     * @return the entity backing this light source, if any: LambDynamicLights represents players/mobs/item
     * entities by mixing its behavior directly into the {@link Entity} itself (so the source object IS the
     * entity), while this mod's own {@link AbstractLambDynamicLightSource}s are separate objects that expose their
     * backing entity (if any) via {@link AbstractLambDynamicLightSource#getTrackedEntity()}.
     */
    private static Entity resolveEntity(Object source) {
        if (source instanceof Entity entity) return entity;
        if (source instanceof AbstractLambDynamicLightSource abstractSource) return abstractSource.getTrackedEntity();
        return null;
    }

    private static int getLightEmission(ItemStack stack) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        return 0;
    }
}
