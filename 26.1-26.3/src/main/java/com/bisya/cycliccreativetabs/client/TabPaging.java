package com.bisya.cycliccreativetabs.client;

/**
 * Holds the page that is currently displayed by the mod's own paging
 * implementation.
 *
 * <p>Vanilla positions every creative tab with {@code CreativeModeTab#row()}
 * and {@code CreativeModeTab#column()}, values that are fixed when a tab is
 * registered. When the mod regroups tabs into pages, a tab can end up on a
 * different row than the one it was registered with, so the two mixins that
 * add paging share this holder to report the position of a tab on the page
 * that is being shown.</p>
 *
 * <p>The holder is only filled while a screen using the fallback paging is
 * open and it is cleared as soon as that screen goes away. If Fabric API is
 * installed its own paging is used instead and this holder stays empty.</p>
 */
public final class TabPaging {
    private static TabPage current;

    private TabPaging() {
    }

    public static void set(TabPage page) {
        current = page;
    }

    public static void clear() {
        current = null;
    }

    /** {@return the displayed page, or {@code null} when the mod is not paging} */
    public static TabPage current() {
        return current;
    }
}
