package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Strict, schema-aware arbitrary-NBT framing for Store/History/Revision envelopes. */
final class StoreNbtFraming {
    private static final int TAG_END = Tag.TAG_END;
    private static final int TAG_INT = Tag.TAG_INT;
    private static final int TAG_BYTE_ARRAY = Tag.TAG_BYTE_ARRAY;
    private static final int TAG_STRING = Tag.TAG_STRING;
    private static final int TAG_LIST = Tag.TAG_LIST;
    private static final int TAG_COMPOUND = Tag.TAG_COMPOUND;
    private static final int TAG_INT_ARRAY = Tag.TAG_INT_ARRAY;

    private static final int STORE_WRAPPER_BYTES = 52;
    private static final int HISTORY_WRAPPER_BYTES = 85;
    private static final int REVISION_WRAPPER_BYTES = 85;
    private static final long QUOTA_WIRE_MULTIPLIER = 2;
    private static final long QUOTA_NODE_BYTES = 65;
    private static final MaterializationObserver NO_MATERIALIZATION_OBSERVER = () -> {
    };

    private StoreNbtFraming() {
    }

    static FramingResult<ImmutableStoreBlob> encodeStore(StorePersistentEnvelopeV0 envelope) {
        Objects.requireNonNull(envelope, "envelope");
        var framed = encodeStoreBytes(envelope.schemaVersion(), envelope.historyEntries());
        if (framed.failureValue().isPresent()) {
            return failure(framed.failureValue().orElseThrow());
        }
        return success(framed.successValue().orElseThrow().blob());
    }

    static FramingResult<EncodedStoreFrame> encodeStoreWithLayout(
            int schemaVersion,
            List<? extends RoutedHistorySource> historyEntries) {
        Objects.requireNonNull(historyEntries, "historyEntries");
        SkillId previousSkillId = null;
        var sources = new ArrayList<HistoryBlobSource>(historyEntries.size());
        for (var history : historyEntries) {
            Objects.requireNonNull(history, "history");
            if (previousSkillId != null
                    && previousSkillId.value().compareTo(history.skillId().value()) >= 0) {
                throw new IllegalArgumentException(
                        "routed histories must be in strict canonical SkillId order");
            }
            previousSkillId = history.skillId();
            sources.add(history.blobSource());
        }
        var framed = encodeStoreBytes(schemaVersion, sources);
        if (framed.failureValue().isPresent()) {
            return failure(framed.failureValue().orElseThrow());
        }
        var raw = framed.successValue().orElseThrow();
        if (raw.historyRanges().size() != historyEntries.size()) {
            throw new IllegalStateException("Store writer returned a mismatched history layout");
        }

        var indexes = new ArrayList<EncodedHistoryIndex>(historyEntries.size());
        for (var historyIndex = 0; historyIndex < historyEntries.size(); historyIndex++) {
            var history = historyEntries.get(historyIndex);
            var historyRange = raw.historyRanges().get(historyIndex);
            var references = history.references();
            var relativeRanges = history.revisionRanges();
            if (references.size() != relativeRanges.size()) {
                throw new IllegalArgumentException(
                        "routed revision references and ranges must have equal size");
            }
            var revisions = new ArrayList<EncodedRevisionIndex>(references.size());
            for (var revisionIndex = 0; revisionIndex < references.size(); revisionIndex++) {
                var relative = relativeRanges.get(revisionIndex);
                var absoluteOffset = Math.addExact(
                        (long) historyRange.offset(), relative.offset());
                revisions.add(new EncodedRevisionIndex(
                        references.get(revisionIndex), absoluteOffset, relative.length()));
            }
            indexes.add(new EncodedHistoryIndex(
                    history.skillId(),
                    history.owner(),
                    historyRange,
                    revisions));
        }
        return success(new WrittenStoreFrame(raw.blob(), raw.historyRanges(), indexes));
    }

    private static FramingResult<RawStoreFrame> encodeStoreBytes(
            int schemaVersion,
            List<? extends HistoryBlobSource> historyEntries) {
        Objects.requireNonNull(historyEntries, "historyEntries");
        try {
            if (schemaVersion < 0) {
                return failure(StorePersistenceFailure.EncodeFailed.INSTANCE);
            }
            var size = historyEnvelopeSize(historyEntries);
            if (size > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
                return failure(new StorePersistenceFailure.StoreBlobEncodedCapacityExceeded(
                        MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES + 1L,
                        MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES));
            }
            var output = new FramingOutput(toIntSize(size));
            output.writeByte(TAG_COMPOUND);
            output.writeNamedInt("store_schema_version", schemaVersion);
            var ranges = output.writeNamedHistoryBlobList("history_entries", historyEntries);
            output.writeByte(TAG_END);
            var blob = ImmutableStoreBlob.takeOwnership(output.finish());
            return success(new RawStoreFrame(blob, ranges));
        } catch (RuntimeException exception) {
            return failure(StorePersistenceFailure.EncodeFailed.INSTANCE);
        }
    }

