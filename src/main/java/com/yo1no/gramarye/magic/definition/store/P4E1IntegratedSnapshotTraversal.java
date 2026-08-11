package com.yo1no.gramarye.magic.definition.store;

import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * Single-pass logical accounting for the already materialized integrated-player snapshot.
 *
 * <p>This primitive deliberately owns neither global source composition nor P4-C admission. A
 * caller consumes the returned nested Attachment observation immediately, then invokes the exact
 * freshness check before any complete audit result can escape the synchronous startup call chain.
 * The source tree is never copied, serialized, string-rendered, or traversed a second time.</p>
 */
final class P4E1IntegratedSnapshotTraversal {
    private static final String ATTACHMENTS_FIELD = "neoforge:attachments";
    private static final String PLAYER_SKILLS_FIELD = "gramarye:player_skills";

    private P4E1IntegratedSnapshotTraversal() {
    }

    static Selection capture(MinecraftServer server, P4E1AuditBudget budget) {
        Objects.requireNonNull(server, "server");
        return capture(new MinecraftSnapshotAccess(server), budget, true);
    }

    static Selection capture(SnapshotAccess access, P4E1AuditBudget budget) {
        return capture(access, budget, true);
    }

    /** B1 ordering seam; the global owner performs the sole relevant-record checkpoint later. */
    static Selection captureForGlobal(MinecraftServer server, P4E1AuditBudget budget) {
        Objects.requireNonNull(server, "server");
        return capture(new MinecraftSnapshotAccess(server), budget, false);
    }

    static Selection captureForGlobal(SnapshotAccess access, P4E1AuditBudget budget) {
        return capture(access, budget, false);
    }

    private static Selection capture(
            SnapshotAccess access, P4E1AuditBudget budget, boolean countRelevant) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(budget, "budget");
        if (!access.isSameThread()) {
            throw new IllegalStateException(
                    "Integrated player snapshot capture requires the server thread");
        }

        var serverIdentity = Objects.requireNonNull(
                access.serverIdentity(), "server identity");
        var profileId = access.profileId();
        var snapshot = access.loadedPlayerSnapshot();
        if (profileId == null && snapshot != null) {
            return new Selection.Failure(P4E1SourceFailure.simple(
                    P4E1SourceFailure.Code.INTEGRATED_OWNER_IDENTITY_UNAVAILABLE,
                    P4E1AuditStage.SOURCE_SELECTION));
        }
        if (profileId == null || snapshot == null) {
            return new Selection.Disk(serverIdentity, Optional.ofNullable(profileId));
        }

