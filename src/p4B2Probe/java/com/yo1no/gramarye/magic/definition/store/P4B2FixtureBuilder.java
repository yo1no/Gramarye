package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.IOUtilities;

/** Deterministic P4-B2-B world and primary-file fixture construction. */
final class P4B2FixtureBuilder {
    static final int FULL_SIZE_MINIMUM_BYTES = 63 * 1_024 * 1_024;

    private static final String FULL_WORKLOAD = "dedicated-mixed";
    private static final int GZIP_FIXED_HEADER_BYTES = 10;
    private static final int GZIP_FNAME_FLAG = 0x08;
    private static final byte HOSTILE_FNAME_BYTE =
            "\u00e9".getBytes(StandardCharsets.ISO_8859_1)[0];
    private static final int STREAM_BUFFER_BYTES = 8_192;
    private static final Optional<HolderLookup.Provider> STANDALONE_PROVIDER =
            Optional.of(RegistryAccess.EMPTY);
    private static final byte TAG_END = Tag.TAG_END;
    private static final byte TAG_INT = Tag.TAG_INT;
    private static final byte TAG_LIST = Tag.TAG_LIST;
    private static final byte TAG_BYTE_ARRAY = Tag.TAG_BYTE_ARRAY;
    private static final byte TAG_COMPOUND = Tag.TAG_COMPOUND;

    private P4B2FixtureBuilder() {
    }

    static void prepareFull(Path gameDirectory) throws IOException {
        var worldRoot = P4B2FixtureManifest.worldRoot(gameDirectory);
        var dataDirectory = worldRoot.resolve("data");
        Files.createDirectories(dataDirectory);

        var fixture = fullStoreFixture();
        var primary = P4B2FixtureManifest.primary(worldRoot);
        writePlatformPrimary(primary, fixture.carrier(), fixture.noncanonicalStoreBytes());
        requireCompressedCapacity(primary);
        requireFullPrimary(primary, fixture);

        var manifest = P4B2FixtureManifest.full(
                P4B2Hashing.sha256(primary),
                P4B2Hashing.sha256(fixture.noncanonicalStoreBytes()),
                P4B2Hashing.sha256(fixture.canonicalStoreBytes()),
                Files.size(primary),
                Files.getLastModifiedTime(primary).toMillis(),
                fixture.carrier().storeByteCount(),
                fixture.carrier().historyCount(),
                fixture.carrier().revisionCount());
        manifest.write(worldRoot);
    }

    static void prepareHostileFname(Path gameDirectory) throws IOException {
        var worldRoot = P4B2FixtureManifest.worldRoot(gameDirectory);
        var dataDirectory = worldRoot.resolve("data");
        Files.createDirectories(dataDirectory);

        var fixture = fullStoreFixture();
        var primary = P4B2FixtureManifest.primary(worldRoot);
        var basePrimary = dataDirectory.resolve(
                P4B2FixtureManifest.PRIMARY_FILE_NAME + ".hostile-base");
        writePlatformPrimary(basePrimary, fixture.carrier(), fixture.noncanonicalStoreBytes());
        requireCompressedCapacity(basePrimary);
        requireFullPrimary(basePrimary, fixture);

        var fnameBytes = Math.subtractExact(
                Math.subtractExact(
                        (long) MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES,
                        Files.size(basePrimary)),
                1L);
        if (fnameBytes <= 0) {
            throw new AssertionError("canonical full primary leaves no room for hostile FNAME");
        }
        try {
            writeHostileFnamePrimary(basePrimary, primary, fnameBytes);
        } finally {
            Files.deleteIfExists(basePrimary);
        }
        requireExactMaximumHostileFname(primary, fnameBytes);

        P4B2FixtureManifest.hostileFname(
                        P4B2Hashing.sha256(primary),
                        P4B2Hashing.sha256(fixture.noncanonicalStoreBytes()),
                        P4B2Hashing.sha256(fixture.canonicalStoreBytes()),
                        Files.size(primary),
                        fnameBytes,
                        Files.getLastModifiedTime(primary).toMillis(),
                        fixture.carrier().storeByteCount(),
                        fixture.carrier().historyCount(),
                        fixture.carrier().revisionCount())
                .write(worldRoot);
    }

