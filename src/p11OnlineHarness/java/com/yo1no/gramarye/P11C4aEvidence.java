package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/** Excluded, fixed-cohort public evidence only. No authentication input or console is read. */
final class P11C4aEvidence {
    private P11C4aEvidence() {}

    static boolean enabled() {
        return System.getProperty("gramarye.p11.online.output") != null && P11OnlineInputs.c4aCase();
    }

    static String property(String key) {
        var value = System.getProperty("gramarye.p11.online." + key);
        require(value != null && !value.isBlank(), "MISSING_C4A_PROPERTY");
        return value;
    }

    static int port() {
        String text = property("port");
        require(text.matches("[1-9][0-9]{3,4}"), "INVALID_C4A_PORT");
        int port = Integer.parseInt(text);
        require(port >= 1024 && port <= 65535, "INVALID_C4A_PORT");
        return port;
    }

    static Path root() throws IOException {
        var path = Path.of(property("output"));
        require(path.isAbsolute() && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path), "INVALID_C4A_EVIDENCE_ROOT");
        return path.toRealPath();
    }

    static Path reserve(String role) throws IOException {
        require(java.util.List.of("server", "client-a", "client-b", "client-host").contains(role), "INVALID_C4A_EVIDENCE_ROLE");
        return Files.createDirectory(root().resolve(role));
    }

    static void write(Path directory, String leaf, Object value) throws IOException {
        require(leaf.matches("[a-z0-9-]{1,64}\\.json"), "INVALID_C4A_EVIDENCE_LEAF");
        Files.writeString(directory.resolve(leaf), new GsonBuilder().setPrettyPrinting().create().toJson(value) + '\n',
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static void cue(Path directory, String leaf) throws IOException {
        require(leaf.matches("[a-z0-9-]{1,64}\\.ready"), "INVALID_C4A_CUE_LEAF");
        Files.write(directory.resolve(leaf), new byte[0], StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static boolean cuePresent(Path directory, String leaf) throws IOException {
        require(leaf.matches("[a-z0-9-]{1,64}\\.ready"), "INVALID_C4A_CUE_LEAF");
        var file = directory.resolve(leaf);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { return false; }
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) == 0, "INVALID_C4A_COORDINATION_CUE");
        return true;
    }

    static boolean receiptPresent(Path directory, String leaf) {
        require(leaf.matches("[a-z0-9-]{1,64}\\.json"), "INVALID_C4A_RECEIPT_LEAF");
        var file = directory.resolve(leaf);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file);
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA256_UNAVAILABLE"); }
    }

    static String pseudonym(UUID id) {
        return hash((property("runId") + '\n' + id).getBytes(StandardCharsets.UTF_8));
    }

    /** Only bounded scalars/lists/maps cross the evidence JSON boundary, never JDK Optional internals. */
    static Map<String, Object> sourceObservation(P11QualifiedSourceOwner.Diagnostics source) {
        require(source != null && source.resources() != null && source.dirtyAge() != null
                && source.saveProgress() != null && source.nativeResponsibilities() != null,
                "INVALID_SOURCE_OBSERVATION_SHAPE");
        var values = new LinkedHashMap<String, Object>();
        values.put("active", source.active());
        values.put("sourceEpoch", source.sourceEpoch());
        values.put("sourceVersion", source.sourceVersion());
        values.put("bodyComplete", source.bodyComplete());
        values.put("candidatePresent", source.candidatePresent());
        values.put("sourceFault", observationText(source.sourceFault(), 64));
        values.put("sourceInput", observationText(source.sourceInput(), 64));
        values.put("equippedSlot0", observationText(source.equippedSlot0(), 256));
        var writers = new ArrayList<Map<String, Object>>();
        for (var writer : observationList(source.writers(), 5)) {
            writers.add(Map.of("kind", observationText(writer.kind(), 64), "attempt", writer.attempt(),
                    "dirty", writer.dirty(), "terminal", observationText(writer.terminal(), 64),
                    "encode", observationText(writer.encode(), 64), "write", observationText(writer.write(), 64),
                    "close", observationText(writer.close(), 64), "replace", observationText(writer.replace(), 64),
                    "cacheAssignment", observationText(writer.cacheAssignment(), 64)));
        }
        values.put("writers", List.copyOf(writers));
        var resources = source.resources();
        values.put("resources", Map.of("retainedUuids", resources.retainedUuids(),
                "waitingConnections", resources.waitingConnections(), "sealedSnapshots", resources.sealedSnapshots(),
                "sealedBytes", resources.sealedBytes(), "inFlight", resources.inFlight(), "dirtyUuids", resources.dirtyUuids()));
        values.put("serializations", source.serializations());
        values.put("serializerNanos", source.serializerNanos());
        values.put("writes", source.writes());
        values.put("writeNanos", source.writeNanos());
        var dirty = source.dirtyAge();
        values.put("dirtyAge", Map.of("dirtyUuids", dirty.dirtyUuids(), "oldestMillis", observationLong(dirty.oldestMillis()),
                "warning", dirty.warning(), "clockUnavailable", dirty.clockUnavailable()));
        var progress = source.saveProgress();
        var kinds = new ArrayList<Map<String, Object>>();
        for (var kind : observationList(progress.kinds(), 5)) {
            require(kind.kind() != null, "INVALID_SOURCE_OBSERVATION_KIND");
            kinds.add(Map.of("kind", kind.kind().name(), "dirtySources", kind.dirtySources(),
                    "oldestDirtyMillis", observationLong(kind.oldestDirtyMillis()), "ageUnavailable", kind.ageUnavailable(),
                    "successfulPhysicalWrites", kind.successfulPhysicalWrites(), "counterSaturated", kind.counterSaturated()));
        }
        values.put("saveProgress", Map.of("elapsedMillis", observationLong(progress.elapsedMillis()), "kinds", List.copyOf(kinds)));
        var nativeRoots = source.nativeResponsibilities();
        var roots = new ArrayList<Map<String, Object>>();
        for (var root : observationList(nativeRoots.roots(), 5)) {
            roots.add(Map.of("kind", observationText(root.kind(), 64), "count", root.count(),
                    "accountPeakSum", root.accountPeakSum(), "oldestAgeMillis", root.oldestAgeMillis()));
        }
        values.put("nativeResponsibilities", Map.of("liveCanonicalHolders", nativeRoots.liveCanonicalHolders(),
                "detachedCanonicalHolders", nativeRoots.detachedCanonicalHolders(), "partialHolders", nativeRoots.partialHolders(),
                "roots", List.copyOf(roots)));
        return java.util.Collections.unmodifiableMap(values);
    }

    private static Map<String, Object> observationLong(OptionalLong value) {
        require(value != null, "INVALID_SOURCE_OBSERVATION_OPTIONAL");
        // Empty is explicitly absent, never silently represented as a measured zero.
        return value.isPresent() ? Map.of("present", true, "value", value.getAsLong()) : Map.of("present", false);
    }

    private static String observationText(String value, int maximum) {
        require(value != null && value.length() <= maximum && value.chars().noneMatch(Character::isISOControl),
                "INVALID_SOURCE_OBSERVATION_TEXT");
        return value;
    }

    private static <T> List<T> observationList(List<T> value, int maximum) {
        require(value != null && value.size() <= maximum && value.stream().allMatch(java.util.Objects::nonNull),
                "INVALID_SOURCE_OBSERVATION_LIST");
        return value;
    }

    static void require(boolean condition, String code) {
        if (!condition) { throw new Fault(code); }
    }

    static String failureCode(Throwable failure) {
        if (failure instanceof Fault fixed) { return fixed.code; }
        if (failure instanceof IOException) { return "EVIDENCE_IO_FAILURE"; }
        if (failure instanceof JsonIOException) { return "EVIDENCE_JSON_SERIALIZATION_FAILURE"; }
        if (failure instanceof LinkageError) { return "HARNESS_LINKAGE_FAILURE"; }
        return "UNCLASSIFIED_HARNESS_FAILURE";
    }

    private static final class Fault extends IllegalStateException {
        private final String code;
        private Fault(String code) { super(code); this.code = code; }
    }
}
