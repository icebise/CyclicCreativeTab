package com.bisya.cycliccreativetabs.client;

import java.util.List;
import net.minecraft.world.item.CreativeModeTab;

/**
 * One page of creative tabs. A page holds at most ten tabs: the first five
 * are drawn above the inventory, the remaining five below it.
 */
public final class TabPage {
    /** Tabs shown on a single page, as used by NeoForge. */
    public static final int MAX_TABS = 10;
    private static final int PER_ROW = MAX_TABS / 2;

    private final List<CreativeModeTab> tabs;

    public TabPage(List<CreativeModeTab> tabs) {
        this.tabs = List.copyOf(tabs);
    }

    public List<CreativeModeTab> tabs() {
        return this.tabs;
    }

    public boolean isEmpty() {
        return this.tabs.isEmpty();
    }

    public boolean contains(CreativeModeTab tab) {
        return this.indexOf(tab) >= 0;
    }

    public int indexOf(CreativeModeTab tab) {
        for (int i = 0; i < this.tabs.size(); i++) {
            if (this.tabs.get(i) == tab) {
                return i;
            }
        }
        return -1;
    }

    public boolean isTop(int index) {
        return index < PER_ROW;
    }

    public int column(int index) {
        return index % PER_ROW;
    }
}
