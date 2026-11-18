package com.bisya.cycliccreativetabs.mixin;

import com.bisya.cycliccreativetabs.client.FabricPaging;
import com.bisya.cycliccreativetabs.client.TabPage;
import com.bisya.cycliccreativetabs.client.TabPaging;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes creative tab page navigation cyclic.
 *
 * <p>The original NeoForge mod only rewires the {@code <} / {@code >} buttons
 * that NeoForge already draws. On Fabric the paging is provided either by
 * Fabric API or, when Fabric API is absent, by this mod itself, so the mixin
 * supports both cases:</p>
 *
 * <ul>
 *   <li><b>Fabric API present</b> - Fabric keeps ownership of the pages and of
 *   hiding tabs that are not on the current page. Its two page buttons are
 *   removed and replaced with always active buttons that wrap around by
 *   calling Fabric's own page switching method.</li>
 *   <li><b>No Fabric API</b> - tabs are grouped into pages of ten (five above
 *   the inventory, five below) and tabs that are not on the displayed page are
 *   moved far off screen, which makes vanilla's rendering, hovering and
 *   clicking code ignore them.</li>
 * </ul>
 *
 * <p>In both cases the buttons sit where NeoForge puts them and a
 * {@code current / total} indicator is drawn above the inventory.</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
        extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

    @Unique
    private static final int CCT_PAGE_SIZE = TabPage.MAX_TABS;
    @Unique
    private static final int CCT_OFFSCREEN = -10000;
    @Unique
    private static final int CCT_BUTTON_SIZE = 20;

    @Shadow
    private static CreativeModeTab selectedTab;

    @Unique
    private final List<TabPage> cct$pages = new ArrayList<>();
    @Unique
    private int cct$pageIndex;
    @Unique
    private boolean cct$fabricPaging;
    @Unique
    private Button cct$previousButton;
    @Unique
    private Button cct$nextButton;
    @Unique
    private boolean cct$externalButtonsRemoved;

    protected CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
            Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cct$init(CallbackInfo ci) {
        TabPaging.clear();
        this.cct$pages.clear();
        this.cct$pageIndex = 0;

        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        this.cct$fabricPaging = FabricPaging.isSupported(screen);
        this.cct$previousButton = null;
        this.cct$nextButton = null;
        this.cct$externalButtonsRemoved = false;
        if (this.cct$fabricPaging) {
            this.cct$removeExternalPageButtons();
        } else {
            this.cct$buildPages();
        }

        this.cct$addButtons();
    }

    /**
     * Adds the two cyclic page buttons.
     *
     * <p>Fabric API computes its page count from inside {@code init} as well,
     * and the order of those two injections is not guaranteed, so this can run
     * a second time from the first frame. It never adds a second pair.</p>
     */
    @Unique
    private void cct$addButtons() {
        if (this.cct$previousButton != null || this.cct$nextButton != null) {
            return;
        }
        if (this.cct$pageCount() <= 1) {
            return;
        }
        this.cct$previousButton = this.addRenderableWidget(
                Button.builder(Component.literal("<"), button -> cct$cycle(-1))
                        .bounds(this.leftPos, this.topPos - 50, CCT_BUTTON_SIZE, CCT_BUTTON_SIZE)
                        .build());
        this.cct$nextButton = this.addRenderableWidget(
                Button.builder(Component.literal(">"), button -> cct$cycle(1))
                        .bounds(this.leftPos + this.imageWidth - CCT_BUTTON_SIZE, this.topPos - 50,
                                CCT_BUTTON_SIZE, CCT_BUTTON_SIZE)
                        .build());
    }

    @Unique
    private void cct$buildPages() {
        if (this.minecraft == null || this.minecraft.player == null
                || !this.minecraft.player.hasInfiniteMaterials()) {
            return;
        }

        List<CreativeModeTab> paged = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (!cct$isFixedTab(tab)) {
                paged.add(tab);
            }
        }

        for (int i = 0; i < paged.size(); i += CCT_PAGE_SIZE) {
            this.cct$pages.add(new TabPage(paged.subList(i, Math.min(i + CCT_PAGE_SIZE, paged.size()))));
        }
        if (this.cct$pages.isEmpty()) {
            this.cct$pages.add(new TabPage(List.of()));
        }

        // Remember the page the currently selected tab lives on.
        if (selectedTab != null && !cct$isFixedTab(selectedTab)) {
            for (int i = 0; i < this.cct$pages.size(); i++) {
                if (this.cct$pages.get(i).contains(selectedTab)) {
                    this.cct$pageIndex = i;
                    break;
                }
            }
        }
        TabPaging.set(this.cct$currentPage());
    }

    /**
     * Fabric API draws its own {@code <} / {@code >} buttons at the top of the
     * inventory. They are replaced by the always active cyclic ones, otherwise
     * both pairs would be visible and only the Fabric ones would stop at the
     * first and last page.
     */
    @Unique
    private void cct$removeExternalPageButtons() {
        List<GuiEventListener> external = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (child == this.cct$previousButton || child == this.cct$nextButton) {
                continue;
            }
            if (child instanceof AbstractButton button) {
                String label = button.getMessage().getString();
                if ("<".equals(label) || ">".equals(label)) {
                    external.add(button);
                }
            }
        }
        for (GuiEventListener child : external) {
            this.removeWidget(child);
        }
    }

    /**
     * Fabric API adds its buttons from inside {@code init} as well. Depending
     * on the loader's mixin order they can therefore appear after this mixin
     * has already cleaned the screen. Removing them once more right before the
     * first frame makes the result independent of that order.
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void cct$dropLateExternalButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (this.cct$fabricPaging && !this.cct$externalButtonsRemoved) {
            this.cct$externalButtonsRemoved = true;
            this.cct$removeExternalPageButtons();
        }
        this.cct$addButtons();
    }

    @Inject(method = "getTabX", at = @At("HEAD"), cancellable = true)
    private void cct$getTabX(CreativeModeTab tab, CallbackInfoReturnable<Integer> cir) {
        if (!this.cct$fabricPaging && !cct$isFixedTab(tab) && this.cct$indexOnCurrentPage(tab) < 0) {
            cir.setReturnValue(CCT_OFFSCREEN);
        }
    }

    @Inject(method = "getTabY", at = @At("HEAD"), cancellable = true)
    private void cct$getTabY(CreativeModeTab tab, CallbackInfoReturnable<Integer> cir) {
        if (!this.cct$fabricPaging && !cct$isFixedTab(tab) && this.cct$indexOnCurrentPage(tab) < 0) {
            cir.setReturnValue(CCT_OFFSCREEN);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cct$renderPageIndicator(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        int pages = this.cct$pageCount();
        if (pages <= 1) {
            return;
        }
        Component page = Component.literal((this.cct$currentPageIndex() + 1) + " / " + pages);
        graphics.centeredText(this.font, page, this.leftPos + this.imageWidth / 2,
                this.topPos - 44, -1);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void cct$removed(CallbackInfo ci) {
        TabPaging.clear();
    }

    @Unique
    private TabPage cct$currentPage() {
        return this.cct$pages.get(Math.min(this.cct$pageIndex, this.cct$pages.size() - 1));
    }

    @Unique
    private int cct$indexOnCurrentPage(CreativeModeTab tab) {
        if (this.cct$pages.isEmpty()) {
            return -1;
        }
        return this.cct$currentPage().indexOf(tab);
    }

    @Unique
    private int cct$pageCount() {
        if (this.cct$fabricPaging) {
            return Math.max(1, FabricPaging.pageCount(this));
        }
        return Math.max(1, this.cct$pages.size());
    }

    @Unique
    private int cct$currentPageIndex() {
        if (this.cct$fabricPaging) {
            return Math.max(0, FabricPaging.currentPage(this));
        }
        return this.cct$pageIndex;
    }

    @Unique
    private void cct$cycle(int direction) {
        int pages = this.cct$pageCount();
        if (pages <= 1) {
            return;
        }
        int target = Math.floorMod(this.cct$currentPageIndex() + direction, pages);
        if (this.cct$fabricPaging) {
            FabricPaging.goToPage(this, target);
        } else {
            this.cct$pageIndex = target;
            TabPaging.set(this.cct$currentPage());
        }
    }

    /**
     * Tabs that vanilla keeps at a fixed position: the search tab, the
     * inventory tab, the hotbar tab and the operator blocks tab. The latter
     * two are ordinary category tabs but right aligned, like the two special
     * tabs.
     */
    @Unique
    private static boolean cct$isFixedTab(CreativeModeTab tab) {
        return tab.getType() != CreativeModeTab.Type.CATEGORY || tab.isAlignedRight();
    }
}
