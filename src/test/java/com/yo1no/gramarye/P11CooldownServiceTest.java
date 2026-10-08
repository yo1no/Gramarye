package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** Actual sole-owner settlement and source contracts, not native logout/save qualification. */
final class P11CooldownServiceTest {
    private static final Path ROOT = projectRoot();
    private static final UUID SKILL = new UUID(1, 2), ATTEMPT = new UUID(3, 4);
    private static P11CastCooldownData.Entry pending() {
        return P11CastCooldownData.Entry.pending(SKILL, 7, 120, 1000, ATTEMPT, 1101);
    }
    private record Receipt(UUID attemptId, P11CastCooldownService.ReleaseFact fact, long releasedAt)
            implements P11CastCooldownService.ReleaseReceipt { }
    private static Receipt receipt(P11CastCooldownService.ReleaseFact fact) {
        return new Receipt(ATTEMPT, fact, fact == P11CastCooldownService.ReleaseFact.ARM ? 1002 : -1);
    }

    @Test void knownArmAndNoReleasePrecedeClockAvailabilityWithoutGuessingExpiry() {
        var active = P11CastCooldownService.settleEntry(pending(), receipt(P11CastCooldownService.ReleaseFact.ARM), null, 1002);
        assertEquals(0, active.material().kind);
        assertEquals(1002, active.material().releasedAt);
        assertEquals(1122, active.material().expiresAt);
        assertTrue(active.receiptConsumed());
        var cleared = P11CastCooldownService.settleEntry(pending(), receipt(P11CastCooldownService.ReleaseFact.NO_RELEASE), null, 1002);
        assertNull(cleared.material()); assertTrue(cleared.receiptConsumed());
        var same = P11CastCooldownService.settleEntry(active.material(), null, null, 2000);
        assertSame(active.material(), same.material()); // Bad now cannot be replaced with trusted floor.
        assertThrows(IllegalArgumentException.class,
                () -> P11CastCooldownService.settleEntry(pending(), null, 999L, 1000));
    }

    @Test void orphanAndUnknownUseOnlyCapturedUpperBoundAndNotCandidateExpiry() {
        var orphan = P11CastCooldownService.settleEntry(pending(), null, 1220L, 1220);
        assertEquals(pending().attemptId, orphan.material().attemptId);
        assertEquals(1, orphan.material().kind);
        assertNull(P11CastCooldownService.settleEntry(pending(), null, 1221L, 1221).material());
        var candidate = new P11CastCooldownData.Entry(SKILL, 2, 7, 120, 1000, ATTEMPT, 1101,
                1002, 1122, P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, 0, null, 0, 0);
        assertSame(candidate, P11CastCooldownService.settleEntry(candidate, null, 1122L, 1122).material());
        assertSame(candidate, P11CastCooldownService.settleEntry(candidate, null, 1220L, 1220).material());
        assertNull(P11CastCooldownService.settleEntry(candidate, null, 1221L, 1221).material());
        var unknown = receipt(P11CastCooldownService.ReleaseFact.UNKNOWN);
        var at1122 = P11CastCooldownService.settleEntry(pending(), unknown, 1122L, 1122);
        assertEquals(2, at1122.material().kind); assertEquals(0, at1122.material().releaseKnowledge);
        assertFalse(at1122.receiptConsumed());
        var at1221 = P11CastCooldownService.settleEntry(pending(), unknown, 1221L, 1221);
        assertNull(at1221.material()); assertTrue(at1221.receiptConsumed());
    }

    @Test void livePendingIsNotIrreversiblyCancelledByTimeOrAnEmptyQueueGuess() {
        for (var fact : new P11CastCooldownService.ReleaseFact[] {
                P11CastCooldownService.ReleaseFact.UNPUBLISHED, P11CastCooldownService.ReleaseFact.PENDING}) {
            var entry = pending();
            var result = P11CastCooldownService.settleEntry(entry, receipt(fact), 2000L, 2000);
            assertSame(entry, result.material()); assertFalse(result.receiptConsumed());
        }
        var source = source("P11CastCooldownService.java");
        String settlement = method(source, "static Settlement settleEntry(");
        assertFalse(settlement.contains("queue") || settlement.contains("isClosed") || settlement.contains("isRemoved"));
    }

