package com.yo1no.gramarye.magic.definition.store;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Iterative, bounded scanner for one decompressed playerdata unnamed Compound root.
 *
 * <p>The scanner materializes only {@code neoforge:attachments/gramarye:player_skills}. All other
 * fields are streamed and discarded after duplicate-name and structural checks. The supplied
 * Attachment limit is an admission coordinate, not a second production ceiling owner; the P4-C
 * owner supplies it when the future E1-B composition seam is built.</p>
 */
final class P4E1PlayerDataNbtScanner {
    static final int CURRENT_DATA_VERSION = 3_955;
    static final String ATTACHMENTS_FIELD = "neoforge:attachments";
    static final String PLAYER_SKILLS_FIELD = "gramarye:player_skills";
    private static final String DATA_VERSION_FIELD = "DataVersion";
    private static final int SKIP_BUFFER_BYTES = 8_192;

    private P4E1PlayerDataNbtScanner() {
    }

    static ScanResult scan(
            InputStream decompressed,
            P4E1AuditBudget.FileScope scope,
            long maximumAttachmentEncodedBytes)
            throws P4E1CompressedCapacityRejected {
        Objects.requireNonNull(decompressed, "decompressed");
        Objects.requireNonNull(scope, "scope");
        if (maximumAttachmentEncodedBytes < 1L
                || maximumAttachmentEncodedBytes == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Attachment byte maximum must be finite and positive");
        }

        var counted = new BudgetedInputStream(decompressed, scope);
        var input = new DataInputStream(counted);
        try {
            var rootType = input.readUnsignedByte();
            if (rootType != Tag.TAG_COMPOUND) {
                throw Rejected.platform("playerdata root is not Compound");
            }
            var rootName = readModifiedUtf(input, scope);
            if (!rootName.value().isEmpty()) {
                throw Rejected.strict("playerdata root name is not empty");
            }

            var parser = new Parser(input, scope, maximumAttachmentEncodedBytes);
            parser.parseRoot();
            if (input.read() != -1) {
                throw Rejected.strict("playerdata contains bytes after the root Compound");
            }
            return parser.finish();
        } catch (P4E1CompressedCapacityRejected rejected) {
            throw rejected;
        } catch (CapacityRejected rejected) {
            return new ScanResult.Failure(
                    P4E1SourceFailure.capacity(rejected.exceeded()),
                    P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION);
        } catch (Rejected rejected) {
            return new ScanResult.Failure(
                    P4E1SourceFailure.simple(rejected.code(), rejected.stage()),
                    rejected.category());
        } catch (UTFDataFormatException | EOFException rejected) {
            return new ScanResult.Failure(
                    P4E1SourceFailure.simple(
                            P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN,
                            P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND),
                    P4E1PlayerDataSourceSelector.FailureCategory
                            .PLATFORM_READ_FAILURE_PROVEN);
        } catch (IOException exception) {
            return new ScanResult.Failure(
                    P4E1SourceFailure.simple(
                            P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN,
                            P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND),
                    P4E1PlayerDataSourceSelector.FailureCategory
                            .PLATFORM_READ_FAILURE_PROVEN);
        }
    }

    sealed interface ScanResult {
        record Ready(AttachmentObservation attachment) implements ScanResult {
            public Ready {
                Objects.requireNonNull(attachment, "attachment");
            }
        }

        record Failure(
                P4E1SourceFailure failure,
                P4E1PlayerDataSourceSelector.FailureCategory category) implements ScanResult {
            public Failure {
                Objects.requireNonNull(failure, "failure");
                Objects.requireNonNull(category, "category");
            }
        }
    }

    sealed interface AttachmentObservation {
        enum Missing implements AttachmentObservation {
            INSTANCE
        }

        record Present(Tag tag, long exactWriteAnyTagBytes) implements AttachmentObservation {
            public Present {
                Objects.requireNonNull(tag, "tag");
                if (exactWriteAnyTagBytes < 1L) {
                    throw new IllegalArgumentException("present Attachment size must be positive");
                }
            }
        }

        record Oversize(long observedAtLeast, long maximum) implements AttachmentObservation {
            public Oversize {
                if (maximum < 1L || maximum == Long.MAX_VALUE
                        || observedAtLeast != maximum + 1L) {
                    throw new IllegalArgumentException("non-canonical Attachment excess");
                }
            }
        }
    }

