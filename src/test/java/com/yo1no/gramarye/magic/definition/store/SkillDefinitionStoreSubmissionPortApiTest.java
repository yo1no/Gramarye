package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class SkillDefinitionStoreSubmissionPortApiTest {
    @Test
    void serviceOwnsOneNarrowPortIdentity() {
        var service = new SkillDefinitionStoreService();
        assertSame(service.submissionPort(), service.submissionPort());
    }

    @Test
    void publicPortDeclaresExactlyTheNineApprovedOperations() {
        var methods = Arrays.stream(SkillDefinitionStoreSubmissionPort.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "observeSubmissionAuthority",
                "bootstrapJournal",
                "journalStatus",
                "journalRoots",
                "observePendingRecovery",
                "prepareSubmissionCommit",
                "commitPreparedSubmission",
                "prepareJournalPrefixClear",
                "commitPreparedJournalClear"), methods);
        assertTrue(Arrays.stream(SkillDefinitionStoreSubmissionPort.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
    }

    @Test
    void preparedHandlesExposeNoConstructorOrPayloadAccessor() {
        for (var type : List.of(
                SkillDefinitionStoreSubmissionPort.PreparedStoreSubmissionCommit.class,
                SkillDefinitionStoreSubmissionPort.PreparedJournalPrefixClear.class)) {
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                            || Modifier.isProtected(constructor.getModifiers())));
            assertFalse(Arrays.stream(type.getDeclaredMethods())
                    .anyMatch(method -> Modifier.isPublic(method.getModifiers())));
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        }
    }

    @Test
    void domainRejectedCannotWrapCommitted() {
        var reference = new SkillReference(
                StoreTestFixtures.skillId(1), StoreTestFixtures.revision(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.DomainRejected(
                        new SkillStoreCommitResult.Committed(reference)));
    }
}