    static void prepareInvalidWorlds(
            Path malformedGameDirectory,
            Path trailingGameDirectory,
            Path secondMemberGameDirectory) throws IOException {
        var member = canonicalEmptyMember();
        var malformed = member.clone();
        if (malformed.length < 18) {
            throw new AssertionError("canonical empty gzip member is unexpectedly short");
        }
        malformed[malformed.length - 8] ^= 1;

        var trailing = Arrays.copyOf(member, member.length + 1);
        trailing[trailing.length - 1] = 0;

        var secondMember = new byte[Math.multiplyExact(member.length, 2)];
        System.arraycopy(member, 0, secondMember, 0, member.length);
        System.arraycopy(member, 0, secondMember, member.length, member.length);

        prepareInvalid(
                malformedGameDirectory,
                P4B2ProbeCase.MALFORMED_GZIP,
                P4B2RunMode.MALFORMED_FIRST,
                malformed,
                member);
        prepareInvalid(
                trailingGameDirectory,
                P4B2ProbeCase.COMPRESSED_TRAILING,
                P4B2RunMode.TRAILING_FIRST,
                trailing,
                member);
        prepareInvalid(
                secondMemberGameDirectory,
                P4B2ProbeCase.SECOND_MEMBER,
                P4B2RunMode.SECOND_MEMBER_FIRST,
                secondMember,
                member);
    }

    static void preparePackagedRuntime(Path gameDirectory) throws IOException {
        var worldRoot = P4B2FixtureManifest.worldRoot(gameDirectory);
        Files.createDirectories(worldRoot.resolve("data"));

        var workload = P4A3ProbeWorkloads.smallDeterminismFixture();
        var carrier = requireCarrier(workload.store());
        requireA2MatchesCarrier(workload.store(), carrier);
        requireCarrierDomain(
                carrier,
                workload.store(),
                workload.expectedHistoryCount(),
                workload.expectedRevisionCount());
        var canonical = copy(carrier);
        var noncanonical = reorderStoreRootFields(canonical);
        requireNoncanonicalDomain(noncanonical, canonical, STANDALONE_PROVIDER);

        var primary = P4B2FixtureManifest.primary(worldRoot);
        writePlatformPrimary(primary, carrier, noncanonical);
        requireCompressedCapacity(primary);
        requireRewriteReady(primary, carrier, canonical);
        P4B2RuntimePackagingVerifier.writeFixtureManifest(
                worldRoot,
                P4B2Hashing.sha256(primary),
                P4B2Hashing.sha256(noncanonical),
                P4B2Hashing.sha256(canonical),
                carrier.storeByteCount(),
                carrier.historyCount(),
                carrier.revisionCount());
    }

    static SmallStoreFixture smallStoreFixture() {
        var workload = P4A3ProbeWorkloads.smallDeterminismFixture();
        var carrier = requireCarrier(workload.store());
        var canonical = copy(carrier);
        requireA2MatchesCarrier(workload.store(), carrier);
        var noncanonical = reorderStoreRootFields(canonical);
        requireNoncanonicalDomain(noncanonical, canonical, STANDALONE_PROVIDER);
        return new SmallStoreFixture(
                canonical,
                noncanonical,
                P4B2Hashing.sha256(canonical),
                P4B2Hashing.sha256(noncanonical),
                carrier.historyCount(),
                carrier.revisionCount());
    }