    private static final class Parser {
        private final DataInputStream input;
        private final P4E1AuditBudget.FileScope scope;
        private final long attachmentMaximum;
        private final ArrayDeque<Frame> frames = new ArrayDeque<>();
        private DataVersionKind dataVersionKind = DataVersionKind.MISSING;
        private int dataVersion;
        private AttachmentObservation attachment = AttachmentObservation.Missing.INSTANCE;

        private Parser(
                DataInputStream input,
                P4E1AuditBudget.FileScope scope,
                long attachmentMaximum) {
            this.input = input;
            this.scope = scope;
            this.attachmentMaximum = attachmentMaximum;
        }

        private void parseRoot() throws IOException {
            checkpointDepth(1L);
            checkpointPair(
                    P4E1AuditCounter.COMPOUND_CONTAINERS_PER_FILE,
                    P4E1AuditCounter.COMPOUND_CONTAINERS_TOTAL,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    1L);
            frames.push(new CompoundFrame(
                    1,
                    Context.ROOT,
                    null,
                    (ignored, ignoredWidth) -> { }));
            while (!frames.isEmpty()) {
                frames.peek().step();
            }
        }

        private ScanResult finish() {
            return switch (dataVersionKind) {
                case MISSING -> new ScanResult.Failure(
                        P4E1SourceFailure.simple(
                                P4E1SourceFailure.Code.DATA_VERSION_MISSING,
                                P4E1AuditStage.DATA_VERSION),
                        P4E1PlayerDataSourceSelector.FailureCategory
                                .POST_NBT_SEMANTIC_FAILURE);
                case WRONG_TYPE -> new ScanResult.Failure(
                        P4E1SourceFailure.simple(
                                P4E1SourceFailure.Code.DATA_VERSION_WRONG_TYPE,
                                P4E1AuditStage.DATA_VERSION),
                        P4E1PlayerDataSourceSelector.FailureCategory
                                .POST_NBT_SEMANTIC_FAILURE);
                case INT -> {
                    if (dataVersion != CURRENT_DATA_VERSION) {
                        yield new ScanResult.Failure(
                                P4E1SourceFailure.simple(
                                        P4E1SourceFailure.Code.DATA_VERSION_NOT_CURRENT,
                                        P4E1AuditStage.DATA_VERSION),
                                P4E1PlayerDataSourceSelector.FailureCategory
                                        .POST_NBT_SEMANTIC_FAILURE);
                    }
                    yield new ScanResult.Ready(attachment);
                }
            };
        }

        private void startValue(
                int type,
                int containerDepth,
                Context context,
                AttachmentCapture capture,
                ValueSink sink) throws IOException {
            if (type <= Tag.TAG_END || type > Tag.TAG_LONG_ARRAY) {
                throw Rejected.platform("unsupported NBT tag kind");
            }
            switch (type) {
                case Tag.TAG_BYTE -> scalar(1, capture, sink,
                        () -> ByteTag.valueOf(input.readByte()));
                case Tag.TAG_SHORT -> scalar(2, capture, sink,
                        () -> ShortTag.valueOf(input.readShort()));
                case Tag.TAG_INT -> scalar(4, capture, sink,
                        () -> IntTag.valueOf(input.readInt()));
                case Tag.TAG_LONG -> scalar(8, capture, sink,
                        () -> LongTag.valueOf(input.readLong()));
                case Tag.TAG_FLOAT -> scalar(4, capture, sink,
                        () -> FloatTag.valueOf(input.readFloat()));
                case Tag.TAG_DOUBLE -> scalar(8, capture, sink,
                        () -> DoubleTag.valueOf(input.readDouble()));
                case Tag.TAG_BYTE_ARRAY -> readByteArray(capture, sink);
                case Tag.TAG_STRING -> readString(capture, sink);
                case Tag.TAG_LIST -> pushList(containerDepth, context, capture, sink);
                case Tag.TAG_COMPOUND -> pushCompound(containerDepth, context, capture, sink);
                case Tag.TAG_INT_ARRAY -> readIntArray(capture, sink);
                case Tag.TAG_LONG_ARRAY -> readLongArray(capture, sink);
                default -> throw Rejected.platform("unsupported NBT tag kind");
            }
        }

