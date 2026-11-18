package com.bisya.smoke.mixin;

import com.bisya.smoke.SmokeTabs;
import net.minecraft.core.Registry;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the verification tabs while the creative tab registry is still open.
 *
 * <p>Vanilla bootstraps the creative tabs during game startup and freezes the
 * registry right afterwards, so a client initializer cannot add tabs any more.
 * This injection runs at the end of vanilla's own registration.</p>
 */
@Mixin(CreativeModeTabs.class)
public abstract class CreativeModeTabsMixin {
    @Inject(method = "bootstrap", at = @At("HEAD"))
    private static void cct$smokeTabs(Registry<CreativeModeTab> registry,
            CallbackInfoReturnable<CreativeModeTab> cir) {
        SmokeTabs.register(registry);
    }
}
