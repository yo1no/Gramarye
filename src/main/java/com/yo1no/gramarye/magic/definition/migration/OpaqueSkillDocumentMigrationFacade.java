package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.TokenizedSkillDocumentMigrationInput;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * The sole public persistence adapter into P3 skill-document migration. It accepts only a bounded,
 * raw-free tokenized logical-document representation and always uses the immutable production
 * migration plan.
 */
public final class OpaqueSkillDocumentMigrationFacade {
    private static final long ENCODED_BYTE_QUOTA_MULTIPLIER = 2;
    private static final long NODE_QUOTA_BYTES = 65;

    private OpaqueSkillDocumentMigrationFacade() {
    }

    /** Migrates a tokenized logical document to the current production skill schema. */
    public static Result migrate(TokenizedSkillDocumentMigrationInput source) {
        Objects.requireNonNull(source, "source");
        return migrateCaptured(
                source.copyBytes(),
                SkillMigrationPlans.production(),
                SkillDocument.CURRENT_SCHEMA_VERSION);
    }

    /** Package-private fixture seam; production callers cannot supply a plan or target version. */
    static Result migrateTo(
            byte[] source,
            SkillMigrationPlan plan,
            int currentSchemaVersion) {
        Objects.requireNonNull(source, "source");
        if (source.length == 0
                || source.length > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
            throw new IllegalArgumentException(
                    "tokenized migration input byte count is outside the hard ceiling");
        }
        return migrateCaptured(source.clone(), plan, currentSchemaVersion);
    }

    private static Result migrateCaptured(
            byte[] source,
            SkillMigrationPlan plan,
            int currentSchemaVersion) {
        Objects.requireNonNull(plan, "plan");
        if (currentSchemaVersion < 0) {
            throw new IllegalArgumentException("currentSchemaVersion must be non-negative");
        }

        var decoded = decode(source);
        if (decoded.isEmpty()) {
            return Failure.transport(FailureCode.MALFORMED_TOKENIZED_DOCUMENT);
        }
        var captured = RawSkillDocumentSnapshot.capture(decoded.orElseThrow());
        if (captured instanceof RawSkillDocumentSnapshot.CaptureResult.Failure failed) {
            return Failure.migration(failed.failure(), emptyFacts());
        }
        var original = ((RawSkillDocumentSnapshot.CaptureResult.Success) captured).snapshot();
        var initialProbe = SkillSchemaVersionProbe.probe(original);
        if (initialProbe instanceof SkillSchemaVersionProbe.Result.Failure failed) {
            return Failure.migration(failed.failure(), emptyFacts());
        }
        var initialVersion = ((SkillSchemaVersionProbe.Result.Success) initialProbe).schemaVersion();

        var migrated = SkillDocumentMigrator.migrateTo(original, plan, currentSchemaVersion);
        if (migrated instanceof SkillMigrationResult.Failure failed) {
            return Failure.migration(failed.failure(), failed.factReport());
        }
        var success = (SkillMigrationResult.Success) migrated;
        var encoded = encode(success.migratedSnapshot());
        if (encoded.isEmpty()) {
            return new Failure(
                    FailureCode.TOKENIZED_DOCUMENT_ENCODE_FAILED,
                    Optional.empty(),
                    success.factReport());
        }
        return new Success(
                encoded.orElseThrow(),
                success.factReport(),
                initialVersion != currentSchemaVersion);
    }

