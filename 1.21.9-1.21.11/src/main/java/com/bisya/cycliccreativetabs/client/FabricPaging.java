package com.bisya.cycliccreativetabs.client;

import java.lang.reflect.Method;

/**
 * Optional bridge to the paging that Fabric API adds to the creative
 * inventory.
 *
 * <p>Fabric API (through {@code fabric-item-group-api-v1} on 1.21.x and
 * {@code fabric-creative-tab-api-v1} on 26.x) already splits the creative tabs
 * into pages of ten and adds its own {@code <} / {@code >} buttons, but those
 * buttons stop at the first and last page. When that API is present this class
 * lets the mod keep Fabric's page handling and only make the buttons cyclic,
 * which is exactly what the original NeoForge mod does.</p>
 *
 * <p>The API is accessed reflectively so that the released jar does not need
 * Fabric API at all: without it the mod falls back to its own paging.</p>
 */
public final class FabricPaging {
    private static Method pageCount;
    private static Method currentPage;
    private static Method switchToPage;
    private static boolean unavailable;

    private FabricPaging() {
    }

    /** {@return whether the given screen is paged by Fabric API} */
    public static boolean isSupported(Object screen) {
        if (pageCount != null) {
            return true;
        }
        if (unavailable || screen == null) {
            return false;
        }
        try {
            Class<?> type = screen.getClass();
            Method count = type.getMethod("getPageCount");
            Method current = type.getMethod("getCurrentPage");
            Method goTo = type.getMethod("switchToPage", int.class);
            pageCount = count;
            currentPage = current;
            switchToPage = goTo;
            return true;
        } catch (NoSuchMethodException | SecurityException exception) {
            unavailable = true;
            return false;
        }
    }

    public static int pageCount(Object screen) {
        return (Integer) invokeNoArgs(pageCount, screen, 1);
    }

    public static int currentPage(Object screen) {
        return (Integer) invokeNoArgs(currentPage, screen, 0);
    }

    public static void goToPage(Object screen, int page) {
        if (switchToPage == null) {
            return;
        }
        try {
            switchToPage.invoke(screen, page);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Fabric API changed under us; the buttons simply do nothing.
        }
    }

    private static Object invokeNoArgs(Method method, Object screen, int fallback) {
        if (method == null) {
            return fallback;
        }
        try {
            return method.invoke(screen);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return fallback;
        }
    }
}
