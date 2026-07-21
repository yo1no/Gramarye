package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.resources.ResourceLocation;

final class AppearanceStorageCodec {
    private static final List<String> KNOWN_FIELDS = List.of(
            "primary_argb",
            "secondary_argb",
            "sound_profile",
            "particle_profile",
            "trail_profile",
            "intensity_milli");
    private static final Set<String> KNOWN_FIELD_SET = Set.copyOf(KNOWN_FIELDS);
    private static final Pattern CANONICAL_INTEGER = Pattern.compile("(?:0|-[1-9][0-9]*|[1-9][0-9]*)");
    private static final Pattern CANONICAL_ARGB = Pattern.compile("0x[0-9A-F]{8}");
    private static final Pattern TOLERANT_ARGB = Pattern.compile("(?i)(?:0x|#)?([0-9a-f]{8})");
    private static final BigInteger MIN_SIGNED_ARGB = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX_UNSIGNED_ARGB = new BigInteger("4294967295");

    private AppearanceStorageCodec() {
    }

    static DataResult<AppearanceDocument> parseStrictTop(Dynamic<?> dynamic) {
        var bounds = relativeBounds(dynamic);
        if (bounds != DynamicTreeSupport.BoundsResult.WITHIN_LIMITS) {
            return DataResult.error(() -> "Canonical appearance exceeds hard bounds: " + bounds);
        }
        return parseStrictValues(dynamic).flatMap(values -> values.isEmpty()
                ? DataResult.success(AppearanceDocument.defaultAppearance())
                : DataResult.success(new AppearanceDocument.Decoded(values.definition())));
    }

    static DataResult<AppearanceOverrideDocument> parseStrictOverride(Dynamic<?> dynamic) {
        var bounds = relativeBounds(dynamic);
        if (bounds != DynamicTreeSupport.BoundsResult.WITHIN_LIMITS) {
            return DataResult.error(() -> "Canonical appearance override exceeds hard bounds: " + bounds);
        }
        return parseStrictValues(dynamic).flatMap(values -> values.isEmpty()
                ? DataResult.error(() -> "Empty appearance_override must be omitted")
                : DataResult.success(new AppearanceOverrideDocument.Decoded(values.override())));
    }

    static DataResult<AppearanceDocument> readTop(
            Dynamic<?> dynamic,
            ReadSite site,
            ReadFactCollector facts) {
        if (DynamicTreeSupport.isNull(dynamic)) {
            facts.add(site.fact(ReadFactCode.LEGACY_NULL_APPEARANCE_DEFAULTED, null));
            return DataResult.success(AppearanceDocument.defaultAppearance());
        }
        var bounds = relativeBounds(dynamic);
        if (bounds == DynamicTreeSupport.BoundsResult.DEPTH_EXCEEDED) {
            return DataResult.success(new AppearanceDocument.Rejected(
                    AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED));
        }
        if (bounds == DynamicTreeSupport.BoundsResult.NODE_COUNT_EXCEEDED) {
            return DataResult.success(new AppearanceDocument.Rejected(
                    AppearanceRejectionCode.NODE_LIMIT_EXCEEDED));
        }
        if (bounds == DynamicTreeSupport.BoundsResult.UNSUPPORTED
                || bounds == DynamicTreeSupport.BoundsResult.KEY_LENGTH_EXCEEDED) {
            return DataResult.error(() -> "Unsupported appearance Dynamic representation");
        }

        var localFacts = new ArrayList<ReadFact>();
        var parsed = parseTolerantValues(dynamic, site, localFacts);
        if (parsed.error().isPresent()) {
            return AppearanceRawSnapshot.capture(dynamic).map(snapshot -> new AppearanceDocument.Unparsed(snapshot));
        }
        var values = parsed.result().orElseThrow();
        facts.addAll(localFacts);
        return DataResult.success(values.isEmpty()
                ? AppearanceDocument.defaultAppearance()
                : new AppearanceDocument.Decoded(values.definition()));
    }

