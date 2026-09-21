package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
                "p9StarterCommand = new P9StarterCommand(",
                "NeoForge.EVENT_BUS.addListener(p9StarterCommand::register);",
                "NeoForge.EVENT_BUS.addListener(this::handleP9ReloadStarted);",
                "NeoForge.EVENT_BUS.addListener(this::handleP9ReloadCompleted);",
                "NeoForge.EVENT_BUS.addListener(this::handleP5RuntimeStarted);",
                "exactContainer.registerExtensionPoint(P4E2QualificationFacade.class, exactFacade);");

        assertAll(
                () -> assertEquals(1, occurrences(source, "P6RuntimeExecutionCapability.forRuntimeAdapter()")),
                () -> assertEquals(1, occurrences(source, "PlayerSkillAttachmentService.registerOn(")),
                () -> assertEquals(1, occurrences(source, "SkillDefinitionStoreService.registerOn(")),
                () -> assertEquals(1, occurrences(source, "SkillDefinitionSubmissionService.production(")),
                () -> assertEquals(1, occurrences(source, "SkillRuntimeService.create(")),
                () -> assertEquals(1, occurrences(source, "new P9StarterCommand(")),
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
                        runtimeStarted,
                        "p8ServerPresentationService.handleServerStarted(event);",
                        "p5ServerRuntimeConfig.snapshotForStarted();",
                        "skillRuntimeService.handleRuntimeStarted(event, limits);"));
    }

    @Test
    void commandSurfaceAndSourceRemainExactPackageLocalFailClosedOwners()
            throws IOException, ReflectiveOperationException {
        var type = P9StarterCommand.class;
        var constructor = type.getDeclaredConstructor(
                PlayerSkillAttachmentService.class,
                SkillDefinitionSubmissionService.class,
                SkillDefinitionStoreService.class);
        var registration = type.getDeclaredMethod("register", RegisterCommandsEvent.class);
        var dispatcherRegistration = type.getDeclaredMethod("register", CommandDispatcher.class);
        var source = read(COMMAND_SOURCE);
        var fields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .collect(Collectors.toUnmodifiableMap(
                        java.lang.reflect.Field::getName,
                        java.lang.reflect.Field::getType));

        assertAll(
                () -> assertFalse(Modifier.isPublic(type.getModifiers())),
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertEquals(1, type.getDeclaredConstructors().length),
                () -> assertFalse(Modifier.isPublic(constructor.getModifiers())),
                () -> assertFalse(Modifier.isProtected(constructor.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(constructor.getModifiers())),
                () -> assertEquals(void.class, registration.getReturnType()),
                () -> assertFalse(Modifier.isPublic(registration.getModifiers())),
                () -> assertFalse(Modifier.isProtected(registration.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(registration.getModifiers())),
                () -> assertTrue(Modifier.isPrivate(dispatcherRegistration.getModifiers())),
                () -> assertEquals(
                        Map.of(
                                "EQUIPPED_SLOT", int.class,
                                "attachments", PlayerSkillAttachmentService.class,
                                "submissions", SkillDefinitionSubmissionService.class,
                                "store", SkillDefinitionStoreService.class),
                        fields),
                () -> assertTrue(Arrays.stream(type.getDeclaredFields())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertEquals(0, publicOrProtectedDeclaredMembers(type)),
                () -> assertTrue(source.contains("private static final int EQUIPPED_SLOT = 0;")),
                () -> assertEquals(2, occurrences(source, "Commands.literal(")),
                () -> assertEquals(1, occurrences(source, "Commands.literal(\"gramarye\")")),
                () -> assertEquals(1, occurrences(source, "Commands.literal(\"starter\")")),
                () -> assertEquals(1, occurrences(source, ".requires(source -> source.hasPermission(0))")),
                () -> assertEquals(1, occurrences(source, ".executes(context -> provision(context.getSource()))")),
                () -> assertFalse(source.contains("Commands.argument(")),
                () -> assertTrue(source.contains("!server.isSameThread()")),
                () -> assertTrue(source.contains("source.getEntity() instanceof ServerPlayer player")),
                () -> assertTrue(source.contains("source.source != player")),
                () -> assertTrue(source.contains("player instanceof FakePlayer")),
                () -> assertTrue(source.contains("server.getPlayerList().getPlayer(player.getUUID()) != player")),
                () -> assertEquals(1, occurrences(source, "observePendingRecovery(server, owner)")),
                () -> assertEquals(1, occurrences(source, "observeLatestStates(player)")),
                () -> assertEquals(1, occurrences(source, "submissions.submit(player, skillId)")),
                () -> assertEquals(1, occurrences(source, "attachments.putDraft(player, canonicalDraft)")),
                () -> assertEquals(1, occurrences(source, "attachments.setEquipped(")),
                () -> assertEquals(
                        1,
                        occurrences(
                                source,
                                "source.sendFailure(Component.translatable(\n"
                                        + "                        \"commands.gramarye.starter.slot_occupied\"));")),
                () -> assertEquals(2, occurrences(source, "SkillDraftPersistenceFacade.encodeCurrent(")),
                () -> assertTrue(source.contains("MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES")),
                () -> assertTrue(source.contains("MagicSafetyCeilings.MAX_PLAYER_DRAFTS")),
                () -> assertTrue(source.contains("instanceof SkillSubmissionCompositionOutcome.Committed committed")));

        for (var forbidden : List.of(
                "SkillSubmissionRecoveryService",
                "recoverPersistedPlayer",
                "CommittedPendingAttachmentRecovery",
                "removeDraft",
                "deleteDraft",
                "rollback",
                "UUID.randomUUID",
                "setEquipped(player, 1",
                "setEquipped(player, 2")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void commandAlgorithmKeepsExactReadScanCreateSubmitRereadMutationOrder() throws IOException {
        var source = read(COMMAND_SOURCE);
        var provision = source.substring(
                source.indexOf("private int provision("),
                source.indexOf("private Optional<SkillReference> createOrSubmitCanonical("));
        var creation = source.substring(
                source.indexOf("private Optional<SkillReference> createOrSubmitCanonical("),
                source.indexOf("private ReferenceCheck checkReference("));

        var firstEquipped = provision.indexOf("attachments.equippedAt(player, EQUIPPED_SLOT)");
        var recovery = provision.indexOf("observePendingRecovery(server, owner)");
        var latestScan = provision.indexOf("attachments.observeLatestStates(player)");
        var loop = provision.indexOf("for (var state : latestStates)");
        var create = provision.indexOf("createOrSubmitCanonical(player, server, owner)");
        var finalEquipped = provision.indexOf(
                "attachments.equippedAt(player, EQUIPPED_SLOT)", firstEquipped + 1);
        var mutation = provision.indexOf("attachments.setEquipped(", finalEquipped);

        assertAll(
                () -> assertTrue(
                        firstEquipped >= 0
                                && firstEquipped < recovery
                                && recovery < latestScan
                                && latestScan < loop
                                && loop < create
                                && create < finalEquipped
                                && finalEquipped < mutation,
                        "slot/read-only recovery/scan/create/final-reread/mutation order"),
                () -> assertEquals(2, occurrences(provision, "attachments.equippedAt(")),
                () -> assertTrue(provision.contains("if (matches.size() > 1)")),
                () -> assertTrue(provision.contains("if (matches.size() == 1)")),
                () -> assertTrue(provision.contains("if (finalEquipped.isPresent())")),
                () -> assertOrdered(
                        creation,
                        "P9StarterSkillIdentityV0.forPlayer(player.getUUID())",
                        "attachments.findLatestState(player, skillId)",
                        "store.latestReference(server, skillId)",
                        "store.ownerOf(server, skillId)",
                        "P9StarterSkillContent.canonicalDraft(skillId)",
                        "attachments.findDraft(player, skillId)",
                        "attachments.draftCount(player)",
                        "attachments.findDraft(player, skillId)",
                        "attachments.putDraft(player, canonicalDraft)",
                        "submissions.submit(player, skillId)",
                        "checkReference(server, owner, reference)"),
                () -> assertEquals(2, occurrences(creation, "attachments.findDraft(player, skillId)")));
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
                    .map(PROJECT_ROOT::relativize)
                    .sorted()
                    .toList();
        }
        var expectedEn = "{\n"
                + "  \"commands.gramarye.starter.slot_occupied\": "
                + "\"Starter skill slot is already occupied\",\n"
                + "  \"key.gramarye.cast\": \"Cast Skill\",\n"
                + "  \"key.categories.gramarye\": \"Gramarye\"\n"
                + "}\n";
        var expectedZh = "{\n"
                + "  \"commands.gramarye.starter.slot_occupied\": \"起始技能欄位已被佔用\",\n"
                + "  \"key.gramarye.cast\": \"施放技能\",\n"
                + "  \"key.categories.gramarye\": \"Gramarye\"\n"
                + "}\n";

        assertAll(
                () -> assertEquals(
                        List.of(
                                Path.of("src/main/resources/assets/gramarye/lang/en_us.json"),
                                Path.of("src/main/resources/assets/gramarye/lang/zh_tw.json")),
                        jsonResources),
                () -> assertEquals(expectedEn, Files.readString(en, StandardCharsets.UTF_8)),
                () -> assertEquals(expectedZh, Files.readString(zh, StandardCharsets.UTF_8)),
                () -> assertEquals(
                        Set.of(
                                "commands.gramarye.starter.slot_occupied",
                                "key.gramarye.cast",
                                "key.categories.gramarye"),
                        JsonParser.parseString(Files.readString(en, StandardCharsets.UTF_8))
                                .getAsJsonObject()
                                .keySet()),
                () -> assertEquals(
                        Set.of(
                                "commands.gramarye.starter.slot_occupied",
                                "key.gramarye.cast",
                                "key.categories.gramarye"),
                        JsonParser.parseString(Files.readString(zh, StandardCharsets.UTF_8))
                                .getAsJsonObject()
                                .keySet()));
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
                        "finalizedBy(cleanupP9S5ClientRuntimePlayerData)")));
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
