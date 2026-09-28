package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Direct opaque-boundary negatives only; actual source/constructor/load proof is native-harness work. */
final class P11SynchronousSourceBoundaryTest {
    private static final Path ROOT = projectRoot();

    @Test
    void noCallerCanStartAnOriginalSaveWithoutTheRootOwnedCallLocalRequest() {
        var calls = new AtomicInteger();
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11NativeStorageBoundary.preparePrimary(null, null, null, arguments -> {
                    calls.incrementAndGet();
                    return null;
                }));
        assertEquals(0, calls.get());
    }

    @Test
    void privatePrimaryReaderCannotBeUsedAsAPublicRawNbtLocator() {
        var calls = new AtomicInteger();
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11NativeStorageBoundary.readPrimary(null, null, arguments -> {
                    calls.incrementAndGet();
                    return Optional.empty();
                }));
        assertEquals(0, calls.get());
    }

    @Test
    void preparedLoadRequiresTheExactRootOwnedPlacementScope() {
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11NativeStorageBoundary.loadPreparedPrimary(null, null, null));
    }

    @Test
    void managedNoncanonicalLifecycleActorCannotReachOriginalMutation() {
        var originals = new AtomicInteger();
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class, () -> {
            P11NativeStorageBoundary.requireManagedLifecycleAccess(true, false);
            originals.incrementAndGet();
        });
        assertEquals(0, originals.get());
    }

    @Test
    void trulyUnmanagedLifecycleStillUsesNativePath() {
        assertDoesNotThrow(() -> P11NativeStorageBoundary.requireManagedLifecycleAccess(false, false));
    }

    @Test
    void exactManagedLifecycleOwnerOrCopyScopeRemainsEligibleForItsFurtherChecks() {
        assertDoesNotThrow(() -> P11NativeStorageBoundary.requireManagedLifecycleAccess(true, true));
    }

    @Test
    void newSourceProducerDeclaresItsImmediateDirtyResponsibility() throws IOException {
        // Wiring assertion, not native admission proof. Resources' behavioral watermark tests
        // separately cover the true/false distinction; this producer must choose true.
        var source = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        assertTrue(source.contains("resources.tryAcquireRoot(resource, P11ControlBudgets.Root.WRITE, true)"));
    }

    @Test
    void loginWiringFlushesCanonicalJsonBeforeSealingAndFencesBeforeConstructor() throws IOException {
        // Static producer-order assertion only; the authenticated native harness exercises it.
        var boundary = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
        int flush = boundary.indexOf("source.flushIndependentBeforeLogin(previous)");
        int seal = boundary.indexOf("selection.memory = source.seal(previous");
        int fence = boundary.indexOf("source.constructorStarted(selection.independent)");
        int constructor = boundary.indexOf("var actor = original.call(profile, information)");
        assertTrue(flush >= 0 && flush < seal && seal < fence && fence < constructor);
    }

    @Test
    void MissingRespawnConstructorReturnRetainsPartialResponsibility() throws IOException {
        // The null successor must not silently leave predecessor A eligible for another save.
        var boundary = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
        var source = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        assertTrue(boundary.contains("source.constructorEscaped(body, null)"));
        assertTrue(source.contains("account.constructorFailed = true"));
        assertTrue(source.contains("previous.fault = Fault.PARTIAL"));
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle"))) {
            current = current.getParent();
        }
        if (current == null) { throw new IllegalStateException("project root not found"); }
        return current;
    }
}
