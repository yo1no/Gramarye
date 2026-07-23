package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** Deterministically combines the bounded Draft and B3 reports without losing hidden state. */
final class SubmissionReportMerger {
    private SubmissionReportMerger() {
    }

    static ValidationResult merge(ValidationResult earlier, ValidationResult later) {
        Objects.requireNonNull(earlier, "earlier");
        Objects.requireNonNull(later, "later");
        var collector = new ValidationCollector();
        append(collector, earlier);
        append(collector, later);
        return collector.result();
    }

    private static void append(ValidationCollector collector, ValidationResult source) {
        for (var issue : source.issues()) {
            collector.add(issue);
        }
        collector.inheritReportState(source);
    }
}
