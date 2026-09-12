package com.yo1no.gramarye;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeDefinition;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Package-local owner of the immutable canonical starter content and its registration. */
final class P9StarterSkillContent {
    static final ResourceLocation ACTIVE_CAST_ID = id("active_cast");
    static final ResourceLocation SPAWN_PROJECTILE_ID = id("spawn_projectile");
    static final ResourceLocation EFFECT_HIT_ID = id("effect_hit");
    static final ResourceLocation DAMAGE_ID = id("damage");

    static final Codec<Integer> EXACT_INT = exactIntegerCodec();
    static final Codec<Long> EXACT_LONG = exactLongCodec();

    private static final StarterGameplayFingerprintV0 CANONICAL_FINGERPRINT =
            new StarterGameplayFingerprintV0(
                    0,
                    ACTIVE_CAST_ID,
                    SPAWN_PROJECTILE_ID,
                    0,
                    0L,
                    0,
                    ActionOutputKind.PROJECTILE,
                    1,
                    EFFECT_HIT_ID,
                    SourceSelection.PRIOR_NODE,
                    0,
                    0,
                    true,
                    DAMAGE_ID,
                    4_000L,
                    0L);

    private static boolean definitionTypesDeclared;

    private P9StarterSkillContent() {
    }

    static void registerDefinitionTypes() {
        if (definitionTypesDeclared) {
            throw new IllegalStateException("P9 starter definition types were already declared");
        }
        definitionTypesDeclared = true;
        MagicRegistries.TRIGGER_TYPES.register(
                ACTIVE_CAST_ID.getPath(), () -> P9ActiveCastTriggerType.INSTANCE);
        MagicRegistries.TRIGGER_TYPES.register(
                EFFECT_HIT_ID.getPath(), () -> P9EffectHitTriggerType.INSTANCE);
        MagicRegistries.ACTION_TYPES.register(
                SPAWN_PROJECTILE_ID.getPath(), () -> P9SpawnProjectileActionType.INSTANCE);
        MagicRegistries.ACTION_TYPES.register(
                DAMAGE_ID.getPath(), () -> P9DamageActionType.INSTANCE);
    }

