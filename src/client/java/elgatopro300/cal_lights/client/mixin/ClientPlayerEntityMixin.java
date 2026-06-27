package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.ui.CALEditorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void cal_cancelSendMovementPackets(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof CALEditorScreen) {
            ci.cancel();
        }
    }
}
