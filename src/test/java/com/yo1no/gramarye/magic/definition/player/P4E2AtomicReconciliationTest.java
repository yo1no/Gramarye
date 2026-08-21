package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class P4E2AtomicReconciliationTest {
    @Test
    void exactPlayerViewUsesOnlyTheCapabilityToPreparedTokenP2Route()
            throws Exception {
        var opaque = PlayerSkillAttachmentService.OpaqueReconciliationCapability.class;
        var prepared = PlayerSkillAttachmentService.PreparedOnlineReconciliation.class;
        var playerView = P4E2QualificationFacade.PlayerView.class;
        var opaqueView = opaque.getDeclaredField("qualificationPlayerView");
        var preparedView = prepared.getDeclaredField("qualificationPlayerView");
        var constructors = Arrays.asList(opaque.getDeclaredConstructors());

        assertEquals(playerView, opaqueView.getType());
        assertTrue(Modifier.isPrivate(opaqueView.getModifiers()));
        assertEquals(playerView, preparedView.getType());
        assertTrue(Modifier.isPrivate(preparedView.getModifiers()));
        assertEquals(2, constructors.size());
        assertTrue(constructors.stream().allMatch(constructor ->
                Modifier.isProtected(constructor.getModifiers())));
        assertTrue(constructors.stream().anyMatch(constructor ->
                constructor.getParameterCount() == 11));
        assertTrue(constructors.stream().anyMatch(constructor ->
                constructor.getParameterCount() == 12
                        && constructor.getParameterTypes()[11] == playerView));
    }

    @Test
    void playerFreshnessRejectsEveryExactCoordinateDrift() {
        assertTrue(PlayerSkillAttachmentService.onlineReconciliationFactsCurrent(
                currentnessFactsExcept("none")));

        for (var coordinate : List.of(
                "service",
                "stage",
                "bindings",
                "thread",
                "server-thread",
                "player",
                "server",
                "uuid",
                "player-list",
                "uuid-lookup",
                "attachment-presence",
                "attachment-state")) {
            assertFalse(PlayerSkillAttachmentService.onlineReconciliationFactsCurrent(
                    currentnessFactsExcept(coordinate)), coordinate);
        }
    }

    @Test
    void capabilityLifecycleRejectsSecondClaim() {
        var lifecycle = new PlayerSkillAttachmentService.ReconciliationCapabilityLifecycle();

        lifecycle.claim();
        var failure = assertThrows(IllegalStateException.class, lifecycle::claim);

        assertEquals("P4E2_RECONCILIATION_CAPABILITY_ALREADY_CONSUMED",
                failure.getMessage());
    }

    @Test
    void oneBatchPrunesLatestAndEquippedWhilePreservingAllUnrelatedReadyState() {
        var staleLatestRoute = skill(1);
        var validLatestRoute = skill(2);
        var explicitEmptyRoute = skill(3);
        var validEquippedRoute = skill(5);
        var staleLatest = reference(staleLatestRoute, 10);
        var validLatest = reference(validLatestRoute, 20);
        var staleEquipped = staleLatest;
        var validEquipped = reference(validEquippedRoute, 40);
        var draft = draft(skill(6));
        var editor = new PlayerSkillEditorState(
                Optional.of(draft.skillId()), OptionalInt.of(0));
        var original = ready(
                List.of(new PlayerDraftEntry(
                        draft.skillId(), draft, encoded(draft))),
                List.of(
                        new PlayerLatestState(
                                staleLatestRoute, Optional.of(staleLatest), 7),
                        new PlayerLatestState(
                                validLatestRoute, Optional.of(validLatest), 8),
                        new PlayerLatestState(
                                explicitEmptyRoute, Optional.empty(), 9)),
                List.of(
                        new EquippedSkillReference(0, staleEquipped),
                        new EquippedSkillReference(1, validEquipped)),
                editor);

        var prepared = assertInstanceOf(
                PlayerSkillAttachmentService.ReconciliationRebuildOutcome.Prepared.class,
                PlayerSkillAttachmentService.rebuildForOnlineReconciliation(
                        original, new int[] {0}, new int[] {0}));
        var replacement = prepared.replacement();
        var originalTag = assertInstanceOf(CompoundTag.class, original.carrier().copyTag());
        var replacementTag = assertInstanceOf(
                CompoundTag.class, replacement.carrier().copyTag());

        assertEquals(original.drafts(), replacement.drafts());
        assertSame(editor, replacement.editor());
        assertEquals(
                PlayerSkillAttachmentSchema.CURRENT_VERSION,
                replacementTag.getInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION));
        assertEquals(
                originalTag.get(PlayerSkillAttachmentSchema.DRAFTS),
                replacementTag.get(PlayerSkillAttachmentSchema.DRAFTS));
        assertEquals(
                originalTag.get(PlayerSkillAttachmentSchema.EDITOR),
                replacementTag.get(PlayerSkillAttachmentSchema.EDITOR));
        assertEquals(
                List.of(
                        new PlayerLatestState(
                                staleLatestRoute, Optional.empty(), 8),
                        original.latestStates().get(1),
                        original.latestStates().get(2)),
                replacement.latestStates());
        assertEquals(List.of(original.equipped().get(1)), replacement.equipped());
    }

    @Test
    void allLatestSuccessorsArePrevalidatedBeforeAnyEquippedPruneCanBeBuilt() {
        var firstRoute = skill(10);
        var exhaustedRoute = skill(11);
        var equipped = reference(skill(12), 1);
        var original = ready(
                List.of(),
                List.of(
                        new PlayerLatestState(
                                firstRoute, Optional.of(reference(firstRoute, 1)), 3),
                        new PlayerLatestState(
                                exhaustedRoute,
                                Optional.of(reference(exhaustedRoute, 2)),
                                Integer.MAX_VALUE)),
                List.of(new EquippedSkillReference(0, equipped)),
                PlayerSkillEditorState.empty());

        assertSame(
                PlayerSkillAttachmentService.ReconciliationRebuildOutcome
                        .GenerationExhausted.INSTANCE,
                PlayerSkillAttachmentService.rebuildForOnlineReconciliation(
                        original, new int[] {0, 1}, new int[] {0}));
        assertEquals(2, original.latestStates().size());
        assertEquals(1, original.equipped().size());
        assertEquals(Integer.MAX_VALUE,
                original.latestStates().get(1).mutationGeneration());
    }

    @Test
    void equippedOnlyPruneDoesNotChangeAnyLatestGeneration() {
        var route = skill(15);
        var latest = new PlayerLatestState(
                route, Optional.of(reference(route, 4)), 17);
        var original = ready(
                List.of(),
                List.of(latest),
                List.of(new EquippedSkillReference(0, reference(skill(16), 5))),
                PlayerSkillEditorState.empty());

        var prepared = assertInstanceOf(
                PlayerSkillAttachmentService.ReconciliationRebuildOutcome.Prepared.class,
                PlayerSkillAttachmentService.rebuildForOnlineReconciliation(
                        original, new int[] {}, new int[] {0}));

        assertEquals(List.of(latest), prepared.replacement().latestStates());
        assertEquals(List.of(), prepared.replacement().equipped());
    }

    @Test
    void multipleStaleLatestRoutesEachAdvanceExactlyOnceAndValidNonlatestIsUntouched() {
        var firstRoute = skill(20);
        var secondRoute = skill(21);
        var validNonlatestRoute = skill(22);
        var validNonlatest = reference(validNonlatestRoute, 3);
        var original = ready(
                List.of(),
                List.of(
                        new PlayerLatestState(
                                firstRoute, Optional.of(reference(firstRoute, 1)), 0),
                        new PlayerLatestState(
                                secondRoute, Optional.of(reference(secondRoute, 2)), 41),
                        new PlayerLatestState(
                                validNonlatestRoute, Optional.of(validNonlatest), 7)),
                List.of(),
                PlayerSkillEditorState.empty());

        var prepared = assertInstanceOf(
                PlayerSkillAttachmentService.ReconciliationRebuildOutcome.Prepared.class,
                PlayerSkillAttachmentService.rebuildForOnlineReconciliation(
                        original, new int[] {0, 1}, new int[] {}));

        assertEquals(Optional.empty(), prepared.replacement().latestStates().get(0).pointer());
        assertEquals(1, prepared.replacement().latestStates().get(0).mutationGeneration());
        assertEquals(Optional.empty(), prepared.replacement().latestStates().get(1).pointer());
        assertEquals(42, prepared.replacement().latestStates().get(1).mutationGeneration());
        assertEquals(original.latestStates().get(2),
                prepared.replacement().latestStates().get(2));
    }

    private static PlayerSkillAttachmentReady ready(
            List<PlayerDraftEntry> drafts,
            List<PlayerLatestState> latest,
            List<EquippedSkillReference> equipped,
            PlayerSkillEditorState editor) {
        return assertInstanceOf(
                        PlayerSkillAttachmentBuildResult.Built.class,
                        PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                                drafts, latest, equipped, editor))
                .ready();
    }

    private static SkillId skill(long value) {
        return new SkillId(new UUID(0L, value));
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, new SkillRevision(revision));
    }

    private static SkillDraft draft(SkillId skillId) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                Optional.empty(),
                List.of(),
                AppearanceDocument.Default.INSTANCE);
    }

    private static SkillDraftPersistenceFacade.EncodedSkillDraft encoded(SkillDraft draft) {
        return assertInstanceOf(
                        SkillDraftPersistenceFacade.Encoded.class,
                        SkillDraftPersistenceFacade.encodeCurrent(draft))
                .draft();
    }

    private static PlayerSkillAttachmentService.OnlineReconciliationCurrentnessFacts
            currentnessFactsExcept(String drift) {
        return new PlayerSkillAttachmentService.OnlineReconciliationCurrentnessFacts(
                !drift.equals("service"),
                !drift.equals("stage"),
                !drift.equals("bindings"),
                !drift.equals("thread"),
                !drift.equals("server-thread"),
                !drift.equals("player"),
                !drift.equals("server"),
                !drift.equals("uuid"),
                !drift.equals("player-list"),
                !drift.equals("uuid-lookup"),
                !drift.equals("attachment-presence"),
                !drift.equals("attachment-state"));
    }
}
