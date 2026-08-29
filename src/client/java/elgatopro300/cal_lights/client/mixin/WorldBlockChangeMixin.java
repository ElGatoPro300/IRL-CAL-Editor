package elgatopro300.cal_lights.client.mixin;

import org.qualet.irl.light.shadow.BlockShadowCache;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class WorldBlockChangeMixin {
    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void irlite$invalidateBlockShadows(
        BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
        CallbackInfoReturnable<Boolean> cir) {
        if (FabricLoader.getInstance().isModLoaded("irlite")) {
            return;
        }
        Level self = (Level) (Object) this;
        if (!self.isClientSide() || self.isOutsideBuildHeight(pos)) {
            return;
        }
        BlockState old = self.getBlockState(pos);
        if (old == state) {
            return;
        }
        BlockShadowCache.invalidateChange(self, pos, old, state);
    }
}
