package com.subsonar.vista.mixin;

import com.subsonar.vista.client.SubSonarVistaClient;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sub Sonar keeps scan data only for the console whose screen is open; also keep it for the one on the TV. */
@Mixin(targets = "com.subsonar.client.ClientPayloadHandler")
public abstract class ClientPayloadHandlerMixin {
    @Inject(method = "viewing(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private static void subsonarVista$watchingOnTv(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SubSonarVistaClient.isWatchingOnTv(pos)) {
            cir.setReturnValue(true);
        }
    }
}
