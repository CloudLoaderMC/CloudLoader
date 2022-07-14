/*
 * Copyright 2022 DaRubyMiner360 & Cloud Loader
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.minecraftforge.fml.fabricmod;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.forgespi.language.ILifecycleEvent;
import net.minecraftforge.forgespi.language.IModLanguageProvider;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.minecraftforge.fml.Logging.LOADING;

public class FabricModLanguageProvider implements IModLanguageProvider
{
    private record FabricModTarget(String modId) implements IModLanguageProvider.IModLanguageLoader
    {
        private static final Logger LOGGER = LogManager.getLogger();

        @SuppressWarnings("unchecked")
        @Override
        public <T> T loadMod(final IModInfo info, final ModFileScanData modFileScanResults, ModuleLayer gameLayer)
        {
            // This language class is loaded in the system level classloader - before the game even starts
            // So we must treat container construction as an arms length operation, and load the container
            // in the classloader of the game - the context classloader is appropriate here.
            try {
                final Class<?> fmlContainer = Class.forName("net.minecraftforge.fml.fabricmod.FabricModContainer", true, Thread.currentThread().getContextClassLoader());
                LOGGER.debug(LOADING, "Loading FabricModContainer from classloader {} - got {}", Thread.currentThread().getContextClassLoader(), fmlContainer.getClassLoader());
                final Constructor<?> constructor = fmlContainer.getConstructor(IModInfo.class, ModFileScanData.class, ModuleLayer.class);
                return (T) constructor.newInstance(info, modFileScanResults, gameLayer);
            } catch (InvocationTargetException e) {
                LOGGER.fatal(LOADING, "Failed to build mod", e);
                if (e.getTargetException() instanceof ModLoadingException mle) {
                    throw mle;
                } else {
                    throw new ModLoadingException(info, ModLoadingStage.CONSTRUCT, "fml.modloading.failedtoloadmodclass", e);
                }
            } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                LOGGER.fatal(LOADING, "Unable to load FMLModContainer, wut?", e);
                final Class<RuntimeException> mle = (Class<RuntimeException>) LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.ModLoadingException", true, Thread.currentThread().getContextClassLoader()));
                final Class<ModLoadingStage> mls = (Class<ModLoadingStage>) LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.ModLoadingStage", true, Thread.currentThread().getContextClassLoader()));
                throw LamdbaExceptionUtils.uncheck(() -> LamdbaExceptionUtils.uncheck(() -> mle.getConstructor(IModInfo.class, mls, String.class, Throwable.class)).newInstance(info, Enum.valueOf(mls, "CONSTRUCT"), "fml.modloading.failedtoloadmodclass", e));
            }
        }
    }

    @Override
    public String name()
    {
        return "fabricfml";
    }

    @Override
    public Consumer<ModFileScanData> getFileVisitor()
    {
        return scanResult ->
        {
            final Map<String, FabricModTarget> modTargetMap = scanResult.getIModInfoData().stream()
                    .flatMap(fi->fi.getMods().stream())
                    .map(IModInfo::getModId)
                    .map(FabricModTarget::new)
                    .collect(Collectors.toMap(FabricModTarget::modId, Function.identity(), (a, b)->a));
            scanResult.addLanguageLoader(modTargetMap);
        };
    }

    @Override
    public <R extends ILifecycleEvent<R>> void consumeLifecycleEvent(final Supplier<R> consumeEvent)
    {
    }
}
