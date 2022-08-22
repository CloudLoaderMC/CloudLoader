/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading;

import java.util.Map;

public record VersionInfo(String cloudVersion, String forgeVersion, String fabricVersion, String mcVersion, String mcpVersion, String forgeGroup) {
    VersionInfo(Map<String, ?> arguments) {
        this((String) arguments.get("cloudVersion"), (String) arguments.get("forgeVersion"), (String) arguments.get("fabricVersion"), (String) arguments.get("mcVersion"), (String) arguments.get("mcpVersion"), (String) arguments.get("forgeGroup"));
    }

    public String mcAndCloudVersion() {
        return mcVersion+"-"+cloudVersion;
    }

    public String mcAndForgeVersion() {
        return mcVersion+"-"+forgeVersion;
    }

    public String mcAndFabricVersion() {
        return mcVersion+"-"+fabricVersion;
    }

    public String mcAndMCPVersion() {
        return mcVersion + "-" + mcpVersion;
    }
}
