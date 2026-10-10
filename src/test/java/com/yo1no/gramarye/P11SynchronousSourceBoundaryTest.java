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
        // The earlier no-predecessor branch also invokes the factory. This contract concerns
        // only retained canonical input, whose constructor must follow its own source fence.
        var managed = boundary.substring(boundary.indexOf("var selection = new LoginSelection(source, previous);"),
                boundary.indexOf("private static void closeSelection("));
        assertManagedLoginOrder(managed);
        assertThrows(AssertionError.class, () -> assertManagedLoginOrder(managed.replace(
                "source.constructorStarted(selection.independent);",
                "var actor = original.call(profile, information);\nsource.constructorStarted(selection.independent);")));
        var validator = source.substring(source.indexOf("void flushIndependentBeforeLogin"),
                source.indexOf("void finishLoginIndependent"));
        assertTrue(validator.contains("canonicalInputComplete(body)"));
        assertTrue(!validator.contains("physicalClean(") && !validator.contains(".save()"));
        assertTrue(source.contains("actor.getStats() != account.current.stats"));
        assertTrue(source.contains("actor.getAdvancements() != account.current.advancements"));
    }

    private static void assertManagedLoginOrder(String managed) {
        int flush = managed.indexOf("source.flushIndependentBeforeLogin(previous)");
        int seal = managed.indexOf("selection.memory = source.seal(previous");
        int fence = managed.indexOf("source.constructorStarted(selection.independent)");
        int constructor = managed.indexOf("var actor = original.call(profile, information)");
        assertTrue(flush >= 0 && flush < seal && seal < fence && fence < constructor);
    }

    @Test
    void synchronousFallbackStartsPlayerWriterWithoutCrossCategoryDiskPrerequisite() throws IOException {
        // A dirty statistics/PA file is independent durability duty, not missing player material.
        // Actual native dirty-JSON fallback/readback remains an excluded-harness assertion.
        var source = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        assertSynchronousAdmission(source);
        assertThrows(AssertionError.class, () -> assertSynchronousAdmission(source.replace(
                "if (body == null || !canSerialize(body)) {\n            retainSynchronousDuty(body);",
                "if (body == null || !canSerialize(body) || !physicalClean(body, "
                        + "P11ReceiptLedger.WriterKind.STATISTICS)) {\n            retainSynchronousDuty(body);")));
        assertThrows(AssertionError.class, () -> assertSynchronousAdmission(source.replace(
                "if (body == null || !canSerialize(body)) {\n            retainSynchronousDuty(body);",
                "if (body == null || !canSerialize(body) || !physicalClean(body, "
                        + "P11ReceiptLedger.WriterKind.ADVANCEMENTS)) {\n            retainSynchronousDuty(body);")));
        assertThrows(AssertionError.class, () -> assertSynchronousAdmission(source.replace(
                "if (body == null || !canSerialize(body)) {\n            retainSynchronousDuty(body);",
                "if (body == null) {\n            retainSynchronousDuty(body);")));
        assertThrows(AssertionError.class, () -> assertSynchronousAdmission(source.replace(
                "return beginWriter(body, P11ReceiptLedger.WriterKind.PLAYER_DATA, true);",
                "return beginWriter(body, P11ReceiptLedger.WriterKind.PLAYER_DATA, false);")));
    }

    @Test
    void synchronousFallbackPreservesOneNativeSaveFreshReceiptAndReadback() throws IOException {
        var boundary = metadataBoundarySource();
        var source = Files.readString(ROOT.resolve(
                "src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java"));
        var mixin = Files.readString(ROOT.resolve("src/main/java/com/yo1no/gramarye/mixin/P11PlayerListMixin.java"));
        assertSynchronousReceiptChain(boundary, source, mixin);
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary.replace(
                "originalSave.call(request.previous.actor);",
                "originalSave.call(request.previous.actor); originalSave.call(request.previous.actor);"), source, mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary.replace(
                "request.duplicateWriter ||", "false ||"), source, mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary.replace(
                "((P11NativeWorldAccess.PrimaryReader) storage).p11$readPrimary(request);", ""), source, mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary.replace(
                "!request.material.equals(latest)", "false"), source, mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary, source.replace(
                "current.capturedSource() != receipt.source()", "false"), mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary, source.replace(
                "current.materialVersion() != receipt.materialVersion()", "false"), mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary, source.replace(
                "attempt.observation(step) != P11ReceiptLedger.Observation.SUCCEEDED", "false"), mixin));
        assertThrows(AssertionError.class, () -> assertSynchronousReceiptChain(boundary, source, mixin.replace(
                "save((ServerPlayer) args[0]);", "playerIo.save((ServerPlayer) args[0]);")));
    }

    private static void assertSynchronousAdmission(String source) {
        assertEquals("if(body==null||!canSerialize(body)){retainSynchronousDuty(body);returnnull;}"
                        + "returnbeginWriter(body,P11ReceiptLedger.WriterKind.PLAYER_DATA,true);",
                compactMethodBody(source, "P11ReceiptLedger.PhysicalWriterReceiptbeginSynchronousPlayerWriter("));
    }

    private static void assertSynchronousReceiptChain(String boundary, String source, String mixin) {
        var prepare = compactMethodBody(boundary, "publicstaticvoidpreparePrimary(");
        assertEquals(1, occurrences(prepare, "originalSave.call(request.previous.actor);"));
        assertTrue(prepare.contains("||!request.owner.canSerialize(request.previous)){throwunavailable();}"));
        assertTrue(prepare.contains("request.stage=PrimaryStage.SAVING;originalSave.call(request.previous.actor);"
                + "if(request.duplicateWriter||!request.owner.completedPlayerWrite(request.previous,request.receipt))"
                + "{throwunavailable();}request.stage=PrimaryStage.READ_REQUESTED;"
                + "((P11NativeWorldAccess.PrimaryReader)storage).p11$readPrimary(request);"));
        assertEquals(3, occurrences(prepare,
                "!request.owner.completedPlayerWrite(request.previous,request.receipt)"));
        assertTrue(prepare.contains("if(request.stage!=PrimaryStage.READ||request.material==null"));
        assertTrue(prepare.contains("varlatest=selectedMaterial(request.previous);NbtUtils.addCurrentDataVersion(latest);"
                + "if(!request.material.equals(latest)||!request.owner.completedPlayerWrite(request.previous,request.receipt))"
                + "{throwunavailable();}request.witness=request.owner.captureSelection(request.previous);"
                + "request.stage=PrimaryStage.VERIFIED;"));
        assertEquals("P11NativeStorageBoundary.preparePrimary((PlayerList)(Object)this,playerIo,request,"
                        + "args->{save((ServerPlayer)args[0]);returnnull;});",
                compactMethodBody(mixin, "publicvoidp11$preparePrimary("));
        var reader = compactMethodBody(boundary, "publicstaticvoidreadPrimary(");
        assertTrue(reader.contains("||!request.owner.completedPlayerWrite(request.previous,request.receipt))"
                + "{throwunavailable();}request.stage=PrimaryStage.READING;"
                + "varresult=originalRead.call(request.previous.actor,\".dat\");"));
        assertTrue(reader.contains("if(!request.readEntered||request.readState!=ReadState.READ||result.isEmpty())"
                + "{throwunavailable();}"));
        var receipt = compactMethodBody(source, "booleancompletedPlayerWrite(Bodybody,"
                + "P11ReceiptLedger.PhysicalWriterReceiptreceipt,SelectionWitnesswitness)");
        assertTrue(receipt.contains("if(receipt==null||receipt.kind()!=P11ReceiptLedger.WriterKind.PLAYER_DATA"
                + "||!selectedInputCurrent(body,receipt.source(),witness)){returnfalse;}"));
        assertTrue(receipt.contains("if(current==null||attempt==null||current.dirty()"
                + "||current.materialVersion()!=receipt.materialVersion()"
                + "||current.capturedSource()!=receipt.source()"
                + "||current.terminal()!=P11ReceiptLedger.Terminal.COMPLETED"
                + "||attempt.terminal()!=P11ReceiptLedger.Terminal.COMPLETED){returnfalse;}"));
        assertTrue(receipt.contains("P11ReceiptLedger.PhysicalStep.ENCODE,P11ReceiptLedger.PhysicalStep.WRITE,"
                + "P11ReceiptLedger.PhysicalStep.CLOSE,P11ReceiptLedger.PhysicalStep.REPLACE"));
        assertTrue(receipt.contains("if(attempt.observation(step)!=P11ReceiptLedger.Observation.SUCCEEDED){returnfalse;}"));
    }

    private static int occurrences(String source, String text) {
        return source.split(java.util.regex.Pattern.quote(text), -1).length - 1;
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
        assertThrows(AssertionError.class, () -> assertMetadataCompletionContracts(boundary,
                recovery.replace(" || generation != sessionGeneration", "")));
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
                        + "if(metadataSessionCurrent(lease)){lease.continuation.observeInitialSync(lease,epoch,generation,stage);}}"
                        + "catch(RuntimeException|Errorsecondary){if(observerFailures!=Long.MAX_VALUE){observerFailures++;}}",
                compactMethodBody(boundary, "publicstaticvoidmetadataInitialSync("));
        assertTrue(compactMethodBody(boundary, "publicstaticMetadataManaObservationbeginMetadataManaObservation(")
                .contains("if(!metadataCurrent(lease)||!lease.continuation.matchesSession(lease,epoch,generation)){returnnull;}"));
        assertEquals("returnresuming&&dependency==candidate&&player==actor"
                        + "&&P11NativeStorageBoundary.metadataCurrent(lease);",
                compactMethodBody(recovery, "publicbooleanresumeAuthorized("));
        assertEquals("returnport==loginPort&&actor==player&&!stages.blocked"
                        + "&&P11NativeStorageBoundary.metadataCurrent(lease);",
                compactMethodBody(recovery, "publicbooleanloginActor("));
        assertTrue(compactMethodBody(recovery, "publicbooleanresume(")
                .contains("if(candidate==null||candidate!=lease||resuming||!stages.resumable()"
                        + "||!P11NativeStorageBoundary.metadataCurrent(candidate)){returnfalse;}"));
        assertEquals("if(candidate==null||candidate!=lease||epoch!=sessionEpoch||generation!=sessionGeneration"
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
