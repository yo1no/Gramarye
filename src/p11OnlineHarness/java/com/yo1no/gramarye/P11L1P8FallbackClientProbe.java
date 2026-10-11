package com.yo1no.gramarye;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Read-only observations of the original production execution and native sound event. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11L1P8FallbackClientProbe {
    private static final ResourceLocation DEFAULT_SOUND = ResourceLocation.withDefaultNamespace("entity.experience_orb.pickup");
    private static P8ClientPresentationExecution execution;
    private static PresentationEventPayload payload;
    private static boolean missingAsset, scope, failed, written;
    private static int calls, soundEvents;
    private static long connectionGeneration, worldGeneration, resourceGeneration;
    private static Map<String, Object> completed;
    private P11L1P8FallbackClientProbe() { }
    private static boolean selected() {
        return P11L1P8FallbackProbe.selected() && "b".equals(System.getProperty("gramarye.p11.online.role", ""));
    }

    public static void constructed(Object actual, Object backend) {
        if (!selected()) return;
        if (!(actual instanceof P8ClientPresentationExecution original) || execution != null
                || backend != MinecraftP8ClientPresentationBackend.INSTANCE) { failed = true; return; }
        execution = original;
    }

    public static void assetObserved(ResourceLocation asset, boolean originalExists) {
        if (!selected() || !P11L1P8FallbackProbe.ASSET.equals(asset)) return;
        if (originalExists) failed = true;
        else missingAsset = true;
    }

    public static boolean entering(Object actual, Object value, long connection, long world, long resource) {
        if (!selected() || !(value instanceof PresentationEventPayload event) || event.kind() != PresentationEventKind.HIT
                || !event.appearance().soundProfileId().filter(P11L1P8FallbackProbe.PROFILE::equals).isPresent()) return false;
        if (!Minecraft.getInstance().isSameThread() || actual != execution || scope || calls != 0
                || !missingAsset || connection <= 0 || world <= 0 || resource <= 0) { failed = true; return false; }
        calls++; scope = true; payload = event;
        connectionGeneration = connection; worldGeneration = world; resourceGeneration = resource;
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void sound(PlaySoundEvent event) {
        if (!selected() || !scope || !DEFAULT_SOUND.equals(event.getOriginalSound().getLocation())) return;
        if (event.getSound() == null || !DEFAULT_SOUND.equals(event.getSound().getLocation())) { failed = true; return; }
        soundEvents++;
    }

    /** Never replaces an escaping original primary with an observation failure. */
    public static void finished(boolean observed, Object result, Throwable primary) {
        if (!observed) return;
        scope = false;
        if (primary != null) { failed = true; return; }
        try {
            boolean assetDiagnostic = execution.hasDiagnosticForTesting(P8ClientDiagnosticCode.ASSET_UNAVAILABLE,
                    P11L1P8FallbackProbe.PROFILE, ResourceLocation.fromNamespaceAndPath("gramarye", "sound"));
            boolean profileDiagnostic = execution.hasDiagnosticForTesting(P8ClientDiagnosticCode.PROFILE_UNAVAILABLE,
                    P11L1P8FallbackProbe.PROFILE, null);
            if (failed || result != P8ClientPresentationHandoffResult.PRESENTED || calls != 1 || soundEvents != 1
                    || !assetDiagnostic || !profileDiagnostic || !payload.sourceSummary().targetEntityId().isPresent()) {
                failed = true; return;
            }
            var facts = new LinkedHashMap<String, Object>();
            facts.put("status", "ORIGINAL_CLIENT_MISSING_ASSET_DEFAULT_SOUND_FALLBACK");
            facts.put("runId", P11C4aEvidence.property("runId")); facts.put("case", "l1-p8-missing-asset"); facts.put("role", "b");
            facts.put("sequence", payload.sequence()); facts.put("catalogGeneration", payload.catalogGeneration());
            facts.put("connectionGeneration", connectionGeneration); facts.put("worldGeneration", worldGeneration);
            facts.put("resourceGeneration", resourceGeneration); facts.put("targetEntityId", payload.sourceSummary().targetEntityId().getAsInt());
            facts.put("actualMissingAsset", missingAsset); facts.put("assetUnavailableDiagnostic", assetDiagnostic);
            facts.put("profileUnavailableDiagnostic", profileDiagnostic); facts.put("originalDefaultSoundEvents", soundEvents);
            facts.put("presentCalls", calls); facts.put("originalResult", "PRESENTED");
            facts.put("newRegistryTypeMissingClaim", false); facts.put("pixelOrAudibleOutputClaim", false);
            completed = Map.copyOf(facts);
        } catch (RuntimeException | Error observationFailure) { failed = true; }
    }

    static void tick(Minecraft minecraft) throws IOException {
        if (!selected()) return;
        P11C4aEvidence.require(!failed, "P8_FALLBACK_CLIENT_OBSERVATION_FAILED");
        if (completed == null || written) return;
        P11C4aEvidence.require(minecraft.isSameThread() && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getConnection().getConnection().isConnected(), "P8_FALLBACK_CLIENT_CURRENT_PLAY");
        P11C4aEvidence.write(P11C4aEvidence.root().resolve("client-b"), "p8-fallback-client.json", completed);
        written = true;
    }
}
