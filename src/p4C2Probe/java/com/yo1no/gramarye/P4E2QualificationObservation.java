package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.UUID;

/** Strict, bounded test-only transport for one completed P4-E2 qualification session. */
public record P4E2QualificationObservation(
        int schemaVersion,
        String caseId,
        Phase phase,
        UUID playerUuid,
        int recoveryHandlerCalls,
        RecoveryOutcome typedRecoveryOutcome,
        int entriesCleared,
        int stepsReplayed,
        boolean recoveryChanged,
        int e2ContinuationCalls,
        E2ResultVariant e2ResultVariant,
        int invalidationAttempts,
        int invalidationAccepted,
        boolean invalidationGenerationPresent,
        int e2SetDataAttempts,
        int e2SetDataSuccesses,
        String completionMarker) {
    public static final int SCHEMA_VERSION = 1;
    public static final String CASE_ID = "p4-c2-ready";
    public static final String FILE_NAME = "p4-e2-direct-observation.json";
    public static final String COMPLETION_MARKER = "P4_E2_DIRECT_OBSERVATION_COMPLETE";
    public static final int MAX_FILE_BYTES = 65_536;

    public P4E2QualificationObservation {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(typedRecoveryOutcome, "typedRecoveryOutcome");
        Objects.requireNonNull(e2ResultVariant, "e2ResultVariant");
        Objects.requireNonNull(completionMarker, "completionMarker");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported direct-observation schema");
        }
        if (!CASE_ID.equals(caseId)) {
            throw new IllegalArgumentException("unknown direct-observation case");
        }
        if (!COMPLETION_MARKER.equals(completionMarker)) {
            throw new IllegalArgumentException("invalid direct-observation completion marker");
        }
        requireBinaryCounter(recoveryHandlerCalls, "recoveryHandlerCalls");
        requireBinaryCounter(e2ContinuationCalls, "e2ContinuationCalls");
        requireBinaryCounter(invalidationAttempts, "invalidationAttempts");
        requireBinaryCounter(invalidationAccepted, "invalidationAccepted");
        requireBinaryCounter(e2SetDataAttempts, "e2SetDataAttempts");
        requireBinaryCounter(e2SetDataSuccesses, "e2SetDataSuccesses");
        requireNonNegative(entriesCleared, "entriesCleared");
        requireNonNegative(stepsReplayed, "stepsReplayed");
        if (recoveryHandlerCalls != 1 || e2ContinuationCalls != 1) {
            throw new IllegalArgumentException("completed observation lacks a direct coordinate");
        }
        if (invalidationAccepted > invalidationAttempts
                || e2SetDataSuccesses > e2SetDataAttempts
                || invalidationGenerationPresent != (invalidationAccepted == 1)) {
            throw new IllegalArgumentException("direct coordinate ordering is inconsistent");
        }
    }

    /** The two process phases are the only canonical values admitted by the fixed schema. */
    public enum Phase {
        FIRST("first"),
        RESTART("restart");

        private final String token;

        Phase(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        private static Phase fromToken(String token) {
            return switch (token) {
                case "first" -> FIRST;
                case "restart" -> RESTART;
                default -> throw new IllegalArgumentException(
                        "invalid direct-observation phase");
            };
        }
    }

    /** Exact bounded projection of the sealed recovery outcome local. */
    public enum RecoveryOutcome {
        NO_PENDING("NoPending"),
        CLEARED("Cleared"),
        REPLAYED("Replayed"),
        CLEARED_AND_REPLAYED("ClearedAndReplayed"),
        CONFLICT("Conflict"),
        TARGET_INVALID("TargetInvalid"),
        UNAVAILABLE("Unavailable");

        private final String token;

        RecoveryOutcome(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        private static RecoveryOutcome fromToken(String token) {
            for (var value : values()) {
                if (value.token.equals(token)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("invalid typed recovery outcome");
        }
    }

    /** Exact bounded projection of the sealed E2 result local. */
    public enum E2ResultVariant {
        NO_CHANGES("NoChanges"),
        RECOVERY_CHANGED("RecoveryChanged"),
        CHANGED("Changed"),
        DEFERRED("Deferred"),
        FAILED("Failed"),
        GENERATION_EXHAUSTED("GenerationExhausted");

        private final String token;

        E2ResultVariant(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        private static E2ResultVariant fromToken(String token) {
            for (var value : values()) {
                if (value.token.equals(token)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("invalid E2 result variant");
        }
    }

    /** Resolves the sole runtime direct target without accepting a caller-selected file name. */
    public static Path directPath(Path gameDirectory) {
        return Objects.requireNonNull(gameDirectory, "gameDirectory").resolve(FILE_NAME);
    }

    /** Publishes canonical UTF-8 only when the fixed direct target does not already exist. */
    public void writeNewIn(Path gameDirectory) throws IOException {
        var directory = requireRegularDirectory(gameDirectory);
        var target = directPath(directory);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("direct-observation target already exists");
        }
        var bytes = canonicalUtf8();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(
                    directory, ".p4-e2-direct-observation-", ".tmp");
            Files.write(
                    temporary,
                    bytes,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            var attributes = Files.readAttributes(
                    temporary,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || Files.isSymbolicLink(temporary)
                    || attributes.size() != bytes.length) {
                throw new IOException("direct-observation temporary file is invalid");
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
            requireRegularFile(target);
        } finally {
            if (temporary != null) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    /** Strictly reads the sole direct target from its fixed per-case game directory. */
    public static P4E2QualificationObservation readDirectFrom(Path gameDirectory)
            throws IOException {
        return readEvidence(directPath(requireRegularDirectory(gameDirectory)));
    }

    /** Strictly reads an already archived, generated qualification evidence file. */
    public static P4E2QualificationObservation readEvidence(Path path) throws IOException {
        var attributes = requireRegularFile(path);
        if (attributes.size() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("direct-observation file exceeds its bound");
        }
        final byte[] bytes;
        try (var input = Files.newInputStream(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES || input.read() != -1) {
                throw new IllegalArgumentException(
                        "direct-observation file exceeds its bound");
            }
        }
        if (bytes.length != attributes.size()) {
            throw new IllegalArgumentException("direct-observation file changed while reading");
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        final String text;
        try {
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IllegalArgumentException(
                    "direct-observation file is not strict UTF-8", exception);
        }
        var observation = new Parser(text).parse();
        if (!java.util.Arrays.equals(bytes, observation.canonicalUtf8())) {
            throw new IllegalArgumentException("direct-observation JSON is not canonical");
        }
        return observation;
    }

    /** Verifies every fixed READY value against the direct phase/player inputs. */
    public void requireReady(Phase expectedPhase, UUID expectedPlayerUuid) {
        Objects.requireNonNull(expectedPhase, "expectedPhase");
        Objects.requireNonNull(expectedPlayerUuid, "expectedPlayerUuid");
        if (phase != expectedPhase
                || !playerUuid.equals(expectedPlayerUuid)
                || recoveryHandlerCalls != 1
                || typedRecoveryOutcome != RecoveryOutcome.NO_PENDING
                || entriesCleared != 0
                || stepsReplayed != 0
                || recoveryChanged
                || e2ContinuationCalls != 1
                || e2ResultVariant != E2ResultVariant.NO_CHANGES
                || invalidationAttempts != 0
                || invalidationAccepted != 0
                || invalidationGenerationPresent
                || e2SetDataAttempts != 0
                || e2SetDataSuccesses != 0) {
            throw new AssertionError("P4-C2 READY direct observation differs");
        }
    }

    /** Verifies that first/restart differ only by their explicitly selected phase. */
    public void requireSameSemanticsExceptPhase(P4E2QualificationObservation other) {
        Objects.requireNonNull(other, "other");
        if (schemaVersion != other.schemaVersion
                || !caseId.equals(other.caseId)
                || !playerUuid.equals(other.playerUuid)
                || recoveryHandlerCalls != other.recoveryHandlerCalls
                || typedRecoveryOutcome != other.typedRecoveryOutcome
                || entriesCleared != other.entriesCleared
                || stepsReplayed != other.stepsReplayed
                || recoveryChanged != other.recoveryChanged
                || e2ContinuationCalls != other.e2ContinuationCalls
                || e2ResultVariant != other.e2ResultVariant
                || invalidationAttempts != other.invalidationAttempts
                || invalidationAccepted != other.invalidationAccepted
                || invalidationGenerationPresent != other.invalidationGenerationPresent
                || e2SetDataAttempts != other.e2SetDataAttempts
                || e2SetDataSuccesses != other.e2SetDataSuccesses
                || !completionMarker.equals(other.completionMarker)) {
            throw new AssertionError("P4-C2 READY direct semantics differ across phases");
        }
    }

    byte[] canonicalUtf8() {
        var text = "{\"schema_version\":" + schemaVersion
                + ",\"case_id\":\"" + caseId + "\""
                + ",\"phase\":\"" + phase.token + "\""
                + ",\"player_uuid\":\"" + playerUuid + "\""
                + ",\"recovery_handler_calls\":" + recoveryHandlerCalls
                + ",\"typed_recovery_outcome\":\"" + typedRecoveryOutcome.token + "\""
                + ",\"entries_cleared\":" + entriesCleared
                + ",\"steps_replayed\":" + stepsReplayed
                + ",\"recovery_changed\":" + recoveryChanged
                + ",\"e2_continuation_calls\":" + e2ContinuationCalls
                + ",\"e2_result_variant\":\"" + e2ResultVariant.token + "\""
                + ",\"invalidation_attempts\":" + invalidationAttempts
                + ",\"invalidation_accepted\":" + invalidationAccepted
                + ",\"invalidation_generation_present\":"
                + invalidationGenerationPresent
                + ",\"e2_set_data_attempts\":" + e2SetDataAttempts
                + ",\"e2_set_data_successes\":" + e2SetDataSuccesses
                + ",\"completion_marker\":\"" + completionMarker + "\"}";
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalStateException("direct-observation JSON exceeds its bound");
        }
        return bytes;
    }

    private static Path requireRegularDirectory(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        var attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || Files.isSymbolicLink(path)) {
            throw new IOException("direct-observation directory is not regular");
        }
        return path;
    }

    private static BasicFileAttributes requireRegularFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        var attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException(
                    "direct-observation path is not a regular non-symlink file");
        }
        return attributes;
    }

    private static void requireBinaryCounter(int value, String name) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " is outside its direct bound");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private P4E2QualificationObservation parse() {
            expect("{\"schema_version\":");
            var schemaVersion = integer();
            expect(",\"case_id\":");
            var caseId = string();
            expect(",\"phase\":");
            var phase = Phase.fromToken(string());
            expect(",\"player_uuid\":");
            var playerUuid = uuid(string());
            expect(",\"recovery_handler_calls\":");
            var recoveryHandlerCalls = integer();
            expect(",\"typed_recovery_outcome\":");
            var typedRecoveryOutcome = RecoveryOutcome.fromToken(string());
            expect(",\"entries_cleared\":");
            var entriesCleared = integer();
            expect(",\"steps_replayed\":");
            var stepsReplayed = integer();
            expect(",\"recovery_changed\":");
            var recoveryChanged = bool();
            expect(",\"e2_continuation_calls\":");
            var e2ContinuationCalls = integer();
            expect(",\"e2_result_variant\":");
            var e2ResultVariant = E2ResultVariant.fromToken(string());
            expect(",\"invalidation_attempts\":");
            var invalidationAttempts = integer();
            expect(",\"invalidation_accepted\":");
            var invalidationAccepted = integer();
            expect(",\"invalidation_generation_present\":");
            var invalidationGenerationPresent = bool();
            expect(",\"e2_set_data_attempts\":");
            var e2SetDataAttempts = integer();
            expect(",\"e2_set_data_successes\":");
            var e2SetDataSuccesses = integer();
            expect(",\"completion_marker\":");
            var completionMarker = string();
            expect("}");
            if (index != text.length()) {
                throw invalid("trailing bytes");
            }
            return new P4E2QualificationObservation(
                    schemaVersion,
                    caseId,
                    phase,
                    playerUuid,
                    recoveryHandlerCalls,
                    typedRecoveryOutcome,
                    entriesCleared,
                    stepsReplayed,
                    recoveryChanged,
                    e2ContinuationCalls,
                    e2ResultVariant,
                    invalidationAttempts,
                    invalidationAccepted,
                    invalidationGenerationPresent,
                    e2SetDataAttempts,
                    e2SetDataSuccesses,
                    completionMarker);
        }

        private int integer() {
            var start = index;
            if (index >= text.length() || text.charAt(index) < '0'
                    || text.charAt(index) > '9') {
                throw invalid("integer");
            }
            if (text.charAt(index) == '0') {
                index++;
                if (index < text.length()
                        && text.charAt(index) >= '0'
                        && text.charAt(index) <= '9') {
                    throw invalid("leading zero");
                }
                return 0;
            }
            while (index < text.length()
                    && text.charAt(index) >= '0'
                    && text.charAt(index) <= '9') {
                index++;
            }
            try {
                return Integer.parseInt(text.substring(start, index));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "invalid direct-observation integer", exception);
            }
        }

        private boolean bool() {
            if (text.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return false;
            }
            throw invalid("boolean");
        }

        private String string() {
            if (index >= text.length() || text.charAt(index) != '\"') {
                throw invalid("string");
            }
            var start = ++index;
            while (index < text.length() && text.charAt(index) != '\"') {
                var current = text.charAt(index);
                if (!((current >= 'a' && current <= 'z')
                        || (current >= 'A' && current <= 'Z')
                        || (current >= '0' && current <= '9')
                        || current == '-'
                        || current == '_')) {
                    throw invalid("noncanonical string");
                }
                index++;
            }
            if (index >= text.length() || index == start) {
                throw invalid("unterminated or empty string");
            }
            var value = text.substring(start, index);
            index++;
            return value;
        }

        private void expect(String expected) {
            if (!text.startsWith(expected, index)) {
                throw invalid("field order or token");
            }
            index += expected.length();
        }

        private IllegalArgumentException invalid(String reason) {
            return new IllegalArgumentException(
                    "invalid direct-observation JSON at " + index + ": " + reason);
        }

        private static UUID uuid(String value) {
            final UUID parsed;
            try {
                parsed = UUID.fromString(value);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "invalid direct-observation player UUID", exception);
            }
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException(
                        "noncanonical direct-observation player UUID");
            }
            return parsed;
        }
    }
}
