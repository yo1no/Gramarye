package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationPathSegment;
import java.util.ArrayList;
import java.util.Objects;

/** Bounded typed-path composition shared by inspector and descriptor validation reports. */
final class PayloadPathPrefixer {
    private PayloadPathPrefixer() {
    }

    static Result prefix(ValidationPath prefix, ValidationPath relative) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(relative, "relative");
        var segmentCount = prefix.segments().size() + relative.segments().size();
        if (segmentCount > MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS) {
            return new Result.Overflow(new ValidationIssueMetadata.Limit(
                    segmentCount, MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS));
        }

        var combined = new ArrayList<ValidationPathSegment>(segmentCount);
        combined.addAll(prefix.segments());
        combined.addAll(relative.segments());
        var renderLength = renderedLength(combined);
        if (renderLength > MagicSafetyCeilings.MAX_STRING_LENGTH) {
            return new Result.Overflow(new ValidationIssueMetadata.Limit(
                    renderLength, MagicSafetyCeilings.MAX_STRING_LENGTH));
        }
        return new Result.Success(new ValidationPath(combined));
    }

    private static int renderedLength(Iterable<ValidationPathSegment> segments) {
        var length = 0;
        for (var segment : segments) {
            if (segment instanceof ValidationPathSegment.Field field) {
                if (length > 0) {
                    length++;
                }
                length += field.name().length();
            } else if (segment instanceof ValidationPathSegment.Index index) {
                length += 2 + decimalDigits(index.value());
            }
        }
        return length;
    }

    private static int decimalDigits(int value) {
        var digits = 1;
        while (value >= 10) {
            value /= 10;
            digits++;
        }
        return digits;
    }

    sealed interface Result permits Result.Success, Result.Overflow {
        record Success(ValidationPath path) implements Result {
            public Success {
                Objects.requireNonNull(path, "path");
            }
        }

        record Overflow(ValidationIssueMetadata.Limit metadata) implements Result {
            public Overflow {
                Objects.requireNonNull(metadata, "metadata");
            }
        }
    }
}