    static DataResult<AppearanceOverrideDocument> readOverride(
            Dynamic<?> dynamic,
            ReadSite site,
            ReadFactCollector facts) {
        if (DynamicTreeSupport.isNull(dynamic)) {
            facts.add(site.fact(ReadFactCode.LEGACY_NULL_OVERRIDE_NORMALIZED, null));
            return DataResult.success(AppearanceOverrideDocument.none());
        }
        var bounds = relativeBounds(dynamic);
        if (bounds == DynamicTreeSupport.BoundsResult.DEPTH_EXCEEDED) {
            return DataResult.success(new AppearanceOverrideDocument.Rejected(
                    AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED));
        }
        if (bounds == DynamicTreeSupport.BoundsResult.NODE_COUNT_EXCEEDED) {
            return DataResult.success(new AppearanceOverrideDocument.Rejected(
                    AppearanceRejectionCode.NODE_LIMIT_EXCEEDED));
        }
        if (bounds == DynamicTreeSupport.BoundsResult.UNSUPPORTED
                || bounds == DynamicTreeSupport.BoundsResult.KEY_LENGTH_EXCEEDED) {
            return DataResult.error(() -> "Unsupported appearance override Dynamic representation");
        }

        var localFacts = new ArrayList<ReadFact>();
        var parsed = parseTolerantValues(dynamic, site, localFacts);
        if (parsed.error().isPresent()) {
            return AppearanceRawSnapshot.capture(dynamic)
                    .map(snapshot -> new AppearanceOverrideDocument.Unparsed(snapshot));
        }
        var values = parsed.result().orElseThrow();
        facts.addAll(localFacts);
        return DataResult.success(values.isEmpty()
                ? AppearanceOverrideDocument.none()
                : new AppearanceOverrideDocument.Decoded(values.override()));
    }

    static <T> DataResult<T> encodeCanonical(AppearanceDocument appearance, DynamicOps<T> ops) {
        if (appearance instanceof AppearanceDocument.Default) {
            return DataResult.success(ops.emptyMap());
        }
        if (appearance instanceof AppearanceDocument.Decoded decoded) {
            return encodeValues(decoded.definition(), ops);
        }
        return DataResult.error(() -> "Strict canonical Codec cannot encode unparsed or rejected appearance");
    }

    static <T> DataResult<T> encodeCanonical(AppearanceOverrideDocument appearance, DynamicOps<T> ops) {
        if (appearance instanceof AppearanceOverrideDocument.Decoded decoded) {
            return encodeValues(decoded.override(), ops);
        }
        return DataResult.error(() -> "Strict canonical Codec requires a decoded appearance override");
    }

    static <T> DataResult<T> encodeForStorage(AppearanceDocument appearance, DynamicOps<T> ops) {
        if (appearance instanceof AppearanceDocument.Default
                || appearance instanceof AppearanceDocument.Rejected) {
            return DataResult.success(ops.emptyMap());
        }
        if (appearance instanceof AppearanceDocument.Decoded decoded) {
            return encodeValues(decoded.definition(), ops);
        }
        var unparsed = (AppearanceDocument.Unparsed) appearance;
        return encodeRaw(unparsed.rawFamily(), unparsed.copyRawAppearance(), ops);
    }

    static <T> DataResult<Optional<T>> encodeForStorage(
            AppearanceOverrideDocument appearance,
            DynamicOps<T> ops) {
        if (appearance instanceof AppearanceOverrideDocument.None
                || appearance instanceof AppearanceOverrideDocument.Rejected) {
            return DataResult.success(Optional.empty());
        }
        if (appearance instanceof AppearanceOverrideDocument.Decoded decoded) {
            return encodeValues(decoded.override(), ops).map(Optional::of);
        }
        var unparsed = (AppearanceOverrideDocument.Unparsed) appearance;
        return encodeRaw(unparsed.rawFamily(), unparsed.copyRawAppearance(), ops).map(Optional::of);
    }

    private static DynamicTreeSupport.BoundsResult relativeBounds(Dynamic<?> dynamic) {
        return DynamicTreeSupport.checkBounds(
                dynamic,
                MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_DEPTH,
                MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_NODES);
    }

