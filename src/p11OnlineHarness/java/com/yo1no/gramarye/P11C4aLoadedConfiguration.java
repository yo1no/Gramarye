package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;

/** Excluded observer of the platform-selected local SERVER config, not a guessed world path. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aLoadedConfiguration {
    private static volatile ModConfig loaded;

    private P11C4aLoadedConfiguration() {}

    @SubscribeEvent
    static void loading(ModConfigEvent.Loading event) {
        if (!P11C4aEvidence.enabled() && !P11L1ServerHarness.enabled()
                && !P11CooldownServerHarness.selected()) { return; }
        var config = event.getConfig();
        if (config.getType() == ModConfig.Type.SERVER && Gramarye.MOD_ID.equals(config.getModId())
                && P5ServerRuntimeConfig.CONFIG_FILE_NAME.equals(config.getFileName())) {
            loaded = config;
        }
    }

    static String hash() throws IOException {
        return (String) snapshot().get("sha256");
    }

    /** Actual platform selection, never a guessed world/config path or an authority grant. */
    static java.util.Map<String, Object> snapshot() throws IOException {
        var config = loaded;
        P11C4aEvidence.require(config != null && config.getLoadedConfig() != null,
                "MISSING_PLATFORM_LOADED_SERVER_CONFIG");
        var file = config.getFullPath().toAbsolutePath().normalize();
        var game = FMLPaths.GAMEDIR.get().toRealPath();
        var realFile = file.toRealPath();
        String inheritedHash = P11L1RestartProbe.readSelected()
                ? P11L1RestartProbe.expectedConfigurationHash(realFile)
                : P11CooldownRestartProbe.readSelected() ? P11CooldownRestartProbe.expectedConfigurationHash(realFile) : null;
        P11C4aEvidence.require((file.startsWith(game) && realFile.startsWith(game) || inheritedHash != null)
                && (inheritedHash == null || file.equals(realFile))
                && file.getFileName().toString().equals(P5ServerRuntimeConfig.CONFIG_FILE_NAME)
                && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file),
                "INVALID_PLATFORM_LOADED_SERVER_CONFIG_PATH");
        var sha256 = P11C4aEvidence.hash(Files.readAllBytes(file));
        P11C4aEvidence.require(inheritedHash == null || inheritedHash.equals(sha256),
                "RESTART_PLATFORM_LOADED_CONFIG_BYTES_CHANGED");
        return java.util.Map.of("path", realFile.toString(), "sha256", sha256);
    }
}
