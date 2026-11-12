package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The isolated physical owner. The catalog supplies structurally validated image bytes. */
final class ScopeDStorage implements AutoCloseable {
    private static final int MAGIC = 0x474D4431;
    private static final int PHYSICAL_VERSION = 1;
    private static final byte CATALOG = 1;
    private static final byte ALLOCATION = 2;
    private static final byte DRAFTS = 3;
    private static final int HASH_BYTES = 32;
    private static final int FRAME_HEADER_BYTES = 2 * Integer.BYTES + 1 + 4 * Long.BYTES + Integer.BYTES;
    private static final int FRAME_OVERHEAD = FRAME_HEADER_BYTES + HASH_BYTES;
    private static final int CATALOG_HEADER_BYTES = 3 * Long.BYTES + 2 * HASH_BYTES;
    private static final int ALLOCATION_BYTES = FRAME_OVERHEAD + Long.BYTES;
    private static final HexFormat HEX = HexFormat.of();
    private static final String ZERO_HASH = "0".repeat(2 * HASH_BYTES);
    private static final String CURRENT = "catalog.current";
    private static final String PREPARE = "catalog.prepare";
    private static final String STAGE_WRITE = "catalog.writing";
    private static final String GENESIS_WRITE = "catalog.genesis.writing";
    private static final String WATERMARK = "allocation.current";
    private static final String WATERMARK_WRITE = "allocation.writing";
    private static final String DRAFT = "draft.current";
    private static final String DRAFT_WRITE = "draft.writing";
    private static final String LOCK = "writer.lock";
    // A second open/close of the same lock inode can release POSIX locks held by this JVM.
    private static final Set<Object> LIVE_DIRECTORY_KEYS = ConcurrentHashMap.newKeySet();

    private final Path directory;
    private final SkillOwnerId owner;
    private final FileChannel directoryChannel;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Object directoryKey;
    private final Object lockKey;
    private final UUID source;
    private Image current;
    private Object currentKey;
    private Image pending;
    private long watermark;
    private long reservedHere;
    private boolean catalogFenced;
    private boolean draftFenced;
    private boolean closed;

    private ScopeDStorage(
            Path directory, SkillOwnerId owner, FileChannel directoryChannel, FileChannel lockChannel,
            FileLock lock, Object directoryKey, Object lockKey) throws IOException {
        this.directory = directory;
        this.owner = owner;
        this.directoryChannel = directoryChannel;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.directoryKey = directoryKey;
        this.lockKey = lockKey;

        verifySource();
        directoryChannel.force(true);
        Frame committed = readFrame(CURRENT, CATALOG, ScopeDLimits.MAX_CATALOG_BYTES);
        if (committed == null) {
            if (exists(PREPARE) || exists(STAGE_WRITE) || exists(DRAFT) || exists(DRAFT_WRITE)) {
                throw failure(Reason.CORRUPT, "catalog missing from an initialized owner source");
            }
            Frame bootstrap = readFrame(WATERMARK, ALLOCATION, ALLOCATION_BYTES);
            if (bootstrap != null && decodeWatermark(bootstrap) != 0) {
                throw failure(Reason.CORRUPT, "catalog missing after durable reservation");
            }
            source = bootstrap == null ? newSourceIncarnation() : bootstrap.source();
            byte[] genesis = imageFrame(-1, 0, 0, ZERO_HASH, ZERO_HASH, new byte[0]);
            current = new Image(-1, 0, 0, ZERO_HASH, ZERO_HASH, frameDigest(genesis), new byte[0]);
        } else {
            source = committed.source();
            current = readImage(committed);
            currentKey = committed.fileKey();
            watermark = readWatermark();
            if (watermark < current.transaction()) {
                throw failure(Reason.CORRUPT, "committed transaction exceeds durable allocation");
            }
        }
        if (exists(PREPARE)) {
            pending();
        }
    }

