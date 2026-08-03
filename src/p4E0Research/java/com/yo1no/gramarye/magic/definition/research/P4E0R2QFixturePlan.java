package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.zip.Deflater;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Pure, research-only arithmetic blueprint for the locked R2Q profile.
 *
 * <p>The observed vector is derived from fixture-component facts below. The locked profile is not
 * an input to that derivation; callers compare the independently derived vector with the profile.
 * This type neither materializes the full profile nor starts a child JVM. In particular, the
 * playerdata/NBT counter envelope and the typed latest/equipped root-projection envelope are
 * distinct coordinates which are combined only by the future qualification child.</p>
 */
final class P4E0R2QFixturePlan {
    static final String PROFILE_NAME = P4E0R2QProfile.PROFILE_NAME;
    static final long FIXED_FRAMING_BYTES = 382_335_895L;
    static final long PAYLOAD_BYTES = 154_535_017L;
    static final long NON_PAYLOAD_BYTE_ARRAY_ELEMENTS = 301_989_688L;

    private static final int SELECTED_RECORDS = 2_048;
    private static final long COMPRESSED_BYTES_PER_FILE_WITNESS = 33_559_514L;
    private static final long COMPRESSED_BYTES_TOTAL = 268_440_533L;
    private static final int CANONICAL_DATA_VERSION = 3_955;

    private P4E0R2QFixturePlan() {
    }

    static Blueprint locked() {
        return Holder.BLUEPRINT;
    }

    private static Blueprint createLocked() {
        var directory = new DirectoryShape(
                2_048, 2_048, 1_024, 1, 1_023, 1_024);
        var roots = new RootProjectionShape(
                2_049,
                1_023,
                58,
                57,
                59_391,
                61_440,
                4_096,
                65_536,
                65_537,
                true);
        var structural = StructuralComposition.locked();
        var jointRecords = P4E0R2QJointRecords.build(structural);
        return new Blueprint(
                directory,
                structural,
                jointRecords,
                tuneCompressedHeaders(jointRecords.canonicalPhysicalBytes()),
                roots);
    }

    /**
     * Produces legal FHCRC+FNAME header lengths for an already measured canonical file set.
     * One non-NUL FNAME byte, its terminating NUL, and FHCRC add at least four physical bytes.
     * The returned facts can be rendered directly through {@link HeaderTuning#headerOptions()}.
     */
    static CompressedTuning tuneCompressedHeaders(List<Long> canonicalPhysicalBytes) {
        Objects.requireNonNull(canonicalPhysicalBytes, "canonicalPhysicalBytes");
        if (canonicalPhysicalBytes.size() != SELECTED_RECORDS) {
            throw new IllegalArgumentException("R2Q compressed plan requires every selected file");
        }
        var targets = new long[canonicalPhysicalBytes.size()];
        var baselineTotal = 0L;
        for (var index = 0; index < canonicalPhysicalBytes.size(); index++) {
            var baseline = Objects.requireNonNull(
                    canonicalPhysicalBytes.get(index), "canonicalPhysicalBytes element");
            if (baseline <= 0 || baseline > COMPRESSED_BYTES_PER_FILE_WITNESS) {
                throw new IllegalArgumentException("R2Q canonical compressed size is invalid");
            }
            targets[index] = baseline;
            baselineTotal = Math.addExact(baselineTotal, baseline);
        }
        if (baselineTotal > COMPRESSED_BYTES_TOTAL) {
            throw new IllegalArgumentException("R2Q canonical files exceed aggregate compressed target");
        }

        var remaining = Math.subtractExact(COMPRESSED_BYTES_TOTAL, baselineTotal);
        var firstCapacity = Math.subtractExact(
                COMPRESSED_BYTES_PER_FILE_WITNESS, canonicalPhysicalBytes.getFirst());
        if (remaining < firstCapacity || firstCapacity < 4) {
            throw new IllegalArgumentException(
                    "R2Q compressed target cannot realize the per-file maximum");
        }
        targets[0] = COMPRESSED_BYTES_PER_FILE_WITNESS;
        remaining = Math.subtractExact(remaining, firstCapacity);

        for (var index = 1; index < targets.length && remaining > 0; index++) {
            var capacity = Math.subtractExact(
                    COMPRESSED_BYTES_PER_FILE_WITNESS, canonicalPhysicalBytes.get(index));
            var addition = Math.min(remaining, capacity);
            if (addition > 0 && addition < 4) {
                var extra = 4L - addition;
                var donor = findTunedDonor(
                        canonicalPhysicalBytes, targets, index, 4L + extra);
                if (donor < 0 || capacity < 4) {
                    throw new IllegalArgumentException(
                            "R2Q gzip optional-field framing cannot express the final addition");
                }
                targets[donor] = Math.subtractExact(targets[donor], extra);
                addition = 4;
                remaining = 4;
            }
            if (addition > 0 && addition < 4) {
                throw new IllegalArgumentException("R2Q gzip header addition is not legal");
            }
            targets[index] = Math.addExact(targets[index], addition);
            remaining = Math.subtractExact(remaining, addition);
        }
        if (remaining != 0) {
            throw new IllegalArgumentException("R2Q compressed aggregate has insufficient headroom");
        }

        var entries = new ArrayList<HeaderTuning>(targets.length);
        var total = 0L;
        var perFileMaximumIndex = -1;
        var aggregateOverrunIndex = -1;
        var compensationIndex = -1;
        for (var index = 0; index < targets.length; index++) {
            var baseline = canonicalPhysicalBytes.get(index);
            var target = targets[index];
            var increase = Math.subtractExact(target, baseline);
            if ((increase > 0 && increase < 4)
                    || target > COMPRESSED_BYTES_PER_FILE_WITNESS) {
                throw new IllegalArgumentException("R2Q gzip target is not legally tunable");
            }
            var fileNameBytes = increase == 0 ? 0 : Math.toIntExact(increase - 3);
            entries.add(new HeaderTuning(index, baseline, target, fileNameBytes));
            total = Math.addExact(total, target);
            if (perFileMaximumIndex < 0 && target == COMPRESSED_BYTES_PER_FILE_WITNESS) {
                perFileMaximumIndex = index;
            }
            if (aggregateOverrunIndex < 0
                    && increase >= 4
                    && target < COMPRESSED_BYTES_PER_FILE_WITNESS) {
                aggregateOverrunIndex = index;
            }
            if (index != perFileMaximumIndex && increase >= 5) {
                compensationIndex = index;
            }
        }
        if (total != COMPRESSED_BYTES_TOTAL
                || perFileMaximumIndex < 0
                || aggregateOverrunIndex < 0
                || compensationIndex < 0) {
            throw new IllegalArgumentException(
                    "R2Q compressed tuning lacks an independent +1 mutation site");
        }
        return new CompressedTuning(
                entries,
                baselineTotal,
                total,
                perFileMaximumIndex,
                aggregateOverrunIndex,
                compensationIndex);
    }

