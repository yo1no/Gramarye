package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable typed path whose rendering is bounded and deterministic. */
public record ValidationPath(List<ValidationPathSegment> segments) {
    private static final ValidationPath EMPTY = new ValidationPath(List.of());

    public ValidationPath {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.size() > MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS) {
            throw new IllegalArgumentException("validation path exceeds the segment ceiling");
        }
        if (render(segments).length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("rendered validation path exceeds the string ceiling");
        }
    }

    public static ValidationPath empty() {
        return EMPTY;
    }

    public ValidationPath field(String name) {
        return append(new ValidationPathSegment.Field(name));
    }

    public ValidationPath index(int value) {
        return append(new ValidationPathSegment.Index(value));
    }

    public String render() {
        return render(segments);
    }

    private ValidationPath append(ValidationPathSegment segment) {
        Objects.requireNonNull(segment, "segment");
        var appended = new ArrayList<ValidationPathSegment>(segments.size() + 1);
        appended.addAll(segments);
        appended.add(segment);
        return new ValidationPath(appended);
    }

    private static String render(List<ValidationPathSegment> segments) {
        var rendered = new StringBuilder();
        for (var segment : segments) {
            if (segment instanceof ValidationPathSegment.Field field) {
                if (!rendered.isEmpty()) {
                    rendered.append('.');
                }
                rendered.append(field.name());
            } else if (segment instanceof ValidationPathSegment.Index index) {
                rendered.append('[').append(index.value()).append(']');
            }
        }
        return rendered.toString();
    }

    @Override
    public String toString() {
        return render();
    }
}
