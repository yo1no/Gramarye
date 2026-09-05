package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.Gramarye;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.junit.jupiter.api.Test;

/** Direct API, retention, and persistence checks for the P6-S2 mana boundary. */
final class ManaBoundaryTest {
    private static final String P6_S1_BASE =
            "aa38e369154f0bd55405f6a306a3a74b28178e56";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path MANA_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana");
    private static final Path EFFECT_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/effect");
    private static final Path TEST_JAVA = PROJECT_ROOT.resolve("src/test/java");
    private static final Path MANA_TEST = TEST_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana");
    private static final Path EFFECT_TEST = TEST_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/effect");
    private static final Path PLAYER_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player");
    private static final Pattern TEST_METHOD = Pattern.compile(
            "(?m)^\\s*@Test\\s*\\R\\s*void\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final List<String> S1_PRODUCTION_FILE_NAMES = List.of(
            "DamageEffectCommitPort.java",
            "EffectCommitPlan.java",
            "EffectExecutionEngine.java",
            "EffectExecutionGuard.java",
            "EffectExecutionResult.java",
            "EffectRequest.java",
            "EffectResolution.java",
            "EffectStep.java",
            "EffectStepOutcome.java",
            "EffectTrace.java",
            "P6EffectBounds.java",
            "P6ExecutionInvariantException.java");
    private static final List<String> S1_TEST_FILE_NAMES = List.of(
            "DamageEffectCommitPortTest.java",
            "DamageEffectRequestTest.java",
            "DamageEffectResolverTest.java",
            "EffectCommitPlanTest.java",
            "EffectEngineTestDoubles.java",
            "EffectExecutionEngineFailureTest.java",
            "EffectExecutionEngineSuccessTest.java",
            "EffectExecutionGuardTest.java",
            "EffectExecutionResultTest.java",
            "EffectSemanticBoundaryTest.java",
            "EffectStepOutcomeTest.java",
            "EffectTestFixtures.java",
            "EffectTraceTest.java",
            "P6EffectVocabularyTest.java");
    private static final List<String> S2_PRODUCT_FILE_NAMES = List.of(
            "ManaAccountAccess.java",
            "ManaAttachmentDefinitionBridge.java",
            "ManaAttachmentSerializer.java",
            "ManaAttachments.java",
            "ManaAvailability.java",
            "ManaDecodeFailure.java",
            "ManaLifecycle.java",
            "ManaMutationBudget.java",
            "ManaOperationKind.java",
            "ManaReason.java",
            "ManaReceipt.java",
            "ManaRejectReason.java",
            "ManaState.java",
            "ManaStateCodec.java",
            "ManaTransactionResult.java",
            "ManaTransactionService.java",
            "P6ManaBounds.java",
            "PlayerManaAccountAccess.java");
    private static final List<Class<?>> PRODUCT_TYPES = List.of(
            ManaAccountAccess.class,
            ManaAttachmentDefinitionBridge.class,
            ManaAttachmentSerializer.class,
            ManaAttachments.class,
            ManaAvailability.class,
            ManaDecodeFailure.class,
            ManaLifecycle.class,
            ManaMutationBudget.class,
            ManaOperationKind.class,
            ManaReason.class,
            ManaReceipt.class,
            ManaReceiptIdentity.class,
            ManaRefundState.class,
            ManaRejectReason.class,
            ManaState.class,
            ManaStateCodec.class,
            ManaTransactionResult.class,
            ManaTransactionService.class,
            P6ManaBounds.class,
            PlayerManaAccountAccess.class);

