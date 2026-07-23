package com.yo1no.gramarye.magic.api.id;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillRevisionSuccessorTest {
    @Test
    void computesCandidateSuccessorsWithoutMutatingTheSource() {
        var zero = new SkillRevision(0);
        var arbitrary = new SkillRevision(41);
        var almostMaximum = new SkillRevision(Integer.MAX_VALUE - 1);

        assertAll(
                () -> assertEquals(Optional.of(new SkillRevision(1)), zero.successor()),
                () -> assertEquals(Optional.of(new SkillRevision(42)), arbitrary.successor()),
                () -> assertEquals(
                        Optional.of(new SkillRevision(Integer.MAX_VALUE)),
                        almostMaximum.successor()),
                () -> assertEquals(0, zero.value()),
                () -> assertEquals(41, arbitrary.value()),
                () -> assertEquals(Integer.MAX_VALUE - 1, almostMaximum.value()));
    }

    @Test
    void maximumRevisionHasNoSuccessorAndDoesNotOverflowOrWrap() {
        var maximum = new SkillRevision(Integer.MAX_VALUE);

        assertAll(
                () -> assertTrue(maximum.successor().isEmpty()),
                () -> assertEquals(Integer.MAX_VALUE, maximum.value()));
    }
}