    private static DataResult<ParsedValues> parseStrictValues(Dynamic<?> dynamic) {
        if (DynamicTreeSupport.isNull(dynamic)) {
            return DataResult.error(() -> "Canonical appearance must not be null");
        }
        return parseStrictValuesCaptured(dynamic);
    }

    private static <T> DataResult<ParsedValues> parseStrictValuesCaptured(Dynamic<T> dynamic) {
        var mapResult = dynamic.getOps().getMap(dynamic.getValue());
        if (mapResult.error().isPresent()) {
            return DataResult.error(() -> "Canonical appearance must be an object or compound");
        }
        var map = mapResult.result().orElseThrow();
        var unknown = unknownKeys(dynamic.getOps(), map);
        if (unknown.error().isPresent()) {
            return DataResult.error(() -> unknown.error().orElseThrow().message());
        }
        if (!unknown.result().orElseThrow().isEmpty()) {
            return DataResult.error(() -> "Canonical appearance contains unknown fields");
        }

        var primary = parseOptionalCanonicalArgb(dynamic.getOps(), map, "primary_argb");
        var secondary = parseOptionalCanonicalArgb(dynamic.getOps(), map, "secondary_argb");
        var sound = parseCanonicalProfile(dynamic.getOps(), map, "sound_profile");
        var particle = parseCanonicalProfile(dynamic.getOps(), map, "particle_profile");
        var trail = parseCanonicalProfile(dynamic.getOps(), map, "trail_profile");
        var intensity = parseOptionalCanonicalIntensity(dynamic.getOps(), map, "intensity_milli");
        return combine(primary, secondary, sound, particle, trail, intensity);
    }

    private static DataResult<ParsedValues> parseTolerantValues(
            Dynamic<?> dynamic,
            ReadSite site,
            List<ReadFact> facts) {
        return parseTolerantValuesCaptured(dynamic, site, facts);
    }

    private static <T> DataResult<ParsedValues> parseTolerantValuesCaptured(
            Dynamic<T> dynamic,
            ReadSite site,
            List<ReadFact> facts) {
        var mapResult = dynamic.getOps().getMap(dynamic.getValue());
        if (mapResult.error().isPresent()) {
            return DataResult.error(() -> "Appearance must be an object or compound");
        }
        var map = mapResult.result().orElseThrow();
        var unknown = unknownKeys(dynamic.getOps(), map);
        if (unknown.error().isPresent()) {
            return DataResult.error(() -> unknown.error().orElseThrow().message());
        }
        for (var ignored : unknown.result().orElseThrow()) {
            facts.add(site.fact(ReadFactCode.UNKNOWN_APPEARANCE_FIELD_IGNORED, null));
        }

        var primary = parseOptionalTolerantArgb(
                dynamic.getOps(), map, "primary_argb", AppearanceField.PRIMARY_ARGB, site, facts);
        var secondary = parseOptionalTolerantArgb(
                dynamic.getOps(), map, "secondary_argb", AppearanceField.SECONDARY_ARGB, site, facts);
        var sound = parseTolerantProfile(
                dynamic.getOps(), map, "sound_profile", AppearanceField.SOUND_PROFILE, site, facts);
        var particle = parseTolerantProfile(
                dynamic.getOps(), map, "particle_profile", AppearanceField.PARTICLE_PROFILE, site, facts);
        var trail = parseTolerantProfile(
                dynamic.getOps(), map, "trail_profile", AppearanceField.TRAIL_PROFILE, site, facts);
        var intensity = parseOptionalTolerantIntensity(dynamic.getOps(), map, site, facts);
        return combine(primary, secondary, sound, particle, trail, intensity);
    }