    /** The caller supplies an existing, privately owned engineering directory for this owner. */
    static ScopeDStorage open(Path ownerDirectory, SkillOwnerId owner) throws IOException {
        Objects.requireNonNull(ownerDirectory, "ownerDirectory");
        Objects.requireNonNull(owner, "owner");
        if (ownerDirectory.getFileSystem() != FileSystems.getDefault()) {
            throw failure(Reason.UNAVAILABLE, "owner source does not use the default local file provider");
        }
        BasicFileAttributes directoryAttrs = attributes(ownerDirectory);
        if (directoryAttrs == null || !directoryAttrs.isDirectory() || directoryAttrs.fileKey() == null) {
            throw failure(Reason.UNAVAILABLE, "owner directory has no stable identity");
        }
        Path directory = ownerDirectory.toRealPath();
        String fileSystem = Files.getFileStore(directory).type();
        if (!fileSystem.equals("apfs") && !fileSystem.equals("ext4")) {
            throw failure(Reason.UNAVAILABLE, "owner source filesystem is not qualified");
        }
        if (!LIVE_DIRECTORY_KEYS.add(directoryAttrs.fileKey())) {
            throw failure(Reason.UNAVAILABLE, "owner source already has a writer in this process");
        }
        FileChannel dir = null;
        FileChannel writer = null;
        try {
            dir = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            Path lockPath = directory.resolve(LOCK);
            BasicFileAttributes beforeLock = attributes(lockPath);
            if (beforeLock != null) {
                requireRegular(beforeLock);
            }
            writer = FileChannel.open(lockPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                    LinkOption.NOFOLLOW_LINKS);
            FileLock held;
            try {
                held = writer.tryLock();
            } catch (OverlappingFileLockException occupied) {
                throw failure(Reason.UNAVAILABLE, "owner source already has a writer");
            }
            if (held == null) {
                throw failure(Reason.UNAVAILABLE, "owner source already has a writer");
            }
            BasicFileAttributes lockAttrs = attributes(lockPath);
            requireRegular(lockAttrs);
            if (beforeLock != null && !beforeLock.fileKey().equals(lockAttrs.fileKey())) {
                throw failure(Reason.UNAVAILABLE, "owner writer lock changed during acquisition");
            }
            return new ScopeDStorage(directory, owner, dir, writer, held, directoryAttrs.fileKey(), lockAttrs.fileKey());
        } catch (IOException | RuntimeException error) {
            closeOnFailure(writer, error);
            closeOnFailure(dir, error);
            LIVE_DIRECTORY_KEYS.remove(directoryAttrs.fileKey());
            throw error;
        }
    }

    synchronized UUID sourceId() throws IOException {
        verifySource();
        return source;
    }

    SkillOwnerId owner() {
        return owner;
    }

    synchronized Image current() throws IOException {
        requireCatalog();
        return current;
    }

    synchronized Optional<Image> pending() throws IOException {
        requireCatalog();
        Frame frame = readFrame(PREPARE, CATALOG, ScopeDLimits.MAX_CATALOG_BYTES);
        if (frame == null) {
            return Optional.empty();
        }
        if (exists(STAGE_WRITE)) {
            throw failure(Reason.CORRUPT, "two incompatible staging states");
        }
        requireSource(frame);
        Image stored = readImage(frame);
        if (stored.baseGeneration() != current.generation() || !stored.baseDigest().equals(current.digest())
                || stored.transaction() <= current.transaction() || stored.transaction() != watermark) {
            throw failure(Reason.CORRUPT, "prepare does not bind the committed predecessor and durable reservation");
        }
        if (pending == null || !pending.digest().equals(stored.digest())) {
            pending = stored;
        }
        return Optional.of(pending);
    }

    synchronized boolean hasPendingMaterial() throws IOException {
        requireCatalog();
        return exists(PREPARE) || exists(STAGE_WRITE);
    }

    synchronized long reserve() throws IOException {
        requireCatalog();
        requireNoPending();
        verifyWatermark();
        long next = watermark + 1;
        if (next > ScopeDLimits.MAX_SERIAL) {
            throw failure(Reason.OVERFLOW, "publication serial exhausted");
        }
        ensureGenesis();
        catalogFenced = true;
        try {
            discardScratch(WATERMARK_WRITE);
            byte[] bytes = frame(ALLOCATION, ByteBuffer.allocate(Long.BYTES).putLong(next).array());
            writeAtomically(WATERMARK_WRITE, WATERMARK, bytes, ALLOCATION, ALLOCATION_BYTES);
        } catch (IOException error) {
            throw fenced("allocation outcome requires recovery", error);
        }
        watermark = next;
        reservedHere = next;
        catalogFenced = false;
        return next;
    }

