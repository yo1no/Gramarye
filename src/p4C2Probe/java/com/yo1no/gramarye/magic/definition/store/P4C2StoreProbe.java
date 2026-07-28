package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;

/** Narrow, self-contained test-only live-Store seam for the combined P4-C2 memory gate. */
public final class P4C2StoreProbe {
    private static final String MANIFEST_FILE_NAME = "p4-b2-manifest.properties";
    private static final String PRIMARY_FILE_NAME = "gramarye_skill_definitions.dat";
    private static final String NONE = "none";
    private static final long MAX_MANIFEST_BYTES = 4_096;
    private static final int FULL_STORE_MINIMUM_BYTES = 63 * 1_024 * 1_024;
    private static final int HASH_BUFFER_BYTES = 8_192;
    private static final long IO_LATCH_TIMEOUT_SECONDS = 30;
    private static final Set<String> MANIFEST_KEYS = Set.of(
            "case",
            "phase",
            "source_primary_sha256",
            "expected_primary_sha256",
            "source_store_sha256",
            "canonical_store_sha256",
            "source_primary_bytes",
            "source_fname_bytes",
            "expected_primary_bytes",
            "expected_primary_last_modified_millis",
            "expected_store_bytes",
            "expected_histories",
            "expected_revisions",
            "expected_old_sha256",
            "expected_old_bytes");
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("P4-C2 cache-hit constructor was invoked");
                    },
                    (tag, provider) -> {
                        throw new AssertionError("P4-C2 cache-hit deserializer was invoked");
                    });

    private P4C2StoreProbe() {
    }

    /** Reads only bounded facts from the existing full-size Store fixture manifest. */
    public static ExpectedStore readExpected(Path worldRoot) throws IOException {
        var path = worldRoot.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("full Store fixture manifest is absent or oversized");
        }
        var values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            values.load(reader);
        }
        if (!values.stringPropertyNames().equals(MANIFEST_KEYS)
                || !"full".equals(required(values, "case"))
                || !"full-first-load-save".equals(required(values, "phase"))) {
            throw new IllegalArgumentException("full Store fixture manifest shape is not exact");
        }

        var sourcePrimary = required(values, "source_primary_sha256");
        var expectedPrimary = required(values, "expected_primary_sha256");
        var sourceStore = required(values, "source_store_sha256");
        var canonicalStore = required(values, "canonical_store_sha256");
        var sourcePrimaryBytes = parseLong(values, "source_primary_bytes");
        var expectedPrimaryBytes = parseLong(values, "expected_primary_bytes");
        requireSha256(sourcePrimary);
        requireSha256(expectedPrimary);
        requireSha256(sourceStore);
        requireSha256(canonicalStore);
        if (!sourcePrimary.equals(expectedPrimary)
                || sourcePrimaryBytes <= 0
                || sourcePrimaryBytes != expectedPrimaryBytes
                || parseLong(values, "source_fname_bytes") != 0
                || parseLong(values, "expected_primary_last_modified_millis") < 0
                || !NONE.equals(required(values, "expected_old_sha256"))
                || parseLong(values, "expected_old_bytes") != 0
                || sourceStore.equals(canonicalStore)) {
            throw new IllegalArgumentException("full Store fixture manifest facts are inconsistent");
        }
        return new ExpectedStore(
                parseInt(values, "expected_store_bytes"),
                parseInt(values, "expected_histories"),
                parseInt(values, "expected_revisions"),
                sourcePrimary,
                sourceStore,
                canonicalStore);
    }

    /**
     * Starts the first-run SavedData save while the shared IO worker is deliberately held.
     * The returned session must remain open across the exact Attachment lifecycle.
     */
    public static HeldFirstSave beginHeldFirstSave(
            MinecraftServer server, ExpectedStore expected) throws IOException {
        requireServerThread(server);
        var live = requireLive(server, expected);
        if (!live.ready().rewriteRequired() || !live.adapter().isDirty()) {
            throw new AssertionError("full noncanonical Store was not dirty rewrite Ready");
        }
        var primary = primary(server);
        if (!sha256(primary).equals(expected.sourcePrimaryChecksum())) {
            throw new AssertionError("full Store source primary checksum differs from manifest");
        }

        var workerEntered = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        IOUtilities.withIOWorker(() -> {
            workerEntered.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("P4-C2 IO hold was interrupted");
            }
        });
        boolean started = false;
        try {
            if (!workerEntered.await(IO_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("P4-C2 IO hold did not start");
            }
            live.storage().save();
            if (live.adapter().isDirty() || !live.ready().rewriteRequired()) {
                throw new AssertionError("held Store save did not consume only dirty state");
            }
            started = true;
            return new HeldFirstSave(server, expected, live.facts(), releaseWorker);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("P4-C2 server thread was interrupted");
        } finally {
            if (!started) {
                releaseWorker.countDown();
                IOUtilities.waitUntilIOWorkerComplete();
            }
        }
    }

    /** Proves a canonical restart is live, clean, and does not rewrite its primary. */
    public static StoreFacts requireCleanRestart(
            MinecraftServer server, ExpectedStore expected) throws IOException {
        requireServerThread(server);
        var live = requireLive(server, expected);
        if (live.ready().rewriteRequired() || live.adapter().isDirty()) {
            throw new AssertionError("canonical full Store restart was not clean Ready");
        }
        var primary = primary(server);
        var beforeHash = sha256(primary);
        var beforeBytes = Files.size(primary);
        var beforeModified = Files.getLastModifiedTime(primary).toMillis();
        live.storage().save();
        IOUtilities.waitUntilIOWorkerComplete();
        if (live.adapter().isDirty()
                || !sha256(primary).equals(beforeHash)
                || Files.size(primary) != beforeBytes
                || Files.getLastModifiedTime(primary).toMillis() != beforeModified) {
            throw new AssertionError("clean full Store restart performed an unexpected write");
        }
        requireCanonicalPrimary(primary, expected);
        return live.facts();
    }

    /** External-process proof that the full primary is canonical and semantically unchanged. */
    public static StoreFacts verifyCanonical(
            Path worldRoot, ExpectedStore expected) throws IOException {
        return requireCanonicalPrimary(
                worldRoot.resolve("data").resolve(PRIMARY_FILE_NAME), expected);
    }

    private static LiveStore requireLive(MinecraftServer server, ExpectedStore expected) {
        var storage = server.overworld().getDataStorage();
        var first = storage.get(
                CACHE_HIT_ONLY_FACTORY, SkillDefinitionStoreService.SAVED_DATA_NAME);
        var second = storage.get(
                CACHE_HIT_ONLY_FACTORY, SkillDefinitionStoreService.SAVED_DATA_NAME);
        if (!(first instanceof GramaryeSkillSavedData adapter) || second != adapter) {
            throw new AssertionError("P4-C2 Overworld cache lost exact Store adapter identity");
        }
        if (!(adapter.state() instanceof SkillSavedDataState.Ready ready)) {
            throw new AssertionError("P4-C2 full Store did not install Ready");
        }
        var carrier = ready.storeCarrier();
        var facts = new StoreFacts(
                carrier.storeByteCount(),
                carrier.historyCount(),
                carrier.revisionCount(),
                sha256(carrier));
        requireFacts(facts, expected);
        if (ready.innerCarrier().pending().byteCount() != 0) {
            throw new AssertionError("P4-C2 full Store unexpectedly contains pending bytes");
        }
        requireCarrierDomain(
                carrier, ready.store(), expected.histories(), expected.revisions());
        requireControlledReads(adapter, carrier);
        return new LiveStore(storage, adapter, ready, facts);
    }

    private static StoreFacts requireCanonicalPrimary(
            Path primary, ExpectedStore expected) {
        var loaded = SkillSavedDataPrimaryIngress.load(
                primary, Optional.of(RegistryAccess.EMPTY));
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)
                || ready.candidate().rewriteRequired()
                || ready.candidate().facts().truncated()
                || !ready.candidate().facts().facts().isEmpty()) {
            throw new AssertionError("P4-C2 saved full Store primary is not canonical Ready");
        }
        var candidate = ready.candidate();
        if (candidate.carrier().pending().byteCount() != 0) {
            throw new AssertionError("P4-C2 canonical primary contains pending bytes");
        }
        var carrier = candidate.carrier().storeCarrier();
        var facts = new StoreFacts(
                carrier.storeByteCount(),
                carrier.historyCount(),
                carrier.revisionCount(),
                sha256(carrier));
        requireFacts(facts, expected);
        requireCarrierDomain(
                carrier, candidate.store(), expected.histories(), expected.revisions());
        return facts;
    }

    private static void requireCarrierDomain(
            EncodedSkillStoreCarrier carrier,
            SkillDefinitionStore store,
            int expectedHistories,
            int expectedRevisions) {
        if (carrier.historyCount() != expectedHistories
                || carrier.revisionCount() != expectedRevisions) {
            throw new AssertionError("Store carrier counts differ from the manifest");
        }
        var revisions = 0;
        for (var history : carrier.histories()) {
            if (!store.ownerOf(history.skillId()).orElseThrow().equals(history.owner())
                    || !store.latestReference(history.skillId()).orElseThrow()
                            .equals(history.latestReference())) {
                throw new AssertionError("Store owner/latest route differs from the carrier");
            }
            for (var revision : history.revisions()) {
                if (store.find(revision.reference()).isEmpty()) {
                    throw new AssertionError("Store is missing a carrier revision route");
                }
                revisions++;
            }
        }
        if (revisions != expectedRevisions) {
            throw new AssertionError("Store revision traversal differs from the manifest");
        }
    }

    private static void requireControlledReads(
            GramaryeSkillSavedData adapter, EncodedSkillStoreCarrier carrier) {
        for (var history : carrier.histories()) {
            if (!(adapter.ownerOf(history.skillId())
                            instanceof SkillSubsystemResult.Available<?> owner)
                    || !(owner.value() instanceof Optional<?> ownerValue)
                    || ownerValue.isEmpty()
                    || !ownerValue.orElseThrow().equals(history.owner())
                    || !(adapter.latestReference(history.skillId())
                            instanceof SkillSubsystemResult.Available<?> latest)
                    || !(latest.value() instanceof Optional<?> latestValue)
                    || latestValue.isEmpty()
                    || !latestValue.orElseThrow().equals(history.latestReference())) {
                throw new AssertionError("P4-C2 controlled Store read differs from carrier");
            }
            for (var revision : history.revisions()) {
                if (!(adapter.find(revision.reference())
                                instanceof SkillSubsystemResult.Available<?> found)
                        || !(found.value() instanceof Optional<?> foundValue)
                        || foundValue.isEmpty()) {
                    throw new AssertionError("P4-C2 controlled Store missed a revision");
                }
            }
        }
    }

    private static void requireFacts(StoreFacts facts, ExpectedStore expected) {
        if (facts.storeBytes() < FULL_STORE_MINIMUM_BYTES
                || facts.storeBytes() != expected.storeBytes()
                || facts.histories() != expected.histories()
                || facts.revisions() != expected.revisions()
                || !facts.checksum().equals(expected.canonicalStoreChecksum())) {
            throw new AssertionError("P4-C2 full Store facts differ from manifest");
        }
    }

    private static String sha256(Path path) throws IOException {
        var digest = digest();
        var buffer = new byte[HASH_BUFFER_BYTES];
        try (InputStream input = Files.newInputStream(path)) {
            for (int count; (count = input.read(buffer)) != -1; ) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        return hex(digest.digest());
    }

    /** Hashes the exact canonical Store framing with at most one history-sized scratch buffer. */
    private static String sha256(EncodedSkillStoreCarrier carrier) {
        var output = new CarrierDigest();
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeNamedInt(
                "store_schema_version", StorePersistenceSchema.CURRENT_SCHEMA_VERSION);
        output.writeByte(Tag.TAG_LIST);
        output.writeUtf("history_entries");
        output.writeByte(Tag.TAG_BYTE_ARRAY);
        output.writeInt(carrier.historyCount());

        var maximumHistoryBytes = carrier.histories().stream()
                .mapToInt(EncodedHistoryIndex::byteLength)
                .max()
                .orElse(0);
        var scratch = new byte[maximumHistoryBytes];
        for (var history : carrier.histories()) {
            output.writeInt(history.byteLength());
            carrier.historySlice(history).copyInto(scratch, 0);
            output.writeBytes(scratch, history.byteLength());
        }
        output.writeByte(Tag.TAG_END);
        if (output.byteCount() != carrier.storeByteCount()) {
            throw new AssertionError("canonical Store digest framing length changed");
        }
        return output.finish();
    }

    private static Path primary(MinecraftServer server) {
        return SkillSavedDataPrimaryIngress.resolvePrimaryPath(server);
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("P4-C2 Store probe requires the server thread");
        }
    }

    private static String required(Properties values, String key) {
        var value = values.getProperty(key);
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("fixture manifest value is missing or unbounded");
        }
        return value;
    }

    private static int parseInt(Properties values, String key) {
        try {
            return Integer.parseInt(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("fixture manifest integer is malformed");
        }
    }

    private static long parseLong(Properties values, String key) {
        try {
            return Long.parseLong(required(values, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("fixture manifest long is malformed");
        }
    }

    private static void requireSha256(String checksum) {
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "checksum must be 64 lowercase hexadecimal characters");
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    public record ExpectedStore(
            int storeBytes,
            int histories,
            int revisions,
            String sourcePrimaryChecksum,
            String sourceStoreChecksum,
            String canonicalStoreChecksum) {
        public ExpectedStore {
            if (storeBytes < FULL_STORE_MINIMUM_BYTES || histories <= 0 || revisions <= 0) {
                throw new IllegalArgumentException("full Store shape is outside its bound");
            }
            requireSha256(sourcePrimaryChecksum);
            requireSha256(sourceStoreChecksum);
            requireSha256(canonicalStoreChecksum);
            if (sourceStoreChecksum.equals(canonicalStoreChecksum)) {
                throw new IllegalArgumentException("source Store must be noncanonical");
            }
        }
    }

    public record StoreFacts(
            int storeBytes, int histories, int revisions, String checksum) {
        public StoreFacts {
            if (storeBytes < FULL_STORE_MINIMUM_BYTES || histories <= 0 || revisions <= 0) {
                throw new IllegalArgumentException("Store facts are outside their bound");
            }
            requireSha256(checksum);
        }
    }

    public static final class HeldFirstSave implements AutoCloseable {
        private final MinecraftServer server;
        private final ExpectedStore expected;
        private final StoreFacts facts;
        private final CountDownLatch releaseWorker;
        private boolean closed;

        private HeldFirstSave(
                MinecraftServer server,
                ExpectedStore expected,
                StoreFacts facts,
                CountDownLatch releaseWorker) {
            this.server = server;
            this.expected = expected;
            this.facts = facts;
            this.releaseWorker = releaseWorker;
        }

        public StoreFacts facts() {
            return facts;
        }

        @Override
        public void close() {
            requireServerThread(server);
            if (closed) {
                throw new IllegalStateException("P4-C2 held Store save closed twice");
            }
            closed = true;
            try {
                requireLive(server, expected);
            } finally {
                releaseWorker.countDown();
                IOUtilities.waitUntilIOWorkerComplete();
            }
            requireCanonicalPrimary(primary(server), expected);
            requireLive(server, expected);
        }
    }

    private record LiveStore(
            DimensionDataStorage storage,
            GramaryeSkillSavedData adapter,
            SkillSavedDataState.Ready ready,
            StoreFacts facts) {
    }

    private static final class CarrierDigest {
        private final MessageDigest digest = digest();
        private long byteCount;

        void writeNamedInt(String name, int value) {
            writeByte(Tag.TAG_INT);
            writeUtf(name);
            writeInt(value);
        }

        void writeUtf(String value) {
            var bytes = value.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65_535) {
                throw new IllegalArgumentException("digest field name exceeds unsigned short");
            }
            writeByte(bytes.length >>> 8);
            writeByte(bytes.length);
            writeBytes(bytes, bytes.length);
        }

        void writeInt(int value) {
            writeByte(value >>> 24);
            writeByte(value >>> 16);
            writeByte(value >>> 8);
            writeByte(value);
        }

        void writeByte(int value) {
            digest.update((byte) value);
            byteCount++;
        }

        void writeBytes(byte[] bytes, int length) {
            digest.update(bytes, 0, length);
            byteCount = Math.addExact(byteCount, length);
        }

        long byteCount() {
            return byteCount;
        }

        String finish() {
            return hex(digest.digest());
        }
    }
}