    private static DataResult<ParsedValues> combine(
            DataResult<OptionalInt> primary,
            DataResult<OptionalInt> secondary,
            DataResult<ProfileSelection> sound,
            DataResult<ProfileSelection> particle,
            DataResult<ProfileSelection> trail,
            DataResult<OptionalInt> intensity) {
        if (primary.error().isPresent()
                || secondary.error().isPresent()
                || sound.error().isPresent()
                || particle.error().isPresent()
                || trail.error().isPresent()
                || intensity.error().isPresent()) {
            return DataResult.error(() -> "One or more known appearance fields are malformed");
        }
        return DataResult.success(new ParsedValues(
                primary.result().orElseThrow(),
                secondary.result().orElseThrow(),
                sound.result().orElseThrow(),
                particle.result().orElseThrow(),
                trail.result().orElseThrow(),
                intensity.result().orElseThrow()));
    }

    private static <T> DataResult<OptionalInt> parseOptionalCanonicalArgb(
            DynamicOps<T> ops,
            MapLike<T> map,
            String name) {
        var field = field(ops, map, name);
        if (field.isEmpty()) {
            return DataResult.success(OptionalInt.empty());
        }
        var raw = field.orElseThrow().value();
        var value = new Dynamic<>(ops, raw);
        if (DynamicTreeSupport.isNull(value)) {
            return DataResult.error(() -> name + " must not be null");
        }
        if (!(raw instanceof JsonPrimitive) && ops.getStringValue(raw).error().isPresent()) {
            return DataResult.error(() -> name + " must be a canonical ARGB string");
        }
        return ops.getStringValue(raw).flatMap(text -> CANONICAL_ARGB.matcher(text).matches()
                ? DataResult.success(OptionalInt.of(parseArgbHex(text.substring(2))))
                : DataResult.error(() -> name + " must use 0xAARRGGBB"));
    }

    private static <T> DataResult<OptionalInt> parseOptionalTolerantArgb(
            DynamicOps<T> ops,
            MapLike<T> map,
            String name,
            AppearanceField field,
            ReadSite site,
            List<ReadFact> facts) {
        var fieldValue = field(ops, map, name);
        if (fieldValue.isEmpty()) {
            return DataResult.success(OptionalInt.empty());
        }
        var raw = fieldValue.orElseThrow().value();
        var dynamic = new Dynamic<>(ops, raw);
        if (DynamicTreeSupport.isNull(dynamic)) {
            facts.add(site.fact(ReadFactCode.LEGACY_NULL_SCALAR_NORMALIZED, field));
            return DataResult.success(OptionalInt.empty());
        }
        var string = ops.getStringValue(raw);
        if (string.error().isEmpty()) {
            var matcher = TOLERANT_ARGB.matcher(string.result().orElseThrow());
            return matcher.matches()
                    ? DataResult.success(OptionalInt.of(parseArgbHex(matcher.group(1))))
                    : DataResult.error(() -> name + " has invalid ARGB text");
        }
        return exactIntegral(raw).flatMap(number -> number.compareTo(MIN_SIGNED_ARGB) < 0
                        || number.compareTo(MAX_UNSIGNED_ARGB) > 0
                ? DataResult.error(() -> name + " is outside the 32-bit ARGB bit-pattern range")
                : DataResult.success(OptionalInt.of(number.intValue())));
    }

    private static <T> DataResult<OptionalInt> parseOptionalCanonicalIntensity(
            DynamicOps<T> ops,
            MapLike<T> map,
            String name) {
        var field = field(ops, map, name);
        if (field.isEmpty()) {
            return DataResult.success(OptionalInt.empty());
        }
        var raw = field.orElseThrow().value();
        return canonicalInteger(raw).flatMap(number -> number.signum() < 0
                        || number.compareTo(BigInteger.valueOf(MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY)) > 0
                ? DataResult.error(() -> name + " is outside the canonical range")
                : DataResult.success(OptionalInt.of(number.intValue())));
    }

