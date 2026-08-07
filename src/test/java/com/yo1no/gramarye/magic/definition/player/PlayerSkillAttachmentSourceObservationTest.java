package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerSkillAttachmentSourceObservationTest {
    @Test
    void sourceLocalRootsAreLatestPresentThenEquippedAndPreserveDuplicates() {
        var low = reference(1, 3);
        var high = reference(9, 5);
        var built = assertInstanceOf(
                PlayerSkillAttachmentBuildResult.Built.class,
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(
                                new PlayerLatestState(
                                        high.skillId(), Optional.of(high), 2),
                                new PlayerLatestState(
                                        low.skillId(), Optional.of(low), 1),
                                new PlayerLatestState(
                                        new SkillId(new UUID(0, 4)), Optional.empty(), 7)),
                        List.of(
                                new EquippedSkillReference(63, high),
                                new EquippedSkillReference(0, low)),
                        PlayerSkillEditorState.empty()));

        var roots = PlayerSkillAttachmentSourceObservation.rootsForReady(
                built.ready());

        assertEquals(List.of(low, high, low, high), roots);
        assertThrows(UnsupportedOperationException.class, () -> roots.add(low));
    }

    private static SkillReference reference(long route, int revision) {
        return new SkillReference(
                new SkillId(new UUID(0, route)), new SkillRevision(revision));
    }
}
