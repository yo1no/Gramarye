package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P4E2QualificationObservationTest {
    private static final UUID READY_PLAYER =
            UUID.fromString("c2b00000-0000-4000-8000-000000000001");

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalWriterUsesAllSeventeenFieldsInExactOrderAndRoundTrips()
            throws Exception {
        var observation = ready(P4E2QualificationObservation.Phase.FIRST);
        var expected = "{\"schema_version\":1"
                + ",\"case_id\":\"p4-c2-ready\""
                + ",\"phase\":\"first\""
                + ",\"player_uuid\":\"c2b00000-0000-4000-8000-000000000001\""
                + ",\"recovery_handler_calls\":1"
                + ",\"typed_recovery_outcome\":\"NoPending\""
                + ",\"entries_cleared\":0"
                + ",\"steps_replayed\":0"
                + ",\"recovery_changed\":false"
                + ",\"e2_continuation_calls\":1"
                + ",\"e2_result_variant\":\"NoChanges\""
                + ",\"invalidation_attempts\":0"
                + ",\"invalidation_accepted\":0"
                + ",\"invalidation_generation_present\":false"
                + ",\"e2_set_data_attempts\":0"
                + ",\"e2_set_data_successes\":0"
                + ",\"completion_marker\":\"P4_E2_DIRECT_OBSERVATION_COMPLETE\"}";

        observation.writeNewIn(temporaryDirectory);
        var path = P4E2QualificationObservation.directPath(temporaryDirectory);

        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(path));
        assertEquals(observation,
                P4E2QualificationObservation.readDirectFrom(temporaryDirectory));
        assertTrue(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(path));
        assertTrue(Files.size(path) <= P4E2QualificationObservation.MAX_FILE_BYTES);
    }

    @Test
    void writerNeverOverwritesAndLeavesNoTemporaryArtifact() throws Exception {
        var observation = ready(P4E2QualificationObservation.Phase.FIRST);
        observation.writeNewIn(temporaryDirectory);

        assertThrows(Exception.class, () -> observation.writeNewIn(temporaryDirectory));
        try (var paths = Files.list(temporaryDirectory)) {
            assertEquals(1L, paths.count());
        }
        assertEquals(observation,
                P4E2QualificationObservation.readDirectFrom(temporaryDirectory));
    }

    @Test
    void strictReaderRejectsDuplicateUnknownMissingReorderedWrongTypeAndTrailing()
            throws Exception {
        var canonical = canonical();
        assertRejected(canonical.replace(
                "\"case_id\":\"p4-c2-ready\"",
                "\"case_id\":\"p4-c2-ready\",\"case_id\":\"p4-c2-ready\""));
        assertRejected(canonical.replace(
                "\"case_id\":\"p4-c2-ready\"",
                "\"unknown\":0,\"case_id\":\"p4-c2-ready\""));
        assertRejected(canonical.replace(",\"entries_cleared\":0", ""));
        assertRejected(canonical.replace(
                ",\"entries_cleared\":0,\"steps_replayed\":0",
                ",\"steps_replayed\":0,\"entries_cleared\":0"));
        assertRejected(canonical.replace("\"schema_version\":1", "\"schema_version\":\"1\""));
        assertRejected(" " + canonical);
        assertRejected(canonical + "\n");
    }

    @Test
    void strictReaderRejectsInvalidEnumsIdentityUuidMarkerAndEncoding() throws Exception {
        var canonical = canonical();
        assertRejected(canonical.replace("\"NoPending\"", "\"Missing\""));
        assertRejected(canonical.replace("\"NoChanges\"", "\"Other\""));
        assertRejected(canonical.replace("\"p4-c2-ready\"", "\"p4-c2-other\""));
        assertRejected(canonical.replace(
                "P4_E2_DIRECT_OBSERVATION_COMPLETE", "P4_E2_DIRECT_OBSERVATION_OTHER"));
        assertRejected(canonical.replace(
                "c2b00000-0000-4000-8000-000000000001",
                "C2B00000-0000-4000-8000-000000000001"));
        assertRejected(canonical.replace("\"phase\":\"first\"", "\"phase\":\"later\""));

        var path = temporaryDirectory.resolve("malformed-utf8.json");
        Files.write(path, new byte[] {(byte) 0xC3, (byte) 0x28});
        assertThrows(IllegalArgumentException.class,
                () -> P4E2QualificationObservation.readEvidence(path));
    }

    @Test
    void strictReaderRejectsOversizeSymlinkAndNonregularPaths() throws Exception {
        var oversized = temporaryDirectory.resolve("oversized.json");
        Files.write(oversized,
                new byte[P4E2QualificationObservation.MAX_FILE_BYTES + 1]);
        assertThrows(IllegalArgumentException.class,
                () -> P4E2QualificationObservation.readEvidence(oversized));

        var regular = temporaryDirectory.resolve("regular.json");
        Files.writeString(regular, canonical(), StandardCharsets.UTF_8);
        var link = temporaryDirectory.resolve("link.json");
        Files.createSymbolicLink(link, regular.getFileName());
        assertThrows(IllegalArgumentException.class,
                () -> P4E2QualificationObservation.readEvidence(link));

        var directory = temporaryDirectory.resolve("directory.json");
        Files.createDirectory(directory);
        assertThrows(IllegalArgumentException.class,
                () -> P4E2QualificationObservation.readEvidence(directory));
    }

    @Test
    void readyVerifierRequiresExactPhasePlayerAndEveryDirectValue() {
        var first = ready(P4E2QualificationObservation.Phase.FIRST);
        first.requireReady(P4E2QualificationObservation.Phase.FIRST, READY_PLAYER);

        assertThrows(AssertionError.class, () -> first.requireReady(
                P4E2QualificationObservation.Phase.RESTART, READY_PLAYER));
        assertThrows(AssertionError.class, () -> first.requireReady(
                P4E2QualificationObservation.Phase.FIRST,
                UUID.fromString("c2b00000-0000-4000-8000-000000000002")));
        var changed = new P4E2QualificationObservation(
                1,
                P4E2QualificationObservation.CASE_ID,
                P4E2QualificationObservation.Phase.FIRST,
                READY_PLAYER,
                1,
                P4E2QualificationObservation.RecoveryOutcome.NO_PENDING,
                0,
                0,
                false,
                1,
                P4E2QualificationObservation.E2ResultVariant.RECOVERY_CHANGED,
                1,
                1,
                true,
                0,
                0,
                P4E2QualificationObservation.COMPLETION_MARKER);
        assertThrows(AssertionError.class, () -> changed.requireReady(
                P4E2QualificationObservation.Phase.FIRST, READY_PLAYER));
    }

    @Test
    void readyVerifierRejectsEveryNonfixedSemanticCoordinate() throws Exception {
        var canonical = canonical();
        assertReadyRejected(canonical.replace("\"NoPending\"", "\"Cleared\""));
        assertReadyRejected(canonical.replace("\"entries_cleared\":0",
                "\"entries_cleared\":1"));
        assertReadyRejected(canonical.replace("\"steps_replayed\":0",
                "\"steps_replayed\":1"));
        assertReadyRejected(canonical.replace("\"recovery_changed\":false",
                "\"recovery_changed\":true"));
        assertReadyRejected(canonical.replace("\"NoChanges\"", "\"Deferred\""));
        assertReadyRejected(canonical.replace("\"invalidation_attempts\":0",
                "\"invalidation_attempts\":1"));
        assertReadyRejected(canonical
                .replace("\"invalidation_attempts\":0", "\"invalidation_attempts\":1")
                .replace("\"invalidation_accepted\":0", "\"invalidation_accepted\":1")
                .replace("\"invalidation_generation_present\":false",
                        "\"invalidation_generation_present\":true"));
        assertReadyRejected(canonical.replace("\"e2_set_data_attempts\":0",
                "\"e2_set_data_attempts\":1"));
        assertReadyRejected(canonical
                .replace("\"e2_set_data_attempts\":0", "\"e2_set_data_attempts\":1")
                .replace("\"e2_set_data_successes\":0", "\"e2_set_data_successes\":1"));
    }

    @Test
    void phasePairComparisonIgnoresOnlyPhase() {
        var first = ready(P4E2QualificationObservation.Phase.FIRST);
        var restart = ready(P4E2QualificationObservation.Phase.RESTART);
        restart.requireSameSemanticsExceptPhase(first);

        var differentPlayer = new P4E2QualificationObservation(
                1,
                P4E2QualificationObservation.CASE_ID,
                P4E2QualificationObservation.Phase.RESTART,
                UUID.fromString("c2b00000-0000-4000-8000-000000000002"),
                1,
                P4E2QualificationObservation.RecoveryOutcome.NO_PENDING,
                0,
                0,
                false,
                1,
                P4E2QualificationObservation.E2ResultVariant.NO_CHANGES,
                0,
                0,
                false,
                0,
                0,
                P4E2QualificationObservation.COMPLETION_MARKER);
        assertThrows(AssertionError.class,
                () -> differentPlayer.requireSameSemanticsExceptPhase(first));
    }

    private String canonical() throws Exception {
        var directory = temporaryDirectory.resolve("canonical");
        Files.createDirectory(directory);
        ready(P4E2QualificationObservation.Phase.FIRST).writeNewIn(directory);
        return Files.readString(
                P4E2QualificationObservation.directPath(directory),
                StandardCharsets.UTF_8);
    }

    private void assertRejected(String text) throws Exception {
        var path = Files.createTempFile(temporaryDirectory, "invalid-", ".json");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> P4E2QualificationObservation.readEvidence(path));
    }

    private void assertReadyRejected(String text) throws Exception {
        var path = Files.createTempFile(temporaryDirectory, "nonready-", ".json");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        var observation = P4E2QualificationObservation.readEvidence(path);
        assertThrows(AssertionError.class, () -> observation.requireReady(
                P4E2QualificationObservation.Phase.FIRST, READY_PLAYER));
    }

    private static P4E2QualificationObservation ready(
            P4E2QualificationObservation.Phase phase) {
        return new P4E2QualificationObservation(
                P4E2QualificationObservation.SCHEMA_VERSION,
                P4E2QualificationObservation.CASE_ID,
                phase,
                READY_PLAYER,
                1,
                P4E2QualificationObservation.RecoveryOutcome.NO_PENDING,
                0,
                0,
                false,
                1,
                P4E2QualificationObservation.E2ResultVariant.NO_CHANGES,
                0,
                0,
                false,
                0,
                0,
                P4E2QualificationObservation.COMPLETION_MARKER);
    }
}
