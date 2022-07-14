/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;

public interface IConfigSpec<T extends IConfigSpec<T>> extends UnmodifiableConfig {
    @SuppressWarnings("unchecked")
    default T self() {
        return (T) this;
    }

    void acceptConfig(Config data);

    boolean isCorrecting();

    boolean isCorrect(Config fileConfig);

    int correct(Config fileConfig);

    void afterReload();
}