        if (countRelevant) {
            var exceeded = budget.checkpointSingle(
                    P4E1AuditCounter.RELEVANT_RECORDS,
                    P4E1AuditStage.RELEVANT_RECORDS,
                    1L);
            if (exceeded.isPresent()) {
                return new Selection.Failure(P4E1SourceFailure.capacity(exceeded.orElseThrow()));
            }
        }
        return new Selection.Integrated(serverIdentity, profileId, snapshot);
    }

    static TraversalResult traverse(
            CompoundTag snapshot, P4E1AuditBudget.FileScope scope) {
        var walker = new Walker(snapshot, scope);
        return walker.traverse();
    }

    interface SnapshotAccess {
        Object serverIdentity();

        boolean isSameThread();

        UUID profileId();

        CompoundTag loadedPlayerSnapshot();
    }

    sealed interface Selection permits Selection.Disk, Selection.Integrated, Selection.Failure {
        record Disk(Object serverIdentity, Optional<UUID> profileId) implements Selection {
            public Disk {
                Objects.requireNonNull(serverIdentity, "serverIdentity");
                Objects.requireNonNull(profileId, "profileId");
            }

            boolean isCurrent(MinecraftServer candidate) {
                Objects.requireNonNull(candidate, "candidate");
                return isCurrent(new MinecraftSnapshotAccess(candidate));
            }

            boolean isCurrent(SnapshotAccess candidate) {
                Objects.requireNonNull(candidate, "candidate");
                return candidate.isSameThread()
                        && candidate.serverIdentity() == serverIdentity
                        && profileId.equals(Optional.ofNullable(candidate.profileId()))
                        && candidate.loadedPlayerSnapshot() == null;
            }
        }

        final class Integrated implements Selection {
            private final Object serverIdentity;
            private final UUID ownerId;
            private final CompoundTag snapshotIdentity;
            private boolean traversalClaimed;

            private Integrated(
                    Object serverIdentity, UUID ownerId, CompoundTag snapshotIdentity) {
                this.serverIdentity = Objects.requireNonNull(
                        serverIdentity, "serverIdentity");
                this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
                this.snapshotIdentity = Objects.requireNonNull(
                        snapshotIdentity, "snapshotIdentity");
            }

            UUID ownerId() {
                return ownerId;
            }

            TraversalResult traverse(P4E1AuditBudget budget) {
                Objects.requireNonNull(budget, "budget");
                if (traversalClaimed) {
                    throw new IllegalStateException(
                            "Integrated player snapshot may be traversed only once");
                }
                traversalClaimed = true;
                var scope = budget.newFileScope();
                scope.markCompressedBytesNotApplicable();
                return P4E1IntegratedSnapshotTraversal.traverse(snapshotIdentity, scope);
            }

            boolean isCurrent(MinecraftServer candidate) {
                Objects.requireNonNull(candidate, "candidate");
                return isCurrent(new MinecraftSnapshotAccess(candidate));
            }

            boolean isCurrent(SnapshotAccess candidate) {
                return freshnessFailure(candidate).isEmpty();
            }

            Optional<P4E1SourceFailure> freshnessFailure(MinecraftServer candidate) {
                Objects.requireNonNull(candidate, "candidate");
                return freshnessFailure(new MinecraftSnapshotAccess(candidate));
            }

            Optional<P4E1SourceFailure> freshnessFailure(SnapshotAccess candidate) {
                Objects.requireNonNull(candidate, "candidate");
                var current = candidate.isSameThread()
                        && candidate.serverIdentity() == serverIdentity
                        && ownerId.equals(candidate.profileId())
                        && candidate.loadedPlayerSnapshot() == snapshotIdentity;
                return current
                        ? Optional.empty()
                        : Optional.of(P4E1SourceFailure.simple(
                                P4E1SourceFailure.Code.INTEGRATED_OWNER_FRESHNESS_LOST,
                                P4E1AuditStage.SOURCE_SELECTION));
            }
        }

        record Failure(P4E1SourceFailure failure) implements Selection {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    sealed interface TraversalResult permits TraversalResult.Ready, TraversalResult.Failure {
        record Ready(long logicalEncodedWidth, Optional<AttachmentObservation> attachment)
                implements TraversalResult {
            public Ready {
                if (logicalEncodedWidth < 4L) {
                    throw new IllegalArgumentException(
                            "integrated logical width cannot be smaller than an empty root");
                }
                Objects.requireNonNull(attachment, "attachment");
            }
        }

        record Failure(P4E1SourceFailure failure) implements TraversalResult {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    /** Exact source reference plus its standalone arbitrary-Tag logical encoded width. */
    record AttachmentObservation(Tag tagIdentity, long exactEncodedWidth) {
        AttachmentObservation {
            Objects.requireNonNull(tagIdentity, "tagIdentity");
            if (exactEncodedWidth < 1L) {
                throw new IllegalArgumentException("Attachment encoded width must include type");
            }
        }
    }

    private enum Location {
        ROOT,
        ROOT_ATTACHMENTS,
        OTHER,
        ATTACHMENT_SUBTREE
    }

    private sealed interface Work permits ValueWork, CompoundWork, ListWork {
    }

    private record ValueWork(Tag tag, int containerDepth, Location location) implements Work {
        ValueWork {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(location, "location");
            if (containerDepth <= 0) {
                throw new IllegalArgumentException("containerDepth must be positive");
            }
        }
    }

    private record CompoundWork(
            CompoundTag tag,
            int depth,
            Location location,
            Iterator<String> fields) implements Work {
        CompoundWork {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(fields, "fields");
        }
    }

    private record ListWork(
            ListTag tag, int depth, Location location, int nextIndex) implements Work {
        ListWork {
            Objects.requireNonNull(tag, "tag");
            Objects.requireNonNull(location, "location");
            if (nextIndex < 0 || nextIndex > tag.size()) {
                throw new IllegalArgumentException("invalid ListTag cursor");
            }
        }
    }

    private static final class Walker {
        private final CompoundTag root;
        private final P4E1AuditBudget.FileScope scope;
        private final ArrayDeque<Work> work = new ArrayDeque<>();
        private P4E1SourceFailure failure;
        private Tag attachmentIdentity;
        private long logicalWidth;
        private long attachmentWidth;

        private Walker(CompoundTag root, P4E1AuditBudget.FileScope scope) {
            this.root = Objects.requireNonNull(root, "root");
            this.scope = Objects.requireNonNull(scope, "scope");
        }

        private TraversalResult traverse() {
            // Root Compound type plus the empty modified-UTF root-name length prefix.
            if (!addLogicalWidth(3L, false)) {
                return failed();
            }
            work.push(new ValueWork(root, 1, Location.ROOT));
            try {
                while (!work.isEmpty() && failure == null) {
                    switch (work.pop()) {
                        case ValueWork value -> visitValue(value);
                        case CompoundWork compound -> visitCompound(compound);
                        case ListWork list -> visitList(list);
                    }
                }
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = P4E1SourceFailure.runtime(
                            P4E1SourceFailure.Code.INTERNAL_RUNTIME_FAILURE,
                            P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                            exception);
                }
            }
            if (failure != null) {
                return failed();
            }
            var attachment = attachmentIdentity == null
                    ? Optional.<AttachmentObservation>empty()
                    : Optional.of(new AttachmentObservation(
                            attachmentIdentity, attachmentWidth));
            return new TraversalResult.Ready(logicalWidth, attachment);
        }

        private TraversalResult.Failure failed() {
            return new TraversalResult.Failure(Objects.requireNonNull(failure, "failure"));
        }

        private void visitValue(ValueWork value) {
            var tag = value.tag();
            var inAttachment = value.location() == Location.ATTACHMENT_SUBTREE;
            switch (tag.getId()) {
                case Tag.TAG_BYTE -> visitScalar(1L, inAttachment);
                case Tag.TAG_SHORT -> visitScalar(2L, inAttachment);
                case Tag.TAG_INT, Tag.TAG_FLOAT -> visitScalar(4L, inAttachment);
                case Tag.TAG_LONG, Tag.TAG_DOUBLE -> visitScalar(8L, inAttachment);
                case Tag.TAG_BYTE_ARRAY -> visitByteArray(tag, inAttachment);
                case Tag.TAG_STRING -> visitString(tag, inAttachment);
                case Tag.TAG_LIST -> visitListValue(tag, value.containerDepth(), value.location());
                case Tag.TAG_COMPOUND -> visitCompoundValue(
                        tag, value.containerDepth(), value.location());
                case Tag.TAG_INT_ARRAY -> visitIntArray(tag, inAttachment);
                case Tag.TAG_LONG_ARRAY -> visitLongArray(tag, inAttachment);
                default -> rejectStrict(P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND);
            }
        }

        private void visitScalar(long payloadWidth, boolean inAttachment) {
            if (checkpointPaired(
                    P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                    P4E1AuditCounter.SCALAR_TAGS_TOTAL,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    1L)) {
                addLogicalWidth(payloadWidth, inAttachment);
            }
        }

        private void visitString(Tag tag, boolean inAttachment) {
            if (!(tag instanceof StringTag stringTag)) {
                rejectStrict(P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND);
                return;
            }
            if (!checkpointPaired(
                    P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                    P4E1AuditCounter.SCALAR_TAGS_TOTAL,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    1L)
                    || !addLogicalWidth(2L, inAttachment)) {
                return;
            }
            addModifiedUtfPayload(stringTag.getAsString(), inAttachment);
        }

        private void visitByteArray(Tag tag, boolean inAttachment) {
            if (!(tag instanceof ByteArrayTag array)) {
                rejectStrict(P4E1AuditStage.TYPED_ARRAY_LENGTH);
                return;
            }
            var length = array.size();
            if (!addLogicalWidth(4L, inAttachment)
                    || !checkpointPaired(
                            P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                            P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_TOTAL,
                            P4E1AuditStage.TYPED_ARRAY_LENGTH,
                            P4E1AuditStage.TYPED_ARRAY_LENGTH,
                            length)) {
                return;
            }
            addLogicalWidth(length, inAttachment);
        }

        private void visitIntArray(Tag tag, boolean inAttachment) {
            if (!(tag instanceof IntArrayTag array)) {
                rejectStrict(P4E1AuditStage.TYPED_ARRAY_LENGTH);
                return;
            }
            visitWideArray(
                    array.size(),
                    4L,
                    inAttachment,
                    P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL);
        }

        private void visitLongArray(Tag tag, boolean inAttachment) {
            if (!(tag instanceof LongArrayTag array)) {
                rejectStrict(P4E1AuditStage.TYPED_ARRAY_LENGTH);
                return;
            }
            visitWideArray(
                    array.size(),
                    8L,
                    inAttachment,
                    P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.LONG_ARRAY_ELEMENTS_TOTAL);
        }

        private void visitWideArray(
                int length,
                long elementWidth,
                boolean inAttachment,
                P4E1AuditCounter perFile,
                P4E1AuditCounter total) {
            if (!addLogicalWidth(4L, inAttachment)
                    || !checkpointPaired(
                            perFile,
                            total,
                            P4E1AuditStage.TYPED_ARRAY_LENGTH,
                            P4E1AuditStage.TYPED_ARRAY_LENGTH,
                            length)) {
                return;
            }
            addLogicalWidth((long) length * elementWidth, inAttachment);
        }

        private void visitCompoundValue(Tag tag, int depth, Location location) {
            if (!(tag instanceof CompoundTag compound)) {
                rejectStrict(P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND);
                return;
            }
            if (!checkpointDepth(depth)
                    || !checkpointPaired(
                            P4E1AuditCounter.COMPOUND_CONTAINERS_PER_FILE,
                            P4E1AuditCounter.COMPOUND_CONTAINERS_TOTAL,
                            P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                            P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                            1L)) {
                return;
            }
            work.push(new CompoundWork(
                    compound, depth, location, compound.getAllKeys().iterator()));
        }

        private void visitCompound(CompoundWork compound) {
            if (!compound.fields().hasNext()) {
                addLogicalWidth(
                        1L, compound.location() == Location.ATTACHMENT_SUBTREE);
                return;
            }

            var name = compound.fields().next();
            var value = compound.tag().get(name);
            if (value == null || value.getId() == Tag.TAG_END) {
                rejectStrict(P4E1AuditStage.COMPOUND_FIELD_CHECKPOINT);
                return;
            }
            work.push(compound);
            var inAttachment = compound.location() == Location.ATTACHMENT_SUBTREE;
            // Field type is consumed before the field-count checkpoint.
            if (!addLogicalWidth(1L, inAttachment)
                    || !checkpointPaired(
                            P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                            P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL,
                            P4E1AuditStage.COMPOUND_FIELD_CHECKPOINT,
                            P4E1AuditStage.COMPOUND_FIELD_CHECKPOINT,
                            1L)
                    || !addLogicalWidth(2L, inAttachment)
                    || !addModifiedUtfPayload(name, inAttachment)) {
                return;
            }

            var childLocation = childLocation(compound.location(), name, value);
            if (childLocation == Location.ATTACHMENT_SUBTREE && !inAttachment) {
                if (attachmentIdentity != null) {
                    rejectStrict(P4E1AuditStage.P4C_ADMISSION);
                    return;
                }
                attachmentIdentity = value;
                attachmentWidth = 1L;
            }
            work.push(new ValueWork(value, compound.depth() + 1, childLocation));
        }

        private Location childLocation(Location parent, String name, Tag value) {
            if (parent == Location.ATTACHMENT_SUBTREE) {
                return Location.ATTACHMENT_SUBTREE;
            }
            if (parent == Location.ROOT
                    && ATTACHMENTS_FIELD.equals(name)
                    && value instanceof CompoundTag) {
                return Location.ROOT_ATTACHMENTS;
            }
            if (parent == Location.ROOT_ATTACHMENTS && PLAYER_SKILLS_FIELD.equals(name)) {
                return Location.ATTACHMENT_SUBTREE;
            }
            return Location.OTHER;
        }

        private void visitListValue(Tag tag, int depth, Location location) {
            if (!(tag instanceof ListTag list)) {
                rejectStrict(P4E1AuditStage.LIST_LENGTH);
                return;
            }
            if (!checkpointDepth(depth)
                    || !addLogicalWidth(
                            5L, location == Location.ATTACHMENT_SUBTREE)
                    || !checkpointPaired(
                            P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                            P4E1AuditCounter.LIST_ELEMENTS_TOTAL,
                            P4E1AuditStage.LIST_LENGTH,
                            P4E1AuditStage.LIST_LENGTH,
                            list.size())) {
                return;
            }
            if (!list.isEmpty() && list.getElementType() == Tag.TAG_END) {
                rejectStrict(P4E1AuditStage.LIST_LENGTH);
                return;
            }
            work.push(new ListWork(list, depth, location, 0));
        }

        private void visitList(ListWork list) {
            if (list.nextIndex() >= list.tag().size()) {
                return;
            }
            var value = list.tag().get(list.nextIndex());
            if (value == null || value.getId() != list.tag().getElementType()) {
                rejectStrict(P4E1AuditStage.LIST_LENGTH);
                return;
            }
            work.push(new ListWork(
                    list.tag(), list.depth(), list.location(), list.nextIndex() + 1));
            work.push(new ValueWork(value, list.depth() + 1, list.location()));
        }

        private boolean addModifiedUtfPayload(String value, boolean inAttachment) {
            long encodedWidth = 0L;
            for (var index = 0; index < value.length(); index++) {
                var codeUnit = value.charAt(index);
                var width = codeUnit >= 0x0001 && codeUnit <= 0x007f
                        ? 1L
                        : codeUnit > 0x07ff ? 3L : 2L;
                if (width > 0xffffL - encodedWidth) {
                    rejectStrict(P4E1AuditStage.MODIFIED_UTF_PREFIX);
                    return false;
                }
                if (!addLogicalWidth(width, inAttachment)
                        || !checkpointPaired(
                                P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE,
                                P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL,
                                P4E1AuditStage.MODIFIED_UTF_PREFIX,
                                P4E1AuditStage.MODIFIED_UTF_PREFIX,
                                width)) {
                    return false;
                }
                encodedWidth += width;
            }
            return true;
        }

        private boolean addLogicalWidth(long delta, boolean inAttachment) {
            if (delta < 0L) {
                rejectStrict(P4E1AuditStage.PER_FILE_DECOMPRESSED);
                return false;
            }
            var exceeded = scope.checkpointFileAndAggregate(
                    P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE,
                    P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL,
                    P4E1AuditStage.PER_FILE_DECOMPRESSED,
                    P4E1AuditStage.AGGREGATE_DECOMPRESSED_CHECKED_ADD,
                    delta);
            if (exceeded.isPresent()) {
                failure = P4E1SourceFailure.capacity(exceeded.orElseThrow());
                return false;
            }
            logicalWidth += delta;
            if (inAttachment) {
                attachmentWidth += delta;
            }
            return true;
        }

        private boolean checkpointDepth(int depth) {
            var exceeded = scope.checkpointDepth(
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND, depth);
            if (exceeded.isPresent()) {
                failure = P4E1SourceFailure.capacity(exceeded.orElseThrow());
                return false;
            }
            return true;
        }

        private boolean checkpointPaired(
                P4E1AuditCounter perFile,
                P4E1AuditCounter total,
                P4E1AuditStage perFileStage,
                P4E1AuditStage totalStage,
                long delta) {
            var exceeded = scope.checkpointFileAndAggregate(
                    perFile, total, perFileStage, totalStage, delta);
            if (exceeded.isPresent()) {
                failure = P4E1SourceFailure.capacity(exceeded.orElseThrow());
                return false;
            }
            return true;
        }

        private void rejectStrict(P4E1AuditStage stage) {
            failure = P4E1SourceFailure.simple(
                    P4E1SourceFailure.Code.STRICT_NBT_REJECTED, stage);
        }
    }

    private static final class MinecraftSnapshotAccess implements SnapshotAccess {
        private final MinecraftServer server;

        private MinecraftSnapshotAccess(MinecraftServer server) {
            this.server = Objects.requireNonNull(server, "server");
        }

        @Override
        public Object serverIdentity() {
            return server;
        }

        @Override
        public boolean isSameThread() {
            return server.isSameThread();
        }

        @Override
        public UUID profileId() {
            GameProfile profile = server.getSingleplayerProfile();
            return profile == null ? null : profile.getId();
        }

        @Override
        public CompoundTag loadedPlayerSnapshot() {
            return server.getWorldData().getLoadedPlayerTag();
        }
    }
}
