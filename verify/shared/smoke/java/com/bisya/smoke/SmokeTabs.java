package com.bisya.smoke;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Registers twelve extra creative tabs so that the creative inventory spans
 * three pages instead of one.
 *
 * <p>The tabs are registered from inside {@code CreativeModeTabs.bootstrap},
 * which is the only moment at which the creative tab registry may still be
 * modified; registering from a client initializer is too late in 1.21.1.</p>
 *
 * <p>The tabs reuse the columns of the vanilla tabs, because vanilla indexes
 * its seven entry tab textures with {@code column()} and would crash on any
 * column above six. Vanilla's duplicate position check therefore has to be
 * cancelled, which {@code CreativeModeTabsValidateMixin} does; Fabric API
 * cancels the same check for the same reason. The mod then replaces the
 * registered positions with its own page layout.</p>
 */
public final class SmokeTabs {
    public static final int COUNT = 12;
    private static final int FIRST_COLUMN = 0;

    private static final List<CreativeModeTab> TABS = new ArrayList<>();
    private static String error;

    private SmokeTabs() {
    }

    public static void register(Registry<CreativeModeTab> registry) {
        if (!TABS.isEmpty() || error != null) {
            return;
        }
        try {
            for (int i = 0; i < COUNT; i++) {
                CreativeModeTab.Row row = i < COUNT / 2 ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM;
                CreativeModeTab.Builder builder = CreativeModeTab.builder(row, FIRST_COLUMN + (i % (COUNT / 2)))
                        .title(Component.literal("CCT Smoke " + (i + 1)))
                        .icon(() -> new ItemStack(Items.DIRT));
                fillWithDirt(builder);
                CreativeModeTab tab = builder.build();
                Registry.register(registry, "cct_smoke_tab_" + i, tab);
                TABS.add(tab);
            }
        } catch (Throwable throwable) {
            error = throwable.toString();
        }
    }

    /**
     * Fills the tab with a single item so that vanilla does not treat it as an
     * empty tab and filters it out of the creative inventory.
     *
     * <p>{@code CreativeModeTab.Output} is a protected nested interface in
     * current versions, which means a plain {@code displayItems} lambda cannot
     * be compiled against it. The generator is therefore a dynamic proxy and
     * the item is added reflectively through that interface, which is public
     * in every supported version.</p>
     */
    private static void fillWithDirt(CreativeModeTab.Builder builder) {
        try {
            Class<?> generatorType = Class.forName("net.minecraft.world.item.CreativeModeTab$DisplayItemsGenerator");
            Object generator = Proxy.newProxyInstance(generatorType.getClassLoader(),
                    new Class<?>[] {generatorType}, (proxy, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "equals" -> proxy == arguments[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                default -> "dirt generator";
                            };
                        }
                        if ("accept".equals(method.getName()) && arguments != null && arguments.length == 2) {
                            acceptDirt(arguments[1]);
                        }
                        return null;
                    });
            Method displayItems = builder.getClass().getMethod("displayItems", generatorType);
            displayItems.setAccessible(true);
            displayItems.invoke(builder, generator);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not register the display items of the verification tab", exception);
        }
    }

    /**
     * Adds the tab's single item.
     *
     * <p>{@code CreativeModeTab.Output} is a protected nested interface on the
     * Minecraft versions this harness has to build against, so the item is
     * added reflectively through the interface. The interface itself is
     * public API in every supported version.</p>
     */
    private static void acceptDirt(Object output) {
        try {
            Class<?> outputType = Class.forName("net.minecraft.world.item.CreativeModeTab$Output");
            Method accept = outputType.getMethod("accept", ItemStack.class);
            accept.setAccessible(true);
            accept.invoke(output, new ItemStack(Items.DIRT));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not fill the verification tab", exception);
        }
    }

    public static int count() {
        return TABS.size();
    }

    public static String error() {
        return error;
    }
}