    private static <T> DataResult<OptionalInt> parseOptionalTolerantIntensity(
            DynamicOps<T> ops,
            MapLike<T> map,
            ReadSite site,
            List<ReadFact> facts) {
        var field = field(ops, map, "intensity_milli");
        if (field.isEmpty()) {
            return DataResult.success(OptionalInt.empty());
        }
        var raw = field.orElseThrow().value();
        var dynamic = new Dynamic<>(ops, raw);
        if (DynamicTreeSupport.isNull(dynamic)) {
            facts.add(site.fact(
                    ReadFactCode.LEGACY_NULL_SCALAR_NORMALIZED,
                    AppearanceField.INTENSITY_MILLI));
            return DataResult.success(OptionalInt.empty());
        }
        return exactIntegral(raw).flatMap(number -> {
            if (number.signum() < 0) {
                facts.add(site.fact(ReadFactCode.INTENSITY_CLAMPED_LOW, AppearanceField.INTENSITY_MILLI));
                return DataResult.success(OptionalInt.of(0));
            }
            if (number.compareTo(BigInteger.valueOf(MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY)) > 0) {
                facts.add(site.fact(ReadFactCode.INTENSITY_CLAMPED_HIGH, AppearanceField.INTENSITY_MILLI));
                return DataResult.success(OptionalInt.of(MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY));
            }
            return DataResult.success(OptionalInt.of(number.intValue()));
        });
    }

    private static <T> DataResult<ProfileSelection> parseCanonicalProfile(
            DynamicOps<T> ops,
            MapLike<T> appearance,
            String name) {
        var field = field(ops, appearance, name);
        if (field.isEmpty()) {
            return DataResult.success(ProfileSelection.inherit());
        }
        var raw = field.orElseThrow().value();
        var dynamic = new Dynamic<>(ops, raw);
        if (DynamicTreeSupport.isNull(dynamic)) {
            return DataResult.error(() -> name + " must not be null");
        }
        return parseProfileObject(dynamic);
    }

    private static <T> DataResult<ProfileSelection> parseTolerantProfile(
            DynamicOps<T> ops,
            MapLike<T> appearance,
            String name,
            AppearanceField field,
            ReadSite site,
            List<ReadFact> facts) {
        var fieldValue = field(ops, appearance, name);
        if (fieldValue.isEmpty()) {
            return DataResult.success(ProfileSelection.inherit());
        }
        var raw = fieldValue.orElseThrow().value();
        var dynamic = new Dynamic<>(ops, raw);
        if (DynamicTreeSupport.isNull(dynamic)) {
            facts.add(site.fact(ReadFactCode.LEGACY_NULL_PROFILE_NORMALIZED, field));
            return DataResult.success(ProfileSelection.disabled());
        }
        return parseProfileObject(dynamic);
    }

    private static <T> DataResult<ProfileSelection> parseProfileObject(Dynamic<T> dynamic) {
        var mapResult = dynamic.getOps().getMap(dynamic.getValue());
        if (mapResult.error().isPresent()) {
            return DataResult.error(() -> "Profile selection must be a tagged object");
        }
        var map = mapResult.result().orElseThrow();
        var modeField = field(dynamic.getOps(), map, "mode");
        if (modeField.isEmpty()) {
            return DataResult.error(() -> "Profile selection mode is required");
        }
        var modeRaw = modeField.orElseThrow().value();
        var mode = dynamic.getOps().getStringValue(modeRaw);
        if (mode.error().isPresent()) {
            return DataResult.error(() -> "Profile selection mode must be a string");
        }
        var idField = field(dynamic.getOps(), map, "id");
        var keys = profileKeys(dynamic.getOps(), map);
        if (keys.error().isPresent()) {
            return DataResult.error(() -> keys.error().orElseThrow().message());
        }
        return switch (mode.result().orElseThrow()) {
            case "disabled" -> idField.isEmpty() && keys.result().orElseThrow().equals(Set.of("mode"))
                    ? DataResult.success(ProfileSelection.disabled())
                    : DataResult.error(() -> "Disabled profile selection must contain only mode");
            case "specified" -> idField.isPresent() && keys.result().orElseThrow().equals(Set.of("mode", "id"))
                    ? ResourceLocation.CODEC.parse(dynamic.getOps(), idField.orElseThrow().value())
                            .map(ProfileSelection::specified)
                    : DataResult.error(() -> "Specified profile selection requires exactly mode and id");
            default -> DataResult.error(() -> "Unsupported profile selection mode");
        };
    }

