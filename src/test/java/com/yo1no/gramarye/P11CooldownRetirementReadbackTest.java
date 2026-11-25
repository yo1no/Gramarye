package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact production bodies over isolated native-port values; these controls are not disk/native proof. */
final class P11CooldownRetirementReadbackTest {
    @TempDir Path temporary;

    @Test void exactReadOnlyProducerPreservesSourceAndPrimaryAcrossFiniteNativePortControls() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) root = root.getParent();
        assertNotNull(root);
        var base = root.resolve("src/main/java/com/yo1no/gramarye");
        String boundary = Files.readString(base.resolve("P11NativeStorageBoundary.java"));
        String owner = Files.readString(base.resolve("P11QualifiedSourceOwner.java"));
        String source = scaffold().replace("/* BOUNDARY_METHODS */",
                method(boundary, "    static boolean readPrimaryForCooldownRetirement(")
                + method(boundary, "    public static void preparePrimary(")
                + method(boundary, "    public static void readPrimary("))
                .replace("/* OWNER_METHOD */", method(owner, "    boolean prepareCooldownRetirementReadback("));
        var java = temporary.resolve("Control.java"); var classes = Files.createDirectory(temporary.resolve("classes"));
        Files.writeString(java, source, StandardCharsets.UTF_8);
        var compiler = ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            assertTrue(Boolean.TRUE.equals(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-Xlint:all,-serial,-auxiliaryclass", "-Werror",
                            "-classpath", classes.toString(), "-d", classes.toString()), null,
                    manager.getJavaFileObjects(java)).call()), () -> diagnostics.getDiagnostics().toString());
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var control = loader.loadClass("com.yo1no.gramarye.Control");
            for (String name : List.of("success", "missing-writer", "wrong-storage", "mismatch", "source-change",
                    "missing-read", "read-error", "observation-error", "nested", "host", "capture-change", "normal")) {
                control.getMethod("run", String.class).invoke(null, name);
            }
        }
    }

    private static String method(String source, String head) {
        int start = source.indexOf(head); assertTrue(start >= 0, head);
        int open = source.indexOf('{', start), end = open + 1, depth = 1;
        while (depth != 0) {
            char c = source.charAt(end++); if (c == '{') depth++; else if (c == '}') depth--;
        }
        return source.substring(start, end) + '\n';
    }

    private static String scaffold() { return """
            package com.yo1no.gramarye;
            import java.util.Optional;
            public final class Control {
                public static void run(String name) {
                    var owner = new P11QualifiedSourceOwner(); var body = owner.body;
                    var root = new Root(owner); P11NativeStorageBoundary.root = root;
                    var primary = new AssertionError("controlled native primary");
                    switch (name) {
                        case "success", "normal" -> { }
                        case "missing-writer" -> owner.writerComplete = false;
                        case "wrong-storage" -> root.wrongStorage = true;
                        case "mismatch" -> body.actor.server.players.storage.disk = new CompoundTag(2);
                        case "source-change" -> body.actor.server.players.storage.afterRead = () -> body.source = new P11ReceiptLedger.Source();
                        case "missing-read" -> body.actor.server.players.storage.present = false;
                        case "read-error" -> body.actor.server.players.storage.failure = primary;
                        case "observation-error" -> owner.observationError = primary;
                        case "nested" -> P11NativeStorageBoundary.PRIMARY_READ.set(new P11NativeStorageBoundary.PrimaryReadRequest(owner, body));
                        case "host" -> body.actor.server.host = true;
                        case "capture-change" -> owner.captureChange = true;
                        default -> throw new AssertionError(name);
                    }
                    var nested = P11NativeStorageBoundary.PRIMARY_READ.get();
                    var expected = body.source; boolean actual = false; Throwable caught = null;
                    try {
                        if (name.equals("normal")) {
                            var request = new P11NativeStorageBoundary.PrimaryReadRequest(owner, body);
                            P11NativeStorageBoundary.PRIMARY_READ.set(request);
                            try {
                                body.actor.server.players.p11$preparePrimary(request);
                                actual = request.stage == P11NativeStorageBoundary.PrimaryStage.VERIFIED;
                            } finally { P11NativeStorageBoundary.PRIMARY_READ.remove(); request.clear(); }
                        } else { actual = owner.prepareCooldownRetirementReadback(body, expected); }
                    } catch (RuntimeException | Error failure) { caught = failure; }
                    if (name.equals("success") || name.equals("normal")) {
                        check(actual && caught == null && owner.observed == 1, name + " real reader path");
                    } else {
                        check(!actual && owner.observed == 0, name + " no fabricated readback");
                        check(caught == (name.endsWith("error") ? primary : null), name + " exact primary");
                    }
                    check(body.actor.server.players.saves == (name.equals("normal") ? 1 : 0), name + " original save count");
                    int reads = ListSupport.contains(name, "success", "normal", "mismatch", "source-change", "missing-read", "read-error", "observation-error") ? 1 : 0;
                    check(body.actor.server.players.storage.reads == reads, name + " original private read count");
                    check(P11NativeStorageBoundary.PRIMARY_READ.get() == nested || name.equals("normal"), name + " scope restored");
                    if (name.equals("read-error") || name.equals("observation-error") || name.equals("mismatch")
                            || name.equals("source-change") || name.equals("missing-read") || name.equals("wrong-storage")) {
                        check(owner.retained == 0, name + " optional retirement read does not dirty or fault the ordinary source");
                    }
                    var last = body.actor.server.players.last;
                    if (last != null) check(last.material == null && last.stage == P11NativeStorageBoundary.PrimaryStage.CLOSED, name + " request cleared");
                    P11NativeStorageBoundary.PRIMARY_READ.remove();
                }
                static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
            }
            final class ListSupport {
                static boolean contains(String value, String... choices) { for (String s : choices) if (s.equals(value)) return true; return false; }
            }
            interface Operation<T> { T call(Object... args); }
            record CompoundTag(int value) { }
            final class NbtUtils { static void addCurrentDataVersion(CompoundTag value) { } }
            final class Actor {
                final Server server = new Server(); Server getServer() { return server; } Object getGameProfile() { return this; }
            }
            final class Server {
                final PlayerList players = new PlayerList(); boolean host;
                PlayerList getPlayerList() { return players; } boolean isSingleplayerOwner(Object profile) { return host; }
            }
            final class PlayerList implements P11NativeWorldAccess.PlayerStorage {
                final PlayerDataStorage storage = new PlayerDataStorage(); int saves;
                P11NativeStorageBoundary.PrimaryReadRequest last;
                public boolean p11$independentOwnersMatch(Actor player) { return true; }
                public void p11$preparePrimary(P11NativeStorageBoundary.PrimaryReadRequest request) {
                    last = request;
                    P11NativeStorageBoundary.preparePrimary(this, storage, request, args -> {
                        saves++; request.receipt = request.owner.writer; return null;
                    });
                }
            }
            final class PlayerDataStorage implements P11NativeWorldAccess.PrimaryReader {
                int reads; boolean present = true; Error failure; Runnable afterRead = () -> { };
                CompoundTag disk = new CompoundTag(1);
                public void p11$readPrimary(P11NativeStorageBoundary.PrimaryReadRequest request) {
                    P11NativeStorageBoundary.readPrimary(this, request, args -> {
                        reads++; if (failure != null) throw failure;
                        request.readEntered = true; request.readState = P11NativeStorageBoundary.ReadState.READ;
                        afterRead.run(); return present ? Optional.of(disk) : Optional.empty();
                    });
                }
            }
            final class Root {
                final P11QualifiedSourceOwner owner; boolean wrongStorage;
                Root(P11QualifiedSourceOwner owner) { this.owner = owner; }
                P11QualifiedSourceOwner writerOwner(PlayerList players) { return owner; }
                P11QualifiedSourceOwner playerStorageOwner(PlayerDataStorage storage, Actor actor) { return wrongStorage ? null : owner; }
            }
            final class P11NativeWorldAccess {
                interface PlayerStorage { boolean p11$independentOwnersMatch(Actor actor); void p11$preparePrimary(P11NativeStorageBoundary.PrimaryReadRequest request); }
                interface PrimaryReader { void p11$readPrimary(P11NativeStorageBoundary.PrimaryReadRequest request); }
            }
            final class P11ReceiptLedger {
                static final class Source { }
                static final class PhysicalWriterReceipt {
                    final Source source; PhysicalWriterReceipt(Source source) { this.source = source; } Source source() { return source; }
                }
            }
            final class P11QualifiedSourceOwner {
                final Body body = new Body(); final SelectionWitness witness = new SelectionWitness(body.source);
                final P11ReceiptLedger.PhysicalWriterReceipt writer = new P11ReceiptLedger.PhysicalWriterReceipt(body.source);
                final ReceiptOwner receipts = new ReceiptOwner(); final Server server = body.actor.server;
                boolean writerComplete = true, captureChange; Error observationError; int observed, retained;
                final class ReceiptOwner {
                    Optional<P11ReceiptLedger.PhysicalWriterReceipt> currentPlayerWriteForReadback(P11ReceiptLedger.Source source) {
                        return writerComplete && source == writer.source ? Optional.of(writer) : Optional.empty();
                    }
                }
                static final class Body { final Actor actor = new Actor(); P11ReceiptLedger.Source source = new P11ReceiptLedger.Source(); }
                static final class SelectionWitness {
                    final P11ReceiptLedger.Source source; SelectionWitness(P11ReceiptLedger.Source source) { this.source = source; }
                }
                static final class SourceUnavailable extends IllegalStateException { }
                boolean canCopy(Body value) { return value == body; } boolean canSerialize(Body value) { return canCopy(value); }
                SelectionWitness captureSelection(Body value) { if (captureChange) value.source = new P11ReceiptLedger.Source(); return witness; }
                boolean metadataCurrent(Body value, P11ReceiptLedger.Source source, SelectionWitness proof) {
                    return value == body && value.source == source && proof == witness && proof.source == source;
                }
                boolean completedPlayerWrite(Body value, P11ReceiptLedger.PhysicalWriterReceipt receipt) { return completedPlayerWrite(value, receipt, witness); }
                boolean completedPlayerWrite(Body value, P11ReceiptLedger.PhysicalWriterReceipt receipt, SelectionWitness proof) {
                    return writerComplete && receipt == writer && value.source == writer.source && metadataCurrent(value, value.source, proof);
                }
                Object cooldownRetirementReadback(Body value, P11ReceiptLedger.Source source) { return observed == 1 && value.source == source ? witness : null; }
                void cooldownPrimaryReadback(Body value, P11ReceiptLedger.PhysicalWriterReceipt receipt, SelectionWitness proof,
                        P11NativeStorageBoundary.PrimaryReadRequest request) {
                    if (observationError != null) throw observationError;
                    Control.check(request.stage == P11NativeStorageBoundary.PrimaryStage.VERIFIED && request.readEntered, "only verified native read");
                    observed++;
                }
                /* OWNER_METHOD */
            }
            final class P11NativeStorageBoundary {
                static Root root;
                static final ThreadLocal<PrimaryReadRequest> PRIMARY_READ = new ThreadLocal<>();
                static final ThreadLocal<Object> WRITE = new ThreadLocal<>(), CACHE = new ThreadLocal<>();
                enum PrimaryStage { CREATED, SAVING, READ_REQUESTED, READING, READ, VERIFIED, CLOSED }
                enum ReadState { UNOBSERVED, READ }
                static final class PrimaryReadRequest {
                    final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body previous;
                    PlayerDataStorage storage; P11ReceiptLedger.PhysicalWriterReceipt receipt;
                    P11QualifiedSourceOwner.SelectionWitness witness; PrimaryStage stage = PrimaryStage.CREATED;
                    ReadState readState = ReadState.UNOBSERVED; boolean readEntered, duplicateWriter;
                    CompoundTag material;
                    PrimaryReadRequest(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body previous) { this.owner = owner; this.previous = previous; }
                    void clear() { material = null; stage = PrimaryStage.CLOSED; }
                }
                static CompoundTag selectedMaterial(P11QualifiedSourceOwner.Body body) { return new CompoundTag(1); }
                static P11QualifiedSourceOwner.SourceUnavailable unavailable() { return new P11QualifiedSourceOwner.SourceUnavailable(); }
                static void retainWithoutReplacingPrimary(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) { owner.retained++; }
                /* BOUNDARY_METHODS */
            }
            """; }
}
