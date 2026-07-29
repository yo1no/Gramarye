package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Strict no-root-name framing and deterministic fixed-field writer for the pending journal. */
final class PendingAttachmentJournalFraming {
    private PendingAttachmentJournalFraming() {
    }

    static PendingAttachmentJournalLoadResult load(
            OpaquePendingAttachmentUpdatesBlob sourcePending) {
        return load(sourcePending, LoadObserver.NONE);
    }

    static PendingAttachmentJournalLoadResult loadBytesForTesting(
            byte[] sourceBytes, LoadObserver observer) {
        Objects.requireNonNull(sourceBytes, "sourceBytes");
        Objects.requireNonNull(observer, "observer");
        if (sourceBytes.length
                > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES) {
            return rejected(PendingAttachmentJournalFailure.capacity(
                    PendingAttachmentJournalFailure.Code.ENCODED_CAPACITY_EXCEEDED,
                    (long) MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES + 1,
                    MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES));
        }
        return load(OpaquePendingAttachmentUpdatesBlob.capture(sourceBytes), observer);
    }

    private static PendingAttachmentJournalLoadResult load(
            OpaquePendingAttachmentUpdatesBlob sourcePending, LoadObserver observer) {
        Objects.requireNonNull(sourcePending, "sourcePending");
        Objects.requireNonNull(observer, "observer");
        if (sourcePending.isEmpty()) {
            var encoded = new EncodedPendingAttachmentJournal(sourcePending, 0);
            return new PendingAttachmentJournalLoadResult.Loaded(
                    new PendingAttachmentJournalLoadCandidate(
                            PendingAttachmentJournal.empty(),
                            encoded,
                            sourcePending,
                            false));
        }

        var sourceBytes = sourcePending.copyBytes();
        observer.scannerInvoked();
        var scanned = PendingAttachmentJournalWireScan.scan(sourceBytes);
        if (scanned instanceof PendingAttachmentJournalWireScan.Result.Rejected rejected) {
            return rejected(rejected.failure());
        }
        var proof = (PendingAttachmentJournalWireScan.Result.Scanned) scanned;
        if (!proof.schemaSeen()) {
            return rejected(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.MISSING_FIELD,
                    PendingAttachmentJournalFailure.Stage.SCHEMA,
                    PendingAttachmentJournalFailure.Field.VERSION));
        }
        if (proof.schemaTagType() != Tag.TAG_INT) {
            return rejected(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.WRONG_TAG_TYPE,
                    PendingAttachmentJournalFailure.Stage.SCHEMA,
                    PendingAttachmentJournalFailure.Field.VERSION));
        }
        if (proof.schemaVersion() < 0) {
            return rejected(PendingAttachmentJournalFailure.simple(
                    PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA));
        }
        var migrationPlan = PendingAttachmentJournalMigrationPlans.production();
        if (proof.schemaVersion() != PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION) {
            return rejected(PendingAttachmentJournalFailure.simple(
                    proof.schemaVersion() < PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION
                            && migrationPlan.stepFrom(proof.schemaVersion()).isEmpty()
                            ? PendingAttachmentJournalFailure.Code.MISSING_MIGRATION_EDGE
                            : PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA));
        }

        var preflight = preflightCurrent(sourceBytes);
        if (preflight.isPresent()) {
            return rejected(preflight.orElseThrow());
        }

        PendingAttachmentJournalPhysicalV0 physical;
        try {
            physical = decodeCurrent(sourceBytes, observer);
        } catch (PhysicalDecodeException exception) {
            return rejected(exception.failure);
        }
        var admitted = PendingAttachmentJournal.admitPhysical(physical);
        if (admitted instanceof PendingAttachmentJournal.DomainAdmission.Rejected rejected) {
            return rejected(rejected.failure());
        }
        var admission = (PendingAttachmentJournal.DomainAdmission.Admitted) admitted;
        var encodedResult = encode(admission.journal());
        if (encodedResult instanceof JournalEncodingResult.Rejected rejected) {
            return rejected(rejected.failure());
        }
        var encoded = ((JournalEncodingResult.Encoded) encodedResult).journal();
        var sourceIsCanonical = sourcePending.contentEquals(encoded.pending());
        if (sourceIsCanonical) {
            encoded = new EncodedPendingAttachmentJournal(
                    sourcePending, admission.journal().entryCount());
        }
        var rewriteRequired = physical.entries().isEmpty()
                || admission.nonCanonicalOrder()
                || !sourceIsCanonical;
        return new PendingAttachmentJournalLoadResult.Loaded(
                new PendingAttachmentJournalLoadCandidate(
                        admission.journal(), encoded, sourcePending, rewriteRequired));
    }

    /**
     * Proves all current-V0 field names and tag types before the deferred raw-count gate.
     *
     * <p>The version-neutral scan has already proved complete framing, EOF, and the absence of
     * duplicate Compound fields. This pass deliberately does not decode UUID/reference values or
     * construct entry DTOs: those are later-precedence entry failures.</p>
     */
    private static Optional<PendingAttachmentJournalFailure> preflightCurrent(byte[] bytes) {
        var cursor = new PendingAttachmentJournalCursor(bytes);
        PendingAttachmentJournalFailure deferredCount = null;
        try {
            requireType(cursor.readUnsignedByte(), Tag.TAG_COMPOUND,
                    PendingAttachmentJournalFailure.Field.ROOT);
            var versionSeen = false;
            var entriesSeen = false;
            var fields = new HashSet<String>();
            while (true) {
                var type = cursor.readUnsignedByte();
                if (type == Tag.TAG_END) {
                    break;
                }
                var name = cursor.readModifiedUtf();
                requireUnique(fields, name, PendingAttachmentJournalFailure.Field.ROOT);
                switch (name) {
                    case PendingAttachmentJournalSchema.VERSION -> {
                        requireType(type, Tag.TAG_INT,
                                PendingAttachmentJournalFailure.Field.VERSION);
                        cursor.skip(Integer.BYTES);
                        versionSeen = true;
                    }
                    case PendingAttachmentJournalSchema.ENTRIES -> {
                        requireType(type, Tag.TAG_LIST,
                                PendingAttachmentJournalFailure.Field.ENTRIES);
                        var countFailure = preflightEntries(cursor);
                        if (deferredCount == null && countFailure.isPresent()) {
                            deferredCount = countFailure.orElseThrow();
                        }
                        entriesSeen = true;
                    }
                    default -> throw failure(PendingAttachmentJournalFailure.at(
                            PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                            PendingAttachmentJournalFailure.Stage.PHYSICAL,
                            PendingAttachmentJournalFailure.Field.ROOT));
                }
            }
            if (!cursor.finished()) {
                throw failure(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.TRAILING_DATA,
                        PendingAttachmentJournalFailure.Stage.FRAMING,
                        PendingAttachmentJournalFailure.Field.ROOT));
            }
            if (!versionSeen) {
                throw missing(PendingAttachmentJournalFailure.Field.VERSION);
            }
            if (!entriesSeen) {
                throw missing(PendingAttachmentJournalFailure.Field.ENTRIES);
            }
            return Optional.ofNullable(deferredCount);
        } catch (PhysicalDecodeException exception) {
            return Optional.of(exception.failure);
        } catch (MalformedWireException exception) {
            return Optional.of(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                    PendingAttachmentJournalFailure.Stage.FRAMING,
                    PendingAttachmentJournalFailure.Field.ROOT));
        }
    }

    private static Optional<PendingAttachmentJournalFailure> preflightEntries(
            PendingAttachmentJournalCursor cursor)
            throws MalformedWireException, PhysicalDecodeException {
        var elementType = cursor.readUnsignedByte();
        var count = cursor.readLength();
        if (count > 0 && elementType != Tag.TAG_COMPOUND
                || count == 0 && elementType != Tag.TAG_END) {
            throw wrong(PendingAttachmentJournalFailure.Field.ENTRIES);
        }
        PendingAttachmentJournalFailure deferredCount = count
                > PendingAttachmentJournalSchema.MAX_ENTRIES
                ? PendingAttachmentJournalFailure.capacity(
                        PendingAttachmentJournalFailure.Code.ENTRY_COUNT_EXCEEDED,
                        count,
                        PendingAttachmentJournalSchema.MAX_ENTRIES)
                : null;
        for (var index = 0; index < count; index++) {
            preflightEntry(cursor);
        }
        return Optional.ofNullable(deferredCount);
    }

    private static void preflightEntry(PendingAttachmentJournalCursor cursor)
            throws MalformedWireException, PhysicalDecodeException {
        var ownerSeen = false;
        var skillIdSeen = false;
        var expectedGenerationSeen = false;
        var targetGenerationSeen = false;
        var targetPointerSeen = false;
        var fields = new HashSet<String>();
        while (true) {
            var type = cursor.readUnsignedByte();
            if (type == Tag.TAG_END) {
                break;
            }
            var name = cursor.readModifiedUtf();
            requireUnique(fields, name, PendingAttachmentJournalFailure.Field.ENTRY);
            switch (name) {
                case PendingAttachmentJournalSchema.OWNER -> {
                    requireType(type, Tag.TAG_INT_ARRAY,
                            PendingAttachmentJournalFailure.Field.OWNER);
                    skipIntArray(cursor);
                    ownerSeen = true;
                }
                case PendingAttachmentJournalSchema.SKILL_ID -> {
                    requireType(type, Tag.TAG_INT_ARRAY,
                            PendingAttachmentJournalFailure.Field.SKILL_ID);
                    skipIntArray(cursor);
                    skillIdSeen = true;
                }
                case PendingAttachmentJournalSchema.EXPECTED_GENERATION -> {
                    requireType(type, Tag.TAG_INT,
                            PendingAttachmentJournalFailure.Field.EXPECTED_GENERATION);
                    cursor.skip(Integer.BYTES);
                    expectedGenerationSeen = true;
                }
                case PendingAttachmentJournalSchema.TARGET_GENERATION -> {
                    requireType(type, Tag.TAG_INT,
                            PendingAttachmentJournalFailure.Field.TARGET_GENERATION);
                    cursor.skip(Integer.BYTES);
                    targetGenerationSeen = true;
                }
                case PendingAttachmentJournalSchema.EXPECTED_POINTER -> {
                    requireType(type, Tag.TAG_COMPOUND,
                            PendingAttachmentJournalFailure.Field.EXPECTED_POINTER);
                    preflightReference(cursor, PendingAttachmentJournalFailure.Field.EXPECTED_POINTER);
                }
                case PendingAttachmentJournalSchema.TARGET_POINTER -> {
                    requireType(type, Tag.TAG_COMPOUND,
                            PendingAttachmentJournalFailure.Field.TARGET_POINTER);
                    preflightReference(cursor, PendingAttachmentJournalFailure.Field.TARGET_POINTER);
                    targetPointerSeen = true;
                }
                default -> throw failure(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                        PendingAttachmentJournalFailure.Stage.PHYSICAL,
                        PendingAttachmentJournalFailure.Field.ENTRY));
            }
        }
        if (!ownerSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.OWNER);
        }
        if (!skillIdSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.SKILL_ID);
        }
        if (!expectedGenerationSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.EXPECTED_GENERATION);
        }
        if (!targetGenerationSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.TARGET_GENERATION);
        }
        if (!targetPointerSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.TARGET_POINTER);
        }
    }

    private static void preflightReference(
            PendingAttachmentJournalCursor cursor,
            PendingAttachmentJournalFailure.Field enclosingField)
            throws MalformedWireException, PhysicalDecodeException {
        var skillIdSeen = false;
        var revisionSeen = false;
        var fields = new HashSet<String>();
        while (true) {
            var type = cursor.readUnsignedByte();
            if (type == Tag.TAG_END) {
                break;
            }
            var name = cursor.readModifiedUtf();
            requireUnique(fields, name, enclosingField);
            switch (name) {
                case PendingAttachmentJournalSchema.SKILL_ID -> {
                    requireType(type, Tag.TAG_STRING,
                            PendingAttachmentJournalFailure.Field.SKILL_ID);
                    cursor.readModifiedUtf();
                    skillIdSeen = true;
                }
                case PendingAttachmentJournalSchema.REVISION -> {
                    requireType(type, Tag.TAG_INT,
                            PendingAttachmentJournalFailure.Field.REVISION);
                    cursor.skip(Integer.BYTES);
                    revisionSeen = true;
                }
                default -> throw failure(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                        PendingAttachmentJournalFailure.Stage.PHYSICAL,
                        enclosingField));
            }
        }
        if (!skillIdSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.SKILL_ID);
        }
        if (!revisionSeen) {
            throw missing(PendingAttachmentJournalFailure.Field.REVISION);
        }
    }

    private static void skipIntArray(PendingAttachmentJournalCursor cursor)
            throws MalformedWireException {
        cursor.skipElements(cursor.readLength(), Integer.BYTES);
    }

    static JournalEncodingResult encode(PendingAttachmentJournal journal) {
        Objects.requireNonNull(journal, "journal");
        if (journal.entryCount() == 0) {
            return new JournalEncodingResult.Encoded(
                    new EncodedPendingAttachmentJournal(
                            OpaquePendingAttachmentUpdatesBlob.empty(), 0));
        }
        try {
            var counting = new CountingOutputStream(
                    MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES);
            try (var output = new DataOutputStream(counting)) {
                writeJournal(output, journal);
            }
            var byteCount = Math.toIntExact(counting.count());
            var bytes = new byte[byteCount];
            var fixed = new FixedOutputStream(bytes);
            try (var output = new DataOutputStream(fixed)) {
                writeJournal(output, journal);
            }
            if (!fixed.finished()) {
                throw new IllegalStateException("journal counting/write size mismatch");
            }
            return new JournalEncodingResult.Encoded(new EncodedPendingAttachmentJournal(
                    OpaquePendingAttachmentUpdatesBlob.capture(bytes), journal.entryCount()));
        } catch (CapacityException exception) {
            return new JournalEncodingResult.Rejected(PendingAttachmentJournalFailure.capacity(
                    PendingAttachmentJournalFailure.Code.ENCODED_CAPACITY_EXCEEDED,
                    (long) MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES + 1,
                    MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES));
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory journal writer failed", exception);
        }
    }

    private static PendingAttachmentJournalPhysicalV0 decodeCurrent(
            byte[] bytes, LoadObserver observer)
            throws PhysicalDecodeException {
        var cursor = new PendingAttachmentJournalCursor(bytes);
        try {
            requireType(cursor.readUnsignedByte(), Tag.TAG_COMPOUND,
                    PendingAttachmentJournalFailure.Field.ROOT);
            Integer version = null;
            ArrayList<PendingAttachmentJournalEntryPhysicalV0> entries = null;
            var fields = new HashSet<String>();
            while (true) {
                var type = cursor.readUnsignedByte();
                if (type == Tag.TAG_END) {
                    break;
                }
                var name = cursor.readModifiedUtf();
                requireUnique(fields, name, PendingAttachmentJournalFailure.Field.ROOT);
                switch (name) {
                    case PendingAttachmentJournalSchema.VERSION -> {
                        requireType(type, Tag.TAG_INT,
                                PendingAttachmentJournalFailure.Field.VERSION);
                        version = cursor.readInt();
                    }
                    case PendingAttachmentJournalSchema.ENTRIES -> {
                        requireType(type, Tag.TAG_LIST,
                                PendingAttachmentJournalFailure.Field.ENTRIES);
                        entries = readEntries(cursor, observer);
                    }
                    default -> throw failure(PendingAttachmentJournalFailure.at(
                            PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                            PendingAttachmentJournalFailure.Stage.PHYSICAL,
                            PendingAttachmentJournalFailure.Field.ROOT));
                }
            }
            if (!cursor.finished()) {
                throw failure(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.TRAILING_DATA,
                        PendingAttachmentJournalFailure.Stage.FRAMING,
                        PendingAttachmentJournalFailure.Field.ROOT));
            }
            if (version == null) {
                throw missing(PendingAttachmentJournalFailure.Field.VERSION);
            }
            if (entries == null) {
                throw missing(PendingAttachmentJournalFailure.Field.ENTRIES);
            }
            if (version != PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION) {
                throw failure(PendingAttachmentJournalFailure.simple(
                        PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA));
            }
            return new PendingAttachmentJournalPhysicalV0(version, entries);
        } catch (MalformedWireException exception) {
            throw failure(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                    PendingAttachmentJournalFailure.Stage.FRAMING,
                    PendingAttachmentJournalFailure.Field.ROOT));
        }
    }

    private static ArrayList<PendingAttachmentJournalEntryPhysicalV0> readEntries(
            PendingAttachmentJournalCursor cursor, LoadObserver observer)
            throws MalformedWireException, PhysicalDecodeException {
        var elementType = cursor.readUnsignedByte();
        var count = cursor.readLength();
        if (count > 0 && elementType != Tag.TAG_COMPOUND
                || count == 0 && elementType != Tag.TAG_END) {
            throw wrong(PendingAttachmentJournalFailure.Field.ENTRIES);
        }
        if (count > PendingAttachmentJournalSchema.MAX_ENTRIES) {
            throw failure(PendingAttachmentJournalFailure.capacity(
                    PendingAttachmentJournalFailure.Code.ENTRY_COUNT_EXCEEDED,
                    count,
                    PendingAttachmentJournalSchema.MAX_ENTRIES));
        }
        var entries = new ArrayList<PendingAttachmentJournalEntryPhysicalV0>(count);
        for (var index = 0; index < count; index++) {
            var entry = readEntry(cursor, index);
            observer.entryConstructed();
            entries.add(entry);
        }
        return entries;
    }

    private static PendingAttachmentJournalEntryPhysicalV0 readEntry(
            PendingAttachmentJournalCursor cursor, int index)
            throws MalformedWireException, PhysicalDecodeException {
        SkillOwnerId owner = null;
        SkillId skillId = null;
        Integer expectedGeneration = null;
        Integer targetGeneration = null;
        Optional<SkillReference> expectedPointer = Optional.empty();
        var expectedPointerSeen = false;
        SkillReference targetPointer = null;
        var fields = new HashSet<String>();
        while (true) {
            var type = cursor.readUnsignedByte();
            if (type == Tag.TAG_END) {
                break;
            }
            var name = cursor.readModifiedUtf();
            requireUnique(fields, name, PendingAttachmentJournalFailure.Field.ENTRY);
            switch (name) {
                case PendingAttachmentJournalSchema.OWNER -> {
                    requireType(type, Tag.TAG_INT_ARRAY,
                            PendingAttachmentJournalFailure.Field.OWNER);
                    owner = decodeOwner(cursor.readUuidArray());
                }
                case PendingAttachmentJournalSchema.SKILL_ID -> {
                    requireType(type, Tag.TAG_INT_ARRAY,
                            PendingAttachmentJournalFailure.Field.SKILL_ID);
                    skillId = decodeSkillId(cursor.readUuidArray());
                }
                case PendingAttachmentJournalSchema.EXPECTED_GENERATION -> {
                    requireType(type, Tag.TAG_INT,
                            PendingAttachmentJournalFailure.Field.EXPECTED_GENERATION);
                    expectedGeneration = cursor.readInt();
                }
                case PendingAttachmentJournalSchema.TARGET_GENERATION -> {
                    requireType(type, Tag.TAG_INT,
                            PendingAttachmentJournalFailure.Field.TARGET_GENERATION);
                    targetGeneration = cursor.readInt();
                }
                case PendingAttachmentJournalSchema.EXPECTED_POINTER -> {
                    requireType(type, Tag.TAG_COMPOUND,
                            PendingAttachmentJournalFailure.Field.EXPECTED_POINTER);
                    expectedPointer = Optional.of(readReference(cursor));
                    expectedPointerSeen = true;
                }
                case PendingAttachmentJournalSchema.TARGET_POINTER -> {
                    requireType(type, Tag.TAG_COMPOUND,
                            PendingAttachmentJournalFailure.Field.TARGET_POINTER);
                    targetPointer = readReference(cursor);
                }
                default -> throw failure(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                        PendingAttachmentJournalFailure.Stage.PHYSICAL,
                        PendingAttachmentJournalFailure.Field.ENTRY));
            }
        }
        if (owner == null) {
            throw missing(PendingAttachmentJournalFailure.Field.OWNER);
        }
        if (skillId == null) {
            throw missing(PendingAttachmentJournalFailure.Field.SKILL_ID);
        }
        if (expectedGeneration == null) {
            throw missing(PendingAttachmentJournalFailure.Field.EXPECTED_GENERATION);
        }
        if (targetGeneration == null) {
            throw missing(PendingAttachmentJournalFailure.Field.TARGET_GENERATION);
        }
        if (targetPointer == null) {
            throw missing(PendingAttachmentJournalFailure.Field.TARGET_POINTER);
        }
        return new PendingAttachmentJournalEntryPhysicalV0(
                owner, skillId, expectedGeneration, targetGeneration,
                expectedPointerSeen ? expectedPointer : Optional.empty(), targetPointer);
    }

    private static SkillReference readReference(PendingAttachmentJournalCursor cursor)
            throws MalformedWireException, PhysicalDecodeException {
        SkillId skillId = null;
        Integer revision = null;
        Set<String> fields = new HashSet<>();
        while (true) {
            var type = cursor.readUnsignedByte();
            if (type == Tag.TAG_END) {
                break;
            }
            var name = cursor.readModifiedUtf();
            requireUnique(fields, name, PendingAttachmentJournalFailure.Field.TARGET_POINTER);
            switch (name) {
                case PendingAttachmentJournalSchema.SKILL_ID -> {
                    requireType(type, Tag.TAG_STRING,
                            PendingAttachmentJournalFailure.Field.SKILL_ID);
                    var value = cursor.readModifiedUtf();
                    try {
                        var uuid = UUID.fromString(value);
                        if (!uuid.toString().equals(value)) {
                            throw new IllegalArgumentException();
                        }
                        skillId = new SkillId(uuid);
                    } catch (IllegalArgumentException exception) {
                        throw wrong(PendingAttachmentJournalFailure.Field.SKILL_ID);
                    }
                }
                case PendingAttachmentJournalSchema.REVISION -> {
                    requireType(type, Tag.TAG_INT,
                            PendingAttachmentJournalFailure.Field.REVISION);
                    revision = cursor.readInt();
                }
                default -> throw failure(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                        PendingAttachmentJournalFailure.Stage.PHYSICAL,
                        PendingAttachmentJournalFailure.Field.TARGET_POINTER));
            }
        }
        if (skillId == null) {
            throw missing(PendingAttachmentJournalFailure.Field.SKILL_ID);
        }
        if (revision == null) {
            throw missing(PendingAttachmentJournalFailure.Field.REVISION);
        }
        if (revision < 0) {
            throw wrong(PendingAttachmentJournalFailure.Field.REVISION);
        }
        var tag = new CompoundTag();
        tag.putString(PendingAttachmentJournalSchema.SKILL_ID,
                skillId.value().toString());
        tag.putInt(PendingAttachmentJournalSchema.REVISION, revision);
        return SkillReference.CODEC.parse(NbtOps.INSTANCE, tag)
                .result()
                .orElseThrow(() -> wrong(PendingAttachmentJournalFailure.Field.TARGET_POINTER));
    }

    private static SkillOwnerId decodeOwner(int[] value) throws PhysicalDecodeException {
        return SkillOwnerId.CODEC.parse(NbtOps.INSTANCE, new IntArrayTag(value))
                .result()
                .orElseThrow(() -> wrong(PendingAttachmentJournalFailure.Field.OWNER));
    }

    private static SkillId decodeSkillId(int[] value) throws PhysicalDecodeException {
        return SkillId.CODEC.parse(NbtOps.INSTANCE, new IntArrayTag(value))
                .result()
                .orElseThrow(() -> wrong(PendingAttachmentJournalFailure.Field.SKILL_ID));
    }

    private static void writeJournal(DataOutputStream output, PendingAttachmentJournal journal)
            throws IOException {
        output.writeByte(Tag.TAG_COMPOUND);
        writeNamedInt(output, PendingAttachmentJournalSchema.VERSION,
                PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION);
        output.writeByte(Tag.TAG_LIST);
        output.writeUTF(PendingAttachmentJournalSchema.ENTRIES);
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeInt(journal.entryCount());
        for (var entry : journal.entries()) {
            writeNamedUuid(output, PendingAttachmentJournalSchema.OWNER, entry.owner().value());
            writeNamedUuid(output, PendingAttachmentJournalSchema.SKILL_ID, entry.skillId().value());
            writeNamedInt(output, PendingAttachmentJournalSchema.EXPECTED_GENERATION,
                    entry.expectedAttachmentGeneration());
            writeNamedInt(output, PendingAttachmentJournalSchema.TARGET_GENERATION,
                    entry.targetAttachmentGeneration());
            if (entry.expectedPointer().isPresent()) {
                writeNamedReference(output, PendingAttachmentJournalSchema.EXPECTED_POINTER,
                        entry.expectedPointer().orElseThrow());
            }
            writeNamedReference(output, PendingAttachmentJournalSchema.TARGET_POINTER,
                    entry.targetPointer());
            output.writeByte(Tag.TAG_END);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeNamedInt(DataOutputStream output, String name, int value)
            throws IOException {
        output.writeByte(Tag.TAG_INT);
        output.writeUTF(name);
        output.writeInt(value);
    }

    private static void writeNamedUuid(DataOutputStream output, String name, UUID value)
            throws IOException {
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF(name);
        output.writeInt(4);
        output.writeInt((int) (value.getMostSignificantBits() >>> 32));
        output.writeInt((int) value.getMostSignificantBits());
        output.writeInt((int) (value.getLeastSignificantBits() >>> 32));
        output.writeInt((int) value.getLeastSignificantBits());
    }

    private static void writeNamedReference(
            DataOutputStream output, String name, SkillReference reference) throws IOException {
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeUTF(name);
        output.writeByte(Tag.TAG_STRING);
        output.writeUTF(PendingAttachmentJournalSchema.SKILL_ID);
        output.writeUTF(reference.skillId().value().toString());
        writeNamedInt(output, PendingAttachmentJournalSchema.REVISION,
                reference.revision().value());
        output.writeByte(Tag.TAG_END);
    }

    private static void requireType(int actual, int expected, PendingAttachmentJournalFailure.Field field)
            throws PhysicalDecodeException {
        if (actual != expected) {
            throw wrong(field);
        }
    }

    private static void requireUnique(Set<String> fields, String name,
            PendingAttachmentJournalFailure.Field field) throws PhysicalDecodeException {
        if (!fields.add(name)) {
            throw failure(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.DUPLICATE_PHYSICAL_FIELD,
                    PendingAttachmentJournalFailure.Stage.PHYSICAL, field));
        }
    }

    private static PhysicalDecodeException missing(PendingAttachmentJournalFailure.Field field) {
        return failure(PendingAttachmentJournalFailure.at(
                PendingAttachmentJournalFailure.Code.MISSING_FIELD,
                PendingAttachmentJournalFailure.Stage.PHYSICAL, field));
    }

    private static PhysicalDecodeException wrong(PendingAttachmentJournalFailure.Field field) {
        return failure(PendingAttachmentJournalFailure.at(
                PendingAttachmentJournalFailure.Code.WRONG_TAG_TYPE,
                PendingAttachmentJournalFailure.Stage.PHYSICAL, field));
    }

    private static PhysicalDecodeException failure(PendingAttachmentJournalFailure failure) {
        return new PhysicalDecodeException(failure);
    }

    private static PendingAttachmentJournalLoadResult.Rejected rejected(
            PendingAttachmentJournalFailure failure) {
        return new PendingAttachmentJournalLoadResult.Rejected(failure);
    }

    sealed interface JournalEncodingResult
            permits JournalEncodingResult.Encoded, JournalEncodingResult.Rejected {
        record Encoded(EncodedPendingAttachmentJournal journal)
                implements JournalEncodingResult {
            public Encoded {
                Objects.requireNonNull(journal, "journal");
            }
        }

        record Rejected(PendingAttachmentJournalFailure failure)
                implements JournalEncodingResult {
            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    interface LoadObserver {
        LoadObserver NONE = new LoadObserver() {
            @Override
            public void scannerInvoked() {
            }

            @Override
            public void entryConstructed() {
            }
        };

        void scannerInvoked();

        void entryConstructed();
    }

    private static final class CountingOutputStream extends OutputStream {
        private final long maximum;
        private long count;

        private CountingOutputStream(long maximum) {
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            add(1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            add(length);
        }

        private void add(int length) throws CapacityException {
            if (length < 0 || count > maximum - length) {
                count = maximum + 1;
                throw new CapacityException();
            }
            count += length;
        }

        private long count() {
            return count;
        }
    }

    private static final class FixedOutputStream extends OutputStream {
        private final byte[] bytes;
        private int position;

        private FixedOutputStream(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "bytes");
        }

        @Override
        public void write(int value) {
            if (position == bytes.length) {
                throw new IllegalStateException("journal writer exceeded counted size");
            }
            bytes[position++] = (byte) value;
        }

        @Override
        public void write(byte[] source, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, source.length);
            if (length > bytes.length - position) {
                throw new IllegalStateException("journal writer exceeded counted size");
            }
            System.arraycopy(source, offset, bytes, position, length);
            position += length;
        }

        private boolean finished() {
            return position == bytes.length;
        }
    }

    private static final class CapacityException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class PhysicalDecodeException extends Exception {
        private static final long serialVersionUID = 1L;
        private final PendingAttachmentJournalFailure failure;

        private PhysicalDecodeException(PendingAttachmentJournalFailure failure) {
            this.failure = Objects.requireNonNull(failure, "failure");
        }
    }
}

/** Canonical encoded form owning only the one opaque pending handle. */
final class EncodedPendingAttachmentJournal {
    private final OpaquePendingAttachmentUpdatesBlob pending;
    private final int entryCount;

    EncodedPendingAttachmentJournal(
            OpaquePendingAttachmentUpdatesBlob pending, int entryCount) {
        this.pending = Objects.requireNonNull(pending, "pending");
        if (entryCount < 0 || entryCount > PendingAttachmentJournalSchema.MAX_ENTRIES
                || pending.isEmpty() != (entryCount == 0)) {
            throw new IllegalArgumentException("encoded journal count/zero invariant failed");
        }
        this.entryCount = entryCount;
    }

    OpaquePendingAttachmentUpdatesBlob pending() {
        return pending;
    }

    int entryCount() {
        return entryCount;
    }

    int byteCount() {
        return pending.byteCount();
    }

    boolean zero() {
        return pending.isEmpty();
    }

    @Override
    public String toString() {
        return "EncodedPendingAttachmentJournal[entryCount=" + entryCount
                + ", byteCount=" + byteCount() + ", zero=" + zero() + ']';
    }
}

sealed interface PendingAttachmentJournalLoadResult
        permits PendingAttachmentJournalLoadResult.Loaded,
                PendingAttachmentJournalLoadResult.Rejected {
    record Loaded(PendingAttachmentJournalLoadCandidate candidate)
            implements PendingAttachmentJournalLoadResult {
        public Loaded {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record Rejected(PendingAttachmentJournalFailure failure)
            implements PendingAttachmentJournalLoadResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

record PendingAttachmentJournalLoadCandidate(
        PendingAttachmentJournal journal,
        EncodedPendingAttachmentJournal encoded,
        OpaquePendingAttachmentUpdatesBlob sourcePending,
        boolean rewriteRequired) {
    PendingAttachmentJournalLoadCandidate {
        Objects.requireNonNull(journal, "journal");
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(sourcePending, "sourcePending");
        if (journal.entryCount() != encoded.entryCount()) {
            throw new IllegalArgumentException("loaded journal count mismatch");
        }
    }
}
