package com.bisya.cycliccreativetabs.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

    @Shadow @Final private List<CreativeTabsScreenPage> pages;
    @Shadow private CreativeTabsScreenPage currentPage;
    @Shadow public abstract void setCurrentPage(CreativeTabsScreenPage page);

    protected CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        if (this.pages == null || this.pages.size() <= 1) return;

        Button prevBtn = null;
        Button nextBtn = null;

        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                String text = button.getMessage().getString();
                if ("<".equals(text)) {
                    prevBtn = button;
                } else if (">".equals(text)) {
                    nextBtn = button;
                }
            }
        }

        if (prevBtn != null) {
            replaceButton(prevBtn, -1);
        }
        if (nextBtn != null) {
            replaceButton(nextBtn, 1);
        }
    }

    private void replaceButton(Button oldBtn, int direction) {
        Button cyclicBtn = Button.builder(oldBtn.getMessage(), btn -> {
            int total = this.pages.size();
            if (total <= 1) return;
            int currentIndex = this.pages.indexOf(this.currentPage);
            int nextIndex = (currentIndex + direction + total) % total;
            this.setCurrentPage(this.pages.get(nextIndex));
        }).bounds(oldBtn.getX(), oldBtn.getY(), oldBtn.getWidth(), oldBtn.getHeight()).build();

        this.removeWidget(oldBtn);
        this.addRenderableWidget(cyclicBtn);
    }
}
