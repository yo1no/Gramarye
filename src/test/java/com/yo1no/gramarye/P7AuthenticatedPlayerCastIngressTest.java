package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentServiceTestSupport;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P7AuthenticatedPlayerCastIngressTest {
    private static final Path ROOT_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/Gramarye.java");
    private static final Path INGRESS_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/P7AuthenticatedPlayerCastIngress.java");

    @TempDir
    Path temporary;

    @Test
    void namedIngressHasTheExactFixedOverrideAndRetentionSurface() throws Exception {
        var type = P7AuthenticatedPlayerCastIngress.class;
        var constructors = type.getDeclaredConstructors();
        var fields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toList();
        var authorizeAndAdmit = type.getDeclaredMethod(
                "authorizeAndAdmit",
                MinecraftServer.class,
                ServerPlayer.class,
                int.class,
                P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class);
        var mapAdmission = type.getDeclaredMethod("mapAdmission", RuntimeAdmissionResult.class);
        var visibleMethods = Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers()))
                .toList();
        var fieldsByName = fields.stream().collect(Collectors.toMap(
                field -> field.getName(), field -> field.getType()));

        assertAll(
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertFalse(Modifier.isPublic(type.getModifiers())),
                () -> assertFalse(Modifier.isProtected(type.getModifiers())),
                () -> assertEquals(
                        List.of(P7ServerAuthorizationBoundary.RootIngressPort.class),
                        List.of(type.getInterfaces())),
                () -> assertEquals(1, constructors.length),
                () -> assertFalse(Modifier.isPublic(constructors[0].getModifiers())),
                () -> assertFalse(Modifier.isProtected(constructors[0].getModifiers())),
                () -> assertFalse(Modifier.isPrivate(constructors[0].getModifiers())),
                () -> assertEquals(
                        List.of(
                                SkillRuntimeService.class,
                                PlayerSkillAttachmentService.class,
                                SkillDefinitionStoreService.class),
                        List.of(constructors[0].getParameterTypes())),
                () -> assertEquals(List.of(authorizeAndAdmit), visibleMethods),
                () -> assertTrue(Modifier.isPublic(authorizeAndAdmit.getModifiers())),
                () -> assertFalse(Modifier.isStatic(authorizeAndAdmit.getModifiers())),
                () -> assertFalse(authorizeAndAdmit.isBridge()),
                () -> assertFalse(authorizeAndAdmit.isSynthetic()),
                () -> assertEquals(
                        P7ServerAuthorizationBoundary.AdmissionDisposition.class,
                        authorizeAndAdmit.getReturnType()),
                () -> assertEquals(0, authorizeAndAdmit.getExceptionTypes().length),
                () -> assertFalse(Modifier.isPublic(mapAdmission.getModifiers())),
                () -> assertFalse(Modifier.isProtected(mapAdmission.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(mapAdmission.getModifiers())),
                () -> assertTrue(Modifier.isStatic(mapAdmission.getModifiers())),
                () -> assertEquals(
                        P7ServerAuthorizationBoundary.AdmissionDisposition.class,
                        mapAdmission.getReturnType()),
                () -> assertEquals(
                        Map.of(
                                "runtimeService", SkillRuntimeService.class,
                                "attachmentService", PlayerSkillAttachmentService.class,
                                "storeService", SkillDefinitionStoreService.class),
                        fieldsByName),
                () -> assertTrue(fields.stream().allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers())
                                && !Modifier.isStatic(field.getModifiers()))),
                () -> assertTrue(fields.stream().noneMatch(field -> Set.of(
                                MinecraftServer.class,
                                ServerPlayer.class,
                                SkillReference.class,
                                SkillDocument.class,
                                RuntimeAdmissionResult.class,
                                P7ServerAuthorizationBoundary.AdvisoryTargetCheck.class,
                                Entity.class,
                                Level.class,
                                Throwable.class)
                        .contains(field.getType()))),
                () -> assertEquals(0, Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                && Modifier.isStatic(method.getModifiers()))
                        .count()));
    }

    @Test
    void constructorRetainsTheSameThreeProductionServiceIdentities() throws Exception {
        var gameBus = BusBuilder.builder().build();
        var attachmentService = PlayerSkillAttachmentServiceTestSupport.createService();
        var storeService = SkillDefinitionStoreService.registerOn(gameBus, attachmentService);
        var runtimeService = SkillRuntimeService.create(
                gameBus, storeService, SkillSubmissionPolicyProvider.defaults());
        var ingress = new P7AuthenticatedPlayerCastIngress(
                runtimeService, attachmentService, storeService);

        assertSame(runtimeService, retained(ingress, "runtimeService"));
        assertSame(attachmentService, retained(ingress, "attachmentService"));
        assertSame(storeService, retained(ingress, "storeService"));
    }

    @Test
    void rootBuildsAndInstallsOneIngressFromTheExistingServiceGraph() throws Exception {
        var source = Files.readString(ROOT_SOURCE);
        var ingressSource = Files.readString(INGRESS_SOURCE);
        var runtimeCreation = source.indexOf("skillRuntimeService = SkillRuntimeService.create(");
        var ingressCreation = source.indexOf(
                "var p7AuthenticatedPlayerCastIngress = new P7AuthenticatedPlayerCastIngress(");
        var installation = source.indexOf("P7ServerAuthorizationBoundary.install(");

        assertAll(
                () -> assertEquals(
                        1,
                        occurrences(
                                source,
                                "playerSkillAttachmentService = "
                                        + "PlayerSkillAttachmentService.registerOn(")),
                () -> assertEquals(
                        1,
                        occurrences(
                                source,
                                "skillDefinitionStoreService = "
                                        + "SkillDefinitionStoreService.registerOn(")),
                () -> assertEquals(
                        1,
                        occurrences(source, "skillRuntimeService = SkillRuntimeService.create(")),
                () -> assertEquals(
                        1, occurrences(source, "new P7AuthenticatedPlayerCastIngress(")),
                () -> assertEquals(
                        1, occurrences(source, "P7ServerAuthorizationBoundary.install(")),
                () -> assertTrue(runtimeCreation >= 0),
                () -> assertTrue(ingressCreation > runtimeCreation),
                () -> assertTrue(installation > ingressCreation),
                () -> assertTrue(source.contains("new P7AuthenticatedPlayerCastIngress(\n"
                        + "                skillRuntimeService,\n"
                        + "                playerSkillAttachmentService,\n"
                        + "                skillDefinitionStoreService);")),
                () -> assertTrue(source.contains("P7ServerAuthorizationBoundary.install(\n"
                        + "                P6RuntimeExecutionCapability.forRuntimeAdapter(),\n"
                        + "                p7AuthenticatedPlayerCastIngress);")),
                () -> assertOrdered(
                        ingressSource,
                        "Objects.requireNonNull(server, \"server\")",
                        "Objects.requireNonNull(actor, \"actor\")",
                        "Objects.requireNonNull(targetCheck, \"targetCheck\")",
                        "if (slot < 0 || slot > 63)",
                        "if (!server.isSameThread())",
                        "actor.getServer() != server",
                        "var actorId = actor.getUUID();",
                        "server.getPlayerList().getPlayer(actorId) != actor",
                        "attachmentService.equippedAt(actor, slot)",
                        "if (exactReference.isEmpty())",
                        "attachmentService.ownerId(actor)",
                        "storeService.ownerOf(server, reference.skillId())",
                        "if (!actorOwner.equals(skillOwner.orElseThrow()))",
                        "storeService.find(server, reference)",
                        "if (definition.isEmpty())",
                        "targetCheck.validate(server, actor)",
                        "runtimeService.admitAuthenticatedPlayerCast(server, actor, reference)"),
                () -> assertEquals(
                        1, occurrences(ingressSource, "attachmentService.equippedAt(actor, slot)")),
                () -> assertEquals(
                        1, occurrences(ingressSource, "attachmentService.ownerId(actor)")),
                () -> assertEquals(
                        1, occurrences(ingressSource, "storeService.ownerOf(server, reference.skillId())")),
                () -> assertEquals(
                        1, occurrences(ingressSource, "storeService.find(server, reference)")),
                () -> assertEquals(
                        1, occurrences(ingressSource, "targetCheck.validate(server, actor)")),
                () -> assertEquals(
                        1,
                        occurrences(
                                ingressSource,
                                "runtimeService.admitAuthenticatedPlayerCast(server, actor, reference)")),
                () -> assertFalse(ingressSource.contains("latestReference")),
                () -> assertFalse(ingressSource.contains("Available<?>")),
                () -> assertTrue(ingressSource.contains("Available<SkillOwnerId>")),
                () -> assertFalse(ingressSource.contains("catch (")),
                () -> assertFalse(ingressSource.contains("P6RuntimeExecution")));
    }

    @Test
    void actualIngressSourceExecutesTheClosedAuthorizationOrderAndEveryPreP5Branch()
            throws Exception {
        var sourceRoot = Files.createDirectories(temporary.resolve("source"));
        var outputRoot = Files.createDirectories(temporary.resolve("classes"));
        var skillIdStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/api/id/SkillId.java", """
                package com.yo1no.gramarye.magic.api.id;

                public record SkillId(String value) {
                }
                """);
        var ownerIdStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/api/id/SkillOwnerId.java", """
                package com.yo1no.gramarye.magic.api.id;

                public record SkillOwnerId(String value) {
                }
                """);
        var referenceStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/document/SkillReference.java", """
                package com.yo1no.gramarye.magic.definition.document;

                import com.yo1no.gramarye.magic.api.id.SkillId;

                public record SkillReference(SkillId skillId, int revision) {
                }
                """);
        var documentStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/document/SkillDocument.java", """
                package com.yo1no.gramarye.magic.definition.document;

                public final class SkillDocument {
                }
                """);
        var playerListStub = write(sourceRoot,
                "net/minecraft/server/players/PlayerList.java", """
                package net.minecraft.server.players;

                import java.util.List;
                import java.util.UUID;
                import net.minecraft.server.level.ServerPlayer;

                public final class PlayerList {
                    private final List<String> calls;
                    private ServerPlayer currentPlayer;

                    public PlayerList(List<String> calls) {
                        this.calls = calls;
                    }

                    public ServerPlayer getPlayer(UUID ignored) {
                        calls.add("currentPlayer");
                        return currentPlayer;
                    }

                    public void setCurrentPlayer(ServerPlayer player) {
                        currentPlayer = player;
                    }
                }
                """);
        var serverStub = write(sourceRoot,
                "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                import java.util.List;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.server.players.PlayerList;

                public final class MinecraftServer {
                    private final List<String> calls;
                    private final PlayerList playerList;
                    private boolean sameThread = true;

                    public MinecraftServer(List<String> calls) {
                        this.calls = calls;
                        playerList = new PlayerList(calls);
                    }

                    public boolean isSameThread() {
                        calls.add("sameThread");
                        return sameThread;
                    }

                    public PlayerList getPlayerList() {
                        calls.add("playerList");
                        return playerList;
                    }

                    public void setSameThread(boolean value) {
                        sameThread = value;
                    }

                    public void setCurrentPlayer(ServerPlayer player) {
                        playerList.setCurrentPlayer(player);
                    }
                }
                """);
        var playerStub = write(sourceRoot,
                "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;

                import java.util.List;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;

                public final class ServerPlayer {
                    private final UUID playerId;
                    private final List<String> calls;
                    private MinecraftServer server;

                    public ServerPlayer(
                            MinecraftServer server,
                            UUID playerId,
                            List<String> calls) {
                        this.server = server;
                        this.playerId = playerId;
                        this.calls = calls;
                    }

                    public MinecraftServer getServer() {
                        calls.add("actorServer");
                        return server;
                    }

                    public UUID getUUID() {
                        calls.add("actorUuid");
                        return playerId;
                    }

                    public void setServer(MinecraftServer value) {
                        server = value;
                    }
                }
                """);
        var attachmentStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
                """
                package com.yo1no.gramarye.magic.definition.player;

                import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import java.util.List;
                import java.util.Optional;
                import net.minecraft.server.level.ServerPlayer;

                public final class PlayerSkillAttachmentService {
                    private final List<String> calls;
                    private Result<Optional<SkillReference>> equippedResult;
                    private Result<SkillOwnerId> ownerResult;

                    public PlayerSkillAttachmentService(
                            List<String> calls,
                            Result<Optional<SkillReference>> equippedResult,
                            Result<SkillOwnerId> ownerResult) {
                        this.calls = calls;
                        this.equippedResult = equippedResult;
                        this.ownerResult = ownerResult;
                    }

                    public Result<Optional<SkillReference>> equippedAt(
                            ServerPlayer ignored,
                            int slot) {
                        calls.add("equipped:" + slot);
                        return equippedResult;
                    }

                    public Result<SkillOwnerId> ownerId(ServerPlayer ignored) {
                        calls.add("actorOwner");
                        return ownerResult;
                    }

                    public void setEquippedResult(
                            Result<Optional<SkillReference>> value) {
                        equippedResult = value;
                    }

                    public void setOwnerResult(Result<SkillOwnerId> value) {
                        ownerResult = value;
                    }

                    public sealed interface Result<T> permits Available, Unavailable {
                    }

                    public record Available<T>(T value) implements Result<T> {
                    }

                    public record Unavailable<T>() implements Result<T> {
                    }
                }
                """);
        var subsystemResultStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/store/SkillSubsystemResult.java", """
                package com.yo1no.gramarye.magic.definition.store;

                public sealed interface SkillSubsystemResult<T>
                        permits SkillSubsystemResult.Available,
                                SkillSubsystemResult.Unavailable {
                    record Available<T>(T value) implements SkillSubsystemResult<T> {
                    }

                    record Unavailable<T>() implements SkillSubsystemResult<T> {
                    }
                }
                """);
        var storeStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java",
                """
                package com.yo1no.gramarye.magic.definition.store;

                import com.yo1no.gramarye.magic.api.id.SkillId;
                import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
                import com.yo1no.gramarye.magic.definition.document.SkillDocument;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import java.util.List;
                import java.util.Optional;
                import net.minecraft.server.MinecraftServer;

                public final class SkillDefinitionStoreService {
                    private final List<String> calls;
                    private SkillSubsystemResult<Optional<SkillOwnerId>> ownerResult;
                    private SkillSubsystemResult<Optional<SkillDocument>> definitionResult;

                    public SkillDefinitionStoreService(
                            List<String> calls,
                            SkillSubsystemResult<Optional<SkillOwnerId>> ownerResult,
                            SkillSubsystemResult<Optional<SkillDocument>> definitionResult) {
                        this.calls = calls;
                        this.ownerResult = ownerResult;
                        this.definitionResult = definitionResult;
                    }

                    public SkillSubsystemResult<Optional<SkillOwnerId>> ownerOf(
                            MinecraftServer ignoredServer,
                            SkillId ignoredSkillId) {
                        calls.add("skillOwner");
                        return ownerResult;
                    }

                    public SkillSubsystemResult<Optional<SkillDocument>> find(
                            MinecraftServer ignoredServer,
                            SkillReference ignoredReference) {
                        calls.add("definition");
                        return definitionResult;
                    }

                    public void setOwnerResult(
                            SkillSubsystemResult<Optional<SkillOwnerId>> value) {
                        ownerResult = value;
                    }

                    public void setDefinitionResult(
                            SkillSubsystemResult<Optional<SkillDocument>> value) {
                        definitionResult = value;
                    }
                }
                """);
        var boundaryStub = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary.java", """
                package com.yo1no.gramarye.magic.network;

                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;

                public final class P7ServerAuthorizationBoundary {
                    private P7ServerAuthorizationBoundary() {
                    }

                    public interface RootIngressPort {
                        AdmissionDisposition authorizeAndAdmit(
                                MinecraftServer server,
                                ServerPlayer actor,
                                int slot,
                                AdvisoryTargetCheck targetCheck);
                    }

                    public interface AdvisoryTargetCheck {
                        TargetDisposition validate(
                                MinecraftServer server,
                                ServerPlayer actor);
                    }

                    public enum AdmissionDisposition {
                        ACCEPTED,
                        UNKNOWN_SKILL,
                        UNAUTHORIZED_INTENT,
                        INVALID_TARGET,
                        TARGET_UNAVAILABLE,
                        P5_ADMISSION_REJECTED,
                        P5_UNAVAILABLE,
                        INTERNAL_SERVER_FAULT
                    }

                    public enum TargetDisposition {
                        VALID,
                        INVALID_TARGET,
                        TARGET_UNAVAILABLE
                    }
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/P7IngressBehaviorHarness.java", """
                package com.yo1no.gramarye;

                import com.yo1no.gramarye.magic.api.id.SkillId;
                import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
                import com.yo1no.gramarye.magic.definition.document.SkillDocument;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
                import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
                import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
                import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
                import java.util.ArrayList;
                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;

                public final class P7IngressBehaviorHarness {
                    private P7IngressBehaviorHarness() {
                    }

                    public static String run() {
                        verifyNulls();
                        verifySlotBoundsAndEdges();
                        verifyServerAndActorOrder();
                        verifyAttachmentAndOwnerBranches();
                        verifyExactRevisionBranches();
                        verifyTargetBranchesAndP5Identity();
                        return "PASS";
                    }

                    private static void verifyNulls() {
                        var fixture = new Fixture();
                        expectNull("runtimeService", () -> new P7AuthenticatedPlayerCastIngress(
                                null, fixture.attachments, fixture.store));
                        expectNull("attachmentService", () -> new P7AuthenticatedPlayerCastIngress(
                                fixture.runtime, null, fixture.store));
                        expectNull("storeService", () -> new P7AuthenticatedPlayerCastIngress(
                                fixture.runtime, fixture.attachments, null));
                        expectNull("server", () -> fixture.ingress.authorizeAndAdmit(
                                null, fixture.actor, 0, fixture.target(validTarget())));
                        fixture.expectCalls();

                        var actorNullFixture = new Fixture();
                        expectNull("actor", () -> actorNullFixture.ingress.authorizeAndAdmit(
                                actorNullFixture.server,
                                null,
                                0,
                                actorNullFixture.target(validTarget())));
                        actorNullFixture.expectCalls();

                        var targetNullFixture = new Fixture();
                        expectNull("targetCheck", () -> targetNullFixture.ingress.authorizeAndAdmit(
                                targetNullFixture.server,
                                targetNullFixture.actor,
                                0,
                                null));
                        targetNullFixture.expectCalls();
                    }

                    private static void verifySlotBoundsAndEdges() {
                        for (var invalidSlot : new int[] {-1, 64}) {
                            var fixture = new Fixture();
                            var failure = expect(
                                    IllegalArgumentException.class,
                                    () -> fixture.ingress.authorizeAndAdmit(
                                            fixture.server,
                                            fixture.actor,
                                            invalidSlot,
                                            fixture.target(validTarget())));
                            check("slot is outside 0..63".equals(failure.getMessage()),
                                    "slot failure message");
                            fixture.expectCalls();
                            fixture.expectTerminalCounts(0, 0);
                        }
                        for (var validSlot : new int[] {0, 63}) {
                            var fixture = new Fixture();
                            var result = fixture.ingress.authorizeAndAdmit(
                                    fixture.server,
                                    fixture.actor,
                                    validSlot,
                                    fixture.target(validTarget()));
                            check(result == admission("ACCEPTED"), "valid edge admission");
                            fixture.expectCalls(
                                    "sameThread",
                                    "actorServer",
                                    "actorUuid",
                                    "playerList",
                                    "currentPlayer",
                                    "equipped:" + validSlot,
                                    "actorOwner",
                                    "skillOwner",
                                    "definition",
                                    "target",
                                    "p5");
                            fixture.expectTerminalCounts(1, 1);
                            check(fixture.runtime.reference == fixture.reference,
                                    "P5 did not receive exact reference identity");
                        }
                    }

                    private static void verifyServerAndActorOrder() {
                        var wrongThread = new Fixture();
                        wrongThread.server.setSameThread(false);
                        check(wrongThread.ingress.authorizeAndAdmit(
                                        wrongThread.server,
                                        wrongThread.actor,
                                        0,
                                        wrongThread.target(validTarget()))
                                == admission("INTERNAL_SERVER_FAULT"), "wrong thread");
                        wrongThread.expectCalls("sameThread");
                        wrongThread.expectTerminalCounts(0, 0);

                        var wrongServer = new Fixture();
                        wrongServer.actor.setServer(new MinecraftServer(wrongServer.calls));
                        check(wrongServer.ingress.authorizeAndAdmit(
                                        wrongServer.server,
                                        wrongServer.actor,
                                        0,
                                        wrongServer.target(validTarget()))
                                == admission("UNAUTHORIZED_INTENT"), "wrong supplied server");
                        wrongServer.expectCalls("sameThread", "actorServer");
                        wrongServer.expectTerminalCounts(0, 0);

                        var staleActor = new Fixture();
                        staleActor.server.setCurrentPlayer(new ServerPlayer(
                                staleActor.server,
                                UUID.fromString("00000000-0000-0000-0000-000000000702"),
                                staleActor.calls));
                        check(staleActor.ingress.authorizeAndAdmit(
                                        staleActor.server,
                                        staleActor.actor,
                                        0,
                                        staleActor.target(validTarget()))
                                == admission("UNAUTHORIZED_INTENT"), "no-longer-current actor");
                        staleActor.expectCalls(
                                "sameThread",
                                "actorServer",
                                "actorUuid",
                                "playerList",
                                "currentPlayer");
                        staleActor.expectTerminalCounts(0, 0);
                    }

                    private static void verifyAttachmentAndOwnerBranches() {
                        var unavailableAttachment = new Fixture();
                        unavailableAttachment.attachments.setEquippedResult(
                                new PlayerSkillAttachmentService.Unavailable<>());
                        check(unavailableAttachment.invoke() == admission("P5_UNAVAILABLE"),
                                "attachment unavailable");
                        unavailableAttachment.expectCallsThrough("equipped:0");

                        var emptyAttachment = new Fixture();
                        emptyAttachment.attachments.setEquippedResult(
                                new PlayerSkillAttachmentService.Available<>(Optional.empty()));
                        check(emptyAttachment.invoke() == admission("UNKNOWN_SKILL"),
                                "attachment empty");
                        emptyAttachment.expectCallsThrough("equipped:0");

                        var unavailableActorOwner = new Fixture();
                        unavailableActorOwner.attachments.setOwnerResult(
                                new PlayerSkillAttachmentService.Unavailable<>());
                        check(unavailableActorOwner.invoke() == admission("P5_UNAVAILABLE"),
                                "actor owner unavailable");
                        unavailableActorOwner.expectCallsThrough("actorOwner");

                        var unavailableSkillOwner = new Fixture();
                        unavailableSkillOwner.store.setOwnerResult(
                                new SkillSubsystemResult.Unavailable<>());
                        check(unavailableSkillOwner.invoke() == admission("P5_UNAVAILABLE"),
                                "skill owner unavailable");
                        unavailableSkillOwner.expectCallsThrough("skillOwner");

                        var missingSkillOwner = new Fixture();
                        missingSkillOwner.store.setOwnerResult(
                                new SkillSubsystemResult.Available<>(Optional.empty()));
                        check(missingSkillOwner.invoke() == admission("UNKNOWN_SKILL"),
                                "skill owner missing");
                        missingSkillOwner.expectCallsThrough("skillOwner");

                        var mismatchedOwner = new Fixture();
                        mismatchedOwner.store.setOwnerResult(new SkillSubsystemResult.Available<>(
                                Optional.of(new SkillOwnerId("other-owner"))));
                        check(mismatchedOwner.invoke() == admission("UNAUTHORIZED_INTENT"),
                                "skill owner mismatch");
                        mismatchedOwner.expectCallsThrough("skillOwner");
                    }

                    private static void verifyExactRevisionBranches() {
                        var unavailable = new Fixture();
                        unavailable.store.setDefinitionResult(
                                new SkillSubsystemResult.Unavailable<>());
                        check(unavailable.invoke() == admission("P5_UNAVAILABLE"),
                                "exact revision unavailable");
                        unavailable.expectCallsThrough("definition");

                        var missing = new Fixture();
                        missing.store.setDefinitionResult(
                                new SkillSubsystemResult.Available<>(Optional.empty()));
                        check(missing.invoke() == admission("UNKNOWN_SKILL"),
                                "exact revision missing");
                        missing.expectCallsThrough("definition");
                    }

                    private static void verifyTargetBranchesAndP5Identity() {
                        var invalid = new Fixture();
                        check(invalid.invoke(target("INVALID_TARGET"))
                                == admission("INVALID_TARGET"), "invalid target");
                        invalid.expectCallsThrough("target");
                        invalid.expectTerminalCounts(1, 0);

                        var unavailable = new Fixture();
                        check(unavailable.invoke(target("TARGET_UNAVAILABLE"))
                                == admission("TARGET_UNAVAILABLE"), "target unavailable");
                        unavailable.expectCallsThrough("target");
                        unavailable.expectTerminalCounts(1, 0);

                        var valid = new Fixture();
                        check(valid.invoke(target("VALID")) == admission("ACCEPTED"),
                                "valid target");
                        valid.expectCallsThrough("p5");
                        valid.expectTerminalCounts(1, 1);
                        check(valid.runtime.server == valid.server, "P5 server identity");
                        check(valid.runtime.actor == valid.actor, "P5 actor identity");
                        check(valid.runtime.reference == valid.reference,
                                "P5 exact reference identity");

                        var runtimeFailure = new Fixture();
                        var runtimeException = new IllegalStateException("runtime failure");
                        runtimeFailure.runtime.failure = runtimeException;
                        var observedRuntime = expect(IllegalStateException.class,
                                () -> runtimeFailure.invoke(target("VALID")));
                        check(observedRuntime == runtimeException,
                                "RuntimeException exact identity");
                        runtimeFailure.expectCallsThrough("p5");
                        runtimeFailure.expectTerminalCounts(1, 1);

                        var errorFailure = new Fixture();
                        var error = new AssertionError("runtime error");
                        errorFailure.runtime.failure = error;
                        var observedError = expect(AssertionError.class,
                                () -> errorFailure.invoke(target("VALID")));
                        check(observedError == error, "Error exact identity");
                        errorFailure.expectCallsThrough("p5");
                        errorFailure.expectTerminalCounts(1, 1);
                    }

                    private static P7ServerAuthorizationBoundary.AdmissionDisposition admission(
                            String name) {
                        return P7ServerAuthorizationBoundary.AdmissionDisposition.valueOf(name);
                    }

                    private static P7ServerAuthorizationBoundary.TargetDisposition target(
                            String name) {
                        return P7ServerAuthorizationBoundary.TargetDisposition.valueOf(name);
                    }

                    private static P7ServerAuthorizationBoundary.TargetDisposition validTarget() {
                        return target("VALID");
                    }

                    private static void expectNull(String name, Runnable action) {
                        var failure = expect(NullPointerException.class, action);
                        check(name.equals(failure.getMessage()), "null order/message for " + name);
                    }

                    private static <T extends Throwable> T expect(
                            Class<T> type,
                            Runnable action) {
                        try {
                            action.run();
                        } catch (Throwable failure) {
                            check(type.isInstance(failure),
                                    "wrong failure type: " + failure.getClass().getName());
                            return type.cast(failure);
                        }
                        throw new AssertionError("expected " + type.getName());
                    }

                    private static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }

                    private static final class Fixture {
                        private final List<String> calls = new ArrayList<>();
                        private final SkillOwnerId owner = new SkillOwnerId("actor-owner");
                        private final SkillReference reference = new SkillReference(
                                new SkillId("exact-skill"), 47);
                        private final MinecraftServer server = new MinecraftServer(calls);
                        private final ServerPlayer actor = new ServerPlayer(
                                server,
                                UUID.fromString("00000000-0000-0000-0000-000000000701"),
                                calls);
                        private final PlayerSkillAttachmentService attachments =
                                new PlayerSkillAttachmentService(
                                        calls,
                                        new PlayerSkillAttachmentService.Available<>(
                                                Optional.of(reference)),
                                        new PlayerSkillAttachmentService.Available<>(owner));
                        private final SkillDefinitionStoreService store =
                                new SkillDefinitionStoreService(
                                        calls,
                                        new SkillSubsystemResult.Available<>(Optional.of(owner)),
                                        new SkillSubsystemResult.Available<>(
                                                Optional.of(new SkillDocument())));
                        private final SkillRuntimeService runtime =
                                new SkillRuntimeService(calls);
                        private final P7AuthenticatedPlayerCastIngress ingress =
                                new P7AuthenticatedPlayerCastIngress(
                                        runtime, attachments, store);
                        private int targetCalls;

                        private Fixture() {
                            server.setCurrentPlayer(actor);
                        }

                        private P7ServerAuthorizationBoundary.AdmissionDisposition invoke() {
                            return invoke(validTarget());
                        }

                        private P7ServerAuthorizationBoundary.AdmissionDisposition invoke(
                                P7ServerAuthorizationBoundary.TargetDisposition disposition) {
                            return ingress.authorizeAndAdmit(
                                    server, actor, 0, target(disposition));
                        }

                        private P7ServerAuthorizationBoundary.AdvisoryTargetCheck target(
                                P7ServerAuthorizationBoundary.TargetDisposition disposition) {
                            return (observedServer, observedActor) -> {
                                calls.add("target");
                                targetCalls++;
                                check(observedServer == server, "target server identity");
                                check(observedActor == actor, "target actor identity");
                                return disposition;
                            };
                        }

                        private void expectCalls(String... expected) {
                            check(calls.equals(List.of(expected)),
                                    "call order expected=" + List.of(expected)
                                            + " actual=" + calls);
                        }

                        private void expectCallsThrough(String terminal) {
                            var full = List.of(
                                    "sameThread",
                                    "actorServer",
                                    "actorUuid",
                                    "playerList",
                                    "currentPlayer",
                                    "equipped:0",
                                    "actorOwner",
                                    "skillOwner",
                                    "definition",
                                    "target",
                                    "p5");
                            var terminalIndex = full.indexOf(terminal);
                            check(terminalIndex >= 0, "unknown expected terminal " + terminal);
                            check(calls.equals(full.subList(0, terminalIndex + 1)),
                                    "call order through " + terminal + " actual=" + calls);
                            expectTerminalCounts(
                                    terminalIndex >= full.indexOf("target") ? 1 : 0,
                                    terminalIndex >= full.indexOf("p5") ? 1 : 0);
                        }

                        private void expectTerminalCounts(int targets, int p5) {
                            check(targetCalls == targets, "target call count");
                            check(runtime.calls == p5, "P5 call count");
                        }
                    }
                }

                final class SkillRuntimeService {
                    private final List<String> callsLog;
                    int calls;
                    MinecraftServer server;
                    ServerPlayer actor;
                    SkillReference reference;
                    Throwable failure;

                    SkillRuntimeService(List<String> callsLog) {
                        this.callsLog = callsLog;
                    }

                    RuntimeAdmissionResult admitAuthenticatedPlayerCast(
                            MinecraftServer observedServer,
                            ServerPlayer observedActor,
                            SkillReference observedReference) {
                        callsLog.add("p5");
                        calls++;
                        server = observedServer;
                        actor = observedActor;
                        reference = observedReference;
                        if (failure instanceof RuntimeException runtimeFailure) {
                            throw runtimeFailure;
                        }
                        if (failure instanceof Error errorFailure) {
                            throw errorFailure;
                        }
                        return new RuntimeAdmissionResult.AcceptedMemoryOnly();
                    }
                }

                sealed interface RuntimeAdmissionResult
                        permits RuntimeAdmissionResult.AcceptedMemoryOnly,
                                RuntimeAdmissionResult.PersistentScheduleUnsupported,
                                RuntimeAdmissionResult.DelayOutOfRange,
                                RuntimeAdmissionResult.DelayOverflow,
                                RuntimeAdmissionResult.DeadlineOutOfRange,
                                RuntimeAdmissionResult.DeadlineOverflow,
                                RuntimeAdmissionResult.DeadlineBeforeScheduledTick,
                                RuntimeAdmissionResult.InvalidRuntimeReference,
                                RuntimeAdmissionResult.SkillRevisionUnavailable,
                                RuntimeAdmissionResult.InvalidEvent,
                                RuntimeAdmissionResult.OwnerInstanceUnavailable,
                                RuntimeAdmissionResult.ActiveLineageCapacityExceeded,
                                RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded,
                                RuntimeAdmissionResult.RootAdmissionBudgetExceeded,
                                RuntimeAdmissionResult.CircuitBroken,
                                RuntimeAdmissionResult.SequenceExhausted,
                                RuntimeAdmissionResult.TickExhausted,
                                RuntimeAdmissionResult.ServerNotRunning,
                                RuntimeAdmissionResult.ServerStopping,
                                RuntimeAdmissionResult.KernelFaulted,
                                RuntimeAdmissionResult.WrongThread {
                    record AcceptedMemoryOnly() implements RuntimeAdmissionResult {
                    }

                    record PersistentScheduleUnsupported() implements RuntimeAdmissionResult {
                    }

                    record DelayOutOfRange() implements RuntimeAdmissionResult {
                    }

                    record DelayOverflow() implements RuntimeAdmissionResult {
                    }

                    record DeadlineOutOfRange() implements RuntimeAdmissionResult {
                    }

                    record DeadlineOverflow() implements RuntimeAdmissionResult {
                    }

                    record DeadlineBeforeScheduledTick() implements RuntimeAdmissionResult {
                    }

                    record InvalidRuntimeReference() implements RuntimeAdmissionResult {
                    }

                    record SkillRevisionUnavailable() implements RuntimeAdmissionResult {
                    }

                    record InvalidEvent() implements RuntimeAdmissionResult {
                    }

                    record OwnerInstanceUnavailable() implements RuntimeAdmissionResult {
                    }

                    record ActiveLineageCapacityExceeded() implements RuntimeAdmissionResult {
                    }

                    record ActiveBudgetAttributionCapacityExceeded()
                            implements RuntimeAdmissionResult {
                    }

                    record RootAdmissionBudgetExceeded() implements RuntimeAdmissionResult {
                    }

                    record CircuitBroken() implements RuntimeAdmissionResult {
                    }

                    record SequenceExhausted() implements RuntimeAdmissionResult {
                    }

                    record TickExhausted() implements RuntimeAdmissionResult {
                    }

                    record ServerNotRunning() implements RuntimeAdmissionResult {
                    }

                    record ServerStopping() implements RuntimeAdmissionResult {
                    }

                    record KernelFaulted() implements RuntimeAdmissionResult {
                    }

                    record WrongThread() implements RuntimeAdmissionResult {
                    }
                }
                """);

        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "Java compiler is unavailable");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (var files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(List.of(
                    INGRESS_SOURCE,
                    skillIdStub,
                    ownerIdStub,
                    referenceStub,
                    documentStub,
                    playerListStub,
                    serverStub,
                    playerStub,
                    attachmentStub,
                    subsystemResultStub,
                    storeStub,
                    boundaryStub,
                    harness));
            var options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-implicit:none",
                    "-Xlint:all,-auxiliaryclass",
                    "-Werror",
                    "-classpath", outputRoot.toString(),
                    "-sourcepath", sourceRoot.toString(),
                    "-d", outputRoot.toString());
            success = Boolean.TRUE.equals(compiler.getTask(
                    null, files, diagnostics, options, null, units).call());
        }
        assertTrue(success, () -> diagnostics.getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[] {outputRoot.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var behavioralHarness = Class.forName(
                    "com.yo1no.gramarye.P7IngressBehaviorHarness", true, loader);
            assertEquals("PASS", behavioralHarness.getMethod("run").invoke(null));
        }
    }

    private static Object retained(P7AuthenticatedPlayerCastIngress ingress, String fieldName)
            throws Exception {
        var field = P7AuthenticatedPlayerCastIngress.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(ingress);
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        var from = 0;
        while (true) {
            var found = source.indexOf(fragment, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + fragment.length();
        }
    }

    private static void assertOrdered(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var next = source.indexOf(fragment, previous + 1);
            assertTrue(next > previous, fragment);
            previous = next;
        }
    }

    private static Path write(Path root, String relativePath, String content)
            throws IOException {
        var target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("project root unavailable");
    }
}
