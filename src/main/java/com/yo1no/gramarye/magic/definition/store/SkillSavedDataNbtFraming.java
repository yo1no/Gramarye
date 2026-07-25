package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.Tag;

/** Strict pre-materialization reader for one decompressed SavedData whole root. */
final class SkillSavedDataNbtFraming {
    private static final int READ_BUFFER_BYTES = 8_192;

    private SkillSavedDataNbtFraming() {
    }

    static FramingResult<ParsedSavedDataEnvelope> decodeWholeRoot(InputStream decompressed) {
        Objects.requireNonNull(decompressed, "decompressed");
        var captured = captureWholeRoot(decompressed);
        if (captured.failureValue().isPresent()) {
            return failure(captured.failureValue().orElseThrow());
        }
        var bytes = captured.successValue().orElseThrow();
        try {
            var input = new StrictNbtFramingInput(
                    bytes, SkillSavedDataPersistenceSchema.FINITE_WHOLE_ROOT_NBT_QUOTA);
            input.requireUnnamedRootCompound();

            Integer dataVersion = null;
            ParsedInner inner = null;
            var outerFields = new HashSet<String>();
            while (!input.atCompoundEnd()) {
                var field = input.readNamedField();
                requireNewField(outerFields, field.name());
                switch (field.name()) {
                    case SkillSavedDataPersistenceSchema.DATA_FIELD -> {
                        requireType(field.type(), Tag.TAG_COMPOUND);
                        inner = readInner(input);
                    }
                    case SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD -> {
                        requireType(field.type(), Tag.TAG_INT);
                        dataVersion = input.readInt();
                    }
                    default -> throw new StrictNbtFramingInput.MalformedNbtException();
                }
            }
            input.requireFinished();
            if (dataVersion == null || inner == null || outerFields.size() != 2) {
                throw new StrictNbtFramingInput.MalformedNbtException();
            }
            if (inner.encodedByteCount()
                    > MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES) {
                return failure(new SkillSavedDataCarrierFailure.SavedDataCarrierCapacityExceeded(
                        inner.encodedByteCount(),
                        MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES));
            }
            if (inner.pendingSlice().length()
                    > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES) {
                return failure(
                        new SkillSavedDataCarrierFailure.PendingAttachmentUpdatesCapacityExceeded(
                                inner.pendingSlice().length(),
                                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES));
            }
            if (inner.storeSlice().length()
                    > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
                return failure(new SkillSavedDataCarrierFailure.StoreLoadFailed(
                        new StorePersistenceFailure.StoreBlobEncodedCapacityExceeded(
                                inner.storeSlice().length(),
                                MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES)));
            }

            input.verifyFiniteMaterializationQuota();
            var storeBytes = input.copySlice(inner.storeSlice());
            var pendingBytes = input.copySlice(inner.pendingSlice());
            if (storeBytes.length == 0) {
                return failure(new SkillSavedDataCarrierFailure.StoreLoadFailed(
                        StorePersistenceFailure.MalformedStoreEnvelope.INSTANCE));
            }
            return success(new ParsedSavedDataEnvelope(
                    inner.schemaVersion(),
                    ImmutableStoreBlob.takeOwnership(storeBytes),
                    OpaquePendingAttachmentUpdatesBlob.capture(pendingBytes),
                    inner.encodedByteCount()));
        } catch (StrictNbtFramingInput.MalformedNbtException exception) {
            return failure(new SkillSavedDataCarrierFailure.MalformedSavedDataEnvelope(
                    SkillSavedDataCarrierFailure.EnvelopeStage.WHOLE_ROOT));
        } catch (RuntimeException exception) {
            return failure(SkillSavedDataCarrierFailure.InternalCodecException.from(
                    SkillSavedDataCarrierFailure.EnvelopeStage.WHOLE_ROOT, exception));
        }
    }