    @Test void confirmedArmRestoresTrueExpiryAndNoReleaseCannotDowngradeIt() {
        var known = pending().uncertain(P11CastCooldownData.Reason.SAVE_FAILURE, 1003, true, 1002);
        var restored = P11CastCooldownService.settleEntry(known, null, 1121L, 1121);
        assertEquals(0, restored.material().kind); assertEquals(1122, restored.material().expiresAt);
        assertNull(P11CastCooldownService.settleEntry(known, null, 1122L, 1122).material());
        assertThrows(IllegalStateException.class, () -> P11CastCooldownService.settleEntry(known,
                receipt(P11CastCooldownService.ReleaseFact.NO_RELEASE), null, 1003));
        assertThrows(IllegalStateException.class, () -> P11CastCooldownService.settleEntry(pending().active(1002),
                receipt(P11CastCooldownService.ReleaseFact.NO_RELEASE), null, 1003));
    }

    @Test void exactAttemptAndReleaseBoundsRemainMandatoryWithScalarReceipt() {
        assertThrows(IllegalArgumentException.class, () -> P11CastCooldownService.settleEntry(pending(),
                new Receipt(new UUID(3, 5), P11CastCooldownService.ReleaseFact.ARM, 1002), 1002L, 1002));
        for (long release : new long[] {-1, 999, 1102, Long.MAX_VALUE}) {
            assertThrows(IllegalStateException.class, () -> P11CastCooldownService.settleEntry(pending(),
                    new Receipt(ATTEMPT, P11CastCooldownService.ReleaseFact.ARM, release), null, 1101));
        }
        assertThrows(IllegalStateException.class, () -> P11CastCooldownService.settleEntry(pending(),
                new Receipt(ATTEMPT, P11CastCooldownService.ReleaseFact.ARM, 1003), null, 1002));
        var lastLegal = P11CastCooldownService.settleEntry(pending(),
                new Receipt(ATTEMPT, P11CastCooldownService.ReleaseFact.ARM, 1101), 1101L, 1101);
        assertEquals(1221, lastLegal.material().expiresAt);
    }

    @Test void immutableRawOwnershipAndBoundedUtfDoNotBorrowMutableInput() {
        var raw = new CompoundTag(); raw.putString("x", "a");
        var whole = P11CastCooldownData.raw(P11CastCooldownData.Reason.MALFORMED, raw);
        var local = P11CastCooldownData.Entry.raw(SKILL, P11CastCooldownData.Reason.MALFORMED, raw);
        raw.putString("x", "changed");
        assertEquals("a", P11CastCooldownCodec.write(whole).getCompound("raw").getString("x"));
        var encoded = P11CastCooldownCodec.write(P11CastCooldownData.routed(1000, Map.of(SKILL, local)));
        assertEquals("a", encoded.getList("entries", 10).getCompound(0).getCompound("raw").getString("x"));
        var enormousString = StringTag.valueOf("x".repeat(100_000));
        var bounded = P11CastCooldownNbtSize.measure(enormousString, 297, 8);
        assertFalse(bounded.fits()); assertEquals(298, bounded.observedAtLeast());
        assertThrows(IllegalArgumentException.class, () -> P11CastCooldownData.raw(
                P11CastCooldownData.Reason.MALFORMED, enormousString));
        // END cannot occur as a named value in native NBT; synthetic nonvalues are
        // invariant rejection, not persisted malformed data remapped to Ready.
        assertThrows(IllegalArgumentException.class, () -> P11CastCooldownCodec.read(EndTag.INSTANCE));
        var namedEnd = new CompoundTag(); namedEnd.put("invalid", EndTag.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> P11CastCooldownNbtSize.measure(namedEnd, 297, 8));
    }

    @Test void receiptOwnershipPrecedesPublicationAndConsumptionFollowsActualInstallation() {
        String service = source("P11CastCooldownService.java");
        String install = method(service, "boolean installPending(");
        assertTrue(ordered(install, "attempt.installed = true", "attempt.cell.attempts.put", "publish(source, body",
                "finally", "attempt.cell.data != attempt.replacement", "attempt.installed = false"));
        assertFalse(ordered(install.replace("attempt.cell.attempts.put", "missing.put"),
                "attempt.cell.attempts.put", "publish(source, body"));
        String settle = method(service, "private void reconcileAt(");
        assertTrue(ordered(settle, "consumed.add(exact)", "publish(source, body, cell, replacement)",
                "finally", "cell.data == replacement", "cell.attempts.remove"));
        assertFalse(ordered(settle.replace("cell.data == replacement", "true"),
                "publish(source, body, cell, replacement)", "cell.data == replacement", "cell.attempts.remove"));
        assertTrue(method(service, "private void publish(").contains("P11CastCooldownAttachments.existing(body.actor) == replacement"));
        assertFalse(service.contains("randomUUID") || service.contains("System.currentTimeMillis"));
        assertTrue(method(service, "ArmPreparation prepareArm(").contains("work != attempt.work"));
        assertTrue(method(service, "Admission prepareAdmission(").contains("!work.qualifies(actor)"));
    }

