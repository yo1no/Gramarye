package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual excluded recorder source only. No auth, native gameplay, packet or qualification PASS claim. */
final class U0OrdinaryQualificationTraceTest {
    @TempDir Path temporary;

    @Test void fourThousandPostsRetainOnlyFirstChangedGatesAndActualSendMarker() throws Exception {
        try (var compiled = compile("posts")) {
            for (int index = 0; index < 4000; index++) {
                call(compiled.type, "enter", new Class<?>[]{String.class, long.class}, "INPUT_POST", -1L);
                note(compiled.type, "CLIENT_GATE", index == 3999 ? 503 : 507, "");
                if (index == 1999) { note(compiled.type, "N1_SEND_RETURN", 1, "SOLE_POST_SENDER"); }
                call(compiled.type, "leave", new Class<?>[]{String.class}, "INPUT_POST");
            }
            var result = output(compiled, "posts-result");
            assertEquals("OBSERVATIONS_ONLY_NOT_QUALIFIED", result.get("status").getAsString());
            var rows = result.getAsJsonArray("rows");
            assertEquals(4, rows.size(), "Only the one actual-send Post adds timing; empty Posts never fill the4096-row ledger");
            assertEquals("CLIENT_GATE", rows.get(0).getAsJsonObject().get("tag").getAsString());
            assertEquals("N1_SEND_RETURN", rows.get(1).getAsJsonObject().get("tag").getAsString());
            assertEquals("SPAN_NANOS", rows.get(2).getAsJsonObject().get("tag").getAsString());
            assertEquals("INPUT_POST", rows.get(2).getAsJsonObject().get("text").getAsString());
            assertTrue(rows.get(2).getAsJsonObject().get("a").getAsLong() >= 0);
            assertEquals(rows.get(1).getAsJsonObject().get("span"), rows.get(2).getAsJsonObject().get("span"));
            assertEquals("CLIENT_GATE", rows.get(3).getAsJsonObject().get("tag").getAsString());
            assertFalse(result.get("overflow").getAsBoolean());
        }
    }

    @Test void missingSolePostOrOverflowInvalidatesTheObservationArtifact() throws Exception {
        try (var compiled = compile("outside")) {
            note(compiled.type, "N1_SEND_RETURN", 1, "SOLE_POST_SENDER");
            assertEquals("OBSERVER_FAILED", output(compiled, "outside-result").get("status").getAsString());
        }
        try (var compiled = compile("metric-scopes")) {
            note(compiled.type, "NATIVE_TICKET_ADD", 1, "RETURN");
            note(compiled.type, "BOUNDED_SCAN_RETURN", 64, "");
            call(compiled.type, "enter", new Class<?>[]{String.class, long.class}, "ENTITY", -1L);
            note(compiled.type, "NATIVE_TICKET_ADD", 2, "RETURN");
            note(compiled.type, "BOUNDED_SCAN_RETURN", 64, "");
            call(compiled.type, "leave", new Class<?>[]{String.class}, "ENTITY");
            call(compiled.type, "enter", new Class<?>[]{String.class, long.class}, "DISPATCH", 20L);
            note(compiled.type, "NATIVE_TICKET_ADD", 3, "RETURN");
            note(compiled.type, "BOUNDED_SCAN_RETURN", 65, "");
            call(compiled.type, "leave", new Class<?>[]{String.class}, "DISPATCH");
            var result = output(compiled, "metric-scopes-result");
            var rows = result.getAsJsonArray("rows").asList().stream().map(value -> value.getAsJsonObject()).toList();
            assertEquals(2, rows.stream().filter(value -> value.get("tag").getAsString().equals("NATIVE_TICKET_ADD")).count());
            assertEquals(1, rows.stream().filter(value -> value.get("tag").getAsString().equals("BOUNDED_SCAN_RETURN")).count());
            assertEquals(2, rows.stream().filter(value -> value.get("tag").getAsString().equals("SPAN_NANOS")).count());
            assertEquals(List.of("ENTITY", "DISPATCH"), rows.stream()
                    .filter(value -> value.get("tag").getAsString().equals("SPAN_NANOS"))
                    .map(value -> value.get("text").getAsString()).toList());
            assertTrue(rows.stream().filter(value -> value.get("tag").getAsString().equals("SPAN_NANOS"))
                    .allMatch(value -> value.get("a").getAsLong() >= 0));
            assertEquals("OBSERVATIONS_ONLY_NOT_QUALIFIED", result.get("status").getAsString());
        }
        try (var compiled = compile("overflow")) {
            for (int index = 0; index < 4097; index++) { note(compiled.type, "BOUNDED_FIXTURE_ONLY", index, ""); }
            var result = output(compiled, "overflow-result");
            assertEquals("OBSERVER_FAILED", result.get("status").getAsString());
            assertTrue(result.get("overflow").getAsBoolean());
            assertEquals(4096, result.getAsJsonArray("rows").size());
        }
    }