    /** Checks the reservation or exact recovered prepare without installing or changing physical material. */
    synchronized void requirePublicationReservation(Image expected, long transaction, String contextDigest,
                                                    String contentDigest) throws IOException {
        requireExpected(expected);
        verifyWatermark();
        if (transaction != watermark || transaction <= current.transaction()) {
            throw failure(Reason.STALE, "compiler candidate reservation is stale");
        }
        Optional<Image> staged = pending();
        if (staged.isPresent()) {
            Image image = staged.orElseThrow();
            if (image.transaction() != transaction || !image.contextDigest().equals(contextDigest)
                    || !HEX.formatHex(digest(image.payload, image.payload.length)).equals(contentDigest)) {
                throw failure(Reason.STALE, "pending image differs from compiler candidate");
            }
        } else if (reservedHere != transaction || exists(STAGE_WRITE)) {
            throw failure(Reason.STALE, "compiler candidate has no current reservation");
        }
    }

    synchronized Image stage(Image expected, long transaction, String contextDigest, byte[] payload) throws IOException {
        requireExpected(expected);
        requireNoPending();
        verifyWatermark();
        if (transaction != reservedHere || transaction != watermark || transaction <= current.transaction()) {
            throw failure(Reason.STALE, "stage requires this writer's current durable reservation");
        }
        if (payload.length == 0 || payload.length > ScopeDLimits.MAX_CATALOG_PAYLOAD) {
            throw failure(Reason.OVERFLOW, "catalog payload length out of bounds");
        }
        if (current.generation() == Long.MAX_VALUE) {
            throw failure(Reason.OVERFLOW, "catalog generation exhausted");
        }
        byte[] bytes = imageFrame(current.generation(), current.generation() + 1, transaction,
                current.digest(), contextDigest, payload);
        Frame prepared = writeAtomically(STAGE_WRITE, PREPARE, bytes, CATALOG, ScopeDLimits.MAX_CATALOG_BYTES);
        pending = readImage(prepared);
        return pending;
    }

    synchronized Image commitPreparedImage(Image expected, Image staged) throws IOException {
        beginCatalogReplacement(expected, staged);
        Frame durable = finishCatalogReplacement(staged);
        current = staged;
        currentKey = durable.fileKey();
        pending = null;
        reservedHere = 0;
        catalogFenced = false;
        return current;
    }

    private void beginCatalogReplacement(Image expected, Image staged) throws IOException {
        requireExpected(expected);
        verifyWatermark();
        if (pending().orElse(null) != staged || staged == null) {
            throw failure(Reason.STALE, "prepared image belongs to a different operation or writer");
        }
        catalogFenced = true;
        try {
            replace(PREPARE, CURRENT);
        } catch (IOException error) {
            throw fenced("catalog replacement outcome requires recovery", error);
        }
    }

    private Frame finishCatalogReplacement(Image staged) throws IOException {
        try {
            directoryChannel.force(true);
            Frame durable = readExact(CURRENT, CATALOG, ScopeDLimits.MAX_CATALOG_BYTES, staged.digest());
            verifySource();
            return durable;
        } catch (IOException error) {
            throw fenced("catalog durability or readback requires recovery", error);
        }
    }

    synchronized void discardPending(Image expected, Image staged) throws IOException {
        requireExpected(expected);
        if (pending().orElse(null) != staged || staged == null) {
            throw failure(Reason.STALE, "prepared image belongs to a different operation or writer");
        }
        catalogFenced = true;
        try {
            discardScratch(PREPARE);
        } catch (IOException error) {
            throw fenced("prepare disposition requires recovery", error);
        }
        pending = null;
        reservedHere = 0;
        catalogFenced = false;
    }

    synchronized void discardInterruptedStage(Image expected) throws IOException {
        requireExpected(expected);
        if (exists(PREPARE)) {
            throw failure(Reason.PENDING, "complete staging requires exact disposition");
        }
        discardScratch(STAGE_WRITE);
        reservedHere = 0;
    }

    synchronized Optional<byte[]> readDrafts() throws IOException {
        requireDrafts();
        Frame draft = readFrame(DRAFT, DRAFTS, ScopeDLimits.MAX_DRAFT_BYTES);
        if (draft == null) {
            return Optional.empty();
        }
        requireSource(draft);
        if (draft.body().length == 0) {
            throw failure(Reason.CORRUPT, "draft image is empty");
        }
        return Optional.of(draft.body());
    }

    synchronized void writeDrafts(byte[] payload) throws IOException {
        requireDrafts();
        if (payload.length == 0 || payload.length > ScopeDLimits.MAX_DRAFT_PAYLOAD) {
            throw failure(Reason.OVERFLOW, "draft payload length out of bounds");
        }
        readDrafts();
        ensureGenesis();
        draftFenced = true;
        try {
            discardScratch(DRAFT_WRITE);
            writeAtomically(DRAFT_WRITE, DRAFT, frame(DRAFTS, payload), DRAFTS, ScopeDLimits.MAX_DRAFT_BYTES);
        } catch (IOException error) {
            throw fenced("draft replacement outcome requires recovery", error);
        }
        draftFenced = false;
    }