    @Test
    void registrationAndPersistentSchemaAreExact() throws Exception {
        var playerRegistration = code(PLAYER_MAIN.resolve("PlayerSkillAttachments.java"));
        var manaDefinition = code(MANA_MAIN.resolve("ManaAttachments.java"));
        var bridge = code(MANA_MAIN.resolve("ManaAttachmentDefinitionBridge.java"));
        var attachmentRegistryOwners = javaSources(MAIN_JAVA).stream()
                .filter(path -> code(path).contains(
                        "NeoForgeRegistries.Keys.ATTACHMENT_TYPES"))
                .map(path -> MAIN_JAVA.relativize(path).toString().replace('\\', '/'))
                .collect(Collectors.toSet());
        var bridgeConsumers = javaSources(MAIN_JAVA).stream()
                .filter(path -> code(path).contains("ManaAttachmentDefinitionBridge"))
                .map(path -> MAIN_JAVA.relativize(path).toString().replace('\\', '/'))
                .collect(Collectors.toSet());
        var idField = ManaAttachments.class.getDeclaredField("PLAYER_MANA_ID");
        assertTrue(idField.trySetAccessible());
        var id = (ResourceLocation) idField.get(null);
        var minimum = ManaStateCodec.encode(ManaState.available(0L));
        var maximum = ManaStateCodec.encode(
                ManaState.available(P6ManaBounds.MAX_MANA_VALUE));
        var fields = Set.of(
                ManaStateCodec.SCHEMA_VERSION_FIELD,
                ManaStateCodec.BALANCE_FIELD);
        var allMain = javaSources(MAIN_JAVA).stream()
                .map(ManaBoundaryTest::code)
                .collect(Collectors.joining("\n"));
        var manaGameTests = code(MANA_MAIN.resolve("ManaLifecycleGameTests.java"));
        var totalGameTests = occurrences(allMain, "@GameTest(");
        var manaGameTestCount = occurrences(manaGameTests, "@GameTest(");
        var baselineGameTests = totalGameTests - manaGameTestCount - com.yo1no.gramarye.P7GameTestInventory.s4Count();

        assertAll(
                () -> assertEquals(
                        ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_mana"),
                        id),
                () -> assertEquals("gramarye:player_mana", id.toString()),
                () -> assertSame(id, ManaAttachmentDefinitionBridge.attachmentId()),
                () -> assertSame(
                        ManaAttachments.type(),
                        ManaAttachmentDefinitionBridge.attachmentType()),
                () -> assertEquals(
                        Set.of("com/yo1no/gramarye/magic/definition/player/"
                                + "PlayerSkillAttachments.java"),
                        attachmentRegistryOwners),
                () -> assertEquals(
                        Set.of(
                                "com/yo1no/gramarye/magic/definition/player/"
                                        + "PlayerSkillAttachments.java",
                                "com/yo1no/gramarye/magic/runtime/mana/"
                                        + "ManaAttachmentDefinitionBridge.java"),
                        bridgeConsumers),
                () -> assertEquals(
                        3, occurrences(playerRegistration, "ATTACHMENT_TYPES.register(")),
                () -> assertEquals(2, occurrences(playerRegistration, "DeferredHolder<")),
                () -> assertTrue(playerRegistration.contains(
                        "ManaAttachmentDefinitionBridge.attachmentId().getPath()")),
                () -> assertTrue(playerRegistration.contains(
                        "ManaAttachmentDefinitionBridge::attachmentType")),
                () -> assertEquals(0, occurrences(manaDefinition, ".register(")),
                () -> assertFalse(manaDefinition.contains("RegisterEvent")),
                () -> assertFalse(manaDefinition.contains("EventBusSubscriber")),
                () -> assertFalse(manaDefinition.contains("SubscribeEvent")),
                () -> assertFalse(manaDefinition.contains("NeoForgeRegistries")),
                () -> assertFalse(manaDefinition.contains("DeferredRegister")),
                () -> assertEquals(1, occurrences(manaDefinition, ".serialize(")),
                () -> assertEquals(1, occurrences(manaDefinition, ".copyOnDeath()")),
                () -> assertEquals(1, occurrences(manaDefinition, ".copyHandler(")),
                () -> assertTrue(manaDefinition.contains(
                        "AttachmentType.<ManaState>builder(ManaState::freshDefault)")),
                () -> assertFalse(manaDefinition.contains(".sync(")),
                () -> assertFalse(bridge.contains(".register(")),
                () -> assertFalse(bridge.contains(".getData(")),
                () -> assertFalse(bridge.contains(".setData(")),
                () -> assertFalse(bridge.contains("ServerPlayer")),
                () -> assertFalse(bridge.contains("ManaState")),
                () -> assertEquals(0, ManaStateCodec.CURRENT_SCHEMA_VERSION),
                () -> assertEquals(1_000_000_000L, P6ManaBounds.MAX_MANA_VALUE),
                () -> assertEquals(fields, minimum.getAllKeys()),
                () -> assertEquals(fields, maximum.getAllKeys()),
                () -> assertTrue(minimum.get(ManaStateCodec.SCHEMA_VERSION_FIELD)
                        instanceof IntTag),
                () -> assertTrue(minimum.get(ManaStateCodec.BALANCE_FIELD)
                        instanceof LongTag),
                () -> assertEquals(0, minimum.getInt(ManaStateCodec.SCHEMA_VERSION_FIELD)),
                () -> assertEquals(0L, minimum.getLong(ManaStateCodec.BALANCE_FIELD)),
                () -> assertEquals(
                        P6ManaBounds.MAX_MANA_VALUE,
                        maximum.getLong(ManaStateCodec.BALANCE_FIELD)),
                () -> assertEquals(12, baselineGameTests),
                () -> assertEquals(7, manaGameTestCount),
                () -> assertEquals(com.yo1no.gramarye.P7GameTestInventory.totalCount(), totalGameTests));
    }

