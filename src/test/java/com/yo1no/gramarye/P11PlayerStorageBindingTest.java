package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Real guard decision only; not a fabricated server, Mixin match or native IO qualification. */
final class P11PlayerStorageBindingTest {
    @Test
    void canonicalUnmanagedAndExactManagedActorsSelectTheSameRoot() {
        assertTrue(access(true, true, true, false, false));
        assertTrue(access(true, true, true, true, true));
        assertFalse(access(false, false, true, false, false));
    }

    @Test
    void storageAndServerMustAgreeEvenBeforeAnAccountIsAcquired() {
        for (boolean managed : new boolean[] {false, true}) {
            assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                    () -> access(true, false, true, managed, false));
            assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                    () -> access(false, true, true, managed, true));
        }
    }

    @Test
    void managedUuidCannotUseForeignStorageOrAnotherSameServerActor() {
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> access(false, false, true, true, false));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> access(true, true, true, true, false));
        assertTrue(access(true, true, true, true, true));
    }

    @Test
    void wrongThreadRejectsEitherActiveNativeIdentityWithoutReadingAccountMaterial() {
        // The production caller supplies no map/body facts on this branch.
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> access(true, false, false, false, false));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> access(false, true, false, false, false));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> access(true, true, false, false, false));
        assertFalse(access(false, false, false, false, false),
                "an unrelated server/storage does not become this root's writer");
    }

    @Test
    void activeServerWritersCannotBecomeUnmanagedOnWrongThread() {
        assertTrue(P11FoundationService.requireServerWriter(true, true));
        assertFalse(P11FoundationService.requireServerWriter(false, true));
        assertFalse(P11FoundationService.requireServerWriter(false, false));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireServerWriter(true, false));
    }

    @Test
    void worldWriterRequiresBothExactStorageAndDataAndMainThread() {
        assertTrue(P11FoundationService.requireWorldWriter(true, true, true, true));
        assertTrue(P11FoundationService.requireWorldWriter(true, false, false, true));
        assertFalse(P11FoundationService.requireWorldWriter(false, true, false, true));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireWorldWriter(true, true, false, true));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireWorldWriter(false, true, true, true));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireWorldWriter(true, false, false, false));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireWorldWriter(true, true, true, false));
    }

    @Test
    void canonicalIndependentWriterCannotFollowAReboundForeignOrSameUuidActor() {
        P11FoundationService.requireIndependentWriterBinding(true, true, true);
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireIndependentWriterBinding(true, false, false));
        assertThrows(P11QualifiedSourceOwner.SourceUnavailable.class,
                () -> P11FoundationService.requireIndependentWriterBinding(true, true, false));
    }

    private static boolean access(boolean canonical, boolean sameServer, boolean main,
            boolean managed, boolean exactActor) {
        return P11FoundationService.requirePlayerStorageAccess(
                canonical, sameServer, main, managed, exactActor);
    }
}