    private void ensureGenesis() throws IOException {
        if (currentKey != null) {
            return;
        }
        requireCatalog();
        catalogFenced = true;
        if (readWatermark() == -1) {
            discardScratch(WATERMARK_WRITE);
            byte[] bootstrap = frame(ALLOCATION, ByteBuffer.allocate(Long.BYTES).putLong(0).array());
            writeAtomically(WATERMARK_WRITE, WATERMARK, bootstrap, ALLOCATION, ALLOCATION_BYTES);
        }
        discardScratch(GENESIS_WRITE);
        byte[] bytes = imageFrame(-1, 0, 0, ZERO_HASH, ZERO_HASH, new byte[0]);
        Frame genesis = writeAtomically(GENESIS_WRITE, CURRENT, bytes, CATALOG, ScopeDLimits.MAX_CATALOG_BYTES);
        currentKey = genesis.fileKey();
        catalogFenced = false;
    }

    private long readWatermark() throws IOException {
        Frame allocation = readFrame(WATERMARK, ALLOCATION, ALLOCATION_BYTES);
        if (allocation == null) {
            return -1;
        }
        requireSource(allocation);
        return decodeWatermark(allocation);
    }

    private static long decodeWatermark(Frame allocation) throws IOException {
        if (allocation.body().length != Long.BYTES) {
            throw failure(Reason.CORRUPT, "invalid allocation frame length");
        }
        long value = ByteBuffer.wrap(allocation.body()).getLong();
        if (value < 0 || value > ScopeDLimits.MAX_SERIAL) {
            throw failure(Reason.CORRUPT, "invalid allocation watermark");
        }
        return value;
    }

    private void verifyWatermark() throws IOException {
        try {
            long durable = readWatermark();
            if (durable != watermark && !(durable == -1 && watermark == 0 && currentKey == null)) {
                throw failure(Reason.CORRUPT, "durable allocation changed outside this writer");
            }
        } catch (IOException error) {
            catalogFenced = true;
            throw error;
        }
    }

    private void requireCatalog() throws IOException {
        verifySource();
        if (catalogFenced) {
            throw failure(Reason.FENCED, "catalog publication outcome requires recovery");
        }
        BasicFileAttributes actual = attributes(directory.resolve(CURRENT));
        Object actualKey = actual == null ? null : actual.fileKey();
        if ((actual != null && (!actual.isRegularFile()
                || actual.size() != FRAME_OVERHEAD + CATALOG_HEADER_BYTES + current.payload.length))
                || !Objects.equals(currentKey, actualKey)) {
            catalogFenced = true;
            throw failure(Reason.FENCED, "current image identity changed outside this writer");
        }
    }

    private void requireExpected(Image expected) throws IOException {
        requireCatalog();
        if (expected != current) {
            throw failure(Reason.STALE, "catalog base is stale or belongs to another writer");
        }
        if (currentKey != null) {
            try {
                readExact(CURRENT, CATALOG, ScopeDLimits.MAX_CATALOG_BYTES, expected.digest());
            } catch (IOException error) {
                catalogFenced = true;
                throw error;
            }
        }
    }

    private void requireDrafts() throws IOException {
        verifySource();
        if (draftFenced) {
            throw failure(Reason.FENCED, "draft publication outcome requires recovery");
        }
    }

    private void requireNoPending() throws IOException {
        if (hasPendingMaterial()) {
            throw failure(Reason.PENDING, "existing staging must be recovered or safely discarded first");
        }
    }

    private void verifySource() throws IOException {
        BasicFileAttributes directoryAttrs = attributes(directory);
        BasicFileAttributes lockAttrs = attributes(directory.resolve(LOCK));
        if (!lock.isValid() || directoryAttrs == null || !directoryAttrs.isDirectory()
                || !directoryKey.equals(directoryAttrs.fileKey()) || lockAttrs == null || !lockAttrs.isRegularFile()
                || !lockKey.equals(lockAttrs.fileKey())) {
            throw failure(Reason.UNAVAILABLE, "owner directory or writer lock lost");
        }
    }

    private boolean exists(String name) throws IOException {
        return attributes(directory.resolve(name)) != null;
    }