        private void scalar(
                int width,
                AttachmentCapture capture,
                ValueSink sink,
                ScalarReader reader) throws IOException {
            checkpointPair(
                    P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                    P4E1AuditCounter.SCALAR_TAGS_TOTAL,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    1L);
            addCapture(capture, width);
            var tag = reader.read();
            sink.accept(capture == null || capture.oversize ? null : tag, width);
        }

        private void readString(AttachmentCapture capture, ValueSink sink) throws IOException {
            checkpointPair(
                    P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                    P4E1AuditCounter.SCALAR_TAGS_TOTAL,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    1L);
            var value = readModifiedUtf(input, scope);
            var width = checkedAdd(2L, value.encodedBytes());
            addCapture(capture, width);
            sink.accept(capture == null || capture.oversize
                    ? null
                    : StringTag.valueOf(value.value()), width);
        }

        private void readByteArray(AttachmentCapture capture, ValueSink sink) throws IOException {
            var length = input.readInt();
            requireNonNegativeLength(length);
            checkpointPair(
                    P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_TOTAL,
                    P4E1AuditStage.TYPED_ARRAY_LENGTH,
                    length);
            var width = checkedAdd(4L, length);
            addCapture(capture, width);
            if (capture != null && !capture.oversize) {
                var values = new byte[length];
                input.readFully(values);
                sink.accept(new ByteArrayTag(values), width);
            } else {
                skipFully(input, length);
                sink.accept(null, width);
            }
        }

        private void readIntArray(AttachmentCapture capture, ValueSink sink) throws IOException {
            var length = input.readInt();
            requireNonNegativeLength(length);
            checkpointPair(
                    P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL,
                    P4E1AuditStage.TYPED_ARRAY_LENGTH,
                    length);
            var payload = checkedMultiply(length, Integer.BYTES);
            var width = checkedAdd(4L, payload);
            addCapture(capture, width);
            if (capture != null && !capture.oversize) {
                var values = new int[length];
                for (var index = 0; index < values.length; index++) {
                    values[index] = input.readInt();
                }
                sink.accept(new IntArrayTag(values), width);
            } else {
                skipFully(input, payload);
                sink.accept(null, width);
            }
        }

        private void readLongArray(AttachmentCapture capture, ValueSink sink) throws IOException {
            var length = input.readInt();
            requireNonNegativeLength(length);
            checkpointPair(
                    P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.LONG_ARRAY_ELEMENTS_TOTAL,
                    P4E1AuditStage.TYPED_ARRAY_LENGTH,
                    length);
            var payload = checkedMultiply(length, Long.BYTES);
            var width = checkedAdd(4L, payload);
            addCapture(capture, width);
            if (capture != null && !capture.oversize) {
                var values = new long[length];
                for (var index = 0; index < values.length; index++) {
                    values[index] = input.readLong();
                }
                sink.accept(new LongArrayTag(values), width);
            } else {
                skipFully(input, payload);
                sink.accept(null, width);
            }
        }

        private void pushList(
                int depth,
                Context context,
                AttachmentCapture capture,
                ValueSink sink) throws IOException {
            var elementType = input.readUnsignedByte();
            var length = input.readInt();
            requireNonNegativeLength(length);
            if (length > 0 && elementType == Tag.TAG_END) {
                throw Rejected.platform("non-empty NBT list declared End elements");
            }
            if (elementType > Tag.TAG_LONG_ARRAY) {
                throw Rejected.platform("NBT list declared unsupported elements");
            }
            checkpointPair(
                    P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.LIST_ELEMENTS_TOTAL,
                    P4E1AuditStage.LIST_LENGTH,
                    length);
            checkpointDepth(depth);
            addCapture(capture, 5L);
            frames.push(new ListFrame(
                    depth, context, capture, sink, elementType, length));
        }

        private void pushCompound(
                int depth,
                Context context,
                AttachmentCapture capture,
                ValueSink sink) throws IOException {
            checkpointDepth(depth);
            checkpointPair(
                    P4E1AuditCounter.COMPOUND_CONTAINERS_PER_FILE,
                    P4E1AuditCounter.COMPOUND_CONTAINERS_TOTAL,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    1L);
            frames.push(new CompoundFrame(depth, context, capture, sink));
        }

