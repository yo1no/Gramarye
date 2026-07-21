package com.yo1no.gramarye.magic.definition.migration;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

/** Immutable, deeply isolated raw skill-document tree retained only by the migration pipeline. */
final class RawSkillDocumentSnapshot {
    private final SnapshotTree tree;

    private RawSkillDocumentSnapshot(SnapshotTree tree) {
        this.tree = Objects.requireNonNull(tree, "tree");
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
            var treeResult = createTree(source);
            if (treeResult instanceof TreeCapture.Failure failure) {
                return new CaptureResult.Failure(failure.failure());
            }
            var tree = ((TreeCapture.Success) treeResult).tree();
            return new CaptureResult.Success(new RawSkillDocumentSnapshot(tree));
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
        return tree.copyDynamic();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RawSkillDocumentSnapshot snapshot && tree.structurallyEquals(snapshot.tree);
    }

    @Override
    public int hashCode() {
        return tree.structuralHashCode();
    }

    @Override
    public String toString() {
        return "RawSkillDocumentSnapshot[family=" + tree.familyName() + "]";
    }

    private static TreeCapture createTree(Dynamic<?> source) {
        var ops = source.getOps();
        var value = source.getValue();
        if (value instanceof JsonElement jsonValue) {
            if (ops instanceof JsonOps jsonOps) {
                return new TreeCapture.Success(new JsonSnapshotTree(jsonOps, jsonValue));
            }
            if (ops instanceof RegistryOps<?> registryOps) {
                var parent = registryOps.compressMaps() ? JsonOps.COMPRESSED : JsonOps.INSTANCE;
                var typed = registryOps.withParent(parent);
                if (registryOps.equals(typed)) {
                    return new TreeCapture.Success(new JsonSnapshotTree(typed, jsonValue));
                }
            }
        }
        if (value instanceof Tag nbtValue) {
            if (ops instanceof NbtOps nbtOps) {
                return new TreeCapture.Success(new NbtSnapshotTree(nbtOps, nbtValue));
            }
            if (ops instanceof RegistryOps<?> registryOps) {
                var typed = registryOps.withParent(NbtOps.INSTANCE);
                if (registryOps.equals(typed)) {
                    return new TreeCapture.Success(new NbtSnapshotTree(typed, nbtValue));
                }
            }
        }
        return new TreeCapture.Failure(
                SkillMigrationFailure.of(SkillMigrationFailure.Code.UNSUPPORTED_RAW_FAMILY));
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

    private sealed interface TreeCapture permits TreeCapture.Success, TreeCapture.Failure {
        record Success(SnapshotTree tree) implements TreeCapture {
        }

        record Failure(SkillMigrationFailure failure) implements TreeCapture {
        }
    }
}

sealed interface SnapshotTree permits JsonSnapshotTree, NbtSnapshotTree {
    Dynamic<?> copyDynamic();

    String familyName();

    boolean structurallyEquals(SnapshotTree other);

    int structuralHashCode();
}

final class JsonSnapshotTree implements SnapshotTree {
    private final DynamicOps<JsonElement> ops;
    private final JsonElement value;

    JsonSnapshotTree(DynamicOps<JsonElement> ops, JsonElement value) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.value = Objects.requireNonNull(value, "value").deepCopy();
    }

    @Override
    public Dynamic<JsonElement> copyDynamic() {
        return new Dynamic<>(ops, value.deepCopy());
    }

    @Override
    public String familyName() {
        return "json";
    }

    @Override
    public boolean structurallyEquals(SnapshotTree other) {
        return other instanceof JsonSnapshotTree json && value.equals(json.value);
    }

    @Override
    public int structuralHashCode() {
        return 31 + value.hashCode();
    }
}

final class NbtSnapshotTree implements SnapshotTree {
    private final DynamicOps<Tag> ops;
    private final Tag value;

    NbtSnapshotTree(DynamicOps<Tag> ops, Tag value) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.value = Objects.requireNonNull(value, "value").copy();
    }

    @Override
    public Dynamic<Tag> copyDynamic() {
        return new Dynamic<>(ops, value.copy());
    }

    @Override
    public String familyName() {
        return "nbt";
    }

    @Override
    public boolean structurallyEquals(SnapshotTree other) {
        return other instanceof NbtSnapshotTree nbt && value.equals(nbt.value);
    }

    @Override
    public int structuralHashCode() {
        return 37 + value.hashCode();
    }
}
