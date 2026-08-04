package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Executable fail-closed controls for the B1 formal invocation boundary. */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
final class P4E0ResearchR2QFormalGateNegativeTest {
    private static final String ENABLED_PROPERTY =
            "gramarye.p4e0.r2q.formal.enabled";
    private static final String BUDGET_PROPERTY =
            "gramarye.p4e0.r2q.formal.diskBudgetBytes";
    private static final String REPOSITORY_PROPERTY =
            "gramarye.p4e0.r2q.formal.repositoryRoot";
    private static final String EXACT_BUDGET = "12884901888";
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(15L);
    private static final int MAXIMUM_GIT_OUTPUT_BYTES = 65_536;

    @TempDir
    Path temporaryDirectory;

    @Test
    void prepareStudyRejectsAbsentFormalPropertyBeforeAnyChildOrOfficialEvidence()
            throws Throwable {
        var invocation = invocation("missing-formal-property", RepositoryMutation.NONE);

        var failure = assertThrows(IllegalStateException.class,
                () -> withProperties(null, EXACT_BUDGET, invocation.repository(),
                        () -> P4E0R2QFormalMain.main(invocation.arguments("prepare-study"))));

        assertAll(
                () -> assertTrue(failure.getMessage().contains("formal R2Q property is absent")),
                () -> assertNoFormalOutput(invocation));
    }

    @Test
    void prepareStudyRejectsAbsentOrWrongExactDiskBudgetBeforeAnyOutput()
            throws Throwable {
        for (var budget : List.<String>of("", "12884901887")) {
            var invocation = invocation("budget-" + (budget.isEmpty() ? "missing" : "wrong"),
                    RepositoryMutation.NONE);

            var failure = assertThrows(IllegalStateException.class,
                    () -> withProperties("true", budget.isEmpty() ? null : budget,
                            invocation.repository(),
                            () -> P4E0R2QFormalMain.main(
                                    invocation.arguments("prepare-study"))));

            assertAll(
                    () -> assertTrue(failure.getMessage().contains(
                            "formal R2Q disk budget property changed")),
                    () -> assertNoFormalOutput(invocation));
        }
    }

    @Test
    void prepareStudyRejectsDirtyTrackedIndexAndUntrackedStatesBeforeAnyOutput()
            throws Throwable {
        for (var mutation : List.of(
                RepositoryMutation.DIRTY_TRACKED,
                RepositoryMutation.DIRTY_INDEX,
                RepositoryMutation.NONIGNORED_UNTRACKED)) {
            var invocation = invocation(mutation.name().toLowerCase(), mutation);

            var failure = invokeRejectedPrepare(invocation);

            assertAll(
                    () -> assertTrue(failure.getMessage().contains(
                            "formal R2Q repository gate rejected dirty or detached state")),
                    () -> assertNoFormalOutput(invocation));
        }
    }

    @Test
    void prepareStudyRejectsDetachedAndWrongBranchStatesBeforeAnyOutput()
            throws Throwable {
        for (var mutation : List.of(
                RepositoryMutation.DETACHED_HEAD,
                RepositoryMutation.WRONG_BRANCH)) {
            var invocation = invocation(mutation.name().toLowerCase(), mutation);

            var failure = invokeRejectedPrepare(invocation);

            assertAll(
                    () -> assertTrue(
                            failure.getMessage().contains(
                                    "formal R2Q repository gate rejected dirty or detached state")
                                    || failure.getMessage().contains(
                                            "formal git command failed"),
                            "detached and wrong-branch states must fail inside the Git gate"),
                    () -> assertNoFormalOutput(invocation));
        }
    }

    @Test
    void prepareStudyRejectsOriginMainMismatchBeforeAnyOutput() throws Throwable {
        var invocation = invocation("origin-main-mismatch",
                RepositoryMutation.ORIGIN_MAIN_MISMATCH);

        var failure = invokeRejectedPrepare(invocation);

        assertAll(
                () -> assertTrue(failure.getMessage().contains(
                        "formal R2Q HEAD differs from origin/main")),
                () -> assertNoFormalOutput(invocation));
    }

    @Test
    void captureFailureRequiresFormalModeAndExactBudgetEvenWhenWorkRootIsAbsent()
            throws Throwable {
        var missingMode = invocation("capture-missing-mode", RepositoryMutation.NONE);
        var modeFailure = assertThrows(IllegalStateException.class,
                () -> withProperties(null, EXACT_BUDGET, missingMode.repository(),
                        () -> P4E0R2QFormalMain.main(
                                missingMode.arguments(
                                        "capture-failure", "0", "CHILD_EXIT_FAILURE"))));

        var missingBudget = invocation("capture-missing-budget", RepositoryMutation.NONE);
        var budgetFailure = assertThrows(IllegalStateException.class,
                () -> withProperties("true", null, missingBudget.repository(),
                        () -> P4E0R2QFormalMain.main(
                                missingBudget.arguments(
                                        "capture-failure", "0", "CHILD_EXIT_FAILURE"))));

        assertAll(
                () -> assertTrue(modeFailure.getMessage().contains(
                        "formal R2Q property is absent")),
                () -> assertTrue(budgetFailure.getMessage().contains(
                        "formal R2Q disk budget property changed")),
                () -> assertNoFormalOutput(missingMode),
                () -> assertNoFormalOutput(missingBudget));
    }

