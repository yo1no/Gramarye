package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
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
        // Retained identity validation replaces the old successful-JSON-write prerequisite.
        // Method ID is retained; these are source assertions, not native behavior evidence.
        var boundary = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
        var source = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        int flush = boundary.indexOf("source.flushIndependentBeforeLogin(previous)");
        int seal = boundary.indexOf("selection.memory = source.seal(previous");
        int fence = boundary.indexOf("source.constructorStarted(selection.independent)");
        int constructor = boundary.indexOf("var actor = original.call(profile, information)");
        assertTrue(flush >= 0 && flush < seal && seal < fence && fence < constructor);
        var validator = source.substring(source.indexOf("void flushIndependentBeforeLogin"),
                source.indexOf("void finishLoginIndependent"));
        assertTrue(validator.contains("canonicalInputComplete(body)"));
        assertTrue(!validator.contains("physicalClean(") && !validator.contains(".save()"));
        assertTrue(source.contains("actor.getStats() != account.current.stats"));
        assertTrue(source.contains("actor.getAdvancements() != account.current.advancements"));
    }

    @Test
    void MissingRespawnConstructorReturnRetainsPartialResponsibility() throws IOException {
        // The null successor must not silently leave predecessor A eligible for another save.
        var boundary = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
        var source = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        assertTrue(boundary.contains("source.constructorEscaped(body, scope.associatedActor)"));
        assertTrue(boundary.contains("if (!withdrawAssociation(source, body, scope.association,"));
        assertTrue(source.contains("account.constructorFailed = true"));
        assertTrue(source.contains("previous.fault = Fault.PARTIAL"));
    }

    @Test
    void metadataCompletionObservationDoesNotGrantStaleSnapshotResume() throws Exception {
        var method = P11NativeStorageBoundary.class.getDeclaredMethod("metadataSessionCurrent",
                P11NativeStorageBoundary.MetadataLease.class);
        assertTrue(Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()));
        assertEquals(boolean.class, method.getReturnType());
        assertEquals(false, P11NativeStorageBoundary.metadataSessionCurrent(null));
        assertEquals(false, P11NativeStorageBoundary.metadataCurrent(null));
        assertMetadataCompletionContracts(metadataBoundarySource(), metadataRecoverySource());
    }

    @Test
    void metadataCompletionContractsRejectVersionRefreshWrongTransportAndBroadenedResume() throws IOException {
        var boundary = metadataBoundarySource();
        var recovery = metadataRecoverySource();
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(
                boundary.replace("lease.owner.metadataCurrent(lease.body, lease.version, lease.witness)", "true"), recovery));
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(
                boundary.replace("getConnection() == lease.connection", "getConnection() != lease.connection"), recovery));
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(
                boundary.replace("if (metadataSessionCurrent(lease))", "if (metadataCurrent(lease))"), recovery));
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(boundary,
                recovery.replace("P11NativeStorageBoundary.metadataCurrent(lease)",
                        "P11NativeStorageBoundary.metadataSessionCurrent(lease)")));
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(boundary,
                recovery.replace("!P11NativeStorageBoundary.metadataSessionCurrent(candidate)",
                        "!P11NativeStorageBoundary.metadataCurrent(candidate)")));
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(boundary,
                recovery.replace("stages.initialSync(stage);", "refresh(); stages.initialSync(stage);")));
    }

    /** Wiring contract only; authenticated native stage/H release evidence belongs to the harness. */
    private static void assertMetadataCompletionContracts(String boundary, String recovery) {
        assertEquals("returnmetadataSessionCurrent(lease)"
                        + "&&lease.owner.metadataCurrent(lease.body,lease.version,lease.witness);",
                compactMethodBody(boundary, "publicstaticbooleanmetadataCurrent("));
        assertEquals("returnlease!=null&&!lease.closed&&lease.body.account.metadata==lease"
                        + "&&lease.body.source.epoch()==lease.epoch&&lease.owner.canSerialize(lease.body)"
                        + "&&nativeDeliveryEligible(lease.body.actor)"
                        + "&&lease.body.actor.connection.getConnection()==lease.connection;",
                compactMethodBody(boundary, "publicstaticbooleanmetadataSessionCurrent("));
        assertEquals("returnmetadataCurrent(lease)&&lease.continuation.resume(lease);",
                compactMethodBody(boundary, "privatestaticbooleanresumeMetadata("));
        assertEquals("try{varsource=nativeSourceOwner(actor);varbody=source==null?null:source.body(actor);"
                        + "varlease=body==null?null:body.account.metadata;"
                        + "if(metadataSessionCurrent(lease)){lease.continuation.observeInitialSync(lease,epoch,stage);}}"
                        + "catch(RuntimeException|Errorsecondary){if(observerFailures!=Long.MAX_VALUE){observerFailures++;}}",
                compactMethodBody(boundary, "publicstaticvoidmetadataInitialSync("));
        assertTrue(compactMethodBody(boundary, "publicstaticMetadataManaObservationbeginMetadataManaObservation(")
                .contains("if(!metadataCurrent(lease)||!lease.continuation.matchesSession(lease,epoch)){returnnull;}"));
        assertEquals("returnresuming&&dependency==candidate&&player==actor"
                        + "&&P11NativeStorageBoundary.metadataCurrent(lease);",
                compactMethodBody(recovery, "publicbooleanresumeAuthorized("));
        assertEquals("returnport==loginPort&&actor==player&&!stages.blocked"
                        + "&&P11NativeStorageBoundary.metadataCurrent(lease);",
                compactMethodBody(recovery, "publicbooleanloginActor("));
        assertTrue(compactMethodBody(recovery, "publicbooleanresume(")
                .contains("if(candidate==null||candidate!=lease||resuming||!stages.resumable()"
                        + "||!P11NativeStorageBoundary.metadataCurrent(candidate)){returnfalse;}"));
        assertEquals("if(candidate==null||candidate!=lease||epoch!=sessionEpoch"
                        + "||stages.session!=MetadataStage.DONE||stage==null){return;}"
                        + "if(!P11NativeStorageBoundary.metadataSessionCurrent(candidate)){stages.blocked=true;return;}"
                        + "stages.initialSync(stage);if(stages.complete()&&!stages.blocked){release();}",
                compactMethodBody(recovery, "publicvoidobserveInitialSync("));
    }

    private static String metadataBoundarySource() throws IOException {
        return Files.readString(ROOT.resolve("src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java"));
    }

    private static String metadataRecoverySource() throws IOException {
        return Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java"));
    }

    private static String compactMethodBody(String source, String signature) {
        var compact = source.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "").replaceAll("\\s+", "");
        int method = compact.indexOf(signature);
        assertTrue(method >= 0, signature);
        int open = compact.indexOf('{', method);
        int depth = 1;
        for (int index = open + 1; index < compact.length(); index++) {
            if (compact.charAt(index) == '{') { depth++; }
            else if (compact.charAt(index) == '}' && --depth == 0) {
                return compact.substring(open + 1, index);
            }
        }
        throw new AssertionError("unclosed method " + signature);
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