    @Test
    void attachmentAccessHasOneOwnerAndNoSecondTruth() throws Exception {
        var attachmentFields = PRODUCT_TYPES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> AttachmentType.class.isAssignableFrom(field.getType()))
                .toList();
        var attachmentField = attachmentFields.getFirst();
        var generic = (ParameterizedType) attachmentField.getGenericType();
        var accessOwners = javaSources(MANA_MAIN).stream()
                .filter(path -> code(path).contains(".getData(")
                        || code(path).contains(".setData("))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        var playerDefinitionSource = javaSources(PLAYER_MAIN).stream()
                .map(ManaBoundaryTest::code)
                .collect(Collectors.joining("\n"));
        var playerStateSource = javaSources(PLAYER_MAIN).stream()
                .filter(path -> !path.getFileName().toString()
                        .equals("PlayerSkillAttachments.java"))
                .map(ManaBoundaryTest::code)
                .collect(Collectors.joining("\n"));
        var manaDefinition = code(MANA_MAIN.resolve("ManaAttachments.java"));
        var playerRegistration = code(PLAYER_MAIN.resolve("PlayerSkillAttachments.java"));
        var playerManaRegistrationOwners = javaSources(PLAYER_MAIN).stream()
                .filter(path -> {
                    var source = code(path);
                    return source.contains("ManaAttachmentDefinitionBridge")
                            || source.contains("PLAYER_MANA");
                })
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(1, attachmentFields.size()),
                () -> assertEquals(ManaAttachments.class, attachmentField.getDeclaringClass()),
                () -> assertEquals("PLAYER_MANA", attachmentField.getName()),
                () -> assertEquals(ManaState.class, generic.getActualTypeArguments()[0]),
                () -> assertEquals(Set.of("ManaAttachments.java"), accessOwners),
                () -> assertEquals(1, occurrences(manaDefinition, ".getData(")),
                () -> assertEquals(1, occurrences(manaDefinition, ".setData(")),
                () -> assertEquals(
                        Set.of("PlayerSkillAttachments.java"),
                        playerManaRegistrationOwners),
                () -> assertFalse(playerRegistration.contains(".getData(")),
                () -> assertFalse(playerRegistration.contains(".setData(")),
                () -> assertFalse(playerDefinitionSource.contains("ManaState")),
                () -> assertFalse(playerDefinitionSource.contains("ManaAttachmentSerializer")),
                () -> assertFalse(playerDefinitionSource.toLowerCase(java.util.Locale.ROOT)
                        .contains("balance")),
                () -> assertFalse(playerStateSource.toLowerCase(java.util.Locale.ROOT)
                        .contains("mana")));
    }

    @Test
    void productApiIsPackagePrivateAndAccountHandleIsNotPublic() throws Exception {
        var productSource = s2ProductSource();
        var publicTopLevel = Pattern.compile(
                "(?m)^public\\s+(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                        + "(?:class|interface|record|enum)\\b");
        var protectedMember = Pattern.compile("(?m)^\\s*protected\\s+");
        var accountField = PlayerManaAccountAccess.class.getDeclaredField("player");
        var publicProductTypes = PRODUCT_TYPES.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers()))
                .collect(Collectors.toSet());
        var bridge = ManaAttachmentDefinitionBridge.class;
        var bridgeConstructors = bridge.getDeclaredConstructors();
        var bridgeMethods = bridge.getDeclaredMethods();
        var attachmentId = bridge.getDeclaredMethod("attachmentId");
        var attachmentType = bridge.getDeclaredMethod("attachmentType");

        assertAll(
                () -> assertEquals(1L, publicTopLevel.matcher(productSource).results().count()),
                () -> assertFalse(protectedMember.matcher(productSource).find()),
                () -> assertEquals(Set.of(bridge), publicProductTypes),
                () -> assertTrue(Modifier.isPublic(bridge.getModifiers())),
                () -> assertTrue(Modifier.isFinal(bridge.getModifiers())),
                () -> assertEquals(0, bridge.getDeclaredFields().length),
                () -> assertEquals(1, bridgeConstructors.length),
                () -> assertTrue(Modifier.isPrivate(bridgeConstructors[0].getModifiers())),
                () -> assertEquals(2, bridgeMethods.length),
                () -> assertEquals(
                        Set.of("attachmentId", "attachmentType"),
                        Arrays.stream(bridgeMethods)
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(Arrays.stream(bridgeMethods).allMatch(method ->
                        Modifier.isPublic(method.getModifiers())
                                && Modifier.isStatic(method.getModifiers()))),
                () -> assertEquals(ResourceLocation.class, attachmentId.getReturnType()),
                () -> assertEquals(AttachmentType.class, attachmentType.getReturnType()),
                () -> assertEquals(
                        "net.neoforged.neoforge.attachment.AttachmentType<?>",
                        attachmentType.getGenericReturnType().getTypeName()),
                () -> assertFalse(Modifier.isPublic(ManaAccountAccess.class.getModifiers())),
                () -> assertFalse(
                        Modifier.isPublic(PlayerManaAccountAccess.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(PlayerManaAccountAccess.class.getModifiers())),
                () -> assertTrue(Modifier.isPrivate(accountField.getModifiers())
                        && Modifier.isFinal(accountField.getModifiers())),
                () -> assertEquals(ServerPlayer.class, accountField.getType()),
                () -> assertTrue(Modifier.isPublic(
                        ManaLifecycleGameTests.class.getModifiers())),
                () -> assertTrue(ManaLifecycleGameTests.class
                        .isAnnotationPresent(GameTestHolder.class)));
    }

    @Test
    void receiptRetainsOnlyScalarIdentityAndIsNotPersistent() {
        var components = Arrays.stream(ManaReceiptIdentity.class.getRecordComponents())
                .collect(Collectors.toMap(
                        component -> component.getName(),
                        component -> component.getType()));
        var identityFields = Arrays.asList(ManaReceiptIdentity.class.getDeclaredFields());
        var receiptFields = Arrays.asList(ManaReceipt.class.getDeclaredFields());
        var retainedFields = java.util.stream.Stream.concat(
                        identityFields.stream(), receiptFields.stream())
                .toList();
        var forbiddenLiveTypes = List.of(
                ServerPlayer.class,
                Entity.class,
                Level.class,
                MinecraftServer.class,
                IAttachmentHolder.class,
                AttachmentType.class,
                Thread.class,
                Throwable.class);
        var persistentTypes = List.of(
                ManaReceipt.class,
                ManaReceiptIdentity.class,
                ManaTransactionResult.class,
                ManaMutationBudget.class);
        var persistenceSource = code(MANA_MAIN.resolve("ManaAttachments.java"))
                + code(MANA_MAIN.resolve("ManaAttachmentSerializer.java"))
                + code(MANA_MAIN.resolve("ManaLifecycle.java"));

        assertAll(
                () -> assertEquals(Map.<String, Class<?>>of(
                        "operation", ManaOperationKind.class,
                        "reason", ManaReason.class,
                        "accountId", UUID.class,
                        "amount", long.class,
                        "beforeBalance", long.class,
                        "afterBalance", long.class), components),
                () -> assertTrue(identityFields.stream().allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers())
                                && !Modifier.isStatic(field.getModifiers()))),
                () -> assertEquals(Set.of("identity", "refundState"), receiptFields.stream()
                        .map(field -> field.getName()).collect(Collectors.toSet())),
                () -> assertTrue(receiptFields.stream()
                        .filter(field -> field.getName().equals("identity"))
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertTrue(retainedFields.stream().noneMatch(field ->
                        field.getType() == Object.class
                                || forbiddenLiveTypes.stream().anyMatch(forbidden ->
                                        forbidden.isAssignableFrom(field.getType())))),
                () -> assertTrue(retainedFields.stream()
                        .noneMatch(field -> Modifier.isStatic(field.getModifiers())
                                || field.getGenericType().getTypeName()
                                        .contains("java.util.function"))),
                () -> assertTrue(persistentTypes.stream().noneMatch(type ->
                        IAttachmentSerializer.class.isAssignableFrom(type)
                                || java.io.Serializable.class.isAssignableFrom(type))),
                () -> assertTrue(persistentTypes.stream()
                        .noneMatch(type -> persistenceSource.contains(type.getSimpleName()))));
    }

    @Test
    void noAsyncRandomMapJournalReservationRetryOrCrossTickStateExists()
            throws Exception {
        var source = s2ProductSource();
        var forbiddenFragments = List.of(
                "java.lang.Thread",
                "new Thread(",
                "ThreadLocal",
                "Executor",
                "CompletableFuture",
                "Future<",
                ".parallel(",
                ".parallelStream(",
                "java.util.Random",
                "RandomGenerator",
                "ThreadLocalRandom");
        var forbiddenState = Pattern.compile(
                "\\b(?:worker|journal|reservation|escrow|retry|cross[-_]?tick)\\b",
                Pattern.CASE_INSENSITIVE);
        var staticMaps = PRODUCT_TYPES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Map.class.isAssignableFrom(field.getType()))
                .toList();

        assertAll(
                () -> assertTrue(forbiddenFragments.stream().noneMatch(source::contains)),
                () -> assertFalse(forbiddenState.matcher(source).find()),
                () -> assertTrue(staticMaps.isEmpty()),
                () -> assertFalse(source.contains("Map<UUID")),
                () -> assertFalse(source.contains("player_skills")),
                () -> assertFalse(source.contains("catch (Throwable")));
    }

    @Test
    void threadGuardPrecedesAllAccountAndReceiptAccess() {
        var service = code(MANA_MAIN.resolve("ManaTransactionService.java"));
        var debit = section(
                service, "ManaTransactionResult debit(", "ManaTransactionResult credit(");
        var credit = section(
                service, "ManaTransactionResult credit(", "ManaTransactionResult refund(");
        var refund = section(
                service,
                "ManaTransactionResult refund(",
                "private static ManaTransactionResult accepted(");

        assertAll(
                () -> assertEquals(1, occurrences(debit, "Math.subtractExact(")),
                () -> assertEquals(1, occurrences(credit, "Math.addExact(")),
                () -> assertEquals(1, occurrences(refund, "Math.addExact(")));
        assertGuardPrecedes(debit,
                "account.accountId()", "account.availability()", "account.balance()",
                "account.writeBalance(", "accepted(");
        assertGuardPrecedes(credit,
                "account.accountId()", "account.availability()", "account.balance()",
                "account.writeBalance(", "accepted(");
        assertGuardPrecedes(refund,
                "account.accountId()", "debitReceipt.operation()", "debitReceipt.reason()",
                "debitReceipt.accountId()", "debitReceipt.refundState()",
                "account.availability()", "account.balance()",
                "debitReceipt.afterBalance()", "debitReceipt.amount()",
                "debitReceipt.beforeBalance()", "account.writeBalance(",
                "debitReceipt.markRefunded()", "accepted(");
        assertTrue(refund.indexOf("debitReceipt.operation()")
                        < refund.indexOf("account.accountId()"),
                "refund must snapshot receipt fields before reading account identity");
    }

    @Test
    void p5AndP6S1SourcesRemainAtBaselineBytes() throws Exception {
        var command = new ArrayList<>(List.of(
                "git", "diff", "--name-only", P6_S1_BASE, "--",
                "src/main/java/com/yo1no/gramarye/Gramarye.java",
                ":(glob)src/main/java/com/yo1no/gramarye/P5*.java",
                "src/main/java/com/yo1no/gramarye/SkillRuntimeService.java",
                ":(glob)src/test/java/com/yo1no/gramarye/P5*.java"));
        var process = new ProcessBuilder(command)
                .directory(PROJECT_ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor();
        var exactS4P5Changes = output.lines()
                .filter(line -> !line.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        var relocatedProduction = S1_PRODUCTION_FILE_NAMES.stream()
                .map(MANA_MAIN::resolve)
                .toList();
        var relocatedTests = S1_TEST_FILE_NAMES.stream()
                .map(MANA_TEST::resolve)
                .toList();
        var productionCopies = javaSources(MAIN_JAVA).stream()
                .filter(path -> S1_PRODUCTION_FILE_NAMES.contains(
                        path.getFileName().toString()))
                .collect(Collectors.toUnmodifiableSet());
        var testCopies = javaSources(TEST_JAVA).stream()
                .filter(path -> S1_TEST_FILE_NAMES.contains(
                        path.getFileName().toString()))
                .collect(Collectors.toUnmodifiableSet());
        var relocatedTestCoordinates = relocatedTests.stream()
                .flatMap(path -> testCoordinates(
                        path.getFileName().toString(), readSource(path)).stream())
                .collect(Collectors.toUnmodifiableSet());
        var baselineTestCoordinates = S1_TEST_FILE_NAMES.stream()
                .flatMap(fileName -> testCoordinates(
                        fileName, baselineS1TestSource(fileName)).stream())
                .collect(Collectors.toUnmodifiableSet());
        var expectedRelocatedTestCoordinates = java.util.stream.Stream.concat(
                        baselineTestCoordinates.stream(),
                        java.util.stream.Stream.of(
                                "P6EffectVocabularyTest.java#"
                                        + "effectRequestCardinalityIsStructurallyExactlyOne",
                                "DamageEffectRequestTest.java#"
                                        + "damageTargetCardinalityIsStructurallyExactlyOne"))
                .collect(Collectors.toUnmodifiableSet());
        assertAll(
                () -> assertEquals(0, exit, () -> "git continuity check failed: " + output),
                () -> assertEquals(
                        Set.of(
                                "src/main/java/com/yo1no/gramarye/Gramarye.java",
                                "src/main/java/com/yo1no/gramarye/P5RuntimeVocabulary.java",
                                "src/main/java/com/yo1no/gramarye/SkillRuntimeService.java",
                                "src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java",
                                "src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java"),
                        exactS4P5Changes,
                        () -> "unexpected P5 source drift: " + output),
                () -> assertEquals(12, relocatedProduction.size()),
                () -> assertTrue(relocatedProduction.stream().allMatch(Files::isRegularFile)),
                () -> assertEquals(Set.copyOf(relocatedProduction), productionCopies),
                () -> assertTrue(S1_PRODUCTION_FILE_NAMES.stream()
                        .map(EFFECT_MAIN::resolve)
                        .noneMatch(Files::exists)),
                () -> assertTrue(!Files.exists(EFFECT_MAIN)
                        || javaSources(EFFECT_MAIN).isEmpty()),
                () -> assertEquals(14, relocatedTests.size()),
                () -> assertTrue(relocatedTests.stream().allMatch(Files::isRegularFile)),
                () -> assertEquals(Set.copyOf(relocatedTests), testCopies),
                () -> assertTrue(S1_TEST_FILE_NAMES.stream()
                        .map(EFFECT_TEST::resolve)
                        .noneMatch(Files::exists)),
                () -> assertTrue(!Files.exists(EFFECT_TEST)
                        || javaSources(EFFECT_TEST).isEmpty()),
                () -> assertTrue(relocatedProduction.stream().allMatch(
                        ManaBoundaryTest::usesManaPackage)),
                () -> assertTrue(relocatedTests.stream().allMatch(
                        ManaBoundaryTest::usesManaPackage)),
                () -> assertEquals(91, baselineTestCoordinates.size()),
                () -> assertEquals(93, expectedRelocatedTestCoordinates.size()),
                () -> assertEquals(93, relocatedTestCoordinates.size()),
                () -> assertEquals(
                        expectedRelocatedTestCoordinates, relocatedTestCoordinates));
    }

    private static void assertGuardPrecedes(String section, String... accesses) {
        var guard = section.indexOf("if (!account.isLogicThread())");
        assertTrue(guard >= 0, "missing logic-thread precondition");
        for (var access : accesses) {
            assertTrue(section.indexOf(access) > guard,
                    () -> "access escaped the thread precondition: " + access);
        }
    }

    private static String productSource() throws IOException {
        return javaSources(MANA_MAIN).stream()
                .filter(path -> !path.getFileName().toString()
                        .equals("ManaLifecycleGameTests.java"))
                .map(ManaBoundaryTest::code)
                .collect(Collectors.joining("\n"));
    }

    private static String s2ProductSource() {
        return S2_PRODUCT_FILE_NAMES.stream()
                .map(MANA_MAIN::resolve)
                .map(ManaBoundaryTest::code)
                .collect(Collectors.joining("\n"));
    }

    private static boolean usesManaPackage(Path path) {
        try {
            return Files.readString(path).startsWith(
                    "package com.yo1no.gramarye.magic.runtime.mana;");
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static Set<String> testCoordinates(String fileName, String source) {
        return TEST_METHOD.matcher(source).results()
                .map(result -> fileName + "#" + result.group(1))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String baselineS1TestSource(String fileName) {
        var path = "src/test/java/com/yo1no/gramarye/magic/runtime/effect/" + fileName;
        try {
            var process = new ProcessBuilder(
                    "git", "show", P6_S1_BASE + ":" + path)
                    .directory(PROJECT_ROOT.toFile())
                    .redirectErrorStream(true)
                    .start();
            var output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var exit = process.waitFor();
            if (exit != 0) {
                throw new AssertionError("unable to read baseline S1 test: " + path
                        + "\n" + output);
            }
            return output;
        } catch (IOException exception) {
            throw new AssertionError("unable to read baseline S1 test: " + path, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while reading baseline S1 test: " + path,
                    exception);
        }
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static String section(String source, String start, String end) {
        var first = source.indexOf(start);
        var last = source.indexOf(end, first + start.length());
        assertTrue(first >= 0 && last > first,
                () -> "source section unavailable: " + start + " -> " + end);
        return source.substring(first, last);
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static String code(Path path) {
        try {
            return Files.readString(path)
                    .replaceAll("(?s)/\\*.*?\\*/", " ")
                    .replaceAll("(?m)//.*$", " ")
                    .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                    .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
