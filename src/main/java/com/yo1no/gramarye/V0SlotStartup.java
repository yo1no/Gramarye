package com.yo1no.gramarye;

import net.minecraft.server.MinecraftServer;

record V0SlotStartup(MinecraftServer server, V0CatalogAuthority authority,
        V0StartupLoadState v0State, SkillRuntimeService runtime) { }
