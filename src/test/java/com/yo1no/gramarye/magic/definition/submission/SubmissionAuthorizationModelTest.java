package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SubmissionAuthorizationModelTest {
    @Test
    void newAndExistingStatesHaveOnlyTheirPossibleDataAndDeriveSkillId() {
        var latest = new SkillReference(
                SubmissionAuthorityTestFixtures.SKILL_ID, new SkillRevision(7));
        var newState = new AuthorizedSkillState.New(
                SubmissionAuthorityTestFixtures.SKILL_ID);
        var existing = new AuthorizedSkillState.Existing(latest);

        assertAll(
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID, newState.skillId()),
                () -> assertSame(latest, existing.latestStoredRevision()),
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID, existing.skillId()),
                () -> assertEquals(Set.of("skillId"), componentNames(AuthorizedSkillState.New.class)),
                () -> assertEquals(Set.of("latestStoredRevision"),
                        componentNames(AuthorizedSkillState.Existing.class)),
                () -> assertThrows(NullPointerException.class,
                        () -> new AuthorizedSkillState.New(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new AuthorizedSkillState.Existing(null)));
    }

    @Test
    void authorizationVariantsDeriveIdsAndRejectNullComponents() {
        var state = new AuthorizedSkillState.New(SubmissionAuthorityTestFixtures.SKILL_ID);
        var authorized = new SkillSubmissionAuthorizationResult.Authorized(
                SubmissionAuthorityTestFixtures.OWNER, state);
        var rejected = new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                SkillIdentityRejectionCode.NOT_AUTHORIZED);

        assertAll(
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID, authorized.skillId()),
                () -> assertSame(SubmissionAuthorityTestFixtures.OWNER, authorized.owner()),
                () -> assertSame(state, authorized.state()),
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID, rejected.skillId()),
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID,
                        rejected.requestedSkillId()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionAuthorizationResult.Authorized(null, state)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionAuthorizationResult.Authorized(
                                SubmissionAuthorityTestFixtures.OWNER, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionAuthorizationResult.Rejected(
                                null, SkillIdentityRejectionCode.NOT_AUTHORIZED)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionAuthorizationResult.Rejected(
                                SubmissionAuthorityTestFixtures.SKILL_ID, null)));
    }

    @Test
    void rejectionVocabularyIsOpaqueAndQuotaExceededIsDormantButRepresentable() {
        var quota = new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                SkillIdentityRejectionCode.QUOTA_EXCEEDED);
        var methodNames = Arrays.stream(
                        SkillSubmissionAuthorizationResult.Rejected.class.getDeclaredMethods())
                .map(method -> method.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(Set.of("NOT_AUTHORIZED", "QUOTA_EXCEEDED"),
                        Arrays.stream(SkillIdentityRejectionCode.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet())),
                () -> assertEquals(SkillIdentityRejectionCode.QUOTA_EXCEEDED, quota.reason()),
                () -> assertFalse(methodNames.stream().anyMatch(name ->
                        name.contains("owner")
                                || name.contains("latest")
                                || name.contains("exists")
                                || name.contains("message"))),
                () -> assertFalse(quota.toString().contains(
                        SubmissionAuthorityTestFixtures.OWNER.value().toString())));
    }

    private static Set<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }
}
