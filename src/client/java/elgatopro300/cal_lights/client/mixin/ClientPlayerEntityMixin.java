package elgatopro300.cal_lights.client.mixin;

import elgatopro300.cal_lights.ui.CALEditorScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void cal_cancelSendMovementPackets(CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof CALEditorScreen) {
            ci.cancel();
        }
    }
}