    /** Builds the one complete, unsubmitted canonical Draft for a caller-supplied identity. */
    static SkillDraft canonicalDraft(SkillId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                Optional.empty(),
                List.of(
                        new DraftNode(
                                DraftTriggerSlot.present(triggerEnvelope(
                                        ACTIVE_CAST_ID,
                                        P9ActiveCastTriggerType.INSTANCE.payloadCodec(),
                                        P9ActiveCastTriggerPayloadV0.INSTANCE)),
                                DraftActionSlot.present(actionEnvelope(
                                        SPAWN_PROJECTILE_ID,
                                        P9SpawnProjectileActionType.INSTANCE.payloadCodec(),
                                        new P9SpawnProjectileActionPayloadV0(0, 0L))),
                                AppearanceOverrideDocument.None.INSTANCE),
                        new DraftNode(
                                DraftTriggerSlot.present(triggerEnvelope(
                                        EFFECT_HIT_ID,
                                        P9EffectHitTriggerType.INSTANCE.payloadCodec(),
                                        new P9EffectHitTriggerPayloadV0(0, 0, true))),
                                DraftActionSlot.present(actionEnvelope(
                                        DAMAGE_ID,
                                        P9DamageActionType.INSTANCE.payloadCodec(),
                                        new P9DamageActionPayloadV0(4_000L, 0L))),
                                AppearanceOverrideDocument.None.INSTANCE)),
                AppearanceDocument.Default.INSTANCE);
    }

    /**
     * Compares only the §63.4 gameplay tuple after formal resolution and validation.
     * Identity, revision and appearance are deliberately absent from the fingerprint.
     */
    static boolean hasCanonicalGameplayFingerprint(ValidatedSkillDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return fingerprintOf(definition)
                .map(CANONICAL_FINGERPRINT::equals)
                .orElse(false);
    }

    static Optional<StarterGameplayFingerprintV0> fingerprintOf(
            ValidatedSkillDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return fingerprint(definition);
    }

    static <C> MapCodec<C> closedPayload(Set<String> allowedMembers, MapCodec<C> delegate) {
        var allowed = Set.copyOf(Objects.requireNonNull(allowedMembers, "allowedMembers"));
        Objects.requireNonNull(delegate, "delegate");
        return new MapCodec<>() {
            @Override
            public <T> DataResult<C> decode(DynamicOps<T> ops, MapLike<T> input) {
                var entries = input.entries().iterator();
                while (entries.hasNext()) {
                    var key = ops.getStringValue(entries.next().getFirst()).result();
                    if (key.isEmpty() || !allowed.contains(key.orElseThrow())) {
                        return DataResult.error(() -> "Unknown P9 starter payload member");
                    }
                }
                return delegate.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(
                    C input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return delegate.encode(input, ops, prefix);
            }

            @Override
            public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }

            @Override
            public String toString() {
                return "ClosedP9StarterPayload[" + delegate + "]";
            }
        };
    }

    private static Optional<StarterGameplayFingerprintV0> fingerprint(
            ValidatedSkillDefinition definition) {
        if (definition.nodes().size() != 2) {
            return Optional.empty();
        }

        var node0 = definition.nodes().get(0);
        var node1 = definition.nodes().get(1);
        if (!hasCanonicalNode0Projection(node0)
                || !hasCanonicalNode1Projection(node1)
                || node0.trigger().descriptor() != P9ActiveCastTriggerType.INSTANCE
                || node0.trigger().payload() != P9ActiveCastTriggerPayloadV0.INSTANCE
                || node0.action().descriptor() != P9SpawnProjectileActionType.INSTANCE
                || !(node0.action().payload() instanceof P9SpawnProjectileActionPayloadV0 spawn)
                || node1.trigger().descriptor() != P9EffectHitTriggerType.INSTANCE
                || !(node1.trigger().payload() instanceof P9EffectHitTriggerPayloadV0 hit)
                || node1.action().descriptor() != P9DamageActionType.INSTANCE
                || !(node1.action().payload() instanceof P9DamageActionPayloadV0 damage)) {
            return Optional.empty();
        }

        return Optional.of(new StarterGameplayFingerprintV0(
                node0.nodeIndex(),
                ACTIVE_CAST_ID,
                SPAWN_PROJECTILE_ID,
                spawn.profileCode(),
                spawn.manaCost(),
                0,
                ActionOutputKind.PROJECTILE,
                node1.nodeIndex(),
                EFFECT_HIT_ID,
                SourceSelection.PRIOR_NODE,
                hit.sourceNodeIndex(),
                hit.sourceOutputOrdinal(),
                hit.includeDerived(),
                DAMAGE_ID,
                damage.magnitude(),
                damage.manaCost()));
    }

    private static boolean hasCanonicalNode0Projection(ValidatedNodeDefinition node) {
        var trigger = node.references().trigger();
        var action = node.references().action();
        return node.nodeIndex() == 0
                && trigger.sourceSelection() == SourceSelection.NONE
                && trigger.targetSelection() == TargetSelection.NONE
                && !trigger.providesCurrentTarget()
                && trigger.references().isEmpty()
                && action.sourceSelection() == SourceSelection.NONE
                && action.targetSelection() == TargetSelection.NONE
                && action.references().isEmpty()
                && action.producedOutputs().equals(Set.of(ActionOutputKind.PROJECTILE));
    }

    private static boolean hasCanonicalNode1Projection(ValidatedNodeDefinition node) {
        var trigger = node.references().trigger();
        var action = node.references().action();
        if (node.nodeIndex() != 1
                || trigger.sourceSelection() != SourceSelection.PRIOR_NODE
                || trigger.targetSelection() != TargetSelection.CURRENT_TARGET
                || !trigger.providesCurrentTarget()
                || trigger.references().size() != 1
                || action.sourceSelection() != SourceSelection.NONE
                || action.targetSelection() != TargetSelection.CURRENT_TARGET
                || !action.references().isEmpty()
                || !action.producedOutputs().isEmpty()) {
            return false;
        }
        var source = trigger.references().getFirst();
        return source.referencedNodeIndex() == 0
                && source.role() == ReferenceRole.SOURCE
                && source.requiredOutputKind().equals(Optional.of(ActionOutputKind.PROJECTILE));
    }

    private static <P extends TriggerPayload> DefinitionEnvelope triggerEnvelope(
            ResourceLocation typeId, MapCodec<P> codec, P payload) {
        return encodedEnvelope(typeId, codec, payload);
    }

    private static <P extends ActionPayload> DefinitionEnvelope actionEnvelope(
            ResourceLocation typeId, MapCodec<P> codec, P payload) {
        return encodedEnvelope(typeId, codec, payload);
    }

    private static <P> DefinitionEnvelope encodedEnvelope(
            ResourceLocation typeId, MapCodec<P> codec, P payload) {
        var encoded = codec.codec().encodeStart(JsonOps.INSTANCE, payload).getOrThrow();
        return new DefinitionEnvelope(typeId, 0, new Dynamic<>(JsonOps.INSTANCE, encoded));
    }

    private static Codec<Integer> exactIntegerCodec() {
        return new PrimitiveCodec<>() {
            @Override
            public <T> DataResult<Integer> read(DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input).flatMap(number -> {
                    try {
                        return DataResult.success(new BigDecimal(number.toString()).intValueExact());
                    } catch (ArithmeticException | NumberFormatException failure) {
                        return DataResult.error(() -> "Expected an exact integer");
                    }
                });
            }

            @Override
            public <T> T write(DynamicOps<T> ops, Integer value) {
                return ops.createInt(value);
            }
        };
    }

    private static Codec<Long> exactLongCodec() {
        return new PrimitiveCodec<>() {
            @Override
            public <T> DataResult<Long> read(DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input).flatMap(number -> {
                    try {
                        return DataResult.success(new BigDecimal(number.toString()).longValueExact());
                    } catch (ArithmeticException | NumberFormatException failure) {
                        return DataResult.error(() -> "Expected an exact long");
                    }
                });
            }

            @Override
            public <T> T write(DynamicOps<T> ops, Long value) {
                return ops.createLong(value);
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

}

/** Exact ordered gameplay-only semantic key used by later P9 recovery. */
record StarterGameplayFingerprintV0(
        int firstNodeIndex,
        ResourceLocation activeCastTypeId,
        ResourceLocation spawnProjectileTypeId,
        int profileCode,
        long spawnManaCost,
        int firstOutputOrdinal,
        ActionOutputKind firstOutputKind,
        int secondNodeIndex,
        ResourceLocation effectHitTypeId,
        SourceSelection sourceSelection,
        int sourceNodeIndex,
        int sourceOutputOrdinal,
        boolean includeDerived,
        ResourceLocation damageTypeId,
        long magnitude,
        long damageManaCost) {
}
