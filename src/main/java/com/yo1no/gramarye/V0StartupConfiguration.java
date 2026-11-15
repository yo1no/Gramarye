package com.yo1no.gramarye;

import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.List;
import java.util.Optional;

sealed interface V0StartupLoadState permits V0StartupLoadState.Disabled,
        V0StartupLoadState.Ready, V0StartupLoadState.Invalid, V0StartupLoadState.Unavailable {
    enum Disabled implements V0StartupLoadState {
        INSTANCE
    }

    record Ready(V0StartupLimits limits) implements V0StartupLoadState {}

    record Invalid(V0ConfigFailure failure) implements V0StartupLoadState {}

    enum Unavailable implements V0StartupLoadState {
        INSTANCE
    }
}

record V0StartupLimits(
        int maxLineagesPerAttribution,
        int maxLineagesPerServer,
        int rootAttemptsPerTick,
        int maxManifestEntries,
        int projectileMaxAgeTicks,
        int projectileMaxRangeBlocks,
        int lineageDeadlineTicks,
        int presentationRecipientsPerOffer,
        long positiveRootFee) {}

record V0ConfigFailure(V0ConfigFailureCode code, String key, Optional<String> relatedKey) {}

enum V0ConfigFailureCode {
    UNAVAILABLE,
    WRONG_SECTION_TYPE,
    MISSING_REQUIRED_VALUE,
    WRONG_VALUE_TYPE,
    BELOW_MINIMUM,
    ABOVE_HARD_MAXIMUM,
    UNKNOWN_KEY,
    INCONSISTENT_LIMITS,
    ARITHMETIC_OVERFLOW
}

final class V0ConfigurationValidation {
    private static final String SECTION = "v0";
    private static final String ENABLED = "enabled";

    private static final int ATTRIBUTION = 0;
    private static final int SERVER = 1;
    private static final int ROOT_ATTEMPTS = 2;
    private static final int AGE = 4;
    private static final int RANGE = 5;
    private static final int DEADLINE = 6;
    private static final int RECIPIENTS = 7;

    private static final List<String> KEYS = List.of(
            "maxLineagesPerAttribution", "maxLineagesPerServer", "rootAttemptsPerTick",
            "maxManifestEntries", "projectileMaxAgeTicks", "projectileMaxRangeBlocks",
            "lineageDeadlineTicks", "presentationRecipientsPerOffer", "positiveRootFee");
    private static final int[] HARD_MAXIMUMS = {2, 4, 2, 8, 24, 24, 40, 16, 4};

    private V0ConfigurationValidation() {}

    static V0StartupLoadState decode(UnmodifiableCommentedConfig raw) {
        if (!raw.contains(SECTION)) {
            return V0StartupLoadState.Disabled.INSTANCE;
        }
        if (!(raw.getRaw(SECTION) instanceof UnmodifiableConfig table)) {
            return invalid(V0ConfigFailureCode.WRONG_SECTION_TYPE, SECTION);
        }
        if (!table.contains(ENABLED)) {
            return invalid(V0ConfigFailureCode.MISSING_REQUIRED_VALUE, path(ENABLED));
        }
        if (!(table.getRaw(ENABLED) instanceof Boolean enabled)) {
            return invalid(V0ConfigFailureCode.WRONG_VALUE_TYPE, path(ENABLED));
        }

        // Zero represents an omitted field in a disabled table. All supplied numbers are positive.
        var values = new long[KEYS.size()];
        for (int index = 0; index < KEYS.size(); index++) {
            var key = KEYS.get(index);
            var segment = List.of(key);
            if (!table.contains(segment)) {
                if (enabled) {
                    return invalid(V0ConfigFailureCode.MISSING_REQUIRED_VALUE, path(key));
                }
                continue;
            }
            Object rawNumber = table.getRaw(segment);
            long number;
            switch (rawNumber) {
                case Integer value -> number = value.longValue();
                case Long value -> number = value;
                case null, default -> {
                    return invalid(V0ConfigFailureCode.WRONG_VALUE_TYPE, path(key));
                }
            }
            if (number < (index == DEADLINE ? 3 : 1)) {
                return invalid(V0ConfigFailureCode.BELOW_MINIMUM, path(key));
            }
            if (number > HARD_MAXIMUMS[index]) {
                return invalid(V0ConfigFailureCode.ABOVE_HARD_MAXIMUM, path(key));
            }
            values[index] = number;
        }

        var unknown = firstUnknownKey(table);
        if (unknown != null) {
            return invalid(V0ConfigFailureCode.UNKNOWN_KEY, path(unknown));
        }
        return finish(enabled, values);
    }

