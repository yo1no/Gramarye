package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.Gramarye;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal GameTest coverage for the production P4-B2-A startup/cache path. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillSavedDataLifecycleGameTests {
    private SkillSavedDataLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void startupInstalledExactReadyAdapterInOverworldCache(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(), "GameTest must run on the server thread");
        helper.assertTrue(
                SkillSavedDataPrimaryIngress.resolvePrimaryPath(server).equals(
                        server.getWorldPath(LevelResource.ROOT)
                                .resolve("data")
                                .resolve("gramarye_skill_definitions.dat")),
                "primary resolver must match the platform Overworld data path");

        var constructorCalls = new AtomicInteger();
        var deserializerCalls = new AtomicInteger();
        SavedData.Factory<SavedData> throwingFactory = new SavedData.Factory<>(
                () -> {
                    constructorCalls.incrementAndGet();
                    throw new AssertionError("cache-hit constructor must not run");
                },
                (tag, provider) -> {
                    deserializerCalls.incrementAndGet();
                    throw new AssertionError("cache-hit deserializer must not run");
                });
        var storage = server.overworld().getDataStorage();
        var first = storage.get(
                throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);
        var second = storage.get(
                throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);

        helper.assertTrue(first instanceof GramaryeSkillSavedData,
                "ServerStarting must install the Gramarye adapter before GameTest execution");
        helper.assertTrue(first == second,
                "Overworld cache must return the exact installed adapter identity");
        helper.assertTrue(constructorCalls.get() == 0 && deserializerCalls.get() == 0,
                "cache-hit verification must not invoke either SavedData factory branch");
        var adapter = (GramaryeSkillSavedData) first;
        helper.assertTrue(adapter.state() instanceof SkillSavedDataState.Ready,
                "fresh normal GameTest world must install Ready state");
        helper.assertTrue(!adapter.isDirty(),
                "absent primary must install a non-dirty empty Ready state");
        var missing = adapter.latestReference(
                new com.yo1no.gramarye.magic.api.id.SkillId(
                        new java.util.UUID(0L, 0xB2A)));
        helper.assertTrue(
                missing instanceof SkillSubsystemResult.Available<?> available
                        && available.value().equals(Optional.empty()),
                "controlled missing lookup must remain available rather than unavailable");

        var isolated = new SkillDefinitionStoreService();
        SkillSubsystemLifecycleException beforeInstall = null;
        try {
            isolated.latestReference(
                    server,
                    new com.yo1no.gramarye.magic.api.id.SkillId(
                            new java.util.UUID(0L, 0xB2A + 2L)));
        } catch (SkillSubsystemLifecycleException exception) {
            beforeInstall = exception;
        }
        helper.assertTrue(
                beforeInstall != null
                        && beforeInstall.code()
                                == SkillSubsystemLifecycleException.Code.BOOTSTRAP_NOT_INSTALLED,
                "controlled access before install must fail with the lifecycle code");

        var wrongThreadFailure = new AtomicReference<Throwable>();
        var wrongThread = new Thread(() -> {
            try {
                isolated.latestReference(
                        server,
                        new com.yo1no.gramarye.magic.api.id.SkillId(
                                new java.util.UUID(0L, 0xB2A + 3L)));
            } catch (Throwable failure) {
                wrongThreadFailure.set(failure);
            }
        }, "gramarye-p4-b2-a-wrong-thread-gate");
        wrongThread.start();
        try {
            wrongThread.join(5_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("wrong-thread gate was interrupted", exception);
        }
        helper.assertTrue(!wrongThread.isAlive(), "wrong-thread gate must terminate promptly");
        helper.assertTrue(
                wrongThreadFailure.get() instanceof SkillSubsystemLifecycleException exception
                        && exception.code()
                                == SkillSubsystemLifecycleException.Code.WRONG_THREAD,
                "controlled wrong-thread access must fail before marker/cache inspection");

        var quarantined = GramaryeSkillSavedData.quarantined(
                new SkillSavedDataPrimaryFailure.MalformedGzip(
                        SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID));
        try {
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, quarantined);
            var fixtureCacheResult = storage.get(
                    throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);
            helper.assertTrue(fixtureCacheResult == quarantined,
                    "controlled quarantine fixture must retain exact cache identity");
            helper.assertTrue(
                    quarantined.state() instanceof SkillSavedDataState.Quarantined,
                    "controlled quarantine fixture must retain only Quarantined state");
            helper.assertTrue(!quarantined.isDirty(),
                    "Quarantined fixture must remain non-dirty");
            var unavailable = quarantined.latestReference(
                    new com.yo1no.gramarye.magic.api.id.SkillId(
                            new java.util.UUID(0L, 0xB2A + 1L)));
            helper.assertTrue(
                    unavailable instanceof SkillSubsystemResult.Unavailable<?> result
                            && result.reason().state()
                                    == SkillSubsystemUnavailableReason.State.QUARANTINED,
                    "controlled reads must distinguish Quarantined from missing");
        } finally {
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, adapter);
        }
        helper.assertTrue(
                storage.get(throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME)
                        == adapter,
                "controlled quarantine fixture must restore the startup adapter");
        helper.succeed();
    }
}