    static FramingResult<ImmutableHistoryBlob> encodeHistory(HistoryPersistentEnvelopeV0 envelope) {
        Objects.requireNonNull(envelope, "envelope");
        var framed = encodeHistoryBytes(
                envelope.skillId(), envelope.owner(), envelope.revisionEntries());
        if (framed.failureValue().isPresent()) {
            return failure(framed.failureValue().orElseThrow());
        }
        return success(framed.successValue().orElseThrow().blob());
    }

    static FramingResult<EncodedHistoryFrame> encodeHistoryWithLayout(
            SkillId skillId,
            SkillOwnerId owner,
            List<? extends RoutedRevisionSource> revisionEntries) {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(revisionEntries, "revisionEntries");
        var references = new ArrayList<SkillReference>(revisionEntries.size());
        var sources = new ArrayList<RevisionBlobSource>(revisionEntries.size());
        SkillRevision previousRevision = null;
        for (var revision : revisionEntries) {
            Objects.requireNonNull(revision, "revision");
            var reference = Objects.requireNonNull(revision.reference(), "revision.reference");
            if (!skillId.equals(reference.skillId())
                    || previousRevision != null
                    && previousRevision.value() >= reference.revision().value()) {
                throw new IllegalArgumentException(
                        "routed revisions must match their history and be canonically ordered");
            }
            previousRevision = reference.revision();
            references.add(reference);
            sources.add(Objects.requireNonNull(revision.blobSource(), "revision.blobSource"));
        }
        var framed = encodeHistoryBytes(skillId, owner, sources);
        if (framed.failureValue().isPresent()) {
            return failure(framed.failureValue().orElseThrow());
        }
        var raw = framed.successValue().orElseThrow();
        return success(new WrittenHistoryFrame(
                skillId, owner, raw.blob(), references, raw.revisionRanges()));
    }

    private static FramingResult<RawHistoryFrame> encodeHistoryBytes(
            SkillId skillId,
            SkillOwnerId owner,
            List<? extends RevisionBlobSource> revisionEntries) {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(revisionEntries, "revisionEntries");
        try {
            var size = revisionEnvelopeSize(revisionEntries);
            if (size > MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES) {
                return failure(new StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded(
                        MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES + 1L,
                        MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES));
            }
            var encodedSkillId = encodeUuid(SkillId.CODEC.encodeStart(NbtOps.INSTANCE, skillId));
            var encodedOwner = encodeUuid(SkillOwnerId.CODEC.encodeStart(NbtOps.INSTANCE, owner));
            if (encodedSkillId == null || encodedOwner == null) {
                return failure(StorePersistenceFailure.EncodeFailed.INSTANCE);
            }

            var output = new FramingOutput(toIntSize(size));
            output.writeByte(TAG_COMPOUND);
            output.writeNamedIntArray("skill_id", encodedSkillId);
            output.writeNamedIntArray("owner", encodedOwner);
            var ranges = output.writeNamedRevisionBlobList("revision_entries", revisionEntries);
            output.writeByte(TAG_END);
            var blob = ImmutableHistoryBlob.takeOwnership(output.finish());
            return success(new RawHistoryFrame(blob, ranges));
        } catch (RuntimeException exception) {
            return failure(StorePersistenceFailure.EncodeFailed.INSTANCE);
        }
    }

