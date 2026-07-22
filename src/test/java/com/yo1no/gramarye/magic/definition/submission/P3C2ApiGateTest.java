package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class P3C2ApiGateTest {
    @Test
    void specifiedModelsArePublicAndAuthorityOrchestrationRemainsPackagePrivate() {
        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillOwnerId.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(SkillIdSource.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(AuthorizedSkillState.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        SkillSubmissionAuthorizationResult.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        SkillIdentityRejectionCode.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(
                        SkillSubmissionConflict.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionAuthorityCheck.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionAuthorityChecker.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionAuthorityInvariants.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(
                        SubmissionConcurrency.class.getModifiers())));
    }

    @Test
    void skillIdSourceIsOnlyTheMintPortAndHasNoProductionConsumerContractYet() {
        assertAll(
                () -> assertTrue(SkillIdSource.class.isInterface()),
                () -> assertEquals(List.of("nextSkillId"),
                        Arrays.stream(SkillIdSource.class.getDeclaredMethods())
                                .map(method -> method.getName())
                                .toList()),
                () -> assertEquals(0, SkillIdSource.class.getDeclaredFields().length));
    }

    @Test
    void p3C2AndC3PhaseLocalModelsHaveNoCodecStreamCodecOrNetworkSurface() {
        var types = List.of(
                SkillOwnerId.class,
                AuthorizedSkillState.class,
                AuthorizedSkillState.New.class,
                AuthorizedSkillState.Existing.class,
                SkillSubmissionAuthorizationResult.class,
                SkillSubmissionAuthorizationResult.Authorized.class,
                SkillSubmissionAuthorizationResult.Rejected.class,
                SkillIdentityRejectionCode.class,
                SkillSubmissionConflict.class,
                SkillSubmissionConflict.BaseRevisionForNew.class,
                SkillSubmissionConflict.MissingBaseForExisting.class,
                SkillSubmissionConflict.StaleBase.class,
                SkillSubmissionConflict.FutureBase.class);

        assertTrue(types.stream().allMatch(type ->
                Arrays.stream(type.getDeclaredFields()).noneMatch(field ->
                        field.getType().getName().contains("Codec"))
                        && Arrays.stream(type.getDeclaredMethods()).noneMatch(method ->
                                method.getReturnType().getName().contains("Codec")
                                        || method.getName().toLowerCase().contains("encode")
                                        || method.getName().toLowerCase().contains("decode"))));
    }
}
