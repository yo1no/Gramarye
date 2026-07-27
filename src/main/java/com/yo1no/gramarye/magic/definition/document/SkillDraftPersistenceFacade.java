package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;

/** The sole player-Attachment seam for bounded, always-migrating SkillDraft persistence. */
public final class SkillDraftPersistenceFacade {
    private SkillDraftPersistenceFacade() {
    }

    public static EncodeResult encodeCurrent(SkillDraft draft) {
        Objects.requireNonNull(draft, "draft");
        return SkillDraftPersistenceBridge.encodeCurrent(draft);
    }

    public static LoadResult loadAlwaysMigrating(
            EncodedSkillDraft encoded,
            Optional<HolderLookup.Provider> provider) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(provider, "provider");
        return SkillDraftPersistenceBridge.loadAlwaysMigrating(encoded, provider);
    }

    /** Opaque, deeply isolated Draft bytes plus their independent physical-encoding identity. */
    public static final class EncodedSkillDraft {
        public static final String CURRENT_ENCODING = "family_tagged_subtrees_v0";

        private final String draftEncoding;
        private final ImmutableEncodedBytes bytes;

        private EncodedSkillDraft(String draftEncoding, ImmutableEncodedBytes bytes) {
            this.draftEncoding = requireEncoding(draftEncoding);
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            if (bytes.size() == 0 || bytes.size() > maximumEncodedBytes()) {
                throw new IllegalArgumentException("encoded Draft bytes are outside the hard range");
            }
        }

        /**
         * Admits a materialized ByteArrayTag payload without invoking its copying accessor before the
         * hard length gate. The supplier must return exactly the declared number of bytes.
         */
        public static CaptureResult capturePersisted(
                String draftEncoding,
                int declaredByteCount,
                Supplier<byte[]> copySource) {
            requireEncoding(draftEncoding);
            Objects.requireNonNull(copySource, "copySource");
            if (declaredByteCount < 0) {
                throw new IllegalArgumentException("declaredByteCount must be non-negative");
            }
            if (declaredByteCount > maximumEncodedBytes()) {
                return new CaptureRejected(new CapacityFailure(
                        FailureCode.DRAFT_ENTRY_CAPACITY_EXCEEDED,
                        declaredByteCount,
                        maximumEncodedBytes()));
            }
            if (declaredByteCount == 0) {
                return new CaptureRejected(new SimpleFailure(FailureCode.DRAFT_DECODE_FAILED));
            }
            try {
                var copied = Objects.requireNonNull(copySource.get(), "copySource result");
                if (copied.length != declaredByteCount) {
                    return new CaptureRejected(new SimpleFailure(
                            FailureCode.ENCODED_LENGTH_MISMATCH));
                }
                return new Captured(new EncodedSkillDraft(
                        draftEncoding,
                        ImmutableEncodedBytes.copyOf(copied)));
            } catch (RuntimeException exception) {
                return new CaptureRejected(CodecFailure.from(
                        FailureCode.INTERNAL_CODEC_EXCEPTION,
                        exception));
            }
        }

        static EncodedSkillDraft takeCurrentOwnership(byte[] bytes) {
            return new EncodedSkillDraft(
                    CURRENT_ENCODING,
                    ImmutableEncodedBytes.takeOwnership(bytes));
        }

        static EncodedSkillDraft copyInternal(String encoding, ImmutableEncodedBytes bytes) {
            return new EncodedSkillDraft(
                    encoding,
                    ImmutableEncodedBytes.copyOf(bytes.copyBytes()));
        }

        static int maximumEncodedBytes() {
            return MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES;
        }

        ImmutableEncodedBytes copyInternalBytes() {
            return ImmutableEncodedBytes.copyOf(bytes.copyBytes());
        }

        public String draftEncoding() {
            return draftEncoding;
        }

        public int byteCount() {
            return bytes.size();
        }

        public byte[] copyBytes() {
            return bytes.copyBytes();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof EncodedSkillDraft encoded
                            && draftEncoding.equals(encoded.draftEncoding)
                            && bytes.equals(encoded.bytes);
        }

        @Override
        public int hashCode() {
            return 31 * draftEncoding.hashCode() + bytes.hashCode();
        }

        @Override
        public String toString() {
            return "EncodedSkillDraft[currentEncoding="
                    + CURRENT_ENCODING.equals(draftEncoding)
                    + ", byteCount=" + bytes.size() + "]";
        }

        private static String requireEncoding(String encoding) {
            Objects.requireNonNull(encoding, "draftEncoding");
            if (encoding.isEmpty()
                    || encoding.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("draftEncoding is outside the hard range");
            }
            return encoding;
        }
    }

    public sealed interface CaptureResult permits Captured, CaptureRejected {
    }

    public record Captured(EncodedSkillDraft draft) implements CaptureResult {
        public Captured {
            Objects.requireNonNull(draft, "draft");
        }
    }

    public record CaptureRejected(Failure failure) implements CaptureResult {
        public CaptureRejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public sealed interface EncodeResult permits Encoded, EncodeRejected {
    }

    public record Encoded(EncodedSkillDraft draft) implements EncodeResult {
        public Encoded {
            Objects.requireNonNull(draft, "draft");
        }
    }

    public record EncodeRejected(Failure failure) implements EncodeResult {
        public EncodeRejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public sealed interface LoadResult permits Loaded, LoadRejected {
    }

    public record Loaded(
            SkillDraft draft,
            boolean physicalMigrated,
            boolean logicalMigrated) implements LoadResult {
        public Loaded {
            Objects.requireNonNull(draft, "draft");
        }
    }

    public record LoadRejected(Failure failure) implements LoadResult {
        public LoadRejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public sealed interface Failure permits SimpleFailure, CapacityFailure, CodecFailure {
        FailureCode code();
    }

    public record SimpleFailure(FailureCode code) implements Failure {
        public SimpleFailure {
            Objects.requireNonNull(code, "code");
        }
    }

    public record CapacityFailure(
            FailureCode code,
            long observedAtLeast,
            long maximum) implements Failure {
        public CapacityFailure {
            Objects.requireNonNull(code, "code");
            if (code != FailureCode.DRAFT_ENTRY_CAPACITY_EXCEEDED
                    || maximum <= 0
                    || observedAtLeast <= maximum) {
                throw new IllegalArgumentException("invalid Draft capacity failure");
            }
        }
    }

    public record CodecFailure(FailureCode code, String exceptionClassName) implements Failure {
        private static final int MAX_CLASS_NAME_LENGTH = 192;

        public CodecFailure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(exceptionClassName, "exceptionClassName");
            if (exceptionClassName.isEmpty()
                    || exceptionClassName.length() > MAX_CLASS_NAME_LENGTH) {
                throw new IllegalArgumentException("invalid bounded exception class name");
            }
        }

        static CodecFailure from(FailureCode code, RuntimeException exception) {
            var name = exception.getClass().getName();
            if (name.length() > MAX_CLASS_NAME_LENGTH) {
                name = name.substring(0, MAX_CLASS_NAME_LENGTH);
            }
            return new CodecFailure(code, name);
        }
    }

    public enum FailureCode {
        ENCODE_REJECTED,
        DRAFT_ENTRY_CAPACITY_EXCEEDED,
        ENCODED_LENGTH_MISMATCH,
        DRAFT_PHYSICAL_MIGRATION_FAILED,
        DRAFT_LOGICAL_MIGRATION_FAILED,
        DRAFT_DECODE_FAILED,
        DRAFT_ROUTE_MISMATCH,
        OPAQUE_DRAFT_RAW_INVARIANT_VIOLATION,
        INTERNAL_CODEC_EXCEPTION
    }
}