    private static <T> DataResult<List<String>> unknownKeys(DynamicOps<T> ops, MapLike<T> map) {
        var unknown = new ArrayList<String>();
        for (var entry : map.entries().toList()) {
            var key = ops.getStringValue(entry.getFirst());
            if (key.error().isPresent()) {
                return DataResult.error(() -> "Appearance key must be a string");
            }
            var name = key.result().orElseThrow();
            if (!KNOWN_FIELD_SET.contains(name)) {
                unknown.add(name);
            }
        }
        unknown.sort(String::compareTo);
        return DataResult.success(List.copyOf(unknown));
    }

    private static <T> Optional<FieldValue<T>> field(DynamicOps<T> ops, MapLike<T> map, String name) {
        for (var entry : map.entries().toList()) {
            var key = ops.getStringValue(entry.getFirst());
            if (key.error().isEmpty() && name.equals(key.result().orElseThrow())) {
                return Optional.of(new FieldValue<>(entry.getSecond()));
            }
        }
        return Optional.empty();
    }

    private static <T> DataResult<Set<String>> profileKeys(DynamicOps<T> ops, MapLike<T> map) {
        var keys = new HashSet<String>();
        for (Pair<T, T> entry : map.entries().toList()) {
            var key = ops.getStringValue(entry.getFirst());
            if (key.error().isPresent()) {
                return DataResult.error(() -> "Profile key must be a string");
            }
            keys.add(key.result().orElseThrow());
        }
        return DataResult.success(Set.copyOf(keys));
    }

    private static DataResult<BigInteger> canonicalInteger(Object raw) {
        if (raw instanceof JsonPrimitive primitive && primitive.isNumber()) {
            var text = primitive.getAsString();
            if (!CANONICAL_INTEGER.matcher(text).matches()) {
                return DataResult.error(() -> "Canonical integer must not use floating or exponent syntax");
            }
            try {
                return DataResult.success(new BigInteger(text));
            } catch (NumberFormatException exception) {
                return DataResult.error(() -> "Canonical integer is malformed");
            }
        }
        if (raw instanceof IntTag intTag) {
            return DataResult.success(BigInteger.valueOf(intTag.getAsInt()));
        }
        return DataResult.error(() -> "Canonical integer must use an integer representation");
    }

    private static DataResult<BigInteger> exactIntegral(Object raw) {
        try {
            if (raw instanceof JsonPrimitive primitive && primitive.isNumber()) {
                return DataResult.success(primitive.getAsBigDecimal().toBigIntegerExact());
            }
            if (raw instanceof FloatTag floatTag) {
                var value = floatTag.getAsFloat();
                return Float.isFinite(value)
                        ? DataResult.success(BigDecimal.valueOf(value).toBigIntegerExact())
                        : DataResult.error(() -> "Numeric appearance value must be finite");
            }
            if (raw instanceof DoubleTag doubleTag) {
                var value = doubleTag.getAsDouble();
                return Double.isFinite(value)
                        ? DataResult.success(BigDecimal.valueOf(value).toBigIntegerExact())
                        : DataResult.error(() -> "Numeric appearance value must be finite");
            }
            if (raw instanceof NumericTag numericTag) {
                return DataResult.success(BigInteger.valueOf(numericTag.getAsLong()));
            }
        } catch (ArithmeticException | NumberFormatException exception) {
            return DataResult.error(() -> "Numeric appearance value must be an exact finite integer");
        }
        return DataResult.error(() -> "Appearance value must be numeric");
    }

    private static int parseArgbHex(String text) {
        return (int) Long.parseUnsignedLong(text, 16);
    }

    private static <T> DataResult<T> encodeValues(AppearanceDefinition definition, DynamicOps<T> ops) {
        return encodeValues(
                definition.primaryArgb(),
                definition.secondaryArgb(),
                definition.soundProfile(),
                definition.particleProfile(),
                definition.trailProfile(),
                definition.intensityMilli(),
                ops);
    }

