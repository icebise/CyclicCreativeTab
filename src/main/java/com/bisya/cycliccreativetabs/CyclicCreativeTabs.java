package com.bisya.cycliccreativetabs;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(CyclicCreativeTabs.MODID)
public class CyclicCreativeTabs {
    public static final String MODID = "cycliccreativetabs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CyclicCreativeTabs(IEventBus modEventBus, ModContainer modContainer) {
    }
}
