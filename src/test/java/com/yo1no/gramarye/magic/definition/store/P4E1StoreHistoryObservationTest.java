package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P4E1StoreHistoryObservationTest {
    @Test
    void presentObservationChecksOwnerAndExactRetainedRevisionWithoutLatestLookup() {
        var skillId = StoreTestFixtures.skillId(0x701);
        var otherSkillId = StoreTestFixtures.skillId(0x702);
        var owner = StoreTestFixtures.ownerId(0x801);
        var foreign = StoreTestFixtures.ownerId(0x802);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0, 2)));

        var present = assertInstanceOf(
                P4E1StoreHistoryObservation.Present.class,
                store.observeExactHistoryForRootAudit(skillId));

        assertTrue(present.ownerMatches(owner));
        assertFalse(present.ownerMatches(foreign));
        assertTrue(present.contains(reference(skillId, 0)),
                "a valid nonlatest retained revision must remain valid");
        assertTrue(present.contains(reference(skillId, 2)));
        assertFalse(present.contains(reference(skillId, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> present.contains(reference(otherSkillId, 0)));

        present.discard();
        present.discard();
        assertThrows(IllegalStateException.class, () -> present.ownerMatches(owner));
        assertThrows(IllegalStateException.class,
                () -> present.contains(reference(skillId, 0)));
    }

    @Test
    void absentObservationRetainsNoStoreHistory() {
        var observation = new SkillDefinitionStore()
                .observeExactHistoryForRootAudit(StoreTestFixtures.skillId(0x711));

        assertInstanceOf(P4E1StoreHistoryObservation.Absent.class, observation);
        assertTrue(Arrays.stream(observation.getClass().getDeclaredFields())
                .noneMatch(field -> field.getType() == StoredSkillHistory.class));
    }

    @Test
    void boundaryIsPackagePrivateAndPresentExposesOnlyOpaquePredicatesAndClear() {
        assertFalse(Modifier.isPublic(P4E1StoreHistoryObservation.class.getModifiers()));
        assertTrue(Arrays.stream(P4E1StoreHistoryObservation.Present.class
                        .getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertTrue(Arrays.stream(P4E1StoreHistoryObservation.Present.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        assertEquals(1L, Arrays.stream(
                        P4E1StoreHistoryObservation.Present.class.getDeclaredFields())
                .filter(field -> field.getType() == StoredSkillHistory.class)
                .count());

        var nonPrivateMethods = Arrays.stream(
                        P4E1StoreHistoryObservation.Present.class.getDeclaredMethods())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("ownerMatches", "contains", "discard"), nonPrivateMethods);
        assertTrue(nonPrivateMethods.stream().noneMatch(name -> Set.of(
                        "owner", "history", "latest", "document", "iterator", "stream")
                .contains(name)));
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, StoreTestFixtures.revision(revision));
    }
}
