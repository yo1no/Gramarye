package com.yo1no.gramarye;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/** Excluded two-case observer. It never starts a command context, reward, hit, or work. */
public final class P11L1WorkRewardProbe {
    public enum Mode { PARTIAL_FUNCTION, MULTI_UUID_QCTX }
    private static final String PEER_TAG = "p11_l1_qctx_peer";
    private static final String PARTIAL_PREFIX = "experience add @s 3 points";
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static Run active;
    private P11L1WorkRewardProbe() {}

    public static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Mode mode) {
        String expected = mode == Mode.PARTIAL_FUNCTION ? "l1-partial-reward-function" : "l1-work-multi-uuid-qctx";
        require((expected.equals(System.getProperty("gramarye.p11.online.case", "")) || mode == Mode.MULTI_UUID_QCTX && P11L1ContextRefusalProbe.selected()) && active == null
                && server != null && server.isSameThread() && current(server, actor) && current(server, peer)
                && actor != peer && !actor.getUUID().equals(peer.getUUID()), "EXACT_CASE_TWO_REAL_ACTORS");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        require(owner != null && owner == P11NativeStorageBoundary.nativeSourceOwner(peer), "SAME_FOUNDATION");
        var body = owner.nativeRecipient(actor); var other = owner.nativeRecipient(peer);
        require(body != null && other != null && body.account != other.account && owner.canCopy(body)
                && owner.canCopy(other) && roots(body, P11ControlBudgets.Root.WORK) == 0
                && roots(body, P11ControlBudgets.Root.NATIVE_CREDIT) == 0
                && roots(body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                && roots(other, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0, "EXISTING_MANAGED_SOURCES_NO_PREVIOUS_CAUSE");
        var run = new Run(server, actor, peer, mode, owner, body, other);
        require(run.advancement != null && run.peerAdvancement != null
                && !actor.getAdvancements().getOrStartProgress(run.advancement).isDone()
                && !peer.getAdvancements().getOrStartProgress(run.peerAdvancement).isDone()
                && !actor.getTags().contains("p11_l1_unreachable_tail")
                && !actor.getTags().contains("p11_l1_qctx_outer")
                && !actor.getTags().contains("p11_l1_qctx_outer_done")
                && !peer.getTags().contains("p11_l1_qctx_peer_done"), "FRESH_FIXED_FIXTURES");
        if (mode == Mode.MULTI_UUID_QCTX) {
            require(peer.getTags().contains(PEER_TAG) && !actor.getTags().contains(PEER_TAG)
                    && server.getPlayerList().getPlayers().stream().filter(p -> p.getTags().contains(PEER_TAG)).count() == 1,
                    "ONLY_AUTHENTICATED_PEER_HAS_NATIVE_FIXTURE_TAG");
        }
        active = run;
    }

    public static void accepted(ServerPlayer actor, Object actualInstance, Object result) {
        var run = active; if (run == null || actor != run.actor) { return; }
        require(run.server.isSameThread() && run.instance == null && actualInstance instanceof ServerSlot.InstanceState
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "ACTUAL_ACCEPTANCE_ONCE");
        var instance = (ServerSlot.InstanceState) actualInstance;
        require(instance.id.equals(((RuntimeAdmissionResult.AcceptedMemoryOnly) result).eventToken().skillInstanceId())
                && instance.work != null && !instance.lease.pin.isClosed()
                && instance.hasP9AuthenticatedActorWitness(actor), "EXACT_WORK_WITNESS_PIN");
        run.instance = instance;
    }

    public static String targetTag() {
        require(active != null, "ARM_BEFORE_TARGET");
        return active.mode == Mode.PARTIAL_FUNCTION ? "p11_l1_partial_target" : "p11_l1_qctx_target";
    }
    public static void target(LivingEntity victim) {
        var run = active;
        require(run != null && run.server.isSameThread() && run.victim == null && victim != null
                && victim.level().getServer() == run.server && victim.isAddedToLevel() && !victim.isRemoved()
                && victim.getType() == net.minecraft.world.entity.EntityType.CHICKEN
                && victim.getTags().contains(targetTag()) && !victim.getTags().contains("p11_l1_target"), "EXACT_NATIVE_CHICKEN");
        run.victim = victim;
    }
    public static void transferred(Object projectile) {
        var run = active; if (run == null) { return; }
        require(run.server.isSameThread() && run.instance != null && run.projectile == null
                && projectile instanceof P9StarterProjectile, "TRANSFER_ONCE");
        run.projectile = (P9StarterProjectile) projectile;
        require(run.projectile.getOwner() == run.actor && run.projectile.isAddedToLevel(), "REAL_TRANSFER_EXACT_A");
    }
    public static void damageEntering(LivingEntity victim, DamageSource cause) {
        var run = active;
        if (run == null || victim != run.victim || cause.getDirectEntity() != run.projectile) { return; }
        require(run.server.isSameThread() && !run.hurtActive && run.hurtEntries++ == 0
                && cause.getEntity() == run.actor && run.instance != null && run.instance.work != null
                && run.actor.isRemoved() && !run.actor.connection.getConnection().isConnected()
                && run.server.getPlayerList().getPlayer(run.actor.getUUID()) != run.actor,
                "FIRST_NATURAL_HURT_AFTER_NORMAL_LOGOUT");
        run.body = run.initialBody.account.current;
        require(run.body != null && run.owner.nativeRecipient(run.body.actor) == run.body && run.owner.canCopy(run.body)
                && roots(run.body, P11ControlBudgets.Root.WORK) > 0
                && roots(run.body, P11ControlBudgets.Root.OPERATION) > 0
                && roots(run.body, P11ControlBudgets.Root.NATIVE_CREDIT) == 0
                && roots(run.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0, "W_FOP_BEFORE_FIRST_N");
        run.epoch = run.body.source.epoch(); run.peerEpoch = run.peerBody.source.epoch();
        run.recipient = run.body.actor; run.xp = run.recipient.totalExperience;
        run.bread = run.recipient.getInventory().countItem(Items.BREAD);
        run.peerXp = run.peer.totalExperience; run.hurtActive = true;
    }
    public static void damageReturned(LivingEntity victim, DamageSource cause, float amount, boolean normal) {
        var run = active;
        if (run == null || victim != run.victim || cause.getDirectEntity() != run.projectile) { return; }
        require(run.hurtActive && run.hurtReturns++ == 0 && cause.getEntity() == run.actor && amount == 4 && normal
                && run.outerGrantReturns == 1 && run.outerFunctionReturns == 1 && !run.observerFailed,
                "ORIGINAL_HURT_NORMAL_AFTER_NATIVE_FUNCTION_POLICY");
        run.hurtActive = false;
    }

    /** Called by the exact grant wrapper; reward object identity, not UUID, selects the fixture. */
    public static int grantEntered(AdvancementRewards reward) {
        var run = active; if (run == null) { return 0; }
        int role = reward == run.advancement.value().rewards() ? 1 : reward == run.peerAdvancement.value().rewards() ? 2 : 0;
        if (role == 0) { return 0; }
        require(run.server.isSameThread() && run.hurtActive && run.instance.work != null
                && run.victim.getKillCredit() == run.actor && roots(run.body, P11ControlBudgets.Root.NATIVE_CREDIT) > 0,
                "NATIVE_DEATH_CREDIT_INSIDE_W_HURT");
        if (role == 1) { require(run.outerGrantEntries++ == 0 && run.outerGrantDepth++ == 0, "ONE_NATURAL_GRANT"); }
        else { require(run.mode == Mode.MULTI_UUID_QCTX && run.outerGrantDepth == 1 && run.peerGrantEntries++ == 0,
                "ONE_NESTED_PEER_GRANT"); run.peerGrantDepth++; }
        return role;
    }
    public static void rewardExperience(AdvancementRewards reward, ServerPlayer recipient, int amount) {
        var run = active; if (run == null) { return; }
        if (reward == run.advancement.value().rewards()) {
            require(run.outerGrantDepth == 1 && recipient == run.recipient && amount == 7
                    && run.rewardXpCalls++ == 0 && fixed(run), "WHOLE_REWARD_EXACT_R");
        } else if (reward == run.peerAdvancement.value().rewards()) {
            require(run.peerGrantDepth == 1 && recipient == run.peer && amount == 11
                    && run.peerRewardXpCalls++ == 0 && fixed(run), "PEER_REWARD_OWN_R");
        }
    }
    public static void grantFinished(int role, boolean normal, Throwable escaping) {
        var run = active; if (run == null || role == 0) { return; }
        try {
            require(normal && escaping == null && fixed(run), "ORIGINAL_REWARD_RETURN");
            if (role == 1) { run.outerGrantDepth--; run.outerGrantReturns++; }
            else {
                require(run.peerCommands == 0 && run.peerFunctionReturns == 1 && liveContext(run, 2),
                        "NESTED_RETURN_BEFORE_QUEUED_PEER_DRAIN");
                run.peerGrantDepth--; run.peerGrantReturns++; run.nestedReturnedWithBothRoots = true;
            }
        } catch (RuntimeException | Error observerFailure) { run.observerFailed = true; }
    }

    public static int functionEntered(CommandFunction<?> function, CommandSourceStack source) {
        var run = active; if (run == null) { return 0; }
        int role = function.id().equals(run.outerFunction) ? 1 : function.id().equals(id("l1_qctx_peer_reward")) ? 2 : 0;
        if (role == 0) { return 0; }
        require(run.server.isSameThread() && run.hurtActive && fixed(run), "FUNCTION_FROM_ACTUAL_REWARD");
        if (role == 1) {
            require(run.outerGrantDepth == 1 && source.getEntity() == run.recipient && run.outerFunctionEntries++ == 0,
                    "ORIGINAL_OUTER_FUNCTION_R"); run.outerFunctionActive = true;
        } else {
            require(run.mode == Mode.MULTI_UUID_QCTX && run.outerFunctionActive && run.peerGrantDepth == 1
                    && source.getEntity() == run.peer && run.peerFunctionEntries++ == 0 && liveContext(run, 1),
                    "NESTED_PEER_FUNCTION_SOURCE");
        }
        return role;
    }
    public static void functionFinished(int role, boolean normal, Throwable escaping) {
        var run = active; if (run == null || role == 0) { return; }
        try {
            require(normal && escaping == null && fixed(run), "ORIGINAL_MANAGER_NORMAL_RETURN");
            if (role == 2) {
                require(run.peerCommands == 0 && liveContext(run, 2), "PEER_ENQUEUED_SAME_CONTEXT"); run.peerFunctionReturns++;
            } else {
                require(run.context != null, "NATIVE_CONTEXT_OBSERVED");
                var end = P11NativeOperationBoundary.observe(run.context);
                require(end.terminal() && end.tracerClosed() && end.retainedBindings() == 0
                        && roots(run.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                        && roots(run.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                        "TRUE_OUTER_FINALLY_RELEASED_BOTH_Q");
                if (run.mode == Mode.PARTIAL_FUNCTION) {
                    require(run.injections == 1 && run.nativeCatchReturns == 1 && !end.outerNormal()
                            && "THREW".equals(end.drain()) && run.outerCommands == 1, "PARTIAL_NATIVE_CATCH_AND_QUEUE_TERMINAL");
                } else {
                    require(end.outerNormal() && "EMPTY".equals(end.drain()) && run.outerCommands == 4
                            && run.peerCommands == 3 && run.nestedReturnedWithBothRoots && run.injections == 0,
                            "MULTI_ACCOUNT_NORMAL_DRAIN");
                }
                run.terminal = end; run.outerFunctionActive = false; run.outerFunctionReturns++;
            }
        } catch (RuntimeException | Error observerFailure) { run.observerFailed = true; }
    }

    /** AFTER the unique original Brigadier executable. No direct command or receiver call. */
    public static void afterCommand(ExecutionCommandSource<?> source, ExecutionContext<?> context, String command) {
        var run = active; if (run == null || !run.outerFunctionActive) { return; }
        require(run.server.isSameThread() && run.hurtActive && source instanceof CommandSourceStack && fixed(run), "NATIVE_COMMAND_SCOPE");
        if (run.context == null) { run.context = context; }
        require(run.context == context, "SAME_OWNING_CONTEXT");
        var actor = ((CommandSourceStack) source).getEntity();
        if (actor == run.recipient) {
            run.outerCommands++;
            require(liveContext(run, run.peerFunctionEntries == 0 ? 1 : 2), "OUTER_Q_BINDINGS");
            if (run.mode == Mode.PARTIAL_FUNCTION) {
                require(run.outerCommands == 1 && PARTIAL_PREFIX.equals(command) && run.injections == 0
                        && run.recipient.totalExperience == run.xp + 10, "ACTUAL_XP_PREFIX_BEFORE_FAULT");
                run.injections++; throw run.primary;
            }
        } else {
            require(run.mode == Mode.MULTI_UUID_QCTX && actor == run.peer && run.peerGrantReturns == 1
                    && liveContext(run, 2), "PEER_QUEUED_COMMAND_OWN_BINDING"); run.peerCommands++;
            P11L1ContextRefusalProbe.afterCommand(run.server, run.actor, run.peer, run.instance, context, command);
        }
    }
    public static boolean selectedCatch(Object function, Object failure) {
        var run = active;
        return run != null && run.mode == Mode.PARTIAL_FUNCTION && run.outerFunctionActive
                && run.outerFunction.equals(function) && failure == run.primary && run.injections == 1;
    }
    public static void catchReturned(boolean selected) {
        if (selected && active != null) { active.nativeCatchReturns++; }
    }
    /** Original failure accounting, not an injected failure or a guessed terminal allowance. */
    public static void operationFailureReturned(Object owner, Object body) {
        var run = active;
        if (run == null || owner != run.owner || body != run.body) { return; }
        if (!run.server.isSameThread() || run.mode != Mode.PARTIAL_FUNCTION || !run.outerFunctionActive
                || run.injections != 1 || ++run.nativeFailureReturns > 3) { run.observerFailed = true; }
    }
    public static int expectedSourceFailures() {
        require(active != null && !active.observerFailed && active.outerFunctionReturns == 1, "FAULT_ACCOUNTING_BEFORE_TERMINAL");
        return active.nativeFailureReturns;
    }

    public static boolean readyToFinish() {
        return active != null && active.instance != null && active.instance.work == null && active.hurtReturns == 1;
    }
    /** Local gameplay/Qctx result, explicitly not a substitute for parent's native saves/reconnect. */
    public static Map<String, Object> finish() {
        var run = active;
        require(run != null && run.server.isSameThread() && !run.observerFailed && !run.hurtActive
                && !run.outerFunctionActive && run.outerGrantDepth == 0 && run.peerGrantDepth == 0
                && run.hurtEntries == 1 && run.hurtReturns == 1 && run.outerGrantEntries == 1 && run.outerGrantReturns == 1
                && run.outerFunctionEntries == 1 && run.outerFunctionReturns == 1 && run.rewardXpCalls == 1
                && run.instance.work == null && run.instance.lease.pin.isClosed() && fixed(run)
                && roots(run.body, P11ControlBudgets.Root.OPERATION) == 0
                && roots(run.peerBody, P11ControlBudgets.Root.OPERATION) == 0
                && run.recipient.totalExperience == run.xp + 10
                && run.recipient.getInventory().countItem(Items.BREAD) == run.bread + 1
                && run.recipient.getRecipeBook().contains(BREAD)
                && run.recipient.getAdvancements().getOrStartProgress(run.advancement).isDone(), "LOCAL_WORK_REWARD_TERMINAL");
        require(!run.recipient.getTags().contains("p11_l1_unreachable_tail")
                && run.nativeFailureReturns == (run.mode == Mode.PARTIAL_FUNCTION ? 3 : 0), "UNREPLAYED_TAIL_AND_ORIGINAL_FAILURE_ACCOUNTING");
        if (run.mode == Mode.MULTI_UUID_QCTX) {
            require(run.peer.totalExperience == run.peerXp + 18 && run.peerGrantEntries == 1 && run.peerGrantReturns == 1
                    && run.peerRewardXpCalls == 1 && run.peerFunctionEntries == 1 && run.peerFunctionReturns == 1
                    && run.recipient.getTags().contains("p11_l1_qctx_outer")
                    && run.recipient.getTags().contains("p11_l1_qctx_outer_done")
                    && run.peer.getTags().contains("p11_l1_qctx_peer_done")
                    && !run.recipient.getTags().contains("p11_l1_qctx_peer_done")
                    && !run.peer.getTags().contains("p11_l1_qctx_outer_done")
                    && run.peer.getAdvancements().getOrStartProgress(run.peerAdvancement).isDone(), "ISOLATED_BOTH_NATIVE_RESULTS");
        } else { require(run.peer.totalExperience == run.peerXp && run.peerGrantEntries == 0, "PARTIAL_PEER_UNCHANGED"); }
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "NAMED_NATURAL_WORK_REWARD_LOCAL_PROOF_NOT_PHYSICAL_ACCEPTANCE");
        facts.put("mode", run.mode.name()); facts.put("actualHurtEntries", run.hurtEntries); facts.put("actualHurtNormalReturns", run.hurtReturns);
        facts.put("outerRewardReturns", run.outerGrantReturns); facts.put("peerRewardReturns", run.peerGrantReturns);
        facts.put("outerFunctionReturns", run.outerFunctionReturns); facts.put("ownedFaultInjections", run.injections);
        facts.put("originalSamePrimaryCatchReturns", run.nativeCatchReturns); facts.put("outerCommandReturns", run.outerCommands);
        facts.put("actualOriginalNativeFailureAccountingReturns", run.nativeFailureReturns);
        facts.put("peerCommandReturns", run.peerCommands); facts.put("nestedReturnHeldBothQBeforePeerDrain", run.nestedReturnedWithBothRoots);
        facts.put("context", Map.of("drain", run.terminal.drain(), "terminal", run.terminal.terminal(),
                "outerNormal", run.terminal.outerNormal(), "tracerClosed", run.terminal.tracerClosed(), "bindings", run.terminal.retainedBindings()));
        facts.put("alphaEpoch", run.epoch); facts.put("peerEpoch", run.peerEpoch); facts.put("alphaXpDelta", 10);
        facts.put("peerXpDelta", run.peer.totalExperience - run.peerXp); facts.put("nativeRecipeAndOneLootPrefix", true);
        facts.put("source", P11C4aEvidence.sourceObservation(run.owner.diagnostics(run.actor.getUUID())));
        facts.put("peerSource", P11C4aEvidence.sourceObservation(run.owner.diagnostics(run.peer.getUUID())));
        return Map.copyOf(facts);
    }
    public static void abort() { active = null; }
    private static boolean liveContext(Run run, int bindings) {
        return run.context != null && !P11NativeOperationBoundary.observe(run.context).terminal()
                && P11NativeOperationBoundary.observe(run.context).retainedBindings() == bindings
                && roots(run.body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1
                && roots(run.peerBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == (bindings == 2 ? 1 : 0);
    }
    private static boolean fixed(Run run) {
        return run.body != null && run.owner.nativeRecipient(run.recipient) == run.body && run.owner.canCopy(run.body)
                && run.body.source.epoch() == run.epoch && run.owner.nativeRecipient(run.peer) == run.peerBody
                && run.owner.canCopy(run.peerBody) && run.peerBody.source.epoch() == run.peerEpoch;
    }
    private static long roots(P11QualifiedSourceOwner.Body body, P11ControlBudgets.Root kind) {
        return body.account.nativeCounts[kind.ordinal()];
    }
    private static boolean current(MinecraftServer server, ServerPlayer actor) {
        return actor != null && actor.getServer() == server && !actor.isFakePlayer() && !actor.isRemoved() && actor.isAlive()
                && actor.connection != null && actor.connection.player == actor && actor.connection.getConnection().isConnected()
                && actor.connection.getConnection().getPacketListener() == actor.connection
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", path); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "L1_WORK_REWARD_" + code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Mode mode;
        final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body initialBody, peerBody;
        final AdvancementHolder advancement, peerAdvancement; final ResourceLocation outerFunction;
        final RuntimeException primary = new IllegalStateException("L1_OWNED_POST_ORIGINAL_XP_FUNCTION_FAULT");
        ServerSlot.InstanceState instance; LivingEntity victim; P9StarterProjectile projectile; ServerPlayer recipient;
        P11QualifiedSourceOwner.Body body; ExecutionContext<?> context; P11NativeOperationBoundary.ContextObservation terminal;
        long epoch, peerEpoch; int xp, bread, peerXp, hurtEntries, hurtReturns, outerGrantEntries, outerGrantReturns,
                peerGrantEntries, peerGrantReturns, outerGrantDepth, peerGrantDepth, rewardXpCalls, peerRewardXpCalls,
                outerFunctionEntries, outerFunctionReturns, peerFunctionEntries, peerFunctionReturns,
                outerCommands, peerCommands, injections, nativeCatchReturns, nativeFailureReturns;
        boolean hurtActive, outerFunctionActive, observerFailed, nestedReturnedWithBothRoots;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Mode mode, P11QualifiedSourceOwner owner,
                P11QualifiedSourceOwner.Body initialBody, P11QualifiedSourceOwner.Body peerBody) {
            this.server = server; this.actor = actor; this.peer = peer; this.mode = mode;
            this.owner = owner; this.initialBody = initialBody; this.peerBody = peerBody;
            advancement = server.getAdvancements().get(id(mode == Mode.PARTIAL_FUNCTION ? "l1_partial_kill" : "l1_qctx_kill"));
            peerAdvancement = server.getAdvancements().get(id("l1_qctx_peer"));
            outerFunction = id(mode == Mode.PARTIAL_FUNCTION ? "l1_partial_reward" : "l1_qctx_outer");
        }
    }
}
