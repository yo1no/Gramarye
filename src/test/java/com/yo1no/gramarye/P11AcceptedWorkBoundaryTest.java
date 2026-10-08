package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Exact production wiring/retention checks, not native or authenticated L1 evidence. */
final class P11AcceptedWorkBoundaryTest {
    @Test void workReceiptIsOpaqueActorFreeAndOwnedByTheExistingSourceAccount() throws Exception {
        var type = P11QualifiedSourceOwner.WorkReservation.class;
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        for (var constructor : type.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
        assertEquals(Set.of("owner", "account", "playerId", "closed"),
                java.util.Arrays.stream(type.getDeclaredFields()).map(java.lang.reflect.Field::getName)
                        .collect(java.util.stream.Collectors.toSet()));
        for (var field : type.getDeclaredFields()) {
            assertFalse(net.minecraft.world.entity.Entity.class.isAssignableFrom(field.getType()));
            assertNotEquals(P11QualifiedSourceOwner.Body.class, field.getType());
            assertFalse(java.util.Collection.class.isAssignableFrom(field.getType()));
            assertFalse(java.util.Map.class.isAssignableFrom(field.getType()));
        }
        String source = read("P11QualifiedSourceOwner.java");
        String work = section(source, "static final class WorkReservation", "/** At most one pre-constructor");
        assertTrue(work.contains("owner.workRecipient(this, exactA)"));
        assertTrue(work.contains("owner.releaseWork(this)"));
        assertFalse(work.contains("new Body") || work.contains("getPlayerList()"));
    }

    @Test void admissionReservesAggregateWOnlyAfterQualifiedOnlineSourceAndEveryWorkBudgetCheck() throws Exception {
        String source = section(read("P11QualifiedSourceOwner.java"), "WorkReservation acquireWork(",
                "private Body workRecipient(");
        assertAdmission(source);
        for (String guard : new String[] {"stopping", "nativeContinuity(this) == null",
                "!canCopy(body)", "!canonicalInputComplete(body)", "actor.isFakePlayer()",
                "getPlayer(actor.getUUID()) != actor", "getPacketListener() != actor.connection",
                "nativeCounts[index] == Long.MAX_VALUE", "!resources.mayAdmitWork(account.resource)"}) {
            assertThrows(AssertionError.class, () -> assertAdmission(source.replace(guard, "REMOVED")), guard);
        }
        before(source, "new WorkReservation(this, account.resource, actor.getUUID())",
                "resources.tryAcquireRoot(account.resource, P11ControlBudgets.Root.WORK, true)");
        before(source, "resources.tryAcquireRoot(", "account.nativeCounts[index]++");
        assertTrue(source.contains("if (account.nativeCounts[index] == 0)"));
        assertFalse(source.contains("accounts.put(") || source.contains("new Body") || source.contains("retainRoot("));
    }

    @Test void retainedWorkFollowsOnlyItsIssuedAccountNotAnIdleUuidOrNewBodyMap() throws Exception {
        String source = section(read("P11QualifiedSourceOwner.java"), "private Body workRecipient(",
                "boolean retainNativeRoot(");
        for (String guard : new String[] {"work.owner != this", "work.closed", "stopping",
                "!owns(exactA.getServer())", "!work.playerId.equals(exactA.getUUID())",
                "nativeContinuity(this) == null", "account.resource != work.account",
                "account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0",
                "canCopy(recipient) && canonicalInputComplete(recipient)"}) {
            assertTrue(source.contains(guard), guard);
        }
        assertTrue(source.contains("work.closed = true;"));
        assertTrue(source.contains("releaseRoot(account, work.playerId, P11ControlBudgets.Root.WORK)"));
        assertFalse(source.contains("new Body") || source.contains("accounts.put(") || source.contains(".actor ="));
        String release = section(read("P11QualifiedSourceOwner.java"), "private void releaseRoot(",
                "/** May advance v");
        assertTrue(release.contains("if (--account.nativeCounts[index] == 0)"));
        assertTrue(release.contains("resources.releaseRoot(account.nativeRoots[index])"));
        assertFalse(release.contains("remove(") || release.contains("Root.NATIVE_CREDIT"));
    }

    @Test void rootResultAllocationPrecedesAdmissionAndRetainedObligationPublication() throws Exception {
        String source = read("P11ControlBudgets.java");
        String admission = section(source, "synchronized Optional<RootReservation> tryAcquireRoot(",
                "synchronized Optional<RootReservation> retainRoot(");
        String retained = section(source, "synchronized Optional<RootReservation> retainRoot(",
                "synchronized boolean releaseRoot(");
        for (String body : new String[] {admission, retained}) {
            before(body, "var result = Optional.of(reservation);", "owner.roots[root.ordinal()] = reservation;");
            assertTrue(body.contains("return result;"));
            assertFalse(body.contains("return Optional.of(reservation);"));
        }
        before(admission, "var result = Optional.of(reservation);", "accounts.put(owner.playerId, owner);");
    }

    @Test void normalLogoutIsExactWholeOnDisconnectNotTheSharedRemoveOrAnEvent() throws Exception {
        String mixin = read("mixin/P11LivePlayMixin.java");
        String hook = section(mixin, "@WrapMethod(method = \"onDisconnect", "@WrapMethod(method = \"handleClientCommand");
        assertTrue(hook.contains("onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V"));
        assertTrue(hook.contains("require = 1, expect = 1, allow = 1"));
        assertTrue(hook.contains("P11NativeStorageBoundary.normalLogout("));
        assertFalse(hook.contains("@Inject") || hook.contains("RETURN") || hook.contains("PlayerLoggedOutEvent"));
        String wrap = section(read("P11NativeStorageBoundary.java"), "public static void normalLogout(",
                "/** Only this call-local native wrapper");
        before(wrap, "foundation.beginNormalLogout(listener, actor)", "original.call(details)");
        before(wrap, "original.call(details)", "foundation.endNormalLogout(work, proof, normal)");
        before(wrap, "foundation.endNormalLogout(work, proof, normal)", "proof.closed = true;");
        assertEquals(1, wrap.split("original\\.call\\(", -1).length - 1);
        assertTrue(wrap.contains("finally {") && wrap.contains("NORMAL_LOGOUT.remove();"));
        assertTrue(wrap.contains("!body.logoutAttempted") && wrap.contains("!body.logoutActive"));
        assertTrue(wrap.contains("actor.isAlive() && !actor.isRemoved()"));
        assertTrue(wrap.contains("nativeContinuity(source) != null"));
        assertFalse(wrap.contains("throw secondary") || wrap.contains("return null") || wrap.contains(".getString()"));
    }

    @Test void nativeProofRequiresOriginalWholeCleanupExactActorEpochSourceAndLiveOuterScope() throws Exception {
        for (var constructor : P11NativeStorageBoundary.NormalLogoutProof.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
        String proof = section(read("P11NativeStorageBoundary.java"), "static final class NormalLogoutProof",
                "public static void remove(");
        assertProof(proof);
        for (String guard : new String[] {"!closed", "NORMAL_LOGOUT.get() == this",
                "removeObserved && wholeRemove", "body.actor == exactA", "source.owns(server)",
                "body.source.epoch() == epoch", "source.canCopy(body)", "body.envelope != null"}) {
            assertThrows(AssertionError.class, () -> assertProof(proof.replace(guard, "REMOVED")), guard);
        }
        assertTrue(proof.contains("!removeObserved && outcome == P11NativeCleanup.LogoutOutcome.WHOLE_NATIVE_COMPLETED"));
        assertFalse(proof.contains("STRUCTURAL_ONLY") || proof.contains("version() =="));
        String remove = section(read("P11NativeStorageBoundary.java"), "public static void remove(",
                "public static ServerPlayer respawn(");
        before(remove, "P11NativeCleanup.finishLogout(cleanup, normal)", "normalLogout.removed(source, body, outcome)");
        before(remove, "body.envelope = body.pendingEnvelope", "normalLogout.removed(source, body, outcome)");
    }

    @Test void acceptedWorkEntersOriginalOperationBeforeNativeMutationAndRetainsFixedRecipient() throws Exception {
        String begin = section(read("P11NativeOperationBoundary.java"), "static OperationScope beginAcceptedWork(",
                "private static OperationScope begin(ServerPlayer actor, Context context)");
        assertTrue(begin.contains("owner(exactA) != source"));
        assertTrue(begin.contains("nativeContinuity(source) == null"));
        assertTrue(begin.contains("source.nativeRecipient(body.actor) != body || !source.canCopy(body)"));
        before(begin, "source.retainNativeRoot(body, P11ControlBudgets.Root.OPERATION)", "source.nativeMutation(body)");
        assertTrue(begin.contains("new OperationScope(binding, exactA, OPERATION.get())"));
        assertTrue(begin.contains("release(binding, P11ControlBudgets.Root.OPERATION)"));
        assertTrue(begin.contains("throw failure;"));
        assertFalse(begin.contains("engineering(") || begin.contains("getUUID()") || begin.contains("hurt("));
    }

    @Test void firstRealCreditUsesActiveExactCauseBindingAcrossSuccessorWithoutFakeField() throws Exception {
        String credit = section(read("P11NativeOperationBoundary.java"), "public static Credit acquireCredit(",
                "/** A field write first reserves");
        for (String fact : new String[] {"holder.level().getServer() != actor.getServer()",
                "nativeContinuity(source) != null", "!operation.closed", "operation.origin == actor",
                "operation.binding.owner == source",
                "source.nativeRecipient(operation.binding.recipient) == operation.binding.body",
                "new Credit(source, body, holder, actor)", "P11ControlBudgets.Root.NATIVE_CREDIT"}) {
            assertTrue(credit.contains(fact), fact);
        }
        assertFalse(credit.contains("getUUID()") || credit.contains("setLastHurt") || credit.contains("engineering("));
    }

    @Test void compositionUsesOneFoundationAndOnlyExplicitObservedInactiveLegacyFallback() throws Exception {
        String foundation = read("P11FoundationService.java");
        String inactive = section(foundation, "boolean observedInactiveForRuntime(",
                "P11QualifiedSourceOwner.WorkReservation acquireWork(");
        assertTrue(inactive.contains("server == exact && exact != null && exact.isSameThread()"));
        assertTrue(inactive.contains("startupState instanceof P11StartupLoadState.Invalid"));
        assertTrue(inactive.contains("startupState instanceof P11StartupLoadState.Unavailable"));
        assertFalse(inactive.contains("slot == null") || inactive.contains("Ready"));
        assertTrue(foundation.contains("if (runtime != null) { throw new IllegalStateException(\"P11_RUNTIME_ALREADY_BOUND\"); }"));
        String root = read("Gramarye.java");
        before(root, "p11FoundationService = new P11FoundationService", "skillRuntimeService = SkillRuntimeService.create(");
        String runtimeComposition = section(root, "skillRuntimeService = SkillRuntimeService.create(",
                "p11FoundationService.bindRuntime(skillRuntimeService);");
        before(runtimeComposition, "p11FoundationService,", "p11CastCooldownService);");
        before(root, "p11FoundationService.bindCooldowns(p11CastCooldownService);",
                "skillRuntimeService = SkillRuntimeService.create(");
        before(root, "p11FoundationService.bindRuntime(skillRuntimeService);", "p8ServerPresentationService.registerAfterP5");
    }

    private static void assertAdmission(String source) {
        for (String fact : new String[] {"stopping", "nativeContinuity(this) == null", "!canCopy(body)",
                "!canonicalInputComplete(body)", "actor.isFakePlayer()", "getPlayer(actor.getUUID()) != actor",
                "getPacketListener() != actor.connection", "nativeCounts[index] == Long.MAX_VALUE",
                "!resources.mayAdmitWork(account.resource)"}) { assertTrue(source.contains(fact), fact); }
    }

    private static void assertProof(String source) {
        for (String fact : new String[] {"!closed", "NORMAL_LOGOUT.get() == this", "removeObserved && wholeRemove",
                "body.actor == exactA", "source.owns(server)", "body.source.epoch() == epoch",
                "source.canCopy(body)", "body.envelope != null"}) { assertTrue(source.contains(fact), fact); }
    }

    private static String read(String name) throws Exception {
        var root = Path.of("").toAbsolutePath().normalize();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        if (root == null) { throw new IllegalStateException("project root not found"); }
        return Files.readString(root.resolve("src/main/java/com/yo1no/gramarye").resolve(name));
    }
    private static String section(String source, String start, String end) {
        int first = source.indexOf(start), last = source.indexOf(end, first + start.length());
        assertTrue(first >= 0 && last > first, start + " -> " + end);
        return source.substring(first, last);
    }
    private static void before(String source, String first, String second) {
        assertTrue(source.indexOf(first) >= 0 && source.indexOf(second) > source.indexOf(first), first + " -> " + second);
    }
}