        private void checkpointDepth(long depth) throws CapacityRejected {
            var exceeded = scope.checkpointDepth(
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND, depth);
            if (exceeded.isPresent()) {
                throw new CapacityRejected(exceeded.orElseThrow());
            }
        }

        private void checkpointPair(
                P4E1AuditCounter perFile,
                P4E1AuditCounter aggregate,
                P4E1AuditStage stage,
                long delta) throws CapacityRejected {
            var exceeded = scope.checkpointFileAndAggregate(
                    perFile, aggregate, stage, stage, delta);
            if (exceeded.isPresent()) {
                throw new CapacityRejected(exceeded.orElseThrow());
            }
        }

        private void addCapture(AttachmentCapture capture, long delta) {
            if (capture != null) {
                capture.add(delta);
            }
        }

        private void requireNonNegativeLength(int length) throws Rejected {
            if (length < 0) {
                throw Rejected.platform("negative NBT length");
            }
        }

        private final class CompoundFrame implements Frame {
            private final int depth;
            private final Context context;
            private final AttachmentCapture capture;
            private final ValueSink sink;
            private final HashSet<String> names = new HashSet<>();
            private final CompoundTag materialized;
            private long payloadWidth;

            private CompoundFrame(
                    int depth,
                    Context context,
                    AttachmentCapture capture,
                    ValueSink sink) {
                this.depth = depth;
                this.context = context;
                this.capture = capture;
                this.sink = sink;
                this.materialized = capture == null || capture.oversize
                        ? null
                        : new CompoundTag();
            }

            @Override
            public void step() throws IOException {
                var type = input.readUnsignedByte();
                addCapture(capture, 1L);
                payloadWidth = checkedAdd(payloadWidth, 1L);
                if (type == Tag.TAG_END) {
                    frames.pop();
                    sink.accept(capture == null || capture.oversize
                            ? null
                            : materialized, payloadWidth);
                    return;
                }
                checkpointPair(
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL,
                        P4E1AuditStage.COMPOUND_FIELD_CHECKPOINT,
                        1L);
                var name = readModifiedUtf(input, scope);
                addCapture(capture, checkedAdd(2L, name.encodedBytes()));
                payloadWidth = checkedAdd(
                        payloadWidth, checkedAdd(2L, name.encodedBytes()));
                if (!names.add(name.value())) {
                    throw Rejected.strict("duplicate raw Compound field");
                }

                var childContext = Context.OTHER;
                var childCapture = capture;
                ValueSink childSink = (tag, width) -> {
                    payloadWidth = checkedAdd(payloadWidth, width);
                    if (materialized != null && tag != null) {
                        materialized.put(name.value(), tag);
                    }
                };

                if (context == Context.ROOT && DATA_VERSION_FIELD.equals(name.value())) {
                    if (type == Tag.TAG_INT) {
                        childCapture = new AttachmentCapture(Long.MAX_VALUE - 1L);
                        childSink = (tag, width) -> {
                            payloadWidth = checkedAdd(payloadWidth, width);
                            dataVersionKind = DataVersionKind.INT;
                            dataVersion = ((IntTag) Objects.requireNonNull(tag, "DataVersion"))
                                    .getAsInt();
                        };
                    } else {
                        dataVersionKind = DataVersionKind.WRONG_TYPE;
                    }
                } else if (context == Context.ROOT
                        && ATTACHMENTS_FIELD.equals(name.value())) {
                    if (type == Tag.TAG_COMPOUND) {
                        childContext = Context.ATTACHMENTS;
                    }
                } else if (context == Context.ATTACHMENTS
                        && PLAYER_SKILLS_FIELD.equals(name.value())) {
                    var selectedCapture = new AttachmentCapture(attachmentMaximum);
                    selectedCapture.add(1L);
                    childCapture = selectedCapture;
                    childContext = Context.MATERIALIZED;
                    childSink = (tag, width) -> {
                        payloadWidth = checkedAdd(payloadWidth, width);
                        attachment = selectedCapture.oversize
                                ? new AttachmentObservation.Oversize(
                                        selectedCapture.maximum + 1L,
                                        selectedCapture.maximum)
                                : new AttachmentObservation.Present(
                                        Objects.requireNonNull(tag, "materialized Attachment"),
                                        selectedCapture.observed);
                    };
                } else if (capture != null) {
                    childContext = Context.MATERIALIZED;
                }
                startValue(type, depth + 1, childContext, childCapture, childSink);
            }
        }