    static FramingResult<EncodedRevisionFrame> encodeRevisionWithRoute(
            SkillReference reference,
            RevisionPersistentEnvelopeV0 envelope) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(envelope, "envelope");
        if (!reference.revision().equals(envelope.revision())) {
            throw new IllegalArgumentException(
                    "revision route must match the encoded Revision envelope");
        }
        var encoded = encodeRevision(envelope);
        if (encoded.failureValue().isPresent()) {
            return failure(encoded.failureValue().orElseThrow());
        }
        return success(new WrittenRevisionFrame(
                reference, encoded.successValue().orElseThrow()));
    }

    static RoutedRevisionSource bindVerifiedRevisionSource(
            SkillReference reference,
            RevisionBlobSource blobSource) {
        return new BoundRevisionSource(reference, blobSource);
    }

    static RoutedHistorySource bindVerifiedHistorySource(
            SkillId skillId,
            SkillOwnerId owner,
            HistoryBlobSource blobSource,
            List<SkillReference> references,
            List<BlobRange> revisionRanges) {
        return new BoundHistorySource(
                skillId, owner, blobSource, references, revisionRanges);
    }

    static FramingResult<ImmutableRevisionBlob> encodeRevision(
            RevisionPersistentEnvelopeV0 envelope) {
        Objects.requireNonNull(envelope, "envelope");
        try {
            var size = Math.addExact((long) REVISION_WRAPPER_BYTES, envelope.document().byteCount());
            if (size > MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES) {
                return failure(new StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded(
                        MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES + 1L,
                        MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES));
            }
            var output = new FramingOutput(toIntSize(size));
            output.writeByte(TAG_COMPOUND);
            output.writeNamedInt("revision", envelope.revision().value());
            output.writeNamedString("document_encoding", envelope.documentEncoding());
            output.writeNamedDocument("document_bytes", envelope.document());
            output.writeByte(TAG_END);
            return success(ImmutableRevisionBlob.takeOwnership(output.finish()));
        } catch (RuntimeException exception) {
            return failure(StorePersistenceFailure.EncodeFailed.INSTANCE);
        }
    }

    static FramingResult<StorePersistentEnvelopeV0> decodeStore(ImmutableStoreBlob blob) {
        return decodeStore(blob, NO_MATERIALIZATION_OBSERVER);
    }

    static FramingResult<StorePersistentEnvelopeV0> decodeStore(
            ImmutableStoreBlob blob,
            MaterializationObserver observer) {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(observer, "observer");
        if (blob.byteCount() > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
            return failure(new StorePersistenceFailure.StoreBlobEncodedCapacityExceeded(
                    blob.byteCount(), MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES));
        }
        try {
            var input = new FramingInput(blob.copyBytes());
            input.requireRootCompound();
            Integer version = null;
            BlobListPreflight historyPreflight = null;
            var fields = new HashSet<String>();
            while (!input.atCompoundEnd()) {
                var field = input.readNamedField();
                requireNewField(fields, field.name());
                switch (field.name()) {
                    case "store_schema_version" -> {
                        requireType(field.type(), TAG_INT);
                        version = input.readInt();
                    }
                    case "history_entries" -> {
                        requireType(field.type(), TAG_LIST);
                        historyPreflight = input.preflightBlobList(
                                MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES,
                                length -> new StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded(
                                        length,
                                        MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES));
                    }
                    default -> throw new MalformedEnvelope();
                }
            }
            input.requireFinished();
            if (version == null || version < 0 || historyPreflight == null || fields.size() != 2) {
                throw new MalformedEnvelope();
            }
            if (historyPreflight.deferredCapacity().isPresent()) {
                return failure(historyPreflight.deferredCapacity().orElseThrow());
            }
            var histories = new ArrayList<ImmutableHistoryBlob>();
            for (var slice : historyPreflight.slices()) {
                observer.onMaterialization();
                histories.add(ImmutableHistoryBlob.takeOwnership(input.copySlice(slice)));
            }
            return success(new StorePersistentEnvelopeV0(version, histories));
        } catch (MalformedEnvelope | RuntimeException exception) {
            return failure(StorePersistenceFailure.MalformedStoreEnvelope.INSTANCE);
        }
    }

    static FramingResult<HistoryPersistentEnvelopeV0> decodeHistory(ImmutableHistoryBlob blob) {
        return decodeHistory(blob, NO_MATERIALIZATION_OBSERVER);
    }

    static FramingResult<HistoryPersistentEnvelopeV0> decodeHistory(
            ImmutableHistoryBlob blob,
            MaterializationObserver observer) {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(observer, "observer");
        if (blob.byteCount() > MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES) {
            return failure(new StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded(
                    blob.byteCount(), MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES));
        }
        try {
            var input = new FramingInput(blob.copyBytes());
            input.requireRootCompound();
            SkillId skillId = null;
            SkillOwnerId owner = null;
            BlobListPreflight revisionPreflight = null;
            var fields = new HashSet<String>();
            while (!input.atCompoundEnd()) {
                var field = input.readNamedField();
                requireNewField(fields, field.name());
                switch (field.name()) {
                    case "skill_id" -> {
                        requireType(field.type(), TAG_INT_ARRAY);
                        skillId = decodeUuid(SkillId.CODEC.parse(
                                NbtOps.INSTANCE, new IntArrayTag(input.readUuidArray())));
                    }
                    case "owner" -> {
                        requireType(field.type(), TAG_INT_ARRAY);
                        owner = decodeUuid(SkillOwnerId.CODEC.parse(
                                NbtOps.INSTANCE, new IntArrayTag(input.readUuidArray())));
                    }
                    case "revision_entries" -> {
                        requireType(field.type(), TAG_LIST);
                        revisionPreflight = input.preflightBlobList(
                                MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES,
                                length -> new StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded(
                                        length,
                                        MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES));
                    }
                    default -> throw new MalformedEnvelope();
                }
            }
            input.requireFinished();
            if (skillId == null || owner == null || revisionPreflight == null
                    || fields.size() != 3) {
                throw new MalformedEnvelope();
            }
            if (revisionPreflight.deferredCapacity().isPresent()) {
                return failure(revisionPreflight.deferredCapacity().orElseThrow());
            }
            var revisions = new ArrayList<ImmutableRevisionBlob>();
            for (var slice : revisionPreflight.slices()) {
                observer.onMaterialization();
                revisions.add(ImmutableRevisionBlob.takeOwnership(input.copySlice(slice)));
            }
            return success(new HistoryPersistentEnvelopeV0(skillId, owner, revisions));
        } catch (MalformedEnvelope | RuntimeException exception) {
            return failure(StorePersistenceFailure.MalformedHistoryEnvelope.INSTANCE);
        }
    }

    static FramingResult<RevisionPersistentEnvelopeV0> decodeRevision(
            ImmutableRevisionBlob blob) {
        return decodeRevision(blob, null, NO_MATERIALIZATION_OBSERVER);
    }

    static FramingResult<RevisionPersistentEnvelopeV0> decodeRevisionForStore(
            ImmutableRevisionBlob blob,
            SkillId routeSkillId) {
        return decodeRevision(blob, Objects.requireNonNull(routeSkillId, "routeSkillId"),
                NO_MATERIALIZATION_OBSERVER);
    }

    static FramingResult<RevisionPersistentEnvelopeV0> decodeRevision(
            ImmutableRevisionBlob blob,
            SkillId routeSkillId,
            MaterializationObserver observer) {
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(observer, "observer");
        if (blob.byteCount() > MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES) {
            return failure(new StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded(
                    blob.byteCount(), MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES));
        }
        try {
            var input = new FramingInput(blob.copyBytes());
            input.requireRootCompound();
            Integer revision = null;
            String encoding = null;
            ByteSlice documentSlice = null;
            var fields = new HashSet<String>();
            while (!input.atCompoundEnd()) {
                var field = input.readNamedField();
                requireNewField(fields, field.name());
                switch (field.name()) {
                    case "revision" -> {
                        requireType(field.type(), TAG_INT);
                        revision = input.readInt();
                    }
                    case "document_encoding" -> {
                        requireType(field.type(), TAG_STRING);
                        encoding = input.readUtf();
                    }
                    case "document_bytes" -> {
                        requireType(field.type(), TAG_BYTE_ARRAY);
                        var length = input.readLength();
                        documentSlice = input.preflightSlice(length);
                    }
                    default -> throw new MalformedEnvelope();
                }
            }
            input.requireFinished();
            if (revision == null || revision < 0 || encoding == null || documentSlice == null
                    || documentSlice.length() == 0 || fields.size() != 3) {
                throw new MalformedEnvelope();
            }
            var typedRevision = new SkillRevision(revision);
            if (routeSkillId != null && !StorePersistenceSchema.DOCUMENT_ENCODING.equals(encoding)) {
                return failure(new StorePersistenceFailure.UnsupportedDocumentEncoding(
                        new SkillReference(routeSkillId, typedRevision)));
            }
            if (documentSlice.length() > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
                return failure(new StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded(
                        documentSlice.length(), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
            }
            observer.onMaterialization();
            var document = input.copySlice(documentSlice);
            return success(new RevisionPersistentEnvelopeV0(
                    typedRevision, encoding, EncodedSkillDocument.copyOf(document)));
        } catch (MalformedEnvelope | RuntimeException exception) {
            return failure(StorePersistenceFailure.MalformedRevisionEnvelope.INSTANCE);
        }
    }

    static CompoundTag toTag(StorePersistentEnvelopeV0 envelope) {
        var tag = new CompoundTag();
        tag.putInt("store_schema_version", envelope.schemaVersion());
        var entries = new net.minecraft.nbt.ListTag();
        for (var history : envelope.historyEntries()) {
            entries.add(new net.minecraft.nbt.ByteArrayTag(history.copyBytes()));
        }
        tag.put("history_entries", entries);
        return tag;
    }

    static FramingResult<StorePersistentEnvelopeV0> fromTag(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        try {
            if (!root.getAllKeys().equals(Set.of("store_schema_version", "history_entries"))
                    || !(root.get("store_schema_version") instanceof IntTag version)
                    || version.getAsInt() < 0
                    || !(root.get("history_entries") instanceof ListTag entries)) {
                throw new MalformedEnvelope();
            }
            var histories = new ArrayList<ImmutableHistoryBlob>();
            for (var entry : entries) {
                if (!(entry instanceof ByteArrayTag bytes)) {
                    throw new MalformedEnvelope();
                }
                var value = bytes.getAsByteArray();
                if (value.length == 0) {
                    throw new MalformedEnvelope();
                }
                if (value.length > MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES) {
                    return failure(
                            new StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded(
                                    value.length,
                                    MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES));
                }
                histories.add(ImmutableHistoryBlob.copyOf(value));
            }
            return success(new StorePersistentEnvelopeV0(version.getAsInt(), histories));
        } catch (MalformedEnvelope | RuntimeException exception) {
            return failure(StorePersistenceFailure.MalformedStoreEnvelope.INSTANCE);
        }
    }

    static long accounterQuota(long encodedLength) {
        if (encodedLength <= 0) {
            throw new IllegalArgumentException("encodedLength must be positive");
        }
        return Math.addExact(
                Math.multiplyExact(QUOTA_WIRE_MULTIPLIER, encodedLength),
                Math.multiplyExact(
                        QUOTA_NODE_BYTES, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES));
    }

    private static long historyEnvelopeSize(List<? extends HistoryBlobSource> entries) {
        var result = (long) STORE_WRAPPER_BYTES;
        for (var entry : entries) {
            result = Math.addExact(result, Math.addExact(4L, entry.byteCount()));
        }
        return result;
    }

    private static long revisionEnvelopeSize(List<? extends RevisionBlobSource> entries) {
        var result = (long) HISTORY_WRAPPER_BYTES;
        for (var entry : entries) {
            result = Math.addExact(result, Math.addExact(4L, entry.byteCount()));
        }
        return result;
    }

    private static int toIntSize(long value) {
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("encoded size is outside the materializable range");
        }
        return (int) value;
    }

    private static int[] encodeUuid(DataResult<Tag> encoded) {
        if (encoded.error().isPresent()
                || !(encoded.result().orElse(null) instanceof IntArrayTag array)) {
            return null;
        }
        var value = array.getAsIntArray();
        return value.length == 4 ? value : null;
    }

    private static <T> T decodeUuid(DataResult<T> decoded) throws MalformedEnvelope {
        if (decoded.error().isPresent() || decoded.result().isEmpty()) {
            throw new MalformedEnvelope();
        }
        return decoded.result().orElseThrow();
    }

    @FunctionalInterface
    interface MaterializationObserver {
        void onMaterialization();
    }

    @FunctionalInterface
    private interface NestedCapacityFactory {
        StorePersistenceFailure create(long observedAtLeast);
    }

    record BlobRange(int offset, int length) {
        BlobRange {
            if (offset < 0 || length <= 0) {
                throw new IllegalArgumentException("blob range must be positive and non-negative");
            }
            Math.addExact(offset, length);
        }

        static BlobRange fromLong(long offset, long length) {
            if (offset < 0 || length <= 0) {
                throw new IllegalArgumentException("blob range must be positive and non-negative");
            }
            Math.addExact(offset, length);
            return new BlobRange(Math.toIntExact(offset), Math.toIntExact(length));
        }

        long endExclusive() {
            return Math.addExact((long) offset, length);
        }

        void requireWithin(long parentLength) {
            if (parentLength < 0 || endExclusive() > parentLength) {
                throw new IllegalArgumentException("blob range is outside its encoded root");
            }
        }
    }

    sealed interface RoutedRevisionSource
            permits EncodedRevisionFrame, BoundRevisionSource {
        SkillReference reference();

        RevisionBlobSource blobSource();
    }

    sealed interface RoutedHistorySource
            permits EncodedHistoryFrame, BoundHistorySource {
        SkillId skillId();

        SkillOwnerId owner();

        HistoryBlobSource blobSource();

        List<SkillReference> references();

        List<BlobRange> revisionRanges();
    }

    sealed interface EncodedRevisionFrame extends RoutedRevisionSource
            permits WrittenRevisionFrame {
        ImmutableRevisionBlob blob();

        @Override
        default RevisionBlobSource blobSource() {
            return blob();
        }
    }

    sealed interface EncodedStoreFrame permits WrittenStoreFrame {
        ImmutableStoreBlob blob();

        List<BlobRange> historyRanges();

        List<EncodedHistoryIndex> histories();
    }

    sealed interface EncodedHistoryFrame extends RoutedHistorySource
            permits WrittenHistoryFrame {
        ImmutableHistoryBlob blob();

        @Override
        default HistoryBlobSource blobSource() {
            return blob();
        }
    }

    private record WrittenStoreFrame(
            ImmutableStoreBlob blob,
            List<BlobRange> historyRanges,
            List<EncodedHistoryIndex> histories) implements EncodedStoreFrame {
        private WrittenStoreFrame {
            Objects.requireNonNull(blob, "blob");
            historyRanges = checkedRanges(blob.byteCount(), historyRanges);
            histories = List.copyOf(Objects.requireNonNull(histories, "histories"));
            if (historyRanges.size() != histories.size()) {
                throw new IllegalArgumentException(
                        "Store writer ranges and routed histories must have equal size");
            }
        }
    }

    private record WrittenHistoryFrame(
            SkillId skillId,
            SkillOwnerId owner,
            ImmutableHistoryBlob blob,
            List<SkillReference> references,
            List<BlobRange> revisionRanges) implements EncodedHistoryFrame {
        private WrittenHistoryFrame {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(blob, "blob");
            references = List.copyOf(Objects.requireNonNull(references, "references"));
            revisionRanges = checkedRanges(blob.byteCount(), revisionRanges);
            requireRoutedRevisionLayout(
                    skillId, blob.byteCount(), references, revisionRanges);
        }
    }

    private record WrittenRevisionFrame(
            SkillReference reference,
            ImmutableRevisionBlob blob) implements EncodedRevisionFrame {
        private WrittenRevisionFrame {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(blob, "blob");
        }
    }

    private record BoundRevisionSource(
            SkillReference reference,
            RevisionBlobSource blobSource) implements RoutedRevisionSource {
        private BoundRevisionSource {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(blobSource, "blobSource");
        }
    }

    private record BoundHistorySource(
            SkillId skillId,
            SkillOwnerId owner,
            HistoryBlobSource blobSource,
            List<SkillReference> references,
            List<BlobRange> revisionRanges) implements RoutedHistorySource {
        private BoundHistorySource {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(blobSource, "blobSource");
            references = List.copyOf(Objects.requireNonNull(references, "references"));
            revisionRanges = List.copyOf(
                    Objects.requireNonNull(revisionRanges, "revisionRanges"));
            requireRoutedRevisionLayout(
                    skillId, blobSource.byteCount(), references, revisionRanges);
        }
    }

    private record RawStoreFrame(
            ImmutableStoreBlob blob,
            List<BlobRange> historyRanges) {
        private RawStoreFrame {
            Objects.requireNonNull(blob, "blob");
            historyRanges = checkedRanges(blob.byteCount(), historyRanges);
        }
    }

    private record RawHistoryFrame(
            ImmutableHistoryBlob blob,
            List<BlobRange> revisionRanges) {
        private RawHistoryFrame {
            Objects.requireNonNull(blob, "blob");
            revisionRanges = checkedRanges(blob.byteCount(), revisionRanges);
        }
    }

    private static List<BlobRange> checkedRanges(long blobLength, List<BlobRange> ranges) {
        var copy = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
        for (var range : copy) {
            range.requireWithin(blobLength);
        }
        return copy;
    }

    private static void requireRoutedRevisionLayout(
            SkillId skillId,
            long historyLength,
            List<SkillReference> references,
            List<BlobRange> ranges) {
        if (references.size() != ranges.size()) {
            throw new IllegalArgumentException(
                    "routed revision references and ranges must have equal size");
        }
        SkillRevision previousRevision = null;
        long previousEnd = 0;
        for (var index = 0; index < references.size(); index++) {
            var reference = Objects.requireNonNull(references.get(index), "reference");
            var range = Objects.requireNonNull(ranges.get(index), "range");
            if (!skillId.equals(reference.skillId())
                    || previousRevision != null
                    && previousRevision.value() >= reference.revision().value()
                    || range.offset() < previousEnd) {
                throw new IllegalArgumentException(
                        "routed revision layout must match its history and be canonical");
            }
            range.requireWithin(historyLength);
            previousRevision = reference.revision();
            previousEnd = range.endExclusive();
        }
    }

    private record NamedField(int type, String name) {
        private NamedField {
            Objects.requireNonNull(name, "name");
        }
    }

    private record ByteSlice(int offset, int length) {
        private ByteSlice {
            if (offset < 0 || length < 0) {
                throw new IllegalArgumentException("slice bounds must be non-negative");
            }
        }
    }

    private record BlobListPreflight(
            List<ByteSlice> slices,
            java.util.Optional<StorePersistenceFailure> deferredCapacity) {
        private BlobListPreflight {
            slices = List.copyOf(Objects.requireNonNull(slices, "slices"));
            Objects.requireNonNull(deferredCapacity, "deferredCapacity");
        }
    }

    private static void requireNewField(Set<String> fields, String name)
            throws MalformedEnvelope {
        if (!fields.add(name)) {
            throw new MalformedEnvelope();
        }
    }

    private static void requireType(int actual, int expected) throws MalformedEnvelope {
        if (actual != expected) {
            throw new MalformedEnvelope();
        }
    }

    private static <T> FramingResult<T> success(T value) {
        return new FramingResult.Success<>(value);
    }

    private static <T> FramingResult<T> failure(StorePersistenceFailure failure) {
        return new FramingResult.Failure<>(failure);
    }

    sealed interface FramingResult<T> permits FramingResult.Success, FramingResult.Failure {
        java.util.Optional<T> successValue();

        java.util.Optional<StorePersistenceFailure> failureValue();

        record Success<T>(T value) implements FramingResult<T> {
            public Success {
                Objects.requireNonNull(value, "value");
            }

            @Override
            public java.util.Optional<T> successValue() {
                return java.util.Optional.of(value);
            }

            @Override
            public java.util.Optional<StorePersistenceFailure> failureValue() {
                return java.util.Optional.empty();
            }
        }

        record Failure<T>(StorePersistenceFailure failure) implements FramingResult<T> {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }

            @Override
            public java.util.Optional<T> successValue() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<StorePersistenceFailure> failureValue() {
                return java.util.Optional.of(failure);
            }
        }
    }

    private static final class FramingInput {
        private final byte[] bytes;
        private final NbtAccounter accounter;
        private int position;
        private int physicalNodeCount;

        FramingInput(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            if (bytes.length == 0) {
                throw new IllegalArgumentException("encoded NBT must not be empty");
            }
            this.accounter = new NbtAccounter(
                    accounterQuota(bytes.length), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH);
        }

        void requireRootCompound() throws MalformedEnvelope {
            if (readUnsignedByte() != TAG_COMPOUND) {
                throw new MalformedEnvelope();
            }
            accountNode();
        }

        boolean atCompoundEnd() throws MalformedEnvelope {
            requireRemaining(1);
            if (Byte.toUnsignedInt(bytes[position]) != TAG_END) {
                return false;
            }
            consume(1);
            return true;
        }

        int readUnsignedByte() throws MalformedEnvelope {
            requireRemaining(1);
            var value = Byte.toUnsignedInt(bytes[position]);
            consume(1);
            return value;
        }

        NamedField readNamedField() throws MalformedEnvelope {
            var field = new NamedField(readUnsignedByte(), readUtf());
            accountNode();
            return field;
        }

        int readInt() throws MalformedEnvelope {
            requireRemaining(4);
            var value = (Byte.toUnsignedInt(bytes[position]) << 24)
                    | (Byte.toUnsignedInt(bytes[position + 1]) << 16)
                    | (Byte.toUnsignedInt(bytes[position + 2]) << 8)
                    | Byte.toUnsignedInt(bytes[position + 3]);
            consume(4);
            return value;
        }

        int readLength() throws MalformedEnvelope {
            var length = readInt();
            if (length < 0) {
                throw new MalformedEnvelope();
            }
            return length;
        }

        String readUtf() throws MalformedEnvelope {
            requireRemaining(2);
            var length = (Byte.toUnsignedInt(bytes[position]) << 8)
                    | Byte.toUnsignedInt(bytes[position + 1]);
            consume(2);
            if (length > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new MalformedEnvelope();
            }
            return new String(readBytes(length), StandardCharsets.UTF_8);
        }

        int[] readUuidArray() throws MalformedEnvelope {
            if (readLength() != 4) {
                throw new MalformedEnvelope();
            }
            return new int[] {readInt(), readInt(), readInt(), readInt()};
        }

        BlobListPreflight preflightBlobList(
                int nestedMaximum,
                NestedCapacityFactory capacityFactory) throws MalformedEnvelope {
            Objects.requireNonNull(capacityFactory, "capacityFactory");
            if (nestedMaximum <= 0) {
                throw new IllegalArgumentException("nestedMaximum must be positive");
            }
            requireByteArrayListHeader();
            var count = readPhysicalCount();
            var slices = new ArrayList<ByteSlice>();
            StorePersistenceFailure deferredCapacity = null;
            for (var index = 0; index < count; index++) {
                accountNode();
                var length = readLength();
                if (length > nestedMaximum && deferredCapacity == null) {
                    deferredCapacity = capacityFactory.create(length);
                }
                slices.add(preflightSlice(length));
            }
            return new BlobListPreflight(slices, java.util.Optional.ofNullable(deferredCapacity));
        }

        ByteSlice preflightSlice(int length) throws MalformedEnvelope {
            requireRemaining(length);
            var slice = new ByteSlice(position, length);
            consume(length);
            return slice;
        }

        byte[] copySlice(ByteSlice slice) throws MalformedEnvelope {
            Objects.requireNonNull(slice, "slice");
            if (slice.offset() > bytes.length
                    || slice.length() > bytes.length - slice.offset()) {
                throw new MalformedEnvelope();
            }
            return java.util.Arrays.copyOfRange(
                    bytes, slice.offset(), slice.offset() + slice.length());
        }

        byte[] readBytes(int length) throws MalformedEnvelope {
            requireRemaining(length);
            var result = java.util.Arrays.copyOfRange(bytes, position, position + length);
            consume(length);
            return result;
        }

        void requireFinished() throws MalformedEnvelope {
            if (position != bytes.length) {
                throw new MalformedEnvelope();
            }
        }

        private void requireByteArrayListHeader() throws MalformedEnvelope {
            if (readUnsignedByte() != TAG_BYTE_ARRAY) {
                throw new MalformedEnvelope();
            }
        }

        private int readPhysicalCount() throws MalformedEnvelope {
            var count = readInt();
            if (count < 0 || count > remaining() / 4) {
                throw new MalformedEnvelope();
            }
            return count;
        }

        private int remaining() {
            return bytes.length - position;
        }

        private void requireRemaining(int length) throws MalformedEnvelope {
            if (length < 0 || length > remaining()) {
                throw new MalformedEnvelope();
            }
        }

        private void consume(int length) {
            accounter.accountBytes(length);
            position += length;
        }

        private void accountNode() throws MalformedEnvelope {
            if (physicalNodeCount >= MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES) {
                throw new MalformedEnvelope();
            }
            physicalNodeCount++;
            accounter.accountBytes(QUOTA_NODE_BYTES);
        }
    }

    private static final class FramingOutput {
        private final byte[] bytes;
        private int position;

        FramingOutput(int size) {
            bytes = new byte[size];
        }

        void writeNamedInt(String name, int value) {
            writeByte(TAG_INT);
            writeUtf(name);
            writeInt(value);
        }

        void writeNamedString(String name, String value) {
            writeByte(TAG_STRING);
            writeUtf(name);
            writeUtf(value);
        }

        void writeNamedDocument(String name, EncodedSkillDocument value) {
            writeByte(TAG_BYTE_ARRAY);
            writeUtf(name);
            writeInt(value.byteCount());
            writeBytes(value);
        }

        void writeNamedIntArray(String name, int[] value) {
            writeByte(TAG_INT_ARRAY);
            writeUtf(name);
            writeInt(value.length);
            for (var element : value) {
                writeInt(element);
            }
        }

        List<BlobRange> writeNamedHistoryBlobList(
                String name,
                List<? extends HistoryBlobSource> values) {
            writeByte(TAG_LIST);
            writeUtf(name);
            writeByte(TAG_BYTE_ARRAY);
            writeInt(values.size());
            var ranges = new ArrayList<BlobRange>(values.size());
            for (var value : values) {
                writeInt(value.byteCount());
                var offset = (long) position;
                writeBytes(value);
                ranges.add(BlobRange.fromLong(offset, value.byteCount()));
            }
            return List.copyOf(ranges);
        }

        List<BlobRange> writeNamedRevisionBlobList(
                String name,
                List<? extends RevisionBlobSource> values) {
            writeByte(TAG_LIST);
            writeUtf(name);
            writeByte(TAG_BYTE_ARRAY);
            writeInt(values.size());
            var ranges = new ArrayList<BlobRange>(values.size());
            for (var value : values) {
                writeInt(value.byteCount());
                var offset = (long) position;
                writeBytes(value);
                ranges.add(BlobRange.fromLong(offset, value.byteCount()));
            }
            return List.copyOf(ranges);
        }

        void writeUtf(String value) {
            var encoded = value.getBytes(StandardCharsets.UTF_8);
            if (encoded.length > 65_535) {
                throw new IllegalArgumentException("UTF value is too long");
            }
            writeShort(encoded.length);
            writeBytes(encoded);
        }

        void writeByte(int value) {
            requireRemaining(1);
            bytes[position++] = (byte) value;
        }

        void writeInt(int value) {
            requireRemaining(4);
            bytes[position++] = (byte) (value >>> 24);
            bytes[position++] = (byte) (value >>> 16);
            bytes[position++] = (byte) (value >>> 8);
            bytes[position++] = (byte) value;
        }

        byte[] finish() {
            if (position != bytes.length) {
                throw new IllegalStateException("encoded size did not match preflight");
            }
            return bytes;
        }

        private void writeShort(int value) {
            requireRemaining(2);
            bytes[position++] = (byte) (value >>> 8);
            bytes[position++] = (byte) value;
        }

        private void writeBytes(byte[] value) {
            requireRemaining(value.length);
            System.arraycopy(value, 0, bytes, position, value.length);
            position += value.length;
        }

        private void writeBytes(HistoryBlobSource value) {
            requireRemaining(value.byteCount());
            value.copyInto(bytes, position);
            position += value.byteCount();
        }

        private void writeBytes(RevisionBlobSource value) {
            requireRemaining(value.byteCount());
            value.copyInto(bytes, position);
            position += value.byteCount();
        }

        private void writeBytes(EncodedSkillDocument value) {
            requireRemaining(value.byteCount());
            value.copyInto(bytes, position);
            position += value.byteCount();
        }

        private void requireRemaining(int length) {
            if (length < 0 || length > bytes.length - position) {
                throw new IllegalStateException("encoded size exceeded preflight");
            }
        }
    }

    private static final class MalformedEnvelope extends Exception {
    }

}
