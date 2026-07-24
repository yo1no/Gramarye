package com.yo1no.gramarye.magic.definition.document;

/** Typed route for one failure without retaining document or payload data. */
sealed interface SkillDocumentPersistenceLocation
        permits SkillDocumentPersistenceLocation.DocumentRoot,
                SkillDocumentPersistenceLocation.TriggerPayload,
                SkillDocumentPersistenceLocation.ActionPayload,
                SkillDocumentPersistenceLocation.TopAppearance,
                SkillDocumentPersistenceLocation.AppearanceOverride {
    enum DocumentRoot implements SkillDocumentPersistenceLocation {
        INSTANCE
    }

    record TriggerPayload(int nodeIndex) implements SkillDocumentPersistenceLocation {
        public TriggerPayload {
            requireNodeIndex(nodeIndex);
        }
    }

    record ActionPayload(int nodeIndex) implements SkillDocumentPersistenceLocation {
        public ActionPayload {
            requireNodeIndex(nodeIndex);
        }
    }

    enum TopAppearance implements SkillDocumentPersistenceLocation {
        INSTANCE
    }

    record AppearanceOverride(int nodeIndex) implements SkillDocumentPersistenceLocation {
        public AppearanceOverride {
            requireNodeIndex(nodeIndex);
        }
    }

    private static void requireNodeIndex(int nodeIndex) {
        if (nodeIndex < 0) {
            throw new IllegalArgumentException("nodeIndex must be non-negative");
        }
    }
}