        private final class ListFrame implements Frame {
            private final int depth;
            private final Context context;
            private final AttachmentCapture capture;
            private final ValueSink sink;
            private final int elementType;
            private final int length;
            private final ListTag materialized;
            private int index;
            private long payloadWidth = 5L;

            private ListFrame(
                    int depth,
                    Context context,
                    AttachmentCapture capture,
                    ValueSink sink,
                    int elementType,
                    int length) {
                this.depth = depth;
                this.context = context;
                this.capture = capture;
                this.sink = sink;
                this.elementType = elementType;
                this.length = length;
                this.materialized = capture == null || capture.oversize
                        ? null
                        : new ListTag(length);
            }

            @Override
            public void step() throws IOException {
                if (index == length) {
                    frames.pop();
                    sink.accept(capture == null || capture.oversize
                            ? null
                            : materialized, payloadWidth);
                    return;
                }
                index++;
                startValue(
                        elementType,
                        depth + 1,
                        context == Context.MATERIALIZED ? context : Context.OTHER,
                        capture,
                        (tag, width) -> {
                            payloadWidth = checkedAdd(payloadWidth, width);
                            if (materialized != null && tag != null
                                    && !materialized.addTag(materialized.size(), tag)) {
                                throw Rejected.platform("NBT list element type mismatch");
                            }
                        });
            }
        }
    }

