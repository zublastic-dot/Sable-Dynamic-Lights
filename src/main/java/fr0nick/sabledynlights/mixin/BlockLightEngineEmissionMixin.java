package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockLightEngine.class, priority = 100)
public abstract class BlockLightEngineEmissionMixin<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>>
        extends LightEngine<M, S> {
    protected BlockLightEngineEmissionMixin(LightChunkGetter chunkSource, S storage) {
        super(chunkSource, storage);
    }

    @Inject(method = "getEmission", at = @At("HEAD"), cancellable = true)
    private void sabledynlights$getVirtualEmission(long blockPosLong, BlockState state, CallbackInfoReturnable<Integer> cir) {
        // Client mixins also transform the integrated server's shared engine class.
        if (!(chunkSource.getLevel() instanceof ClientLevel level)) return;
        BlockPos pos = BlockPos.of(blockPosLong);
        int virtualLight = VirtualLightManager.getVirtualEmission(level, pos);
        if (virtualLight > 0) {
            int vanillaEmission = state.getLightEmission(level, pos);
            if (virtualLight > vanillaEmission) {
                cir.setReturnValue(virtualLight);
            }
        }
    }
}