    static byte[] reorderStoreRootFields(byte[] canonical) {
        if (canonical.length < 53 || canonical[0] != TAG_COMPOUND
                || canonical[canonical.length - 1] != TAG_END) {
            throw new IllegalArgumentException("canonical Store root framing is not exact");
        }

        var firstFieldStart = 1;
        if (canonical[firstFieldStart] != TAG_INT) {
            throw new IllegalArgumentException("canonical Store schema field is not first");
        }
        var schemaNameEnd = requireNamedField(
                canonical, firstFieldStart, "store_schema_version");
        var firstFieldEnd = Math.addExact(schemaNameEnd, Integer.BYTES);
        if (firstFieldEnd >= canonical.length || canonical[firstFieldEnd] != TAG_LIST) {
            throw new IllegalArgumentException("canonical Store history list is not second");
        }
        var listPayload = requireNamedField(canonical, firstFieldEnd, "history_entries");
        if (canonical[listPayload] != TAG_BYTE_ARRAY) {
            throw new IllegalArgumentException("Store history list element type changed");
        }
        var position = Math.addExact(listPayload, 1);
        var count = readInt(canonical, position);
        if (count < 0) {
            throw new IllegalArgumentException("Store history count is negative");
        }
        position = Math.addExact(position, Integer.BYTES);
        for (var index = 0; index < count; index++) {
            var length = readInt(canonical, position);
            if (length <= 0) {
                throw new IllegalArgumentException("Store history blob is empty");
            }
            position = Math.addExact(position, Integer.BYTES);
            position = Math.addExact(position, length);
            if (position > canonical.length - 1) {
                throw new IllegalArgumentException("Store history list exceeds its root");
            }
        }
        if (position != canonical.length - 1) {
            throw new IllegalArgumentException("Store root has unexpected framing after histories");
        }

        var result = new byte[canonical.length];
        result[0] = TAG_COMPOUND;
        var output = 1;
        var listLength = canonical.length - 1 - firstFieldEnd;
        System.arraycopy(canonical, firstFieldEnd, result, output, listLength);
        output += listLength;
        var firstLength = firstFieldEnd - firstFieldStart;
        System.arraycopy(canonical, firstFieldStart, result, output, firstLength);
        output += firstLength;
        result[output] = TAG_END;
        if (output != result.length - 1 || Arrays.equals(canonical, result)) {
            throw new AssertionError("Store field-order transformation did not change exact bytes");
        }
        return result;
    }

    private static FullStoreFixture fullStoreFixture() {
        var workload = P4A3ProbeWorkloads.create(FULL_WORKLOAD, STANDALONE_PROVIDER);
        var carrier = requireCarrier(workload.store());
        var expected = P4A3ProbeWorkloads.shape(FULL_WORKLOAD);
        if (carrier.storeByteCount() < FULL_SIZE_MINIMUM_BYTES
                || carrier.storeByteCount() > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES
                || carrier.storeByteCount() != expected.expectedBaseBlobBytes()) {
            throw new AssertionError("actual P4-B2 Store carrier is outside its fixed full-size shape");
        }
        if (carrier.historyCount() != workload.expectedHistoryCount()
                || carrier.revisionCount() != workload.expectedRevisionCount()) {
            throw new AssertionError("actual P4-B2 Store route counts changed");
        }
        requireCarrierDomain(
                carrier,
                workload.store(),
                workload.expectedHistoryCount(),
                workload.expectedRevisionCount());
        requireA2MatchesCarrier(workload.store(), carrier);

        var canonical = copy(carrier);
        var noncanonical = reorderStoreRootFields(canonical);
        requireNoncanonicalDomain(noncanonical, canonical, STANDALONE_PROVIDER);
        return new FullStoreFixture(carrier, canonical, noncanonical);
    }

    private static void requireA2MatchesCarrier(
            SkillDefinitionStore store,
            EncodedSkillStoreCarrier carrier) {
        var encoded = SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(store);
        if (!(encoded instanceof StorePersistenceEncodeResult.Success success)
                || !carrier.matchesStoreBlob(success.blob())) {
            throw new AssertionError("A2 Store bytes differ from the A3 carrier");
        }
    }

