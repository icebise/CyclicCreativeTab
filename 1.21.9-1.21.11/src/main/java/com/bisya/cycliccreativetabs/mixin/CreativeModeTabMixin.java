package com.bisya.cycliccreativetabs.mixin;

import com.bisya.cycliccreativetabs.client.TabPage;
import com.bisya.cycliccreativetabs.client.TabPaging;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports the position a tab has on the page that is currently displayed.
 *
 * <p>A tab's row and column are fixed at registration time, but paging can
 * move a tab to the other row (top or bottom) or to another column. Vanilla
 * uses {@code row()} for the vertical position and the shape of the tab
 * (through {@code renderTabButton}) and {@code column()} for its column and
 * shape, so both are redirected to the page model while the fallback paging
 * is active.</p>
 *
 * <p>Tabs that are not part of the displayed page keep their registered
 * position; the screen mixin moves those far off screen.</p>
 */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Inject(method = "column", at = @At("HEAD"), cancellable = true)
    private void cct$pageColumn(CallbackInfoReturnable<Integer> cir) {
        TabPage page = TabPaging.current();
        if (page == null) {
            return;
        }
        int index = page.indexOf((CreativeModeTab) (Object) this);
        if (index >= 0) {
            cir.setReturnValue(page.column(index));
        }
    }

    @Inject(method = "row", at = @At("HEAD"), cancellable = true)
    private void cct$pageRow(CallbackInfoReturnable<CreativeModeTab.Row> cir) {
        TabPage page = TabPaging.current();
        if (page == null) {
            return;
        }
        int index = page.indexOf((CreativeModeTab) (Object) this);
        if (index >= 0) {
            cir.setReturnValue(page.isTop(index) ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM);
        }
    }
}