    private static UtfValue readModifiedUtf(
            DataInputStream input,
            P4E1AuditBudget.FileScope scope) throws IOException {
        var encodedBytes = input.readUnsignedShort();
        var exceeded = scope.checkpointFileAndAggregate(
                P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE,
                P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL,
                P4E1AuditStage.MODIFIED_UTF_PREFIX,
                P4E1AuditStage.MODIFIED_UTF_PREFIX,
                encodedBytes);
        if (exceeded.isPresent()) {
            throw new CapacityRejected(exceeded.orElseThrow());
        }
        var bytes = new byte[encodedBytes];
        var characters = new char[encodedBytes];
        input.readFully(bytes);
        var byteIndex = 0;
        var characterIndex = 0;
        while (byteIndex < encodedBytes) {
            var first = bytes[byteIndex] & 0xff;
            if (first > 0x7f) {
                break;
            }
            byteIndex++;
            characters[characterIndex++] = (char) first;
        }
        while (byteIndex < encodedBytes) {
            var first = bytes[byteIndex] & 0xff;
            switch (first >> 4) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> {
                    byteIndex++;
                    characters[characterIndex++] = (char) first;
                }
                case 12, 13 -> {
                    byteIndex += 2;
                    if (byteIndex > encodedBytes) {
                        throw new UTFDataFormatException("partial modified-UTF character");
                    }
                    var second = bytes[byteIndex - 1] & 0xff;
                    if ((second & 0xc0) != 0x80) {
                        throw new UTFDataFormatException("bad modified-UTF continuation");
                    }
                    characters[characterIndex++] = (char) (((first & 0x1f) << 6)
                            | (second & 0x3f));
                }
                case 14 -> {
                    byteIndex += 3;
                    if (byteIndex > encodedBytes) {
                        throw new UTFDataFormatException("partial modified-UTF character");
                    }
                    var second = bytes[byteIndex - 2] & 0xff;
                    var third = bytes[byteIndex - 1] & 0xff;
                    if ((second & 0xc0) != 0x80 || (third & 0xc0) != 0x80) {
                        throw new UTFDataFormatException("bad modified-UTF continuation");
                    }
                    characters[characterIndex++] = (char) (((first & 0x0f) << 12)
                            | ((second & 0x3f) << 6)
                            | (third & 0x3f));
                }
                default -> throw new UTFDataFormatException("bad modified-UTF leading byte");
            }
        }
        return new UtfValue(new String(characters, 0, characterIndex), encodedBytes);
    }

    private static void skipFully(DataInputStream input, long byteCount) throws IOException {
        var buffer = new byte[SKIP_BUFFER_BYTES];
        var remaining = byteCount;
        while (remaining > 0L) {
            var chunk = (int) Math.min(remaining, buffer.length);
            input.readFully(buffer, 0, chunk);
            remaining -= chunk;
        }
    }

    private static long checkedAdd(long left, long right) throws Rejected {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw Rejected.platform("NBT width overflow");
        }
    }

    private static long checkedMultiply(int value, int width) throws Rejected {
        try {
            return Math.multiplyExact((long) value, (long) width);
        } catch (ArithmeticException exception) {
            throw Rejected.platform("NBT payload length overflow");
        }
    }

    @FunctionalInterface
    private interface ScalarReader {
        Tag read() throws IOException;
    }

    @FunctionalInterface
    private interface ValueSink {
        void accept(Tag tag, long payloadWidth) throws IOException;
    }

    private interface Frame {
        void step() throws IOException;
    }

    private enum Context {
        ROOT,
        ATTACHMENTS,
        MATERIALIZED,
        OTHER
    }

    private enum DataVersionKind {
        MISSING,
        WRONG_TYPE,
        INT
    }

    private record UtfValue(String value, int encodedBytes) {
        private UtfValue {
            Objects.requireNonNull(value, "value");
            if (encodedBytes < 0) {
                throw new IllegalArgumentException("negative modified-UTF byte count");
            }
        }
    }

    private static final class AttachmentCapture {
        private final long maximum;
        private long observed;
        private boolean oversize;

        private AttachmentCapture(long maximum) {
            this.maximum = maximum;
        }

        private void add(long delta) {
            if (delta < 0L) {
                throw new IllegalArgumentException("negative Attachment byte delta");
            }
            if (oversize) {
                return;
            }
            if (delta > maximum - observed) {
                observed = maximum + 1L;
                oversize = true;
                return;
            }
            observed += delta;
        }
    }

    private static final class BudgetedInputStream extends InputStream {
        private final InputStream delegate;
        private final P4E1AuditBudget.FileScope scope;
        private final byte[] one = new byte[1];

        private BudgetedInputStream(InputStream delegate, P4E1AuditBudget.FileScope scope) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.scope = Objects.requireNonNull(scope, "scope");
        }

        @Override
        public int read() throws IOException {
            var count = read(one, 0, 1);
            return count == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            var count = delegate.read(bytes, offset, length);
            if (count <= 0) {
                return count;
            }
            var exceeded = scope.checkpointFileAndAggregate(
                    P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE,
                    P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL,
                    P4E1AuditStage.PER_FILE_DECOMPRESSED,
                    P4E1AuditStage.AGGREGATE_DECOMPRESSED_CHECKED_ADD,
                    count);
            if (exceeded.isPresent()) {
                throw new CapacityRejected(exceeded.orElseThrow());
            }
            return count;
        }
    }

    private static final class CapacityRejected extends IOException {
        private final P4E1AuditBudget.Exceeded exceeded;

        private CapacityRejected(P4E1AuditBudget.Exceeded exceeded) {
            super("P4-E1 audit capacity exceeded");
            this.exceeded = Objects.requireNonNull(exceeded, "exceeded");
        }

        private P4E1AuditBudget.Exceeded exceeded() {
            return exceeded;
        }
    }

    private static final class Rejected extends IOException {
        private final P4E1SourceFailure.Code code;
        private final P4E1AuditStage stage;
        private final P4E1PlayerDataSourceSelector.FailureCategory category;

        private Rejected(
                P4E1SourceFailure.Code code,
                P4E1AuditStage stage,
                P4E1PlayerDataSourceSelector.FailureCategory category,
                String detail) {
            super(detail);
            this.code = Objects.requireNonNull(code, "code");
            this.stage = Objects.requireNonNull(stage, "stage");
            this.category = Objects.requireNonNull(category, "category");
        }

        private static Rejected platform(String detail) {
            return new Rejected(
                    P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    P4E1PlayerDataSourceSelector.FailureCategory
                            .PLATFORM_READ_FAILURE_PROVEN,
                    detail);
        }

        private static Rejected strict(String detail) {
            return new Rejected(
                    P4E1SourceFailure.Code.STRICT_NBT_REJECTED,
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                    P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION,
                    detail);
        }

        private P4E1SourceFailure.Code code() {
            return code;
        }

        private P4E1AuditStage stage() {
            return stage;
        }

        private P4E1PlayerDataSourceSelector.FailureCategory category() {
            return category;
        }
    }
}