    private static void requireNoncanonicalDomain(
            byte[] noncanonical,
            byte[] canonical,
            Optional<HolderLookup.Provider> provider) {
        if (Arrays.equals(noncanonical, canonical) || noncanonical.length != canonical.length) {
            throw new AssertionError("noncanonical Store fixture did not preserve exact length");
        }
        var loaded = SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                ImmutableStoreBlob.copyOf(noncanonical), provider);
        if (!(loaded instanceof StorePersistenceLoadResult.Loaded success)
                || success.rewritePending()) {
            throw new AssertionError("physical Store field order caused an A2 migration/failure");
        }
        var rebuilt = requireCarrier(success.store());
        if (!Arrays.equals(canonical, copy(rebuilt))) {
            throw new AssertionError("noncanonical Store changed the restored domain");
        }
    }

    private static void writePlatformPrimary(
            Path primary,
            EncodedSkillStoreCarrier carrier,
            byte[] storeBytes) throws IOException {
        var pending = OpaquePendingAttachmentUpdatesBlob.empty();
        var innerCarrier = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                pending,
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        carrier.storeByteCount()));
        var data = innerCarrier.createDataTag();
        data.putByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD, storeBytes);
        IOUtilities.writeNbtCompressed(platformRoot(data), primary);
    }

    private static void writeHostileFnamePrimary(
            Path basePrimary,
            Path primary,
            long fnameBytes) throws IOException {
        try (var input = new BufferedInputStream(
                        Files.newInputStream(basePrimary), STREAM_BUFFER_BYTES);
                var output = new BufferedOutputStream(
                        Files.newOutputStream(primary), STREAM_BUFFER_BYTES)) {
            var header = readGzipFixedHeader(input);
            requireExactGzipHeaderFlags(header, 0);
            header[3] = GZIP_FNAME_FLAG;
            output.write(header);
            writeHostileFname(output, fnameBytes);
            output.write(0);
            copyRemaining(input, output);
        }
        if (Files.size(primary)
                != MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES) {
            throw new AssertionError("hostile FNAME primary is not at the exact file ceiling");
        }
    }

    private static void writeHostileFname(OutputStream output, long fnameBytes)
            throws IOException {
        var buffer = new byte[STREAM_BUFFER_BYTES];
        Arrays.fill(buffer, HOSTILE_FNAME_BYTE);
        var remaining = fnameBytes;
        while (remaining > 0) {
            var count = (int) Math.min(remaining, buffer.length);
            output.write(buffer, 0, count);
            remaining -= count;
        }
    }

    static void requireExactMaximumHostileFname(Path primary, long expectedFnameBytes)
            throws IOException {
        if (Files.size(primary)
                        != MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES
                || expectedFnameBytes <= 0) {
            throw new AssertionError("hostile FNAME primary does not have its exact bound");
        }
        try (var input = new BufferedInputStream(
                Files.newInputStream(primary), STREAM_BUFFER_BYTES)) {
            var header = readGzipFixedHeader(input);
            requireExactGzipHeaderFlags(header, GZIP_FNAME_FLAG);
            var buffer = new byte[STREAM_BUFFER_BYTES];
            var remaining = expectedFnameBytes;
            while (remaining > 0) {
                var count = input.read(buffer, 0, (int) Math.min(remaining, buffer.length));
                if (count <= 0) {
                    throw new AssertionError("hostile FNAME ended before its manifest bound");
                }
                for (var index = 0; index < count; index++) {
                    if (buffer[index] == 0 || buffer[index] != HOSTILE_FNAME_BYTE) {
                        throw new AssertionError("hostile FNAME contains a non-fixture byte");
                    }
                }
                remaining -= count;
            }
            if (input.read() != 0) {
                throw new AssertionError("hostile FNAME is not exactly NUL-terminated");
            }
            if (input.read() < 0) {
                throw new AssertionError("hostile FNAME consumed the gzip payload");
            }
        }
    }

    static void requireCanonicalGzipWithoutFname(Path primary) throws IOException {
        if (Files.size(primary)
                >= MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES) {
            throw new AssertionError("canonical primary was not compacted below the ceiling");
        }
        try (var input = new BufferedInputStream(
                Files.newInputStream(primary), STREAM_BUFFER_BYTES)) {
            requireExactGzipHeaderFlags(readGzipFixedHeader(input), 0);
            if (input.read() < 0) {
                throw new AssertionError("canonical gzip has no deflate payload");
            }
        }
    }

    private static byte[] readGzipFixedHeader(InputStream input) throws IOException {
        var header = new byte[GZIP_FIXED_HEADER_BYTES];
        var offset = 0;
        while (offset < header.length) {
            var count = input.read(header, offset, header.length - offset);
            if (count <= 0) {
                throw new AssertionError("gzip ended before its fixed header");
            }
            offset += count;
        }
        return header;
    }

    private static void requireExactGzipHeaderFlags(byte[] header, int expectedFlags) {
        if (Byte.toUnsignedInt(header[0]) != 0x1f
                || Byte.toUnsignedInt(header[1]) != 0x8b
                || Byte.toUnsignedInt(header[2]) != 8
                || Byte.toUnsignedInt(header[3]) != expectedFlags) {
            throw new AssertionError("gzip fixed header is not the expected exact form");
        }
    }

    private static void copyRemaining(InputStream input, OutputStream output)
            throws IOException {
        var buffer = new byte[STREAM_BUFFER_BYTES];
        var copied = 0L;
        for (var count = input.read(buffer); count >= 0; count = input.read(buffer)) {
            if (count == 0) {
                continue;
            }
            output.write(buffer, 0, count);
            copied = Math.addExact(copied, count);
        }
        if (copied == 0) {
            throw new AssertionError("canonical gzip has no member payload or trailer");
        }
    }

    private static void requireFullPrimary(Path primary, FullStoreFixture fixture) {
        requireRewriteReady(
                primary,
                fixture.carrier(),
                fixture.canonicalStoreBytes());
    }

    private static void requireRewriteReady(
            Path primary,
            EncodedSkillStoreCarrier expectedCarrier,
            byte[] canonicalStoreBytes) {
        var loaded = SkillSavedDataPrimaryIngress.load(primary, STANDALONE_PROVIDER);
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)
                || !ready.candidate().rewriteRequired()
                || !ready.candidate().facts().facts().isEmpty()
                || ready.candidate().facts().truncated()) {
            throw new AssertionError("full noncanonical primary did not form its Ready candidate");
        }
        var inner = ready.candidate().carrier();
        if (inner.pending().byteCount() != 0
                || inner.storeCarrier().storeByteCount() != expectedCarrier.storeByteCount()
                || !P4B2Hashing.sha256(inner.storeCarrier())
                        .equals(P4B2Hashing.sha256(canonicalStoreBytes))) {
            throw new AssertionError("noncanonical primary rebuilt a different canonical carrier");
        }
    }

    private static void prepareInvalid(
            Path gameDirectory,
            P4B2ProbeCase fixtureCase,
            P4B2RunMode firstMode,
            byte[] primaryBytes,
            byte[] oldBytes) throws IOException {
        var worldRoot = P4B2FixtureManifest.worldRoot(gameDirectory);
        var dataDirectory = worldRoot.resolve("data");
        Files.createDirectories(dataDirectory);
        var primary = P4B2FixtureManifest.primary(worldRoot);
        var oldPrimary = P4B2FixtureManifest.oldPrimary(worldRoot);
        Files.write(primary, primaryBytes);
        Files.write(oldPrimary, oldBytes);
        requireCompressedCapacity(primary);
        requireInvalidClassification(primary, fixtureCase);

        P4B2FixtureManifest.invalid(
                        fixtureCase,
                        firstMode,
                        P4B2Hashing.sha256(primary),
                        Files.size(primary),
                        Files.getLastModifiedTime(primary).toMillis(),
                        P4B2Hashing.sha256(oldPrimary),
                        Files.size(oldPrimary))
                .write(worldRoot);
    }

    static void requireInvalidClassification(Path primary, P4B2ProbeCase fixtureCase) {
        var loaded = SkillSavedDataPrimaryIngress.load(primary, STANDALONE_PROVIDER);
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Failure failure)) {
            throw new AssertionError("invalid P4-B2 primary unexpectedly formed Ready/Absent");
        }
        var matches = switch (fixtureCase) {
            case MALFORMED_GZIP ->
                    failure.failure() instanceof SkillSavedDataPrimaryFailure.MalformedGzip;
            case COMPRESSED_TRAILING ->
                    failure.failure()
                            instanceof SkillSavedDataPrimaryFailure.CompressedTrailingData;
            case SECOND_MEMBER ->
                    failure.failure()
                            instanceof SkillSavedDataPrimaryFailure.MultipleGzipMembers;
            case FULL, HOSTILE_FNAME -> false;
        };
        if (!matches) {
            throw new AssertionError("invalid P4-B2 primary reached the wrong failure class");
        }
    }

    private static byte[] canonicalEmptyMember() throws IOException {
        var candidate = SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent();
        var output = new ByteArrayOutputStream(512);
        NbtIo.writeCompressed(platformRoot(candidate.carrier().createDataTag()), output);
        return output.toByteArray();
    }

    private static CompoundTag platformRoot(CompoundTag data) {
        var root = new CompoundTag();
        root.put(SkillSavedDataPersistenceSchema.DATA_FIELD, data);
        NbtUtils.addCurrentDataVersion(root);
        return root;
    }

    static void requireCarrierDomain(
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

    private static EncodedSkillStoreCarrier requireCarrier(SkillDefinitionStore store) {
        return switch (SkillStoreCarrierBuilder.rebuild(store)) {
            case CarrierBuildResult.Success success -> success.carrier();
            case CarrierBuildResult.Failure failure ->
                    throw new AssertionError("legal fixture carrier rebuild failed: "
                            + failure.failure().getClass().getSimpleName());
        };
    }

    private static byte[] copy(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
        return bytes;
    }

    private static int requireNamedField(byte[] bytes, int offset, String expectedName) {
        var lengthOffset = Math.addExact(offset, 1);
        var nameLength = readUnsignedShort(bytes, lengthOffset);
        var nameStart = Math.addExact(lengthOffset, Short.BYTES);
        var nameEnd = Math.addExact(nameStart, nameLength);
        if (nameEnd > bytes.length
                || !expectedName.equals(new String(
                        bytes, nameStart, nameLength, StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Store field name changed");
        }
        return nameEnd;
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        requireRange(bytes, offset, Short.BYTES);
        return (Byte.toUnsignedInt(bytes[offset]) << 8)
                | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private static int readInt(byte[] bytes, int offset) {
        requireRange(bytes, offset, Integer.BYTES);
        return Byte.toUnsignedInt(bytes[offset]) << 24
                | Byte.toUnsignedInt(bytes[offset + 1]) << 16
                | Byte.toUnsignedInt(bytes[offset + 2]) << 8
                | Byte.toUnsignedInt(bytes[offset + 3]);
    }

    private static void requireRange(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IllegalArgumentException("Store field framing exceeds its root");
        }
    }

    private static void requireCompressedCapacity(Path primary) throws IOException {
        if (Files.size(primary) > MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES) {
            throw new AssertionError("P4-B2 fixture exceeds the compressed-file ceiling");
        }
    }

    record SmallStoreFixture(
            byte[] canonicalStoreBytes,
            byte[] noncanonicalStoreBytes,
            String canonicalStoreSha256,
            String noncanonicalStoreSha256,
            int histories,
            int revisions) {
        SmallStoreFixture {
            canonicalStoreBytes = canonicalStoreBytes.clone();
            noncanonicalStoreBytes = noncanonicalStoreBytes.clone();
        }

        @Override
        public byte[] canonicalStoreBytes() {
            return canonicalStoreBytes.clone();
        }

        @Override
        public byte[] noncanonicalStoreBytes() {
            return noncanonicalStoreBytes.clone();
        }
    }

    private record FullStoreFixture(
            EncodedSkillStoreCarrier carrier,
            byte[] canonicalStoreBytes,
            byte[] noncanonicalStoreBytes) {
    }
}
