package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

final class V0NormalStartupTest {
    @TempDir Path directory;

    @Test
    void constructorFailureBeforeStartHasNoWorkerOrFilesystemResponsibility() {
        var setup = setup();
        assertEquals(new java.util.UUID(1, 1), setup.authority.cooldownAttemptId(1));
        assertEquals(new java.util.UUID(1, Long.MAX_VALUE), setup.authority.cooldownAttemptId(Long.MAX_VALUE));
        assertEquals(setup.authority.cooldownAttemptId(1), setup.authority.cooldownAttemptId(1));
        assertEquals(new java.util.UUID(Long.MAX_VALUE, Long.MAX_VALUE),
                V0CatalogAuthority.open(Long.MAX_VALUE, limits()).cooldownAttemptId(Long.MAX_VALUE));
        assertNotEquals(setup.authority.cooldownAttemptId(1), setup.authority.cooldownAttemptId(2));
        assertThrows(IllegalArgumentException.class, () -> setup.authority.cooldownAttemptId(0));
        assertThrows(IllegalArgumentException.class, () -> setup.authority.cooldownAttemptId(-1));
        assertThrows(IllegalArgumentException.class, () -> setup.authority.cooldownAttemptId(Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> V0CatalogAuthority.open(0, limits()));
        assertThrows(IllegalArgumentException.class, () -> V0CatalogAuthority.open(-1, limits()));
        setup.binding.abortUnpublished(setup.authority);
        assertTrue(setup.cold.finished());
        assertThrows(IllegalStateException.class, setup.cold::start);
        assertThrows(NullPointerException.class, setup.binding::catalog);
        assertFalse(Files.exists(directory.resolve("gramarye")));
    }

    @Test
    void aStartedUnpublishedWorkerConsumesTheOriginalRamRetirementHandoff() throws Exception {
        var setup = setup();
        setup.binding.startNormalCold();
        setup.binding.abortUnpublished(setup.authority);
        awaitFinished(setup.cold);
        assertFalse(Files.exists(directory.resolve("gramarye")));
        var next = setup();
        next.binding.abortUnpublished(next.authority);
        assertTrue(next.cold.finished());
    }

    @Test
    void wrongAuthorityCannotAbortOrPoisonTheOriginalStartup() throws Exception {
        var setup = setup();
        var wrong = V0CatalogAuthority.open(2, limits());
        assertNotEquals(setup.authority.cooldownAttemptId(1), wrong.cooldownAttemptId(1));
        var sameNumericAuthority = V0CatalogAuthority.open(1, limits());
        assertEquals(setup.authority.cooldownAttemptId(1), sameNumericAuthority.cooldownAttemptId(1));
        assertThrows(IllegalStateException.class, () -> setup.binding.abortUnpublished(sameNumericAuthority));
        var offThread = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var observer = Thread.ofPlatform().daemon().unstarted(() -> {
            try { setup.authority.cooldownAttemptId(1); }
            catch (RuntimeException | Error refused) { offThread.set(refused); }
        });
        observer.start(); observer.join(5_000);
        assertFalse(observer.isAlive(), "fixed owner-thread check must terminate");
        assertInstanceOf(IllegalStateException.class, offThread.get());
        assertThrows(IllegalStateException.class, () -> setup.binding.abortUnpublished(wrong));
        assertFalse(setup.cold.finished());
        setup.binding.startNormalCold();
        setup.binding.abortUnpublished(setup.authority);
        awaitFinished(setup.cold);
    }

    @Test
    void repeatedAbortDoesNotRestartAnUnpublishedLane() {
        var setup = setup();
        setup.binding.abortUnpublished(setup.authority);
        setup.binding.abortUnpublished(setup.authority);
        assertTrue(setup.cold.finished());
        assertThrows(IllegalStateException.class, setup.cold::start);
    }

    @Test
    void exactStartupBodiesPreservePrimaryAndDistinguishNewFromStartedOrTerminated() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        assertNotNull(root, "project root unavailable");
        String source = Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/V0NormalColdLane.java"));
        String methods = method(source, "    void start()") + "\n"
                + method(source, "    void abortUnpublished(V0CatalogAuthority exact)") + "\n"
                + method(source, "    private void requireOwner(V0CatalogAuthority exact)") + "\n"
                + method(source, "    private void run()") + "\n"
                + method(source, "    private void retire()");
        int suppressionStart = source.indexOf("                if (cleanupFailure != primary) {");
        int suppressionEnd = source.indexOf("\n                }", suppressionStart);
        assertTrue(suppressionStart >= 0 && suppressionEnd > suppressionStart);
        String suppression = source.substring(suppressionStart, suppressionEnd + "\n                }".length());
        Path java = directory.resolve("StartupModel.java");
        Files.writeString(java, """
                public final class StartupModel {
                    final V0CatalogAuthority authority = new V0CatalogAuthority();
                    final Mailbox mailbox = new Mailbox();
                    final Thread thread = new Thread(this);
                    final V0RuntimeCatalog catalog = new V0RuntimeCatalog();
                    static final Logger LOGGER = new Logger();
                    boolean startAttempted, aborted, entered, finished, retirementTaken;
                    Throwable primary;
                    boolean throwingWake;
                    void wake() { if (throwingWake) throw new IllegalStateException("modeled unpark failure"); }
                    void cleanup(Object ignored) { throw new AssertionError(); }
                    void provision(Object ignored) { throw new AssertionError(); }
                    void prepare(Object ignored) { throw new AssertionError(); }
                    %s
                    static void exactSuppression(Fault primary, Fault cleanupFailure) { %s }
                    static final class Fault {
                        int calls;
                        void addSuppressed(Fault ignored) { calls++; throw new AssertionError("modeled suppression failure"); }
                    }
                    static final class V0CatalogAuthority { boolean isOwnerThread() { return true; } }
                    static final class Mailbox {
                        boolean fenced, retirement, taken;
                        int retirementPolls;
                        Error failure;
                        void fence() { fenced = true; }
                        void handoffRetirement() { if (!fenced) throw new AssertionError(); retirement = true; }
                        boolean retirementForColdLane() {
                            retirementPolls++;
                            if (failure != null) throw failure;
                            if (retirement && !taken) { taken = true; return true; }
                            return false;
                        }
                        java.util.Optional<Object> recoveryForColdLane() { return java.util.Optional.empty(); }
                        void recoveredByColdLane(Object ignored) { throw new AssertionError(); }
                        java.util.Optional<Object> takeBootstrapCleanupRequest() { return java.util.Optional.empty(); }
                        java.util.Optional<Object> takeProvisionRequest() { return java.util.Optional.empty(); }
                        java.util.Optional<Object> takeRequest() { return java.util.Optional.empty(); }
                    }
                    static final class V0RuntimeCatalog {
                        enum Disposition { RELEASED, STALE_OR_RELEASED }
                        int retired;
                        Error cleanupFailure;
                        void suspend(V0CatalogAuthority ignored) { throw cleanupFailure; }
                        boolean recoverAndReopen(V0CatalogAuthority ignored, Object source) { return false; }
                        Disposition retire(V0CatalogAuthority ignored) { retired++; return Disposition.RELEASED; }
                    }
                    static final class Logger {
                        Error failure;
                        void info(String ignored, Object result) { throw failure; }
                        void error(String ignored) { throw new AssertionError(); }
                    }
                    static final class LockSupport {
                        static void parkNanos(Object blocker, long nanos) { throw new AssertionError("retirement token consumed twice"); }
                    }
                    static final class V0NormalComposition {
                        static StartupModel previous;
                        static void claimStartup(StartupModel owner) { previous = owner; }
                        static void releaseUnstarted(StartupModel owner) { if (previous == owner) previous = null; }
                    }
                    static final class Thread {
                        enum State { NEW, RUNNABLE, TERMINATED }
                        final StartupModel owner;
                        State state = State.NEW;
                        int mode;
                        Thread(StartupModel owner) { this.owner = owner; }
                        void start() {
                            if (mode != 0) { state = mode == 2 ? State.TERMINATED : State.RUNNABLE; owner.entered = true; }
                            if (owner.primary instanceof Error error) throw error;
                            throw (RuntimeException) owner.primary;
                        }
                        State getState() { return state; }
                    }
                    public static int controls() {
                        int checks = 0;
                        for (int mode = 0; mode < 3; mode++) {
                            for (boolean error : new boolean[] {false, true}) {
                                StartupModel actual = new StartupModel();
                                actual.thread.mode = mode;
                                actual.primary = error ? new AssertionError("primary") : new IllegalStateException("primary");
                                actual.throwingWake = true;
                                Throwable observed = null;
                                try { actual.start(); }
                                catch (RuntimeException | Error primary) {
                                    try { actual.abortUnpublished(actual.authority); }
                                    catch (RuntimeException | Error cleanup) { if (cleanup != primary) primary.addSuppressed(cleanup); }
                                    observed = primary;
                                }
                                if (observed != actual.primary || observed.getSuppressed().length != 1) throw new AssertionError();
                                if (!actual.mailbox.fenced || !actual.mailbox.retirement) throw new AssertionError();
                                if (actual.finished != (mode == 0)) throw new AssertionError();
                                if ((V0NormalComposition.previous == null) != (mode == 0)) throw new AssertionError();
                                checks += 4;
                                V0NormalComposition.previous = null;
                            }
                        }
                        StartupModel retiring = new StartupModel();
                        retiring.mailbox.fence();
                        retiring.mailbox.handoffRetirement();
                        Error diagnostic = new AssertionError("retirement log failure");
                        LOGGER.failure = diagnostic;
                        try { retiring.run(); throw new AssertionError(); }
                        catch (Error observed) { if (observed != diagnostic) throw new AssertionError(); }
                        if (!retiring.finished || retiring.catalog.retired != 1 || retiring.mailbox.retirementPolls != 1)
                            throw new AssertionError();
                        checks += 4;
                        StartupModel failing = new StartupModel();
                        Error original = new AssertionError("original run failure");
                        Error cleanup = new AssertionError("cleanup failure");
                        failing.mailbox.failure = original;
                        failing.catalog.cleanupFailure = cleanup;
                        try { failing.run(); throw new AssertionError(); }
                        catch (Error observed) {
                            if (observed != original || observed.getSuppressed().length != 1
                                    || observed.getSuppressed()[0] != cleanup) throw new AssertionError();
                        }
                        if (failing.finished) throw new AssertionError();
                        checks += 3;
                        Fault primary = new Fault();
                        exactSuppression(primary, new Fault());
                        if (primary.calls != 1) throw new AssertionError();
                        exactSuppression(primary, primary);
                        if (primary.calls != 1) throw new AssertionError();
                        checks += 2;
                        return checks;
                    }
                }
                """.formatted(methods, suppression));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        Path classes = Files.createDirectory(directory.resolve("classes"));
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-d", classes.toString(), java.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            assertEquals(33, loader.loadClass("StartupModel").getMethod("controls").invoke(null));
        }
    }

    private Setup setup() {
        var authority = V0CatalogAuthority.open(1, limits());
        var catalog = V0RuntimeCatalog.openNormal(authority, directory.resolve("gramarye/spell_catalog"));
        var mailbox = new V0ColdMailbox(authority);
        var cold = new V0NormalColdLane(authority, mailbox, catalog);
        return new Setup(authority, cold, V0SlotBinding.createNormal(catalog, mailbox, cold));
    }

    private static V0StartupLimits limits() { return new V0StartupLimits(1, 2, 1, 4, 16, 16, 24, 8, 1); }

    private static void awaitFinished(V0NormalColdLane cold) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!cold.finished() && System.nanoTime() < end) { Thread.sleep(1); }
        assertTrue(cold.finished(), "Original cold owner did not consume its retirement handoff");
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start);
        return source.substring(start, end + "\n    }".length());
    }

    private record Setup(V0CatalogAuthority authority, V0NormalColdLane cold, V0SlotBinding binding) { }
}
