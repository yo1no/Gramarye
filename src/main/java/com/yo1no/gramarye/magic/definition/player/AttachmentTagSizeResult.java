package com.yo1no.gramarye.magic.definition.player;

sealed interface AttachmentTagSizeResult
        permits AttachmentTagSizeResult.WithinLimit, AttachmentTagSizeResult.Exceeded {
    record WithinLimit(long exactByteCount) implements AttachmentTagSizeResult {
        public WithinLimit {
            if (exactByteCount < 0) {
                throw new IllegalArgumentException("exactByteCount must be non-negative");
            }
        }
    }

    record Exceeded(long observedAtLeast, long maximum) implements AttachmentTagSizeResult {
        public Exceeded {
            if (maximum < 0 || maximum == Long.MAX_VALUE || observedAtLeast != maximum + 1) {
                throw new IllegalArgumentException("capacity result must identify maximum + 1");
            }
        }
    }
}
