package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Exercises the production state transitions, including the actual prepare-body finally. */
final class P10TemplateServiceTest {
    private static final Class<?> CYCLE = nested("Cycle");
    private static final Class<?> PREPARED = nested("Prepared");
    private static final Class<?> TICKET = nested("Ticket");

    @Test
    void listenerCreationDoesNotAcquireAndSupersededUnenteredWorkCannotOpen() {
        var service = service();
        var first = begin(service, new Object());
        var second = begin(service, new Object());
        var opens = new AtomicInteger();
        assertNull(field(service, "readerOwner"));
        var dropped = prepare(service, first, () -> { opens.incrementAndGet(); return missing(); });
        assertSame(staticField("DROPPED_TICKET"), dropped);
        assertEquals(0, opens.get());
        var accepted = prepare(service, second, () -> { opens.incrementAndGet(); return missing(); });
        assertEquals(1, opens.get());
        assertNull(field(service, "readerOwner"));
        apply(service, second, accepted);
        assertNotNull(field(service, "staged"));
        assertEquals(P10TemplateService.Classification.NONE, status(service).classification());
    }

    @Test
    void actualReaderOwnsSlotAndOverlapCannotOpenOrReleaseIt() throws Exception {
        var service = service();
        var first = begin(service, new Object());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                prepare(service, first, () -> { entered.countDown(); await(release); return missing(); });
            } catch (Throwable value) { failure.set(value); }
        });
        worker.start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var owner = field(service, "readerOwner");
            assertNotNull(owner);
            var second = begin(service, new Object());
            var overlap = prepare(service, second, () -> fail("overlap opened resource"));
            assertEquals(P10TemplateService.Classification.OVERLAPPING_RELOAD,
                    ((P10TemplateService.AttemptStatus) field(field(field(service, "staged"), "prepared"),
                            "rejection")).classification());
            apply(service, second, overlap);
            assertSame(owner, field(service, "readerOwner"));
        } finally {
            release.countDown();
            worker.join(10_000);
        }
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertNull(field(service, "readerOwner"));
    }

    @Test
    void stoppingAndRestartNeverReclaimALiveActualReaderOrRestageItsResult() throws Exception {
        var service = service();
        var oldCycle = begin(service, new Object());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var oldResult = new AtomicReference<Object>();
        var failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                oldResult.set(prepare(service, oldCycle, () -> {
                    entered.countDown(); await(release); return missing();
                }));
                apply(service, oldCycle, oldResult.get());
            } catch (Throwable value) { failure.set(value); }
        });
        worker.start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var oldOwner = field(service, "readerOwner");
            synchronized (service) {
                call(service, "clearState", new Class<?>[0]);
                call(service, "clearState", new Class<?>[0]);
            }
            assertSame(oldOwner, field(service, "readerOwner"));
            var next = begin(service, new Object());
            assertNotSame(field(oldCycle, "epoch"), field(next, "epoch"));
            var overlap = prepare(service, next, () -> fail("restart admitted second reader"));
            apply(service, next, overlap);
            assertEquals(P10TemplateService.Classification.OVERLAPPING_RELOAD,
                    ((P10TemplateService.AttemptStatus) field(field(field(service, "staged"), "prepared"),
                            "rejection")).classification());
        } finally {
            release.countDown();
            worker.join(10_000);
        }
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertNull(field(service, "readerOwner"));
        var retained = field(service, "staged");
        assertNotNull(retained);
        assertNotSame(oldCycle, field(retained, "cycle"));
        // Terminal release does not schedule a replacement read or change installed status.
        assertEquals(P10TemplateService.Classification.NONE, status(service).classification());
        var next = begin(service, new Object());
        assertNotNull(prepare(service, next, P10TemplateServiceTest::missing));
    }

    @Test
    void cancelledCompletedFutureCannotReleaseItsStillLivePrepareBodyAcrossRestart() throws Exception {
        var service = service();
        var oldCycle = begin(service, new Object());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var bodyTerminal = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var oldResult = new AtomicReference<Object>();
        var retained = new AtomicReference<Object>();
        var opens = new AtomicInteger();
        var future = new java.util.concurrent.FutureTask<Void>(() -> {
            try {
                oldResult.set(prepare(service, oldCycle, () -> {
                    opens.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return body();
                }));
                apply(service, oldCycle, oldResult.get());
                return null;
            } catch (RuntimeException | Error problem) {
                failure.set(problem);
                throw problem;
            } finally {
                bodyTerminal.countDown();
            }
        });
        var worker = new Thread(future, "p10-owned-cancelled-prepare-test");
        worker.start();
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var exactOwner = field(service, "readerOwner");
            assertNotNull(exactOwner);
            assertTrue(future.cancel(false));
            assertTrue(future.isCancelled());
            assertTrue(future.isDone());
            assertThrows(java.util.concurrent.CancellationException.class, future::get);
            assertTrue(worker.isAlive());
            assertEquals(1L, bodyTerminal.getCount());
            assertSame(exactOwner, field(service, "readerOwner"));
            synchronized (service) {
                call(service, "clearState", new Class<?>[0]);
                call(service, "clearState", new Class<?>[0]);
            }
            assertSame(exactOwner, field(service, "readerOwner"));
            var next = begin(service, new Object());
            var overlap = prepare(service, next, () -> {
                opens.incrementAndGet();
                return fail("cancelled future is not the actual body's terminal boundary");
            });
            apply(service, next, overlap);
            retained.set(field(service, "staged"));
            assertEquals(P10TemplateService.Classification.OVERLAPPING_RELOAD,
                    ((P10TemplateService.AttemptStatus) field(field(retained.get(), "prepared"),
                            "rejection")).classification());
            assertSame(exactOwner, field(service, "readerOwner"));
            assertEquals(1, opens.get());
        } finally {
            release.countDown();
            worker.join(10_000);
        }
        assertFalse(worker.isAlive());
        assertEquals(0L, bodyTerminal.getCount());
        assertNull(failure.get());
        assertTrue(future.isCancelled());
        assertNull(field(service, "readerOwner"));
        assertSame(staticField("DROPPED_TICKET"), oldResult.get());
        assertSame(retained.get(), field(service, "staged"));
        assertEquals(1, opens.get(), "late release must not schedule a replacement read");
        assertEquals(P10TemplateService.Classification.NONE, status(service).classification());
    }

    @Test
    void actualBodyRuntimeAndErrorFaultsReleaseOnlyTheirOwnSlotAndPreserveObject() {
        var service = service();
        var runtime = new IllegalStateException("exact-runtime");
        var error = new AssertionError("exact-error");
        var first = begin(service, new Object());
        assertSame(runtime, assertThrows(IllegalStateException.class,
                () -> prepare(service, first, () -> { throw runtime; })));
        assertNull(field(service, "readerOwner"));
        var second = begin(service, new Object());
        assertSame(error, assertThrows(AssertionError.class,
                () -> prepare(service, second, () -> { throw error; })));
        assertNull(field(service, "readerOwner"));
        var third = begin(service, new Object());
        assertNotNull(prepare(service, third, P10TemplateServiceTest::missing));
    }

    @Test
    void markerRevokedBetweenAcquisitionAndOpenCannotEvenLookUpResource() {
        var service = service();
        var old = begin(service, new Object());
        var neverOpen = net.minecraft.server.packs.resources.ResourceManager.class.cast(
                java.lang.reflect.Proxy.newProxyInstance(
                        net.minecraft.server.packs.resources.ResourceManager.class.getClassLoader(),
                        new Class<?>[] {net.minecraft.server.packs.resources.ResourceManager.class},
                        (proxy, method, arguments) -> { throw new AssertionError("resource lookup after revocation"); }));
        var result = call(service, "prepare", new Class<?>[] {CYCLE, Function.class}, old,
                (Function<Object, Object>) ownership -> {
                    begin(service, new Object());
                    return call(service, "read", new Class<?>[] {CYCLE,
                                    net.minecraft.server.packs.resources.ResourceManager.class, Object.class},
                            old, neverOpen, ownership);
                });
        assertSame(staticField("DROPPED_TICKET"), result);
        assertNull(field(service, "readerOwner"));
        assertNull(field(service, "staged"));
    }

    @Test
    void completedPrepareAndDelayedApplyRetainOnlyTheOneServiceBody() {
        var service = service();
        publish(service, new Object());
        var active = field(service, "active");
        var cycles = new java.util.ArrayList<Object>();
        var tickets = new java.util.ArrayList<Object>();
        for (int index = 0; index < 32; index++) {
            var cycle = begin(service, new Object());
            var ticket = prepare(service, cycle, P10TemplateServiceTest::body);
            cycles.add(cycle);
            tickets.add(ticket);
            assertSame(TICKET, ticket.getClass());
            assertEquals(0, TICKET.getDeclaredFields().length,
                    "barrier result must have no body/resources/collection reference");
            assertSame(ticket, field(field(service, "staged"), "ticket"));
            assertSame(cycle, field(field(service, "staged"), "cycle"));
            assertEquals(false, field(field(service, "staged"), "applied"));
            assertNull(field(service, "readerOwner"));
            assertSame(active, field(service, "active"));
        }
        var lastStaged = field(service, "staged");
        for (int index = 0; index < tickets.size() - 1; index++) {
            apply(service, cycles.get(index), tickets.get(index));
            assertSame(lastStaged, field(service, "staged"));
            assertEquals(false, field(lastStaged, "applied"));
        }
        apply(service, cycles.getLast(), tickets.getLast());
        assertEquals(true, field(lastStaged, "applied"));
        assertEquals(P10TemplateService.Classification.ACCEPTED, status(service).classification(),
                "prepare/apply may not overwrite latest installed status");
    }

    @Test
    void delayedBarrierTicketsCannotRetainOrRestoreBodiesAcrossStopAndRestart() {
        var service = service();
        var oldCycle = begin(service, new Object());
        var oldTicket = prepare(service, oldCycle, P10TemplateServiceTest::body);
        assertNotNull(field(service, "staged"));
        assertNull(field(service, "readerOwner"));
        call(service, "clearState", new Class<?>[0]);
        assertNull(field(service, "staged"));
        assertNull(field(service, "active"));
        assertEquals(0, oldTicket.getClass().getDeclaredFields().length);
        var fresh = begin(service, new Object());
        var freshTicket = prepare(service, fresh, P10TemplateServiceTest::body);
        var freshStaged = field(service, "staged");
        apply(service, oldCycle, oldTicket);
        assertSame(freshStaged, field(service, "staged"));
        assertEquals(false, field(freshStaged, "applied"));
        apply(service, fresh, freshTicket);
        assertEquals(true, field(freshStaged, "applied"));
        assertNull(field(service, "readerOwner"));
        assertEquals(P10TemplateService.Classification.NONE, status(service).classification());
    }

    @Test
    void pessimisticFaultMarkerIsPreallocatedAndPublishAllocatesBeforeMutating() throws Exception {
        var service = service();
        publish(service, new Object());
        var oldActive = field(service, "active");
        var oldToken = field(service, "publicationToken");
        for (int index = 0; index < 8; index++) {
            pessimistic(service);
            assertSame(staticField("INTERNAL_FAILURE_ATTEMPT"), status(service));
            assertSame(oldActive, field(service, "active"));
            assertSame(oldToken, field(service, "publicationToken"));
        }
        var cursor = java.nio.file.Path.of("").toAbsolutePath();
        while (cursor != null && !java.nio.file.Files.isRegularFile(cursor.resolve("settings.gradle"))) {
            cursor = cursor.getParent();
        }
        assertNotNull(cursor);
        var source = java.nio.file.Files.readString(cursor.resolve(
                "src/main/java/com/yo1no/gramarye/P10TemplateService.java"));
        var publish = source.substring(source.indexOf("if (result.ready()) {"),
                source.indexOf("lastAttempt = AttemptStatus.fromValidation(result);"));
        assertTrue(publish.indexOf("var freshToken = new Object();")
                < publish.indexOf("active = candidate.body;"));
        var assignments = publish.substring(publish.indexOf("active = candidate.body;"));
        assertFalse(assignments.contains("new "));
        assertFalse(assignments.contains("AttemptStatus.control("));
    }

    @Test
    void preInstallFailureLeavesCurrentAndInstalledStatusWhileStageIsInaccessible() {
        var service = service();
        var installed = new Object();
        publish(service, installed);
        var token = field(service, "publicationToken");
        var failedInstall = begin(service, new Object());
        stage(service, failedInstall, missing());
        assertEquals(P10TemplateService.Origin.CURRENT, field(service, "origin"));
        assertSame(token, field(service, "publicationToken"));
        assertEquals(P10TemplateService.Classification.ACCEPTED, status(service).classification());
        assertNotNull(field(service, "staged"));
        pessimistic(service);
        decide(service, installed, ready());
        assertEquals(P10TemplateService.Origin.LKG, field(service, "origin"));
        assertEquals(P10TemplateService.Classification.STALE_CANDIDATE, status(service).classification());
        assertNull(field(service, "staged"));
    }

    @Test
    void successfulEvenContentEqualPublicationAllocatesFreshTokenAndClearsSummary() {
        var service = service();
        publish(service, new Object());
        var old = field(service, "publicationToken");
        publish(service, new Object());
        assertNotSame(old, field(service, "publicationToken"));
        assertEquals(P10TemplateService.Origin.CURRENT, field(service, "origin"));
        assertEquals(P10TemplateService.Classification.ACCEPTED, status(service).classification());
        assertEquals(0, status(service).retainedCount());
        assertTrue(status(service).summaries().isEmpty());
        assertEquals(0, status(service).retainedButNotSummarized());
        assertFalse(status(service).upstreamTruncated());
        assertNull(field(service, "staged"));
    }

    @Test
    void scalarOnlySemanticRejectionKeepsLkgAndCannotPublishAnEmptySuccessfulReport() {
        var service = service();
        publish(service, new Object());
        var oldBody = field(service, "active");
        var oldToken = field(service, "publicationToken");
        var resources = new Object();
        var cycle = begin(service, resources);
        stage(service, cycle, body());
        pessimistic(service);
        var rejected = new P10TemplateValidation.Result(
                P10TemplateValidation.Classification.SEMANTIC_INVALID, ValidationResult.valid());
        assertFalse(rejected.ready());
        assertFalse(rejected.report().hasErrors());
        decide(service, resources, ignored -> rejected);
        assertSame(oldBody, field(service, "active"));
        assertSame(oldToken, field(service, "publicationToken"));
        assertEquals(P10TemplateService.Origin.LKG, field(service, "origin"));
        assertEquals(P10TemplateService.Classification.SEMANTIC_INVALID, status(service).classification());
        assertEquals(0, status(service).retainedCount());
        assertTrue(status(service).summaries().isEmpty());
        assertFalse(status(service).upstreamTruncated());
        assertNull(field(service, "staged"));
    }

    @Test
    void missingInvalidAndNoMatchingP8KeepLkgWithoutPretendingCurrentContext() {
        var service = service();
        var first = new Object();
        var initial = begin(service, first);
        stage(service, initial, missing());
        pessimistic(service);
        decide(service, first, ready());
        assertNull(field(service, "active"));
        assertEquals(P10TemplateService.Origin.UNAVAILABLE, field(service, "origin"));
        assertEquals(P10TemplateService.Classification.MISSING_REQUIRED_TEMPLATE, status(service).classification());
        publish(service, new Object());
        var oldBody = field(service, "active");
        var rejectedResources = new Object();
        var invalid = begin(service, rejectedResources);
        stage(service, invalid, body());
        pessimistic(service);
        decide(service, rejectedResources, ignored -> new P10TemplateValidation.Result(
                P10TemplateValidation.Classification.UNKNOWN_TYPE, ValidationResult.valid()));
        assertSame(oldBody, field(service, "active"));
        assertEquals(P10TemplateService.Origin.LKG, field(service, "origin"));
        assertEquals(P10TemplateService.Classification.UNKNOWN_TYPE, status(service).classification());
        assertNull(field(service, "staged"));
        for (var outcome : new P8ServerPresentationService.P8RootFullSyncOutcome[] {
                P8ServerPresentationService.P8RootFullSyncOutcome.NO_MATCHING_CANDIDATE,
                P8ServerPresentationService.P8RootFullSyncOutcome.GENERATION_EXHAUSTED}) {
            pessimistic(service);
            call(service, "decideInstalled", new Class<?>[] {Object.class, outcome.getClass(), Function.class},
                    new Object(), outcome, (Function<P10TemplateBody, P10TemplateValidation.Result>)
                            ignored -> fail("unavailable P8 must precede candidate validation"));
            assertEquals(false, field(service, "contextCurrent"));
            assertEquals(P10TemplateService.Classification.CONTEXT_UNAVAILABLE, status(service).classification());
            assertSame(oldBody, field(service, "active"));
        }
    }

    @Test
    void postInstallFaultRetainsPessimisticMarkerAndLkgWithoutPublishing() {
        var service = service();
        publish(service, new Object());
        var oldBody = field(service, "active");
        var oldToken = field(service, "publicationToken");
        var resources = new Object();
        var cycle = begin(service, resources);
        stage(service, cycle, body());
        pessimistic(service);
        assertEquals(P10TemplateService.Origin.LKG, field(service, "origin"));
        assertEquals(P10TemplateService.Classification.INTERNAL_FAILURE, status(service).classification());
        var exact = new AssertionError("validation fault");
        assertSame(exact, assertThrows(AssertionError.class,
                () -> decide(service, resources, ignored -> { throw exact; })));
        assertSame(oldBody, field(service, "active"));
        assertSame(oldToken, field(service, "publicationToken"));
        assertEquals(P10TemplateService.Classification.INTERNAL_FAILURE, status(service).classification());
        assertTrue(status(service).summaries().isEmpty());
        assertNull(field(service, "staged"));
    }

    @Test
    void retainedIssueCeilingCannotChangeFatalScalarOrExpandLatestSummaryBudget() {
        var service = service();
        var resources = new Object();
        var cycle = begin(service, resources);
        stage(service, cycle, body());
        pessimistic(service);
        var warnings = java.util.stream.IntStream.range(0, 1_024)
                .mapToObj(index -> new ValidationIssue(
                        ValidationIssueCode.fromNamespaceAndPath("gramarye", "p10_existing_warning"),
                        ValidationSeverity.WARNING, ValidationPath.empty().field("nodes").index(index),
                        ValidationIssueMetadata.none()))
                .toList();
        decide(service, resources, ignored -> new P10TemplateValidation.Result(
                P10TemplateValidation.Classification.MIGRATION_FAILED,
                new ValidationResult(warnings, true, true)));
        assertNull(field(service, "active"));
        assertEquals(P10TemplateService.Classification.MIGRATION_FAILED, status(service).classification());
        assertEquals(16, status(service).summaries().size());
        assertEquals(1_024, status(service).retainedCount());
        assertEquals(1_008, status(service).retainedButNotSummarized());
        assertTrue(status(service).upstreamTruncated());
        assertTrue(status(service).summaries().stream().allMatch(value -> value.length() <= 1_024));
        assertTrue(status(service).summaries().stream().mapToInt(String::length).sum() <= 16_384);
        assertNull(field(service, "staged"));
    }

    @Test
    void realSaturatedProductionResultsStayRejectedInInstalledAttemptStatus() throws Exception {
        for (var classification : java.util.List.of(P10TemplateValidation.Classification.UNKNOWN_TYPE,
                P10TemplateValidation.Classification.MIGRATION_FAILED,
                P10TemplateValidation.Classification.DECODE_FAILED,
                P10TemplateValidation.Classification.FUTURE_SCHEMA,
                P10TemplateValidation.Classification.SEMANTIC_INVALID)) {
            var service = service();
            publish(service, new Object());
            var oldBody = field(service, "active");
            var oldToken = field(service, "publicationToken");
            var resources = new Object();
            var cycle = begin(service, resources);
            var candidate = P10TemplateValidationTest.saturatedProductionBody(classification);
            stage(service, cycle, body(candidate));
            pessimistic(service);
            decide(service, resources, actual -> {
                assertSame(candidate, actual);
                var result = P10TemplateValidationTest.saturatedProductionFailure(actual);
                assertFalse(result.ready());
                return result;
            });
            assertSame(oldBody, field(service, "active"));
            assertSame(oldToken, field(service, "publicationToken"));
            assertEquals(P10TemplateService.Origin.LKG, field(service, "origin"));
            assertEquals(classification.name(), status(service).classification().name());
            assertEquals(1024, status(service).retainedCount());
            assertEquals(16, status(service).summaries().size());
            assertEquals(1008, status(service).retainedButNotSummarized());
            assertTrue(status(service).upstreamTruncated());
            assertTrue(status(service).summaries().stream()
                    .allMatch(summary -> summary.contains("early.warning") && summary.contains("nodes[0].trigger.payload")));
            assertNull(field(service, "staged"));
        }
    }

    private static P10TemplateService service() {
        return new P10TemplateService(new P10TemplateValidation(
                SkillSubmissionPolicyProvider.defaults(), P8ServerPresentationService.create()));
    }

    private static void publish(P10TemplateService service, Object resources) {
        var cycle = begin(service, resources);
        stage(service, cycle, body());
        pessimistic(service);
        decide(service, resources, ready());
    }

    private static Function<P10TemplateBody, P10TemplateValidation.Result> ready() {
        return ignored -> new P10TemplateValidation.Result(
                P10TemplateValidation.Classification.ACCEPTED, ValidationResult.valid());
    }

    private static void decide(P10TemplateService service, Object resources,
            Function<P10TemplateBody, P10TemplateValidation.Result> validation) {
        call(service, "decideInstalled", new Class<?>[] {Object.class,
                        P8ServerPresentationService.P8RootFullSyncOutcome.class, Function.class},
                resources, P8ServerPresentationService.P8RootFullSyncOutcome.ACTIVATED_CURRENT, validation);
    }

    private static void pessimistic(P10TemplateService service) {
        call(service, "pessimisticPostInstall", new Class<?>[0]);
    }

    private static P10TemplateService.AttemptStatus status(P10TemplateService service) {
        return (P10TemplateService.AttemptStatus) field(service, "lastAttempt");
    }

    private static Object body() {
        try {
            var decoded = (P10TemplateCodec.Decoded) P10TemplateCodec.decode(new ByteArrayInputStream(
                    "{\"nodes\":[],\"appearance\":{}}".getBytes(StandardCharsets.UTF_8)));
            var constructor = PREPARED.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance(decoded.body(), decoded.rawBytes(), null, false);
        } catch (ReflectiveOperationException | java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object body(P10TemplateBody candidate) {
        try {
            var constructor = PREPARED.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            // This control starts at the installed decision; it does not claim stream admission.
            return constructor.newInstance(candidate, 0, null, false);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Object missing() {
        try {
            var constructor = PREPARED.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return constructor.newInstance(null, 0, new P10TemplateService.AttemptStatus(
                    P10TemplateService.Classification.MISSING_REQUIRED_TEMPLATE, java.util.List.of(), 0, false), false);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Object begin(P10TemplateService service, Object resources) {
        return call(service, "beginCycle", new Class<?>[] {Object.class}, resources);
    }

    private static Object prepare(P10TemplateService service, Object cycle, Supplier<Object> reader) {
        return call(service, "prepare", new Class<?>[] {CYCLE, Function.class}, cycle,
                (Function<Object, Object>) ignored -> reader.get());
    }

    private static void stage(P10TemplateService service, Object cycle, Object result) {
        var ticket = call(service, "stage", new Class<?>[] {CYCLE, PREPARED}, cycle, result);
        apply(service, cycle, ticket);
    }

    private static void apply(P10TemplateService service, Object cycle, Object ticket) {
        call(service, "applyTicket", new Class<?>[] {CYCLE, TICKET}, cycle, ticket);
    }

    private static Class<?> nested(String name) {
        try { return Class.forName(P10TemplateService.class.getName() + "$" + name); }
        catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
    }

    private static Object field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Object staticField(String name) {
        try {
            var field = P10TemplateService.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Object call(Object owner, String name, Class<?>[] parameters, Object... values) {
        try {
            var method = owner.getClass().getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(owner, values);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) { throw runtime; }
            if (failure.getCause() instanceof Error error) { throw error; }
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
}