    private static <T> DataResult<T> encodeValues(AppearanceOverride override, DynamicOps<T> ops) {
        return encodeValues(
                override.primaryArgb(),
                override.secondaryArgb(),
                override.soundProfile(),
                override.particleProfile(),
                override.trailProfile(),
                override.intensityMilli(),
                ops);
    }

    private static <T> DataResult<T> encodeValues(
            OptionalInt primary,
            OptionalInt secondary,
            ProfileSelection sound,
            ProfileSelection particle,
            ProfileSelection trail,
            OptionalInt intensity,
            DynamicOps<T> ops) {
        var builder = ops.mapBuilder();
        if (primary.isPresent()) {
            builder.add("primary_argb", ops.createString(canonicalArgb(primary.getAsInt())));
        }
        if (secondary.isPresent()) {
            builder.add("secondary_argb", ops.createString(canonicalArgb(secondary.getAsInt())));
        }
        builder = addProfile(builder, "sound_profile", sound, ops);
        builder = addProfile(builder, "particle_profile", particle, ops);
        builder = addProfile(builder, "trail_profile", trail, ops);
        if (intensity.isPresent()) {
            builder.add("intensity_milli", ops.createInt(intensity.getAsInt()));
        }
        return builder.build(ops.empty());
    }

    private static <T> com.mojang.serialization.RecordBuilder<T> addProfile(
            com.mojang.serialization.RecordBuilder<T> builder,
            String name,
            ProfileSelection selection,
            DynamicOps<T> ops) {
        if (selection instanceof ProfileSelection.Inherit) {
            return builder;
        }
        var profile = ops.mapBuilder();
        if (selection instanceof ProfileSelection.Disabled) {
            profile.add("mode", ops.createString("disabled"));
        } else {
            var specified = (ProfileSelection.Specified) selection;
            profile.add("mode", ops.createString("specified"));
            profile.add("id", ResourceLocation.CODEC.encodeStart(ops, specified.id()));
        }
        builder.add(name, profile.build(ops.empty()));
        return builder;
    }

    private static String canonicalArgb(int value) {
        return String.format(Locale.ROOT, "0x%08X", value);
    }

    private static <T> DataResult<T> encodeRaw(
            SerializedTreeFamily rawFamily,
            Dynamic<?> raw,
            DynamicOps<T> targetOps) {
        var targetFamily = DynamicTreeSupport.family(targetOps);
        if (targetFamily.error().isPresent()) {
            return DataResult.error(() -> targetFamily.error().orElseThrow().message());
        }
        if (targetFamily.result().orElseThrow() != rawFamily) {
            return DataResult.error(() -> "Unparsed appearance cannot be written across DynamicOps families");
        }
        return Codec.PASSTHROUGH.encodeStart(targetOps, raw);
    }

    private record ParsedValues(
            OptionalInt primary,
            OptionalInt secondary,
            ProfileSelection sound,
            ProfileSelection particle,
            ProfileSelection trail,
            OptionalInt intensity) {
        AppearanceDefinition definition() {
            return new AppearanceDefinition(primary, secondary, sound, particle, trail, intensity);
        }

        AppearanceOverride override() {
            return new AppearanceOverride(primary, secondary, sound, particle, trail, intensity);
        }

        boolean isEmpty() {
            return primary.isEmpty()
                    && secondary.isEmpty()
                    && sound instanceof ProfileSelection.Inherit
                    && particle instanceof ProfileSelection.Inherit
                    && trail instanceof ProfileSelection.Inherit
                    && intensity.isEmpty();
        }
    }

    private record FieldValue<T>(T value) {
    }
}

record ReadSite(ReadLocationKind kind, OptionalInt nodeIndex) {
    ReadSite {
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(nodeIndex, "nodeIndex");
    }

    ReadFact fact(ReadFactCode code, AppearanceField field) {
        return new ReadFact(code, kind, nodeIndex, Optional.ofNullable(field));
    }
}
