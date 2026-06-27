package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.light.shadow.BlockShadowCache;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldBlockChangeMixin {
    @Inject(
        method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void irlite$invalidateBlockShadows(
        BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir) {
        if (FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }
        World self = (World) (Object) this;
        if (self.isClient()) {
            BlockShadowCache.invalidateAt(pos);
        }
    }
}
