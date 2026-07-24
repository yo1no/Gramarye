package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Immutable, deeply isolated raw skill-document tree retained only by the migration pipeline. */
final class RawSkillDocumentSnapshot {
    private final Dynamic<?> tree;
    private final SerializedTreeContext context;

    private RawSkillDocumentSnapshot(Dynamic<?> tree, SerializedTreeContext context) {
        this.tree = Objects.requireNonNull(tree, "tree");
        this.context = Objects.requireNonNull(context, "context");
    }

    /** Direct Java boundary. Invalid or unsupported input is reported as constructor misuse. */
    public static RawSkillDocumentSnapshot of(Dynamic<?> source) {
        var captured = capture(source);
        if (captured instanceof CaptureResult.Success success) {
            return success.snapshot();
        }
        var failure = ((CaptureResult.Failure) captured).failure();
        throw new IllegalArgumentException("Unable to snapshot skill document: " + failure.code());
    }

    /** Pipeline boundary. It never retains an unsupported or over-limit source reference. */
    public static CaptureResult capture(Dynamic<?> source) {
        if (source == null) {
            return new CaptureResult.Failure(
                    SkillMigrationFailure.of(SkillMigrationFailure.Code.UNSUPPORTED_RAW_FAMILY));
        }
        try {
            var bounds = DynamicTreeBounds.check(
                    source,
                    MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                    MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES);
            var boundFailure = failureFor(bounds);
            if (boundFailure != null) {
                return new CaptureResult.Failure(boundFailure);
            }
            var copyResult = SupportedDynamicTrees.defensiveCopy(source);
            if (copyResult.error().isPresent()) {
                return new CaptureResult.Failure(
                        SkillMigrationFailure.of(SkillMigrationFailure.Code.UNSUPPORTED_RAW_FAMILY));
            }
            var tree = copyResult.result().orElseThrow();
            var context = SupportedDynamicTrees.contextOf(tree).result().orElseThrow();
            return new CaptureResult.Success(new RawSkillDocumentSnapshot(tree, context));
        } catch (RuntimeException exception) {
            return new CaptureResult.Failure(SkillMigrationFailure.forSnapshotException(exception));
        }
    }

    private static SkillMigrationFailure failureFor(DynamicTreeBounds.Result bounds) {
        return switch (bounds) {
            case WITHIN_LIMITS -> null;
            case DEPTH_EXCEEDED -> SkillMigrationFailure.of(
                    SkillMigrationFailure.Code.GLOBAL_DEPTH_EXCEEDED);
            case NODE_COUNT_EXCEEDED -> SkillMigrationFailure.of(
                    SkillMigrationFailure.Code.GLOBAL_TREE_NODE_LIMIT_EXCEEDED);
            case KEY_LENGTH_EXCEEDED -> SkillMigrationFailure.of(
                    SkillMigrationFailure.Code.GLOBAL_KEY_LENGTH_EXCEEDED);
            case UNSUPPORTED -> SkillMigrationFailure.of(
                    SkillMigrationFailure.Code.UNSUPPORTED_RAW_FAMILY);
        };
    }

    /** Returns a new deep tree copy while retaining the exact DynamicOps instance. */
    public Dynamic<?> copyRawDocument() {
        return SupportedDynamicTrees.defensiveCopy(tree).getOrThrow();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RawSkillDocumentSnapshot snapshot
                        && context.family() == snapshot.context.family()
                        && tree.getValue().equals(snapshot.tree.getValue());
    }

    @Override
    public int hashCode() {
        var familySalt = context.family() == SerializedTreeFamily.JSON ? 31 : 37;
        return familySalt + tree.getValue().hashCode();
    }

    @Override
    public String toString() {
        return "RawSkillDocumentSnapshot[family=" + context.family().serializedName() + "]";
    }

    public sealed interface CaptureResult permits CaptureResult.Success, CaptureResult.Failure {
        record Success(RawSkillDocumentSnapshot snapshot) implements CaptureResult {
            public Success {
                Objects.requireNonNull(snapshot, "snapshot");
            }
        }

        record Failure(SkillMigrationFailure failure) implements CaptureResult {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }
}