    static NegativeFixture negativeFixture(P4E0R2QCasePlan.CaseSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.kind() != P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
            throw new IllegalArgumentException("R2Q fixture is not a counter negative");
        }
        var target = spec.targetCounter().orElseThrow();
        var template = mutationTemplate(target);
        var recipe = template.recipe();
        if (spec.mutationKind() != recipe.mutationKind()
                || !spec.coupledCounters().equals(recipe.coupledCounters())) {
            throw new IllegalArgumentException("R2Q case does not match its physical recipe");
        }
        var blueprint = locked();
        var baseline = blueprint.counters();
        var derivation = template.derive(baseline);
        var observed = derivation.observedCounters().value(target);
        var proof = new PhysicalMutationProof(
                target,
                recipe.mutationKind(),
                recipe.proofKind(),
                recipe.coupledCounters(),
                baseline.value(target),
                observed);
        return new NegativeFixture(
                derivation.observedCounters(),
                proof,
                derivation,
                PhysicalFixtureBinding.from(derivation));
    }

    static DataVersionFixture dataVersionFixture(P4E0R2QCasePlan.CaseSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                || spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
            throw new IllegalArgumentException("R2Q fixture is not a DataVersion control");
        }
        var physicalRoot = new CompoundTag();
        physicalRoot.putInt("DataVersion", CANONICAL_DATA_VERSION);
        switch (spec.mutationKind()) {
            case REMOVE_DATA_VERSION -> physicalRoot.remove("DataVersion");
            case REPLACE_DATA_VERSION_WITH_WRONG_TYPE ->
                    physicalRoot.putString("DataVersion", "wrong-type");
            case REPLACE_DATA_VERSION_WITH_WRONG_VALUE ->
                    physicalRoot.putInt("DataVersion", CANONICAL_DATA_VERSION - 1);
            default -> throw new IllegalArgumentException(
                    "R2Q DataVersion case has the wrong typed mutation");
        }
        var materialized = physicalRoot.get("DataVersion");
        var state = materialized == null
                ? DataVersionTagState.MISSING
                : materialized instanceof StringTag
                        ? DataVersionTagState.STRING_TAG
                        : materialized instanceof IntTag intTag
                                        && intTag.getAsInt() != CANONICAL_DATA_VERSION
                                ? DataVersionTagState.INT_TAG_WRONG_VALUE
                                : throwInvalidDataVersionShape();
        var proofKind = switch (state) {
            case MISSING -> DataVersionProofKind.REMOVE_CANONICAL_INT_TAG;
            case STRING_TAG -> DataVersionProofKind.REPLACE_WITH_STRING_TAG;
            case INT_TAG_WRONG_VALUE -> DataVersionProofKind.REPLACE_WITH_DIFFERENT_INT_TAG;
        };
        var expectedMutation = switch (state) {
            case MISSING -> P4E0R2QCasePlan.MutationKind.REMOVE_DATA_VERSION;
            case STRING_TAG ->
                    P4E0R2QCasePlan.MutationKind.REPLACE_DATA_VERSION_WITH_WRONG_TYPE;
            case INT_TAG_WRONG_VALUE ->
                    P4E0R2QCasePlan.MutationKind.REPLACE_DATA_VERSION_WITH_WRONG_VALUE;
        };
        if (spec.mutationKind() != expectedMutation) {
            throw new IllegalArgumentException("R2Q DataVersion case has the wrong typed mutation");
        }
        return new DataVersionFixture(
                spec.kind(),
                expectedMutation,
                proofKind,
                state,
                CANONICAL_DATA_VERSION,
                materialized instanceof IntTag intTag ? intTag.getAsInt() : 0,
                0);
    }

    private static DataVersionTagState throwInvalidDataVersionShape() {
        throw new IllegalArgumentException("R2Q DataVersion mutation did not produce its shape");
    }

    static MutationRecipe recipeFor(P4E0R2QProfile.Counter counter) {
        return mutationTemplate(Objects.requireNonNull(counter, "counter")).recipe();
    }

    private static MutationTemplate mutationTemplate(P4E0R2QProfile.Counter counter) {
        return switch (counter) {
            case DIRECTORY_ENTRIES -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_IRRELEVANT_DIRECTORY_ENTRY,
                    MutationMechanism.FILESYSTEM_IRRELEVANT_OCCURRENCE,
                    delta(counter, 1));
            case RELEVANT_RECORDS -> {
                var witness = SelectedRecordWitnessHolder.WITNESS;
                yield template(
                        counter,
                        P4E0R2QCasePlan.MutationKind.ADD_SELECTED_PRIMARY_RECORD,
                        MutationMechanism.RESELECT_IRRELEVANT_AS_CANONICAL_PRIMARY,
                        delta(counter, 1),
                        delta(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL,
                                witness.compressedBytes()),
                        delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL,
                                witness.decompressedBytes()),
                        delta(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL,
                                witness.compoundContainers()),
                        delta(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL,
                                witness.compoundFieldEntries()),
                        delta(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL,
                                witness.modifiedUtf8Bytes()),
                        delta(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL,
                                witness.scalarTags()));
            }
            case COMPRESSED_BYTES_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_GZIP_HEADER_BYTE_REBALANCE_TOTAL,
                    MutationMechanism.RESIZE_LEGAL_GZIP_FNAME,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL, 1));
            case DECOMPRESSED_BYTES_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_DECOMPRESSED_PAYLOAD_BYTE_REBALANCE_TOTAL,
                    MutationMechanism.WIDEN_NUMERIC_SCALAR,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case CONTAINER_DEPTH_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_CONTAINER_LEVEL,
                    MutationMechanism.INSERT_EMPTY_NAMED_COMPOUND,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 4));
            case COMPOUND_CONTAINERS_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_COMPOUND_CONTAINER_REBALANCE_TOTAL,
                    MutationMechanism.INSERT_EMPTY_NAMED_COMPOUND,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 4));
            case COMPOUND_FIELD_ENTRIES_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_COMPOUND_FIELD_REBALANCE_TOTAL,
                    MutationMechanism.INSERT_ONE_BYTE_NAMED_EMPTY_BYTE_ARRAY,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 8));
            case LIST_ELEMENTS_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_LIST_ELEMENT_REBALANCE_TOTAL,
                    MutationMechanism.APPEND_BYTE_LIST_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case BYTE_ARRAY_ELEMENTS_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_BYTE_ARRAY_ELEMENT_REBALANCE_TOTAL,
                    MutationMechanism.APPEND_BYTE_ARRAY_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case INT_ARRAY_ELEMENTS_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_INT_ARRAY_ELEMENT_REBALANCE_TOTAL,
                    MutationMechanism.APPEND_INT_ARRAY_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 4));
            case LONG_ARRAY_ELEMENTS_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_LONG_ARRAY_ELEMENT_REBALANCE_TOTAL,
                    MutationMechanism.APPEND_LONG_ARRAY_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 8));
            case MODIFIED_UTF8_BYTES_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_MODIFIED_UTF_BYTE_REBALANCE_TOTAL,
                    MutationMechanism.EXTEND_MODIFIED_UTF8_VALUE,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case SCALAR_TAGS_PER_FILE -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_SCALAR_TAG_REBALANCE_TOTAL,
                    MutationMechanism.RETYPE_ZERO_BYTE_ARRAY_AS_BYTE_SCALAR,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, -3));
            case COMPRESSED_BYTES_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_GZIP_HEADER_BYTE_TO_AGGREGATE,
                    MutationMechanism.RESIZE_AND_REDISTRIBUTE_LEGAL_GZIP_FNAME,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE, 1));
            case DECOMPRESSED_BYTES_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_DECOMPRESSED_PAYLOAD_BYTE_TO_AGGREGATE,
                    MutationMechanism.WIDEN_AND_REDISTRIBUTE_NUMERIC_SCALAR,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE, 1));
            case COMPOUND_CONTAINERS_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_COMPOUND_CONTAINER_TO_AGGREGATE,
                    MutationMechanism.INSERT_AND_REDISTRIBUTE_EMPTY_COMPOUND,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 4));
            case COMPOUND_FIELD_ENTRIES_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_COMPOUND_FIELD_TO_AGGREGATE,
                    MutationMechanism.INSERT_AND_REDISTRIBUTE_EMPTY_BYTE_ARRAY_FIELD,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 8));
            case LIST_ELEMENTS_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_LIST_ELEMENT_TO_AGGREGATE,
                    MutationMechanism.APPEND_AND_REDISTRIBUTE_BYTE_LIST_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case BYTE_ARRAY_ELEMENTS_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_BYTE_ARRAY_ELEMENT_TO_AGGREGATE,
                    MutationMechanism.APPEND_AND_REDISTRIBUTE_BYTE_ARRAY_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case INT_ARRAY_ELEMENTS_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_INT_ARRAY_ELEMENT_TO_AGGREGATE,
                    MutationMechanism.APPEND_AND_REDISTRIBUTE_INT_ARRAY_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 4));
            case LONG_ARRAY_ELEMENTS_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_LONG_ARRAY_ELEMENT_TO_AGGREGATE,
                    MutationMechanism.APPEND_AND_REDISTRIBUTE_LONG_ARRAY_ELEMENT,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 8));
            case MODIFIED_UTF8_BYTES_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_MODIFIED_UTF_BYTE_TO_AGGREGATE,
                    MutationMechanism.EXTEND_AND_REDISTRIBUTE_MODIFIED_UTF8_VALUE,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, 1));
            case SCALAR_TAGS_TOTAL -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_SCALAR_TAG_TO_AGGREGATE,
                    MutationMechanism.RETYPE_AND_REDISTRIBUTE_ZERO_BYTE_ARRAY,
                    delta(counter, 1),
                    delta(P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE, 1),
                    delta(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, -3));
            case ATTACHMENT_ADMISSIONS -> {
                var witness = ReadyAttachmentWitnessHolder.WITNESS;
                var deltas = new ArrayList<CounterDelta>();
                deltas.add(delta(counter, 1));
                witness.appendPhysicalDeltas(deltas);
                yield new MutationTemplate(
                        counter,
                        P4E0R2QCasePlan.MutationKind.ADD_READY_ATTACHMENT_ADMISSION,
                        MutationMechanism.ADMIT_PRODUCTION_READY_ATTACHMENT,
                        deltas);
            }
            case RAW_ROOT_CLAIMS -> template(
                    counter,
                    P4E0R2QCasePlan.MutationKind.ADD_EQUIPPED_RAW_ROOT_CLAIM,
                    MutationMechanism.ADD_TYPED_EQUIPPED_ROOT_CLAIM,
                    delta(counter, 1));
        };
    }

    private static MutationTemplate template(
            P4E0R2QProfile.Counter target,
            P4E0R2QCasePlan.MutationKind mutation,
            MutationMechanism mechanism,
            CounterDelta... deltas) {
        return new MutationTemplate(target, mutation, mechanism, List.of(deltas));
    }

    private static CounterDelta delta(P4E0R2QProfile.Counter counter, long amount) {
        return new CounterDelta(counter, amount);
    }

    private static PhysicalProofKind proofKindFor(P4E0R2QProfile.Counter counter) {
        return switch (counter) {
            case DIRECTORY_ENTRIES -> PhysicalProofKind.FILESYSTEM_DIRECTORY_OCCURRENCE;
            case RELEVANT_RECORDS -> PhysicalProofKind.PRIMARY_SOURCE_RESELECTION;
            case COMPRESSED_BYTES_PER_FILE, COMPRESSED_BYTES_TOTAL ->
                    PhysicalProofKind.LEGAL_SINGLE_MEMBER_GZIP_HEADER_TUNING;
            case ATTACHMENT_ADMISSIONS -> PhysicalProofKind.READY_ATTACHMENT_ADMISSION;
            case RAW_ROOT_CLAIMS -> PhysicalProofKind.TYPED_ROOT_PROJECTION;
            default -> PhysicalProofKind.LEGAL_NBT_COMPONENT_REBALANCE;
        };
    }

    private static int findTunedDonor(
            List<Long> baselines, long[] targets, int exclusiveEnd, long minimumIncrease) {
        // Do not consume index zero: it is the mandatory per-file exact witness.
        for (var index = exclusiveEnd - 1; index >= 1; index--) {
            if (targets[index] - baselines.get(index) >= minimumIncrease) {
                return index;
            }
        }
        return -1;
    }

    record Blueprint(
            DirectoryShape directory,
            StructuralComposition structural,
            P4E0R2QJointRecords.Plan jointRecords,
            CompressedTuning compressed,
            RootProjectionShape roots) {
        Blueprint {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(structural, "structural");
            Objects.requireNonNull(jointRecords, "jointRecords");
            Objects.requireNonNull(compressed, "compressed");
            Objects.requireNonNull(roots, "roots");
            if (P4E0R2QProfile.Counter.values().length != 25
                    || directory.selectedPrimaries() != structural.recordCount()
                    || directory.selectedPrimaries() != jointRecords.records().size()
                    || directory.selectedPrimaries() != compressed.files().size()
                    || jointRecords.aggregate().decompressedBytes()
                            != structural.decompressedBytes()
                    || jointRecords.aggregate().byteArrayElements()
                            != structural.byteArrayElements()
                    || FIXED_FRAMING_BYTES != structural.fixedFramingBytes()
                    || PAYLOAD_BYTES != structural.payloadBytes()
                    || NON_PAYLOAD_BYTE_ARRAY_ELEMENTS
                            != structural.nonPayloadByteArrayElements()) {
                throw new IllegalArgumentException("R2Q exact arithmetic blueprint changed");
            }
        }

        P4E0R2QProfile.CounterValues counters() {
            var peaks = structural.peaks();
            return new P4E0R2QProfile.CounterValues(
                    directory.totalEntries(),
                    directory.selectedPrimaries(),
                    compressed.maximumPhysicalBytes(),
                    peaks.decompressedBytesPerFile(),
                    peaks.containerDepthPerFile(),
                    peaks.compoundContainersPerFile(),
                    peaks.compoundFieldEntriesPerFile(),
                    peaks.listElementsPerFile(),
                    peaks.byteArrayElementsPerFile(),
                    peaks.intArrayElementsPerFile(),
                    peaks.longArrayElementsPerFile(),
                    peaks.modifiedUtf8BytesPerFile(),
                    peaks.scalarTagsPerFile(),
                    compressed.tunedTotal(),
                    structural.decompressedBytes(),
                    structural.compoundContainers(),
                    structural.compoundFieldEntries(),
                    structural.listElements(),
                    structural.byteArrayElements(),
                    structural.intArrayElements(),
                    structural.longArrayElements(),
                    structural.modifiedUtf8Bytes(),
                    structural.scalarTags(),
                    directory.readyAdmissions(),
                    roots.rawClaims());
        }
    }

    record DirectoryShape(
            int selectedPrimaries,
            int ignoredOldOrIrrelevant,
            int readyAdmissions,
            int mixedFamilyReadyAdmissions,
            int minimalReadyAdmissions,
            int selectedWithoutGramaryeAttachment) {
        DirectoryShape {
            if (selectedPrimaries < 0 || ignoredOldOrIrrelevant < 0
                    || readyAdmissions < 0 || mixedFamilyReadyAdmissions != 1
                    || minimalReadyAdmissions < 0 || selectedWithoutGramaryeAttachment < 0
                    || readyAdmissions != mixedFamilyReadyAdmissions + minimalReadyAdmissions
                    || selectedPrimaries
                            != readyAdmissions + selectedWithoutGramaryeAttachment) {
                throw new IllegalArgumentException("R2Q directory shape is invalid");
            }
        }

        int totalEntries() {
            return Math.addExact(selectedPrimaries, ignoredOldOrIrrelevant);
        }

        int overDirectoryEntries() {
            return Math.addExact(totalEntries(), 1);
        }

        int overRelevantRecords() {
            return Math.addExact(selectedPrimaries, 1);
        }

        int overAttachmentAdmissions() {
            return Math.addExact(readyAdmissions, 1);
        }
    }

    record ComponentFacts(
            String code,
            int recordCount,
            long decompressedBytes,
            long compoundContainers,
            long compoundFieldEntries,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long modifiedUtf8Bytes,
            long scalarTags,
            long payloadBytes) {
        ComponentFacts {
            if (code == null || !code.matches("[A-Z][A-Z0-9_]*") || recordCount <= 0) {
                throw new IllegalArgumentException("R2Q component identity is invalid");
            }
            var facts = new long[] {
                decompressedBytes,
                compoundContainers,
                compoundFieldEntries,
                listElements,
                byteArrayElements,
                intArrayElements,
                longArrayElements,
                modifiedUtf8Bytes,
                scalarTags,
                payloadBytes
            };
            for (var fact : facts) {
                if (fact < 0) {
                    throw new IllegalArgumentException("R2Q component fact is negative");
                }
            }
            if (payloadBytes > byteArrayElements || payloadBytes > decompressedBytes) {
                throw new IllegalArgumentException("R2Q payload is outside its physical envelope");
            }
        }
    }

    record PeakWitnesses(
            long decompressedBytesPerFile,
            long containerDepthPerFile,
            long compoundContainersPerFile,
            long compoundFieldEntriesPerFile,
            long listElementsPerFile,
            long byteArrayElementsPerFile,
            long intArrayElementsPerFile,
            long longArrayElementsPerFile,
            long modifiedUtf8BytesPerFile,
            long scalarTagsPerFile) {
        PeakWitnesses {
            var peaks = new long[] {
                decompressedBytesPerFile,
                containerDepthPerFile,
                compoundContainersPerFile,
                compoundFieldEntriesPerFile,
                listElementsPerFile,
                byteArrayElementsPerFile,
                intArrayElementsPerFile,
                longArrayElementsPerFile,
                modifiedUtf8BytesPerFile,
                scalarTagsPerFile
            };
            for (var peak : peaks) {
                if (peak <= 0) {
                    throw new IllegalArgumentException("R2Q peak witness is not positive");
                }
            }
        }
    }

    record StructuralComposition(List<ComponentFacts> components, PeakWitnesses peaks) {
        StructuralComposition {
            components = List.copyOf(Objects.requireNonNull(components, "components"));
            Objects.requireNonNull(peaks, "peaks");
            if (components.size() != 3
                    || !components.stream().map(ComponentFacts::code).toList().equals(
                            List.of("HCA_WITNESS", "LOW_COMPRESSION_WITNESS", "AGGREGATE_FILLERS"))
                    || components.stream().mapToInt(ComponentFacts::recordCount).sum()
                            != SELECTED_RECORDS) {
                throw new IllegalArgumentException("R2Q component multiplicities changed");
            }
            requireDistribution(components, ComponentFacts::decompressedBytes,
                    peaks.decompressedBytesPerFile());
            requireDistribution(components, ComponentFacts::compoundContainers,
                    peaks.compoundContainersPerFile());
            requireDistribution(components, ComponentFacts::compoundFieldEntries,
                    peaks.compoundFieldEntriesPerFile());
            requireDistribution(components, ComponentFacts::listElements,
                    peaks.listElementsPerFile());
            requireDistribution(components, ComponentFacts::byteArrayElements,
                    peaks.byteArrayElementsPerFile());
            requireDistribution(components, ComponentFacts::intArrayElements,
                    peaks.intArrayElementsPerFile());
            requireDistribution(components, ComponentFacts::longArrayElements,
                    peaks.longArrayElementsPerFile());
            requireDistribution(components, ComponentFacts::modifiedUtf8Bytes,
                    peaks.modifiedUtf8BytesPerFile());
            requireDistribution(components, ComponentFacts::scalarTags,
                    peaks.scalarTagsPerFile());
            if (peaks.containerDepthPerFile() != 512L) {
                throw new IllegalArgumentException("R2Q depth witness changed");
            }
        }

        static StructuralComposition locked() {
            return new StructuralComposition(
                    List.of(
                            new ComponentFacts(
                                    "HCA_WITNESS",
                                    1,
                                    268_435_456L,
                                    1L,
                                    3L,
                                    0L,
                                    268_435_384L,
                                    4L,
                                    0L,
                                    31L,
                                    1L,
                                    0L),
                            new ComponentFacts(
                                    "LOW_COMPRESSION_WITNESS",
                                    1,
                                    33_554_376L,
                                    1L,
                                    3L,
                                    0L,
                                    33_554_304L,
                                    4L,
                                    0L,
                                    31L,
                                    1L,
                                    0L),
                            new ComponentFacts(
                                    "AGGREGATE_FILLERS",
                                    2_046,
                                    234_881_080L,
                                    131_070L,
                                    524_282L,
                                    131_072L,
                                    154_535_017L,
                                    131_064L,
                                    131_072L,
                                    75_497_410L,
                                    458_750L,
                                    154_535_017L)),
                    new PeakWitnesses(
                            268_435_456L,
                            512L,
                            1_024L,
                            65_537L,
                            65_536L,
                            268_435_384L,
                            65_536L,
                            65_536L,
                            67_107_692L,
                            65_537L));
        }

        int recordCount() {
            return components.stream().mapToInt(ComponentFacts::recordCount).sum();
        }

        long decompressedBytes() {
            return sum(ComponentFacts::decompressedBytes);
        }

        long compoundContainers() {
            return sum(ComponentFacts::compoundContainers);
        }

        long compoundFieldEntries() {
            return sum(ComponentFacts::compoundFieldEntries);
        }

        long listElements() {
            return sum(ComponentFacts::listElements);
        }

        long byteArrayElements() {
            return sum(ComponentFacts::byteArrayElements);
        }

        long intArrayElements() {
            return sum(ComponentFacts::intArrayElements);
        }

        long longArrayElements() {
            return sum(ComponentFacts::longArrayElements);
        }

        long modifiedUtf8Bytes() {
            return sum(ComponentFacts::modifiedUtf8Bytes);
        }

        long scalarTags() {
            return sum(ComponentFacts::scalarTags);
        }

        long payloadBytes() {
            return sum(ComponentFacts::payloadBytes);
        }

        long fixedFramingBytes() {
            return Math.subtractExact(decompressedBytes(), payloadBytes());
        }

        long nonPayloadByteArrayElements() {
            return Math.subtractExact(byteArrayElements(), payloadBytes());
        }

        private long sum(ToLongFunction<ComponentFacts> coordinate) {
            var total = 0L;
            for (var component : components) {
                total = Math.addExact(total, coordinate.applyAsLong(component));
            }
            return total;
        }

        private static void requireDistribution(
                List<ComponentFacts> components,
                ToLongFunction<ComponentFacts> coordinate,
                long perFileMaximum) {
            var total = 0L;
            var recordCount = 0L;
            var exactWitnessPossible = false;
            for (var component : components) {
                var value = coordinate.applyAsLong(component);
                var capacity = Math.multiplyExact(component.recordCount(), perFileMaximum);
                if (value > capacity) {
                    throw new IllegalArgumentException("R2Q component exceeds per-file capacity");
                }
                if (value >= perFileMaximum
                        && value - perFileMaximum
                                <= Math.multiplyExact(component.recordCount() - 1L, perFileMaximum)) {
                    exactWitnessPossible = true;
                }
                total = Math.addExact(total, value);
                recordCount = Math.addExact(recordCount, component.recordCount());
            }
            if (!exactWitnessPossible
                    || total < perFileMaximum
                    || total > Math.multiplyExact(recordCount, perFileMaximum)) {
                throw new IllegalArgumentException("R2Q peak witness cannot be distributed");
            }
        }
    }

    record RootProjectionShape(
            int latestClaims,
            int recordsWith58Equipped,
            int equippedClaimsPerFullRecord,
            int equippedClaimsOnFinalRecord,
            int equippedClaims,
            int playerClaims,
            int journalClaims,
            int rawClaims,
            int overRawClaims,
            boolean separateFromPlayerdataNbtCounters) {
        RootProjectionShape {
            var expectedEquipped = Math.addExact(
                    Math.multiplyExact(recordsWith58Equipped, equippedClaimsPerFullRecord),
                    equippedClaimsOnFinalRecord);
            if (latestClaims != 2_049 || recordsWith58Equipped != 1_023
                    || equippedClaimsPerFullRecord != 58
                    || equippedClaimsOnFinalRecord != 57
                    || equippedClaims != expectedEquipped
                    || playerClaims != latestClaims + equippedClaims
                    || rawClaims != playerClaims + journalClaims
                    || overRawClaims != rawClaims + 1
                    || !separateFromPlayerdataNbtCounters) {
                throw new IllegalArgumentException("R2Q root projection shape is invalid");
            }
        }
    }

    enum PhysicalProofKind {
        FILESYSTEM_DIRECTORY_OCCURRENCE,
        PRIMARY_SOURCE_RESELECTION,
        LEGAL_SINGLE_MEMBER_GZIP_HEADER_TUNING,
        LEGAL_NBT_COMPONENT_REBALANCE,
        READY_ATTACHMENT_ADMISSION,
        TYPED_ROOT_PROJECTION
    }

    enum DataVersionTagState {
        MISSING,
        STRING_TAG,
        INT_TAG_WRONG_VALUE
    }

    enum DataVersionProofKind {
        REMOVE_CANONICAL_INT_TAG,
        REPLACE_WITH_STRING_TAG,
        REPLACE_WITH_DIFFERENT_INT_TAG
    }

    /** Concrete fixture operation; unlike a counter name this identifies the physical mutation. */
    enum MutationMechanism {
        FILESYSTEM_IRRELEVANT_OCCURRENCE,
        RESELECT_IRRELEVANT_AS_CANONICAL_PRIMARY,
        RESIZE_LEGAL_GZIP_FNAME,
        RESIZE_AND_REDISTRIBUTE_LEGAL_GZIP_FNAME,
        WIDEN_NUMERIC_SCALAR,
        WIDEN_AND_REDISTRIBUTE_NUMERIC_SCALAR,
        INSERT_EMPTY_NAMED_COMPOUND,
        INSERT_AND_REDISTRIBUTE_EMPTY_COMPOUND,
        INSERT_ONE_BYTE_NAMED_EMPTY_BYTE_ARRAY,
        INSERT_AND_REDISTRIBUTE_EMPTY_BYTE_ARRAY_FIELD,
        APPEND_BYTE_LIST_ELEMENT,
        APPEND_AND_REDISTRIBUTE_BYTE_LIST_ELEMENT,
        APPEND_BYTE_ARRAY_ELEMENT,
        APPEND_AND_REDISTRIBUTE_BYTE_ARRAY_ELEMENT,
        APPEND_INT_ARRAY_ELEMENT,
        APPEND_AND_REDISTRIBUTE_INT_ARRAY_ELEMENT,
        APPEND_LONG_ARRAY_ELEMENT,
        APPEND_AND_REDISTRIBUTE_LONG_ARRAY_ELEMENT,
        EXTEND_MODIFIED_UTF8_VALUE,
        EXTEND_AND_REDISTRIBUTE_MODIFIED_UTF8_VALUE,
        RETYPE_ZERO_BYTE_ARRAY_AS_BYTE_SCALAR,
        RETYPE_AND_REDISTRIBUTE_ZERO_BYTE_ARRAY,
        ADMIT_PRODUCTION_READY_ATTACHMENT,
        ADD_TYPED_EQUIPPED_ROOT_CLAIM
    }

    enum CompensationPlacement {
        BEFORE_TARGET,
        AFTER_TARGET
    }

    enum CompensationMechanism {
        RESERVE_AGGREGATE_HEADROOM,
        REDISTRIBUTE_PEAK_TO_HEADROOM_RECORD,
        RESTORE_AGGREGATE_FILLER
    }

    /** The concrete research-only artifact on which a negative mutation is executed. */
    enum PhysicalFixtureKind {
        FILESYSTEM_DIRECTORY,
        FILESYSTEM_SOURCE_SELECTION,
        STRICT_SINGLE_MEMBER_GZIP,
        STREAMING_UNNAMED_COMPOUND,
        PRODUCTION_READY_ADMISSION,
        PRODUCTION_ROOT_PROJECTION
    }

    /** Stable placement in the 2,048-record qualification fixture; no filesystem path is kept. */
    enum MutationPlacement {
        EXTRA_IRRELEVANT_ENTRY,
        RESELECTED_IRRELEVANT_ENTRY,
        PER_FILE_PEAK_RECORD,
        AGGREGATE_HEADROOM_RECORD,
        FIRST_RECORD_WITHOUT_ATTACHMENT,
        FINAL_EQUIPPED_OWNER
    }

    enum CompensationPlacementKind {
        NONE,
        PRIOR_AGGREGATE_FILLER,
        INDEPENDENT_HEADROOM_RECORD,
        FOLLOWING_AGGREGATE_FILLER
    }

    /** One inverse/headroom operation that a physical preflight must execute and measure. */
    record PhysicalCompensationBinding(
            P4E0R2QProfile.Counter counter,
            long expectedDelta,
            CompensationMechanism mechanism,
            CompensationPlacementKind placement) {
        PhysicalCompensationBinding {
            Objects.requireNonNull(counter, "counter");
            Objects.requireNonNull(mechanism, "mechanism");
            Objects.requireNonNull(placement, "placement");
            if (expectedDelta == 0L || placement == CompensationPlacementKind.NONE) {
                throw new IllegalArgumentException(
                        "R2Q physical compensation binding is incomplete");
            }
        }
    }

    /**
     * Executable placement contract for one negative. Tests materialize the named artifact and
     * measure the forward writer plus every inverse/headroom compensation; this record cannot be
     * manufactured from the final counter vector alone.
     */
    record PhysicalFixtureBinding(
            P4E0R2QProfile.Counter targetCounter,
            MutationMechanism mechanism,
            PhysicalFixtureKind fixtureKind,
            MutationPlacement mutationPlacement,
            List<PhysicalCompensationBinding> compensations) {
        PhysicalFixtureBinding {
            Objects.requireNonNull(targetCounter, "targetCounter");
            Objects.requireNonNull(mechanism, "mechanism");
            Objects.requireNonNull(fixtureKind, "fixtureKind");
            Objects.requireNonNull(mutationPlacement, "mutationPlacement");
            compensations = List.copyOf(Objects.requireNonNull(
                    compensations, "compensations"));
        }

        static PhysicalFixtureBinding from(MutationDerivation derivation) {
            Objects.requireNonNull(derivation, "derivation");
            var target = derivation.targetCounter();
            var fixtureKind = switch (target) {
                case DIRECTORY_ENTRIES -> PhysicalFixtureKind.FILESYSTEM_DIRECTORY;
                case RELEVANT_RECORDS -> PhysicalFixtureKind.FILESYSTEM_SOURCE_SELECTION;
                case COMPRESSED_BYTES_PER_FILE, COMPRESSED_BYTES_TOTAL ->
                        PhysicalFixtureKind.STRICT_SINGLE_MEMBER_GZIP;
                case ATTACHMENT_ADMISSIONS -> PhysicalFixtureKind.PRODUCTION_READY_ADMISSION;
                case RAW_ROOT_CLAIMS -> PhysicalFixtureKind.PRODUCTION_ROOT_PROJECTION;
                default -> PhysicalFixtureKind.STREAMING_UNNAMED_COMPOUND;
            };
            var mutationPlacement = switch (target) {
                case DIRECTORY_ENTRIES -> MutationPlacement.EXTRA_IRRELEVANT_ENTRY;
                case RELEVANT_RECORDS -> MutationPlacement.RESELECTED_IRRELEVANT_ENTRY;
                case ATTACHMENT_ADMISSIONS ->
                        MutationPlacement.FIRST_RECORD_WITHOUT_ATTACHMENT;
                case RAW_ROOT_CLAIMS -> MutationPlacement.FINAL_EQUIPPED_OWNER;
                default -> isPerFileCounter(target)
                        ? MutationPlacement.PER_FILE_PEAK_RECORD
                        : MutationPlacement.AGGREGATE_HEADROOM_RECORD;
            };
            var bindings = derivation.compensations().stream()
                    .map(compensation -> new PhysicalCompensationBinding(
                            compensation.counter(),
                            compensation.amount(),
                            compensation.mechanism(),
                            switch (compensation.placement()) {
                                case BEFORE_TARGET -> compensation.mechanism()
                                                == CompensationMechanism
                                                        .REDISTRIBUTE_PEAK_TO_HEADROOM_RECORD
                                        ? CompensationPlacementKind.INDEPENDENT_HEADROOM_RECORD
                                        : CompensationPlacementKind.PRIOR_AGGREGATE_FILLER;
                                case AFTER_TARGET ->
                                        CompensationPlacementKind.FOLLOWING_AGGREGATE_FILLER;
                            }))
                    .toList();
            if (bindings.size() != derivation.compensations().size()) {
                throw new IllegalArgumentException(
                        "R2Q physical compensation placement is incomplete");
            }
            return new PhysicalFixtureBinding(
                    target, derivation.mechanism(), fixtureKind, mutationPlacement, bindings);
        }

        private static boolean isPerFileCounter(P4E0R2QProfile.Counter counter) {
            return switch (counter) {
                case COMPRESSED_BYTES_PER_FILE,
                        DECOMPRESSED_BYTES_PER_FILE,
                        CONTAINER_DEPTH_PER_FILE,
                        COMPOUND_CONTAINERS_PER_FILE,
                        COMPOUND_FIELD_ENTRIES_PER_FILE,
                        LIST_ELEMENTS_PER_FILE,
                        BYTE_ARRAY_ELEMENTS_PER_FILE,
                        INT_ARRAY_ELEMENTS_PER_FILE,
                        LONG_ARRAY_ELEMENTS_PER_FILE,
                        MODIFIED_UTF8_BYTES_PER_FILE,
                        SCALAR_TAGS_PER_FILE -> true;
                default -> false;
            };
        }
    }

    record CounterDelta(P4E0R2QProfile.Counter counter, long amount) {
        CounterDelta {
            Objects.requireNonNull(counter, "counter");
            if (amount == 0 || amount == Long.MIN_VALUE) {
                throw new IllegalArgumentException("R2Q physical delta must be finite and nonzero");
            }
        }
    }

    record CompensationDelta(
            P4E0R2QProfile.Counter counter,
            long amount,
            CompensationPlacement placement,
            CompensationMechanism mechanism) {
        CompensationDelta {
            Objects.requireNonNull(counter, "counter");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(mechanism, "mechanism");
            if (amount == 0 || amount == Long.MIN_VALUE) {
                throw new IllegalArgumentException("R2Q compensation delta is invalid");
            }
        }
    }

    /**
     * A physical operation and every counter delta caused by it. Side effects are not silently
     * overwritten: the derivation reserves or restores explicit headroom around the target event.
     */
    record MutationTemplate(
            P4E0R2QProfile.Counter targetCounter,
            P4E0R2QCasePlan.MutationKind mutationKind,
            MutationMechanism mechanism,
            List<CounterDelta> physicalDeltas) {
        MutationTemplate {
            Objects.requireNonNull(targetCounter, "targetCounter");
            Objects.requireNonNull(mutationKind, "mutationKind");
            Objects.requireNonNull(mechanism, "mechanism");
            physicalDeltas = List.copyOf(Objects.requireNonNull(
                    physicalDeltas, "physicalDeltas"));
            var counters = EnumSet.noneOf(P4E0R2QProfile.Counter.class);
            var targetDeltaCount = 0;
            for (var delta : physicalDeltas) {
                if (!counters.add(delta.counter())) {
                    throw new IllegalArgumentException("R2Q physical delta counter is duplicated");
                }
                if (delta.counter() == targetCounter) {
                    targetDeltaCount++;
                    if (delta.amount() != 1L) {
                        throw new IllegalArgumentException("R2Q target delta is not exact +1");
                    }
                }
            }
            if (targetDeltaCount != 1) {
                throw new IllegalArgumentException("R2Q physical template lacks one target delta");
            }
        }

        MutationRecipe recipe() {
            var coupled = EnumSet.noneOf(P4E0R2QProfile.Counter.class);
            for (var delta : physicalDeltas) {
                if (delta.counter() != targetCounter) {
                    coupled.add(delta.counter());
                }
            }
            return new MutationRecipe(
                    targetCounter,
                    mutationKind,
                    proofKindFor(targetCounter),
                    coupled);
        }

        MutationDerivation derive(P4E0R2QProfile.CounterValues baseline) {
            Objects.requireNonNull(baseline, "baseline");
            var compensations = new ArrayList<CompensationDelta>();
            for (var delta : physicalDeltas) {
                if (delta.counter() == targetCounter) {
                    continue;
                }
                var placement = delta.amount() > 0
                        ? CompensationPlacement.BEFORE_TARGET
                        : CompensationPlacement.AFTER_TARGET;
                var compensation = Math.negateExact(delta.amount());
                compensations.add(new CompensationDelta(
                        delta.counter(),
                        compensation,
                        placement,
                        compensationMechanism(delta.counter(), placement)));
            }

            var beforeTarget = baseline;
            for (var compensation : compensations) {
                if (compensation.placement() == CompensationPlacement.BEFORE_TARGET) {
                    beforeTarget = add(
                            beforeTarget, compensation.counter(), compensation.amount());
                }
            }
            var afterTarget = beforeTarget;
            for (var delta : physicalDeltas) {
                afterTarget = add(afterTarget, delta.counter(), delta.amount());
            }
            var observed = afterTarget;
            for (var compensation : compensations) {
                if (compensation.placement() == CompensationPlacement.AFTER_TARGET) {
                    observed = add(observed, compensation.counter(), compensation.amount());
                }
            }

            for (var counter : P4E0R2QProfile.Counter.values()) {
                var expected = counter == targetCounter
                        ? Math.addExact(baseline.value(counter), 1L)
                        : baseline.value(counter);
                if (observed.value(counter) != expected
                        || beforeTarget.value(counter) > baseline.value(counter)
                        || (counter != targetCounter
                                && afterTarget.value(counter) > baseline.value(counter))) {
                    throw new IllegalArgumentException(
                            "R2Q compensation failed to isolate the target overrun");
                }
            }
            return new MutationDerivation(
                    targetCounter,
                    mutationKind,
                    mechanism,
                    physicalDeltas,
                    compensations,
                    beforeTarget,
                    afterTarget,
                    observed);
        }

        private static P4E0R2QProfile.CounterValues add(
                P4E0R2QProfile.CounterValues values,
                P4E0R2QProfile.Counter counter,
                long amount) {
            return values.with(counter, Math.addExact(values.value(counter), amount));
        }

        private static CompensationMechanism compensationMechanism(
                P4E0R2QProfile.Counter counter,
                CompensationPlacement placement) {
            if (placement == CompensationPlacement.AFTER_TARGET) {
                return CompensationMechanism.RESTORE_AGGREGATE_FILLER;
            }
            return isPerFile(counter)
                    ? CompensationMechanism.REDISTRIBUTE_PEAK_TO_HEADROOM_RECORD
                    : CompensationMechanism.RESERVE_AGGREGATE_HEADROOM;
        }

        private static boolean isPerFile(P4E0R2QProfile.Counter counter) {
            return switch (counter) {
                case COMPRESSED_BYTES_PER_FILE,
                        DECOMPRESSED_BYTES_PER_FILE,
                        CONTAINER_DEPTH_PER_FILE,
                        COMPOUND_CONTAINERS_PER_FILE,
                        COMPOUND_FIELD_ENTRIES_PER_FILE,
                        LIST_ELEMENTS_PER_FILE,
                        BYTE_ARRAY_ELEMENTS_PER_FILE,
                        INT_ARRAY_ELEMENTS_PER_FILE,
                        LONG_ARRAY_ELEMENTS_PER_FILE,
                        MODIFIED_UTF8_BYTES_PER_FILE,
                        SCALAR_TAGS_PER_FILE -> true;
                default -> false;
            };
        }
    }

    record MutationDerivation(
            P4E0R2QProfile.Counter targetCounter,
            P4E0R2QCasePlan.MutationKind mutationKind,
            MutationMechanism mechanism,
            List<CounterDelta> physicalDeltas,
            List<CompensationDelta> compensations,
            P4E0R2QProfile.CounterValues beforeTargetCounters,
            P4E0R2QProfile.CounterValues afterTargetCounters,
            P4E0R2QProfile.CounterValues observedCounters) {
        MutationDerivation {
            Objects.requireNonNull(targetCounter, "targetCounter");
            Objects.requireNonNull(mutationKind, "mutationKind");
            Objects.requireNonNull(mechanism, "mechanism");
            physicalDeltas = List.copyOf(Objects.requireNonNull(
                    physicalDeltas, "physicalDeltas"));
            compensations = List.copyOf(Objects.requireNonNull(
                    compensations, "compensations"));
            Objects.requireNonNull(beforeTargetCounters, "beforeTargetCounters");
            Objects.requireNonNull(afterTargetCounters, "afterTargetCounters");
            Objects.requireNonNull(observedCounters, "observedCounters");
            var targetDeltas = physicalDeltas.stream()
                    .filter(delta -> delta.counter() == targetCounter)
                    .toList();
            if (targetDeltas.size() != 1 || targetDeltas.getFirst().amount() != 1L
                    || compensations.stream()
                            .anyMatch(delta -> delta.counter() == targetCounter)) {
                throw new IllegalArgumentException("R2Q derivation target is not isolated");
            }
        }
    }

    record MutationRecipe(
            P4E0R2QProfile.Counter targetCounter,
            P4E0R2QCasePlan.MutationKind mutationKind,
            PhysicalProofKind proofKind,
            Set<P4E0R2QProfile.Counter> coupledCounters) {
        MutationRecipe {
            Objects.requireNonNull(targetCounter, "targetCounter");
            Objects.requireNonNull(mutationKind, "mutationKind");
            Objects.requireNonNull(proofKind, "proofKind");
            coupledCounters = Set.copyOf(Objects.requireNonNull(
                    coupledCounters, "coupledCounters"));
            if (coupledCounters.contains(targetCounter)) {
                throw new IllegalArgumentException("R2Q target cannot be its own coupled counter");
            }
        }
    }

    record PhysicalMutationProof(
            P4E0R2QProfile.Counter targetCounter,
            P4E0R2QCasePlan.MutationKind mutationKind,
            PhysicalProofKind proofKind,
            Set<P4E0R2QProfile.Counter> coupledCounters,
            long sourceValue,
            long observedValue) {
        PhysicalMutationProof {
            var expected = recipeFor(Objects.requireNonNull(targetCounter, "targetCounter"));
            Objects.requireNonNull(mutationKind, "mutationKind");
            Objects.requireNonNull(proofKind, "proofKind");
            coupledCounters = Set.copyOf(Objects.requireNonNull(
                    coupledCounters, "coupledCounters"));
            if (mutationKind != expected.mutationKind()
                    || proofKind != expected.proofKind()
                    || !coupledCounters.equals(expected.coupledCounters())
                    || sourceValue < 0
                    || observedValue != Math.addExact(sourceValue, 1L)) {
                throw new IllegalArgumentException("R2Q physical mutation proof is invalid");
            }
        }
    }

    record NegativeFixture(
            P4E0R2QProfile.CounterValues observedCounters,
            PhysicalMutationProof proof,
            MutationDerivation derivation,
            PhysicalFixtureBinding physicalBinding) {
        NegativeFixture {
            Objects.requireNonNull(observedCounters, "observedCounters");
            Objects.requireNonNull(proof, "proof");
            Objects.requireNonNull(derivation, "derivation");
            Objects.requireNonNull(physicalBinding, "physicalBinding");
            var baseline = locked().counters();
            if (proof.sourceValue() != baseline.value(proof.targetCounter())
                    || proof.targetCounter() != derivation.targetCounter()
                    || proof.mutationKind() != derivation.mutationKind()
                    || physicalBinding.targetCounter() != derivation.targetCounter()
                    || physicalBinding.mechanism() != derivation.mechanism()
                    || physicalBinding.compensations().size()
                            != derivation.compensations().size()
                    || !observedCounters.equals(derivation.observedCounters())
                    || observedCounters.value(proof.targetCounter())
                            != proof.observedValue()) {
                throw new IllegalArgumentException("R2Q negative is not derived from its fixture");
            }
            for (var counter : P4E0R2QProfile.Counter.values()) {
                if (counter != proof.targetCounter()
                        && observedCounters.value(counter) != baseline.value(counter)) {
                    throw new IllegalArgumentException(
                            "R2Q negative compensation changed a second counter");
                }
            }
        }
    }

    record DataVersionFixture(
            P4E0R2QCasePlan.CaseKind caseKind,
            P4E0R2QCasePlan.MutationKind mutationKind,
            DataVersionProofKind proofKind,
            DataVersionTagState resultingState,
            int sourceValue,
            int resultingIntValue,
            int expectedDfuInvocations) {
        DataVersionFixture {
            Objects.requireNonNull(caseKind, "caseKind");
            Objects.requireNonNull(mutationKind, "mutationKind");
            Objects.requireNonNull(proofKind, "proofKind");
            Objects.requireNonNull(resultingState, "resultingState");
            if (sourceValue != CANONICAL_DATA_VERSION || expectedDfuInvocations != 0) {
                throw new IllegalArgumentException("R2Q DataVersion source contract changed");
            }
            switch (resultingState) {
                case MISSING -> {
                    if (caseKind != P4E0R2QCasePlan.CaseKind.DATA_VERSION_MISSING
                            || mutationKind
                                    != P4E0R2QCasePlan.MutationKind.REMOVE_DATA_VERSION
                            || proofKind != DataVersionProofKind.REMOVE_CANONICAL_INT_TAG
                            || resultingIntValue != 0) {
                        throw new IllegalArgumentException("invalid missing DataVersion proof");
                    }
                }
                case STRING_TAG -> {
                    if (caseKind != P4E0R2QCasePlan.CaseKind.DATA_VERSION_WRONG_TYPE
                            || mutationKind
                                    != P4E0R2QCasePlan.MutationKind
                                            .REPLACE_DATA_VERSION_WITH_WRONG_TYPE
                            || proofKind != DataVersionProofKind.REPLACE_WITH_STRING_TAG
                            || resultingIntValue != 0) {
                        throw new IllegalArgumentException("invalid typed DataVersion proof");
                    }
                }
                case INT_TAG_WRONG_VALUE -> {
                    if (caseKind != P4E0R2QCasePlan.CaseKind.DATA_VERSION_WRONG_VALUE
                            || mutationKind
                                    != P4E0R2QCasePlan.MutationKind
                                            .REPLACE_DATA_VERSION_WITH_WRONG_VALUE
                            || proofKind
                                    != DataVersionProofKind.REPLACE_WITH_DIFFERENT_INT_TAG
                            || resultingIntValue == sourceValue) {
                        throw new IllegalArgumentException("invalid value DataVersion proof");
                    }
                }
            }
        }
    }

    record HeaderTuning(
            int fileIndex,
            long canonicalPhysicalBytes,
            long targetPhysicalBytes,
            int fileNameBytes) {
        HeaderTuning {
            if (fileIndex < 0 || canonicalPhysicalBytes <= 0
                    || targetPhysicalBytes < canonicalPhysicalBytes
                    || fileNameBytes < 0
                    || targetPhysicalBytes - canonicalPhysicalBytes
                            != (fileNameBytes == 0 ? 0L : fileNameBytes + 3L)) {
                throw new IllegalArgumentException("R2Q gzip header tuning is invalid");
            }
        }

        P4E0ResearchWireNbt.HeaderOptions headerOptions() {
            return fileNameBytes == 0
                    ? P4E0ResearchWireNbt.HeaderOptions.canonical()
                    : P4E0ResearchWireNbt.HeaderOptions.fileName(fileNameBytes);
        }
    }

    record CompressedTuning(
            List<HeaderTuning> files,
            long canonicalTotal,
            long tunedTotal,
            int perFileMaximumIndex,
            int aggregateOverrunIndex,
            int perFileCompensationIndex) {
        CompressedTuning {
            files = List.copyOf(files);
            if (files.isEmpty() || canonicalTotal <= 0 || tunedTotal <= 0
                    || perFileMaximumIndex < 0 || aggregateOverrunIndex < 0
                    || perFileCompensationIndex < 0
                    || perFileMaximumIndex >= files.size()
                    || aggregateOverrunIndex >= files.size()
                    || perFileCompensationIndex >= files.size()) {
                throw new IllegalArgumentException("R2Q compressed tuning facts are invalid");
            }
        }

        long maximumPhysicalBytes() {
            return files.stream().mapToLong(HeaderTuning::targetPhysicalBytes).max().orElseThrow();
        }

        /** Per-file +1: increase the max FNAME and shorten a different FNAME by one byte. */
        List<HeaderTuning> perFileOverrun() {
            var changed = new ArrayList<>(files);
            changed.set(
                    perFileMaximumIndex,
                    resize(changed.get(perFileMaximumIndex), 1));
            changed.set(
                    perFileCompensationIndex,
                    resize(changed.get(perFileCompensationIndex), -1));
            return List.copyOf(changed);
        }

        /** Aggregate +1: lengthen a non-maximal FNAME, leaving every per-file size legal. */
        List<HeaderTuning> aggregateOverrun() {
            var changed = new ArrayList<>(files);
            changed.set(
                    aggregateOverrunIndex,
                    resize(changed.get(aggregateOverrunIndex), 1));
            return List.copyOf(changed);
        }

        private static HeaderTuning resize(HeaderTuning source, int delta) {
            return new HeaderTuning(
                    source.fileIndex(),
                    source.canonicalPhysicalBytes(),
                    Math.addExact(source.targetPhysicalBytes(), delta),
                    Math.addExact(source.fileNameBytes(), delta));
        }
    }

    record SelectedRecordWitness(
            long compressedBytes,
            long decompressedBytes,
            long compoundContainers,
            long compoundFieldEntries,
            long modifiedUtf8Bytes,
            long scalarTags) {
        SelectedRecordWitness {
            if (compressedBytes <= 0
                    || decompressedBytes != 22L
                    || compoundContainers != 1L
                    || compoundFieldEntries != 1L
                    || modifiedUtf8Bytes != 11L
                    || scalarTags != 1L) {
                throw new IllegalArgumentException("R2Q selected-record witness changed");
            }
        }

        static SelectedRecordWitness measure() {
            var root = new CompoundTag();
            root.putInt("DataVersion", CANONICAL_DATA_VERSION);
            var metrics = P4E0ResearchNbtMetrics.measure(root);
            try {
                var wire = P4E0ResearchWireNbt.measure(
                        P4E0ResearchWireNbt.HeaderOptions.canonical(),
                        Deflater.DEFAULT_COMPRESSION,
                        1_024L,
                        64L,
                        output -> NbtIo.writeUnnamedTag(root, output));
                return new SelectedRecordWitness(
                        wire.physicalBytes(),
                        wire.decompressedBytes(),
                        metrics.compoundCount(),
                        metrics.compoundEntryCount(),
                        metrics.modifiedUtf8Bytes(),
                        metrics.scalarTagCount());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "R2Q selected-record witness could not be measured", exception);
            }
        }
    }

    record ReadyAttachmentWitness(
            long compressedBytes,
            long decompressedBytes,
            long compoundContainers,
            long compoundFieldEntries,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long modifiedUtf8Bytes,
            long scalarTags,
            long projectedRoots) {
        ReadyAttachmentWitness {
            var requiredPositive = new long[] {
                compressedBytes,
                decompressedBytes,
                compoundContainers,
                compoundFieldEntries,
                listElements,
                intArrayElements,
                modifiedUtf8Bytes,
                scalarTags,
                projectedRoots
            };
            for (var value : requiredPositive) {
                if (value <= 0) {
                    throw new IllegalArgumentException(
                            "R2Q Ready attachment witness lost a required coordinate");
                }
            }
            if (byteArrayElements < 0 || longArrayElements < 0 || projectedRoots != 320L) {
                throw new IllegalArgumentException("R2Q Ready attachment witness is invalid");
            }
        }

        static ReadyAttachmentWitness measure() {
            var fixture = P4E0ResearchAttachmentFixtures.readyRootMax(false);
            var tag = fixture.serializedTag();
            if (!(tag instanceof CompoundTag)
                    || fixture.projectedRoots().orElseThrow().size() != 320) {
                throw new IllegalStateException("R2Q production Ready witness changed shape");
            }
            var metrics = P4E0ResearchNbtMetrics.measure(tag);
            try {
                var wire = P4E0ResearchWireNbt.measure(
                        P4E0ResearchWireNbt.HeaderOptions.canonical(),
                        Deflater.DEFAULT_COMPRESSION,
                        16_777_216L,
                        16_777_216L,
                        output -> NbtIo.writeUnnamedTag(tag, output));
                return new ReadyAttachmentWitness(
                        wire.physicalBytes(),
                        wire.decompressedBytes(),
                        metrics.compoundCount(),
                        metrics.compoundEntryCount(),
                        metrics.listElementCount(),
                        metrics.byteArrayElements(),
                        metrics.intArrayElements(),
                        metrics.longArrayElements(),
                        metrics.modifiedUtf8Bytes(),
                        metrics.scalarTagCount(),
                        fixture.projectedRoots().orElseThrow().size());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "R2Q production Ready witness could not be measured", exception);
            }
        }

        void appendPhysicalDeltas(List<CounterDelta> deltas) {
            append(deltas, P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL, compressedBytes);
            append(deltas, P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, decompressedBytes);
            append(deltas, P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL,
                    compoundContainers);
            append(deltas, P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL,
                    compoundFieldEntries);
            append(deltas, P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL, listElements);
            append(deltas, P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL,
                    byteArrayElements);
            append(deltas, P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL,
                    intArrayElements);
            append(deltas, P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL,
                    longArrayElements);
            append(deltas, P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL,
                    modifiedUtf8Bytes);
            append(deltas, P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL, scalarTags);
            append(deltas, P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS, projectedRoots);
        }

        private static void append(
                List<CounterDelta> deltas,
                P4E0R2QProfile.Counter counter,
                long amount) {
            if (amount != 0) {
                deltas.add(delta(counter, amount));
            }
        }
    }

    private static final class Holder {
        private static final Blueprint BLUEPRINT = createLocked();

        private Holder() {
        }
    }

    private static final class SelectedRecordWitnessHolder {
        private static final SelectedRecordWitness WITNESS = SelectedRecordWitness.measure();

        private SelectedRecordWitnessHolder() {
        }
    }

    private static final class ReadyAttachmentWitnessHolder {
        private static final ReadyAttachmentWitness WITNESS = ReadyAttachmentWitness.measure();

        private ReadyAttachmentWitnessHolder() {
        }
    }
}
