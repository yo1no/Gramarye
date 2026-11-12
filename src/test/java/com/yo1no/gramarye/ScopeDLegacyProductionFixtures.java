package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Test-only access to the exact package-private production starter descriptors and template. */
public final class ScopeDLegacyProductionFixtures {
    public static final SkillId ID = new SkillId(new UUID(918L, 20L));
    private static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);
    private static final SkillDocumentReadReport NO_FACTS = new SkillDocumentReadReport(List.of(), false);

    private ScopeDLegacyProductionFixtures() {}

    public static SkillDocument starter() {
        var draft = P9StarterSkillContent.canonicalDraft(ID);
        return new SkillDocument(SkillDocument.CURRENT_SCHEMA_VERSION, ID, new SkillRevision(0),
                draft.nodes().stream().map(node -> new NodeDocument(
                        ((DraftTriggerSlot.Present) node.trigger()).definition(),
                        ((DraftActionSlot.Present) node.action()).definition(),
                        node.appearanceOverride())).toList(), draft.appearance());
    }

    public static Template template(int revision, int activeSchema, int cooldown, int damageSchema, int damage)
            throws IOException {
        try (var resource = ScopeDLegacyProductionFixtures.class.getResourceAsStream(
                "/data/gramarye/gramarye/skill_templates/starter_bolt_v0.json");
             var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var nodes = json.getAsJsonArray("nodes");
            var active = nodes.get(0).getAsJsonObject().getAsJsonObject("trigger");
            active.addProperty("schema_version", activeSchema);
            var activePayload = active.getAsJsonObject("payload");
            if (activeSchema == 0) {
                activePayload.remove("cooldown_ticks");
            } else {
                activePayload.addProperty("cooldown_ticks", cooldown);
            }
            var action = nodes.get(1).getAsJsonObject().getAsJsonObject("action");
            action.addProperty("schema_version", damageSchema);
            action.getAsJsonObject("payload").addProperty("magnitude", damage);
            var decoded = (P10TemplateCodec.Decoded) P10TemplateCodec.decode(new ByteArrayInputStream(
                    json.toString().getBytes(StandardCharsets.UTF_8)));
            var validation = validation();
            var document = decoded.body().document(ID, new SkillRevision(revision));
            return new Template(document, validation.validate(decoded.body(), CONTEXT, ValidationResult.valid())
                    .classification().name(), runtimeProfile(document));
        }
    }

    public static TemplateTransition retainLkg(Template current, Template invalid) {
        var retained = P10TemplateServiceTest.retainLegacyCompilerFixture(
                validation(), body(current.document()), body(invalid.document()));
        return new TemplateTransition(retained.document(ID, current.document().revision()), invalid.document());
    }

    private static P10TemplateBody body(SkillDocument document) {
        return new P10TemplateBody(document.nodes(), document.appearance());
    }

    private static P10TemplateValidation validation() {
        return new P10TemplateValidation(SkillSubmissionPolicyProvider.defaults(),
                P8ServerPresentationService.create(), resolver(), ProfileAvailabilityView.unknown());
    }

    public static SkillCandidateResolver resolver() {
        return resolver(Map.of(), Map.of());
    }

    public static SkillCandidateResolver resolver(
            Map<ResourceLocation, TriggerType<?>> triggerAdditions,
            Map<ResourceLocation, ActionType<?>> actionAdditions) {
        var triggers = Map.<ResourceLocation, TriggerType<?>>of(
                P9StarterSkillContent.ACTIVE_CAST_ID, P9ActiveCastTriggerType.INSTANCE,
                P9StarterSkillContent.EFFECT_HIT_ID, P9EffectHitTriggerType.INSTANCE);
        var actions = Map.<ResourceLocation, ActionType<?>>of(
                P9StarterSkillContent.DAMAGE_ID, P9DamageActionType.INSTANCE,
                P9StarterSkillContent.SPAWN_PROJECTILE_ID, P9SpawnProjectileActionType.INSTANCE);
        return new SkillCandidateResolver(new TriggerTypeLookup() {
            @Override
            public Optional<TriggerType<?>> find(ResourceLocation key) {
                return Optional.ofNullable(triggerAdditions.getOrDefault(key, triggers.get(key)));
            }

            @Override
            public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
                return key(triggers, descriptor).or(() -> key(triggerAdditions, descriptor));
            }
        }, new ActionTypeLookup() {
            @Override
            public Optional<ActionType<?>> find(ResourceLocation key) {
                return Optional.ofNullable(actionAdditions.getOrDefault(key, actions.get(key)));
            }

            @Override
            public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
                return key(actions, descriptor).or(() -> key(actionAdditions, descriptor));
            }
        });
    }

    private static <T> Optional<ResourceLocation> key(Map<ResourceLocation, T> entries, T descriptor) {
        return entries.entrySet().stream().filter(entry -> entry.getValue() == descriptor)
                .map(Map.Entry::getKey).findFirst();
    }

    public static ValidatedSkillDefinition validated(SkillDocument document) {
        var projector = new SkillDefinitionProjector();
        var analyzer = new SkillValidationAnalyzer(new NodeProjectionResolver(), ProfileAvailabilityView.unknown());
        var outcome = projector.project(analyzer.analyze(resolver().resolve(document, NO_FACTS), CONTEXT));
        return outcome instanceof SkillValidationOutcome.Accepted accepted
                ? accepted.definition() : null;
    }

    private static boolean runtimeProfile(SkillDocument document) {
        var definition = validated(document);
        return definition != null && P9StarterSkillContent.hasSupportedRuntimeGameplay(definition);
    }

    public record Template(SkillDocument document, String classification, boolean runtimeProfile) {}
    public record TemplateTransition(SkillDocument retained, SkillDocument rejected) {}
}
