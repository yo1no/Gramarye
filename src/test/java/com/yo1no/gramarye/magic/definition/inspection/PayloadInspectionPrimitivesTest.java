package com.yo1no.gramarye.magic.definition.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PayloadInspectionPrimitivesTest {
    @Test
    void sealedResultRepresentsCompleteSuccessOrExplicitFailureOnly() {
        var projection = emptyTriggerProjection();
        var failure = new PayloadInspectionFailure(
                ValidationIssueCode.fromNamespaceAndPath("othermod", "payload.invalid_shape"),
                new ValidationIssueMetadata.Limit(3, 2));

        var success = new PayloadInspectionResult.Success<>(projection);
        var failed = new PayloadInspectionResult.Failure<TriggerReferenceProjection>(failure);

        assertEquals(projection, success.projection());
        assertEquals(failure, failed.failure());
        assertThrows(IllegalArgumentException.class,
                () -> new PayloadInspectionResult.Success<TriggerReferenceProjection>(null));
        assertThrows(IllegalArgumentException.class,
                () -> new PayloadInspectionResult.Failure<TriggerReferenceProjection>(null));
        assertTrue(PayloadInspectionResult.class.isSealed());
        assertEquals(2, PayloadInspectionResult.class.getPermittedSubclasses().length);
    }

    @Test
    void failureShapeContainsOnlyCodeAndMetadata() {
        var components = Arrays.stream(PayloadInspectionFailure.class.getRecordComponents())
                .map(component -> component.getName() + ":" + component.getType().getSimpleName())
                .toList();
        var failure = new PayloadInspectionFailure(
                PayloadInspectionFailureCodes.INSPECTOR_CONTRACT_VIOLATION,
                ValidationIssueMetadata.none());

        assertEquals(
                List.of("code:ValidationIssueCode", "metadata:ValidationIssueMetadata"),
                components);
        assertFalse(failure.toString().contains("message="));
        assertThrows(IllegalArgumentException.class,
                () -> new PayloadInspectionFailure(null, ValidationIssueMetadata.none()));
        assertThrows(IllegalArgumentException.class,
                () -> new PayloadInspectionFailure(
                        PayloadInspectionFailureCodes.INSPECTOR_EXCEPTION, null));
    }

    @Test
    void nodeReferenceRetainsInvalidIndicesForLaterValidation() {
        var path = ValidationPath.empty().field("source_node");
        var negative = new NodeReference(
                -1,
                ReferenceRole.SOURCE,
                path,
                Optional.empty());
        var forward = new NodeReference(
                19,
                ReferenceRole.TARGET,
                path,
                Optional.of(ActionOutputKind.PROJECTILE));

        assertEquals(-1, negative.referencedNodeIndex());
        assertTrue(negative.requiredOutputKind().isEmpty());
        assertEquals(19, forward.referencedNodeIndex());
        assertEquals(Optional.of(ActionOutputKind.PROJECTILE), forward.requiredOutputKind());
        assertThrows(IllegalArgumentException.class,
                () -> new NodeReference(0, null, path, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new NodeReference(0, ReferenceRole.SOURCE, null, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new NodeReference(0, ReferenceRole.SOURCE, path, null));
    }

    @Test
    void triggerProjectionDefensivelyCopiesAndPreservesInspectorOrder() {
        var first = reference(2, ReferenceRole.SOURCE, "first");
        var second = reference(1, ReferenceRole.CHAIN_SOURCE, "second");
        var source = new ArrayList<>(List.of(first, second));
        var projection = new TriggerReferenceProjection(
                SourceSelection.PRIOR_NODE,
                TargetSelection.CURRENT_TARGET,
                source);
        source.clear();

        assertEquals(List.of(first, second), projection.references());
        assertThrows(UnsupportedOperationException.class, () -> projection.references().clear());
        assertThrows(IllegalArgumentException.class, () -> new TriggerReferenceProjection(
                SourceSelection.NONE,
                TargetSelection.NONE,
                Arrays.asList(first, null)));
    }

    @Test
    void boundedReferenceCopyConsumesOnlyCapPlusOneElements() {
        var maximum = MagicSafetyCeilings.MAX_INSPECTED_REFERENCES_PER_SIDE;
        var exact = new CountingReferenceList(maximum);
        var trigger = new TriggerReferenceProjection(
                SourceSelection.PRIOR_NODE,
                TargetSelection.CURRENT_TARGET,
                exact);

        assertEquals(maximum, exact.consumed());
        assertEquals(maximum, trigger.references().size());
        assertThrows(UnsupportedOperationException.class, () -> trigger.references().clear());

        var over = new CountingReferenceList(maximum + 100);
        var failure = assertThrows(InspectionContractViolationException.class,
                () -> new ActionReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.PRIOR_OUTPUT,
                        over,
                        Set.of(ActionOutputKind.EFFECT)));

        assertEquals(maximum + 1, over.consumed());
        assertEquals(
                new ValidationIssueMetadata.Limit(maximum + 1, maximum),
                failure.metadata());
    }

    @Test
    void actionOutputsAreDefensivelyCopiedImmutableAndEnumOrdered() {
        var source = new HashSet<>(List.of(
                ActionOutputKind.SCHEDULE,
                ActionOutputKind.EFFECT,
                ActionOutputKind.CONSTRUCT));
        var projection = new ActionReferenceProjection(
                SourceSelection.NONE,
                TargetSelection.SELF,
                List.of(),
                source);
        source.clear();

        assertEquals(
                List.of(
                        ActionOutputKind.EFFECT,
                        ActionOutputKind.CONSTRUCT,
                        ActionOutputKind.SCHEDULE),
                List.copyOf(projection.producedOutputs()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> projection.producedOutputs().add(ActionOutputKind.MARKER));
        assertTrue(new ActionReferenceProjection(
                        SourceSelection.NONE,
                        TargetSelection.NONE,
                        List.of(),
                        Set.of())
                .producedOutputs()
                .isEmpty());
    }

    @Test
    void selectionVocabularyIsDistinctFromCapabilityRequirements() {
        assertEquals(
                List.of("NONE", "CURRENT_EVENT", "PRIOR_NODE"),
                Arrays.stream(SourceSelection.values()).map(Enum::name).toList());
        assertEquals(
                List.of("NONE", "SELF", "CURRENT_TARGET", "PRIOR_OUTPUT"),
                Arrays.stream(TargetSelection.values()).map(Enum::name).toList());
        assertEquals(
                List.of("SOURCE", "TARGET", "SPLIT_SOURCE", "CHAIN_SOURCE", "REPEAT_SOURCE"),
                Arrays.stream(ReferenceRole.values()).map(Enum::name).toList());
    }

    @Test
    void relativePathBudgetsReserveFutureValidationPrefix() {
        assertEquals(
                MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS,
                MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS
                        + MagicSafetyCeilings.VALIDATION_PATH_PREFIX_RESERVED_SEGMENTS);
        assertEquals(
                MagicSafetyCeilings.MAX_STRING_LENGTH,
                MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH
                        + MagicSafetyCeilings.VALIDATION_PATH_PREFIX_RESERVED_CHARACTERS);
        assertTrue(MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS > 0);
        assertTrue(MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH > 0);
    }

    @Test
    void explicitNoReferenceInspectorCanReturnAnEmptySuccess() {
        var inspector = (com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector<
                        InspectionTestFixtures.TriggerData>) payload ->
                new PayloadInspectionResult.Success<>(emptyTriggerProjection());

        var result = inspector.inspect(new InspectionTestFixtures.TriggerData(0));
        var success = assertInstanceOf(PayloadInspectionResult.Success.class, result);
        assertEquals(emptyTriggerProjection(), success.projection());
    }

    private static TriggerReferenceProjection emptyTriggerProjection() {
        return new TriggerReferenceProjection(
                SourceSelection.NONE,
                TargetSelection.NONE,
                List.of());
    }

    private static NodeReference reference(int index, ReferenceRole role, String field) {
        return new NodeReference(
                index,
                role,
                ValidationPath.empty().field(field),
                Optional.empty());
    }

    private static final class CountingReferenceList extends AbstractList<NodeReference> {
        private final int size;
        private int consumed;

        private CountingReferenceList(int size) {
            this.size = size;
        }

        @Override
        public NodeReference get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException(index);
            }
            consumed++;
            return reference(index, ReferenceRole.SOURCE, "reference");
        }

        @Override
        public int size() {
            return size;
        }

        private int consumed() {
            return consumed;
        }
    }
}