    private void discardScratch(String name) throws IOException {
        Path file = directory.resolve(name);
        BasicFileAttributes attr = attributes(file);
        if (attr != null) {
            requireRegular(attr);
            Files.delete(file);
            directoryChannel.force(true);
        }
    }

    private Frame writeAtomically(String scratch, String target, byte[] bytes, byte kind, int limit) throws IOException {
        verifySource();
        try (FileChannel file = FileChannel.open(directory.resolve(scratch), StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (file.write(buffer) <= 0) {
                    throw failure(Reason.UNAVAILABLE, "short physical write");
                }
            }
            file.force(true);
        }
        String digest = frameDigest(bytes);
        readExact(scratch, kind, limit, digest);
        replace(scratch, target);
        directoryChannel.force(true);
        Frame durable = readExact(target, kind, limit, digest);
        verifySource();
        return durable;
    }

    private void replace(String from, String to) throws IOException {
        verifySource();
        Files.move(directory.resolve(from), directory.resolve(to),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private Frame readExact(String name, byte kind, int limit, String expectedDigest) throws IOException {
        Frame result = readFrame(name, kind, limit);
        if (result == null || !expectedDigest.equals(result.digest())) {
            throw failure(Reason.CORRUPT, "missing or mismatched exact physical image");
        }
        requireSource(result);
        return result;
    }

    private Frame readFrame(String name, byte kind, int limit) throws IOException {
        Path file = directory.resolve(name);
        BasicFileAttributes before = attributes(file);
        if (before == null) {
            return null;
        }
        requireRegular(before);
        if (before.size() < FRAME_OVERHEAD || before.size() > limit) {
            throw failure(Reason.CORRUPT, "physical frame length out of bounds");
        }
        byte[] bytes = new byte[(int) before.size()];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            while (buffer.hasRemaining()) {
                if (input.read(buffer) <= 0) {
                    throw failure(Reason.CORRUPT, "truncated physical frame");
                }
            }
            if (input.read(ByteBuffer.allocate(1)) != -1) {
                throw failure(Reason.CORRUPT, "trailing physical frame bytes");
            }
        }
        BasicFileAttributes after = attributes(file);
        requireRegular(after);
        if (after.size() != before.size() || !before.fileKey().equals(after.fileKey())) {
            throw failure(Reason.CORRUPT, "physical frame changed during readback");
        }
        buffer.flip();
        if (buffer.getInt() != MAGIC) {
            throw failure(Reason.CORRUPT, "unrecognized physical frame");
        }
        if (buffer.getInt() != PHYSICAL_VERSION) {
            throw failure(Reason.UNSUPPORTED, "unsupported physical frame version");
        }
        if (buffer.get() != kind || !owner.value().equals(getUuid(buffer))) {
            throw failure(Reason.CORRUPT, "physical frame family or owner mismatch");
        }
        UUID frameSource = getUuid(buffer);
        int bodyLength = buffer.getInt();
        if (bodyLength < 0 || bodyLength != buffer.remaining() - HASH_BYTES) {
            throw failure(Reason.CORRUPT, "physical body length mismatch");
        }
        int checksumAt = bytes.length - HASH_BYTES;
        byte[] calculated = digest(bytes, checksumAt);
        if (!Arrays.equals(calculated, 0, HASH_BYTES, bytes, checksumAt, bytes.length)) {
            throw failure(Reason.CORRUPT, "physical frame checksum mismatch");
        }
        byte[] body = Arrays.copyOfRange(bytes, FRAME_HEADER_BYTES, checksumAt);
        return new Frame(frameSource, body, HEX.formatHex(calculated), before.fileKey());
    }

    private void requireSource(Frame frame) throws IOException {
        if (!source.equals(frame.source())) {
            throw failure(Reason.CORRUPT, "physical frame source mismatch");
        }
    }

    private byte[] imageFrame(long base, long generation, long serial, String baseDigest, String context, byte[] payload) {
        ByteBuffer body = ByteBuffer.allocate(CATALOG_HEADER_BYTES + payload.length);
        body.putLong(base).putLong(generation).putLong(serial).put(parseHash(baseDigest)).put(parseHash(context));
        body.put(payload);
        return frame(CATALOG, body.array());
    }

    private Image readImage(Frame frame) throws IOException {
        ByteBuffer body = ByteBuffer.wrap(frame.body());
        if (body.remaining() < CATALOG_HEADER_BYTES) {
            throw failure(Reason.CORRUPT, "catalog header is truncated");
        }
        long base = body.getLong();
        long generation = body.getLong();
        long transaction = body.getLong();
        String baseDigest = readHash(body);
        String context = readHash(body);
        if (generation == 0) {
            if (base != -1 || transaction != 0 || !ZERO_HASH.equals(baseDigest) || !ZERO_HASH.equals(context)
                    || body.hasRemaining()) {
                throw failure(Reason.CORRUPT, "invalid catalog genesis");
            }
        } else if (generation < 0 || base != generation - 1 || transaction <= 0
                || transaction > ScopeDLimits.MAX_SERIAL || ZERO_HASH.equals(baseDigest) || !body.hasRemaining()) {
            throw failure(Reason.CORRUPT, "invalid committed generation or predecessor");
        }
        byte[] payload = new byte[body.remaining()];
        body.get(payload);
        return new Image(base, generation, transaction, baseDigest, context, frame.digest(), payload);
    }

    private byte[] frame(byte kind, byte[] body) {
        ByteBuffer buffer = ByteBuffer.allocate(FRAME_OVERHEAD + body.length);
        buffer.putInt(MAGIC).putInt(PHYSICAL_VERSION).put(kind);
        putUuid(buffer, owner.value());
        putUuid(buffer, source);
        buffer.putInt(body.length).put(body);
        buffer.put(digest(buffer.array(), buffer.position()));
        return buffer.array();
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException absent) {
            return null;
        }
    }

