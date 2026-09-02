package com.yo1no.gramarye.magic.runtime.mana;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

final class ActionTransactionTestFixtures {
    static final ResourceLocation DAMAGE_ACTION_KEY =
            ResourceLocation.fromNamespaceAndPath("gramarye", "test_damage");
    static final UUID ACCOUNT_ID =
            UUID.fromString("70000000-0000-4000-8000-000000000001");

    private ActionTransactionTestFixtures() {}

    static DamageActionInvocation invocation() {
        return invocation(0L);
    }

    static DamageActionInvocation invocation(long manaCost) {
        return invocation(DAMAGE_ACTION_KEY, 25L, manaCost);
    }

    static DamageActionInvocation invocation(
            ResourceLocation key, long magnitude, long manaCost) {
        return new DamageActionInvocation(
                key,
                new EffectRequestId(101L),
                new SourceEventId(303L),
                new DamageTargetReference(UUID.fromString(
                        "70000000-0000-4000-8000-000000000002")),
                magnitude,
                manaCost,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
    }

    static ActionExecutorRegistry damageRegistry() {
        return registry(new DamageActionExecutor());
    }

    static ActionExecutorRegistry registry(ActionExecutor executor) {
        return new ActionExecutorRegistry(List.of(
                new ActionExecutorRegistration(DAMAGE_ACTION_KEY, executor)));
    }

    static ActionDamageTransactionEngine engine(EffectResolver resolver) {
        return engine(damageRegistry(), resolver);
    }

    static ActionDamageTransactionEngine engine(
            ActionExecutorRegistry registry, EffectResolver resolver) {
        return new ActionDamageTransactionEngine(
                registry,
                resolver,
                new EffectExecutionEngine(),
                new ManaTransactionService());
    }

    static EffectCommitPlan plan(int stepCount) {
        List<EffectStep> steps = new ArrayList<>();
        for (int index = 0; index < stepCount; index++) {
            steps.add(step(index));
        }
        return new EffectCommitPlan(steps, 0);
    }

    static DamageEffectStep step(int index) {
        DamageActionInvocation invocation = invocation();
        return new DamageEffectStep(
                index,
                invocation.target(),
                invocation.magnitude(),
                1,
                0);
    }

    static EffectResolver resolverFor(EffectCommitPlan plan) {
        return (request, capacity) -> new AcceptedEffectResolution(plan);
    }

    static List<EffectTraceStage> stages(ActionDamageTransactionResult result) {
        return stages(result.effectResult());
    }

    static List<EffectTraceStage> stages(EffectExecutionResult result) {
        return result.trace().entries().stream().map(EffectTraceEntry::stage).toList();
    }

    static final class RecordingManaAccount implements ManaAccountAccess {
        private boolean logicThread;
        private UUID accountId;
        private ManaAvailability availability;
        private long balance;
        private final List<String> order;
        private int threadChecks;
        private int accountIdReads;
        private int availabilityReads;
        private int balanceReads;
        private int balanceWrites;

        RecordingManaAccount(long balance) {
            this(true, ACCOUNT_ID, ManaAvailability.AVAILABLE, balance, new ArrayList<>());
        }

        RecordingManaAccount(
                boolean logicThread,
                UUID accountId,
                ManaAvailability availability,
                long balance,
                List<String> order) {
            this.logicThread = logicThread;
            this.accountId = accountId;
            this.availability = Objects.requireNonNull(availability, "availability");
            this.balance = balance;
            this.order = Objects.requireNonNull(order, "order");
        }

        @Override
        public boolean isLogicThread() {
            threadChecks++;
            order.add("mana-thread");
            return logicThread;
        }

        @Override
        public UUID accountId() {
            accountIdReads++;
            order.add("mana-account");
            return accountId;
        }

        @Override
        public ManaAvailability availability() {
            availabilityReads++;
            order.add("mana-availability");
            return availability;
        }

        @Override
        public long balance() {
            balanceReads++;
            order.add("mana-balance");
            return balance;
        }

        @Override
        public void writeBalance(long replacement) {
            balanceWrites++;
            order.add("mana-write");
            balance = replacement;
        }

        long currentBalance() {
            return balance;
        }

        int threadChecks() {
            return threadChecks;
        }

        int accountIdReads() {
            return accountIdReads;
        }

        int availabilityReads() {
            return availabilityReads;
        }

        int balanceReads() {
            return balanceReads;
        }

        int balanceWrites() {
            return balanceWrites;
        }

        int totalAccesses() {
            return threadChecks
                    + accountIdReads
                    + availabilityReads
                    + balanceReads
                    + balanceWrites;
        }

        List<String> order() {
            return List.copyOf(order);
        }

        void setLogicThread(boolean replacement) {
            logicThread = replacement;
        }

        void setAccountId(UUID replacement) {
            accountId = replacement;
        }

        void setAvailability(ManaAvailability replacement) {
            availability = Objects.requireNonNull(replacement, "availability");
        }

        void setBalance(long replacement) {
            balance = replacement;
        }

        void resetCounters() {
            threadChecks = 0;
            accountIdReads = 0;
            availabilityReads = 0;
            balanceReads = 0;
            balanceWrites = 0;
            order.clear();
        }
    }

    static final class TransactionRecordingPort implements DamageEffectCommitPort {
        private final boolean available;
        private final List<EffectStepOutcome> outcomes;
        private final List<Integer> committedIndexes = new ArrayList<>();
        private final List<String> order;
        private int availabilityChecks;

        TransactionRecordingPort(boolean available, List<EffectStepOutcome> outcomes) {
            this(available, outcomes, new ArrayList<>());
        }

        TransactionRecordingPort(
                boolean available,
                List<EffectStepOutcome> outcomes,
                List<String> order) {
            this.available = available;
            this.outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
            this.order = Objects.requireNonNull(order, "order");
        }

        static TransactionRecordingPort applyingAll() {
            return new TransactionRecordingPort(true, List.of());
        }

        @Override
        public boolean isAvailable() {
            availabilityChecks++;
            order.add("port-availability");
            return available;
        }

        @Override
        public EffectStepOutcome commitDamage(DamageEffectStep step) {
            int invocation = committedIndexes.size();
            committedIndexes.add(step.index());
            order.add("port-step-" + step.index());
            return invocation < outcomes.size()
                    ? outcomes.get(invocation)
                    : EffectStepOutcome.applied(1);
        }

        int availabilityChecks() {
            return availabilityChecks;
        }

        List<Integer> committedIndexes() {
            return List.copyOf(committedIndexes);
        }

        List<String> order() {
            return List.copyOf(order);
        }
    }

    static final class TransactionRecordingGuard implements EffectExecutionGuard {
        private final Function<EffectGuardPoint, EffectGuardDecision> decisions;
        private final List<EffectGuardPoint> checks = new ArrayList<>();
        private final List<String> order;

        TransactionRecordingGuard(
                Function<EffectGuardPoint, EffectGuardDecision> decisions) {
            this(decisions, new ArrayList<>());
        }

        TransactionRecordingGuard(
                Function<EffectGuardPoint, EffectGuardDecision> decisions,
                List<String> order) {
            this.decisions = Objects.requireNonNull(decisions, "decisions");
            this.order = Objects.requireNonNull(order, "order");
        }

        static TransactionRecordingGuard allowing() {
            return new TransactionRecordingGuard(point -> EffectGuardDecision.ALLOWED);
        }

        @Override
        public EffectGuardDecision check(EffectGuardPoint point) {
            checks.add(point);
            order.add("guard-" + point.kind().name() + "-" + point.stepIndex());
            return decisions.apply(point);
        }

        List<EffectGuardPoint> checks() {
            return List.copyOf(checks);
        }

        List<String> order() {
            return List.copyOf(order);
        }
    }

    static final class TransactionRecordingResolver implements EffectResolver {
        private final EffectResolver delegate;
        private final List<String> order;
        private int calls;

        TransactionRecordingResolver(EffectResolver delegate) {
            this(delegate, new ArrayList<>());
        }

        TransactionRecordingResolver(EffectResolver delegate, List<String> order) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.order = Objects.requireNonNull(order, "order");
        }

        @Override
        public EffectResolution resolve(
                EffectRequest request, int suppliedChildIntentCapacity) {
            calls++;
            order.add("resolver");
            return delegate.resolve(request, suppliedChildIntentCapacity);
        }

        int calls() {
            return calls;
        }

        List<String> order() {
            return List.copyOf(order);
        }
    }
}
