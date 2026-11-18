package com.bisya.smoke;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameType;

/**
 * Development-only automated verification of Cyclic Creative Tabs.
 *
 * <p>The script starts a fresh single player world, switches the player to
 * creative mode, opens the creative inventory and then presses the paging
 * buttons for real. It records the geometry of every tab on every page, the
 * page that is displayed and the position of both buttons, and writes a
 * machine readable {@code result.txt} next to its screenshots.</p>
 */
public final class SmokeTest {
    private static final String MC = System.getProperty("smoke.mc", "unknown");
    private static final String EXPECTED_API = System.getProperty("smoke.api", "unknown");
    private static final String EXPECTED_MODMENU = System.getProperty("smoke.modmenu", "no");
    private static final File OUTPUT = new File(System.getProperty("smoke.output", "smoke"));

    private static final int OFFSCREEN = -10000;
    private static final int TABS_PER_PAGE = 10;

    private static final StringBuilder REPORT = new StringBuilder();
    private static final List<PendingScreenshot> PENDING = new ArrayList<>();

    private static int phase;
    private static int phaseTicks;
    private static int totalTicks;
    private static int cooldown;
    private static int shutdownTicks = -1;
    private static int failures;
    private static boolean finished;
    private static boolean creativeRequested;
    private static boolean metadataChecked;

    private static Object screen;
    private static Method getTabX;
    private static Method getTabY;

    private SmokeTest() {
    }

    public static void start() {
        OUTPUT.mkdirs();
        log("verification of Minecraft " + MC + " (fabric api expected: " + EXPECTED_API
                + ", mod menu expected: " + EXPECTED_MODMENU + ")");
        if (SmokeTabs.error() != null) {
            fail("the verification tabs could not be registered: " + SmokeTabs.error());
        } else {
            log("registered " + SmokeTabs.count() + " extra creative tabs");
        }
    }

    public static void tick(Minecraft minecraft) {
        if (finished) {
            return;
        }
        totalTicks++;
        phaseTicks++;
        if (cooldown > 0) {
            cooldown--;
        }

        try {
            processScreenshots();
            switch (phase) {
                case 0 -> waitForMenu(minecraft);
                case 1 -> openWorldList(minecraft);
                case 2 -> createWorld(minecraft);
                case 3 -> waitForWorld(minecraft);
                case 4 -> switchToCreative(minecraft);
                case 5 -> openCreativeInventory(minecraft);
                case 6 -> firstPage(minecraft);
                case 7 -> secondPage(minecraft);
                case 8 -> thirdPage(minecraft);
                case 9 -> wrappedForward(minecraft);
                case 10 -> wrappedBackward(minecraft);
                case 11 -> finished(minecraft);
                default -> {
                }
            }
            if (totalTicks > 14000) {
                fail("global timeout in phase " + phase);
                shutdown(minecraft);
            }
        } catch (Throwable throwable) {
            fail("exception in phase " + phase + ": " + throwable);
            throwable.printStackTrace();
            shutdown(minecraft);
        }
    }

    // ------------------------------------------------------------------
    // Phases
    // ------------------------------------------------------------------

    private static void waitForMenu(Minecraft minecraft) {
        Screen current = currentScreen(minecraft);
        if (current == null) {
            return;
        }
        if (hasButton(current, "Singleplayer") || hasButton(current, "Create New World")) {
            nextPhase();
        } else if (isOnboarding(current)) {
            pressAny(current, "Continue", "Done", "Acknowledge", "Proceed");
        }
    }

    private static void openWorldList(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        Screen current = currentScreen(minecraft);
        if (current == null) {
            return;
        }
        if (press(current, "Create New World")) {
            log("pressed 'Create New World' on the world select screen");
            cooldown = 20;
            nextPhase();
        } else if (press(current, "Singleplayer")) {
            log("pressed 'Singleplayer'");
            cooldown = 20;
            nextPhase();
        }
    }

