package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentServiceTestSupport;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;

final class P4E2LoginReadyHandoffTest {
    @Test
    void onlyAllThreeNormalTerminalsPermitTheLoginHandoff() throws Exception {
        assertTrue(P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(
                new P4E2ReconciliationResult.NoChanges(emptySummary())));
        assertTrue(P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(
                new P4E2ReconciliationResult.RecoveryChanged(
                        new P4E2ReconciliationResult.Summary(
                                1, 0, 0, 0, 0, 0, 0, 0, OptionalLong.of(1)))));
        assertTrue(P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(
                new P4E2ReconciliationResult.Changed(
                        new P4E2ReconciliationResult.Summary(
                                0, 0, 1, 1, 0, 0, 1, 0, OptionalLong.of(1)))));
        var source = Files.readString(projectRoot().resolve("src/main/java/com/yo1no/gramarye/"
                + "magic/definition/store/P4E2OnlineReconciliationCoordinator.java"));
        var wrapper = source.substring(source.indexOf("public void reconcileAfterRecovery("),
                source.indexOf("static boolean isLoginReadyTerminal("));
        var call = "loginReadyPort.onLoginReady(server, exactPlayer);";
        assertEquals(1, source.split(java.util.regex.Pattern.quote(call), -1).length - 1);
        assertTrue(wrapper.indexOf("if (isLoginReadyTerminal(result))")
                > wrapper.indexOf("var result = reconcile("));
        assertTrue(wrapper.indexOf(call) > wrapper.indexOf("if (isLoginReadyTerminal(result))"));
        assertFalse(wrapper.contains("catch ("));
    }

    @Test
    void everyDeferredFailedAndExhaustedTerminalForbidsTheLoginHandoff() {
        for (var reason : P4E2ReconciliationResult.DeferredReason.values()) {
            assertFalse(P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(
                    new P4E2ReconciliationResult.Deferred(emptySummary(), reason)));
        }
        for (var reason : P4E2ReconciliationResult.FailureReason.values()) {
            var exceptionClass = switch (reason) {
                case INTERNAL_RUNTIME_FAILURE, RECOVERY_RUNTIME_FAILURE ->
                        Optional.of("java.lang.IllegalStateException");
                case RECOVERY_CONFLICT, RECOVERY_TARGET_INVALID,
                        PLAYER_GENERATION_EXHAUSTED, ATTACHMENT_CAPACITY_REJECTED,
                        ATTACHMENT_INVARIANT_REJECTED, FRESHNESS_LOST ->
                        Optional.<String>empty();
            };
            assertFalse(P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(
                    new P4E2ReconciliationResult.Failed(
                            emptySummary(), reason, exceptionClass)));
        }
        assertFalse(P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(
                new P4E2ReconciliationResult.GenerationExhausted(emptySummary())));
        assertThrows(NullPointerException.class,
                () -> P4E2OnlineReconciliationCoordinator.isLoginReadyTerminal(null));
    }

    @Test
    void storeFactoryRoutesTheSameImmutablePortIdentityIntoItsSoleCoordinator()
            throws Exception {
        var bus = BusBuilder.builder().build();
        var attachments = PlayerSkillAttachmentServiceTestSupport.createService();
        P7ServerAuthorizationBoundary.LoginReadyPort port = (server, actor) -> {};
        var store = SkillDefinitionStoreService.registerOn(bus, attachments, port);
        var coordinator = store.onlineReconciliationDependency();
        assertSame(coordinator, store.onlineReconciliationDependency());
        var field = P4E2OnlineReconciliationCoordinator.class.getDeclaredField("loginReadyPort");
        assertSame(P7ServerAuthorizationBoundary.LoginReadyPort.class, field.getType());
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertFalse(Modifier.isStatic(field.getModifiers()));
        field.setAccessible(true);
        assertSame(port, field.get(coordinator));
        assertThrows(NullPointerException.class,
                () -> SkillDefinitionStoreService.registerOn(bus, attachments, null));
    }

    private static P4E2ReconciliationResult.Summary emptySummary() {
        return new P4E2ReconciliationResult.Summary(
                0, 0, 0, 0, 0, 0, 0, 0, OptionalLong.empty());
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath(); candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
