package fr0nick.sabledynlights.mixin;

import fr0nick.sabledynlights.lamb.LambDynLightsDelegate;
import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Matches LightEngineMixin's public-reader fallback without modifying ScalableLux's stored nibbles. */
@Pseudo
@Mixin(targets = "ca.spottedleaf.starlight.common.light.StarLightInterface", remap = false, priority = 100)
public class ScalableLuxLightReaderMixin {
    @Shadow @Final protected Level world;
    @Shadow @Final private boolean hasBlockLight;

    @Inject(method = "getBlockLightValue", at = @At("RETURN"), cancellable = true, remap = false)
    private void sabledynlights$dynamicRead(BlockPos pos, ChunkAccess chunk, CallbackInfoReturnable<Integer> cir) {
        if (!hasBlockLight || chunk == null || pos == null || !VirtualLightManager.isCurrentLevel(world)
                || world.isOutsideBuildHeight(pos)) return;
        int stored = cir.getReturnValueI();
        if (stored < 15) {
            int dynamic = LambDynLightsDelegate.getDynamicLightLevel(pos);
            if (dynamic > stored) cir.setReturnValue(dynamic);
        }
    }
}
