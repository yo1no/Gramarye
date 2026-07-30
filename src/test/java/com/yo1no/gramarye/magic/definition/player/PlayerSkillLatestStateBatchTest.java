package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PlayerSkillLatestStateBatchTest {
    private static final Path SERVICE_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/definition/player/"
                    + "PlayerSkillAttachmentService.java");

    @Test
    void missingObservationIsAnImmutableEmptyBatch() {
        var result = PlayerSkillAttachmentService.latestStateBatch(
                ObservedPlayerSkillAttachment.Missing.INSTANCE);
        var batch = availableBatch(result);

        assertTrue(batch.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> batch.add(view(1, 0)));
    }

    @Test
    void readyObservationReturnsEveryExplicitStateInCanonicalOrder() {
        var low = latest(1, 7, true);
        var high = latest(99, 13, false);
        var built = assertInstanceOf(
                PlayerSkillAttachmentBuildResult.Built.class,
                PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                        List.of(),
                        List.of(high, low),
                        List.of(),
                        PlayerSkillEditorState.empty()));

        var batch = availableBatch(PlayerSkillAttachmentService.latestStateBatch(
                new ObservedPlayerSkillAttachment.Ready(built.ready())));

        assertEquals(
                List.of(
                        view(low),
                        view(high)),
                batch);
        assertThrows(UnsupportedOperationException.class, () -> batch.removeFirst());
    }

    @Test
    void bothQuarantineVariantsRemainTypedUnavailable() {
        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            var result = PlayerSkillAttachmentService.latestStateBatch(
                    new ObservedPlayerSkillAttachment.Quarantined(reason));
            var unavailable = assertInstanceOf(
                    PlayerSkillAttachmentService.Unavailable.class, result);
            assertEquals(reason, unavailable.reason());
        }
    }

    @Test
    void publicBatchObservationReadsOnceAndNeverPublishesOrRebuilds() throws IOException {
        var source = Files.readString(SERVICE_SOURCE);
        var body = methodBody(source, "observeLatestStates");

        assertEquals(1, occurrences(body, "observeChecked("));
        assertEquals(0, occurrences(body, "setData("));
        assertEquals(0, occurrences(body, "getData("));
        assertEquals(0, occurrences(body, "rebuildReady("));
        assertEquals(0, occurrences(body, "findLatestState("));
    }

    private static PlayerLatestState latest(
            long route, int generation, boolean pointerPresent) {
        var skillId = new SkillId(new UUID(0, route));
        var pointer = pointerPresent
                ? Optional.of(new SkillReference(skillId, new SkillRevision(generation)))
                : Optional.<SkillReference>empty();
        return new PlayerLatestState(skillId, pointer, generation);
    }

    private static PlayerSkillAttachmentService.LatestStateView view(
            long route, int generation) {
        var skillId = new SkillId(new UUID(0, route));
        return new PlayerSkillAttachmentService.LatestStateView(
                skillId, Optional.empty(), generation);
    }

    private static PlayerSkillAttachmentService.LatestStateView view(
            PlayerLatestState state) {
        return new PlayerSkillAttachmentService.LatestStateView(
                state.skillId(), state.pointer(), state.mutationGeneration());
    }

    private static List<PlayerSkillAttachmentService.LatestStateView> availableBatch(
            PlayerSkillAttachmentService.Result<
                            List<PlayerSkillAttachmentService.LatestStateView>> result) {
        return switch (result) {
            case PlayerSkillAttachmentService.Available<
                            List<PlayerSkillAttachmentService.LatestStateView>> available ->
                    available.value();
            case PlayerSkillAttachmentService.Unavailable<
                            List<PlayerSkillAttachmentService.LatestStateView>> unavailable ->
                    throw new AssertionError(
                            "expected an available latest-state batch: "
                                    + unavailable.reason());
        };
    }

    private static int occurrences(String text, String token) {
        var count = 0;
        for (var offset = text.indexOf(token);
                offset >= 0;
                offset = text.indexOf(token, offset + token.length())) {
            count++;
        }
        return count;
    }

    private static String methodBody(String source, String methodName) {
        var declaration = source.indexOf(methodName + "(");
        if (declaration < 0) {
            throw new IllegalArgumentException("Missing method: " + methodName);
        }
        var opening = source.indexOf('{', declaration);
        var depth = 0;
        for (var index = opening; index < source.length(); index++) {
            switch (source.charAt(index)) {
                case '{' -> depth++;
                case '}' -> {
                    depth--;
                    if (depth == 0) {
                        return source.substring(opening + 1, index);
                    }
                }
                default -> {
                }
            }
        }
        throw new IllegalArgumentException("Unterminated method: " + methodName);
    }

    private static Path projectRoot() {
        var candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Unable to locate project root");
    }
}