    @Test void observerValidationFailureIsContainedAndTerminalFileIsImmutable() throws Exception {
        try (var compiled = compile("failure")) {
            assertDoesNotThrow(() -> note(compiled.type, "INVALID_FIXTURE_ONLY", 0, "x".repeat(65)));
            var directory = Files.createDirectory(temporary.resolve("failure-result"));
            call(compiled.type, "write", new Class<?>[]{Path.class}, directory);
            var file = directory.resolve("ordinary-scalar-trace.json");
            var before = Files.readAllBytes(file);
            assertEquals("OBSERVER_FAILED", JsonParser.parseString(Files.readString(file))
                    .getAsJsonObject().get("status").getAsString());
            call(compiled.type, "write", new Class<?>[]{Path.class}, directory);
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }

    @Test void isolatedConfigKeepsOnlyNamedObserversAndOriginalAuthenticationHooks() throws Exception {
        var root = root();
        var config = JsonParser.parseString(Files.readString(root.resolve(
                "src/p11OnlineHarness/resources/gramarye-u0-ordinary-qualification.mixins.json"))).getAsJsonObject();
        assertTrue(config.get("required").getAsBoolean());
        assertEquals("com.yo1no.gramarye.harnessmixin", config.get("package").getAsString());
        assertEquals(List.of("P11OnlineLoginMixin", "P11OnlineAuthenticatorMixin", "U0NormalSlotTraceMixin",
                "U0NormalReceiptTraceMixin", "U0NormalCarrierTraceMixin", "U0A05ScalarTraceMixin",
                "U0TrueDamagePostTraceMixin", "U0BoundedScanTraceMixin", "U0EntityTickTraceMixin",
                "U0DistanceTicketTraceMixin", "U0CurrentActorTraceMixin", "P11L1TerminalLogoutMixin"), config.getAsJsonArray("mixins").asList().stream()
                .map(value -> value.getAsString()).toList());
        assertEquals(List.of("U0NormalInputTraceMixin", "U0VanillaFeedbackTraceMixin"), config.getAsJsonArray("client").asList().stream()
                .map(value -> value.getAsString()).toList());
        var mixins = root.resolve("src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin");
        int shadowFields = 0;
        for (var entry : Map.of("U0NormalSlotTraceMixin", 8, "U0NormalReceiptTraceMixin", 6,
                "U0NormalCarrierTraceMixin", 1, "U0A05ScalarTraceMixin", 3,
                "U0TrueDamagePostTraceMixin", 1, "U0NormalInputTraceMixin", 2,
                "U0BoundedScanTraceMixin", 1, "U0EntityTickTraceMixin", 1,
                "U0DistanceTicketTraceMixin", 1, "U0VanillaFeedbackTraceMixin", 1).entrySet()) {
            var source = Files.readString(mixins.resolve(entry.getKey() + ".java"));
            int hooks = source.split("@WrapMethod|@WrapOperation|@Inject", -1).length - 1;
            assertEquals(entry.getValue(), hooks, entry.getKey());
            shadowFields += source.split("@Shadow", -1).length - 1;
            assertFalse(source.contains("@Unique") || source.contains("@SubscribeEvent"));
            assertFalse(source.contains("new A05Observer") || source.contains("new CastCurrentSpellPayload"));
        }
        assertEquals(11, shadowFields);
        var currentActor = Files.readString(mixins.resolve("U0CurrentActorTraceMixin.java"));
        assertEquals(1, currentActor.split("@WrapMethod|@WrapOperation|@Inject", -1).length - 1);
        assertFalse(currentActor.contains("@Shadow") || currentActor.contains("@Unique")
                || currentActor.contains("@SubscribeEvent") || currentActor.contains("currentV0Play("));
        assertTrue(currentActor.contains("currentV0Actor(Lcom/yo1no/gramarye/V0SlotRuntime;"));
        assertEquals(1, currentActor.split(java.util.regex.Pattern.quote(
                "original.call(v0, authority, witness, source, body, proof, actor)"), -1).length - 1);
        assertTrue(currentActor.indexOf("boolean actual = original.call")
                < currentActor.indexOf("try { U0NormalCarrierObservation.actorCurrent"));
        var slot = Files.readString(mixins.resolve("U0NormalSlotTraceMixin.java"));
        assertTrue(slot.contains("@Inject(method = \"<init>(Lcom/yo1no/gramarye/V0SlotStartup;Lcom/yo1no/gramarye/V0SlotBinding;)V\","
                + "\n            at = @At(\"RETURN\"), require = 1, expect = 1, allow = 1)"));
        assertEquals(1, slot.split(java.util.regex.Pattern.quote(
                "U0OrdinaryScenario.captureRuntime(this, startup, binding);"), -1).length - 1);
        var input = Files.readString(mixins.resolve("U0NormalInputTraceMixin.java"));
        assertTrue(input.contains("sendPayload(Lcom/yo1no/gramarye/magic/network/CastCurrentSpellPayload;)V"));
        assertFalse(input.contains("CastIntentPayload;)V") || input.contains("KeyMapping.click"));
        assertTrue(input.contains("P11ClientTransitions.blocksCast()"));
        assertTrue(input.contains("P11ClientTransitions.inputSuppressionGeneration()"));
        var feedback = Files.readString(mixins.resolve("U0VanillaFeedbackTraceMixin.java"));
        assertTrue(feedback.contains("setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"));
        assertFalse(feedback.contains("getString(") || feedback.contains("getArgs(") || feedback.contains("setReturnValue("));
        assertEquals(10, feedback.split("message.gramarye.cast.", -1).length - 1);

    }

    private Compiled compile(String leaf) throws Exception {
        var classes = Files.createDirectory(temporary.resolve(leaf));
        var resource = Gson.class.getResource("Gson.class");
        assertNotNull(resource, "the loaded Gson class resource must be available");
        Path gson;
        if (resource.getProtocol().equals("jar")) {
            gson = Path.of(((java.net.JarURLConnection) resource.openConnection()).getJarFileURL().toURI());
        } else {
            // SecureJar's ModuleReader.list() is unsupported. Read the actual resource filesystem,
            // whose union provider supports directory traversal and read channels.
            var packageRoot = Path.of(resource.toURI()).getParent();
            gson = Files.createDirectory(temporary.resolve(leaf + "-gson"));
            try (var paths = Files.walk(packageRoot)) {
                for (var entry : paths.filter(path -> Files.isRegularFile(path)
                        && path.toString().endsWith(".class")).toList()) {
                    var destination = gson.resolve("com/google/gson").resolve(packageRoot.relativize(entry).toString());
                    Files.createDirectories(destination.getParent());
                    try (var input = Files.newInputStream(entry)) { Files.copy(input, destination); }
                }
            }
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        int result = compiler.run(null, null, null, "--release", "21", "-proc:none", "-classpath", gson.toString(),
                "-d", classes.toString(), root().resolve(
                        "src/p11OnlineHarness/java/com/yo1no/gramarye/U0OrdinaryTrace.java").toString());
        assertEquals(0, result, "the actual qualification recorder source must compile");
        var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL(), gson.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        return new Compiled(loader, loader.loadClass("com.yo1no.gramarye.U0OrdinaryTrace"));
    }
    private JsonObject output(Compiled compiled, String leaf) throws Exception {
        var directory = Files.createDirectory(temporary.resolve(leaf));
        call(compiled.type, "write", new Class<?>[]{Path.class}, directory);
        return JsonParser.parseString(Files.readString(directory.resolve("ordinary-scalar-trace.json"))).getAsJsonObject();
    }
    private static void note(Class<?> owner, String tag, long a, String text) throws Exception {
        call(owner, "note", new Class<?>[]{String.class, long.class, long.class, long.class, long.class,
                long.class, long.class, int.class, int.class, int.class, String.class},
                tag, a, 0L, 0L, 0L, 0L, 0L, 0, 0, 0, text);
    }
    private static Object call(Class<?> owner, String name, Class<?>[] parameters, Object... arguments) throws Exception {
        return owner.getMethod(name, parameters).invoke(null, arguments);
    }
    private record Compiled(URLClassLoader loader, Class<?> type) implements AutoCloseable {
        @Override public void close() throws Exception { loader.close(); }
    }
    private static Path root() {
        for (var path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) { return path; }
        }
        throw new IllegalStateException("project root unavailable");
    }
}
