package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class P3D3ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final String STORE_SOURCE_PREFIX =
            "src/main/java/com/yo1no/gramarye/magic/definition/store/";
    private static final Path STORE_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java");
    private static final Pattern THREAD_FAMILY_IDENTIFIER = Pattern.compile(
            "(?i)\\b(?=[A-Za-z_$])"
                    + "(?=[A-Za-z0-9_$]*(?:thread|future|parallel|concurrent))"
                    + "[A-Za-z_$][A-Za-z0-9_$]*\\b");
    private static final Pattern BACKGROUND_CANDIDATE_IDENTIFIER = Pattern.compile(
            "\\b(?:Runnable|Callable|Worker|Callback|Queue|Deque|Scheduler|Task|"
                    + "runAfterDelay|execute|submit|schedule|scheduleAtFixedRate|"
                    + "scheduleWithFixedDelay|runAsync|supplyAsync|thenApplyAsync|"
                    + "thenRunAsync|thenComposeAsync|thenAcceptAsync|handleAsync|"
                    + "whenCompleteAsync|post|enqueue|dispatch|invokeLater|"
                    + "background|backgroundCallback)\\b");
    private static final List<ForbiddenThreadSurface> FORBIDDEN_THREAD_SURFACES = List.of(
            new ForbiddenThreadSurface("THREAD_CREATION",
                    "\\bnew\\s+(?:java\\.lang\\.)?Thread\\s*\\("),
            new ForbiddenThreadSurface("THREAD_LIFECYCLE",
                    "\\b(?:Thread|[A-Za-z_$][A-Za-z0-9_$]*(?:Thread|thread)"
                            + "[A-Za-z0-9_$]*)\\s*\\.\\s*(?:start|run|join|sleep|yield|"
                            + "interrupt|interrupted|isInterrupted|isAlive|setDaemon|setPriority|"
                            + "setUncaughtExceptionHandler|getAllStackTraces|enumerate)\\s*\\("),
            new ForbiddenThreadSurface("THREAD_OWNERSHIP_TYPE",
                    "\\b(?:ThreadLocal|InheritableThreadLocal|ThreadGroup|ThreadFactory)\\b"),
            new ForbiddenThreadSurface("BACKGROUND_EXECUTION_TYPE",
                    "\\b(?:Executor|Executors|ExecutorService|ScheduledExecutorService|Future|"
                            + "FutureTask|CompletableFuture|CompletionStage|ForkJoinPool|"
                            + "ForkJoinTask|Timer|TimerTask|Cleaner|Callable)\\b"),
            new ForbiddenThreadSurface("BACKGROUND_SUBMISSION",
                    "\\.\\s*(?:execute|submit|invokeAll|invokeAny|schedule|scheduleAtFixedRate|"
                            + "scheduleWithFixedDelay|runAsync|supplyAsync|thenApplyAsync|"
                            + "thenRunAsync|thenComposeAsync|thenAcceptAsync|handleAsync|"
                            + "whenCompleteAsync|post|enqueue|dispatch|invokeLater)\\s*\\("),
            new ForbiddenThreadSurface("BACKGROUND_CALLBACK_SEAM",
                    "\\b(?:background|backgroundCallback|asyncCallback|workerCallback)\\b|"
                            + "\\b(?:Runnable|Callable)\\s+[A-Za-z_$][A-Za-z0-9_$]*"
                            + "(?:callback|worker|background|task)[A-Za-z0-9_$]*\\b"),
            new ForbiddenThreadSurface("PARALLEL_EXECUTION",
                    "\\bparallelStream\\s*\\(|\\.\\s*parallel\\s*\\("),
            new ForbiddenThreadSurface("THREAD_ARRAY_OR_COLLECTION_RETENTION",
                    "\\bThread\\s*\\[|\\b(?:Collection|List|Set|Map|Queue|Deque|Optional)"
                            + "\\s*<[^;{}]*\\bThread\\b"),
            new ForbiddenThreadSurface("THREAD_RETURN",
                    "\\bThread\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\("),
            new ForbiddenThreadSurface("REFLECTIVE_THREAD_DISCOVERY",
                    "\\b(?:Class\\s*\\.\\s*forName|ClassLoader\\s*\\.\\s*loadClass|"
                            + "Thread\\s*\\.\\s*class|ManagementFactory\\s*\\.\\s*"
                            + "getThreadMXBean)\\b"));

    private enum ThreadRole {
        AUTHORIZED_C1_TYPE_NAME,
        AUTHORIZED_C1_OBSERVED_THREAD_ID,
        AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID,
        AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY,
        UNRELATED_IDENTIFIER_SUBSTRING
    }

    private record ThreadSourceAuthority(
            String path,
            Set<String> binaryOwners,
            Set<String> methodDescriptors,
            Set<String> resolvedSymbols,
            Set<ThreadRole> roles,
            Set<String> authorityFamilies,
            int executableCandidateCount,
            String normalizedExpressionSha256,
            String sourceSha256) {
        private ThreadSourceAuthority {
            assertTrue(path.startsWith(STORE_SOURCE_PREFIX));
            assertFalse(binaryOwners.isEmpty());
            assertFalse(methodDescriptors.isEmpty());
            assertFalse(resolvedSymbols.isEmpty());
            assertFalse(roles.isEmpty());
            assertFalse(authorityFamilies.isEmpty());
            assertTrue(executableCandidateCount > 0);
            assertEquals(64, normalizedExpressionSha256.length());
            assertEquals(64, sourceSha256.length());
        }
    }

    private record ForbiddenThreadSurface(String role, Pattern pattern) {
        private ForbiddenThreadSurface(String role, String expression) {
            this(role, Pattern.compile(expression, Pattern.DOTALL));
        }
    }

    private record ExactThreadCoordinate(
            String path,
            String binaryOwner,
            String methodDescriptor,
            String resolvedSymbol,
            ThreadRole role,
            String authorityFamily,
            String normalizedExpressionSha256,
            String candidate,
            long sourceOffset) {
        private ExactThreadCoordinate {
            assertTrue(path.startsWith(STORE_SOURCE_PREFIX));
            assertFalse(binaryOwner.isBlank());
            assertFalse(methodDescriptor.isBlank());
            assertFalse(resolvedSymbol.isBlank());
            assertFalse(authorityFamily.isBlank());
            assertEquals(64, normalizedExpressionSha256.length());
            assertFalse(candidate.isBlank());
            assertTrue(sourceOffset >= 0);
        }

        private String exactKey() {
            return String.join("\t", path, binaryOwner, methodDescriptor, resolvedSymbol,
                    role.name(), authorityFamily, normalizedExpressionSha256);
        }
    }

    private record SemanticThreadProjection(
            List<ExactThreadCoordinate> coordinates,
            List<String> diagnostics) {}

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }

    private static final List<ThreadSourceAuthority> THREAD_SOURCE_AUTHORITIES = List.of(
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "ControlledSkillPin.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.ControlledSkillPin"),
                    Set.of("reference()LSkillReference;;isClosed()Z;close()V;"
                            + "requireServerThread()V"),
                    Set.of("SkillDefinitionStoreService.requireServerThread(MinecraftServer)V",
                            "MinecraftServer.isSameThread()Z"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4_EXISTING_SERVER_THREAD_CONFINEMENT"),
                    5,
                    "585fea987536ee7054e4ba174816685fc3a2586143cb87451634a16c073bb8a6",
                    "861b02b8eae508c9512133c991bdde24af1224b7eca2299d34e03773b1db14bc"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "P4E1AuditedCapture.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.P4E1AuditedCapture",
                            "com.yo1no.gramarye.magic.definition.store.P4E1AuditedCapture$Transfer"),
                    Set.of("discardAfterResultPublicationFailure()V",
                            "requireExactBinding(LP4E1GroupedStoreAudit;LTransfer;)V",
                            "moveReferences()LTransfer;",
                            "Transfer.callChainCurrent(LSkillRetentionRootAuditService;)Z",
                            "Transfer.clearReferences()V",
                            "Transfer.requireActive(LP4E1GroupedStoreAudit;)V"),
                    Set.of("java.lang.Thread creation binding",
                            "Thread.currentThread()Ljava/lang/Thread;",
                            "MinecraftServer.isSameThread()Z"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4E1_CAPTURE_BINDING_MOVE_CLEAR"),
                    22,
                    "2a5e4cadfd72d4c799197ca806419ac1332a61d0cd9146eb463c6de4b35dee79",
                    "0e59b72fcd3fd357a0a57c8f36fe111a7cfb119747762ac352b3d3be38a4a8f6"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "P4E1GlobalSourceCapture.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture",
                            "com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture$Captured",
                            "com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture$Claimed"),
                    Set.of("claim(LP4E1GroupedStoreAudit;LProductThreadPrecondition$Decision;)LClaimed;",
                            "requireActive(LP4E1GroupedStoreAudit;"
                                    + "LProductThreadPrecondition$Decision;)V",
                            "capture(LMinecraftServer;LSkillDefinitionStoreService;"
                                    + "LPlayerSkillAttachmentService;LP4E1GroupedStoreAudit;)"
                                    + "LCaptureResult;",
                            "Captured.discard()V", "Captured.summary()LSummary;",
                            "Captured.clearReferences()V", "Claimed.clearReferences()V",
                            "Claimed.moveToAudited()LP4E1AuditedCapture;"),
                    Set.of("java.lang.Thread creation binding",
                            "ProductThreadPrecondition$Decision same-instance handoff"),
                    Set.of(ThreadRole.AUTHORIZED_C1_TYPE_NAME,
                            ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("C1_CONTROL_2_CLAIM_HANDOFF", "P4E1_CAPTURE_BINDING_MOVE_CLEAR"),
                    35,
                    "b669f096087c185c8dd32c4099c44806543252337754ac1f3f99eb23530cb3bc",
                    "6b5b3d2f9297f0744c948245085b2a4f06e27cece18c9c3fb4b90d3f7aa603e1"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "P4E1GroupedStoreAudit.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.P4E1GroupedStoreAudit"),
                    Set.of("(LP4E1GlobalSourceCapture$Captured;)LResult;",
                            "(LP4E1GlobalSourceCapture$Captured;J)LResult;",
                            "(Lnet/minecraft/server/MinecraftServer;Ljava/lang/Thread;I)V",
                            "(Lnet/minecraft/server/MinecraftServer;Ljava/lang/Thread;I;"
                                    + "LProductThreadPrecondition$Decision;)V",
                            "(Lnet/minecraft/server/MinecraftServer;Ljava/lang/Thread;)V"),
                    Set.of("Thread.currentThread()Ljava/lang/Thread;",
                            "Thread.threadId()J",
                            "MinecraftServer.getRunningThread()Ljava/lang/Thread;",
                            "ProductThreadPrecondition.classify(JJ)LDecision;",
                            "java.lang.Thread creation binding"),
                    Set.of(ThreadRole.AUTHORIZED_C1_TYPE_NAME,
                            ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID,
                            ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID,
                            ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("C1_CONTROL_2", "P4E1_GROUPED_AUDIT_BINDING"),
                    35,
                    "6127ef685060fd5c06ba42766d2f2aa1e392dc732ce4f9214dbec6d256d12e71",
                    "020ac14af9961fead03c8142370d89fe3a61009e679ac1c6a8cb8a55026f39eb"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "P4E1IntegratedSnapshotTraversal.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.P4E1IntegratedSnapshotTraversal",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "P4E1IntegratedSnapshotTraversal$SnapshotAccess",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "P4E1IntegratedSnapshotTraversal$MinecraftSnapshotAccess"),
                    Set.of("capture(LMinecraftServer;LP4E1AuditBudget;)LSelection;",
                            "capture(LSnapshotAccess;LP4E1AuditBudget;)LSelection;",
                            "capture(LSnapshotAccess;LP4E1AuditBudget;Z)LSelection;",
                            "SnapshotAccess.isSameThread()Z",
                            "Disk.isCurrent(LSnapshotAccess;)Z",
                            "Integrated.freshnessFailure(LSnapshotAccess;)LOptional;",
                            "MinecraftSnapshotAccess.isSameThread()Z"),
                    Set.of("MinecraftServer.isSameThread()Z"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4E1_INTEGRATED_SNAPSHOT_CURRENTNESS"),
                    6,
                    "ef145c7b95584cb147dce8144d6cc530a84b3f11e79189b9db823177f368b38a",
                    "1523130dded0f1118db4cd2c738072f9c44ca139ade598a108293b805c91559a"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "P4E2OnlineReconciliationCoordinator.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store."
                            + "P4E2OnlineReconciliationCoordinator"),
                    Set.of("reconcileAfterRecovery(LServerPlayer;LRecoveryContinuation;"
                                    + "LRecoveryKind;IILOptional;)V",
                            "reconcile(LServerPlayer;LRecoveryContinuation;LRecoveryKind;"
                                    + "IILOptional;)LP4E2ReconciliationResult;"),
                    Set.of("SkillDefinitionStoreService.requireServerThread(MinecraftServer)V"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4E2_SERVER_THREAD_CONFINEMENT"),
                    2,
                    "6dc6386d7fddb701de0fb69b0d62f3b0b11e73024f57c81503ec308b9549fb88",
                    "0e6aa8e5291b52b3044b55bc45d5059a290faea0b3b57cd2c56c207c9dd98c38"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "ProductThreadPrecondition.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.ProductThreadPrecondition",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "ProductThreadPrecondition$Decision"),
                    Set.of("()V", "(JJ)Lcom/yo1no/gramarye/magic/definition/store/"
                            + "ProductThreadPrecondition$Decision;"),
                    Set.of("ProductThreadPrecondition exact package-private final type",
                            "expectedLogicThreadId:J", "observedThreadId:J",
                            "Decision.ALLOWED", "Decision.WRONG_THREAD"),
                    Set.of(ThreadRole.AUTHORIZED_C1_TYPE_NAME,
                            ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID,
                            ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID),
                    Set.of("C1_SHARED_CLASSIFIER"),
                    9,
                    "956bdb9028311179ed1914d91406301361ec90bd46248096bb545365055000a8",
                    "57a632e9419207e2b7cc96a7ddd614ee9a33f238dfeaa9909e260e8f4085e119"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "SkillDefinitionStoreService.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService"),
                    Set.of("(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/magic/"
                                    + "api/id/SkillId;)Lcom/yo1no/gramarye/magic/definition/store/"
                                    + "SkillSubsystemResult;",
                            "(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/magic/"
                                    + "api/id/SkillId;J)Lcom/yo1no/gramarye/magic/definition/store/"
                                    + "SkillSubsystemResult;",
                            "(Lnet/minecraft/server/MinecraftServer;J)V",
                            "(Lnet/minecraft/server/MinecraftServer;)V"),
                    Set.of("Thread.currentThread()Ljava/lang/Thread;", "Thread.threadId()J",
                            "MinecraftServer.getRunningThread()Ljava/lang/Thread;",
                            "ProductThreadPrecondition.classify(JJ)LDecision;",
                            "MinecraftServer.isSameThread()Z"),
                    Set.of(ThreadRole.AUTHORIZED_C1_TYPE_NAME,
                            ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID,
                            ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID,
                            ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("C1_CONTROL_1", "P4_EXISTING_SERVER_THREAD_CONFINEMENT"),
                    28,
                    "1aaf829ecdcd321a9a72e4ea09b20644fd45355e092468e411bc49607089c04c",
                    "c7876fccd4831f99e208f04e31d260b8e969f5d269200442faea58b1d3d73450"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "SkillDefinitionStoreSubmissionPort.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store."
                            + "SkillDefinitionStoreSubmissionPort"),
                    Set.of("observeSubmissionAuthority(LMinecraftServer;LSkillId;LSkillOwnerId;)"
                                    + "LAuthoritySnapshot;",
                            "bootstrapJournal(LMinecraftServer;)LBootstrapResult;",
                            "journalStatus(LMinecraftServer;)LJournalStatus;",
                            "journalRoots(LMinecraftServer;)LJournalRootProjection;",
                            "observeP4E1Journal(LMinecraftServer;LStoreReadyWitness;)LResult;",
                            "isP4E1JournalWitnessCurrent(LMinecraftServer;LStoreReadyWitness;"
                                    + "LReady;)Z",
                            "observePendingRecovery(LMinecraftServer;LSkillOwnerId;)"
                                    + "LPendingRecoveryProjection;",
                            "prepareSubmissionCommit(LMinecraftServer;LSkillSubmissionPlan;"
                                    + "LSkillQuota;LPreparedPlayerSkillTransition;)"
                                    + "LSubmissionPreparationResult;",
                            "commitPreparedSubmission(LMinecraftServer;"
                                    + "LPreparedStoreSubmissionCommit;)LSubmissionCommitResult;",
                            "prepareJournalPrefixClear(LMinecraftServer;LSkillOwnerId;LSkillId;I"
                                    + "LSkillReference;)LJournalClearPreparationResult;",
                            "commitPreparedJournalClear(LMinecraftServer;"
                                    + "LPreparedJournalPrefixClear;)LJournalClearCommitResult;"),
                    Set.of("SkillDefinitionStoreService.requireServerThread(MinecraftServer)V"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4_STORE_SUBMISSION_THREAD_CONFINEMENT"),
                    11,
                    "be131adb4af55f2aed78307bb1d491ce815f2168424685efe244f43b5ce080ba",
                    "c805797577f316e520c7b08b8c6cbb59dfd93b800aea52a638136333673a3877"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "SkillRetentionRootAuditService.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store.SkillRetentionRootAuditService",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "SkillRetentionRootAuditService$IndexLifecycle",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "SkillRetentionRootAuditService$ReservationScope",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "SkillRetentionRootAuditService$PermitBinding",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "SkillRetentionRootAuditService$PermitCell",
                            "com.yo1no.gramarye.magic.definition.store."
                                    + "SkillRetentionRootAuditService$LeaseCell"),
                    Set.of("PermitBinding(Ljava/lang/Object;Ljava/lang/Object;"
                                    + "Ljava/lang/Thread;IJ)V",
                            "PermitBinding.requireThread(Ljava/lang/Thread;)V",
                            "PermitCell(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;IJ"
                                    + "LP4E1FinalFreshness$FreshnessSeal;)V",
                            "PermitCell.requireThread(Ljava/lang/Thread;)V",
                            "LeaseCell(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;I"
                                    + "LCallChainCurrentness;LIndexLifecycle;JLIndexedBacking;"
                                    + "LCompleteIndex;LIncompleteState;)V",
                            "LeaseCell.isCurrent()Z", "LeaseCell.clear()V",
                            "ReservationScope.prepareComplete(Ljava/lang/Object;Ljava/lang/Object;"
                                    + "LP4E1RawClaimBuffer;[LPublicationSource;Ljava/lang/Thread;I"
                                    + "LP4E1FinalFreshness$FreshnessSeal;LAuditSummary;)"
                                    + "LPreparedComplete;"),
                    Set.of("java.lang.Thread retained P4E1 binding",
                            "Thread.currentThread()Ljava/lang/Thread;",
                            "MinecraftServer.isSameThread()Z"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4E1_COMPLETE_PERMIT_LEASE_BINDING"),
                    44,
                    "2754b39e351d7043533b3e33edea0b77da271fd38adc0da983204e26de5355de",
                    "67c9e2f89a945621a98ea72b352102cb2ac1470b697360ca190ec0ff95e3744f"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "SkillSavedDataLifecycleGameTests.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store."
                            + "SkillSavedDataLifecycleGameTests"),
                    Set.of("(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
                            "(Lnet/minecraft/gametest/framework/GameTestHelper;"
                                    + "Lnet/minecraft/server/MinecraftServer;"
                                    + "Lnet/minecraft/world/level/storage/DimensionDataStorage;"
                                    + "Lcom/yo1no/gramarye/magic/definition/store/"
                                    + "GramaryeSkillSavedData;Lnet/minecraft/world/level/saveddata/"
                                    + "SavedData$Factory;)V",
                            "(Ljava/lang/String;Ljava/lang/Runnable;)Ljava/lang/RuntimeException;"),
                    Set.of("MinecraftServer.isSameThread()Z",
                            "MinecraftServer.getRunningThread()Ljava/lang/Thread;",
                            "Thread.threadId()J",
                            "ProductThreadPrecondition.classify(JJ)LDecision;",
                            "AtomicInteger synchronous counter"),
                    Set.of(ThreadRole.AUTHORIZED_C1_TYPE_NAME,
                            ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID,
                            ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID,
                            ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY,
                            ThreadRole.UNRELATED_IDENTIFIER_SUBSTRING),
                    Set.of("C1_GAMETEST_NEGATIVE_CONTROL",
                            "P4E1_SYNCHRONOUS_GAMETEST_PRECONDITION",
                            "SYNCHRONOUS_ATOMIC_COUNTER"),
                    31,
                    "3d76c6add7da983645a0e303a96ef34917df3698ad94124db6bbdb90616b6caa",
                    "8047b9f4daa242eeb102d70bf6fa6f608693d0c3cba219bf74413d8b34d27c2b"),
            new ThreadSourceAuthority(
                    STORE_SOURCE_PREFIX + "SkillSubmissionRecoveryGameTests.java",
                    Set.of("com.yo1no.gramarye.magic.definition.store."
                            + "SkillSubmissionRecoveryGameTests"),
                    Set.of("runRecoveryScenario(LGameTestHelper;Ljava/util/UUID;LSkillId;"
                            + "LPersistedPosition;)V"),
                    Set.of("MinecraftServer.isSameThread()Z"),
                    Set.of(ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY),
                    Set.of("P4_RECOVERY_GAMETEST_THREAD_PRECONDITION"),
                    1,
                    "8f5548e0d724bf902b3f6597f386fd2c73ec202cbd54a5bc6749d5562ce566dc",
                    "3e8d4979517449af96df542792a3d57c11f271dcb9cf3ac29a479879b601db13"),
            unrelatedFutureAuthority(
                    "SkillSavedDataCarrierMigrationFailure.java",
                    "com.yo1no.gramarye.magic.definition.store."
                            + "SkillSavedDataCarrierMigrationFailure$Code",
                    "<clinit>()V",
                    "78383932d5278fb6449a31b09c93f291817d24e501200371d1db62d3329f96b3",
                    "28a0a492bb527383bcafb87e14cdab3c5e7a9a1a5f63e5bdb4494230b8d930c5"),
            unrelatedFutureAuthority(
                    "SkillSavedDataCarrierMigrator.java",
                    "com.yo1no.gramarye.magic.definition.store.SkillSavedDataCarrierMigrator",
                    "migrateTo(LTokenizedSavedDataCarrierSnapshot;"
                            + "LSkillSavedDataCarrierMigrationPlan;I)"
                            + "LSkillSavedDataCarrierMigrationResult;",
                    "50803c0938c892b58556fab571f684abd154c00b15f234fa922652603af48338",
                    "481d69f5848f8b05e6604d42cc71646ff12685c63fee5bf5f5814ea1e6970da7"),
            unrelatedFutureAuthority(
                    "SkillSavedDataCarrierPersistenceBridge.java",
                    "com.yo1no.gramarye.magic.definition.store."
                            + "SkillSavedDataCarrierPersistenceBridge",
                    "loadDecompressed(Ljava/io/InputStream;Ljava/util/Optional;LStoreLoader;"
                            + "LCarrierRebuilder;)LSkillSavedDataCarrierLoadResult;",
                    "f7566f54042490b992c80d683d6cf3f449f9adfb62a94cc9fb3295c65640151a",
                    "405d10aadddd6c51b4cc506c4236d99a7332dfa04a9abfae946b3b8dc724e272"),
            unrelatedFutureAuthority(
                    "StorePersistenceMigrationFailure.java",
                    "com.yo1no.gramarye.magic.definition.store."
                            + "StorePersistenceMigrationFailure$Code",
                    "<clinit>()V",
                    "79b7323ee339d5f5917a7d970e2713d41c5aad65663eec5f4670c34b14f8763b",
                    "2c21d4ce8852cefae2a262cb1e840ccb217815c4cc54e5a41a0073137ebc39b9"),
            unrelatedFutureAuthority(
                    "StorePersistenceMigrator.java",
                    "com.yo1no.gramarye.magic.definition.store.StorePersistenceMigrator",
                    "migrateTo(Lnet/minecraft/nbt/CompoundTag;LStorePersistenceMigrationPlan;I)"
                            + "LStorePersistenceMigrationResult;",
                    "bf85edd6ee825b6423f558f4be484a2a6ecf9c8b6134d827c952a06d1d1c888d",
                    "8ac6a6155ab76642671ddc32e149ae6205e5b5feda129c33b8785b6da57ebbef"));
    @Test
    void storeHasExactlySevenReviewedDomainMethodsAndTwoTruthStateMaps() throws Exception {
        var publicMethods = Arrays.stream(SkillDefinitionStore.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        var constructors = SkillDefinitionStore.class.getDeclaredConstructors();

        assertEquals(Set.of(
                        "find", "latestReference", "ownerOf", "committedSkillCount",
                        "commit", "pin", "reclaim"),
                publicMethods.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(7, publicMethods.size());
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("find", SkillReference.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("find", SkillReference.class),
                Optional.class,
                SkillDocument.class);
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("latestReference", SkillId.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("latestReference", SkillId.class),
                Optional.class,
                SkillReference.class);
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("ownerOf", SkillId.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("ownerOf", SkillId.class),
                Optional.class,
                SkillOwnerId.class);
        assertEquals(int.class,
                SkillDefinitionStore.class.getMethod("committedSkillCount", SkillOwnerId.class)
                        .getReturnType());
        assertEquals(SkillStoreCommitResult.class,
                SkillDefinitionStore.class.getMethod(
                                "commit", SkillSubmissionPlan.class, SkillQuota.class)
                        .getReturnType());
        assertEquals(Optional.class,
                SkillDefinitionStore.class.getMethod("pin", SkillReference.class).getReturnType());
        assertGenericReturn(
                SkillDefinitionStore.class.getMethod("pin", SkillReference.class),
                Optional.class,
                SkillRevisionPin.class);
        assertEquals(SkillReclaimResult.class,
                SkillDefinitionStore.class.getMethod(
                                "reclaim", SkillRetentionRootSnapshot.class)
                        .getReturnType());
        assertTrue(publicMethods.stream().noneMatch(method -> Modifier.isSynchronized(
                method.getModifiers())));
        assertTrue(publicMethods.stream().noneMatch(method -> Modifier.isStatic(
                method.getModifiers())));

        assertEquals(2, constructors.length);
        assertEquals(1, Arrays.stream(constructors)
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers())
                        && constructor.getParameterCount() == 0)
                .count());
        assertPackagePrivate(SkillDefinitionStore.class.getDeclaredMethod("snapshot").getModifiers());
        var restore = SkillDefinitionStore.class.getDeclaredMethod(
                "restore", SkillDefinitionStoreSnapshot.class);
        assertPackagePrivate(restore.getModifiers());
        assertTrue(Modifier.isStatic(restore.getModifiers()));
        assertEquals(SkillDefinitionStoreRestoreResult.class, restore.getReturnType());

        var fields = Arrays.stream(SkillDefinitionStore.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        assertEquals(Set.of("histories", "activePinCounts"),
                fields.stream().map(field -> field.getName()).collect(Collectors.toSet()));
        assertMapField(
                SkillDefinitionStore.class.getDeclaredField("histories"),
                SkillId.class,
                StoredSkillHistory.class);
        assertMapField(
                SkillDefinitionStore.class.getDeclaredField("activePinCounts"),
                SkillReference.class,
                Integer.class);
    }

    @Test
    void storedHistoryAddsOnlyTheReviewedPackagePrivateRetainHelper() throws Exception {
        var fields = Arrays.stream(StoredSkillHistory.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var retain = StoredSkillHistory.class.getDeclaredMethod("retainRevisions", Set.class);
        var parameter = assertInstanceOf(
                ParameterizedType.class, retain.getGenericParameterTypes()[0]);

        assertEquals(Set.of("owner", "revisions"),
                fields.stream().map(field -> field.getName()).collect(Collectors.toSet()));
        var owner = StoredSkillHistory.class.getDeclaredField("owner");
        assertEquals(SkillOwnerId.class, owner.getType());
        assertTrue(Modifier.isPrivate(owner.getModifiers()));
        assertTrue(Modifier.isFinal(owner.getModifiers()));
        var revisions = StoredSkillHistory.class.getDeclaredField("revisions");
        assertEquals(NavigableMap.class, revisions.getType());
        var revisionMap = assertInstanceOf(ParameterizedType.class, revisions.getGenericType());
        assertEquals(NavigableMap.class, revisionMap.getRawType());
        assertEquals(List.of(SkillRevision.class, SkillDocument.class),
                Arrays.asList(revisionMap.getActualTypeArguments()));
        assertTrue(Modifier.isPrivate(revisions.getModifiers()));
        assertTrue(Modifier.isFinal(revisions.getModifiers()));
        assertFalse(Modifier.isPublic(retain.getModifiers()));
        assertFalse(Modifier.isPrivate(retain.getModifiers()));
        assertFalse(Modifier.isProtected(retain.getModifiers()));
        assertFalse(Modifier.isStatic(retain.getModifiers()));
        assertEquals(StoredSkillHistory.class, retain.getReturnType());
        assertEquals(Set.class, parameter.getRawType());
        assertEquals(List.of(SkillRevision.class),
                Arrays.asList(parameter.getActualTypeArguments()));
        assertTrue(Arrays.stream(StoredSkillHistory.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("reclaim")));
    }

    @Test
    void rootSnapshotHasExactSealedShapeAndNoFactoryBypass() throws Exception {
        assertTrue(Modifier.isPublic(SkillRetentionRootSnapshot.class.getModifiers()));
        assertTrue(SkillRetentionRootSnapshot.class.isSealed());
        assertEquals(Set.of(
                        SkillRetentionRootSnapshot.Complete.class,
                        SkillRetentionRootSnapshot.Incomplete.class,
                        SkillRetentionRootSnapshot.Truncated.class,
                        SkillRetentionRootSnapshot.OverLimit.class),
                Set.of(SkillRetentionRootSnapshot.class.getPermittedSubclasses()));

        var factory = SkillRetentionRootSnapshot.class.getDeclaredMethod(
                "fromCompleteRoots", Iterable.class);
        var factoryInput = assertInstanceOf(
                ParameterizedType.class, factory.getGenericParameterTypes()[0]);
        assertTrue(Modifier.isPublic(factory.getModifiers()));
        assertTrue(Modifier.isStatic(factory.getModifiers()));
        assertEquals(SkillRetentionRootSnapshot.class, factory.getReturnType());
        assertEquals(Iterable.class, factoryInput.getRawType());
        assertEquals(List.of(SkillReference.class),
                Arrays.asList(factoryInput.getActualTypeArguments()));
        assertEquals(1, Arrays.stream(SkillRetentionRootSnapshot.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .count());

        var complete = SkillRetentionRootSnapshot.Complete.class;
        var constructors = complete.getDeclaredConstructors();
        var fields = complete.getDeclaredFields();
        assertTrue(Modifier.isPublic(complete.getModifiers()));
        assertTrue(Modifier.isFinal(complete.getModifiers()));
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(List.of(List.class), Arrays.asList(constructors[0].getParameterTypes()));
        var constructorInput = assertInstanceOf(
                ParameterizedType.class, constructors[0].getGenericParameterTypes()[0]);
        assertEquals(List.class, constructorInput.getRawType());
        assertEquals(List.of(SkillReference.class),
                Arrays.asList(constructorInput.getActualTypeArguments()));
        assertEquals(1, fields.length);
        assertEquals("roots", fields[0].getName());
        assertListField(fields[0], SkillReference.class);
        assertEquals(Set.of("roots", "toString"),
                Arrays.stream(complete.getDeclaredMethods())
                        .map(method -> method.getName())
                        .collect(Collectors.toSet()));
        assertEquals(2, complete.getDeclaredMethods().length);
        var roots = complete.getDeclaredMethod("roots");
        assertTrue(Modifier.isPublic(roots.getModifiers()));
        assertEquals(List.class, roots.getReturnType());
        var rootsReturn = assertInstanceOf(ParameterizedType.class, roots.getGenericReturnType());
        assertEquals(List.of(SkillReference.class),
                Arrays.asList(rootsReturn.getActualTypeArguments()));
        assertEquals(String.class, complete.getDeclaredMethod("toString").getReturnType());
        assertTrue(Arrays.stream(complete.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("equals")
                        || method.getName().equals("hashCode")));

        assertEquals(List.of("observedAtLeast", "maximum"),
                componentNames(SkillRetentionRootSnapshot.OverLimit.class));
        assertEquals(List.of(int.class, int.class),
                componentTypes(SkillRetentionRootSnapshot.OverLimit.class));
        assertEquals(
                List.of(SkillRetentionRootSnapshot.Incomplete.INSTANCE),
                Arrays.asList(SkillRetentionRootSnapshot.Incomplete.values()));
        assertEquals(
                List.of(SkillRetentionRootSnapshot.Truncated.INSTANCE),
                Arrays.asList(SkillRetentionRootSnapshot.Truncated.values()));
    }

    @Test
    void reclaimFailureResultAndReportHaveOnlyBoundedTypedComponents() {
        assertTrue(Modifier.isPublic(SkillReclaimFailure.class.getModifiers()));
        assertTrue(SkillReclaimFailure.class.isSealed());
        assertEquals(Set.of(
                        SkillReclaimFailure.IncompleteRootSnapshot.class,
                        SkillReclaimFailure.TruncatedRootSnapshot.class,
                        SkillReclaimFailure.RootCapacityExceeded.class,
                        SkillReclaimFailure.MissingExternalRoot.class),
                Set.of(SkillReclaimFailure.class.getPermittedSubclasses()));
        assertEquals(
                List.of(int.class, int.class),
                componentTypes(SkillReclaimFailure.RootCapacityExceeded.class));
        assertEquals(List.of("observedAtLeast", "maximum"),
                componentNames(SkillReclaimFailure.RootCapacityExceeded.class));
        assertEquals(List.of("reference"),
                componentNames(SkillReclaimFailure.MissingExternalRoot.class));
        assertEquals(List.of(SkillReference.class),
                componentTypes(SkillReclaimFailure.MissingExternalRoot.class));
        assertEquals(
                List.of(SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE),
                Arrays.asList(SkillReclaimFailure.IncompleteRootSnapshot.values()));
        assertEquals(
                List.of(SkillReclaimFailure.TruncatedRootSnapshot.INSTANCE),
                Arrays.asList(SkillReclaimFailure.TruncatedRootSnapshot.values()));

        assertTrue(Modifier.isPublic(SkillReclaimReport.class.getModifiers()));
        assertTrue(Modifier.isFinal(SkillReclaimReport.class.getModifiers()));
        assertEquals(
                List.of("historiesScanned", "revisionsScanned", "historiesChanged",
                        "revisionsReclaimed"),
                componentNames(SkillReclaimReport.class));
        assertEquals(List.of(int.class, int.class, int.class, int.class),
                componentTypes(SkillReclaimReport.class));

        assertTrue(Modifier.isPublic(SkillReclaimResult.class.getModifiers()));
        assertTrue(SkillReclaimResult.class.isSealed());
        assertEquals(Set.of(SkillReclaimResult.Completed.class, SkillReclaimResult.Rejected.class),
                Set.of(SkillReclaimResult.class.getPermittedSubclasses()));
        assertEquals(List.of("report"), componentNames(SkillReclaimResult.Completed.class));
        assertEquals(List.of(SkillReclaimReport.class),
                componentTypes(SkillReclaimResult.Completed.class));
        assertEquals(List.of("failure"), componentNames(SkillReclaimResult.Rejected.class));
        assertEquals(List.of(SkillReclaimFailure.class),
                componentTypes(SkillReclaimResult.Rejected.class));

        var records = List.of(
                SkillReclaimFailure.RootCapacityExceeded.class,
                SkillReclaimFailure.MissingExternalRoot.class,
                SkillReclaimReport.class,
                SkillReclaimResult.Completed.class,
                SkillReclaimResult.Rejected.class);
        assertTrue(records.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .noneMatch(component -> component.getType() == String.class
                        || Throwable.class.isAssignableFrom(component.getType())
                        || SkillDocument.class.isAssignableFrom(component.getType())
                        || SkillOwnerId.class.isAssignableFrom(component.getType())
                        || Map.class.isAssignableFrom(component.getType())
                        || List.class.isAssignableFrom(component.getType())));
    }

    @Test
    void persistenceSnapshotShapeStillContainsNoPinRootOrReportState() {
        assertEquals(Set.of("histories"), declaredFieldNames(SkillDefinitionStoreSnapshot.class));
        assertEquals(Set.of("skillId", "owner", "revisions"),
                declaredFieldNames(SkillHistorySnapshot.class));
        assertEquals(Set.of("revision", "document"),
                declaredFieldNames(SkillRevisionSnapshot.class));
        assertTrue(List.of(
                        SkillDefinitionStoreSnapshot.class,
                        SkillHistorySnapshot.class,
                        SkillRevisionSnapshot.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .noneMatch(field -> field.getName().toLowerCase().contains("pin")
                        || field.getName().toLowerCase().contains("root")
                        || field.getName().toLowerCase().contains("reclaim")
                        || field.getType() == SkillReclaimReport.class));
    }

    @Test
    void reclaimConstructionPointsAreCentralizedAtTheStoreBoundary() throws Exception {
        var sources = productionSources();
        var constructedTypes = List.of(
                "SkillReclaimResult.Completed",
                "SkillReclaimResult.Rejected",
                "SkillReclaimFailure.RootCapacityExceeded",
                "SkillReclaimFailure.MissingExternalRoot",
                "SkillReclaimReport");
        for (var constructedType : constructedTypes) {
            var sites = sources.stream()
                    .filter(path -> containsConstruction(path, constructedType))
                    .toList();
            assertEquals(List.of(STORE_SOURCE), sites, constructedType);
            assertEquals(1, sources.stream()
                    .mapToInt(path -> constructionCount(path, constructedType))
                    .sum(), constructedType);
        }

        var rootSource = MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillRetentionRootSnapshot.java");
        assertEquals(List.of(rootSource), sources.stream()
                .filter(path -> rootVariantConstructionCount(
                                path,
                                rootSource,
                                "SkillRetentionRootSnapshot.Complete",
                                "Complete")
                        > 0)
                .toList());
        assertEquals(1, sources.stream()
                .mapToInt(path -> rootVariantConstructionCount(
                        path,
                        rootSource,
                        "SkillRetentionRootSnapshot.Complete",
                        "Complete"))
                .sum());
        assertEquals(List.of(rootSource), sources.stream()
                .filter(path -> rootVariantConstructionCount(
                                path,
                                rootSource,
                                "SkillRetentionRootSnapshot.OverLimit",
                                "OverLimit")
                        > 0)
                .toList());
        assertEquals(1, sources.stream()
                .mapToInt(path -> rootVariantConstructionCount(
                        path,
                        rootSource,
                        "SkillRetentionRootSnapshot.OverLimit",
                        "OverLimit"))
                .sum());
    }

    @Test
    void constructionScannerRecognizesQualifiedImportedWildcardAndReferenceSpellings() {
        var type = "SkillReclaimResult.Completed";
        assertEquals(1, constructionCountSource(
                "class Probe { Object value() { return new "
                        + "com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.Completed(null); } }",
                type));
        assertEquals(1, constructionCountSource(
                "import com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.Completed; "
                        + "class Probe { Object value() { return new Completed(null); } }",
                type));
        assertEquals(1, constructionCountSource(
                "import com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.*; "
                        + "class Probe { Object value() { return new Completed(null); } }",
                type));
        assertEquals(1, constructionCountSource(
                "class Probe { java.util.function.Function<Object,Object> value = "
                        + "SkillReclaimResult.Completed::new; }",
                type));
        assertEquals(1, constructionCountSource(
                "import com.yo1no.gramarye.magic.definition.store.SkillReclaimResult.Completed; "
                        + "class Probe { java.util.function.Function<Object,Object> value = "
                        + "SkillReclaimResult.Completed::new; }",
                type));
        assertEquals(0, constructionCountSource(
                "class Completed {} class Probe { Object value = Completed::new; }",
                type));

        var rootType = "SkillRetentionRootSnapshot.OverLimit";
        var qualifiedRootConstruction =
                "class Probe { Object value() { return new "
                        + "SkillRetentionRootSnapshot.OverLimit(2, 1); } }";
        assertEquals(1, constructionCountSource(qualifiedRootConstruction, rootType));
        assertEquals(0, localConstructionCountSource(qualifiedRootConstruction, "OverLimit"));
        var localRootConstruction =
                "class Probe { Object value() { return new OverLimit(2, 1); } }";
        assertEquals(0, constructionCountSource(localRootConstruction, rootType));
        assertEquals(1, localConstructionCountSource(localRootConstruction, "OverLimit"));
    }

    @Test
    void reclaimSourceKeepsFixedPrecedenceAndPrebuildsBeforePublication() {
        var source = readSanitized(STORE_SOURCE);
        var start = source.indexOf("public SkillReclaimResult reclaim");
        var end = source.indexOf("void releasePin", start);
        var reclaim = source.substring(start, end);
        var nonNull = reclaim.indexOf("Objects.requireNonNull(externalRoots");
        var incomplete = reclaim.indexOf("SkillRetentionRootSnapshot.Incomplete.INSTANCE");
        var truncated = reclaim.indexOf("SkillRetentionRootSnapshot.Truncated.INSTANCE");
        var overLimit = reclaim.indexOf("SkillRetentionRootSnapshot.OverLimit");
        var missingLoop = reclaim.indexOf("for (var root : complete.roots())");
        var dedup = reclaim.indexOf("new HashSet<>(complete.roots())");
        var pins = reclaim.indexOf("requireActivePinInvariants()");
        var replacements = reclaim.indexOf("new HashMap<SkillId, StoredSkillHistory>()");
        var report = reclaim.indexOf("new SkillReclaimReport");
        var completed = reclaim.indexOf("new SkillReclaimResult.Completed");
        var publish = reclaim.indexOf("histories.put");
        var returned = reclaim.indexOf("return completed", publish);

        assertTrue(0 <= nonNull && nonNull < incomplete);
        assertTrue(incomplete < truncated && truncated < overLimit);
        assertTrue(overLimit < missingLoop && missingLoop < dedup);
        assertTrue(dedup < pins && pins < replacements);
        assertTrue(replacements < report && report < completed);
        assertTrue(completed < publish && publish < returned);
        assertFalse(reclaim.contains("activePinCounts.put"));
        assertFalse(reclaim.contains("activePinCounts.remove"));
        assertFalse(reclaim.contains("histories.remove"));
        assertFalse(reclaim.contains(".compute("));
        assertFalse(reclaim.contains("replaceAll"));
        for (var forbiddenMutation : List.of(
                "histories.putAll", "histories.merge", "histories.replace",
                "histories.clear", "activePinCounts.put", "activePinCounts.putAll",
                "activePinCounts.merge", "activePinCounts.replace",
                "activePinCounts.compute", "activePinCounts.clear")) {
            assertFalse(reclaim.contains(forbiddenMutation), forbiddenMutation);
        }
    }

    @Test
    void storePackageContainsNoP4RuntimeDiscoveryOrDestructiveSurface() throws Exception {
        var allStoreSources = storePackageSources();
        var storeSources = allStoreSources.stream()
                .filter(P3D3ApiGateTest::isP3dStoreSource)
                .toList();
        var forbiddenSourceTokens = List.of(
                "net.minecraft.", "net.neoforged.", "SavedData", "Attachment",
                "Codec", "DynamicOps", "Tag", "RootProvider", "RootCollector",
                "ReclaimService", "ReclaimOptions", "ForceReclaim", "PinRegistry",
                "setDirty", "forceReclaim", "bestEffortReclaim", "releaseQuota",
                "retire(", "delete(", "tombstone", "generation", "epoch", "synchronized",
                "Player", "ServerPlayer", "Level", "SkillInstance", "Marker", "Construct",
                "Schedule", "SkillMigration", "java.util.concurrent", "Cleaner",
                "Executor", "Timer", "scheduler");

        for (var source : storeSources) {
            var text = withoutExactD1StoreIntegration(source, readSanitized(source));
            for (var token : forbiddenSourceTokens) {
                assertFalse(text.contains(token), source + " contains " + token);
            }
        }
        assertExactThreadSurface(allStoreSources);

        // C1/C2-A player/document production and C2-B test-only types never join Store production.
        assertTrue(storeSources.stream()
                .map(path -> path.getFileName().toString())
                .noneMatch(name -> P4C1PhaseTypes.containsSourceFileName(name)
                        || P4C2PhaseTypes.containsSourceFileName(name)
                        || P4C2BPhaseTypes.containsSourceFileName(name)));

        var productionNames = productionClassNames();
        assertTrue(productionNames.stream()
                .map(P3D3ApiGateTest::simpleTopLevelName)
                .map(String::toLowerCase)
                .noneMatch(name -> name.contains("rootprovider")
                        || name.contains("rootcollector")
                        || name.contains("forcereclaim")
                        || name.contains("reclaimoptions")));
        assertFalse(productionNames.contains(
                "com.yo1no.gramarye.magic.definition.store.StoreTestFixtures"));
    }

    @Test
    void retentionRootCeilingHasOneProductionTruthAndFactoryUsesIt() throws Exception {
        var ceilingSource = readSanitized(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"));
        assertEquals(1, countMatches(
                Pattern.compile("\\bpublic\\s+static\\s+final\\s+int\\s+"
                        + "MAX_RETENTION_ROOTS_PER_RECLAIM\\s*=\\s*65_536\\s*;"),
                ceilingSource));
        var rootSource = readSanitized(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/store/SkillRetentionRootSnapshot.java"));
        var factoryStart = rootSource.indexOf("static SkillRetentionRootSnapshot fromCompleteRoots");
        var factoryEnd = rootSource.indexOf("final class Complete", factoryStart);
        assertTrue(factoryStart >= 0 && factoryEnd > factoryStart);
        var factorySource = rootSource.substring(factoryStart, factoryEnd);
        assertTrue(factorySource.contains(
                "MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM"));
    }

    @Test
    void finalArchitectureLedgerRecordsP4MigrationAndDirtyObligations() throws Exception {
        var ledger = PROJECT_ROOT.resolve("docs/architecture/P3-D-skill-definition-store.md");
        assertTrue(Files.isRegularFile(ledger));
        var readme = Files.readString(PROJECT_ROOT.resolve("docs/architecture/README.md"));
        var d0 = Files.readString(PROJECT_ROOT.resolve(
                "docs/architecture/P3-D0-store-boundary.md"));
        var normalized = Files.readString(ledger).toLowerCase();

        assertTrue(readme.contains("P3-D-skill-definition-store.md"));
        assertTrue(d0.contains("P3-D-skill-definition-store.md"));
        assertTrue(normalized.contains("../codex-spec/17_p3"));
        assertTrue(normalized.contains("../codex-spec/16_"));
        assertTrue(normalized.contains("../codex-spec/codex_"));
        assertTrue(normalized.contains("../codex-spec/neoforge1.21.1_"));
        assertTrue(normalized.contains("## d0"));
        assertTrue(normalized.contains("## d1"));
        assertTrue(normalized.contains("## d2"));
        assertTrue(normalized.contains("## d3-a"));
        assertTrue(normalized.contains("## d3-b"));
        assertTrue(normalized.contains("dirty-state matrix"));
        assertTrue(normalized.contains(
                "p3-d store `committed` + prebuilt store carrier/journal published | dirty"));
        assertTrue(normalized.contains("commit typed failure | not dirty"));
        assertTrue(normalized.contains("pin / close | not dirty"));
        assertTrue(normalized.contains("reclaim `rejected` | not dirty"));
        assertTrue(normalized.contains("reclaim `completed`, reclaimed = 0 | not dirty"));
        assertTrue(normalized.contains("reclaim `completed`, reclaimed > 0 | dirty"));
        assertTrue(normalized.contains("snapshot / read | not dirty"));
        assertTrue(normalized.contains("## p4 obligations"));
        assertTrue(normalized.contains("migration before restore"));
        assertTrue(normalized.contains("old schema -> migration -> current-schema snapshot -> restore success"));
        assertTrue(normalized.contains(
                "same old document without migration -> unsupporteddocumentschema"));
        assertTrue(normalized.contains("migration failure"));
        assertTrue(normalized.contains("restore rejection"));
        assertTrue(normalized.contains("corruption/quarantine"));

        var storePackageText = productionSources().stream()
                .filter(path -> path.toString().contains("/magic/definition/store/"))
                .filter(P3D3ApiGateTest::isP3dStoreSource)
                .map(P3D3ApiGateTest::readSanitized)
                .collect(Collectors.joining("\n"));
        assertFalse(storePackageText.contains("SkillDocumentMigrator"));
        assertFalse(storePackageText.contains("SkillMigrationPlan"));
        assertFalse(storePackageText.contains("RawSkillDocumentSnapshot"));
        assertFalse(storePackageText.contains("magic.definition.migration"));
        assertFalse(storePackageText.contains("P4B2Probe"));
        assertFalse(storePackageText.contains("P4B2Memory"));
        var storeSource = readSanitized(STORE_SOURCE);
        assertTrue(storeSource.contains(
                "restore(SkillDefinitionStoreSnapshot snapshot)"));
        assertTrue(storeSource.contains("SkillDocument.CURRENT_SCHEMA_VERSION"));
    }

    private static ThreadSourceAuthority unrelatedFutureAuthority(
            String fileName,
            String binaryOwner,
            String methodDescriptor,
            String normalizedExpressionSha256,
            String sourceSha256) {
        return new ThreadSourceAuthority(
                STORE_SOURCE_PREFIX + fileName,
                Set.of(binaryOwner),
                Set.of(methodDescriptor),
                Set.of("domain migration Code.FUTURE_SCHEMA_VERSION"),
                Set.of(ThreadRole.UNRELATED_IDENTIFIER_SUBSTRING),
                Set.of("P4_DOMAIN_SCHEMA_VERSION"),
                1,
                normalizedExpressionSha256,
                sourceSha256);
    }

    private static List<Path> storePackageSources() throws Exception {
        return productionSources().stream()
                .filter(path -> repositoryRelative(path).startsWith(STORE_SOURCE_PREFIX))
                .toList();
    }

    private static void assertExactThreadSurface(List<Path> storeSources) throws Exception {
        var relativePaths = storeSources.stream()
                .map(P3D3ApiGateTest::repositoryRelative)
                .sorted()
                .toList();
        assertEquals(114, relativePaths.size());
        assertEquals(
                "9ce0d3ddad81bd5526f9272c895abe37e8eb9d8c09036193d8b5e375b313099c",
                sha256(String.join("\n", relativePaths) + "\n"));

        var authoritiesByPath = THREAD_SOURCE_AUTHORITIES.stream().collect(Collectors.toMap(
                ThreadSourceAuthority::path,
                authority -> authority));
        assertEquals(THREAD_SOURCE_AUTHORITIES.size(), authoritiesByPath.size());
        assertEquals(17, authoritiesByPath.size());

        var actualAuthorityPaths = new LinkedHashSet<String>();
        var backgroundProjection = new ArrayList<String>();
        var rawCandidateCount = 0;
        var executableCandidateCount = 0;
        for (var source : storeSources) {
            var path = repositoryRelative(source);
            var raw = Files.readString(source);
            var executable = executableSource(raw);
            rawCandidateCount += countMatches(THREAD_FAMILY_IDENTIFIER, raw);
            var entries = normalizedExpressionProjection(
                    path, executable, THREAD_FAMILY_IDENTIFIER);
            executableCandidateCount += entries.size();

            if (!entries.isEmpty()) {
                var authority = authoritiesByPath.get(path);
                assertTrue(authority != null, "unclassified executable coordinate in " + path);
                assertEquals(authority.executableCandidateCount(), entries.size(), path);
                assertEquals(authority.normalizedExpressionSha256(),
                        sha256(String.join("\n", entries) + "\n"), path);
                assertEquals(authority.sourceSha256(), sha256(raw), path);
                actualAuthorityPaths.add(path);
            }

            backgroundProjection.addAll(normalizedExpressionProjection(
                    path, executable, BACKGROUND_CANDIDATE_IDENTIFIER));
            for (var forbidden : FORBIDDEN_THREAD_SURFACES) {
                var forbiddenSource = executable;
                if (forbidden.role().equals("BACKGROUND_SUBMISSION")
                        && path.equals(STORE_SOURCE_PREFIX
                                + "SkillSubmissionRecoveryGameTests.java")) {
                    // §59.6/§60.32: exact synchronous local-bus test realization,
                    // not a class-wide or production background-submission exemption.
                    var start = executable.indexOf(
                            "private static void assertRecoveryChangedHandoffOnce(");
                    var end = executable.indexOf(
                            "private static InstalledRecoveryFixture installRecoveryFixture(",
                            start);
                    assertTrue(start >= 0 && end > start, path);
                    var handoff = executable.substring(start, end);
                    assertEquals(1, countMatches(Pattern.compile(Pattern.quote(
                            "var bus = BusBuilder.builder().build();")), handoff), path);
                    var synchronousPosts = List.of(
                            "bus.post(new ServerStartingEvent(server));",
                            "bus.post(new PlayerEvent.PlayerLoggedInEvent(player));",
                            "bus.post(new ServerStoppedEvent(server));");
                    assertEquals(synchronousPosts.stream()
                                    .map(statement -> path + "\tpost\t" + statement).toList(),
                            normalizedExpressionProjection(
                                    path, handoff, Pattern.compile("\\bpost\\b")), path);
                    for (var statement : synchronousPosts) {
                        assertEquals(1, countMatches(
                                Pattern.compile(Pattern.quote(statement)), executable), path);
                        forbiddenSource = forbiddenSource.replace(statement, "");
                    }
                }
                assertFalse(
                        forbidden.pattern().matcher(forbiddenSource).find(),
                        path + " contains forbidden " + forbidden.role());
            }
        }

        assertEquals(331, rawCandidateCount);
        assertEquals(234, executableCandidateCount);
        assertEquals(97, rawCandidateCount - executableCandidateCount);
        assertEquals(authoritiesByPath.keySet(), actualAuthorityPaths);
        assertEquals(9, backgroundProjection.size());
        assertEquals(
                "1154c3b33f8030214a7baee5cb5c1158fbec4ef8ac732bf08ffa7f1d781d462c",
                sha256(String.join("\n", backgroundProjection) + "\n"));

        var authorizedThreadPaths = THREAD_SOURCE_AUTHORITIES.stream()
                .filter(authority -> authority.roles().stream()
                        .anyMatch(role -> role != ThreadRole.UNRELATED_IDENTIFIER_SUBSTRING))
                .map(ThreadSourceAuthority::path)
                .sorted()
                .toList();
        assertEquals(12, authorizedThreadPaths.size());
        assertEquals(
                "a1cbc78c7da7a2d946c3b6c17f8d2fece42f7eaebab97020040314cdbb4b4522",
                sha256(String.join("\n", authorizedThreadPaths) + "\n"));

        var semanticProjection = semanticThreadProjection(storeSources, authoritiesByPath);
        assertTrue(semanticProjection.diagnostics().isEmpty(),
                String.join("\n", semanticProjection.diagnostics()));
        assertEquals(executableCandidateCount, semanticProjection.coordinates().size());
        var exactKeys = semanticProjection.coordinates().stream()
                .map(ExactThreadCoordinate::exactKey)
                .sorted()
                .toList();
        assertEquals(234, exactKeys.size());
        assertEquals(231, new LinkedHashSet<>(exactKeys).size(),
                "exact aggregate keys must preserve the reviewed lexical multiplicities");
        assertEquals(
                "ab23b2c64772beed36811bce902885dd43cbf91b207a553269ca0fe3a5cee595",
                sha256(String.join("\n", exactKeys) + "\n"));
        assertEquals(
                Map.of(
                        ThreadRole.AUTHORIZED_C1_TYPE_NAME, 19L,
                        ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID, 32L,
                        ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID, 13L,
                        ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY, 164L,
                        ThreadRole.UNRELATED_IDENTIFIER_SUBSTRING, 6L),
                semanticProjection.coordinates().stream().collect(Collectors.groupingBy(
                        ExactThreadCoordinate::role, Collectors.counting())));
        for (var coordinate : semanticProjection.coordinates()) {
            assertCoordinateExecutableExists(coordinate);
        }

        assertExactProductThreadPrecondition();
        assertExactThreadRetentionBinarySurface();
    }

    private static SemanticThreadProjection semanticThreadProjection(
            List<Path> storeSources,
            Map<String, ThreadSourceAuthority> authoritiesByPath) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "P3D3 exact gate requires a full Java 21 JDK");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fileManager = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var sourceFiles = fileManager.getJavaFileObjectsFromPaths(storeSources);
            var task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:none", "-classpath", System.getProperty("java.class.path")),
                    null,
                    sourceFiles);
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            task.analyze();
            var errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(diagnostic -> diagnostic.getSource() + ":" + diagnostic.getLineNumber()
                            + ": " + diagnostic.getMessage(java.util.Locale.ROOT))
                    .toList();
            assertTrue(errors.isEmpty(), String.join("\n", errors));

            var trees = Trees.instance(task);
            var elements = task.getElements();
            var types = task.getTypes();
            var coordinates = new ArrayList<ExactThreadCoordinate>();
            var unresolved = new ArrayList<String>();
            for (var unit : units) {
                var source = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                var path = repositoryRelative(source);
                var authority = authoritiesByPath.get(path);
                var raw = Files.readString(source);
                var executable = executableSource(raw);
                var matcher = THREAD_FAMILY_IDENTIFIER.matcher(executable);
                while (matcher.find()) {
                    if (authority == null) {
                        unresolved.add(path + ":" + matcher.start()
                                + " has no exact source authority");
                        continue;
                    }
                    var coordinate = resolveCoordinate(
                            unit,
                            trees,
                            elements,
                            types,
                            path,
                            raw,
                            matcher.start(),
                            matcher.end(),
                            matcher.group(),
                            authority);
                    if (coordinate == null) {
                        unresolved.add(path + ":" + matcher.start() + ":" + matcher.group()
                                + " did not resolve to a Java symbol");
                    } else {
                        coordinates.add(coordinate);
                    }
                }
            }
            return new SemanticThreadProjection(List.copyOf(coordinates), List.copyOf(unresolved));
        }
    }

    private static ExactThreadCoordinate resolveCoordinate(
            CompilationUnitTree unit,
            Trees trees,
            Elements elements,
            Types types,
            String path,
            String raw,
            int start,
            int end,
            String candidate,
            ThreadSourceAuthority authority) {
        var positions = trees.getSourcePositions();
        class Resolver extends TreePathScanner<Void, Void> {
            private TreePath best;
            private long bestWidth = Long.MAX_VALUE;

            @Override
            public Void scan(Tree tree, Void unused) {
                if (tree == null) {
                    return null;
                }
                var treeStart = positions.getStartPosition(unit, tree);
                var treeEnd = positions.getEndPosition(unit, tree);
                if (treeStart >= 0 && treeEnd >= end && treeStart <= start) {
                    var pathAtTree = new TreePath(getCurrentPath(), tree);
                    if (trees.getElement(pathAtTree) != null && treeEnd - treeStart < bestWidth) {
                        best = pathAtTree;
                        bestWidth = treeEnd - treeStart;
                    }
                    return super.scan(tree, unused);
                }
                return null;
            }
        }
        var resolver = new Resolver();
        resolver.scan(unit, null);
        if (resolver.best == null) {
            return null;
        }
        var symbol = trees.getElement(resolver.best);
        var owner = enclosingBinaryOwner(resolver.best, trees, elements);
        if (owner == null) {
            owner = unit.getTypeDecls().stream()
                    .map(tree -> trees.getElement(TreePath.getPath(unit, tree)))
                    .filter(TypeElement.class::isInstance)
                    .map(TypeElement.class::cast)
                    .map(elements::getBinaryName)
                    .map(Object::toString)
                    .findFirst()
                    .orElse("");
        }
        var methodDescriptor = enclosingMethodDescriptor(resolver.best, trees, elements, types);
        if (methodDescriptor == null) {
            methodDescriptor = declarationDescriptor(symbol, elements, types);
        }
        var resolvedSymbol = resolvedSymbol(symbol, elements, types);
        var semanticSource = executableSource(raw);
        var lineStart = semanticSource.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        var lineEnd = semanticSource.indexOf('\n', end);
        if (lineEnd < 0) {
            lineEnd = semanticSource.length();
        }
        var normalizedExpression = semanticSource.substring(lineStart, lineEnd)
                .trim()
                .replaceAll("\\s+", " ");
        var role = coordinateRole(
                authority, methodDescriptor, candidate, resolvedSymbol, normalizedExpression);
        var family = coordinateAuthorityFamily(authority, role);
        return new ExactThreadCoordinate(
                path,
                owner,
                methodDescriptor,
                resolvedSymbol,
                role,
                family,
                sha256(normalizedExpression),
                candidate,
                start);
    }

    private static String enclosingBinaryOwner(
            TreePath path, Trees trees, Elements elements) {
        for (var cursor = path; cursor != null; cursor = cursor.getParentPath()) {
            if (cursor.getLeaf() instanceof ClassTree) {
                var element = trees.getElement(cursor);
                if (element instanceof TypeElement type) {
                    return elements.getBinaryName(type).toString();
                }
            }
        }
        return null;
    }

    private static String enclosingMethodDescriptor(
            TreePath path, Trees trees, Elements elements, Types types) {
        for (var cursor = path; cursor != null; cursor = cursor.getParentPath()) {
            if (cursor.getLeaf() instanceof MethodTree) {
                var element = trees.getElement(cursor);
                if (element instanceof ExecutableElement executable) {
                    var name = executable.getKind() == ElementKind.CONSTRUCTOR
                            ? "<init>"
                            : executable.getSimpleName().toString();
                    return name + executableDescriptor(executable, elements, types);
                }
            }
        }
        return null;
    }

    private static String declarationDescriptor(
            Element symbol, Elements elements, Types types) {
        if (symbol instanceof ExecutableElement executable) {
            var name = executable.getKind() == ElementKind.CONSTRUCTOR
                    ? "<init>"
                    : executable.getSimpleName().toString();
            return name + executableDescriptor(executable, elements, types);
        }
        if (symbol instanceof VariableElement variable
                && (variable.getKind() == ElementKind.FIELD
                        || variable.getKind() == ElementKind.ENUM_CONSTANT)) {
            return "FIELD:" + variable.getSimpleName() + ":"
                    + typeDescriptor(variable.asType(), elements, types);
        }
        if (symbol instanceof TypeElement) {
            return "<TYPE>";
        }
        if (symbol.getKind() == ElementKind.PACKAGE) {
            return "<IMPORT>";
        }
        return "<DECLARATION:" + symbol.getKind() + ">";
    }

    private static String resolvedSymbol(Element symbol, Elements elements, Types types) {
        if (symbol instanceof TypeElement type) {
            return elements.getBinaryName(type).toString();
        }
        if (symbol instanceof ExecutableElement executable) {
            var owner = enclosingType(executable);
            var name = executable.getKind() == ElementKind.CONSTRUCTOR
                    ? "<init>"
                    : executable.getSimpleName().toString();
            return elements.getBinaryName(owner) + "#" + name
                    + executableDescriptor(executable, elements, types);
        }
        if (symbol instanceof VariableElement variable) {
            var owner = enclosingType(variable);
            var ownerName = owner == null ? "<NO_OWNER>" : elements.getBinaryName(owner).toString();
            return ownerName + "#" + variable.getKind() + ":" + variable.getSimpleName()
                    + ":" + typeDescriptor(variable.asType(), elements, types);
        }
        return symbol.getKind() + ":" + symbol;
    }

    private static TypeElement enclosingType(Element element) {
        for (var cursor = element; cursor != null; cursor = cursor.getEnclosingElement()) {
            if (cursor instanceof TypeElement type) {
                return type;
            }
        }
        return null;
    }

    private static String executableDescriptor(
            ExecutableElement executable, Elements elements, Types types) {
        var executableType = (ExecutableType) types.erasure(executable.asType());
        return "(" + executableType.getParameterTypes().stream()
                .map(type -> typeDescriptor(type, elements, types))
                .collect(Collectors.joining()) + ")"
                + (executable.getKind() == ElementKind.CONSTRUCTOR
                        ? "V"
                        : typeDescriptor(executableType.getReturnType(), elements, types));
    }

    private static String typeDescriptor(TypeMirror input, Elements elements, Types types) {
        var type = switch (input.getKind()) {
            case TYPEVAR, WILDCARD -> types.erasure(input);
            default -> input;
        };
        if (type instanceof PrimitiveType primitive) {
            return switch (primitive.getKind()) {
                case BOOLEAN -> "Z";
                case BYTE -> "B";
                case CHAR -> "C";
                case SHORT -> "S";
                case INT -> "I";
                case LONG -> "J";
                case FLOAT -> "F";
                case DOUBLE -> "D";
                default -> throw new AssertionError("unexpected primitive " + primitive);
            };
        }
        if (type instanceof NoType noType && noType.getKind() == TypeKind.VOID) {
            return "V";
        }
        if (type instanceof ArrayType array) {
            return "[" + typeDescriptor(array.getComponentType(), elements, types);
        }
        if (type instanceof DeclaredType declared
                && declared.asElement() instanceof TypeElement declaredElement) {
            return "L" + elements.getBinaryName(declaredElement).toString().replace('.', '/') + ";";
        }
        if (type instanceof TypeVariable variable) {
            return typeDescriptor(types.erasure(variable), elements, types);
        }
        if (type instanceof WildcardType wildcard) {
            var bound = wildcard.getExtendsBound() == null
                    ? elements.getTypeElement("java.lang.Object").asType()
                    : wildcard.getExtendsBound();
            return typeDescriptor(types.erasure(bound), elements, types);
        }
        throw new AssertionError("unresolved descriptor type " + type + " (" + type.getKind() + ")");
    }

    private static ThreadRole coordinateRole(
            ThreadSourceAuthority authority,
            String methodDescriptor,
            String candidate,
            String resolvedSymbol,
            String expression) {
        if (authority.roles().size() == 1) {
            return authority.roles().iterator().next();
        }
        var file = authority.path().substring(authority.path().lastIndexOf('/') + 1);
        var normalized = (candidate + " " + resolvedSymbol + " " + expression).toLowerCase();
        var role = switch (file) {
            case "P4E1GlobalSourceCapture.java" ->
                    normalized.contains("productthreadprecondition")
                            ? ThreadRole.AUTHORIZED_C1_TYPE_NAME
                            : ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY;
            case "P4E1GroupedStoreAudit.java" -> {
                if (candidate.toLowerCase().contains("observed")) {
                    yield ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID;
                }
                if (normalized.contains("productthreadprecondition")
                        || normalized.contains("wrong_thread")) {
                    yield ThreadRole.AUTHORIZED_C1_TYPE_NAME;
                }
                if (methodDescriptor.startsWith("audit(")
                        && expression.contains("currentThread")) {
                    yield ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID;
                }
                if (expression.contains("getRunningThread")) {
                    yield ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID;
                }
                yield ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY;
            }
            case "ProductThreadPrecondition.java" -> {
                if (candidate.toLowerCase().contains("expected")) {
                    yield ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID;
                }
                if (candidate.toLowerCase().contains("observed")) {
                    yield ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID;
                }
                yield ThreadRole.AUTHORIZED_C1_TYPE_NAME;
            }
            case "SkillDefinitionStoreService.java" -> {
                if (candidate.toLowerCase().contains("observed")) {
                    yield ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID;
                }
                if (expression.contains("getRunningThread")) {
                    yield ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID;
                }
                if (methodDescriptor.equals("requireServerThread("
                                + "Lnet/minecraft/server/MinecraftServer;J)V")
                        && (candidate.equals("ProductThreadPrecondition")
                                || candidate.equals("WRONG_THREAD"))) {
                    yield ThreadRole.AUTHORIZED_C1_TYPE_NAME;
                }
                if (expression.contains("currentThread")
                        || (candidate.equals("requireServerThread")
                                && resolvedSymbol.contains("MinecraftServer;J)V"))) {
                    yield ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID;
                }
                yield ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY;
            }
            case "SkillSavedDataLifecycleGameTests.java" -> {
                if (normalized.contains("java.util.concurrent")
                        || normalized.contains("atomicinteger")) {
                    yield ThreadRole.UNRELATED_IDENTIFIER_SUBSTRING;
                }
                if (normalized.contains("wrongthreadfailure")
                        || normalized.contains("wrongthreadcapture")
                        || normalized.contains("exactthreadowner")
                        || (candidate.equals("WRONG_THREAD")
                                && resolvedSymbol.contains("SkillSubsystemLifecycleException"))) {
                    yield ThreadRole.AUTHORIZED_C1_OBSERVED_THREAD_ID;
                }
                if (candidate.toLowerCase().contains("expected")
                        || expression.contains("getRunningThread")) {
                    yield ThreadRole.AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID;
                }
                if (candidate.equals("ProductThreadPrecondition")
                        || (candidate.equals("WRONG_THREAD")
                                && resolvedSymbol.contains("ProductThreadPrecondition"))) {
                    yield ThreadRole.AUTHORIZED_C1_TYPE_NAME;
                }
                yield ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY;
            }
            default -> normalized.contains("future_schema_version")
                    ? ThreadRole.UNRELATED_IDENTIFIER_SUBSTRING
                    : ThreadRole.AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY;
        };
        assertTrue(authority.roles().contains(role),
                authority.path() + " does not authorize derived role " + role
                        + " for " + candidate + " -> " + resolvedSymbol);
        return role;
    }

    private static String coordinateAuthorityFamily(
            ThreadSourceAuthority authority, ThreadRole role) {
        if (authority.authorityFamilies().size() == 1) {
            return authority.authorityFamilies().iterator().next();
        }
        var prefix = switch (role) {
            case AUTHORIZED_C1_TYPE_NAME,
                    AUTHORIZED_C1_OBSERVED_THREAD_ID,
                    AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID -> "C1_";
            case AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY -> "P4";
            case UNRELATED_IDENTIFIER_SUBSTRING -> "SYNCHRONOUS_";
        };
        return authority.authorityFamilies().stream()
                .filter(family -> family.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError(authority.path()
                        + " has no exact family for role " + role));
    }

    private static void assertCoordinateExecutableExists(ExactThreadCoordinate coordinate)
            throws Exception {
        if (coordinate.methodDescriptor().startsWith("<IMPORT>")
                || coordinate.methodDescriptor().startsWith("<TYPE>")
                || coordinate.methodDescriptor().startsWith("FIELD:")
                || coordinate.methodDescriptor().startsWith("<DECLARATION:")) {
            return;
        }
        var type = Class.forName(
                coordinate.binaryOwner(), false, P3D3ApiGateTest.class.getClassLoader());
        var actual = new LinkedHashSet<String>();
        for (var constructor : type.getDeclaredConstructors()) {
            actual.add("<init>" + executableDescriptor(constructor.getParameterTypes(), void.class));
        }
        for (var method : type.getDeclaredMethods()) {
            actual.add(method.getName()
                    + executableDescriptor(method.getParameterTypes(), method.getReturnType()));
        }
        assertTrue(actual.contains(coordinate.methodDescriptor()),
                "unresolved enclosing executable " + coordinate.exactKey());
    }

    private static List<String> normalizedExpressionProjection(
            String path,
            String executableSource,
            Pattern candidates) {
        var entries = new ArrayList<String>();
        for (var line : executableSource.split("\n", -1)) {
            var matcher = candidates.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            var normalized = line.trim().replaceAll("\\s+", " ");
            do {
                entries.add(path + "\t" + matcher.group() + "\t" + normalized);
            } while (matcher.find());
        }
        return List.copyOf(entries);
    }

    private static String repositoryRelative(Path path) {
        return PROJECT_ROOT.relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 unavailable", exception);
        }
    }

    private static String executableSource(String source) {
        var result = source.toCharArray();
        var state = LexicalState.CODE;
        var escaped = false;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (index + 2 < source.length()
                            && source.startsWith("\"\"\"", index)) {
                        result[index] = ' ';
                        result[index + 1] = ' ';
                        result[index + 2] = ' ';
                        index += 2;
                        state = LexicalState.TEXT_BLOCK;
                    } else if (current == '/' && next == '/') {
                        result[index] = ' ';
                        result[index + 1] = ' ';
                        index++;
                        state = LexicalState.LINE_COMMENT;
                    } else if (current == '/' && next == '*') {
                        result[index] = ' ';
                        result[index + 1] = ' ';
                        index++;
                        state = LexicalState.BLOCK_COMMENT;
                    } else if (current == '"') {
                        result[index] = ' ';
                        state = LexicalState.STRING;
                        escaped = false;
                    } else if (current == '\'') {
                        result[index] = ' ';
                        state = LexicalState.CHARACTER;
                        escaped = false;
                    }
                }
                case LINE_COMMENT -> {
                    if (current == '\n') {
                        state = LexicalState.CODE;
                    } else {
                        result[index] = ' ';
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        result[index] = ' ';
                        result[index + 1] = ' ';
                        index++;
                        state = LexicalState.CODE;
                    } else if (current != '\n') {
                        result[index] = ' ';
                    }
                }
                case STRING, CHARACTER -> {
                    if (current != '\n') {
                        result[index] = ' ';
                    }
                    if (escaped) {
                        escaped = false;
                    } else if (current == '\\') {
                        escaped = true;
                    } else if ((state == LexicalState.STRING && current == '"')
                            || (state == LexicalState.CHARACTER && current == '\'')) {
                        state = LexicalState.CODE;
                    }
                }
                case TEXT_BLOCK -> {
                    if (index + 2 < source.length()
                            && source.startsWith("\"\"\"", index)) {
                        result[index] = ' ';
                        result[index + 1] = ' ';
                        result[index + 2] = ' ';
                        index += 2;
                        state = LexicalState.CODE;
                    } else if (current != '\n') {
                        result[index] = ' ';
                    }
                }
            }
        }
        assertTrue(state == LexicalState.CODE || state == LexicalState.LINE_COMMENT,
                "unterminated Java lexical segment");
        return new String(result);
    }

    private static void assertExactProductThreadPrecondition() throws Exception {
        var type = ProductThreadPrecondition.class;
        assertEquals(
                "com.yo1no.gramarye.magic.definition.store.ProductThreadPrecondition",
                type.getName());
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertPackagePrivate(type.getModifiers());
        assertEquals(0, type.getDeclaredFields().length);

        var constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());

        var methods = type.getDeclaredMethods();
        assertEquals(1, methods.length);
        var classify = type.getDeclaredMethod("classify", long.class, long.class);
        assertTrue(Modifier.isStatic(classify.getModifiers()));
        assertPackagePrivate(classify.getModifiers());
        assertEquals(ProductThreadPrecondition.Decision.class, classify.getReturnType());
        assertEquals(
                "(JJ)Lcom/yo1no/gramarye/magic/definition/store/"
                        + "ProductThreadPrecondition$Decision;",
                executableDescriptor(classify.getParameterTypes(), classify.getReturnType()));
        assertEquals(
                List.of("ALLOWED", "WRONG_THREAD"),
                Arrays.stream(ProductThreadPrecondition.Decision.values())
                        .map(Enum::name)
                        .toList());

        var binaryNames = productionClassNames();
        assertEquals(1, binaryNames.stream().filter(type.getName()::equals).count());
        assertEquals(1, binaryNames.stream()
                .filter(ProductThreadPrecondition.Decision.class.getName()::equals)
                .count());
    }

    private static void assertExactThreadRetentionBinarySurface() throws Exception {
        var actual = new LinkedHashSet<String>();
        var classNames = productionClassNames().stream()
                .filter(name -> name.startsWith(
                        "com.yo1no.gramarye.magic.definition.store."))
                .sorted()
                .toList();
        for (var className : classNames) {
            var type = Class.forName(className, false, P3D3ApiGateTest.class.getClassLoader());
            for (var field : type.getDeclaredFields()) {
                if (containsThreadType(field.getGenericType())) {
                    actual.add(className + "#FIELD:" + field.getName() + ":"
                            + typeDescriptor(field.getType()));
                }
            }
            for (var constructor : type.getDeclaredConstructors()) {
                if (Arrays.stream(constructor.getGenericParameterTypes())
                        .anyMatch(P3D3ApiGateTest::containsThreadType)) {
                    actual.add(className + "#<init>"
                            + executableDescriptor(constructor.getParameterTypes(), void.class));
                }
            }
            for (var method : type.getDeclaredMethods()) {
                if (containsThreadType(method.getGenericReturnType())
                        || Arrays.stream(method.getGenericParameterTypes())
                                .anyMatch(P3D3ApiGateTest::containsThreadType)) {
                    actual.add(className + "#" + method.getName()
                            + executableDescriptor(method.getParameterTypes(), method.getReturnType()));
                }
            }
        }

        var expected = Set.of(
                "com.yo1no.gramarye.magic.definition.store.P4E1AuditedCapture"
                        + "#FIELD:creationThreadIdentity:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store.P4E1AuditedCapture"
                        + "#<init>(Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GroupedStoreAudit;Lnet/minecraft/server/MinecraftServer;"
                        + "Ljava/lang/Thread;ILnet/minecraft/server/players/PlayerList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservation;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$StoreReadyWitness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PendingJournalObservation$Ready;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1SourceInventory$Witness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PlayerDataDirectorySnapshot;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1IntegratedSnapshotTraversal$Selection;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer;"
                        + "Ljava/util/ArrayList;Ljava/util/ArrayList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$Summary;I)V",
                "com.yo1no.gramarye.magic.definition.store.P4E1AuditedCapture$Transfer"
                        + "#FIELD:creationThreadIdentity:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store.P4E1AuditedCapture$Transfer"
                        + "#<init>(Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GroupedStoreAudit;Lnet/minecraft/server/MinecraftServer;"
                        + "Ljava/lang/Thread;ILnet/minecraft/server/players/PlayerList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservation;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$StoreReadyWitness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PendingJournalObservation$Ready;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1SourceInventory$Witness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PlayerDataDirectorySnapshot;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1IntegratedSnapshotTraversal$Selection;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer;"
                        + "Ljava/util/ArrayList;Ljava/util/ArrayList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$Summary;I)V",
                "com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture$Captured"
                        + "#FIELD:creationThreadIdentity:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture$Captured"
                        + "#<init>(Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GroupedStoreAudit;Lnet/minecraft/server/MinecraftServer;"
                        + "Ljava/lang/Thread;ILnet/minecraft/server/players/PlayerList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservation;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$StoreReadyWitness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PendingJournalObservation$Ready;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1SourceInventory$Witness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PlayerDataDirectorySnapshot;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1IntegratedSnapshotTraversal$Selection;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer;"
                        + "Ljava/util/ArrayList;Ljava/util/ArrayList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$Summary;)V",
                "com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture$Claimed"
                        + "#FIELD:creationThreadIdentity:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store.P4E1GlobalSourceCapture$Claimed"
                        + "#<init>(Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GroupedStoreAudit;Lnet/minecraft/server/MinecraftServer;"
                        + "Ljava/lang/Thread;ILnet/minecraft/server/players/PlayerList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservation;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$StoreReadyWitness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PendingJournalObservation$Ready;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1SourceInventory$Witness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PlayerDataDirectorySnapshot;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1IntegratedSnapshotTraversal$Selection;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer;"
                        + "Ljava/util/ArrayList;Ljava/util/ArrayList;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture$Summary;)V",
                "com.yo1no.gramarye.magic.definition.store.P4E1GroupedStoreAudit"
                        + "#FIELD:creationThreadIdentity:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store.P4E1GroupedStoreAudit"
                        + "#requireCaptureBinding(Lnet/minecraft/server/MinecraftServer;"
                        + "Ljava/lang/Thread;I)V",
                "com.yo1no.gramarye.magic.definition.store.P4E1GroupedStoreAudit"
                        + "#requireCaptureBinding(Lnet/minecraft/server/MinecraftServer;"
                        + "Ljava/lang/Thread;ILcom/yo1no/gramarye/magic/definition/store/"
                        + "ProductThreadPrecondition$Decision;)V",
                "com.yo1no.gramarye.magic.definition.store.P4E1GroupedStoreAudit"
                        + "#requireCaptureServerAndThreadBinding("
                        + "Lnet/minecraft/server/MinecraftServer;Ljava/lang/Thread;)V",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$LeaseCell"
                        + "#FIELD:thread:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$LeaseCell"
                        + "#<init>(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;I"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$CallChainCurrentness;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$IndexLifecycle;J"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$IndexedBacking;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$CompleteIndex;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$IncompleteState;)V",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$PermitBinding"
                        + "#FIELD:threadIdentity:Ljava/lang/Thread;",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$PermitBinding"
                        + "#<init>(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;IJ)V",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$PermitBinding"
                        + "#requireThread(Ljava/lang/Thread;)V",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$PermitCell"
                        + "#<init>(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Thread;IJ"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1FinalFreshness$FreshnessSeal;)V",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$PermitCell"
                        + "#requireThread(Ljava/lang/Thread;)V",
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillRetentionRootAuditService$ReservationScope"
                        + "#prepareComplete(Ljava/lang/Object;Ljava/lang/Object;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer;"
                        + "[Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$PublicationSource;Ljava/lang/Thread;I"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "P4E1FinalFreshness$FreshnessSeal;"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditResult$AuditSummary;)"
                        + "Lcom/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService$PreparedComplete;");
        assertEquals(expected, actual);
    }

    private static boolean containsThreadType(Type type) {
        return type.getTypeName().contains("java.lang.Thread");
    }

    private static String executableDescriptor(Class<?>[] parameters, Class<?> result) {
        return "(" + Arrays.stream(parameters)
                .map(P3D3ApiGateTest::typeDescriptor)
                .collect(Collectors.joining()) + ")" + typeDescriptor(result);
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (!type.isPrimitive()) {
            return "L" + type.getName().replace('.', '/') + ";";
        }
        if (type == void.class) {
            return "V";
        }
        if (type == boolean.class) {
            return "Z";
        }
        if (type == byte.class) {
            return "B";
        }
        if (type == char.class) {
            return "C";
        }
        if (type == short.class) {
            return "S";
        }
        if (type == int.class) {
            return "I";
        }
        if (type == long.class) {
            return "J";
        }
        if (type == float.class) {
            return "F";
        }
        if (type == double.class) {
            return "D";
        }
        throw new AssertionError("unknown primitive " + type);
    }

    private static boolean isP3dStoreSource(Path path) {
        var name = path.getFileName().toString();
        return !name.startsWith("StorePersistence")
                && !name.equals("SkillSubmissionRecoveryGameTests.java")
                && !name.equals("StoreNbtFraming.java")
                && !name.equals("StorePersistentEnvelopeV0.java")
                && !name.equals("ImmutableStoreBlob.java")
                && !name.equals("SkillDefinitionStorePersistenceBridge.java")
                && !P4B1PhaseTypes.containsSourceFileName(name)
                && !P4B2PhaseTypes.containsSourceFileName(name)
                && !P4DPhaseTypes.containsNewStoreSourceFileName(name)
                && !P4EPhaseTypes.STORE_TYPE_NAMES.contains(
                        name.endsWith(".java")
                                ? name.substring(0, name.length() - ".java".length())
                                : name)
                && !Set.of(
                                "StoreEncodingLayout.java",
                                "StoreLayoutEncodeResult.java",
                                "EncodedSkillStoreCarrier.java",
                                "EncodedHistoryIndex.java",
                                "EncodedRevisionIndex.java",
                                "PreparedCarrierUpdate.java",
                                "CarrierUpdateKind.java",
                                "SkillStoreCarrierBuilder.java",
                                "CarrierBuildResult.java",
                                "CarrierUpdateResult.java",
                                "CarrierInvariantException.java",
                                "HistoryBlobSource.java",
                                "RevisionBlobSource.java",
                                "StoreHistoryBlobSlice.java",
                                "StoreRevisionBlobSlice.java")
                        .contains(name);
    }

    private static String withoutExactD1StoreIntegration(Path source, String text) {
        if (!source.getFileName().toString().equals("SkillDefinitionStore.java")) {
            return text;
        }
        var start = text.indexOf("StoreSubmissionAuthorityObservation observeSubmissionAuthority(");
        var end = text.indexOf("public Optional<SkillRevisionPin> pin(", start);
        assertTrue(start >= 0, "missing reviewed D1 authority observation slice");
        assertTrue(end > start, "missing end of reviewed D1 Store integration slice");
        return text.substring(0, start) + text.substring(end);
    }

    private static void assertMapField(
            java.lang.reflect.Field field,
            Class<?> key,
            Class<?> value) {
        var generic = assertInstanceOf(ParameterizedType.class, field.getGenericType());
        assertEquals(Map.class, field.getType());
        assertEquals(Map.class, generic.getRawType());
        assertEquals(List.of(key, value), Arrays.asList(generic.getActualTypeArguments()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    private static void assertPackagePrivate(int modifiers) {
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
        assertFalse(Modifier.isPrivate(modifiers));
    }

    private static void assertListField(java.lang.reflect.Field field, Class<?> element) {
        var generic = assertInstanceOf(ParameterizedType.class, field.getGenericType());
        assertEquals(List.class, field.getType());
        assertEquals(List.class, generic.getRawType());
        assertEquals(List.of(element), Arrays.asList(generic.getActualTypeArguments()));
        assertTrue(Modifier.isPrivate(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
    }

    private static void assertGenericReturn(
            java.lang.reflect.Method method,
            Class<?> rawType,
            Type... arguments) {
        var generic = assertInstanceOf(ParameterizedType.class, method.getGenericReturnType());
        assertEquals(rawType, generic.getRawType());
        assertEquals(List.of(arguments), Arrays.asList(generic.getActualTypeArguments()));
    }

    private static List<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
    }

    private static List<Class<?>> componentTypes(Class<? extends Record> type) {
        var types = new java.util.ArrayList<Class<?>>();
        Arrays.stream(type.getRecordComponents()).forEach(component -> types.add(component.getType()));
        return List.copyOf(types);
    }

    private static Set<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());
    }

    private static List<Path> productionSources() throws Exception {
        try (var paths = Files.walk(MAIN_JAVA)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static Set<String> productionClassNames() throws Exception {
        var root = PROJECT_ROOT.resolve("build/classes/java/main");
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".class"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .map(name -> name.replace(java.io.File.separatorChar, '.'))
                    .collect(Collectors.toSet());
        }
    }

    private static boolean containsConstruction(Path path, String typeName) {
        return containsConstructionSource(readSanitized(path), typeName);
    }

    private static int constructionCount(Path path, String typeName) {
        return constructionCountSource(readSanitized(path), typeName);
    }

    private static int rootVariantConstructionCount(
            Path path,
            Path rootSource,
            String qualifiedTypeName,
            String localTypeName) {
        var count = constructionCount(path, qualifiedTypeName);
        return path.equals(rootSource)
                ? count + localConstructionCountSource(readSanitized(path), localTypeName)
                : count;
    }

    private static boolean containsConstructionSource(String source, String typeName) {
        return constructionCountSource(withoutCommentsAndLiterals(source), typeName) > 0;
    }

    private static int constructionCountSource(String source, String typeName) {
        var simple = typeName.substring(typeName.lastIndexOf('.') + 1);
        var outer = typeName.contains(".")
                ? typeName.substring(0, typeName.lastIndexOf('.'))
                : "";
        var qualifiedPattern = Pattern.compile("\\bnew\\s+(?:[\\w$.]+\\.)?"
                + Pattern.quote(typeName) + "\\s*\\(");
        var nestedImport = source.contains("import com.yo1no.gramarye.magic.definition.store."
                + typeName + ";")
                || source.contains("import static com.yo1no.gramarye.magic.definition.store."
                        + typeName + ";");
        var wildcardImport = !outer.isEmpty()
                && (source.contains("import com.yo1no.gramarye.magic.definition.store."
                                + outer + ".*;")
                        || source.contains("import static com.yo1no.gramarye.magic.definition.store."
                                + outer + ".*;"));
        var simplePattern = Pattern.compile("\\bnew\\s+" + Pattern.quote(simple) + "\\s*\\(");
        var qualifiedReferencePattern = Pattern.compile("\\b(?:[\\w$.]+\\.)?"
                + Pattern.quote(typeName) + "\\s*::\\s*new");
        var simpleReferencePattern = Pattern.compile(
                "(?<![\\w$.])" + Pattern.quote(simple) + "\\s*::\\s*new");
        var count = countMatches(qualifiedPattern, source);
        if (typeName.contains(".") && (nestedImport || wildcardImport)) {
            count += countMatches(simplePattern, source);
        }
        count += countMatches(qualifiedReferencePattern, source);
        if (typeName.contains(".") && (nestedImport || wildcardImport)) {
            count += countMatches(simpleReferencePattern, source);
        }
        return count;
    }

    private static int localConstructionCountSource(String source, String simpleTypeName) {
        return countMatches(
                Pattern.compile("\\bnew\\s+" + Pattern.quote(simpleTypeName) + "\\s*\\("),
                withoutCommentsAndLiterals(source));
    }

    private static int countMatches(Pattern pattern, String source) {
        var matcher = pattern.matcher(source);
        var count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String readSanitized(Path path) {
        try {
            return withoutCommentsAndLiterals(Files.readString(path));
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read source " + path, exception);
        }
    }

    private static String withoutCommentsAndLiterals(String source) {
        var result = new StringBuilder(source.length());
        var block = false;
        var line = false;
        var string = false;
        var character = false;
        var escaped = false;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (line) {
                if (current == '\n') {
                    line = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                }
            } else if (block) {
                if (current == '*' && next == '/') {
                    result.append("  ");
                    index++;
                    block = false;
                } else {
                    result.append(current == '\n' ? '\n' : ' ');
                }
            } else if (string || character) {
                result.append(current == '\n' ? '\n' : ' ');
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if ((string && current == '"') || (character && current == '\'')) {
                    string = false;
                    character = false;
                }
            } else if (current == '/' && next == '/') {
                result.append("  ");
                index++;
                line = true;
            } else if (current == '/' && next == '*') {
                result.append("  ");
                index++;
                block = true;
            } else if (current == '"') {
                result.append(' ');
                string = true;
            } else if (current == '\'') {
                result.append(' ');
                character = true;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new AssertionError("Unable to locate project root");
    }

    private static String simpleTopLevelName(String className) {
        var simple = className.substring(className.lastIndexOf('.') + 1);
        var nested = simple.indexOf('$');
        return nested < 0 ? simple : simple.substring(0, nested);
    }
}
