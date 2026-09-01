package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private static final Path PLAYER_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player");
    private static final List<Class<?>> PRODUCT_TYPES = List.of(
            ManaAccountAccess.class,
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
        var registration = code(MANA_MAIN.resolve("ManaAttachments.java"));
        var idField = ManaAttachments.class.getDeclaredField("PLAYER_MANA_ID");
        assertTrue(idField.trySetAccessible());
        var id = (ResourceLocation) idField.get(null);
        var minimum = ManaStateCodec.encode(ManaState.available(0L));
        var maximum = ManaStateCodec.encode(
                ManaState.available(P6ManaBounds.MAX_MANA_VALUE));
        var fields = Set.of(
                ManaStateCodec.SCHEMA_VERSION_FIELD,
                ManaStateCodec.BALANCE_FIELD);

        assertAll(
                () -> assertEquals(
                        ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_mana"),
                        id),
                () -> assertEquals("gramarye:player_mana", id.toString()),
                () -> assertEquals(1, occurrences(registration, "event.register(")),
                () -> assertEquals(1, occurrences(
                        registration, "NeoForgeRegistries.Keys.ATTACHMENT_TYPES")),
                () -> assertEquals(1, occurrences(registration, ".serialize(")),
                () -> assertEquals(1, occurrences(registration, ".copyOnDeath()")),
                () -> assertEquals(1, occurrences(registration, ".copyHandler(")),
                () -> assertTrue(registration.contains(
                        "AttachmentType.<ManaState>builder(ManaState::freshDefault)")),
                () -> assertFalse(registration.contains(".sync(")),
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
                        maximum.getLong(ManaStateCodec.BALANCE_FIELD)));
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
        var registration = code(MANA_MAIN.resolve("ManaAttachments.java"));

        assertAll(
                () -> assertEquals(1, attachmentFields.size()),
                () -> assertEquals(ManaAttachments.class, attachmentField.getDeclaringClass()),
                () -> assertEquals("PLAYER_MANA", attachmentField.getName()),
                () -> assertEquals(ManaState.class, generic.getActualTypeArguments()[0]),
                () -> assertEquals(Set.of("ManaAttachments.java"), accessOwners),
                () -> assertEquals(1, occurrences(registration, ".getData(")),
                () -> assertEquals(1, occurrences(registration, ".setData(")),
                () -> assertFalse(
                        playerDefinitionSource.toLowerCase(java.util.Locale.ROOT)
                                .contains("mana"),
                        "player_skills must not acquire a mana field or second truth"));
    }

    @Test
    void productApiIsPackagePrivateAndAccountHandleIsNotPublic() throws Exception {
        var productSource = productSource();
        var publicTopLevel = Pattern.compile(
                "(?m)^public\\s+(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                        + "(?:class|interface|record|enum)\\b");
        var protectedMember = Pattern.compile("(?m)^\\s*protected\\s+");
        var accountField = PlayerManaAccountAccess.class.getDeclaredField("player");

        assertAll(
                () -> assertFalse(publicTopLevel.matcher(productSource).find()),
                () -> assertFalse(protectedMember.matcher(productSource).find()),
                () -> assertTrue(PRODUCT_TYPES.stream().noneMatch(type ->
                        Modifier.isPublic(type.getModifiers())
                                || Modifier.isProtected(type.getModifiers()))),
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
        var source = productSource();
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
                ":(glob)src/test/java/com/yo1no/gramarye/P5*.java",
                "src/main/java/com/yo1no/gramarye/magic/runtime/effect",
                "src/test/java/com/yo1no/gramarye/magic/runtime/effect"));
        var process = new ProcessBuilder(command)
                .directory(PROJECT_ROOT.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor();
        assertAll(
                () -> assertEquals(0, exit, () -> "git continuity check failed: " + output),
                () -> assertTrue(output.isBlank(),
                        () -> "P5/P6-S1 source drift: " + output));
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
