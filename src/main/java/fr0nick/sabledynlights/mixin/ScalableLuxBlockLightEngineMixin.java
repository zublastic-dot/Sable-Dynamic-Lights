package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.virtual.SourcePositions;
import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.HashSet;

/** ScalableLux replaces BlockLightEngine, including emission reads and full-chunk source discovery. */
@Pseudo
@Mixin(targets = "ca.spottedleaf.starlight.common.light.BlockStarLightEngine", remap = false, priority = 100)
public abstract class ScalableLuxBlockLightEngineMixin {
    @Unique private Level sabledynlights$world;

    @Shadow protected abstract void initNibble(int chunkX, int sectionY, int chunkZ, boolean extrude, boolean initialiseRemoved);

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void sabledynlights$captureWorld(Level world, CallbackInfo ci) {
        sabledynlights$world = world;
    }

    @Unique private void sabledynlights$prepareSections(SectionPos source) {
        // initNibble checks its existing chunk cache and height bounds; this never loads a chunk.
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            initNibble(source.x() + dx, source.y() + dy, source.z() + dz, true, true);
        }
    }

    @Inject(method = "checkBlock", at = @At("HEAD"), remap = false)
    private void sabledynlights$prepareVirtualSource(LightChunkGetter access, int x, int y, int z, CallbackInfo ci) {
        if (!VirtualLightManager.isCurrentLevel(access.getLevel())) return;
        var pos = new BlockPos(x, y, z);
        if (VirtualLightManager.withVirtualEmission(access.getLevel(), pos, 0) > 0)
            sabledynlights$prepareSections(SectionPos.of(pos));
    }

    @Inject(method = "setNibbleNull", at = @At("HEAD"), cancellable = true, remap = false)
    private void sabledynlights$retainVirtualSections(int x, int y, int z, CallbackInfo ci) {
        if (VirtualLightManager.needsLightSection(sabledynlights$world, x, y, z)) {
            initNibble(x, y, z, true, true);
            ci.cancel();
        }
    }

    @Redirect(method = {"checkBlock", "calculateLightValue", "lightChunk"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I"),
            remap = false, require = 3, allow = 3)
    private int sabledynlights$virtualEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return VirtualLightManager.withVirtualEmission(level, pos, state.getLightEmission(level, pos));
    }

    @Inject(method = "getSources", at = @At("RETURN"), cancellable = true, remap = false)
    private void sabledynlights$virtualSources(LightChunkGetter access, ChunkAccess chunk,
                                              CallbackInfoReturnable<List<BlockPos>> cir) {
        var sources = VirtualLightManager.getVirtualSources(access.getLevel(), chunk.getPos(),
                chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        if (!sources.isEmpty()) {
            var sections = new HashSet<SectionPos>();
            for (var source : sources) sections.add(SectionPos.of(source));
            sections.forEach(this::sabledynlights$prepareSections);
            cir.setReturnValue(SourcePositions.merge(cir.getReturnValue(), sources));
        }
    }
}
