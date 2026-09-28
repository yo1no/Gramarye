package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Actual output-match algorithm; these tests do not manufacture a live actor/save authority. */
final class P11AttachmentWriteResultTest {
    @Test
    void onlyTheExactReturnedSubtreeMatches() {
        var output = new CompoundTag();
        output.putInt("schema", 73);
        var write = PlayerSkillAttachmentService.p11WriteResult(
                new PlayerSkillAttachmentOversizeMarker(), output);
        assertTrue(write.matchesOutput(output));
        assertFalse(write.matchesOutput(output.copy()));
        assertFalse(write.matchesOutput(null));
    }

    @Test
    void nestedSameStateWriteCannotProveTheOuterSubtree() {
        var state = new PlayerSkillAttachmentOversizeMarker();
        var outer = PlayerSkillAttachmentMarker.freshTag();
        var nested = PlayerSkillAttachmentMarker.freshTag();
        var write = PlayerSkillAttachmentService.p11WriteResult(state, nested);
        assertFalse(write.matchesOutput(outer));
        assertTrue(write.matchesOutput(nested));
    }

    @Test
    void nullStateAndOutputCannotCreateWriteResult() {
        assertThrows(NullPointerException.class,
                () -> PlayerSkillAttachmentService.p11WriteResult(null, new CompoundTag()));
        assertThrows(NullPointerException.class,
                () -> PlayerSkillAttachmentService.p11WriteResult(
                        new PlayerSkillAttachmentOversizeMarker(), null));
    }

    @Test
    void closedWriteResultHasNoPublicFactoryOrMutableNbtGetter() {
        var type = PlayerSkillAttachmentService.P11AttachmentWriteResult.class;
        assertTrue(Arrays.stream(type.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        assertFalse(Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .anyMatch(method -> Tag.class.isAssignableFrom(method.getReturnType())
                        || method.getReturnType() == PlayerSkillAttachmentState.class));
    }
}
