package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillSavedDataCacheIdentityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void setThenGetReturnsExactAdapterWithoutInvokingEitherFactoryBranch() {
        var storage = new DimensionDataStorage(temporaryDirectory.toFile(), null, null);
        var adapter = GramaryeSkillSavedData.ready(
                SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent());
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

        storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, adapter);
        var cached = storage.get(
                throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);

        assertSame(adapter, cached);
        assertEquals(0, constructorCalls.get());
        assertEquals(0, deserializerCalls.get());
    }

    @Test
    void quarantinedPrimaryIsNotOverwrittenByStorageSaveEvenWhenValidDatOldExists()
            throws Exception {
        var primary = temporaryDirectory.resolve(
                SkillDefinitionStoreService.SAVED_DATA_NAME + ".dat");
        var old = temporaryDirectory.resolve(
                SkillDefinitionStoreService.SAVED_DATA_NAME + ".dat_old");
        var invalidPrimary = new byte[] {0x1f, (byte) 0x8b, 8};
        Files.write(primary, invalidPrimary);
        Files.write(old, canonicalGzip());
        var failed = assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Failure.class,
                SkillSavedDataPrimaryIngress.load(primary, Optional.empty()));
        var adapter = GramaryeSkillSavedData.quarantined(failed.failure());
        var storage = new DimensionDataStorage(temporaryDirectory.toFile(), null, null);

        storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, adapter);
        storage.save();

        assertArrayEquals(invalidPrimary, Files.readAllBytes(primary));
        assertFalse(adapter.isDirty());
    }

    @Test
    void platformWriterUsesTheStablePrimaryPathAndRoundTripsThroughStrictIngress()
            throws Exception {
        var primary = temporaryDirectory.resolve(
                SkillDefinitionStoreService.SAVED_DATA_NAME + ".dat");
        var storage = new DimensionDataStorage(temporaryDirectory.toFile(), null, null);
        var adapter = GramaryeSkillSavedData.ready(
                SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent());
        storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, adapter);
        adapter.setDirty();

        storage.save();
        IOUtilities.waitUntilIOWorkerComplete();

        assertTrue(Files.isRegularFile(primary));
        var reloaded = SkillSavedDataPrimaryIngress.load(primary, Optional.empty());
        assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Ready.class,
                reloaded,
                reloaded::toString);
        assertFalse(adapter.isDirty());
    }

    private static byte[] canonicalGzip() {
        try {
            var output = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(output)) {
                gzip.write(SkillSavedDataTestSupport.canonicalWholeRoot(
                        SkillSavedDataTestSupport.canonicalEmptyStoreBlob(), new byte[0]));
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