    @Test
    void cleanPrepareAcceptsAbsentEmptyAndExactMetadataOnlyOfficialRoots() throws Throwable {
        for (var shape : List.of("absent", "empty", "metadata")) {
            var invocation = invocation("clean-" + shape, RepositoryMutation.NONE);
            if (!shape.equals("absent")) {
                Files.createDirectories(invocation.officialRoot());
            }
            if (shape.equals("metadata")) {
                Files.writeString(invocation.officialRoot().resolve(".DS_Store"),
                        "finder metadata");
            }

            withProperties("true", EXACT_BUDGET, invocation.repository(),
                    () -> P4E0R2QFormalMain.main(invocation.arguments("prepare-study")));

            assertAll(
                    () -> assertFalse(Files.exists(invocation.officialRoot())),
                    () -> assertTrue(Files.isRegularFile(
                            invocation.workRoot().resolve("study-control.json"))),
                    () -> assertFalse(Files.exists(invocation.firstCaseRoot())),
                    () -> assertFalse(Files.exists(invocation.failedEvidenceRoot())),
                    () -> assertFalse(Files.exists(invocation.staleEvidenceRoot())));
        }
    }

    @Test
    void malformedLegacySmokeOutputIsPreservedBeforeCaseZero() throws Throwable {
        var invocation = invocation("legacy-smoke-output", RepositoryMutation.NONE);
        Files.createDirectories(invocation.officialRoot().resolve("smoke"));
        var smoke = invocation.officialRoot().resolve("smoke/standalone-smoke-v0.json");
        Files.writeString(smoke, "legacy smoke\n");
        Files.writeString(invocation.officialRoot().resolve(".DS_Store"), "finder metadata");

        var failure = invokeRejectedPrepare(invocation);

        assertAll(
                () -> assertTrue(failure.getMessage().contains(
                        "malformed nonempty formal output is preserved")),
                () -> assertEquals("legacy smoke\n", Files.readString(smoke)),
                () -> assertTrue(Files.isRegularFile(
                        invocation.officialRoot().resolve(".DS_Store"))),
                () -> assertFalse(Files.exists(invocation.workRoot())),
                () -> assertFalse(Files.exists(invocation.firstCaseRoot())),
                () -> assertFalse(Files.exists(invocation.failedEvidenceRoot())),
                () -> assertFalse(Files.exists(invocation.staleEvidenceRoot())));
    }

    @Test
    void partialOfficialSetIsPreservedBeforeCaseZero() throws Throwable {
        var invocation = invocation("partial-official-output", RepositoryMutation.NONE);
        Files.createDirectories(invocation.officialRoot());
        var partial = invocation.officialRoot().resolve("PROVENANCE.txt");
        Files.writeString(partial, "partial official evidence\n");

        var failure = invokeRejectedPrepare(invocation);

        assertAll(
                () -> assertTrue(failure.getMessage().contains(
                        "malformed nonempty formal output is preserved")),
                () -> assertEquals("partial official evidence\n", Files.readString(partial)),
                () -> assertFalse(Files.exists(invocation.workRoot())),
                () -> assertFalse(Files.exists(invocation.firstCaseRoot())),
                () -> assertFalse(Files.exists(invocation.failedEvidenceRoot())),
                () -> assertFalse(Files.exists(invocation.staleEvidenceRoot())));
    }

    private IOException invokeRejectedPrepare(Invocation invocation) throws Throwable {
        return assertThrows(IOException.class,
                () -> withProperties("true", EXACT_BUDGET, invocation.repository(),
                        () -> P4E0R2QFormalMain.main(invocation.arguments("prepare-study"))));
    }

    private Invocation invocation(String name, RepositoryMutation mutation) throws Exception {
        var root = temporaryDirectory.resolve(name);
        var repository = root.resolve("repository");
        Files.createDirectories(repository);
        git(repository, "init");
        git(repository, "config", "user.name", "R2Q B1 Gate Test");
        git(repository, "config", "user.email", "r2q-b1-gate@example.invalid");
        git(repository, "symbolic-ref", "HEAD", "refs/heads/main");
        Files.writeString(repository.resolve("tracked.txt"), "base\n");
        Files.writeString(repository.resolve(".gitignore"), "/build/\n");
        git(repository, "add", "tracked.txt", ".gitignore");
        git(repository, "commit", "-m", "base");
        git(repository, "update-ref", "refs/remotes/origin/main", "HEAD");
        mutation.apply(repository);
        return new Invocation(
                repository,
                repository.resolve("build/p4-e0-r2q/formal"),
                repository.resolve("build/reports/p4-e0-r2q"),
                repository.resolve("build/reports/p4-e0-r2q-smoke/supervisor"));
    }

