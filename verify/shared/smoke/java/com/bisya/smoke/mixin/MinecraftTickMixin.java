package com.bisya.smoke.mixin;

import com.bisya.smoke.SmokeTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the verification script from the client tick loop. */
@Mixin(Minecraft.class)
public abstract class MinecraftTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void cct$smokeTick(CallbackInfo ci) {
        SmokeTest.tick((Minecraft) (Object) this);
    }
}
