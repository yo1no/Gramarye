package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.P4E3PlayerDataFixture;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.core.RegistryAccess;
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

/** Streaming builder for the exact lifecycle-reachable P4-E3 first/restart fixture. */
final class P4E3FixtureBuilder {
    private static final int RECORDS = 2_048;
    private static final int READY_RECORDS = 1_024;
    private static final int PEAK_RECORDS = 9;
    private static final int FILLER_RECORDS = RECORDS - PEAK_RECORDS;
    private static final int BUFFER_BYTES = 8_192;
    private static final int COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;
    private static final long MISSING_OWNER_MSB = 0x5034_4533_0000_0002L;
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String PLAYER_SKILLS = "gramarye:player_skills";

    private P4E3FixtureBuilder() {
    }

    static P4E3FixtureManifest prepare(Path gameDirectory, Path reportRoot)
            throws IOException {
        var game = normalized(gameDirectory, "gameDirectory");
        var reports = normalized(reportRoot, "reportRoot");
        if (game.startsWith(reports) || reports.startsWith(game)) {
            throw new IOException("P4-E3 game and report roots must be disjoint");
        }
        deleteTree(game);
        deleteTree(reports);
        var worldRoot = game.resolve("world");
        var playerdata = worldRoot.resolve("playerdata");
        Files.createDirectories(playerdata);
        Files.createDirectories(reports);

        var store = prepareProspectiveStore(worldRoot);
        var payloads = readyPayloads(store.store());
        var plans = plans(payloads);
        var canonical = new ArrayList<Long>(RECORDS);
        for (var plan : plans) {
            var measured = Wire.write(null, Header.canonical(), plan.writer());
            if (measured.decompressedBytes() != plan.facts().decompressedBytes()) {
                throw new IOException("P4-E3 planned decompressed bytes changed");
            }
            canonical.add(measured.physicalBytes());
        }
        var headers = tuneHeaders(canonical);
        var inventoryLines = new ArrayList<String>(4_096);
        for (var index = 0; index < plans.size(); index++) {
            var path = playerdata.resolve(ownerId(index) + ".dat");
            var written = Wire.write(path, headers.get(index), plans.get(index).writer());
            if (written.physicalBytes() != headers.get(index).targetPhysicalBytes()
                    || written.decompressedBytes()
                            != plans.get(index).facts().decompressedBytes()) {
                throw new IOException("P4-E3 tuned playerdata write changed");
            }
            var old = playerdata.resolve(ownerId(index) + ".dat_old");
            Files.write(
                    old,
                    new byte[] {(byte) index},
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            inventoryLines.add(path.getFileName() + ":" + written.physicalBytes()
                    + ":" + P4E3FixtureManifest.sha256(path));
            inventoryLines.add(old.getFileName() + ":1:"
                    + P4E3FixtureManifest.sha256(old));
        }
        var entries = listRegular(playerdata);
        if (entries.size() != 4_096
                || entries.stream().filter(path -> path.getFileName().toString()
                        .endsWith(".dat")).count() != 2_048
                || entries.stream().filter(path -> path.getFileName().toString()
                        .endsWith(".dat_old")).count() != 2_048) {
            throw new IOException("P4-E3 physical playerdata directory changed");
        }
        var primary = P4D3StoreJournalFixture.primary(worldRoot);
        var manifest = new P4E3FixtureManifest(
                P4E3FixtureManifest.Vector.locked(),
                store.histories(), store.revisions(), store.journalEntries(),
                store.journalBytes(), P4E3FixtureManifest.sha256(primary),
                Files.size(primary), Files.getLastModifiedTime(primary).toMillis(),
                P4E3FixtureManifest.sha256(inventoryText(inventoryLines)));
        manifest.write(reports);
        return manifest;
    }

    private static PreparedStore prepareProspectiveStore(Path worldRoot) throws IOException {
        var research = P4E0R2QStoreJournalFixtures.buildExact();
        research.writePrimary(worldRoot, false);
        var loaded = SkillSavedDataPrimaryIngress.load(
                P4D3StoreJournalFixture.primary(worldRoot),
                Optional.of(RegistryAccess.EMPTY));
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)) {
            throw new IOException("P4-E3 current R2Q primary did not load");
        }
        var candidate = ready.candidate();
        var histories = new ArrayList<>(candidate.store().snapshot().histories());
        if (histories.size() != 2_048) {
            throw new IOException("P4-E3 current Store history count changed");
        }
        var h0 = histories.get(0);
        var h1 = histories.get(1);
        var h2 = histories.get(2);
        if (h0.revisions().size() != 2 || h1.revisions().size() != 2
                || h2.revisions().size() != 2) {
            throw new IOException("P4-E3 H0/H1/H2 source histories changed");
        }
        var h0Revision = new SkillRevision(2);
        var h0Source = h0.revisions().getLast().document();
        var h0Document = new SkillDocument(
                h0Source.schemaVersion(), h0.skillId(), h0Revision,
                h0Source.nodes(), h0Source.appearance());
        histories.set(0, new SkillHistorySnapshot(
                h0.skillId(), h0.owner(),
                List.of(
                        h0.revisions().get(0),
                        h0.revisions().get(1),
                        new SkillRevisionSnapshot(h0Revision, h0Document))));
        histories.set(1, new SkillHistorySnapshot(
                h1.skillId(), h1.owner(), List.of(h1.revisions().getFirst())));
        var template = histories.getFirst().revisions().getFirst().document();
        var revision = new SkillRevision(0);
        var document = new SkillDocument(
                template.schemaVersion(),
                P4D3StoreJournalFixture.submissionSkillId(),
                revision,
                template.nodes(),
                template.appearance());
        histories.add(new SkillHistorySnapshot(
                P4D3StoreJournalFixture.submissionSkillId(),
                P4E0R2QStoreJournalFixtures.submissionOwner(),
                List.of(new SkillRevisionSnapshot(revision, document))));
        var restored = SkillDefinitionStore.restore(
                new SkillDefinitionStoreSnapshot(histories));
        if (!(restored instanceof SkillDefinitionStoreRestoreResult.Restored success)) {
            throw new IOException("P4-E3 prospective Store restore failed");
        }
        var prospectiveStore = success.store();
        var rebuilt = SkillStoreCarrierBuilder.rebuild(prospectiveStore);
        if (!(rebuilt instanceof CarrierBuildResult.Success carrierSuccess)) {
            throw new IOException("P4-E3 prospective Store carrier rebuild failed");
        }
        var journalLoad = PendingAttachmentJournalFraming.load(
                candidate.carrier().pending());
        if (!(journalLoad instanceof PendingAttachmentJournalLoadResult.Loaded journalReady)) {
            throw new IOException("P4-E3 current journal load failed");
        }
        var physicalEntries = new ArrayList<PendingAttachmentJournalEntryPhysicalV0>(4_096);
        for (var entry : journalReady.candidate().journal().entries()) {
            if (entry.skillId().equals(h0.skillId())
                    || entry.skillId().equals(h1.skillId())
                    || entry.skillId().equals(h2.skillId())) {
                continue;
            }
            physicalEntries.add(physical(entry));
        }
        var h0r0 = new SkillReference(h0.skillId(), new SkillRevision(0));
        var h0r2 = new SkillReference(h0.skillId(), new SkillRevision(2));
        var h1r0 = new SkillReference(h1.skillId(), new SkillRevision(0));
        var h2r0 = new SkillReference(h2.skillId(), new SkillRevision(0));
        var h2r1 = new SkillReference(h2.skillId(), new SkillRevision(1));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                h0.owner(), h0.skillId(), 0, 1, Optional.empty(), h0r0)));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                h0.owner(), h0.skillId(), 1, 2, Optional.of(h0r0), h0r2)));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                h1.owner(), h1.skillId(), 0, 1, Optional.empty(), h1r0)));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                h2.owner(), h2.skillId(), 0, 1, Optional.empty(), h2r0)));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                h2.owner(), h2.skillId(), 1, 2, Optional.of(h2r0), h2r1)));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                h2.owner(), h2.skillId(), 2, 3, Optional.of(h2r1), h2r0)));
        physicalEntries.add(physical(new PendingAttachmentJournalEntry(
                P4E0R2QStoreJournalFixtures.submissionOwner(),
                P4D3StoreJournalFixture.submissionSkillId(),
                0, 1, Optional.empty(), P4D3StoreJournalFixture.submissionTarget())));
        var admitted = PendingAttachmentJournal.admitPhysical(
                new PendingAttachmentJournalPhysicalV0(
                        PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION,
                        physicalEntries));
        if (!(admitted instanceof PendingAttachmentJournal.DomainAdmission.Admitted
                journalAdmission)) {
            throw new IOException("P4-E3 H0/H1/H2 journal admission failed");
        }
        var journal = journalAdmission.journal();
        var encoded = PendingAttachmentJournalFraming.encode(journal);
        if (!(encoded instanceof PendingAttachmentJournalFraming.JournalEncodingResult.Encoded
                encodedReady)) {
            throw new IOException("P4-E3 prospective journal encode failed");
        }
        var carrier = carrierSuccess.carrier();
        var framed = encodedReady.journal();
        if (carrier.historyCount() != 2_049 || carrier.revisionCount() != 4_096
                || framed.entryCount() != 4_096 || framed.byteCount() != 1_048_538
                || !(prospectiveStore.auditJournalTargets(journal)
                        instanceof JournalTargetAuditResult.Audited)) {
            throw new IOException("P4-E3 prospective Store/journal geometry changed");
        }
        var firstSixTargets = journal.entries().stream()
                .filter(entry -> entry.skillId().equals(h0.skillId())
                        || entry.skillId().equals(h1.skillId())
                        || entry.skillId().equals(h2.skillId()))
                .map(PendingAttachmentJournalEntry::targetPointer)
                .toList();
        if (!firstSixTargets.equals(List.of(h0r0, h0r2, h1r0, h2r0, h2r1, h2r0))) {
            throw new IOException("P4-E3 H0/H1/H2 journal route shape changed");
        }
        P4D3StoreJournalFixture.writePrimary(worldRoot, carrier, framed, false);
        research.retainAtPeak();
        return new PreparedStore(
                prospectiveStore,
                carrier.historyCount(),
                carrier.revisionCount(),
                framed.entryCount(),
                framed.byteCount());
    }

    private static PendingAttachmentJournalEntryPhysicalV0 physical(
            PendingAttachmentJournalEntry entry) {
        return new PendingAttachmentJournalEntryPhysicalV0(
                entry.owner(), entry.skillId(), entry.expectedAttachmentGeneration(),
                entry.targetAttachmentGeneration(), entry.expectedPointer(),
                entry.targetPointer());
    }

    private static List<P4E3PlayerDataFixture.ReadyPayload> readyPayloads(
            SkillDefinitionStore store) {
        var histories = store.snapshot().histories();
        var result = new ArrayList<P4E3PlayerDataFixture.ReadyPayload>(READY_RECORDS);
        var latestCount = 0;
        var equippedCount = 0;
        for (var ownerIndex = 0; ownerIndex < READY_RECORDS; ownerIndex++) {
            var owner = P4E0R2QStoreJournalFixtures.owner(ownerIndex);
            var latest = histories.stream()
                    .filter(history -> history.owner().equals(owner))
                    .map(history -> new SkillReference(
                            history.skillId(), history.revisions().getLast().revision()))
                    .toList();
            var expectedLatest = ownerIndex == 9 ? 3 : 2;
            var equippedForOwner = ownerIndex == READY_RECORDS - 1 ? 57 : 58;
            if (latest.size() != expectedLatest) {
                throw new AssertionError("P4-E3 owner history distribution changed");
            }
            var payload = P4E3PlayerDataFixture.ready(latest, equippedForOwner);
            result.add(payload);
            latestCount = Math.addExact(latestCount, payload.latestRoots().size());
            equippedCount = Math.addExact(equippedCount, payload.equippedRoots().size());
        }
        if (latestCount != 2_049 || equippedCount != 59_391
                || Math.addExact(latestCount, equippedCount) != 61_440) {
            throw new AssertionError("P4-E3 player root projection changed");
        }
        return List.copyOf(result);
    }

    private static List<RecordPlan> plans(
            List<P4E3PlayerDataFixture.ReadyPayload> payloads) throws IOException {
        var records = new ArrayList<RecordPlan>(RECORDS);
        addPeaks(records);
        var baselines = new ArrayList<BasePayload>(FILLER_RECORDS);
        for (var recordIndex = PEAK_RECORDS; recordIndex < RECORDS; recordIndex++) {
            baselines.add(recordIndex < RECORDS - READY_RECORDS
                    ? BasePayload.missing()
                    : BasePayload.ready(payloads.get(recordIndex - (RECORDS - READY_RECORDS))));
        }

        var locked = aggregateTarget();
        var peakFacts = aggregate(records);
        var baselineFacts = aggregateBases(baselines);
        var remaining = locked.subtract(peakFacts).subtract(baselineFacts);
        var compounds = distribute(remaining.compoundContainers(), FILLER_RECORDS);
        var lists = distribute(remaining.listElements(), FILLER_RECORDS);
        var bytes = distribute(remaining.byteArrayElements(), FILLER_RECORDS);
        var ints = distribute(remaining.intArrayElements(), FILLER_RECORDS);
        var longs = distribute(remaining.longArrayElements(), FILLER_RECORDS);
        var scalarFieldTotal = Math.subtractExact(
                remaining.scalarTags(), remaining.listElements());
        var scalarFields = distribute(scalarFieldTotal, FILLER_RECORDS);

        var usedFields = new long[FILLER_RECORDS];
        var usedFieldTotal = 0L;
        for (var index = 0; index < FILLER_RECORDS; index++) {
            usedFields[index] = compounds[index]
                    + (lists[index] > 0 ? 1 : 0)
                    + (bytes[index] > 0 ? 1 : 0)
                    + (ints[index] > 0 ? 1 : 0)
                    + (longs[index] > 0 ? 1 : 0)
                    + scalarFields[index];
            usedFieldTotal = Math.addExact(usedFieldTotal, usedFields[index]);
        }
        var zeroArrays = distribute(
                Math.subtractExact(remaining.compoundFieldEntries(), usedFieldTotal),
                FILLER_RECORDS);
        var fieldCounts = new long[FILLER_RECORDS];
        var minimumUtf = 0L;
        for (var index = 0; index < FILLER_RECORDS; index++) {
            fieldCounts[index] = Math.addExact(usedFields[index], zeroArrays[index]);
            minimumUtf = Math.addExact(
                    minimumUtf, minimumNameBytes(Math.toIntExact(fieldCounts[index])));
        }
        var utf = allocateUtf(fieldCounts, remaining.modifiedUtf8Bytes(), minimumUtf);

        var encodings = new ArrayList<FillerEncoding>(FILLER_RECORDS);
        var minimumDecompressed = 0L;
        var maximumPadding = 0L;
        for (var index = 0; index < FILLER_RECORDS; index++) {
            var shape = new FillerShape(
                    compounds[index], lists[index], bytes[index], ints[index], longs[index],
                    scalarFields[index], zeroArrays[index], utf[index]);
            var encoding = FillerEncoding.minimum(baselines.get(index), shape);
            encodings.add(encoding);
            minimumDecompressed = Math.addExact(
                    minimumDecompressed, encoding.facts().decompressedBytes());
            maximumPadding = Math.addExact(
                    maximumPadding, Math.multiplyExact(7L, scalarFields[index]));
        }
        var desiredFillerDecompressed = Math.subtractExact(
                locked.decompressedBytes(), peakFacts.decompressedBytes());
        var padding = Math.subtractExact(desiredFillerDecompressed, minimumDecompressed);
        if (padding < 0L || padding > maximumPadding) {
            throw new IllegalArgumentException("P4-E3 Ready subtraction made bytes infeasible");
        }
        for (var index = 0; index < encodings.size(); index++) {
            var capacity = Math.multiplyExact(7L, scalarFields[index]);
            var addition = Math.min(padding, capacity);
            var encoding = encodings.get(index).withPadding(addition);
            encodings.set(index, encoding);
            padding -= addition;
        }
        if (padding != 0L) {
            throw new IllegalArgumentException("P4-E3 scalar padding was not consumed");
        }
        for (var encoding : encodings) {
            records.add(new RecordPlan(encoding.facts(), encoding::write));
        }
        requireExact(records, locked);
        return List.copyOf(records);
    }

    private static void addPeaks(List<RecordPlan> plans) {
        plans.add(new RecordPlan(
                new Facts(268_435_456L, 1, 1, 3, 0, 268_435_384L, 4, 0, 31, 1),
                P4E3FixtureBuilder::writeHca));
        plans.add(new RecordPlan(
                new Facts(33_554_376L, 1, 1, 3, 0, 33_554_304L, 4, 0, 31, 1),
                output -> writeRepeatedArray(output, 33_554_304)));
        plans.add(new RecordPlan(
                new Facts(2_577L, 512, 512, 512, 0, 0, 0, 0, 522, 1),
                P4E3FixtureBuilder::writeDepth));
        plans.add(new RecordPlan(
                new Facts(1_054L, 3, 1_024, 2, 1_023, 0, 0, 0, 12, 1),
                P4E3FixtureBuilder::writeCompoundContainers));
        plans.add(new RecordPlan(
                new Facts(655_382L, 1, 1, 65_537, 0, 0, 0, 0, 393_227L, 65_537),
                P4E3FixtureBuilder::writeFieldsAndScalars));
        plans.add(new RecordPlan(
                new Facts(65_567L, 2, 1, 2, 65_536, 0, 0, 0, 12, 65_537),
                P4E3FixtureBuilder::writeList));
        plans.add(new RecordPlan(
                new Facts(262_174L, 1, 1, 2, 0, 0, 65_536, 0, 12, 1),
                P4E3FixtureBuilder::writeIntArray));
        plans.add(new RecordPlan(
                new Facts(524_318L, 1, 1, 2, 0, 0, 0, 65_536, 12, 1),
                P4E3FixtureBuilder::writeLongArray));
        plans.add(new RecordPlan(
                new Facts(67_112_823L, 1, 1, 1_025, 0, 0, 0, 0, 67_107_692L, 1_025),
                P4E3FixtureBuilder::writeModifiedUtf));
    }

    private static Facts aggregateTarget() {
        var vector = P4E3FixtureManifest.Vector.locked();
        return new Facts(
                vector.decompressedBytesTotal(),
                Math.toIntExact(vector.containerDepthPerFile()),
                vector.compoundContainersTotal(),
                vector.compoundFieldEntriesTotal(),
                vector.listElementsTotal(),
                vector.byteArrayElementsTotal(),
                vector.intArrayElementsTotal(),
                vector.longArrayElementsTotal(),
                vector.modifiedUtf8BytesTotal(),
                vector.scalarTagsTotal());
    }

    private static List<Header> tuneHeaders(List<Long> canonical) {
        var vector = P4E3FixtureManifest.Vector.locked();
        var perFile = vector.compressedBytesPerFile();
        var totalTarget = vector.compressedBytesTotal();
        var targets = new long[canonical.size()];
        var baselineTotal = 0L;
        for (var index = 0; index < canonical.size(); index++) {
            var value = canonical.get(index);
            if (value <= 0L || value > perFile) {
                throw new IllegalArgumentException("P4-E3 canonical gzip size is invalid");
            }
            targets[index] = value;
            baselineTotal = Math.addExact(baselineTotal, value);
        }
        var remaining = Math.subtractExact(totalTarget, baselineTotal);
        var firstCapacity = Math.subtractExact(perFile, canonical.getFirst());
        if (remaining < firstCapacity || firstCapacity < 4L) {
            throw new IllegalArgumentException("P4-E3 compressed target is infeasible");
        }
        targets[0] = perFile;
        remaining -= firstCapacity;
        for (var index = 1; index < targets.length && remaining > 0L; index++) {
            var capacity = perFile - canonical.get(index);
            var addition = Math.min(remaining, capacity);
            if (addition > 0L && addition < 4L) {
                var needed = 4L - addition;
                var donor = -1;
                for (var candidate = 1; candidate < index; candidate++) {
                    if (targets[candidate] - canonical.get(candidate) >= 4L + needed) {
                        donor = candidate;
                        break;
                    }
                }
                if (donor < 0 || capacity < 4L) {
                    throw new IllegalArgumentException("P4-E3 gzip tail is not expressible");
                }
                targets[donor] -= needed;
                addition = 4L;
                remaining = 4L;
            }
            targets[index] += addition;
            remaining -= addition;
        }
        if (remaining != 0L) {
            throw new IllegalArgumentException("P4-E3 gzip aggregate lacks headroom");
        }
        var result = new ArrayList<Header>(targets.length);
        var total = 0L;
        for (var index = 0; index < targets.length; index++) {
            var increase = targets[index] - canonical.get(index);
            if (increase > 0L && increase < 4L) {
                throw new IllegalArgumentException("P4-E3 gzip header increase is illegal");
            }
            result.add(increase == 0L
                    ? new Header(0, targets[index])
                    : new Header(Math.toIntExact(increase - 3L), targets[index]));
            total += targets[index];
        }
        if (total != totalTarget || targets[0] != perFile) {
            throw new IllegalArgumentException("P4-E3 compressed vector changed");
        }
        return List.copyOf(result);
    }

    private static long[] allocateUtf(long[] fields, long target, long minimum) {
        if (target < minimum) {
            throw new IllegalArgumentException("P4-E3 Ready subtraction made UTF infeasible");
        }
        var values = new long[fields.length];
        for (var index = 0; index < fields.length; index++) {
            values[index] = minimumNameBytes(Math.toIntExact(fields[index]));
        }
        var remaining = target - minimum;
        for (var index = 0; index < fields.length && remaining > 0L; index++) {
            var capacity = Math.multiplyExact(65_535L, fields[index]) - values[index];
            var addition = Math.min(remaining, capacity);
            values[index] += addition;
            remaining -= addition;
        }
        if (remaining != 0L) {
            throw new IllegalArgumentException("P4-E3 UTF distribution lacks capacity");
        }
        return values;
    }

    private static long minimumNameBytes(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("negative P4-E3 field count");
        }
        var total = 0L;
        for (var index = 0; index < count; index++) {
            total += String.format(Locale.ROOT, "n%05d", index).length();
        }
        return total;
    }

    private static long[] distribute(long total, int count) {
        if (total < 0L || count <= 0) {
            throw new IllegalArgumentException("invalid P4-E3 distribution");
        }
        var result = new long[count];
        var base = total / count;
        var remainder = total % count;
        for (var index = 0; index < count; index++) {
            result[index] = base + (index < remainder ? 1L : 0L);
        }
        return result;
    }

    private static Facts aggregate(List<RecordPlan> records) {
        var result = Facts.zero();
        for (var record : records) {
            result = result.plus(record.facts());
        }
        return result;
    }

    private static Facts aggregateBases(List<BasePayload> baselines) {
        var result = Facts.zero();
        for (var baseline : baselines) {
            result = result.plus(baseline.facts());
        }
        return result;
    }

    private static void requireExact(List<RecordPlan> records, Facts target) {
        if (records.size() != RECORDS || !aggregate(records).equals(target)) {
            throw new IllegalArgumentException("P4-E3 actual Ready subtraction is not exact");
        }
        var vector = P4E3FixtureManifest.Vector.locked();
        for (var record : records) {
            var facts = record.facts();
            if (facts.decompressedBytes() > vector.decompressedBytesPerFile()
                    || facts.containerDepth() > vector.containerDepthPerFile()
                    || facts.compoundContainers() > vector.compoundContainersPerFile()
                    || facts.compoundFieldEntries() > vector.compoundFieldEntriesPerFile()
                    || facts.listElements() > vector.listElementsPerFile()
                    || facts.byteArrayElements() > vector.byteArrayElementsPerFile()
                    || facts.intArrayElements() > vector.intArrayElementsPerFile()
                    || facts.longArrayElements() > vector.longArrayElementsPerFile()
                    || facts.modifiedUtf8Bytes() > vector.modifiedUtf8BytesPerFile()
                    || facts.scalarTags() > vector.scalarTagsPerFile()) {
                throw new IllegalArgumentException("P4-E3 record exceeds a per-file maximum");
            }
        }
    }

    private static UUID ownerId(int recordIndex) {
        return recordIndex < READY_RECORDS
                ? new UUID(MISSING_OWNER_MSB, recordIndex)
                : P4E0R2QStoreJournalFixtures.owner(recordIndex - READY_RECORDS).value();
    }

    private static Path normalized(Path path, String name) {
        var value = Objects.requireNonNull(path, name).toAbsolutePath().normalize();
        if (value.getParent() == null) {
            throw new IllegalArgumentException(name + " has no parent");
        }
        return value;
    }

    private static List<Path> listRegular(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        }
    }

    static String inventoryText(List<String> lines) {
        var ordered = new ArrayList<>(lines);
        ordered.sort(String::compareTo);
        return String.join("\n", ordered) + '\n';
    }

    private static void deleteTree(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("P4-E3 owned root is a symbolic link");
        }
        try (var paths = Files.walk(target)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void writeHca(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        writeUuid(output, 1);
        output.writeByte(Tag.TAG_BYTE_ARRAY);
        output.writeUTF("research_payload");
        output.writeInt(268_435_384);
        writeRepeated(output, 268_435_384, 0x5a);
        output.writeByte(Tag.TAG_END);
    }

    private static void writeRepeatedArray(DataOutput output, int length) throws IOException {
        start(output);
        writeDataVersion(output);
        writeUuid(output, 2);
        output.writeByte(Tag.TAG_BYTE_ARRAY);
        output.writeUTF("research_payload");
        output.writeInt(length);
        writeRepeated(output, length, 0x5a);
        output.writeByte(Tag.TAG_END);
    }

    private static void writeDepth(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        for (var depth = 1; depth < 512; depth++) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("d");
        }
        for (var depth = 0; depth < 512; depth++) {
            output.writeByte(Tag.TAG_END);
        }
    }

    private static void writeCompoundContainers(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_LIST);
        output.writeUTF("c");
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeInt(1_023);
        for (var index = 0; index < 1_023; index++) {
            output.writeByte(Tag.TAG_END);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeFieldsAndScalars(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        for (var index = 0; index < 65_536; index++) {
            output.writeByte(Tag.TAG_BYTE);
            output.writeUTF(String.format(Locale.ROOT, "f%05d", index));
            output.writeByte(index);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeList(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_LIST);
        output.writeUTF("l");
        output.writeByte(Tag.TAG_BYTE);
        output.writeInt(65_536);
        writeRepeated(output, 65_536, 0x5a);
        output.writeByte(Tag.TAG_END);
    }

    private static void writeIntArray(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF("i");
        output.writeInt(65_536);
        for (var index = 0; index < 65_536; index++) {
            output.writeInt(index);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeLongArray(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_LONG_ARRAY);
        output.writeUTF("g");
        output.writeInt(65_536);
        for (var index = 0; index < 65_536; index++) {
            output.writeLong(0x5034_4530_5232_5100L ^ index);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeModifiedUtf(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        var full = "u".repeat(65_535);
        var tail = "u".repeat(59_232);
        for (var index = 0; index < 1_024; index++) {
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF(String.format(Locale.ROOT, "u%05d", index));
            output.writeUTF(index == 1_023 ? tail : full);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void start(DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeShort(0);
    }

    private static void writeDataVersion(DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_INT);
        output.writeUTF("DataVersion");
        output.writeInt(P4E3FixtureManifest.DATA_VERSION);
    }

    private static void writeUuid(DataOutput output, int low) throws IOException {
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF("UUID");
        output.writeInt(4);
        output.writeInt(0x5034_4530);
        output.writeInt(0x5232_5100);
        output.writeInt(0);
        output.writeInt(low);
    }

    private static void writeRepeated(DataOutput output, long length, int value)
            throws IOException {
        var bytes = new byte[BUFFER_BYTES];
        java.util.Arrays.fill(bytes, (byte) value);
        var remaining = length;
        while (remaining > 0L) {
            var count = (int) Math.min(remaining, bytes.length);
            output.write(bytes, 0, count);
            remaining -= count;
        }
    }

    @FunctionalInterface
    private interface PayloadWriter {
        void write(DataOutput output) throws IOException;
    }

    private record PreparedStore(
            SkillDefinitionStore store,
            int histories,
            int revisions,
            int journalEntries,
            int journalBytes) {
    }

    private record RecordPlan(Facts facts, PayloadWriter writer) {
        private RecordPlan {
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(writer, "writer");
        }
    }

    private record FillerShape(
            long emptyCompounds,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long scalarFields,
            long zeroArrays,
            long modifiedUtfBytes) {
        private long fieldCount() {
            return emptyCompounds
                    + (listElements > 0L ? 1L : 0L)
                    + (byteArrayElements > 0L ? 1L : 0L)
                    + (intArrayElements > 0L ? 1L : 0L)
                    + (longArrayElements > 0L ? 1L : 0L)
                    + scalarFields
                    + zeroArrays;
        }
    }

    private record FillerEncoding(
            BasePayload base,
            FillerShape shape,
            List<String> names,
            int wideLongs,
            int wideInts,
            int wideShorts,
            Facts facts) {
        private static FillerEncoding minimum(BasePayload base, FillerShape shape) {
            var names = names(
                    Math.toIntExact(shape.fieldCount()), shape.modifiedUtfBytes());
            var minimumExtra = Math.addExact(
                    Math.multiplyExact(3L, shape.fieldCount()), shape.modifiedUtfBytes());
            minimumExtra = Math.addExact(minimumExtra, shape.emptyCompounds());
            if (shape.listElements() > 0L) {
                minimumExtra = Math.addExact(minimumExtra, 5L + shape.listElements());
            }
            if (shape.byteArrayElements() > 0L) {
                minimumExtra = Math.addExact(minimumExtra, 4L + shape.byteArrayElements());
            }
            if (shape.intArrayElements() > 0L) {
                minimumExtra = Math.addExact(
                        minimumExtra, 4L + 4L * shape.intArrayElements());
            }
            if (shape.longArrayElements() > 0L) {
                minimumExtra = Math.addExact(
                        minimumExtra, 4L + 8L * shape.longArrayElements());
            }
            minimumExtra = Math.addExact(minimumExtra, shape.scalarFields());
            minimumExtra = Math.addExact(minimumExtra, 4L * shape.zeroArrays());
            var baseFacts = base.facts();
            var facts = new Facts(
                    Math.addExact(baseFacts.decompressedBytes(), minimumExtra),
                    Math.max(baseFacts.containerDepth(),
                            shape.emptyCompounds() > 0L || shape.listElements() > 0L ? 2 : 1),
                    Math.addExact(baseFacts.compoundContainers(), shape.emptyCompounds()),
                    Math.addExact(baseFacts.compoundFieldEntries(), shape.fieldCount()),
                    Math.addExact(baseFacts.listElements(), shape.listElements()),
                    Math.addExact(baseFacts.byteArrayElements(), shape.byteArrayElements()),
                    Math.addExact(baseFacts.intArrayElements(), shape.intArrayElements()),
                    Math.addExact(baseFacts.longArrayElements(), shape.longArrayElements()),
                    Math.addExact(baseFacts.modifiedUtf8Bytes(), shape.modifiedUtfBytes()),
                    Math.addExact(baseFacts.scalarTags(),
                            shape.scalarFields() + shape.listElements()));
            return new FillerEncoding(base, shape, names, 0, 0, 0, facts);
        }

        private FillerEncoding withPadding(long padding) {
            if (padding < 0L || padding > 7L * shape.scalarFields()) {
                throw new IllegalArgumentException("P4-E3 scalar padding is invalid");
            }
            var longs = Math.toIntExact(padding / 7L);
            var remainder = Math.toIntExact(padding % 7L);
            var ints = remainder / 3;
            var shorts = remainder % 3;
            if ((long) longs + ints + shorts > shape.scalarFields()) {
                throw new IllegalArgumentException("P4-E3 scalar padding lacks fields");
            }
            return new FillerEncoding(
                    base, shape, names, longs, ints, shorts,
                    new Facts(
                            Math.addExact(facts.decompressedBytes(), padding),
                            facts.containerDepth(), facts.compoundContainers(),
                            facts.compoundFieldEntries(), facts.listElements(),
                            facts.byteArrayElements(), facts.intArrayElements(),
                            facts.longArrayElements(), facts.modifiedUtf8Bytes(),
                            facts.scalarTags()));
        }

        private void write(DataOutput output) throws IOException {
            base.writeOpen(output);
            var cursor = 0;
            for (var index = 0L; index < shape.emptyCompounds(); index++) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF(names.get(cursor++));
                output.writeByte(Tag.TAG_END);
            }
            if (shape.listElements() > 0L) {
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF(names.get(cursor++));
                output.writeByte(Tag.TAG_BYTE);
                output.writeInt(Math.toIntExact(shape.listElements()));
                writeRepeated(output, shape.listElements(), 0x5a);
            }
            if (shape.byteArrayElements() > 0L) {
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(Math.toIntExact(shape.byteArrayElements()));
                writeRepeated(output, shape.byteArrayElements(), 0x5a);
            }
            if (shape.intArrayElements() > 0L) {
                output.writeByte(Tag.TAG_INT_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(Math.toIntExact(shape.intArrayElements()));
                for (var index = 0L; index < shape.intArrayElements(); index++) {
                    output.writeInt((int) index);
                }
            }
            if (shape.longArrayElements() > 0L) {
                output.writeByte(Tag.TAG_LONG_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(Math.toIntExact(shape.longArrayElements()));
                for (var index = 0L; index < shape.longArrayElements(); index++) {
                    output.writeLong(0x5034_4530_5232_5100L ^ index);
                }
            }
            for (var index = 0; index < shape.scalarFields(); index++) {
                if (index < wideLongs) {
                    output.writeByte(Tag.TAG_LONG);
                    output.writeUTF(names.get(cursor++));
                    output.writeLong(index);
                } else if (index < wideLongs + wideInts) {
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF(names.get(cursor++));
                    output.writeInt(index);
                } else if (index < wideLongs + wideInts + wideShorts) {
                    output.writeByte(Tag.TAG_SHORT);
                    output.writeUTF(names.get(cursor++));
                    output.writeShort(index);
                } else {
                    output.writeByte(Tag.TAG_BYTE);
                    output.writeUTF(names.get(cursor++));
                    output.writeByte(index);
                }
            }
            for (var index = 0L; index < shape.zeroArrays(); index++) {
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(0);
            }
            if (cursor != names.size()) {
                throw new IOException("P4-E3 filler names were not consumed exactly");
            }
            output.writeByte(Tag.TAG_END);
        }

        private static List<String> names(int count, long targetBytes) {
            if (count < 0 || targetBytes < minimumNameBytes(count)) {
                throw new IllegalArgumentException("P4-E3 filler name target is invalid");
            }
            var result = new ArrayList<String>(count);
            var remaining = targetBytes - minimumNameBytes(count);
            for (var index = 0; index < count; index++) {
                var base = String.format(Locale.ROOT, "n%05d", index);
                var addition = Math.toIntExact(Math.min(remaining, 65_535L - base.length()));
                result.add(base + "x".repeat(addition));
                remaining -= addition;
            }
            if (remaining != 0L) {
                throw new IllegalArgumentException("P4-E3 filler names lack UTF capacity");
            }
            return List.copyOf(result);
        }
    }

    private record BasePayload(CompoundTag attachment, Facts facts) {
        private BasePayload {
            attachment = attachment == null ? null : attachment.copy();
            Objects.requireNonNull(facts, "facts");
        }

        private static BasePayload missing() throws IOException {
            return create(null);
        }

        private static BasePayload ready(P4E3PlayerDataFixture.ReadyPayload payload)
                throws IOException {
            return create(payload.attachment());
        }

        private static BasePayload create(CompoundTag attachment) throws IOException {
            var root = new CompoundTag();
            root.putInt("DataVersion", P4E3FixtureManifest.DATA_VERSION);
            if (attachment != null) {
                var attachments = new CompoundTag();
                attachments.put(PLAYER_SKILLS, attachment.copy());
                root.put(ATTACHMENTS, attachments);
            }
            var metrics = TagMetrics.measure(root, 1);
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                writeOpen(output, attachment);
                output.writeByte(Tag.TAG_END);
            }
            return new BasePayload(
                    attachment,
                    new Facts(
                            bytes.size(), metrics.maxDepth(), metrics.compounds(),
                            metrics.fields(), metrics.listElements(), metrics.byteArrays(),
                            metrics.intArrays(), metrics.longArrays(), metrics.modifiedUtf(),
                            metrics.scalars()));
        }

        private void writeOpen(DataOutput output) throws IOException {
            writeOpen(output, attachment);
        }

        private static void writeOpen(DataOutput output, CompoundTag attachment)
                throws IOException {
            start(output);
            writeDataVersion(output);
            if (attachment != null) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF(ATTACHMENTS);
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF(PLAYER_SKILLS);
                attachment.write(output);
                output.writeByte(Tag.TAG_END);
            }
        }
    }

    private record TagMetrics(
            int maxDepth,
            long compounds,
            long fields,
            long listElements,
            long byteArrays,
            long intArrays,
            long longArrays,
            long modifiedUtf,
            long scalars) {
        private static TagMetrics measure(Tag tag, int depth) {
            if (tag instanceof CompoundTag compound) {
                var result = new TagMetrics(depth, 1, 0, 0, 0, 0, 0, 0, 0);
                for (var key : compound.getAllKeys()) {
                    result = result.plus(measure(compound.get(key), depth + 1))
                            .withField(modifiedUtfBytes(key));
                }
                return result;
            }
            if (tag instanceof ListTag list) {
                var result = new TagMetrics(depth, 0, 0, list.size(), 0, 0, 0, 0, 0);
                for (var element : list) {
                    result = result.plus(measure(element, depth + 1));
                }
                return result;
            }
            if (tag instanceof ByteArrayTag value) {
                return new TagMetrics(depth - 1, 0, 0, 0,
                        value.getAsByteArray().length, 0, 0, 0, 0);
            }
            if (tag instanceof IntArrayTag value) {
                return new TagMetrics(depth - 1, 0, 0, 0, 0,
                        value.getAsIntArray().length, 0, 0, 0);
            }
            if (tag instanceof LongArrayTag value) {
                return new TagMetrics(depth - 1, 0, 0, 0, 0, 0,
                        value.getAsLongArray().length, 0, 0);
            }
            if (tag instanceof StringTag value) {
                return new TagMetrics(depth - 1, 0, 0, 0, 0, 0, 0,
                        modifiedUtfBytes(value.getAsString()), 1);
            }
            if (tag instanceof ByteTag || tag instanceof ShortTag || tag instanceof IntTag
                    || tag instanceof LongTag || tag instanceof FloatTag
                    || tag instanceof DoubleTag) {
                return new TagMetrics(depth - 1, 0, 0, 0, 0, 0, 0, 0, 1);
            }
            throw new IllegalArgumentException("unsupported P4-E3 Ready tag kind " + tag.getId());
        }

        private TagMetrics plus(TagMetrics other) {
            return new TagMetrics(
                    Math.max(maxDepth, other.maxDepth),
                    compounds + other.compounds,
                    fields + other.fields,
                    listElements + other.listElements,
                    byteArrays + other.byteArrays,
                    intArrays + other.intArrays,
                    longArrays + other.longArrays,
                    modifiedUtf + other.modifiedUtf,
                    scalars + other.scalars);
        }

        private TagMetrics withField(long nameBytes) {
            return new TagMetrics(
                    maxDepth, compounds, fields + 1L, listElements,
                    byteArrays, intArrays, longArrays, modifiedUtf + nameBytes, scalars);
        }

        private static int modifiedUtfBytes(String value) {
            var count = 0;
            for (var index = 0; index < value.length(); index++) {
                var character = value.charAt(index);
                count += character >= 0x0001 && character <= 0x007f
                        ? 1 : character <= 0x07ff ? 2 : 3;
            }
            if (count > 65_535) {
                throw new IllegalArgumentException("P4-E3 string exceeds modified UTF");
            }
            return count;
        }
    }

    private record Facts(
            long decompressedBytes,
            int containerDepth,
            long compoundContainers,
            long compoundFieldEntries,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long modifiedUtf8Bytes,
            long scalarTags) {
        private Facts {
            if (decompressedBytes < 0L || containerDepth < 0
                    || compoundContainers < 0L || compoundFieldEntries < 0L
                    || listElements < 0L || byteArrayElements < 0L
                    || intArrayElements < 0L || longArrayElements < 0L
                    || modifiedUtf8Bytes < 0L || scalarTags < 0L) {
                throw new IllegalArgumentException("negative P4-E3 structural fact");
            }
        }

        private static Facts zero() {
            return new Facts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private Facts plus(Facts other) {
            return new Facts(
                    Math.addExact(decompressedBytes, other.decompressedBytes),
                    Math.max(containerDepth, other.containerDepth),
                    Math.addExact(compoundContainers, other.compoundContainers),
                    Math.addExact(compoundFieldEntries, other.compoundFieldEntries),
                    Math.addExact(listElements, other.listElements),
                    Math.addExact(byteArrayElements, other.byteArrayElements),
                    Math.addExact(intArrayElements, other.intArrayElements),
                    Math.addExact(longArrayElements, other.longArrayElements),
                    Math.addExact(modifiedUtf8Bytes, other.modifiedUtf8Bytes),
                    Math.addExact(scalarTags, other.scalarTags));
        }

        private Facts subtract(Facts other) {
            return new Facts(
                    Math.subtractExact(decompressedBytes, other.decompressedBytes),
                    containerDepth,
                    Math.subtractExact(compoundContainers, other.compoundContainers),
                    Math.subtractExact(compoundFieldEntries, other.compoundFieldEntries),
                    Math.subtractExact(listElements, other.listElements),
                    Math.subtractExact(byteArrayElements, other.byteArrayElements),
                    Math.subtractExact(intArrayElements, other.intArrayElements),
                    Math.subtractExact(longArrayElements, other.longArrayElements),
                    Math.subtractExact(modifiedUtf8Bytes, other.modifiedUtf8Bytes),
                    Math.subtractExact(scalarTags, other.scalarTags));
        }
    }

    private record Header(int fileNameBytes, long targetPhysicalBytes) {
        private Header {
            if (fileNameBytes < 0 || targetPhysicalBytes < 0L) {
                throw new IllegalArgumentException("invalid P4-E3 gzip header");
            }
        }

        private static Header canonical() {
            return new Header(0, 0L);
        }
    }

    private record WriteFacts(long physicalBytes, long decompressedBytes) {
    }

    private static final class Wire {
        private Wire() {
        }

        private static WriteFacts write(
                Path path, Header header, PayloadWriter payload) throws IOException {
            var raw = path == null
                    ? new NullOutputStream()
                    : Files.newOutputStream(
                            path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (raw) {
                var counting = new CountingOutputStream(raw);
                var headerCrc = new CRC32();
                writeHeader(counting, headerCrc, header.fileNameBytes());
                var bodyCrc = new CRC32();
                var deflater = new Deflater(COMPRESSION_LEVEL, true);
                long decompressed;
                try {
                    var compressed = new DeflaterOutputStream(
                            counting, deflater, BUFFER_BYTES, false);
                    var body = new CountingCrcOutputStream(compressed, bodyCrc);
                    var output = new DataOutputStream(body);
                    payload.write(output);
                    output.flush();
                    compressed.finish();
                    decompressed = body.count();
                } finally {
                    deflater.end();
                }
                writeLittleEndianInt(counting, bodyCrc.getValue());
                writeLittleEndianInt(counting, decompressed & 0xffff_ffffL);
                counting.flush();
                return new WriteFacts(counting.count(), decompressed);
            } catch (IOException | RuntimeException exception) {
                if (path != null) {
                    Files.deleteIfExists(path);
                }
                throw exception;
            }
        }

        private static void writeHeader(OutputStream output, CRC32 crc, int fileNameBytes)
                throws IOException {
            writeHeaderByte(output, crc, 0x1f);
            writeHeaderByte(output, crc, 0x8b);
            writeHeaderByte(output, crc, 8);
            writeHeaderByte(output, crc, fileNameBytes == 0 ? 0 : 0x0a);
            for (var index = 0; index < 4; index++) {
                writeHeaderByte(output, crc, 0);
            }
            writeHeaderByte(output, crc, 2);
            writeHeaderByte(output, crc, 255);
            if (fileNameBytes > 0) {
                var buffer = new byte[BUFFER_BYTES];
                java.util.Arrays.fill(buffer, (byte) 0x5a);
                var remaining = fileNameBytes;
                while (remaining > 0) {
                    var count = Math.min(remaining, buffer.length);
                    output.write(buffer, 0, count);
                    crc.update(buffer, 0, count);
                    remaining -= count;
                }
                writeHeaderByte(output, crc, 0);
                var low = (int) crc.getValue() & 0xffff;
                output.write(low & 0xff);
                output.write(low >>> 8);
            }
        }

        private static void writeHeaderByte(OutputStream output, CRC32 crc, int value)
                throws IOException {
            output.write(value);
            crc.update(value);
        }

        private static void writeLittleEndianInt(OutputStream output, long value)
                throws IOException {
            output.write((int) value & 0xff);
            output.write((int) (value >>> 8) & 0xff);
            output.write((int) (value >>> 16) & 0xff);
            output.write((int) (value >>> 24) & 0xff);
        }
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length);
            count += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        private long count() {
            return count;
        }
    }

    private static final class CountingCrcOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final CRC32 crc;
        private long count;

        private CountingCrcOutputStream(OutputStream delegate, CRC32 crc) {
            this.delegate = delegate;
            this.crc = crc;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            crc.update(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            delegate.write(bytes, offset, length);
            crc.update(bytes, offset, length);
            count += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        private long count() {
            return count;
        }
    }

    private static final class NullOutputStream extends OutputStream {
        @Override
        public void write(int value) {
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
        }
    }
}
