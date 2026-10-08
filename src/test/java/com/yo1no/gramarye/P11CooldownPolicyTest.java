package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.*;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.validation.*;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Formal descriptor/resolver/profile coverage, not a native save or authenticated ARM claim. */
final class P11CooldownPolicyTest {
    private static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);
    private static final SkillId SKILL = new SkillId(new UUID(-1L, Long.MIN_VALUE));

    @Test
    void exactMathematicalIntegerCodecIsSeparateFromSemanticRange() {
        var codec = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec();
        for (var input : List.of("{}", "{\"cooldown_ticks\":1,\"extra\":0}",
                "{\"cooldown_ticks\":\"1\"}", "{\"cooldown_ticks\":true}",
                "{\"cooldown_ticks\":null}", "{\"cooldown_ticks\":1.5}",
                "{\"cooldown_ticks\":2147483648}", "{\"cooldown_ticks\":-2147483649}")) {
            assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(input)).error().isPresent(), input);
        }
        for (var input : List.of("1", "1.0", "1e0")) {
            assertEquals(new P9ActiveCastTriggerPayloadV1(1), codec.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"cooldown_ticks\":" + input + "}")).getOrThrow());
        }
        for (int duration : new int[] {Integer.MIN_VALUE, -1, 0, 1, 120, 600, 601, Integer.MAX_VALUE}) {
            var payload = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "{\"cooldown_ticks\":" + duration + "}")).getOrThrow();
            assertEquals(duration < 0 || duration > 600,
                    P9ActiveCastTriggerType.INSTANCE.validate(payload, CONTEXT).hasErrors());
        }
    }

    @Test
    void definitionNbtNumbersDoNotBecomeStrictPersistenceTagRules() {
        var codec = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec();
        for (var number : List.<Tag>of(ByteTag.valueOf((byte) 1), ShortTag.valueOf((short) 1),
                IntTag.valueOf(1), LongTag.valueOf(1), FloatTag.valueOf(1.0f), DoubleTag.valueOf(1.0))) {
            var raw = new CompoundTag(); raw.put("cooldown_ticks", number);
            assertEquals(new P9ActiveCastTriggerPayloadV1(1), codec.parse(NbtOps.INSTANCE, raw).getOrThrow());
        }
        var fractional = new CompoundTag(); fractional.putDouble("cooldown_ticks", 1.5);
        assertTrue(codec.parse(NbtOps.INSTANCE, fractional).error().isPresent());
        var overflow = new CompoundTag(); overflow.putLong("cooldown_ticks", 2147483648L);
        assertTrue(codec.parse(NbtOps.INSTANCE, overflow).error().isPresent());
    }

    @Test
    void guardedLegacyMigrationRetainsOriginalFamilyAndRejectsEveryNonemptyShape() {
        var step = P9ActiveCastTriggerType.INSTANCE.payloadMigrationPlan().stepFrom(0).orElseThrow();
        assertTrue(step.migrate(new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString("{}"))).isSuccess());
        assertTrue(step.migrate(new Dynamic<>(NbtOps.INSTANCE, new CompoundTag())).isSuccess());
        for (var raw : List.of("{\"cooldown_ticks\":0}", "{\"unknown\":0}", "[]", "0", "null")) {
            assertTrue(step.migrate(new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(raw))).error().isPresent());
        }
        var legacy = document(0, "{}");
        var before = SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        var resolved = assertInstanceOf(TriggerResolution.Resolved.class, resolveTrigger(legacy));
        assertSame(legacy.nodes().getFirst().trigger(), resolved.sourceEnvelope());
        assertEquals(1, resolved.definition().schemaVersion());
        assertEquals(new P9ActiveCastTriggerPayloadV1(0), resolved.definition().payload());
        assertEquals(before, SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow());
        assertInstanceOf(TriggerResolution.MigrationFailed.class, resolveTrigger(document(0, "{\"x\":0}")));
        var future = assertInstanceOf(TriggerResolution.MigrationFailed.class,
                resolveTrigger(document(2, "{\"cooldown_ticks\":120}")));
        assertEquals(2, future.originalEnvelope().schemaVersion());
        assertEquals(JsonParser.parseString("{\"cooldown_ticks\":120}"),
                future.originalEnvelope().copyRawPayload().convert(JsonOps.INSTANCE).getValue());
    }

    @Test
    void runtimeAcceptsPositivePolicyButStarterNeverPublishesIt() {
        for (int duration : new int[] {0, 1, 120, 600}) {
            var definition = validate(document(1, "{\"cooldown_ticks\":" + duration + "}"));
            assertTrue(P9StarterSkillContent.hasSupportedRuntimeGameplay(definition));
            assertEquals(duration == 0, P9StarterSkillContent.hasSupportedStarterGameplay(definition));
            assertEquals(duration, P9StarterSkillContent.runtimeCooldownTicks(definition).orElseThrow());
            assertEquals(duration == 0, P9StarterSkillContent.hasCanonicalGameplayFingerprint(definition));
        }
    }

    @Test
    void normalizedLegacyEqualsV1ZeroWhileEveryPositiveDurationIsContent() {
        var legacy = document(0, "{}");
        var zero = document(1, "{\"cooldown_ticks\":0}");
        var positive = document(1, "{\"cooldown_ticks\":120}");
        var other = document(1, "{\"cooldown_ticks\":600}");
        assertEquals(P9StarterSkillContent.normalizedContent(validate(legacy), legacy),
                P9StarterSkillContent.normalizedContent(validate(zero), zero));
        assertNotEquals(P9StarterSkillContent.fingerprintOf(validate(zero)),
                P9StarterSkillContent.fingerprintOf(validate(positive)));
        assertNotEquals(P9StarterSkillContent.normalizedContent(validate(positive), positive),
                P9StarterSkillContent.normalizedContent(validate(other), other));
        assertEquals(0, legacy.nodes().getFirst().trigger().schemaVersion());
        assertEquals(JsonParser.parseString("{}"), legacy.nodes().getFirst().trigger().copyRawPayload().getValue());
    }

    private static SkillDocument document(int schema, String payload) {
        var draft = P9StarterSkillContent.canonicalDraft(SKILL);
        var nodes = draft.nodes().stream().map(node -> new NodeDocument(
                ((DraftTriggerSlot.Present) node.trigger()).definition(),
                ((DraftActionSlot.Present) node.action()).definition(), node.appearanceOverride())).toList();
        var first = nodes.getFirst();
        return new SkillDocument(SkillDocument.CURRENT_SCHEMA_VERSION, SKILL, new SkillRevision(0),
                List.of(new NodeDocument(new DefinitionEnvelope(P9StarterSkillContent.ACTIVE_CAST_ID,
                        schema, new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(payload))),
                        first.action(), first.appearanceOverride()), nodes.get(1)), draft.appearance());
    }

    private static TriggerResolution resolveTrigger(SkillDocument document) {
        return resolver().resolve(document, new SkillDocumentReadReport(List.of(), false)).nodes().getFirst().trigger();
    }

    private static ValidatedSkillDefinition validate(SkillDocument document) {
        var analysis = new SkillValidationAnalyzer(new NodeProjectionResolver(), ProfileAvailabilityView.unknown())
                .analyze(resolver().resolve(document, new SkillDocumentReadReport(List.of(), false)), CONTEXT);
        assertFalse(analysis.report().hasErrors(), () -> analysis.report().toString());
        return assertInstanceOf(SkillValidationOutcome.Accepted.class,
                new SkillDefinitionProjector().project(analysis)).definition();
    }

    private static SkillCandidateResolver resolver() {
        return new SkillCandidateResolver(new TriggerTypeLookup() {
            public Optional<TriggerType<?>> find(ResourceLocation id) {
                return P9StarterSkillContent.ACTIVE_CAST_ID.equals(id) ? Optional.of(P9ActiveCastTriggerType.INSTANCE)
                        : P9StarterSkillContent.EFFECT_HIT_ID.equals(id) ? Optional.of(P9EffectHitTriggerType.INSTANCE) : Optional.empty();
            }
            public Optional<ResourceLocation> keyOf(TriggerType<?> type) {
                return type == P9ActiveCastTriggerType.INSTANCE ? Optional.of(P9StarterSkillContent.ACTIVE_CAST_ID)
                        : type == P9EffectHitTriggerType.INSTANCE ? Optional.of(P9StarterSkillContent.EFFECT_HIT_ID) : Optional.empty();
            }
        }, new ActionTypeLookup() {
            public Optional<ActionType<?>> find(ResourceLocation id) {
                return P9StarterSkillContent.SPAWN_PROJECTILE_ID.equals(id) ? Optional.of(P9SpawnProjectileActionType.INSTANCE)
                        : P9StarterSkillContent.DAMAGE_ID.equals(id) ? Optional.of(P9DamageActionType.INSTANCE) : Optional.empty();
            }
            public Optional<ResourceLocation> keyOf(ActionType<?> type) {
                return type == P9SpawnProjectileActionType.INSTANCE ? Optional.of(P9StarterSkillContent.SPAWN_PROJECTILE_ID)
                        : type == P9DamageActionType.INSTANCE ? Optional.of(P9StarterSkillContent.DAMAGE_ID) : Optional.empty();
            }
        });
    }
}