    private static void assertNoFormalOutput(Invocation invocation) {
        assertAll(
                () -> assertFalse(Files.exists(invocation.workRoot()),
                        "formal work root must not be established"),
                () -> assertFalse(Files.exists(invocation.firstCaseRoot()),
                        "the first formal child case must not be established"),
                () -> assertFalse(Files.exists(invocation.officialRoot()),
                        "official evidence root must not be established"),
                () -> assertFalse(Files.exists(invocation.failedEvidenceRoot()),
                        "failed evidence must not be emitted by a preflight rejection"),
                () -> assertFalse(Files.exists(invocation.staleEvidenceRoot()),
                        "stale evidence must not be emitted by a preflight rejection"));
    }

    private static void withProperties(
            String enabled, String budget, Path repository, Executable executable)
            throws Throwable {
        var oldEnabled = System.getProperty(ENABLED_PROPERTY);
        var oldBudget = System.getProperty(BUDGET_PROPERTY);
        var oldRepository = System.getProperty(REPOSITORY_PROPERTY);
        try {
            setOrClear(ENABLED_PROPERTY, enabled);
            setOrClear(BUDGET_PROPERTY, budget);
            System.setProperty(REPOSITORY_PROPERTY, repository.toString());
            executable.execute();
        } finally {
            setOrClear(ENABLED_PROPERTY, oldEnabled);
            setOrClear(BUDGET_PROPERTY, oldBudget);
            setOrClear(REPOSITORY_PROPERTY, oldRepository);
        }
    }

    private static void setOrClear(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static void git(Path repository, String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add("git");
        command.add("-C");
        command.add(repository.toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new ByteArrayOutputStream();
        try (var input = process.getInputStream()) {
            var buffer = new byte[4_096];
            var total = 0;
            for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                total = Math.addExact(total, read);
                if (total > MAXIMUM_GIT_OUTPUT_BYTES) {
                    process.destroyForcibly();
                    throw new IOException("test git output exceeds its bound");
                }
                output.write(buffer, 0, read);
            }
        }
        final boolean completed;
        try {
            completed = process.waitFor(GIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("test git command was interrupted", exception);
        }
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("test git command timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("test git command failed (exit " + process.exitValue()
                    + "): " + output.toString(StandardCharsets.UTF_8));
        }
    }

    private enum RepositoryMutation {
        NONE {
            @Override
            void apply(Path repository) {
                // The committed main branch and origin/main reference already match.
            }
        },
        DIRTY_TRACKED {
            @Override
            void apply(Path repository) throws Exception {
                Files.writeString(repository.resolve("tracked.txt"), "dirty tracked\n");
            }
        },
        DIRTY_INDEX {
            @Override
            void apply(Path repository) throws Exception {
                Files.writeString(repository.resolve("tracked.txt"), "dirty index\n");
                git(repository, "add", "tracked.txt");
            }
        },
        NONIGNORED_UNTRACKED {
            @Override
            void apply(Path repository) throws Exception {
                Files.writeString(repository.resolve("untracked.txt"), "untracked\n");
            }
        },
        DETACHED_HEAD {
            @Override
            void apply(Path repository) throws Exception {
                git(repository, "checkout", "--detach", "HEAD");
            }
        },
        WRONG_BRANCH {
            @Override
            void apply(Path repository) throws Exception {
                git(repository, "checkout", "-b", "not-main");
            }
        },
        ORIGIN_MAIN_MISMATCH {
            @Override
            void apply(Path repository) throws Exception {
                Files.writeString(repository.resolve("tracked.txt"), "new committed head\n");
                git(repository, "add", "tracked.txt");
                git(repository, "commit", "-m", "head after locked origin/main");
            }
        };

        abstract void apply(Path repository) throws Exception;
    }

    private record Invocation(
            Path repository,
            Path workRoot,
            Path officialRoot,
            Path smokeRoot) {
        String[] arguments(String command, String... commandArguments) {
            var values = new ArrayList<String>();
            values.add(command);
            values.addAll(List.of(commandArguments));
            values.add(workRoot.toString());
            values.add(officialRoot.toString());
            values.add(smokeRoot.toString());
            return values.toArray(String[]::new);
        }

        Path firstCaseRoot() {
            return workRoot.resolve("cases/00");
        }

        Path failedEvidenceRoot() {
            return workRoot.resolveSibling("failed-evidence");
        }

        Path staleEvidenceRoot() {
            return workRoot.resolveSibling("stale-evidence");
        }
    }
}
