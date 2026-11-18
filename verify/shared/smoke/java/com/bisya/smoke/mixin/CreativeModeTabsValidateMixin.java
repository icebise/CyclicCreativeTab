package com.bisya.smoke.mixin;

import com.bisya.smoke.SmokeTabs;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips vanilla's duplicate position check.
 *
 * <p>The verification tabs are registered with the columns of the vanilla
 * tabs, because vanilla indexes its tab textures with {@code column()} and
 * only has seven of them. Vanilla would abort startup with
 * {@code Duplicate position} once it sees them. Fabric API cancels the very
 * same check in its own item group mixin, so this mirrors production
 * behaviour.</p>
 */
@Mixin(CreativeModeTabs.class)
public abstract class CreativeModeTabsValidateMixin {
    @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
    private static void cct$skipDuplicateCheck(CallbackInfo ci) {
        if (SmokeTabs.count() > 0) {
            ci.cancel();
        }
    }
}
