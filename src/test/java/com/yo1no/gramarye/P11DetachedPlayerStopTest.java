package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Stop wiring/opaque authority plus isolated receipt models; not native IO or authentication proof. */
final class P11DetachedPlayerStopTest {
    @Test void absentStopAndOpaqueRequestCannotInvokeAnOriginalSave() {
        var calls = new AtomicInteger();
        long before = P11NativeStorageBoundary.observerFailureCount();
        assertDoesNotThrow(() -> P11NativeStorageBoundary.flushDetachedPlayersAtStop(null, null));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11NativeStorageBoundary.saveDetachedAtStop(null, null, null, arguments -> {
                    calls.incrementAndGet();
                    throw new AssertionError("unreachable original");
                }));
        assertEquals(0, calls.get());
        assertEquals(before, P11NativeStorageBoundary.observerFailureCount());
    }

    @Test void bridgeExposesNoActorSaveOrRequestMutationCapability() throws Exception {
        var request = P11NativeStorageBoundary.DetachedStopSaveRequest.class;
        assertTrue(Modifier.isFinal(request.getModifiers()));
        assertEquals(1, request.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(request.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(0, request.getDeclaredMethods().length);
        for (var field : request.getDeclaredFields()) { assertTrue(Modifier.isPrivate(field.getModifiers())); }
        var access = P11NativeWorldAccess.PlayerStorage.class.getDeclaredMethod("p11$saveDetachedAtStop", request);
        assertEquals(void.class, access.getReturnType());
        var scope = P11NativeStorageBoundary.class.getDeclaredField("DETACHED_STOP_SAVE");
        assertEquals(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL, scope.getModifiers());
        assertEquals(ThreadLocal.class, scope.getType());
    }

    @Test void exactSaveAllNormalReturnPrecedesOriginalRemoveAllAndWorldSave() throws Exception {
        String mixin = read("mixin/P11MinecraftServerMixin.java");
        assertEarlyAnchor(mixin);
        assertThrows(AssertionError.class, () -> assertEarlyAnchor(mixin.replace("shift = At.Shift.AFTER", "shift = At.Shift.BEFORE")));
        assertThrows(AssertionError.class, () -> assertEarlyAnchor(mixin.replace("PlayerList;saveAll()V", "PlayerList;removeAll()V")));
        assertThrows(AssertionError.class, () -> assertEarlyAnchor(mixin.replace("require = 1, expect = 1, allow = 1", "require = 0")));
        String bridge = body(read("P11NativeStorageBoundary.java"), "public static void flushDetachedPlayersAtStop(");
        requireAll(bridge, "STOP_SERVER.get() != server", "root.writerOwner(storage, null) == source",
                "source.flushDetachedPlayersAtStop()", "catch (RuntimeException | Error secondary)");
        for (String guard : new String[] {"STOP_SERVER.get() != server", "root.writerOwner(storage, null) == source"}) {
            assertThrows(AssertionError.class, () -> requireAll(bridge.replace(guard, "false"), guard));
        }
    }

    @Test void oneVirtualSaveUsesOnlyItsPrivateCurrentRequestAndNeverALoadOrDirectIo() throws Exception {
        String boundary = read("P11NativeStorageBoundary.java");
        String bridge = body(boundary, "public static void saveDetachedAtStop(");
        requireAll(bridge, "DETACHED_STOP_SAVE.get() != request", "request.entered || request.closed",
                "request.list != list", "!detachedStopRequestCurrent(request)",
                "root.playerStorageOwner(storage, request.body.actor) != request.owner",
                "p11$independentOwnersMatch(request.body.actor)", "request.entered = true;",
                "originalSave.call(request.body.actor);", "request.completed = true;");
        assertEquals(1, occurrences(bridge, "originalSave.call("));
        assertTrue(bridge.indexOf("request.entered = true;") < bridge.indexOf("originalSave.call("));
        assertTrue(bridge.indexOf("originalSave.call(") < bridge.indexOf("request.completed = true;"));
        assertFalse(bridge.contains("catch (") || bridge.contains("finally") || bridge.contains(".load(")
                || bridge.contains("finishSave(") || bridge.contains("releaseDirty("));
        String mixin = body(read("mixin/P11PlayerListMixin.java"), "public void p11$saveDetachedAtStop(");
        requireAll(mixin, "P11NativeStorageBoundary.saveDetachedAtStop((PlayerList) (Object) this, playerIo, request,",
                "save((ServerPlayer) args[0]);");
        assertFalse(mixin.contains("playerIo.save(") || mixin.contains("super.save("));
    }

    @Test void partialCandidateStaleSourceAndActiveNativeScopesNeverBecomeStopSources() throws Exception {
        String source = read("P11QualifiedSourceOwner.java");
        String current = body(source, "boolean detachedStopCurrent(");
        String[] guards = {"requireMain();", "return stopping &&", "body.source == version",
                "accounts.get(body.actor.getUUID()) == body.account", "canSerialize(body)",
                "!body.logoutActive", "body.actor.connection != null",
                "nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0",
                "nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0",
                "getPlayer(body.actor.getUUID()) == null", "noneMatch(actor -> actor == body.actor)"};
        requireAll(current, guards);
        for (String guard : guards) {
            assertThrows(AssertionError.class, () -> requireAll(current.replace(guard, "true"), guards));
        }
        requireAll(body(source, "boolean canSerialize("), "currentMaterial(body)",
                "body.account.constructing == null", "body.envelope != null");
        requireAll(body(source, "private boolean currentMaterial("), "body.complete", "body.fault == Fault.NONE",
                "body.account.current == body", "body.account.candidate == null");
        String flush = body(source, "void flushDetachedPlayersAtStop()");
        requireAll(flush, "if (!stopping || detachedPlayersStopFlushed) { return; }", "var body = account.current;",
                "P11ReceiptLedger.WriterKind.PLAYER_DATA)", "filter(P11ReceiptLedger.PhysicalFacts::dirty).isEmpty()");
        // Matching public STOP_SERVER/root/thread alone cannot create the formal native phase.
        String phase = "if (!stopping || detachedPlayersStopFlushed) { return; }";
        assertThrows(AssertionError.class, () -> requireAll(flush.replace("!stopping || ", ""), phase));
        requireAll(body(read("P11FoundationService.java"), "void stopping("),
                "sourceOwner(event.getServer())", "source.stopping();");
        assertFalse(body(read("P11NativeStorageBoundary.java"), "public static void stop(").contains(".stopping()"));
    }

    @Test void newAttemptReceiptDuplicateAndVersionAreRevalidatedAfterTheWholeNativeSave() throws Exception {
        String boundary = read("P11NativeStorageBoundary.java");
        String bridge = body(boundary, "public static void saveDetachedAtStop(");
        String[] proof = {"request.duplicateWriter", "!request.playerWriterEntered", "request.receipt == null",
                "request.receipt.source() != request.version", "!detachedStopRequestCurrent(request)",
                "!request.owner.completedPlayerWrite(request.body, request.receipt)"};
        requireAll(bridge.substring(bridge.indexOf("originalSave.call(")), proof);
        for (String guard : proof) {
            assertThrows(AssertionError.class, () -> requireAll(bridge.replace(guard, "false"), proof));
        }
        String writer = body(boundary, "public static void savePlayer(");
        requireAll(writer, "stop.owner == source && stop.body == body && stop.storage == storage",
                "if (stop.playerWriterEntered) { stop.duplicateWriter = true; return; }",
                "stop.playerWriterEntered = true;", "if (!detachedStopRequestCurrent(stop)) { return; }",
                "if (stopWriter) { stop.receipt = receipt; }");
        assertTrue(writer.indexOf("stop.playerWriterEntered = true;") < writer.indexOf("if (managed && previous != null)"));
        assertTrue(writer.indexOf("stop.receipt = receipt;") < writer.indexOf("if (managed && receipt == null)"));
        String current = body(boundary, "private static boolean detachedStopRequestCurrent(");
        requireAll(current, "DETACHED_STOP_SAVE.get() == request", "!request.closed",
                "STOP_SERVER.get() == request.server", "request.server.getPlayerList() == request.list",
                "root.writerOwner(request.list, request.body.actor) == request.owner",
                "request.owner.detachedStopCurrent(request.body, request.version)");
    }

    @Test void detachedCacheEnvelopeNeedsThisStopRequestAndItsExactLiveCacheReceipt() throws Exception {
        String boundary = read("P11NativeStorageBoundary.java");
        String cache = body(boundary, "private static boolean detachedStopCacheEnvelope(");
        String[] proof = {"request != null", "request.entered", "request.body == body", "detachedStopRequestCurrent(request)",
                "WRITE.get() == null", "cache != null", "cache.list == request.list", "cache.source == request.owner",
                "cache.body == body", "cache.receipt != null", "cache.receipt.source() == request.version",
                "request.owner.mayWrite(body, cache.receipt)"};
        requireAll(cache, proof);
        for (String guard : proof) {
            assertThrows(AssertionError.class, () -> requireAll(cache.replace(guard, "true"), proof));
        }
        String serializer = body(boundary, "public static CompoundTag serialize(");
        requireAll(serializer, "body.envelope != null && !body.logoutActive",
                "&& !detachedStopCacheEnvelope(body)", "body.envelope.apply(result);");
        assertTrue(serializer.indexOf("&& !detachedStopCacheEnvelope(body)") < serializer.indexOf("body.envelope.apply(result);"));
        assertFalse(cache.contains("PRIMARY_READ") || cache.contains("SELECTED_SERIALIZE.set"));
    }

    @Test void originalFailureUnwindsRequestWithoutReplacementAndOtherBodiesRemainInTheLoop() throws Exception {
        String boundary = read("P11NativeStorageBoundary.java");
        String request = body(boundary, "static void saveDetachedAtStop(P11QualifiedSourceOwner");
        requireAll(request, "DETACHED_STOP_SAVE.get() != null", "WRITE.get() != null", "CACHE.get() != null",
                "PRIMARY_READ.get() != null", "SERIALIZE.get() != null",
                "finally {\n            request.closed = true;\n            DETACHED_STOP_SAVE.remove();");
        assertFalse(request.contains("catch (") || request.contains("addSuppressed") || request.contains("PRIMARY_READ.set"));
        String flush = body(read("P11QualifiedSourceOwner.java"), "void flushDetachedPlayersAtStop()");
        assertTrue(flush.indexOf("for (var account") < flush.indexOf("try {"));
        requireAll(flush, "catch (RuntimeException | Error failure)", "account.fault = Fault.WRITE;",
                "account.current == body && account.candidate == null",
                "receipts.markDirty(body.source, P11ReceiptLedger.WriterKind.PLAYER_DATA);",
                "account.dirty = resources.markDirty(account.resource, now()).orElseThrow();",
                "catch (RuntimeException | Error secondary)");
        assertFalse(flush.contains("throw failure") || flush.contains("throw secondary") || flush.contains("addSuppressed")
                || flush.contains("releaseDirty") || flush.contains("finishSave"));
        String finish = body(read("P11QualifiedSourceOwner.java"), "void finishWriter(");
        requireAll(finish, "physicalClean(body, P11ReceiptLedger.WriterKind.PLAYER_DATA)",
                "physicalClean(body, P11ReceiptLedger.WriterKind.STATISTICS)",
                "physicalClean(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)",
                "physicalClean(body, P11ReceiptLedger.WriterKind.LEVEL_PLAYER)");
    }

    @Test void modelHostLevelSuccessDoesNotSubstituteForDirtyPlayerData() {
        var fixture = fixture(true);
        for (var kind : P11ReceiptLedger.WriterKind.values()) { fixture.ledger.markDirty(fixture.source, kind); }
        complete(fixture.ledger, fixture.ledger.beginSave(fixture.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER).orElseThrow());
        assertFalse(fixture.ledger.physicalFacts(fixture.source, P11ReceiptLedger.WriterKind.LEVEL_PLAYER).orElseThrow().dirty());
        assertTrue(fixture.ledger.physicalFacts(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        complete(fixture.ledger, fixture.ledger.beginSave(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow());
        assertFalse(fixture.ledger.physicalFacts(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        assertTrue(fixture.ledger.physicalFacts(fixture.source, P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow().dirty());
        assertTrue(fixture.ledger.physicalFacts(fixture.source, P11ReceiptLedger.WriterKind.ADVANCEMENTS).orElseThrow().dirty());
    }

    @Test void modelNativeTailMutationCannotBeClearedByThePreviousAttempt() {
        var fixture = fixture(true);
        var old = fixture.ledger.beginSave(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow();
        physical(fixture.ledger, old);
        var newer = fixture.ledger.advanceMutationVersion(fixture.source).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.STALE, fixture.ledger.finishSave(old, P11ReceiptLedger.Terminal.COMPLETED));
        assertTrue(fixture.ledger.physicalFacts(newer, P11ReceiptLedger.WriterKind.PLAYER_DATA).orElseThrow().dirty());
        assertNotSame(old.source(), newer);
        assertEquals(fixture.source.epoch(), newer.epoch());
        assertEquals(fixture.source.version() + 1, newer.version());
    }

    @Test void modelPartialBodyStillAllowsItsSeparatelyProvedCanonicalWriter() {
        var fixture = fixture(false);
        assertTrue(fixture.ledger.beginSave(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA).isEmpty());
        var material = fixture.ledger.beginIndependentMaterial(fixture.source, P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow();
        assertEquals(P11ReceiptLedger.Change.RECORDED,
                fixture.ledger.independentMaterial(material, P11ReceiptLedger.Observation.SUCCEEDED));
        complete(fixture.ledger, fixture.ledger.beginIndependentSave(material).orElseThrow());
        assertFalse(fixture.ledger.physicalFacts(fixture.source, P11ReceiptLedger.WriterKind.STATISTICS).orElseThrow().dirty());
        assertTrue(fixture.ledger.beginSave(fixture.source, P11ReceiptLedger.WriterKind.PLAYER_DATA).isEmpty());
    }

    private static void assertEarlyAnchor(String source) {
        int handler = source.indexOf("private void p11$detachedPlayersStop(");
        assertTrue(handler > 0);
        String annotation = source.substring(source.lastIndexOf("@Inject", handler), handler);
        requireAll(annotation, "method = \"stopServer()V\"", "target = \"Lnet/minecraft/server/players/PlayerList;saveAll()V\"",
                "shift = At.Shift.AFTER", "require = 1, expect = 1, allow = 1");
        requireAll(body(source, "private void p11$detachedPlayersStop("),
                "P11NativeStorageBoundary.flushDetachedPlayersAtStop((MinecraftServer) (Object) this, storageSource)");
    }

    private static void requireAll(String source, String... fragments) {
        for (String fragment : fragments) { assertTrue(source.contains(fragment), fragment); }
    }

    private static int occurrences(String source, String fragment) {
        return (source.length() - source.replace(fragment, "").length()) / fragment.length();
    }

    private static String body(String source, String signature) {
        int method = source.indexOf(signature);
        assertTrue(method >= 0, signature);
        int start = source.indexOf('{', method), depth = 1, end = start + 1;
        while (depth != 0) {
            char value = source.charAt(end++);
            if (value == '{') { depth++; } else if (value == '}') { depth--; }
        }
        return source.substring(start + 1, end - 1);
    }

    private static String read(String relative) throws Exception {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null && !Files.isRegularFile(directory.resolve("build.gradle"))) { directory = directory.getParent(); }
        assertNotNull(directory);
        return Files.readString(directory.resolve("src/main/java/com/yo1no/gramarye").resolve(relative));
    }

    private static Fixture fixture(boolean qualified) {
        var identities = P11IdentityOwner.isolatedModel(1);
        var identity = identities.captureModelSource(identities.modelActor(new UUID(45, 31), 1)).orElseThrow();
        var ledger = new P11ReceiptLedger(identities);
        var source = ledger.firstSource(identity, P11ReceiptLedger.Disposition.LIVE).orElseThrow();
        if (qualified) {
            var material = ledger.beginMaterial(source).orElseThrow();
            for (var step : P11ReceiptLedger.MaterialStep.values()) {
                ledger.material(material, step, P11ReceiptLedger.Observation.SUCCEEDED);
            }
            assertEquals(P11ReceiptLedger.Change.RECORDED, ledger.finishMaterial(material, P11ReceiptLedger.Terminal.COMPLETED));
        }
        return new Fixture(ledger, source);
    }

    private static void physical(P11ReceiptLedger ledger, P11ReceiptLedger.PhysicalWriterReceipt receipt) {
        for (var step : P11ReceiptLedger.PhysicalStep.values()) {
            boolean required = switch (receipt.kind()) {
                case PLAYER_DATA, LEVEL_PLAYER -> step != P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT;
                case STATISTICS, ADVANCEMENTS -> step == P11ReceiptLedger.PhysicalStep.ENCODE
                        || step == P11ReceiptLedger.PhysicalStep.WRITE || step == P11ReceiptLedger.PhysicalStep.CLOSE;
                case CACHE -> step == P11ReceiptLedger.PhysicalStep.ENCODE || step == P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT;
            };
            if (required) { assertEquals(P11ReceiptLedger.Change.RECORDED, ledger.physical(receipt, step, P11ReceiptLedger.Observation.SUCCEEDED)); }
        }
    }

    private static void complete(P11ReceiptLedger ledger, P11ReceiptLedger.PhysicalWriterReceipt receipt) {
        physical(ledger, receipt);
        assertEquals(P11ReceiptLedger.Change.RECORDED, ledger.finishSave(receipt, P11ReceiptLedger.Terminal.COMPLETED));
    }

    private record Fixture(P11ReceiptLedger ledger, P11ReceiptLedger.Source source) { }
}
