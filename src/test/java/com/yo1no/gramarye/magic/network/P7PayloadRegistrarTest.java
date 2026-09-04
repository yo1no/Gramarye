package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.junit.jupiter.api.Test;

final class P7PayloadRegistrarTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path NETWORK_MAIN = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye/magic/network");
    private static final Path REGISTRAR_SOURCE = NETWORK_MAIN.resolve(
            "P7PayloadRegistrar.java");

    @Test
    void payloadTypesExposeTheExactFourUniqueGramaryeIds() {
        var ids = List.of(
                CastIntentPayload.TYPE.id(),
                IntentAckPayload.TYPE.id(),
                PlayerManaSyncPayload.TYPE.id(),
                SkillCooldownSyncPayload.TYPE.id());

        assertEquals(List.of(
                ResourceLocation.fromNamespaceAndPath("gramarye", "cast_intent"),
                ResourceLocation.fromNamespaceAndPath("gramarye", "intent_ack"),
                ResourceLocation.fromNamespaceAndPath("gramarye", "player_mana_sync"),
                ResourceLocation.fromNamespaceAndPath("gramarye", "skill_cooldown_sync")), ids);
        assertEquals(4, Set.copyOf(ids).size());
    }

    @Test
    void payloadTypeAndCodecFieldsAreExactAndUsable() {
        assertNotNull(CastIntentPayload.STREAM_CODEC);
        assertNotNull(IntentAckPayload.STREAM_CODEC);
        assertNotNull(PlayerManaSyncPayload.STREAM_CODEC);
        assertNotNull(SkillCooldownSyncPayload.STREAM_CODEC);

        var cast = new CastIntentPayload(CastIntentValidation.validate(
                        1, 0, 0, 0, null, null, null, null)
                .intent()
                .orElseThrow());
        var acknowledgement = new IntentAckPayload(new IntentAcknowledgement(
                1,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null));
        var mana = new PlayerManaSyncPayload(new PlayerManaSnapshot(
                1, PlayerManaSnapshot.Availability.UNAVAILABLE, 0));
        var cooldown = new SkillCooldownSyncPayload(
                new SkillCooldownSnapshot(1, List.of()));

        assertSame(CastIntentPayload.TYPE, cast.type());
        assertSame(IntentAckPayload.TYPE, acknowledgement.type());
        assertSame(PlayerManaSyncPayload.TYPE, mana.type());
        assertSame(SkillCooldownSyncPayload.TYPE, cooldown.type());
        assertTrue(List.of(cast, acknowledgement, mana, cooldown).stream()
                .allMatch(CustomPacketPayload.class::isInstance));
    }

    @Test
    void registrarIsTheSinglePackagePrivateStaticModBusOwner() throws Exception {
        var owners = javaSources(NETWORK_MAIN).stream()
                .filter(path -> read(path).contains("RegisterPayloadHandlersEvent"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
        var method = P7PayloadRegistrar.class.getDeclaredMethod(
                "registerPayloads", RegisterPayloadHandlersEvent.class);
        var subscriber = P7PayloadRegistrar.class.getAnnotation(EventBusSubscriber.class);

        assertEquals(Set.of("P7PayloadRegistrar.java"), owners);
        assertFalse(Modifier.isPublic(P7PayloadRegistrar.class.getModifiers()));
        assertFalse(Modifier.isProtected(P7PayloadRegistrar.class.getModifiers()));
        assertTrue(Modifier.isFinal(P7PayloadRegistrar.class.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
        assertNotNull(method.getAnnotation(SubscribeEvent.class));
        var registrationMethods = List.of(P7PayloadRegistrar.class.getDeclaredMethods())
                .stream()
                .filter(candidate -> candidate.isAnnotationPresent(SubscribeEvent.class))
                .filter(candidate -> List.of(candidate.getParameterTypes())
                        .equals(List.of(RegisterPayloadHandlersEvent.class)))
                .toList();
        assertEquals(List.of(method), registrationMethods);
        assertNotNull(subscriber);
        assertEquals("gramarye", subscriber.modid());
    }

    @Test
    void registrarBindsOneServerAndThreeClientPlayPayloadsToNetwork() {
        var source = read(REGISTRAR_SOURCE);
        var normalized = source.replaceAll("\\s+", " ");

        assertEquals("gramarye-p7-v0", P7NetworkBounds.PROTOCOL_VERSION);
        assertEquals(1, occurrences(source, ".playToServer("));
        assertEquals(3, occurrences(source, ".playToClient("));
        assertEquals(1, occurrences(
                source, ".executesOn(HandlerThread.NETWORK)"));
        assertEquals(1, occurrences(
                source, "event.registrar(P7NetworkBounds.PROTOCOL_VERSION)"));
        assertEquals(4, occurrences(source, ".STREAM_CODEC,"));
        assertTrue(normalized.contains(
                "var registrar = event.registrar(P7NetworkBounds.PROTOCOL_VERSION) "
                        + ".executesOn(HandlerThread.NETWORK);"));
        assertEquals(4, occurrences(source, "registrar.playTo"));
        assertTrue(normalized.contains(
                "registrar.playToServer( CastIntentPayload.TYPE, "
                        + "CastIntentPayload.STREAM_CODEC, (payload, context) -> "
                        + "P7CastIntentNetworkHandler.handle( payload, context, PRODUCTION));"));
        assertTrue(normalized.contains(
                "registrar.playToClient( IntentAckPayload.TYPE, "
                        + "IntentAckPayload.STREAM_CODEC, (payload, context) -> "
                        + "P7ClientPayloadHandlers.handleIntentAcknowledgement( "
                        + "payload, context, PRODUCTION));"));
        assertTrue(normalized.contains(
                "registrar.playToClient( PlayerManaSyncPayload.TYPE, "
                        + "PlayerManaSyncPayload.STREAM_CODEC, (payload, context) -> "
                        + "P7ClientPayloadHandlers.handlePlayerManaSnapshot( "
                        + "payload, context, PRODUCTION));"));
        assertTrue(normalized.contains(
                "registrar.playToClient( SkillCooldownSyncPayload.TYPE, "
                        + "SkillCooldownSyncPayload.STREAM_CODEC, (payload, context) -> "
                        + "P7ClientPayloadHandlers.handleSkillCooldownSnapshot( "
                        + "payload, context, PRODUCTION));"));
        assertFalse(source.contains("configuration"));
        assertFalse(source.contains("Bidirectional"));
        assertFalse(source.contains(".optional("));
    }

    @Test
    void registrarAndHandlersHaveNoClientOnlyOrGenericRegistrationSurface()
            throws IOException {
        var source = javaSources(NETWORK_MAIN).stream()
                .map(P7PayloadRegistrarTest::read)
                .collect(Collectors.joining("\n"));

        assertFalse(source.contains("net.minecraft.client"));
        assertFalse(source.contains("Object payload"));
        assertFalse(source.contains("ServiceLoader"));
        assertFalse(source.contains("playBidirectional"));
        assertFalse(source.contains("configurationTo"));
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
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
