package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the production tuple decision only; these inputs cannot construct a native proof. */
final class P11JournalReadbackDecisionTest {
    @Test
    void exactReadbackTupleIsRequiredRatherThanPointerOrGenerationAlone() {
        var target = reference(1, 5);
        var states = List.of(latest(target, 7));

        assertTrue(matches(states, target, 7));
        assertFalse(matches(states, target, 6));
        assertFalse(matches(states, reference(1, 6), 7));
        assertFalse(matches(states, reference(2, 5), 7));
        assertFalse(PlayerSkillAttachmentService.readbackTupleMatches(
                Optional.of(states), new SkillId(new UUID(0, 2)), 7, target));
    }

    @Test
    void unknownReadbackAndProvenAbsenceDoNotAuthorizeAnyTarget() {
        var target = reference(1, 0);
        assertFalse(PlayerSkillAttachmentService.readbackTupleMatches(
                Optional.empty(), target.skillId(), 0, target));
        assertFalse(matches(List.of(), target, 0));
        assertFalse(matches(List.of(new PlayerSkillAttachmentService.LatestStateView(
                target.skillId(), Optional.empty(), 0)), target, 0));
    }

    @Test
    void duplicateRouteCannotLaunderAnOtherwiseMatchingTuple() {
        var target = reference(1, 1);
        assertFalse(matches(List.of(latest(target, 1), latest(target, 1)), target, 1));
        assertFalse(matches(List.of(latest(reference(1, 0), 0), latest(target, 1)), target, 1));
        var containsNull = new ArrayList<PlayerSkillAttachmentService.LatestStateView>();
        containsNull.add(latest(target, 1));
        containsNull.add(null);
        assertFalse(matches(containsNull, target, 1));
    }

    @Test
    void exactRouteCeilingIsAcceptedAndTheFirstExcessIsRejected() {
        var states = new ArrayList<PlayerSkillAttachmentService.LatestStateView>();
        for (var route = 1; route <= MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES; route++) {
            states.add(latest(reference(route, 1), 1));
        }
        var target = reference(MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES, 1);
        assertTrue(matches(states, target, 1));
        states.add(latest(reference(MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES + 1, 1), 1));
        assertFalse(matches(states, target, 1));
    }

    @Test
    void generationComparisonDoesNotWrapOrTreatRevisionAsMutationGeneration() {
        var target = reference(1, 3);
        assertTrue(matches(List.of(latest(target, Integer.MAX_VALUE)), target, Integer.MAX_VALUE));
        assertFalse(matches(List.of(latest(target, Integer.MAX_VALUE)), target, -1));
        assertFalse(matches(List.of(latest(target, 7)), target, target.revision().value()));
    }

    private static boolean matches(List<PlayerSkillAttachmentService.LatestStateView> states,
            SkillReference target, int generation) {
        return PlayerSkillAttachmentService.readbackTupleMatches(
                Optional.of(states), target.skillId(), generation, target);
    }

    private static PlayerSkillAttachmentService.LatestStateView latest(
            SkillReference reference, int generation) {
        return new PlayerSkillAttachmentService.LatestStateView(
                reference.skillId(), Optional.of(reference), generation);
    }

    private static SkillReference reference(int route, int revision) {
        return new SkillReference(new SkillId(new UUID(0, route)), new SkillRevision(revision));
    }
}
