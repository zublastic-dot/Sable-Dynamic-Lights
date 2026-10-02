package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapply stationary virtual sources after authoritative server nibbles replace client storage. */
@Pseudo
@Mixin(targets = "ca.spottedleaf.starlight.common.light.vanillainterface.BaseLevelLightEngineVanillaInterface",
        remap = false, priority = 100)
public class ScalableLuxClientLightUpdatesMixin {
    @Unique private BlockGetter sabledynlights$world;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void sabledynlights$captureWorld(LightChunkGetter chunks, boolean block, boolean sky, CallbackInfo ci) {
        if (block) sabledynlights$world = chunks.getLevel();
    }

    @Inject(method = "scalablelux$clientUpdateLight", at = @At("RETURN"), remap = false)
    private void sabledynlights$afterLightPacket(LightLayer layer, SectionPos section, DataLayer data,
                                                boolean trustEdges, CallbackInfo ci) {
        if (layer == LightLayer.BLOCK) VirtualLightManager.recheckSectionSources(sabledynlights$world, section);
    }

    @Inject(method = "scalablelux$clientChunkLoad", at = @At("RETURN"), remap = false)
    private void sabledynlights$afterChunkLoad(ChunkPos pos, LevelChunk chunk, CallbackInfo ci) {
        VirtualLightManager.recheckChunkSources(sabledynlights$world, pos);
    }
}
