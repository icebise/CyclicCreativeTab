package com.bisya.cycliccreativetabs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point holder for Cyclic Creative Tabs.
 *
 * <p>The mod is entirely client side and works through the mixins declared in
 * {@code cycliccreativetabs.mixins.json}; this class only keeps the mod id and
 * the shared logger.</p>
 */
public final class CyclicCreativeTabs {
    public static final String MOD_ID = "cycliccreativetabs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CyclicCreativeTabs() {
    }
}