    private static Optional<Dynamic<?>> decode(byte[] source) {
        try (var input = new DataInputStream(new ByteArrayInputStream(source))) {
            var quota = Math.addExact(
                    Math.multiplyExact(ENCODED_BYTE_QUOTA_MULTIPLIER, source.length),
                    Math.multiplyExact(
                            NODE_QUOTA_BYTES,
                            (long) MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES));
            var value = NbtIo.readAnyTag(
                    input,
                    new NbtAccounter(quota, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH));
            if (input.read() != -1) {
                return Optional.empty();
            }
            return Optional.of(new Dynamic<>(NbtOps.INSTANCE, value));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<MigratedTokenizedDocument> encode(RawSkillDocumentSnapshot snapshot) {
        var dynamic = snapshot.copyRawDocument();
        // Exact Ops identity was already enforced by SkillDocumentMigrator against the NBT input.
        if (!(dynamic.getValue() instanceof Tag value)) {
            return Optional.empty();
        }
        try {
            var output = new TokenizedDocumentOutput(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
            var data = new DataOutputStream(output);
            NbtIo.writeAnyTag(value, data);
            data.flush();
            return Optional.of(MigratedTokenizedDocument.takeOwnership(output.toByteArray()));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static PipelineFactReport emptyFacts() {
        return new PipelineFactReport(List.of(), false);
    }

    /** Defensive encoded-NBT handle returned only after migration has completed. */
    public static final class MigratedTokenizedDocument {
        private final byte[] bytes;

        private MigratedTokenizedDocument(byte[] bytes, boolean takeOwnership) {
            Objects.requireNonNull(bytes, "bytes");
            if (bytes.length == 0
                    || bytes.length > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
                throw new IllegalArgumentException("tokenized document byte count is outside the hard ceiling");
            }
            this.bytes = takeOwnership ? bytes : bytes.clone();
        }

        /** Takes a defensive snapshot of bounded tokenized-NBT bytes. */
        public static MigratedTokenizedDocument copyOf(byte[] bytes) {
            return new MigratedTokenizedDocument(bytes, false);
        }

        private static MigratedTokenizedDocument takeOwnership(byte[] bytes) {
            return new MigratedTokenizedDocument(bytes, true);
        }

        public int byteCount() {
            return bytes.length;
        }

        /** Returns a fresh copy; the internal encoded tree is never exposed. */
        public byte[] copyBytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof MigratedTokenizedDocument document
                            && Arrays.equals(bytes, document.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "MigratedTokenizedDocument[byteCount=" + bytes.length + "]";
        }
    }

    public sealed interface Result permits Success, Failure {
    }

    public record Success(
            MigratedTokenizedDocument migratedDocument,
            PipelineFactReport factReport,
            boolean migrated) implements Result {
        public Success {
            Objects.requireNonNull(migratedDocument, "migratedDocument");
            Objects.requireNonNull(factReport, "factReport");
        }
    }

    public record Failure(
            FailureCode code,
            Optional<SkillMigrationFailure> migrationFailure,
            PipelineFactReport factReport) implements Result {
        public Failure {
            Objects.requireNonNull(code, "code");
            migrationFailure = Objects.requireNonNull(migrationFailure, "migrationFailure");
            Objects.requireNonNull(factReport, "factReport");
            if ((code == FailureCode.SKILL_MIGRATION_FAILED) != migrationFailure.isPresent()) {
                throw new IllegalArgumentException("migration failure presence must match the failure code");
            }
        }

        private static Failure transport(FailureCode code) {
            return new Failure(code, Optional.empty(), emptyFacts());
        }

        private static Failure migration(
                SkillMigrationFailure failure,
                PipelineFactReport facts) {
            return new Failure(
                    FailureCode.SKILL_MIGRATION_FAILED,
                    Optional.of(Objects.requireNonNull(failure, "failure")),
                    facts);
        }
    }

    public enum FailureCode {
        MALFORMED_TOKENIZED_DOCUMENT,
        TOKENIZED_DOCUMENT_ENCODE_FAILED,
        SKILL_MIGRATION_FAILED
    }

    /** Fixed-purpose bounded sink for the transient tokenized-NBT transport only. */
    private static final class TokenizedDocumentOutput extends OutputStream {
        private final int maximum;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int count;

        private TokenizedDocumentOutput(int maximum) {
            if (maximum <= 0) {
                throw new IllegalArgumentException("maximum must be positive");
            }
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            output.write(value);
        }

        @Override
        public void write(byte[] source, int offset, int length) throws IOException {
            Objects.requireNonNull(source, "source");
            Objects.checkFromIndexSize(offset, length, source.length);
            reserve(length);
            output.write(source, offset, length);
        }

        private void reserve(int length) throws IOException {
            if (length < 0 || length > maximum - count) {
                throw new IOException("tokenized document capacity exceeded");
            }
            count += length;
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
