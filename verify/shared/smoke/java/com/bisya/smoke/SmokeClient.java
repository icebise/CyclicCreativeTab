package com.bisya.smoke;

import net.fabricmc.api.ClientModInitializer;

/**
 * Development-only entry point of the verification mod.
 *
 * <p>The verification mod is never published. It is compiled into the runtime
 * projects under {@code verify/} and does two things: it registers a handful
 * of extra creative tabs (from a mixin, see {@code CreativeModeTabsMixin}) so
 * that the creative inventory really needs more than one page, and it drives
 * the game through the menu code to check the paging added by Cyclic Creative
 * Tabs.</p>
 */
public final class SmokeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SmokeTest.start();
    }
}
