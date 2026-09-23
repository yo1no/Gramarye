package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

/** Exact S5 server bootstrap, provisioning, identity, and resource boundary. */
final class P9S5BoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path ROOT_MAIN = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Path ROOT_SOURCE = ROOT_MAIN.resolve("Gramarye.java");
    private static final Path COMMAND_SOURCE = ROOT_MAIN.resolve("P9StarterCommand.java");
    private static final Path IDENTITY_SOURCE = ROOT_MAIN.resolve("P9StarterSkillIdentityV0.java");
    private static final Path BUILD_SOURCE = PROJECT_ROOT.resolve("build.gradle");
    private static final Path CLIENT_HARNESS_SOURCE = PROJECT_ROOT.resolve(
            "src/p9S5ClientHarness/java/com/yo1no/gramarye/"
                    + "P9S5ClientRuntimeHarness.java");
    private static final Path MAIN_RESOURCES = PROJECT_ROOT.resolve("src/main/resources");

    @Test
    void rootConstructionAndRegistrationFollowExactR00ThroughR26Order() throws IOException {
        var source = read(ROOT_SOURCE);

        assertOrdered(
                source,
                "Objects.requireNonNull(modBus, \"modBus\")",
                "Objects.requireNonNull(exactContainer, \"exactContainer\")",
                "if (!MOD_ID.equals(exactContainer.getModId()))",
                "var exactFacade = new P4E2QualificationFacade();",
                "P9StarterSkillContent.registerDefinitionTypes();",
                "MagicRegistries.register(modBus);",
                "P9StarterProjectileRegistration.register(modBus);",
                "new DescriptorMigrationAudit().register(modBus);",
                "playerSkillAttachmentService = PlayerSkillAttachmentService.registerOn(modBus);",
                "p8ServerPresentationService = P8ServerPresentationService.create();",
                "var profileAvailability = p8ServerPresentationService.profileAvailabilityView();",
                "var runtimeCapability = P6RuntimeExecutionCapability.forRuntimeAdapter();",
                "var p7LoginReadyPort = P7ServerAuthorizationBoundary.loginReadyPort(runtimeCapability);",
                "skillDefinitionStoreService = SkillDefinitionStoreService.registerOn(",
                "skillIdSource = SkillDraftCreationService.randomUuidSkillIdSource();",
                "skillDraftCreationService = new SkillDraftCreationService(",
                "skillSubmissionPolicyProvider = SkillSubmissionPolicyProvider.defaults();",
                "skillDefinitionSubmissionService = SkillDefinitionSubmissionService.production(",
                "skillSubmissionRecoveryService = SkillSubmissionRecoveryService.create(",
                "skillSubmissionRecoveryService.registerOn(NeoForge.EVENT_BUS);",
                "p5ServerRuntimeConfig = new P5ServerRuntimeConfig(modBus, exactContainer);",
                "skillRuntimeService = SkillRuntimeService.create(",
                "p8ServerPresentationService.registerAfterP5(NeoForge.EVENT_BUS);",
                "var p7AuthenticatedPlayerCastIngress = new P7AuthenticatedPlayerCastIngress(",
                "P7ServerAuthorizationBoundary.install(",
                "var p10TemplateValidation = new P10TemplateValidation(",
                "p10TemplateService = new P10TemplateService(p10TemplateValidation);",
                "p9StarterCommand = new P9StarterCommand(",
                "p10TemplateValidateCommand = new P10TemplateValidateCommand(",
                "NeoForge.EVENT_BUS.addListener(p9StarterCommand::register);",
                "NeoForge.EVENT_BUS.addListener(p10TemplateValidateCommand::register);",
                "NeoForge.EVENT_BUS.addListener(this::handleP9ReloadStarted);",
                "p10TemplateService.registerAfterP8(NeoForge.EVENT_BUS);",
                "NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::handleP9ReloadCompleted);",
                "NeoForge.EVENT_BUS.addListener(this::handleP5RuntimeStarted);",
                "exactContainer.registerExtensionPoint(P4E2QualificationFacade.class, exactFacade);");

        assertAll(
                () -> assertEquals(1, occurrences(source, "P6RuntimeExecutionCapability.forRuntimeAdapter()")),
                () -> assertEquals(1, occurrences(source, "PlayerSkillAttachmentService.registerOn(")),
                () -> assertEquals(1, occurrences(source, "SkillDefinitionStoreService.registerOn(")),
                () -> assertEquals(1, occurrences(source, "SkillDefinitionSubmissionService.production(")),
                () -> assertEquals(1, occurrences(source, "SkillRuntimeService.create(")),
                () -> assertEquals(1, occurrences(source, "new P9StarterCommand(")),
                () -> assertEquals(1, occurrences(source, "new P10TemplateService(")),
                () -> assertEquals(1, occurrences(source, "new P10TemplateValidation(")),
                () -> assertEquals(1, occurrences(source, "new P10TemplateValidateCommand(")),
                () -> assertEquals(1, occurrences(source, "P7ServerAuthorizationBoundary.install(")),
                () -> assertEquals(
                        Set.of("skillDefinitionStoreService"),
                        Arrays.stream(Gramarye.class.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers()))
                                .filter(method -> !method.isSynthetic())
                                .map(java.lang.reflect.Method::getName)
                                .collect(Collectors.toUnmodifiableSet())));
    }

    @Test
    void reloadCallbacksInvalidateAtStartAndReopenOnlyForFullDatapackSync()
            throws IOException, ReflectiveOperationException {
        var source = read(ROOT_SOURCE);
        var started = source.substring(
                source.indexOf("private void handleP9ReloadStarted("),
                source.indexOf("private void handleP9ReloadCompleted("));
        var completed = source.substring(
                source.indexOf("private void handleP9ReloadCompleted("),
                source.indexOf("private void handleP5RuntimeStarted("));
        var runtimeStarted = source.substring(
                source.indexOf("private void handleP5RuntimeStarted("),
                source.indexOf("/** Returns the controlled server skill subsystem port"));

        assertAll(
                () -> assertTrue(Modifier.isPrivate(Gramarye.class
                        .getDeclaredMethod(
                                "handleP9ReloadStarted",
                                net.neoforged.neoforge.event.AddReloadListenerEvent.class)
                        .getModifiers())),
                () -> assertTrue(Modifier.isPrivate(Gramarye.class
                        .getDeclaredMethod(
                                "handleP9ReloadCompleted",
                                net.neoforged.neoforge.event.OnDatapackSyncEvent.class)
                        .getModifiers())),
                () -> assertEquals(1, occurrences(started, "requestP9ReloadInvalidation()")),
                () -> assertFalse(started.contains("completeP9Reload(")),
                () -> assertEquals(1, occurrences(completed, "if (event.getPlayer() == null)")),
                () -> assertEquals(1, occurrences(completed, "completeP9Reload(")),
                () -> assertFalse(completed.contains("requestP9ReloadInvalidation()")),
                () -> assertOrdered(
                        completed,
                        "p10TemplateService.beginPostInstall(server)",
                        "p8ServerPresentationService.activateMatchingCandidateForRoot(server)",
                        "p10TemplateService.completeInstalled(server, outcome)",
                        "skillRuntimeService.completeP9Reload(server)"),
                () -> assertOrdered(completed, "markInstalled.run()", "activateP8.get()",
                        "settleP10.accept(outcome)", "reopenP5.run()"),
                () -> assertFalse(completed.contains("catch (")),
                () -> assertFalse(completed.contains("finally")),
                () -> assertOrdered(
                        runtimeStarted,
                        "p8ServerPresentationService.handleServerStarted(event);",
                        "p10TemplateService.handleServerStarted(event);",
                        "p5ServerRuntimeConfig.snapshotForStarted();",
                        "skillRuntimeService.handleRuntimeStarted(event, limits);"));
    }

    @Test
    void rootReloadCoordinatorSettlesEachExactNormalOutcomeBeforeReopening() throws Throwable {
        for (var outcome : P8ServerPresentationService.P8RootFullSyncOutcome.values()) {
            var calls = new java.util.ArrayList<String>();
            runRootReloadProbe(() -> calls.add("mark"), () -> {
                calls.add("p8");
                return outcome;
            }, actual -> {
                assertSame(outcome, actual);
                calls.add("p10");
            }, () -> calls.add("p5"));
            assertEquals(List.of("mark", "p8", "p10", "p5"), calls);
        }
    }

    @Test
    void rootReloadCoordinatorPropagatesEachFaultWithoutReopenRollbackOrRetry() {
        var order = List.of("mark", "p8", "p10", "p5");
        for (int index = 0; index < order.size(); index++) {
            final int failingStage = index;
            for (Throwable failure : List.of(new IllegalStateException("root reload"),
                    new AssertionError("root reload"))) {
                var calls = new java.util.ArrayList<String>();
                java.util.function.IntConsumer stage = selected -> {
                    calls.add(order.get(selected));
                    if (selected == failingStage) {
                        if (failure instanceof Error error) throw error;
                        throw (RuntimeException) failure;
                    }
                };
                assertSame(failure, assertThrows(Throwable.class, () -> runRootReloadProbe(
                        () -> stage.accept(0), () -> {
                            stage.accept(1);
                            return P8ServerPresentationService.P8RootFullSyncOutcome.ACTIVATED_CURRENT;
                        }, ignored -> stage.accept(2), () -> stage.accept(3))));
                assertEquals(order.subList(0, index + 1), calls);
            }
        }
    }

    private static void runRootReloadProbe(Runnable mark,
            java.util.function.Supplier<P8ServerPresentationService.P8RootFullSyncOutcome> p8,
            java.util.function.Consumer<P8ServerPresentationService.P8RootFullSyncOutcome> p10,
            Runnable p5) throws Throwable {
        var method = Gramarye.class.getDeclaredMethod("finishP10Reload", Runnable.class,
                java.util.function.Supplier.class, java.util.function.Consumer.class, Runnable.class);
        assertTrue(Modifier.isPrivate(method.getModifiers()) && Modifier.isStatic(method.getModifiers()));
        method.setAccessible(true);
        try {
            method.invoke(null, mark, p8, p10, p5);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    @Test
    void commandSurfaceAndSourceRemainExactPackageLocalFailClosedOwners()
            throws IOException, ReflectiveOperationException {
        var type = P9StarterCommand.class;
        var constructor = type.getDeclaredConstructor(
                PlayerSkillAttachmentService.class, SkillDefinitionSubmissionService.class,
                SkillDefinitionStoreService.class, P10TemplateService.class, P10TemplateValidation.class);
        var registration = type.getDeclaredMethod("register", RegisterCommandsEvent.class);
        var source = read(COMMAND_SOURCE);
        var fields = Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic())
                .collect(Collectors.toUnmodifiableMap(java.lang.reflect.Field::getName,
                        java.lang.reflect.Field::getType));
        assertAll(
                () -> assertFalse(Modifier.isPublic(type.getModifiers())),
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertEquals(1, type.getDeclaredConstructors().length),
                () -> assertEquals(0, constructor.getModifiers()),
                () -> assertEquals(void.class, registration.getReturnType()),
                () -> assertEquals(0, registration.getModifiers()),
                () -> assertEquals(Map.of("EQUIPPED_SLOT", int.class,
                        "attachments", PlayerSkillAttachmentService.class,
                        "submissions", SkillDefinitionSubmissionService.class,
                        "store", SkillDefinitionStoreService.class,
                        "templates", P10TemplateService.class,
                        "validation", P10TemplateValidation.class), fields),
                () -> assertTrue(Arrays.stream(type.getDeclaredFields()).allMatch(field ->
                        Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()))),
                () -> assertEquals(0, publicOrProtectedDeclaredMembers(type)),
                () -> assertTrue(source.contains("private static final int EQUIPPED_SLOT = 0;")),
                () -> assertEquals(2, occurrences(source, "Commands.literal(")),
                () -> assertEquals(1, occurrences(source, ".requires(source -> source.hasPermission(0))")),
                () -> assertEquals(1, occurrences(source, ".executes(context -> provision(context.getSource()))")),
                () -> assertFalse(source.contains("Commands.argument(")),
                () -> assertTrue(source.contains("!server.isSameThread()")),
                () -> assertTrue(source.contains("source.getEntity() instanceof ServerPlayer player")),
                () -> assertTrue(source.contains("source.source != player")),
                () -> assertTrue(source.contains("player instanceof FakePlayer")),
                () -> assertTrue(source.contains("server.getPlayerList().getPlayer(player.getUUID()) != player")),
                () -> assertEquals(1, occurrences(source, "observePendingRecovery(server, owner)")),
                () -> assertEquals(0, occurrences(source, "observeLatestStates(")),
                () -> assertEquals(1, occurrences(source, "submissions.submit(player, skillId)")),
                () -> assertEquals(1, occurrences(source, "attachments.putDraft(player, target)")),
                () -> assertEquals(1, occurrences(source, "attachments.setEquipped(")),
                () -> assertTrue(source.contains("MagicSafetyCeilings.MAX_PLAYER_DRAFTS")),
                () -> assertTrue(source.contains("instanceof SkillSubmissionCompositionOutcome.Committed committed")));
        for (var forbidden : List.of("SkillSubmissionRecoveryService", "recoverPersistedPlayer",
                "removeDraft", "deleteDraft", "rollback", "UUID.randomUUID",
                "setEquipped(player, 1", "setEquipped(player, 2", "canonicalDraft(")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void commandAlgorithmKeepsExactReadScanCreateSubmitRereadMutationOrder() throws IOException {
        // Historical method ID retained; §68 replaces the arbitrary-ID scan with exact lineage.
        var source = read(COMMAND_SOURCE);
        assertOrdered(source,
                "templates.capture(server)", "validation.validate(server, capture.body().orElseThrow())",
                "templates.isCurrent(server, capture)", "P9StarterSkillIdentityV0.forPlayer(player.getUUID())",
                "attachments.equippedAt(player, EQUIPPED_SLOT)", "store.ownerOf(server, skillId)",
                "store.latestReference(server, skillId)", "P9StarterSkillContent.sameNormalizedContent(",
                "return finishRevisionRoute(same, expected, latest,");
        var route = source.substring(source.indexOf("static Fine finishRevisionRoute("),
                source.indexOf("private Optional<Fine> mutationGuard("));
        assertOrdered(route, "if (same && expected.equals(latest))", "mutationGuard.get()",
                "if (rejection.isPresent())", "if (same) return equipExisting.apply(latest.orElseThrow())",
                "Integer.MAX_VALUE", "return submitSuccessor.get()");
        var guard = source.substring(source.indexOf("private Optional<Fine> mutationGuard("),
                source.indexOf("private Fine submitSuccessor("));
        assertOrdered(guard, "observePendingRecovery(server, owner)", "PendingRecoveryProjection.TargetInvalid",
                "PendingRecoveryProjection.Available", "!available.chains().isEmpty()",
                "!templates.isCurrent(server, capture)", "return Optional.empty()");
        var successor = source.substring(source.indexOf("private Fine submitSuccessor("),
                source.indexOf("static Fine submitAndEquip("));
        assertOrdered(successor, "attachments.findDraft(player, skillId)", "attachments.draftCount(player)",
                "attachments.putDraft(player, target)", "return submitAndEquip(() -> submissions.submit(player, skillId)",
                "reference -> equip(player, expected, reference, true)");
        var handoff = source.substring(source.indexOf("static Fine submitAndEquip("),
                source.indexOf("private Fine equip("));
        assertOrdered(handoff, "submit.get()", "instanceof SkillSubmissionCompositionOutcome.Committed committed",
                "equipCommitted.apply(committed.reference())", "submissionFailure(submission)");
        var finalEquip = source.substring(source.indexOf("static Fine finishEquip("),
                source.indexOf("static Fine submissionFailure("));
        assertOrdered(finalEquip, "read.get()", "if (!current.value().equals(expected))", "set.get()");
        assertAll(
                () -> assertEquals(1, occurrences(source, "return finishRevisionRoute(")),
                () -> assertEquals(1, occurrences(source, "() -> mutationGuard(server, owner, capture)")),
                () -> assertEquals(1, occurrences(source, "reference -> equip(player, expected, reference, false)")),
                () -> assertEquals(1, occurrences(source, "() -> submitSuccessor(player, expected, latest, observedLatestDocument,")),
                () -> assertEquals(1, occurrences(source, "return finishEquip(expected, committed,")),
                () -> assertEquals(2, occurrences(source, "attachments.equippedAt(")),
                () -> assertEquals(1, occurrences(source, "attachments.findDraft(")),
                () -> assertTrue(source.contains("draft.baseRevision().equals(latest.map(SkillReference::revision))")),
                () -> assertTrue(source.contains("sameContent(draft, latest.orElseThrow(), latestDocument)")),
                () -> assertTrue(source.contains("POST_COMMIT_STORE_COMMITTED")),
                () -> assertTrue(source.contains("STORE_JOURNAL_PUBLICATION_INVARIANT")),
                () -> assertFalse(source.contains("for (var state : latestStates)")),
                () -> assertFalse(source.contains("checkQuota(")));
    }

    @Test
    void deterministicIdentityMatchesExactUtf8NameUuidVectors() throws IOException {
        var zero = UUID.fromString("00000000-0000-0000-0000-000000000000");
        var sample = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        var high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        var source = read(IDENTITY_SOURCE);

        assertAll(
                () -> assertEquals(
                        UUID.fromString("25255bc6-06a7-3845-add1-a1bf9c8d6c8b"),
                        P9StarterSkillIdentityV0.forPlayer(zero).value()),
                () -> assertEquals(
                        UUID.fromString("302b9449-e85c-3f06-95fa-431c87022c64"),
                        P9StarterSkillIdentityV0.forPlayer(sample).value()),
                () -> assertEquals(
                        UUID.fromString("0e5c9e53-385e-310b-a093-38c7f043d0a3"),
                        P9StarterSkillIdentityV0.forPlayer(high).value()),
                () -> assertNotEquals(
                        P9StarterSkillIdentityV0.forPlayer(zero),
                        P9StarterSkillIdentityV0.forPlayer(sample)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> P9StarterSkillIdentityV0.forPlayer(null)),
                () -> assertEquals(1, occurrences(source, "UUID.nameUUIDFromBytes(")),
                () -> assertEquals(1, occurrences(source, "StandardCharsets.UTF_8")),
                () -> assertTrue(source.contains("gramarye:starter_bolt_v0")),
                () -> assertFalse(source.contains("getBytes()")),
                () -> assertFalse(source.contains("UUID.randomUUID")));
    }

    @Test
    void persistedCanonicalFingerprintUsesFormalProjectionAndExactReference() {
        var player = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        var skillId = P9StarterSkillIdentityV0.forPlayer(player);
        var revision = new SkillRevision(7);
        var document = formalDocument(P9StarterSkillContent.canonicalDraft(skillId), revision);
        var reference = new SkillReference(skillId, revision);
        var otherReference = new SkillReference(
                new SkillId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                revision);
        var noncanonical = new SkillDocument(
                document.schemaVersion(),
                document.skillId(),
                document.revision(),
                List.of(document.nodes().getFirst(), document.nodes().getFirst()),
                document.appearance());

        assertAll(
                () -> assertTrue(P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                        reference, document)),
                () -> assertFalse(P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                        otherReference, document)),
                () -> assertFalse(P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                        reference, noncanonical)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> P9StarterSkillContent.hasCanonicalGameplayFingerprint(null, document)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> P9StarterSkillContent.hasCanonicalGameplayFingerprint(reference, null)));
    }

    @Test
    void languageResourcesAreTheExactClosedUtf8Pair() throws IOException {
        var en = MAIN_RESOURCES.resolve("assets/gramarye/lang/en_us.json");
        var zh = MAIN_RESOURCES.resolve("assets/gramarye/lang/zh_tw.json");
        List<Path> jsonResources;
        try (var paths = Files.walk(MAIN_RESOURCES)) {
            jsonResources = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .map(PROJECT_ROOT::relativize).sorted().toList();
        }
        var keys = Set.of(
                "commands.gramarye.starter.slot_occupied",
                "commands.gramarye.starter.source_rejected",
                "commands.gramarye.starter.template_unavailable",
                "commands.gramarye.starter.stale_context",
                "commands.gramarye.starter.snapshot_changed",
                "commands.gramarye.starter.attachment_unavailable",
                "commands.gramarye.starter.store_unavailable",
                "commands.gramarye.starter.starter_identity_not_authorized",
                "commands.gramarye.starter.starter_identity_unavailable_or_collision",
                "commands.gramarye.starter.recovery_pending",
                "commands.gramarye.starter.recovery_target_invalid",
                "commands.gramarye.starter.recovery_unavailable",
                "commands.gramarye.starter.equip_state_unavailable",
                "commands.gramarye.starter.equip_publish_unavailable",
                "commands.gramarye.starter.draft_conflict",
                "commands.gramarye.starter.draft_has_unsubmitted_changes",
                "commands.gramarye.starter.draft_limit_reached",
                "commands.gramarye.starter.revision_exhausted",
                "commands.gramarye.starter.route_capacity_rejected",
                "commands.gramarye.starter.draft_retained_quota_rejected",
                "commands.gramarye.starter.draft_retained_capacity_rejected",
                "commands.gramarye.starter.draft_retained_submission_rejected",
                "commands.gramarye.starter.store_committed_journal_publication_invariant",
                "commands.gramarye.starter.committed_pending_recovery",
                "commands.gramarye.starter.committed_not_equipped",
                "commands.gramarye.starter.slot_unavailable",
                "commands.gramarye.starter.equip_conflict",
                "commands.gramarye.starter.already_current",
                "commands.gramarye.starter.equipped_target",
                "key.gramarye.cast", "key.categories.gramarye");
        var english = JsonParser.parseString(Files.readString(en, StandardCharsets.UTF_8)).getAsJsonObject();
        var chinese = JsonParser.parseString(Files.readString(zh, StandardCharsets.UTF_8)).getAsJsonObject();
        assertAll(
                () -> assertEquals(List.of(
                        Path.of("src/main/resources/assets/gramarye/lang/en_us.json"),
                        Path.of("src/main/resources/assets/gramarye/lang/zh_tw.json"),
                        Path.of("src/main/resources/data/gramarye/gramarye/skill_templates/starter_bolt_v0.json")),
                        jsonResources),
                () -> assertEquals(keys, english.keySet()),
                () -> assertEquals(keys, chinese.keySet()),
                () -> assertEquals("Cast Skill", english.get("key.gramarye.cast").getAsString()),
                () -> assertEquals("施放技能", chinese.get("key.gramarye.cast").getAsString()),
                () -> assertEquals("Gramarye", english.get("key.categories.gramarye").getAsString()),
                () -> assertEquals("Gramarye", chinese.get("key.categories.gramarye").getAsString()),
                () -> assertEquals("Starter skill slot is already occupied",
                        english.get("commands.gramarye.starter.slot_occupied").getAsString()),
                () -> assertEquals("起始技能欄位已被佔用",
                        chinese.get("commands.gramarye.starter.slot_occupied").getAsString()));
        for (var language : List.of(english, chinese)) {
            for (var key : keys) assertTrue(language.get(key).getAsString().length() <= 1_024);
            for (var success : List.of("already_current", "equipped_target")) {
                assertTrue(language.get("commands.gramarye.starter." + success).getAsString().contains("%s"));
            }
        }
        assertTrue(Files.readString(en, StandardCharsets.UTF_8).endsWith("\n"));
        assertTrue(Files.readString(zh, StandardCharsets.UTF_8).endsWith("\n"));
    }

    @Test
    void actualClientFixtureRecordsAndCleansOnlyItsExactPlayerFiles()
            throws IOException {
        var build = read(BUILD_SOURCE);
        var cleanup = build.substring(
                build.indexOf("def cleanupP9S5ClientRuntimePlayerData ="),
                build.indexOf("// Full unit/check paths compile", build.indexOf(
                        "def cleanupP9S5ClientRuntimePlayerData =")));
        var harness = read(CLIENT_HARNESS_SOURCE);

        assertAll(
                () -> assertTrue(harness.contains(
                        "P9-S5-CLIENT-OWNED-PLAYER-V1")),
                () -> assertTrue(harness.contains(
                        "StandardOpenOption.CREATE_NEW")),
                () -> assertTrue(build.contains(
                        "systemProperty 'gramarye.p9s5.clientHarnessOwnedPlayer'")),
                () -> assertTrue(cleanup.contains(
                        "playerdata/${uuid}.dat")),
                () -> assertTrue(cleanup.contains(
                        "playerdata/${uuid}.dat_old")),
                () -> assertTrue(cleanup.contains("stats/${uuid}.json")),
                () -> assertTrue(cleanup.contains(
                        "advancements/${uuid}.json")),
                () -> assertEquals(1, occurrences(cleanup,
                        "java.nio.file.Files.delete(target)")),
                () -> assertTrue(cleanup.contains(
                        "java.nio.file.Files.isSymbolicLink(target)")),
                () -> assertFalse(cleanup.contains("deleteDir")),
                () -> assertFalse(cleanup.contains("rm -")),
                () -> assertTrue(build.contains(
                        "finalizedBy(cleanupP9S5ClientRuntimePlayerData)")),
                () -> assertTrue(harness.contains("getDeclaredField(\"installedRootIngress\")")),
                () -> assertTrue(harness.contains("ingress.getClass() == P7AuthenticatedPlayerCastIngress.class")),
                () -> assertTrue(harness.contains("ready.chains().isEmpty()")),
                () -> assertTrue(harness.contains("observePendingRecovery(server, new SkillOwnerId(playerId))")),
                () -> assertTrue(harness.contains("server.reloadResources(selected)")),
                () -> assertTrue(harness.contains("minecraft.getConnection().sendCommand(COMMAND)")),
                () -> assertTrue(harness.contains("expectedDamage = 5.0F")),
                () -> assertTrue(build.contains("systemProperty 'gramarye.p10.frozenJar'")),
                () -> assertTrue(build.contains("[builtBy: tasks.named('jar')], tasks.named('jar', Jar).flatMap { it.archiveFile }")),
                () -> assertTrue(build.contains("add(p9S5ClientHarnessSourceSet.compileOnlyConfigurationName, sourceSets.main.output)")),
                () -> assertFalse(build.contains("add(p9S5ClientHarnessSourceSet.implementationConfigurationName, sourceSets.main.output)")),
                () -> assertTrue(build.contains("def runtimeClasspathFiles = p9S5ClientHarnessSourceSet.runtimeClasspath")),
                () -> assertTrue(build.contains("def runtimeFiles = runtimeClasspathFiles.files")),
                () -> assertFalse(build.contains("def runtimeFiles = p9S5ClientHarnessSourceSet.runtimeClasspath.files")),
                () -> assertTrue(build.contains("configurations.named(p9S5ClientHarnessSourceSet.runtimeClasspathConfigurationName)")),
                () -> assertTrue(build.contains("additionalRuntimeClasspathConfiguration.exclude(\n"
                        + "                    group: 'org.apache.commons', module: 'commons-compress')")),
                () -> assertTrue(build.contains("runtimeFiles.contains(frozenJar)")),
                () -> assertTrue(build.contains("runtimeFiles.any { mainOutputFiles.contains(it) }")),
                () -> assertTrue(build.contains("runtimeFiles.any { it.name == standaloneCompressName }")),
                () -> assertTrue(harness.contains("System.getProperty(\"java.class.path\", \"\")")),
                () -> assertTrue(harness.contains("JVM runtime classpath contains exploded production classes")),
                () -> assertTrue(harness.contains("roots.contains(frozenProductionJar)")),
                () -> assertTrue(harness.contains("frozenProductionJarHash.equals(productionJarHash(frozenProductionJar))")),
                () -> assertTrue(harness.contains("Arrays.equals(loaded.readAllBytes(), archived.readAllBytes())")),
                () -> assertFalse(harness.contains(".setEquipped(")),
                () -> assertFalse(harness.contains(".putDraft(")),
                () -> assertFalse(harness.contains(".submit(")),
                () -> assertFalse(harness.contains("new SkillDefinitionStoreService(")),
                () -> assertFalse(harness.contains("new PlayerSkillAttachmentService(")));
    }

    private static SkillDocument formalDocument(SkillDraft draft, SkillRevision revision) {
        var nodes = draft.nodes().stream()
                .map(node -> new NodeDocument(
                        assertInstanceOf(DraftTriggerSlot.Present.class, node.trigger()).definition(),
                        assertInstanceOf(DraftActionSlot.Present.class, node.action()).definition(),
                        node.appearanceOverride()))
                .toList();
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                draft.skillId(),
                revision,
                nodes,
                draft.appearance());
    }

    private static void assertOrdered(String source, String... needles) {
        var previous = -1;
        for (var needle : needles) {
            var current = source.indexOf(needle, previous + 1);
            assertTrue(current >= 0, () -> "missing exact source fragment: " + needle);
            assertTrue(current > previous, () -> "out-of-order source fragment: " + needle);
            previous = current;
        }
    }

    private static long publicOrProtectedDeclaredMembers(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()
                + Arrays.stream(type.getDeclaredFields())
                        .filter(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))
                        .count()
                + Arrays.stream(type.getDeclaredConstructors())
                        .filter(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))
                        .count();
    }

    private static int occurrences(String source, String needle) {
        var count = 0;
        for (var index = source.indexOf(needle); index >= 0;
                index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root not found");
        }
        return current;
    }
}
