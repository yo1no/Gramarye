package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.store.P4E0R2QStoreJournalFixtures;
import org.junit.jupiter.api.Test;

/** Isolated proof that the positive raw-root envelope comes from admitted P4-C Ready states. */
final class P4E0R2QRootProjectionTest {
    @Test
    void exactAndCapPlusOneRootsComeFromOneThousandTwentyFourAdmittedReadyStates() {
        var fixture = P4E0R2QStoreJournalFixtures.buildExact();
        var admissions = fixture.admissionFacts();
        var roots = fixture.facts();

        assertEquals(1_024, admissions.totalAdmissions());
        assertEquals(1, admissions.mixedFamilyAdmissions());
        assertEquals(1_023, admissions.minimalReadyAdmissions());
        assertEquals(2_049, roots.latestRoots());
        assertEquals(59_391, roots.equippedRoots());
        assertEquals(61_440, roots.latestRoots() + roots.equippedRoots());
        assertEquals(4_096, roots.journalRoots());
        assertEquals(65_536, roots.exactRawRoots());
        assertEquals(65_537, roots.overRawRoots());
        assertTrue(roots.exactRootsComplete());
        assertTrue(roots.overRootsRejected());

        // Retains the same admitted Ready states and carriers used to form the projections.
        fixture.retainAtPeak();
    }
}
