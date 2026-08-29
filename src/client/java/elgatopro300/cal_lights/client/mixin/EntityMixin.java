package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.ui.CALEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "isSpectator", at = @At("HEAD"), cancellable = true)
    private void cal_isSpectator(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer) {
            if (Minecraft.getInstance().gui.screen() instanceof CALEditorScreen) {
                cir.setReturnValue(true);
            }
        }
    }
}
