package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.V0CatalogTestAuthority;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real stopped data fixtures only; these tests do not fabricate a player, P11 proof or catalog permit. */
final class V0BootstrapSeedFinalizationTest {
    private static final SkillOwnerId A = new SkillOwnerId(new UUID(0, 1901));
    @TempDir Path world;

    @Test
    void aRealRemainingPlayerCopyCannotBeReplacedByAnEmptyReceiptSet() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            assertTrue(cold.call(() -> catalog.ensureNormalBootstrap(authority, A)).ready());
            byte[] before = Files.readAllBytes(source().resolve(V0NormalBootstrap.SEED.value().toString())
                    .resolve("catalog.current"));
            assertTrue(catalog.stopBootstrap(authority));
            assertTrue(cold.call(() -> catalog.prepareBootstrapSeedRetirement(authority, List.of())).isEmpty());
            assertEquals(BootstrapCleanup.REFUSED,
                    cold.call(() -> catalog.cleanupNormalBootstrap(authority, V0NormalBootstrap.SEED, null)));
            assertArrayEquals(before, Files.readAllBytes(source().resolve(V0NormalBootstrap.SEED.value().toString())
                    .resolve("catalog.current")));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void anActualSeedOnlyRosterYieldsAnOpaqueSourceBoundOneUseAuthorizationButNoPermit() throws Exception {
        Files.createDirectories(source());
        var context = V0NormalBootstrap.context();
        try (var storage = ScopeDSource.open(source(), V0NormalBootstrap.SEED)) {
            var seed = new ScopeDCatalog(V0NormalBootstrap.SEED, storage);
            var reservation = seed.reserve();
            var prepared = seed.prepare(reservation, context.catalogContext(),
                    List.of(V0NormalBootstrap.seed(reservation.newSpell(1))));
            seed.publish(prepared, context, seed.verifyCompatibility(prepared, context).proof().orElseThrow());
        }
        var authority = V0CatalogTestAuthority.open(4);
        var wrong = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        var other = V0RuntimeCatalog.openNormal(authority, world.resolve("another/spell_catalog"));
        assertTrue(catalog.prepareBootstrapSeedRetirement(authority, List.of()).isEmpty());
        try (var cold = new Cold()) {
            var proof = cold.call(() -> catalog.prepareBootstrapSeedRetirement(authority, List.of())).orElseThrow();
            assertFalse(proof.authorizeSeed(wrong, catalog));
            assertFalse(proof.authorizeSeed(authority, other));
            assertTrue(proof.authorizeSeed(authority, catalog));
            assertFalse(proof.authorizeSeed(authority, catalog));
            // Authorization alone cannot produce the private root permit or remove the seed.
            assertEquals(BootstrapCleanup.REFUSED,
                    cold.call(() -> catalog.cleanupNormalBootstrap(authority, V0NormalBootstrap.SEED, null, proof)).result());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void nullOverCapacityAndNullElementReceiptInputsFailClosedWithoutCreatingASeed() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            assertTrue(cold.call(() -> catalog.prepareBootstrapSeedRetirement(authority, null)).isEmpty());
            assertTrue(cold.call(() -> catalog.prepareBootstrapSeedRetirement(authority,
                    Arrays.asList(new BootstrapCleanupReceipt[32]))).isEmpty());
            assertFalse(Files.exists(source()));
            assertTrue(cold.call(() -> catalog.ensureNormalBootstrap(authority, A)).ready());
            assertTrue(cold.call(() -> catalog.prepareBootstrapSeedRetirement(authority,
                    Arrays.asList(new BootstrapCleanupReceipt[1]))).isEmpty());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    private Path source() { return world.resolve("gramarye/spell_catalog"); }

    private static final class Cold implements AutoCloseable {
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private <T> T call(Callable<T> task) throws Exception { return worker.submit(task).get(5, TimeUnit.SECONDS); }
        @Override public void close() { worker.close(); }
    }
}
