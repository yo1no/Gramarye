package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3C3ApiGateTest {
    @Test
    void p3C3ProductionTypesAndNestedSeamRemainPackagePrivate() {
        assertAll(
                () -> assertFalse(Modifier.isPublic(
                        SubmissionRevisionProposal.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionRevisionProposer.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionReportMerger.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionPreparationCheck.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillSubmissionPreparer.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SkillSubmissionPreparer.Stages.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(
                        SkillSubmissionPreparer.class.getModifiers())));
    }

    @Test
    void stagesIsTheSingleStronglyTypedFourMethodSeam() {
        var names = Arrays.stream(SkillSubmissionPreparer.Stages.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(Set.of("formalize", "resolve", "analyze", "project"), names),
                () -> assertEquals(4,
                        SkillSubmissionPreparer.Stages.class.getDeclaredMethods().length),
                () -> assertTrue(Arrays.stream(
                                SkillSubmissionPreparer.Stages.class.getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType() == Object.class
                                || Arrays.asList(method.getParameterTypes()).contains(Object.class))));
    }

    @Test
    void p3C3StageModelsHaveNoCodecNetworkOrPersistenceSurface() {
        var types = List.of(
                SubmissionRevisionProposal.class,
                SubmissionRevisionProposal.Proposed.class,
                SubmissionRevisionProposal.Exhausted.class,
                SubmissionPreparationCheck.class,
                SubmissionPreparationCheck.Prepared.class,
                SubmissionPreparationCheck.Invalid.class,
                SubmissionPreparationCheck.RevisionExhausted.class);

        assertTrue(types.stream().allMatch(type ->
                Arrays.stream(type.getDeclaredFields()).noneMatch(field ->
                        field.getType().getName().contains("Codec"))
                        && Arrays.stream(type.getDeclaredMethods()).noneMatch(method -> {
                            var name = method.getName().toLowerCase();
                            return method.getReturnType().getName().contains("Codec")
                                    || name.contains("encode")
                                    || name.contains("decode")
                                    || name.contains("save")
                                    || name.contains("write")
                                    || name.contains("commit")
                                    || name.contains("allocate");
                        })));
    }

    @Test
    void p4AOwnerIdentityAddsOnlyItsCanonicalCodec() throws Exception {
        assertAll(
                () -> assertEquals(Codec.class,
                        SkillOwnerId.class.getDeclaredField("CODEC").getType()),
                () -> assertTrue(Arrays.stream(SkillOwnerId.class.getDeclaredFields())
                        .noneMatch(field -> field.getType().getName().contains("StreamCodec"))),
                () -> assertTrue(Arrays.stream(SkillOwnerId.class.getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType().getName().contains("StreamCodec"))));
    }

    @Test
    void p3C3PhaseLocalGateNowRecognizesTheC4PublicTypes() {
        // P3-C3 phase-local gate flipped when C4 legitimately introduced these types.
        assertAll(
                () -> assertTrue(classExists(
                        "com.yo1no.gramarye.magic.definition.submission.SkillCommitPrecondition")),
                () -> assertTrue(classExists(
                        "com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan")),
                () -> assertTrue(classExists(
                        "com.yo1no.gramarye.magic.definition.submission.SkillSubmissionOutcome")));
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, P3C3ApiGateTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException expected) {
            return false;
        }
    }
}
