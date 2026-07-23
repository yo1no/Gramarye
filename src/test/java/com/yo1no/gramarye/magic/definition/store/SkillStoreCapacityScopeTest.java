package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillStoreCapacityScopeTest {
    @Test
    void publicScopeVocabularyHasExactlyFourCanonicalCeilings() {
        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillStoreCapacityScope.class.getModifiers())),
                () -> assertEquals(Set.of(
                                SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS),
                        Set.of(SkillStoreCapacityScope.values())),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER,
                        SkillStoreCapacityScope.OWNER_SKILL_HISTORIES.canonicalMaximum()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL,
                        SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES.canonicalMaximum()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL,
                        SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS.canonicalMaximum()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL,
                        SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS.canonicalMaximum()));
    }
}