    /** Phase-local seam that exercises the standalone unnamed inner-carrier byte coordinate. */
    static FramingResult<ParsedSavedDataEnvelope> decodeInnerCarrier(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES) {
            return failure(new SkillSavedDataCarrierFailure.SavedDataCarrierCapacityExceeded(
                    bytes.length,
                    MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES));
        }
        try {
            var input = new StrictNbtFramingInput(bytes);
            input.requireUnnamedRootCompound();
            var inner = readInner(input);
            input.requireFinished();
            if (inner.encodedByteCount() != bytes.length) {
                throw new StrictNbtFramingInput.MalformedNbtException();
            }
            if (inner.pendingSlice().length()
                    > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES) {
                return failure(
                        new SkillSavedDataCarrierFailure.PendingAttachmentUpdatesCapacityExceeded(
                                inner.pendingSlice().length(),
                                MagicSafetyCeilings
                                        .MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES));
            }
            if (inner.storeSlice().length()
                    > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
                return failure(new SkillSavedDataCarrierFailure.StoreLoadFailed(
                        new StorePersistenceFailure.StoreBlobEncodedCapacityExceeded(
                                inner.storeSlice().length(),
                                MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES)));
            }
            var storeBytes = input.copySlice(inner.storeSlice());
            if (storeBytes.length == 0) {
                return failure(new SkillSavedDataCarrierFailure.StoreLoadFailed(
                        StorePersistenceFailure.MalformedStoreEnvelope.INSTANCE));
            }
            return success(new ParsedSavedDataEnvelope(
                    inner.schemaVersion(),
                    ImmutableStoreBlob.takeOwnership(storeBytes),
                    OpaquePendingAttachmentUpdatesBlob.capture(
                            input.copySlice(inner.pendingSlice())),
                    inner.encodedByteCount()));
        } catch (StrictNbtFramingInput.MalformedNbtException exception) {
            return failure(new SkillSavedDataCarrierFailure.MalformedSavedDataEnvelope(
                    SkillSavedDataCarrierFailure.EnvelopeStage.INNER_CARRIER));
        } catch (RuntimeException exception) {
            return failure(SkillSavedDataCarrierFailure.InternalCodecException.from(
                    SkillSavedDataCarrierFailure.EnvelopeStage.INNER_CARRIER, exception));
        }
    }

    private static ParsedInner readInner(StrictNbtFramingInput input)
            throws StrictNbtFramingInput.MalformedNbtException {
        var payloadStart = input.position();
        Integer schemaVersion = null;
        StrictNbtFramingInput.ByteSlice storeSlice = null;
        StrictNbtFramingInput.ByteSlice pendingSlice = null;
        var fields = new HashSet<String>();
        while (!input.atCompoundEnd()) {
            var field = input.readNamedField();
            requireNewField(fields, field.name());
            switch (field.name()) {
                case SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD -> {
                    requireType(field.type(), Tag.TAG_INT);
                    schemaVersion = input.readInt();
                }
                case SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD -> {
                    requireType(field.type(), Tag.TAG_BYTE_ARRAY);
                    storeSlice = input.preflightSlice(input.readLength());
                }
                case SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD -> {
                    requireType(field.type(), Tag.TAG_BYTE_ARRAY);
                    pendingSlice = input.preflightSlice(input.readLength());
                }
                default -> throw new StrictNbtFramingInput.MalformedNbtException();
            }
        }
        if (schemaVersion == null || schemaVersion < 0 || storeSlice == null
                || pendingSlice == null || fields.size() != 3) {
            throw new StrictNbtFramingInput.MalformedNbtException();
        }
        var encodedByteCount = Math.addExact(1, input.position() - payloadStart);
        return new ParsedInner(schemaVersion, storeSlice, pendingSlice, encodedByteCount);
    }

    private static FramingResult<byte[]> captureWholeRoot(InputStream input) {
        var output = new ByteArrayOutputStream(READ_BUFFER_BYTES);
        var buffer = new byte[READ_BUFFER_BYTES];
        var overCapacity = false;
        try {
            for (int count; (count = input.read(buffer)) != -1; ) {
                if (count == 0) {
                    continue;
                }
                if (!overCapacity) {
                    var remaining = SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES
                            + 1 - output.size();
                    var retained = Math.min(count, Math.max(remaining, 0));
                    output.write(buffer, 0, retained);
                    overCapacity = retained < count
                            || output.size()
                            > SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES;
                }
            }
        } catch (IOException exception) {
            return failure(new SkillSavedDataCarrierFailure.InternalCodecException(
                    SkillSavedDataCarrierFailure.EnvelopeStage.WHOLE_ROOT,
                    exception.getClass().getName()));
        }
        if (overCapacity) {
            return failure(
                    new SkillSavedDataCarrierFailure.DecompressedWholeRootCapacityExceeded(
                            (long) SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES
                                    + 1,
                            SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES));
        }
        return success(output.toByteArray());
    }

    private static void requireNewField(HashSet<String> fields, String name)
            throws StrictNbtFramingInput.MalformedNbtException {
        if (!fields.add(name)) {
            throw new StrictNbtFramingInput.MalformedNbtException();
        }
    }

    private static void requireType(int actual, int expected)
            throws StrictNbtFramingInput.MalformedNbtException {
        if (actual != expected) {
            throw new StrictNbtFramingInput.MalformedNbtException();
        }
    }

    private record ParsedInner(
            int schemaVersion,
            StrictNbtFramingInput.ByteSlice storeSlice,
            StrictNbtFramingInput.ByteSlice pendingSlice,
            int encodedByteCount) {
    }

    record ParsedSavedDataEnvelope(
            int schemaVersion,
            ImmutableStoreBlob storeBlob,
            OpaquePendingAttachmentUpdatesBlob pending,
            int innerEncodedByteCount) {
        ParsedSavedDataEnvelope {
            if (schemaVersion < 0 || innerEncodedByteCount <= 0) {
                throw new IllegalArgumentException("parsed SavedData metadata is invalid");
            }
            Objects.requireNonNull(storeBlob, "storeBlob");
            Objects.requireNonNull(pending, "pending");
        }
    }

    sealed interface FramingResult<T> permits FramingResult.Success, FramingResult.Failure {
        Optional<T> successValue();

        Optional<SkillSavedDataCarrierFailure> failureValue();

        record Success<T>(T value) implements FramingResult<T> {
            public Success {
                Objects.requireNonNull(value, "value");
            }

            @Override
            public Optional<T> successValue() {
                return Optional.of(value);
            }

            @Override
            public Optional<SkillSavedDataCarrierFailure> failureValue() {
                return Optional.empty();
            }
        }

        record Failure<T>(SkillSavedDataCarrierFailure failure) implements FramingResult<T> {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }

            @Override
            public Optional<T> successValue() {
                return Optional.empty();
            }

            @Override
            public Optional<SkillSavedDataCarrierFailure> failureValue() {
                return Optional.of(failure);
            }
        }
    }

    private static <T> FramingResult<T> success(T value) {
        return new FramingResult.Success<>(value);
    }

    private static <T> FramingResult<T> failure(SkillSavedDataCarrierFailure failure) {
        return new FramingResult.Failure<>(failure);
    }
}
