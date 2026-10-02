package fr0nick.sabledynlights.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fr0nick.sabledynlights.virtual.VirtualLightManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve remaining virtual emitters during both cached and contextual decrease/recheck paths. */
@Pseudo
@Mixin(targets = "ca.spottedleaf.starlight.common.light.StarLightEngine", remap = false, priority = 100)
public abstract class ScalableLuxLightDecreaseMixin {
    @Shadow @Final protected Level world;
    @Shadow @Final protected boolean skylightPropagator;
    @Unique private final BlockPos.MutableBlockPos sabledynlights$emitterPos = new BlockPos.MutableBlockPos();

    @ModifyExpressionValue(method = "performLightDecrease", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission()I"),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I")
    }, remap = false, require = 4, allow = 4)
    private int sabledynlights$remainingEmitter(int original, @Local(name = "offX") int x,
                                                @Local(name = "offY") int y, @Local(name = "offZ") int z) {
        if (skylightPropagator || !VirtualLightManager.isCurrentLevel(world)) return original;
        // Each engine owns its scratch position, just like ScalableLux's existing propagation scratch state.
        return VirtualLightManager.withVirtualEmission(world, sabledynlights$emitterPos.set(x, y, z), original);
    }
}
