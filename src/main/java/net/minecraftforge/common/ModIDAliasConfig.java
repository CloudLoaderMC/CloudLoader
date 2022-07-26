/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common;

import ml.darubyminer360.cloud.loading.LoadingConstants;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;

import java.util.*;

import static net.minecraftforge.fml.Logging.CLOUDMOD;

public class ModIDAliasConfig {
    public final ConfigValue<List<? extends List<? extends String>>> modIdAliases;

    ModIDAliasConfig(ForgeConfigSpec.Builder builder) {
        modIdAliases = builder
                .translation("cloud.configgui.modIdAliases")
                .defineList("modIdAliases", LoadingConstants.modIdAliases, entry -> true);
    }

    static final ForgeConfigSpec commonSpec;
    public static final ModIDAliasConfig COMMON;
    static {
        final Pair<ModIDAliasConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ModIDAliasConfig::new);
        commonSpec = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading configEvent) {
        LogManager.getLogger().debug(CLOUDMOD, "Loaded Mod ID Alias config file {}", configEvent.getConfig().getFileName());
        LoadingConstants.modIdAliases = COMMON.modIdAliases.get();
    }

    @SubscribeEvent
    public static void onFileChange(final ModConfigEvent.Reloading configEvent) {
        LogManager.getLogger().debug(CLOUDMOD, "Mod ID Alias config just got changed on the file system!");
        LoadingConstants.modIdAliases = COMMON.modIdAliases.get();
    }

    //General
    //public static boolean disableVersionCheck = false;
    //public static boolean logCascadingWorldGeneration = true; // see Chunk#logCascadingWorldGeneration()
    //public static boolean fixVanillaCascading = false;
}