    private static void requireRegular(BasicFileAttributes attributes) throws IOException {
        if (attributes == null || !attributes.isRegularFile() || attributes.fileKey() == null) {
            throw failure(Reason.CORRUPT, "expected a regular physical file with stable identity");
        }
    }

    private static byte[] digest(byte[] bytes, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String frameDigest(byte[] frame) {
        return HEX.formatHex(frame, frame.length - HASH_BYTES, frame.length);
    }

    private static String readHash(ByteBuffer bytes) {
        byte[] hash = new byte[HASH_BYTES];
        bytes.get(hash);
        return HEX.formatHex(hash);
    }

    private static byte[] parseHash(String hex) {
        if (hex.length() != 2 * HASH_BYTES) {
            throw new IllegalArgumentException("expected 32-byte hex digest");
        }
        return HEX.parseHex(hex);
    }

    private static UUID getUuid(ByteBuffer buffer) {
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static UUID newSourceIncarnation() {
        // Opaque 128-bit source provenance. This value never allocates a SkillId or owner identity.
        var random = new SecureRandom();
        return new UUID(random.nextLong(), random.nextLong());
    }

    private static void putUuid(ByteBuffer buffer, UUID value) {
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
    }

    private static void closeOnFailure(FileChannel resource, Throwable failure) {
        if (resource != null) {
            try {
                resource.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try (FileChannel ignoredDirectory = directoryChannel; FileChannel ignoredWriter = lockChannel) {
            // Closing the writer channel releases its lock even when a prior operation fenced the source.
        } finally {
            LIVE_DIRECTORY_KEYS.remove(directoryKey);
        }
    }

    private static Failure failure(Reason reason, String message) {
        return new Failure(reason, message);
    }

    private static Failure fenced(String message, IOException cause) {
        Failure error = failure(Reason.FENCED, message);
        error.initCause(cause);
        return error;
    }

    enum Reason { UNAVAILABLE, CORRUPT, UNSUPPORTED, FENCED, STALE, PENDING, OVERFLOW }

    static final class Failure extends IOException {
        private final Reason reason;

        private Failure(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        Reason reason() {
            return reason;
        }
    }

    static final class Image {
        private final long baseGeneration;
        private final long generation;
        private final long transaction;
        private final String baseDigest;
        private final String contextDigest;
        private final String digest;
        private final byte[] payload;

        private Image(long baseGeneration, long generation, long transaction, String baseDigest,
                String contextDigest, String digest, byte[] payload) {
            this.baseGeneration = baseGeneration;
            this.generation = generation;
            this.transaction = transaction;
            this.baseDigest = baseDigest;
            this.contextDigest = contextDigest;
            this.digest = digest;
            this.payload = payload;
        }

        long baseGeneration() { return baseGeneration; }
        long generation() { return generation; }
        long transaction() { return transaction; }
        String baseDigest() { return baseDigest; }
        String contextDigest() { return contextDigest; }
        String digest() { return digest; }
        byte[] payload() { return payload.clone(); }
    }

    private record Frame(UUID source, byte[] body, String digest, Object fileKey) {}
}
