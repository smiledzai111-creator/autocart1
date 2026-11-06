package com.example.cartbomb.mixin;

import com.example.cartbomb.CartBombClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void cartbomb$holdUse(CallbackInfoReturnable<Boolean> cir) {
        if (!CartBombClient.holdUse) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isUsingItem()
                && (Object) this == mc.options.keyUse) {
            cir.setReturnValue(true);
        }
    }
}
