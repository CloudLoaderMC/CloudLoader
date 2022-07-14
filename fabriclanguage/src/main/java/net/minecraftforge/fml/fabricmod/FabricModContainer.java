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


import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.slf4j.Logger;

import static net.minecraftforge.fml.loading.LogMarkers.LOADING;

public class FabricModContainer extends ModContainer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ModFileScanData scanResults;
    private Object modInstance;

    public FabricModContainer(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer gameLayer)
    {
        super(info);
        LOGGER.debug(LOADING, "Creating FabricModContainer for {}", info.getModId());
        this.scanResults = modFileScanResults;
        this.modInstance = new Object();
        this.contextExtension = () -> null;
        this.extensionPoints.remove(IExtensionPoint.DisplayTest.class);
    }

    @Override
    public boolean matches(Object mod)
    {
        return mod == modInstance;
    }

    @Override
    public Object getMod()
    {
        return modInstance;
    }

    @Override
    protected <T extends Event & IModBusEvent> void acceptEvent(final T e)
    {
    }
}
