package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LightEngine.class, priority = 100)
public class LightEngineMixin {
    @Shadow @Final protected LightChunkGetter chunkSource;

    @Inject(method = "getLightValue", at = @At("RETURN"), cancellable = true)
    private void sabledynlights$getLightValue(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (pos == null || !VirtualLightManager.isCurrentLevel(chunkSource.getLevel())) return;

        Object self = this;
        if (self instanceof BlockLightEngine) {
            int vanilla = cir.getReturnValueI();
            if (vanilla < 15) {
                int dynamic = LambDynLightsDelegate.getDynamicLightLevel(pos);
                if (dynamic > vanilla) {
                    cir.setReturnValue(dynamic);
                }
            }
        }
    }
}
