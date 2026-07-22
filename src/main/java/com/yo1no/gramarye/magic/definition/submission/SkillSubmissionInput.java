package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import java.util.List;
import java.util.Objects;

/** Immutable pairing of a Draft with its actual tolerant-read provenance. */
public final class SkillSubmissionInput {
    private static final SkillDraftReadReport EMPTY_READ_REPORT =
            new SkillDraftReadReport(List.of(), false);

    private final SkillDraft draft;
    private final SkillDraftReadReport readReport;

    private SkillSubmissionInput(SkillDraft draft, SkillDraftReadReport readReport) {
        this.draft = Objects.requireNonNull(draft, "draft");
        this.readReport = Objects.requireNonNull(readReport, "readReport");
    }

    public static SkillSubmissionInput direct(SkillDraft draft) {
        return new SkillSubmissionInput(draft, EMPTY_READ_REPORT);
    }

    public static SkillSubmissionInput fromReadResult(SkillDraftReadResult readResult) {
        Objects.requireNonNull(readResult, "readResult");
        return new SkillSubmissionInput(readResult.draft(), readResult.report());
    }

    public SkillDraft draft() {
        return draft;
    }

    public SkillDraftReadReport readReport() {
        return readReport;
    }

    @Override
    public String toString() {
        return "SkillSubmissionInput[draftSchemaVersion=" + draft.draftSchemaVersion()
                + ", nodeCount=" + draft.nodes().size()
                + ", readFactCount=" + readReport.facts().size()
                + ", readReportTruncated=" + readReport.truncated() + "]";
    }
}