    private static void createWorld(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.player != null) {
            log("world loaded");
            nextPhase();
            return;
        }
        if (cooldown > 0) {
            return;
        }
        Screen current = currentScreen(minecraft);
        if (current == null) {
            return;
        }
        if (press(current, "Create New World")) {
            log("confirmed world creation");
            cooldown = 20;
        }
    }

    private static void waitForWorld(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null) {
            log("player joined the integrated server");
            nextPhase();
        }
    }

    private static void switchToCreative(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return;
        }
        if (!creativeRequested) {
            UUID id = minecraft.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && !player.setGameMode(GameType.CREATIVE)) {
                    log("the server reported that the game mode did not change");
                }
            });
            creativeRequested = true;
            cooldown = 20;
            log("asked the server for creative mode");
            return;
        }
        if (minecraft.player.hasInfiniteMaterials()) {
            log("creative mode is active on the client");
            nextPhase();
        } else if (phaseTicks > 200) {
            fail("creative mode never arrived on the client");
            nextPhase();
        }
    }

    private static void openCreativeInventory(Minecraft minecraft) {
        Screen current = currentScreen(minecraft);
        if (current instanceof CreativeModeInventoryScreen) {
            screen = current;
            cooldown = 10;
            log("the creative inventory is open");
            nextPhase();
            return;
        }
        if (minecraft.player == null) {
            return;
        }
        if (cooldown > 0) {
            return;
        }
        try {
            CreativeModeInventoryScreen creative = new CreativeModeInventoryScreen(
                    minecraft.player, FeatureFlags.VANILLA_SET, true);
            setScreen(minecraft, creative);
            screen = creative;
            log("opened the creative inventory screen");
            cooldown = 10;
            nextPhase();
        } catch (Throwable throwable) {
            fail("could not open the creative inventory: " + throwable);
            shutdown(minecraft);
        }
    }

    private static void firstPage(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        checkMetadata();

        // The mod opens on the page that holds the selected tab, and the
        // verification tabs are registered before the vanilla ones, so the
        // default selection is not necessarily on the first page. Walk back to
        // the first page before the paging assertions start.
        int current = currentPage();
        if (current < 0) {
            shutdown(minecraft);
            return;
        }
        if (current != 0) {
            log("the mod opened on page " + (current + 1) + ", walking back to page 1");
            pressPagingButton("<");
            cooldown = 10;
            return;
        }

        verifyPage(0);
        verifyButtons();
        screenshot(minecraft, "page-1");
        pressPagingButton(">");
        cooldown = 10;
        nextPhase();
    }

    private static void secondPage(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        verifyPage(1);
        verifyButtons();
        screenshot(minecraft, "page-2");
        pressPagingButton(">");
        cooldown = 10;
        nextPhase();
    }

    private static void thirdPage(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        int last = lastPage();
        int current = currentPage();
        if (current < last) {
            pressPagingButton(">");
            cooldown = 10;
            return;
        }
        verifyPage(last);
        screenshot(minecraft, "page-last");
        pressPagingButton(">");
        cooldown = 10;
        nextPhase();
    }

    private static void wrappedForward(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        verifyPage(0);
        log("the next button wrapped from the last page to the first page");
        screenshot(minecraft, "page-wrapped-forward");
        pressPagingButton("<");
        cooldown = 10;
        nextPhase();
    }

    private static void wrappedBackward(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        verifyPage(lastPage());
        log("the previous button wrapped from the first page to the last page");
        screenshot(minecraft, "page-wrapped-backward");
        pressPagingButton(">");
        cooldown = 10;
        nextPhase();
    }

    private static void finished(Minecraft minecraft) {
        if (cooldown > 0) {
            return;
        }
        verifyPage(0);
        shutdown(minecraft);
    }

    // ------------------------------------------------------------------
    // Page verification
    // ------------------------------------------------------------------

    private static void verifyPage(int expected) {
        Object target = screen;
        if (target == null) {
            fail("the creative inventory is not open");
            return;
        }
        boolean fabric = hasMethod(target, "getPageCount");
        List<CreativeModeTab> paged = pagedTabs();
        List<List<CreativeModeTab>> model = partition(paged);

        if (model.size() < 3) {
            fail("only " + model.size() + " page(s) of creative tabs - the extra tabs are missing");
            return;
        }

        if (fabric) {
            int pageCount = (Integer) invoke(target, "getPageCount");
            int current = (Integer) invoke(target, "getCurrentPage");
            log("fabric page " + (current + 1) + " / " + pageCount);
            check(pageCount >= model.size(),
                    "fabric reports " + pageCount + " pages, expected at least " + model.size());
            check(current == expected, "fabric shows page " + current + ", expected " + expected);
            int[] perPage = new int[pageCount];
            for (CreativeModeTab tab : paged) {
                int page = (Integer) invoke(target, "getPage", CreativeModeTab.class, tab);
                check(page >= 0 && page < pageCount, "fabric put a tab on page " + page
                        + ", outside 0.." + (pageCount - 1));
                perPage[Math.max(0, Math.min(pageCount - 1, page))]++;
            }
            log("fabric tabs per page: " + describe(perPage));
            check(perPage[0] >= TABS_PER_PAGE, "fabric page 1 holds " + perPage[0] + " tabs, expected the vanilla tabs");
            for (int i = 1; i < pageCount; i++) {
                check(perPage[i] <= TABS_PER_PAGE, "fabric page " + (i + 1) + " holds " + perPage[i] + " tabs");
            }
            check(perPage[pageCount - 1] >= 1, "the last fabric page is empty");
        } else {
            int current = fallbackPageIndex(model);
            log("built-in page " + (current + 1) + " / " + model.size());
            check(current == expected, "the mod shows page " + current + ", expected " + expected);
            verifyGeometry(model, expected);
        }
    }

    private static String describe(int[] values) {
        StringBuilder builder = new StringBuilder();
        for (int value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static int lastPage() {
        if (screen == null) {
            return 0;
        }
        if (hasMethod(screen, "getPageCount")) {
            return Math.max(0, (Integer) invoke(screen, "getPageCount") - 1);
        }
        List<List<CreativeModeTab>> model = partition(pagedTabs());
        return Math.max(0, model.size() - 1);
    }

    private static int currentPage() {
        if (screen == null) {
            return 0;
        }
        if (hasMethod(screen, "getPageCount")) {
            return (Integer) invoke(screen, "getCurrentPage");
        }
        return fallbackPageIndex(partition(pagedTabs()));
    }

    private static void verifyGeometry(List<List<CreativeModeTab>> model, int expected) {
        List<CreativeModeTab> paged = pagedTabs();
        List<CreativeModeTab> shown = model.get(expected);
        int topY = tabY(shown.get(0));
        int bottomY = shown.size() > TABS_PER_PAGE / 2 ? tabY(shown.get(TABS_PER_PAGE / 2)) : topY;

        for (CreativeModeTab tab : paged) {
            int index = shown.indexOf(tab);
            int x = tabX(tab);
            int y = tabY(tab);
            if (index >= 0) {
                int wantedX = 27 * (index % (TABS_PER_PAGE / 2));
                int wantedY = index < TABS_PER_PAGE / 2 ? topY : bottomY;
                check(x == wantedX, "tab " + index + " of page " + expected + " is at x=" + x
                        + ", expected " + wantedX);
                check(y == wantedY, "tab " + index + " of page " + expected + " is at y=" + y
                        + ", expected " + wantedY);
            } else {
                check(x == OFFSCREEN && y == OFFSCREEN, "a tab of another page is still visible at ("
                        + x + ", " + y + ")");
            }
        }

        if (shown.size() > TABS_PER_PAGE / 2) {
            check(topY != bottomY, "the top and bottom rows share the same y coordinate");
        }
    }

    private static int fallbackPageIndex(List<List<CreativeModeTab>> model) {
        try {
            Class<?> paging = Class.forName("com.bisya.cycliccreativetabs.client.TabPaging");
            Object page = paging.getMethod("current").invoke(null);
            if (page == null) {
                fail("the mod is not holding a current page");
                return -1;
            }
            @SuppressWarnings("unchecked")
            List<CreativeModeTab> tabs = (List<CreativeModeTab>) page.getClass().getMethod("tabs").invoke(page);
            for (int i = 0; i < model.size(); i++) {
                if (model.get(i).equals(tabs)) {
                    return i;
                }
            }
            fail("the mod's current page does not match any computed page (size " + tabs.size() + ")");
            return -1;
        } catch (Throwable throwable) {
            fail("could not read the mod's page state: " + throwable);
            return -1;
        }
    }

    private static void verifyButtons() {
        List<AbstractButton> previous = buttons("<");
        List<AbstractButton> next = buttons(">");
        check(previous.size() == 1, "found " + previous.size() + " buttons labelled '<', expected exactly 1");
        check(next.size() == 1, "found " + next.size() + " buttons labelled '>', expected exactly 1");
        if (previous.size() != 1 || next.size() != 1) {
            return;
        }

        int left = intField(screen, "leftPos");
        int top = intField(screen, "topPos");
        int width = intField(screen, "imageWidth");

        AbstractButton prev = previous.get(0);
        AbstractButton forward = next.get(0);
        log("buttons: '<' at " + prev.getX() + "," + prev.getY() + " size " + prev.getWidth() + "x"
                + prev.getHeight() + ", '>' at " + forward.getX() + "," + forward.getY());
        check(prev.getX() == left && prev.getY() == top - 50, "the '<' button is not at the NeoForge position");
        check(forward.getX() == left + width - 20 && forward.getY() == top - 50,
                "the '>' button is not at the NeoForge position");
        check(prev.getWidth() == 20 && prev.getHeight() == 20, "the '<' button is not 20x20");
        check(forward.getWidth() == 20 && forward.getHeight() == 20, "the '>' button is not 20x20");
        check(prev.active && forward.active, "a paging button is disabled - it should always be active");
    }

    private static void checkMetadata() {
        if (metadataChecked) {
            return;
        }
        metadataChecked = true;
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("cycliccreativetabs");
        if (container.isEmpty()) {
            fail("the Cyclic Creative Tabs jar is not loaded");
            return;
        }
        ModMetadata metadata = container.get().getMetadata();
        log("loaded mod '" + metadata.getName() + "' " + metadata.getVersion().getFriendlyString());
        check(metadata.getAuthors().stream().anyMatch(author -> "bisya".equals(author.getName())),
                "the original author is missing from the metadata");
        check(metadata.getAuthors().stream().anyMatch(author -> "Mr_Lan123".equals(author.getName())),
                "Mr_Lan123 is missing from the metadata authors");
        Optional<String> icon = metadata.getIconPath(128);
        check(icon.isPresent(), "the mod icon is not declared in fabric.mod.json");
        icon.ifPresent(path -> {
            log("mod icon: " + path);
            check(container.get().findPath(path).isPresent(), "the declared mod icon " + path + " is missing");
        });
        check(metadata.getCustomValue("modmenu") != null, "the Mod Menu metadata block is missing");

        boolean modmenu = FabricLoader.getInstance().isModLoaded("modmenu");
        if ("yes".equals(EXPECTED_MODMENU)) {
            check(modmenu, "Mod Menu was expected in this run but is not loaded");
        }
        if (modmenu) {
            log("Mod Menu is installed and will list the mod with its icon, authors and links");
        }
    }

    // ------------------------------------------------------------------
    // Creative tab helpers
    // ------------------------------------------------------------------

    private static List<CreativeModeTab> pagedTabs() {
        List<CreativeModeTab> paged = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && !tab.isAlignedRight()) {
                paged.add(tab);
            }
        }
        return paged;
    }

    private static List<List<CreativeModeTab>> partition(List<CreativeModeTab> tabs) {
        List<List<CreativeModeTab>> pages = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i += TABS_PER_PAGE) {
            pages.add(List.copyOf(tabs.subList(i, Math.min(i + TABS_PER_PAGE, tabs.size()))));
        }
        return pages;
    }

    private static int tabX(CreativeModeTab tab) {
        try {
            if (getTabX == null) {
                getTabX = screen.getClass().getDeclaredMethod("getTabX", CreativeModeTab.class);
                getTabX.setAccessible(true);
            }
            return (Integer) getTabX.invoke(screen, tab);
        } catch (Throwable throwable) {
            fail("could not call getTabX: " + throwable);
            return Integer.MIN_VALUE;
        }
    }

    private static int tabY(CreativeModeTab tab) {
        try {
            if (getTabY == null) {
                getTabY = screen.getClass().getDeclaredMethod("getTabY", CreativeModeTab.class);
                getTabY.setAccessible(true);
            }
            return (Integer) getTabY.invoke(screen, tab);
        } catch (Throwable throwable) {
            fail("could not call getTabY: " + throwable);
            return Integer.MIN_VALUE;
        }
    }

    private static void pressPagingButton(String label) {
        List<AbstractButton> found = buttons(label);
        if (found.size() != 1) {
            fail("cannot press '" + label + "': " + found.size() + " candidates");
            return;
        }
        press(found.get(0));
    }

    // ------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------

    private static Screen currentScreen(Minecraft minecraft) {
        try {
            Field field = Minecraft.class.getField("screen");
            return (Screen) field.get(minecraft);
        } catch (ReflectiveOperationException ignored) {
            // later versions moved the screen into the GUI helper
        }
        try {
            Field gui = Minecraft.class.getField("gui");
            Object value = gui.get(minecraft);
            Method method = value.getClass().getMethod("screen");
            return (Screen) method.invoke(value);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static void setScreen(Minecraft minecraft, Screen target) {
        try {
            Method method = Minecraft.class.getMethod("setScreen", Screen.class);
            method.invoke(minecraft, target);
            return;
        } catch (NoSuchMethodException ignored) {
            // 26.x renamed this method
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        try {
            Method method = Minecraft.class.getMethod("setScreenAndShow", Screen.class);
            method.invoke(minecraft, target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isOnboarding(Screen current) {
        String name = current.getClass().getSimpleName();
        return name.contains("Accessibility") || name.contains("Onboarding");
    }

    private static boolean hasButton(Screen current, String label) {
        return !buttons(current, label).isEmpty();
    }

    private static boolean press(Screen current, String label) {
        List<AbstractButton> found = buttons(current, label);
        if (found.isEmpty()) {
            return false;
        }
        press(found.get(0));
        return true;
    }

    private static boolean pressAny(Screen current, String... labels) {
        for (String label : labels) {
            if (press(current, label)) {
                return true;
            }
        }
        return false;
    }

    private static List<AbstractButton> buttons(String label) {
        if (screen == null) {
            return List.of();
        }
        return buttons(screen, label);
    }

    private static List<AbstractButton> buttons(Object current, String label) {
        List<AbstractButton> found = new ArrayList<>();
        if (!(current instanceof Screen currentScreen)) {
            return found;
        }
        for (GuiEventListener child : currentScreen.children()) {
            if (child instanceof AbstractButton button && label.equals(button.getMessage().getString())) {
                found.add(button);
            }
        }
        return found;
    }

    private static void press(AbstractButton button) {
        for (Method method : button.getClass().getMethods()) {
            if (!"onPress".equals(method.getName())) {
                continue;
            }
            try {
                if (method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    method.invoke(button);
                    return;
                }
                if (method.getParameterCount() == 1) {
                    Object argument = pressArgument(method.getParameterTypes()[0]);
                    if (argument == null) {
                        continue;
                    }
                    method.setAccessible(true);
                    method.invoke(button, argument);
                    return;
                }
            } catch (ReflectiveOperationException ignored) {
                // try the next overload
            }
        }
        log("could not press the button '" + button.getMessage().getString() + "'");
    }

    /**
     * Builds the argument of {@code onPress}.
     *
     * <p>Newer versions take an {@code InputWithModifiers}, which is an
     * interface without a usable implementation, so a proxy that reports no
     * input is passed instead. Buttons ignore it.</p>
     */
    private static Object pressArgument(Class<?> type) {
        try {
            Constructor<?> constructor = type.getConstructor(int.class, int.class);
            return constructor.newInstance(0, 0);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not a simple input value
        }
        if (!type.isInterface()) {
            return null;
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "no input";
                        };
                    }
                    Class<?> result = method.getReturnType();
                    if (result == boolean.class) {
                        return false;
                    }
                    if (result == int.class) {
                        return 0;
                    }
                    return null;
                });
    }

    private static boolean hasMethod(Object target, String name) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    private static Object invoke(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            fail("could not call " + name + ": " + exception);
            return -1;
        }
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object argument) {
        try {
            Method method = target.getClass().getMethod(name, parameter);
            return method.invoke(target, argument);
        } catch (ReflectiveOperationException exception) {
            fail("could not call " + name + ": " + exception);
            return -1;
        }
    }

    private static int intField(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (Integer) field.get(target);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                fail("could not read " + name + ": " + exception);
                return 0;
            }
        }
        fail("field " + name + " not found on " + target.getClass().getName());
        return 0;
    }

    // ------------------------------------------------------------------
    // Screenshots
    // ------------------------------------------------------------------

    private static void screenshot(Minecraft minecraft, String name) {
        File directory = new File(OUTPUT, "screenshots");
        directory.mkdirs();
        File target = new File(directory, name + ".png");
        File screenshots = new File("screenshots");
        Set<String> existing = pngNames(screenshots);

        // The built-in helper is the most reliable one: it grabs the frame that
        // is actually presented. The render-target overload writes black images
        // on some versions, so it is only used as a fallback.
        try {
            Class<?> screenshot = Class.forName("net.minecraft.client.Screenshot");
            Method grab = screenshot.getMethod("grab", Minecraft.class, boolean.class);
            grab.invoke(null, minecraft, false);
            PENDING.add(new PendingScreenshot(target, screenshots, existing, totalTicks));
            log("screenshot requested: " + name);
            return;
        } catch (Throwable ignored) {
            // fall through to the render target overload
        }

        try {
            Class<?> screenshot = Class.forName("net.minecraft.client.Screenshot");
            Method accessor = Minecraft.class.getMethod("getMainRenderTarget");
            Object renderTarget = accessor.invoke(minecraft);
            Consumer<Object> noop = component -> {
            };
            Method grab = screenshot.getMethod("grab", File.class,
                    Class.forName("com.mojang.blaze3d.pipeline.RenderTarget"), Consumer.class);
            grab.invoke(null, new File("."), renderTarget, noop);
            PENDING.add(new PendingScreenshot(target, screenshots, existing, totalTicks));
            log("screenshot requested through the render target: " + name);
        } catch (Throwable throwable) {
            log("screenshot failed: " + throwable);
        }
    }

    private static void processScreenshots() {
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            PendingScreenshot pending = PENDING.get(i);
            File newest = newestScreenshot(pending.directory, pending.existing);
            if (newest != null && newest.length() > 0) {
                long size = newest.length();
                if (size != pending.lastSize) {
                    pending.lastSize = size;
                    continue;
                }
                try {
                    Files.createDirectories(pending.target.getParentFile().toPath());
                    Files.copy(newest.toPath(), pending.target.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    if (pending.target.length() > 0) {
                        Files.deleteIfExists(newest.toPath());
                        log("screenshot saved: " + pending.target.getName());
                        PENDING.remove(i);
                        continue;
                    }
                } catch (Throwable throwable) {
                    log("could not move the screenshot: " + throwable);
                }
            }
            if (totalTicks - pending.requestedTick > 200) {
                log("screenshot timed out: " + pending.target.getName());
                PENDING.remove(i);
            }
        }
    }

    private static Set<String> pngNames(File directory) {
        Set<String> names = new HashSet<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }

    private static File newestScreenshot(File directory, Set<String> existing) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null) {
            return null;
        }
        File best = null;
        for (File file : files) {
            if (existing.contains(file.getName())) {
                continue;
            }
            if (best == null || file.lastModified() > best.lastModified()) {
                best = file;
            }
        }
        return best;
    }

    private static final class PendingScreenshot {
        private final File target;
        private final File directory;
        private final Set<String> existing;
        private final int requestedTick;
        private long lastSize = -1L;

        private PendingScreenshot(File target, File directory, Set<String> existing, int requestedTick) {
            this.target = target;
            this.directory = directory;
            this.existing = existing;
            this.requestedTick = requestedTick;
        }
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    private static void shutdown(Minecraft minecraft) {
        if (finished) {
            return;
        }
        if (shutdownTicks < 0) {
            shutdownTicks = totalTicks;
        }
        if (!PENDING.isEmpty() && totalTicks - shutdownTicks < 100) {
            return;
        }
        finished = true;

        report(failures == 0 ? "PASS" : "FAIL");
        for (PendingScreenshot pending : PENDING) {
            report("pending screenshot not confirmed: " + pending.target);
        }
        try {
            Files.createDirectories(OUTPUT.toPath());
            Files.writeString(new File(OUTPUT, "result.txt").toPath(), REPORT.toString(),
                    StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            System.out.println("[SMOKE] could not write the report: " + throwable);
        }
        System.out.println("[SMOKE] finished: " + (failures == 0 ? "PASS" : "FAIL (" + failures + " failures)"));
        System.out.flush();
        Runtime.getRuntime().halt(failures == 0 ? 0 : 1);
    }

    private static void nextPhase() {
        phase++;
        phaseTicks = 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    private static void fail(String message) {
        failures++;
        report("FAIL " + message);
        System.out.println("[SMOKE] FAIL " + message);
    }

    private static void log(String message) {
        report("INFO " + message);
        System.out.println("[SMOKE] " + message);
    }

    private static void report(String line) {
        REPORT.append(line).append('\n');
    }
}