    @Test void requiredNativeMaterialCoversReadCopyWriteAndEverySelectionWitness() {
        String boundary = source("P11NativeStorageBoundary.java"), owner = source("P11QualifiedSourceOwner.java");
        assertTrue(method(boundary, "private static void loadBody(").contains("!scope.expectedCooldown || scope.cooldownRead"));
        assertTrue(method(boundary, "public static void copy(").contains("!cooldown || scope.cooldownRead"));
        String read = method(boundary, "static void cooldownReadCompleted(");
        assertTrue(read.contains("!scope.cooldownReadObserved") && read.contains("!copy.cooldownReadObserved"));
        String write = method(boundary, "public static CompoundTag writeAttachments(");
        assertTrue(write.contains("observed.cooldown.matches(scope.body.actor, result.get(COOLDOWNS.toString()))"));
        assertTrue(method(owner, "private boolean currentAttachmentSource(").contains("body.cooldown.current(body.actor)"));
        assertTrue(method(owner, "boolean metadataCurrent(").contains("witness.cooldown.same(body.cooldown)"));
        assertTrue(method(owner, "private boolean selectedInputCurrent(").contains("witness.cooldown.same(body.cooldown)"));
        String attachment = source("P11CastCooldownAttachments.java");
        assertTrue(attachment.contains(".serialize(new Serializer()).copyOnDeath().build()"));
        assertFalse(attachment.contains("copyHandler") || attachment.contains("freshEmptyReady"));
    }

    @Test void reconciliationPrecedesSourceVersionAndWriterReceiptCapture() {
        String boundary = source("P11NativeStorageBoundary.java"), owner = source("P11QualifiedSourceOwner.java");
        assertTrue(ordered(method(boundary, "public static ServerPlayer loginPlayer("),
                "source.reconcileCooldown(previous)", "selection.version = previous.source", "source.lineage(previous)"));
        assertTrue(ordered(method(boundary, "public static ServerPlayer respawn("),
                "source.reconcileCooldown(body)", "source.lineage(body)"));
        assertTrue(ordered(method(boundary, "static void saveDetachedAtStop("),
                "source.reconcileCooldown(body)", "new DetachedStopSaveRequest(source, body)"));
        assertTrue(ordered(method(owner, "private P11ReceiptLedger.PhysicalWriterReceipt beginWriter("),
                "reconcileCooldown(body)", "receipts.beginSave(body.source, kind)"));
        String writer = method(owner, "void finishWriter(");
        assertTrue(ordered(writer, "result == P11ReceiptLedger.Change.STALE", "return;", "receipt.source() == body.source",
                "cooldowns.writerFinished"));
    }

    @Test void exactCooldownPublicationMayRefreshOnlyTheExistingMetadataContinuation() {
        String body = method(source("P11NativeStorageBoundary.java"), "static void metadataCooldownPublished(");
        for (String guard : new String[] {"lease.owner != owner", "lease.body != body", "lease.version != before",
                "body.source.epoch() != before.epoch()", "body.source.version() != before.version() + 1",
                "!owner.metadataUnchangedExceptCooldown(body, lease.witness)"}) { assertTrue(body.contains(guard), guard); }
        assertTrue(body.contains("refreshMetadata(lease)"));
        assertFalse(body.contains("resume(") || body.contains("retainMetadata") || body.contains("metadataInitialSync"));
    }

    private static String source(String leaf) {
        try { return Files.readString(ROOT.resolve("src/main/java/com/yo1no/gramarye").resolve(leaf)); }
        catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) { throw new IllegalStateException("project root unavailable"); }
        return current;
    }
    private static String method(String source, String signature) {
        int start = source.indexOf(signature); assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start), depth = 1, end = open + 1;
        while (depth > 0 && end < source.length()) {
            char c = source.charAt(end++); if (c == '{') { depth++; } else if (c == '}') { depth--; }
        }
        assertEquals(0, depth); return source.substring(start, end);
    }
    private static boolean ordered(String source, String... values) {
        int offset = 0;
        for (String value : values) {
            int next = source.indexOf(value, offset); if (next < 0) { return false; } offset = next + value.length();
        }
        return true;
    }
}