    private static V0StartupLoadState finish(boolean enabled, long[] values) {
        try {
            var relationship = firstInvalidRelationship(values);
            if (relationship != null) {
                return relationship;
            }
            checkDerivedCounts(Math.toIntExact(values[SERVER]), Math.toIntExact(values[RECIPIENTS]));
            if (!enabled) {
                return V0StartupLoadState.Disabled.INSTANCE;
            }
            return new V0StartupLoadState.Ready(new V0StartupLimits(
                    Math.toIntExact(values[0]), Math.toIntExact(values[1]),
                    Math.toIntExact(values[2]), Math.toIntExact(values[3]),
                    Math.toIntExact(values[4]), Math.toIntExact(values[5]),
                    Math.toIntExact(values[6]), Math.toIntExact(values[7]), values[8]));
        } catch (ArithmeticException overflow) {
            return invalid(V0ConfigFailureCode.ARITHMETIC_OVERFLOW, path(KEYS.get(SERVER)));
        }
    }

    private static String firstUnknownKey(UnmodifiableConfig table) {
        String first = null;
        for (var entry : table.entrySet()) {
            var key = entry.getKey();
            if (!key.equals(ENABLED) && !KEYS.contains(key) && (first == null || key.compareTo(first) < 0)) {
                first = key;
            }
        }
        return first;
    }

    private static V0StartupLoadState.Invalid firstInvalidRelationship(long[] values) {
        long server = values[SERVER];
        long age = values[AGE];
        long range = values[RANGE];
        long deadline = values[DEADLINE];
        if (server != 0 && values[ATTRIBUTION] > server) {
            return inconsistent(ATTRIBUTION, SERVER);
        }
        if (server != 0 && values[ROOT_ATTEMPTS] > server) {
            return inconsistent(ROOT_ATTEMPTS, SERVER);
        }
        if (deadline != 0 && age != 0 && deadline < Math.addExact(age, 2L)) {
            return inconsistent(DEADLINE, AGE);
        }
        if (range != 0 && range < age) {
            return inconsistent(RANGE, AGE);
        }
        return null;
    }

    private static void checkDerivedCounts(int lineages, int recipients) {
        // Six event/reserve rows per lineage and the fixed cold cancellation fit one exact count.
        Math.addExact(Math.multiplyExact(6, lineages), 1);
        // The single provisional row carries at most three raw pins and 1,089 logical bytes.
        int withProvisional = Math.addExact(lineages, 1);
        Math.multiplyExact(withProvisional, 3);
        Math.multiplyExact(withProvisional, 1_089);
        int offers = Math.multiplyExact(4, lineages);
        Math.multiplyExact(offers, recipients);
        Math.multiplyExact(offers, 512);
    }

    private static V0StartupLoadState.Invalid inconsistent(int primary, int related) {
        return new V0StartupLoadState.Invalid(new V0ConfigFailure(
                V0ConfigFailureCode.INCONSISTENT_LIMITS, path(KEYS.get(primary)),
                Optional.of(path(KEYS.get(related)))));
    }

    private static V0StartupLoadState.Invalid invalid(V0ConfigFailureCode code, String key) {
        return new V0StartupLoadState.Invalid(new V0ConfigFailure(code, key, Optional.empty()));
    }

    private static String path(String key) {
        return SECTION + "." + key;
    }
}
