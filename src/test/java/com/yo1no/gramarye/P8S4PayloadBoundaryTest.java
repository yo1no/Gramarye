package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.junit.jupiter.api.Test;

/** Exact public-surface, registrar-family, and common-side retention gate for P8-S4. */
final class P8S4PayloadBoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path ROOT_PACKAGE = PROJECT_ROOT.resolve(
            "src/main/java/com/yo1no/gramarye");
    private static final Path REGISTRAR_SOURCE = ROOT_PACKAGE.resolve(
            "magic/network/P7PayloadRegistrar.java");
    private static final Path BRIDGE_SOURCE = ROOT_PACKAGE.resolve(
            "P8PayloadRegistrationBridge.java");

    @Test
    void registrationBridgeHasTheExactOnlyPublicSurface() {
        var bridgeModifiers = P8PayloadRegistrationBridge.class.getModifiers();
        var publicMethods = Arrays.stream(P8PayloadRegistrationBridge.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName()
                        + "(" + Arrays.stream(method.getParameterTypes())
                                .map(Class::getName)
                                .collect(Collectors.joining(",")) + ")"
                        + "->" + method.getReturnType().getName()
                        + ":" + Modifier.isStatic(method.getModifiers()))
                .collect(Collectors.toSet());
        var publicFields = Arrays.stream(P8PayloadRegistrationBridge.class.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers())
                        || Modifier.isProtected(field.getModifiers()))
                .map(field -> field.getName() + ":" + field.getType().getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(Modifier.isPublic(bridgeModifiers)),
                () -> assertTrue(Modifier.isFinal(bridgeModifiers)),
                () -> assertEquals(1,
                        P8PayloadRegistrationBridge.class.getDeclaredConstructors().length),
                () -> assertTrue(Modifier.isPrivate(P8PayloadRegistrationBridge.class
                        .getDeclaredConstructors()[0]
                        .getModifiers())),
                () -> assertEquals(
                        Set.of("register(" + PayloadRegistrar.class.getName()
                                + ")->void:true"),
                        publicMethods),
                () -> assertEquals(Set.of(), publicFields),
                () -> assertFalse(Modifier.isPublic(ProfileCatalogPayload.class.getModifiers())),
                () -> assertFalse(
                        Modifier.isPublic(PresentationEventPayload.class.getModifiers())),
                () -> assertFalse(
                        Modifier.isPublic(P8ProfileCatalogEntry.class.getModifiers())),
                () -> assertFalse(
                        Modifier.isPublic(P8ProfileCatalogSnapshot.class.getModifiers())),
                () -> assertFalse(
                        Modifier.isPublic(P8ClientPayloadDispatchPort.class.getModifiers())),
                () -> assertFalse(
                        Modifier.isPublic(P8ClientDispatchTask.class.getModifiers())));
    }

    @Test
    void uniqueP7RegistrarRetainsFourP7RolesThenAddsOneP8BridgeCall() {
        var registrar = read(REGISTRAR_SOURCE);
        var cast = registrar.indexOf("CastIntentPayload.TYPE");
        var acknowledgement = registrar.indexOf("IntentAckPayload.TYPE");
        var mana = registrar.indexOf("PlayerManaSyncPayload.TYPE");
        var cooldown = registrar.indexOf("SkillCooldownSyncPayload.TYPE");
        var bridge = registrar.indexOf("P8PayloadRegistrationBridge.register(");

        assertAll(
                () -> assertEquals(1, occurrences(registrar, "@EventBusSubscriber")),
                () -> assertEquals(1, occurrences(registrar, ".playToServer(")),
                () -> assertEquals(3, occurrences(registrar, ".playToClient(")),
                () -> assertEquals(1, occurrences(
                        registrar, "event.registrar(P7NetworkBounds.PROTOCOL_VERSION)")),
                () -> assertEquals(1, occurrences(
                        registrar, "registrar.versioned(\"gramarye-p8-v0\")")),
                () -> assertEquals(1, occurrences(
                        registrar, "P8PayloadRegistrationBridge.register(")),
                () -> assertTrue(cast >= 0
                        && cast < acknowledgement
                        && acknowledgement < mana
                        && mana < cooldown
                        && cooldown < bridge),
                () -> assertEquals(0, occurrences(registrar, "profile_catalog")),
                () -> assertEquals(0, occurrences(registrar, "presentation_event")));
    }

    @Test
    void bridgeOwnsExactlyTwoClientboundRegistrationsWithoutASecondSubscriber() {
        var bridge = read(BRIDGE_SOURCE);
        assertAll(
                () -> assertEquals(2, occurrences(bridge, ".playToClient(")),
                () -> assertEquals(0, occurrences(bridge, ".playToServer(")),
                () -> assertEquals(0, occurrences(bridge, "@EventBusSubscriber")),
                () -> assertEquals(0, occurrences(bridge, "RegisterPayloadHandlersEvent")),
                () -> assertEquals(1, occurrences(bridge, "ProfileCatalogPayload.TYPE")),
                () -> assertEquals(1, occurrences(
                        bridge, "PresentationEventPayload.TYPE")),
                () -> assertEquals(0, occurrences(bridge, "net.minecraft.client")));
    }

    @Test
    void commonDispatchBoundaryCannotRetainPayloadContextOrClientObjects() {
        var task = read(ROOT_PACKAGE.resolve("P8ClientDispatchTask.java"));
        var port = read(ROOT_PACKAGE.resolve("P8ClientPayloadDispatchPort.java"));
        var factory = read(ROOT_PACKAGE.resolve("P8ClientPayloadDispatchFactory.java"));
        var handlers = read(ROOT_PACKAGE.resolve("P8ClientPayloadHandlers.java"));
        var payloadCodec = read(ROOT_PACKAGE.resolve("P8PayloadCodecSupport.java"));

        assertAll(
                () -> assertEquals(0, occurrences(task, "IPayloadContext")),
                () -> assertEquals(0, occurrences(port, "IPayloadContext")),
                () -> assertEquals(0, occurrences(factory, "IPayloadContext")),
                () -> assertEquals(0, occurrences(task, "net.minecraft.client")),
                () -> assertEquals(0, occurrences(port, "net.minecraft.client")),
                () -> assertEquals(0, occurrences(factory, "net.minecraft.client")),
                () -> assertEquals(0, occurrences(payloadCodec, "net.minecraft.client")),
                () -> assertEquals(1, occurrences(handlers, "context.enqueueWork(task)")),
                () -> assertEquals(1, occurrences(
                        handlers, "disconnect.gramarye.invalid_presentation_payload")),
                () -> assertTrue(handlers.contains("if (!transferred)")),
                () -> assertTrue(handlers.contains("task.releaseAfterFailedEnqueue()")));
    }

    private static int occurrences(String source, String needle) {
        var count = 0;
        var offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("unable to read source: " + path, failure);
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }
}
