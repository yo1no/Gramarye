package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Closed native callsites delegated to the existing foundation. Public visibility is required
 * when Mixin moves the handlers into Minecraft classes; no actor locator or grant is exposed.
 */
public final class P11NativeOperationBoundary {
    private static volatile P11FoundationService root;
    private static final ThreadLocal<OperationScope> OPERATION = new ThreadLocal<>();
    private static final ThreadLocal<CommandInvocation> COMMAND = new ThreadLocal<>();
    private static final ThreadLocal<Credit> CREDIT = new ThreadLocal<>();
    private static long observerFailures;

    private P11NativeOperationBoundary() {}

    static void install(P11FoundationService owner) {
        if (root != null && root != owner) { throw new IllegalStateException("P11_NATIVE_ROOT_ALREADY_INSTALLED"); }
        root = owner;
    }

    private static P11QualifiedSourceOwner owner(ServerPlayer actor) {
        return root == null || actor == null ? null : root.sourceOwner(actor.getServer());
    }

    /** Called before PA's getOrStartProgress, which itself may change the native map. */
    public static OperationScope beginAdvancement(PlayerAdvancements canonical, ServerPlayer actor) {
        var scope = begin(actor);
        try {
            var source = owner(actor);
            var body = source == null ? null : source.canonicalAdvancements(canonical);
            if (body != null) { source.independentMutation(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS); }
        } catch (RuntimeException | Error secondary) { observerFailed(); }
        return scope;
    }

    /** Excluded companion entry only. Validation is not native continuity authorization. */
    static <T> T engineering(ServerPlayer actor, Supplier<T> nativeWork) {
        var source = owner(actor);
        if (source == null || actor.isFakePlayer() || !actor.getServer().isSameThread()
                || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor
                || actor.connection == null || actor.connection.player != actor
                || !actor.connection.getConnection().isConnected()
                || actor.connection.getConnection().getPacketListener() != actor.connection
                || source.nativeRecipient(actor) == null) {
            throw new IllegalStateException("P11_ENGINEERING_REQUIRES_AUTHENTICATED_CURRENT_SOURCE");
        }
        return nativeWork.get();
    }

    /** Entered only after native predicates selected this exact captured listener. */
    public static void criterion(ServerPlayer actor, net.minecraft.advancements.CriterionTrigger.Listener<?> listener,
            PlayerAdvancements advancements, Operation<Void> original) {
        var scope = begin(actor);
        boolean normal = false;
        try { original.call(listener, advancements); normal = true; }
        finally { end(scope, normal); }
    }

    /** One fixed argument supplies every native reward step, including its function source. */
    public static void grant(AdvancementRewards reward, ServerPlayer supplied, Operation<Void> original) {
        var scope = begin(supplied);
        boolean normal = false;
        try {
            original.call(scope == null ? supplied : scope.binding.recipient);
            normal = true;
        } finally { end(scope, normal); }
    }

    /** Used only by the unique increaseScore invoke in ServerPlayer.awardKillScore. */
    public static void killScore(ServerPlayer supplied, int amount, Operation<Void> original) {
        var scope = begin(supplied);
        boolean normal = false;
        try {
            original.call(scope == null ? supplied : scope.binding.recipient, amount);
            normal = true;
        } finally { end(scope, normal); }
    }

    private static OperationScope begin(ServerPlayer actor) {
        return begin(actor, null);
    }

    private static OperationScope begin(ServerPlayer actor, Context context) {
        if (actor == null) { return null; }
        Binding binding = null;
        boolean retained = false;
        try {
            var source = owner(actor);
            if (source == null) { return null; }
            source.nativeEscape(actor);
            var continuity = P11LiveTransitionBoundary.nativeContinuity(source);
            boolean retainedCause = false;
            var previous = OPERATION.get();
            for (var outer = previous; outer != null; outer = outer.previous) {
                if (outer.binding.owner == source
                        && (outer.origin == actor || outer.binding.recipient == actor)) {
                    binding = outer.binding;
                    retainedCause = true;
                    break;
                }
            }
            if (binding == null && continuity != null) {
                binding = bindingInContext(context, source, actor);
                retainedCause = binding != null;
            }
            if (binding == null) {
                var credit = CREDIT.get();
                boolean fieldCause = continuity != null && credit != null && !credit.closed
                        && credit.actor == actor && credit.owner == source;
                var body = fieldCause
                        ? source.nativeRecipient(credit.body) : source.nativeRecipient(actor);
                if (body == null) { return null; }
                binding = new Binding(source, body);
                retainedCause = fieldCause;
            }
            // No UUID lookup or idle native object grants detached continuity. The source
            // capability and the actual retained N/Fop/Qctx cause are both required.
            if (source.nativeRecipient(binding.recipient) != binding.body
                    || (source.detachedPresence(binding.recipient) && (continuity == null || !retainedCause))) {
                return null;
            }
            if (!source.retainNativeRoot(binding.body, P11ControlBudgets.Root.OPERATION)) { return null; }
            retained = true;
            source.nativeMutation(binding.body);
            var scope = new OperationScope(binding, actor, previous);
            OPERATION.set(scope);
            return scope;
        } catch (RuntimeException | Error secondary) {
            if (retained) { release(binding, P11ControlBudgets.Root.OPERATION); }
            observerFailed();
            return null;
        }
    }

    public static void end(OperationScope scope, boolean normal) {
        if (scope == null || scope.closed) { return; }
        scope.closed = true;
        if (OPERATION.get() != scope) { observerFailed(); return; }
        if (scope.previous == null) { OPERATION.remove(); } else { OPERATION.set(scope.previous); }
        afterNative(scope.binding);
        try {
            if (!normal) { scope.binding.owner.nativeOperationFailed(scope.binding.body); }
        } catch (RuntimeException | Error secondary) { observerFailed(); }
        finally { release(scope.binding, P11ControlBudgets.Root.OPERATION); }
    }

    /** The actual native field owns this token; no global collection points back at victims. */
    public static Credit acquireCredit(Entity holder, ServerPlayer actor) {
        if (actor == null || holder.level().isClientSide || holder.level().getServer() != actor.getServer()) { return null; }
        try {
            var source = owner(actor);
            if (source != null) { source.nativeEscape(actor); }
            var body = source == null ? null : source.nativeRecipient(actor);
            if (body == null) { return null; }
            var credit = new Credit(source, body, holder, actor);
            return source.retainNativeRoot(body, P11ControlBudgets.Root.NATIVE_CREDIT) ? credit : null;
        } catch (RuntimeException | Error secondary) { observerFailed(); return null; }
    }

    /** A field write first reserves its successor responsibility, then releases its old one. */
    public static Credit fieldWritten(Credit previous, Credit replacement, boolean written) {
        if (written) { releaseCredit(previous); return replacement; }
        releaseCredit(replacement);
        return previous;
    }

    public static void releaseCredit(Credit credit) {
        if (credit == null || credit.closed) { return; }
        credit.closed = true;
        try { credit.owner.releaseNativeRoot(credit.body, P11ControlBudgets.Root.NATIVE_CREDIT); }
        catch (RuntimeException | Error secondary) { observerFailed(); }
    }

    /** A verified field value supplies cause provenance across death/drop/sculk consumers. */
    public static CreditScope beginCredit(Entity holder, ServerPlayer fieldValue, Credit credit) {
        if (credit == null || credit.closed || credit.holder != holder || credit.actor != fieldValue) { return null; }
        var previous = CREDIT.get();
        CREDIT.set(credit);
        var operation = begin(fieldValue);
        return new CreditScope(credit, previous, operation);
    }

    public static void endCredit(CreditScope scope, boolean normal) {
        if (scope == null || scope.closed) { return; }
        scope.closed = true;
        try { end(scope.operation, normal); }
        finally {
            if (CREDIT.get() == scope.credit) {
                if (scope.previous == null) { CREDIT.remove(); } else { CREDIT.set(scope.previous); }
            } else { observerFailed(); }
        }
    }

    public interface CreditAccess {
        CreditScope p11$beginKillCredit();
        CreditScope p11$beginMobCredit(ServerPlayer exactFieldActor);
        void p11$beginNativeConsumers();
        void p11$endNativeConsumers();
        void p11$nativeCreditRevived();
        void p11$releaseCreditFields();
    }

    public static CreditLifetime createCreditLifetime() { return new CreditLifetime(); }

    public static void beginCreditConsumers(CreditLifetime lifetime) {
        try { lifetime.facts.enter(); }
        catch (RuntimeException | Error secondary) { observerFailed(); }
    }

    public static boolean creditRemoval(CreditLifetime lifetime) {
        try { return lifetime.facts.removed(); }
        catch (RuntimeException | Error secondary) { observerFailed(); return false; }
    }

    public static void creditRevived(CreditLifetime lifetime) {
        try { lifetime.facts.revived(); }
        catch (RuntimeException | Error secondary) { observerFailed(); }
    }

    public static boolean endCreditConsumers(CreditLifetime lifetime) {
        try { return lifetime.facts.leave(); }
        catch (RuntimeException | Error secondary) { observerFailed(); return false; }
    }

    /** Kept on the original LivingEntity, not in a coordinator collection. */
    public static final class CreditLifetime {
        private final P11NativeCreditLifetime facts = new P11NativeCreditLifetime();
        private CreditLifetime() {}
    }

    public interface DragonCreditAccess {
        void p11$releaseDragonCredit();
    }

    /** Metadata is attached to the original queue object and cannot execute commands. */
    public interface ContextAccess {
        Context p11$nativeContext();
    }

    public static Context createContext(ExecutionContext<?> exact) { return new Context(exact); }

    public static void commands(CommandSourceStack source,
            Consumer<ExecutionContext<CommandSourceStack>> consumer, ExecutionContext<?> existing,
            Operation<Void> original) {
        var previous = COMMAND.get();
        var invocation = new CommandInvocation(previous, existing, begin(source.getPlayer(), nativeContext(existing)));
        COMMAND.set(invocation);
        boolean normal = false;
        try {
            Consumer<ExecutionContext<CommandSourceStack>> observing = context -> {
                invocation.context = context instanceof ContextAccess access ? access.p11$nativeContext() : null;
                if (invocation.context != null && invocation.context.exact == context) {
                    // This occurs before the consumer performs its first enqueue.
                    attach(invocation.context, invocation.operation);
                }
                consumer.accept(context);
            };
            original.call(source, observing);
            normal = true;
        } finally {
            // Original try-with-resources, tracer close and CURRENT reset have already unwound.
            if (existing == null && invocation.context != null) { finishContext(invocation.context, normal); }
            if (previous == null) { COMMAND.remove(); } else { COMMAND.set(previous); }
            end(invocation.operation, normal);
        }
    }

    /** Actual execute-as sources are observed without rewriting their native coordinates. */
    public static OperationScope beginCommandSource(Object nativeSource, ExecutionContext<?> exact) {
        var context = nativeContext(exact);
        var scope = nativeSource instanceof CommandSourceStack source ? begin(source.getPlayer(), context) : null;
        attach(context, scope);
        return scope;
    }

    private static Context nativeContext(ExecutionContext<?> exact) {
        if (!(exact instanceof ContextAccess access)) { return null; }
        var context = access.p11$nativeContext();
        return context != null && context.exact == exact ? context : null;
    }

    /** Only the still-active original Commands owner may use its already-retained binding. */
    private static Binding bindingInContext(Context context, P11QualifiedSourceOwner source, ServerPlayer actor) {
        if (context == null || context.facts.terminal()) { return null; }
        boolean active = false;
        for (var invocation = COMMAND.get(); invocation != null; invocation = invocation.previous) {
            if (invocation.context == context) { active = true; break; }
        }
        if (!active) { return null; }
        for (var binding : context.bindings) {
            if (binding.owner == source && binding.recipient == actor) { return binding; }
        }
        return null;
    }

    public static List<OperationScope> beginCommandSources(Object originalSource,
            List<?> nativeSources, ExecutionContext<?> exact) {
        var result = new ArrayList<OperationScope>();
        var actors = new java.util.IdentityHashMap<ServerPlayer, Boolean>();
        if (originalSource instanceof CommandSourceStack source && source.getPlayer() != null) {
            actors.put(source.getPlayer(), Boolean.TRUE);
            var scope = beginCommandSource(source, exact);
            if (scope != null) { result.add(scope); }
        }
        for (var source : nativeSources) {
            if (source instanceof CommandSourceStack stack && stack.getPlayer() != null
                    && actors.put(stack.getPlayer(), Boolean.TRUE) == null) {
                var scope = beginCommandSource(stack, exact);
                if (scope != null) { result.add(scope); }
            }
        }
        return result;
    }

    public static void endCommandSources(List<OperationScope> scopes, boolean normal) {
        for (int i = scopes.size() - 1; i >= 0; i--) { end(scopes.get(i), normal); }
    }

    private static void attach(Context context, OperationScope scope) {
        if (scope == null || context == null || context.facts.terminal()) { return; }
        var binding = scope.binding;
        for (var existing : context.bindings) {
            if (existing.owner == binding.owner && existing.body.account == binding.body.account) { return; }
        }
        boolean retained = false;
        try {
            retained = binding.owner.retainNativeRoot(binding.body, P11ControlBudgets.Root.COMMAND_CONTEXT);
            if (retained) { context.bindings.add(binding); }
        } catch (RuntimeException | Error secondary) {
            if (retained) { release(binding, P11ControlBudgets.Root.COMMAND_CONTEXT); }
            observerFailed();
        }
    }

    public static void queueStarted(Context context) { context.facts.started(); }
    public static void queueOverflow(Context context) { context.facts.overflow(); }
    public static void queueDiscarded(Context context) { context.facts.discarded(); }
    public static void queueReturned(Context context, boolean normal, int quota,
            boolean queueEmpty, boolean newCommandsEmpty) {
        context.facts.drained(normal, quota, queueEmpty, newCommandsEmpty);
    }
    public static void tracerClosed(Context context) { context.facts.tracerClosed(); }

    private static void finishContext(Context context, boolean normal) {
        if (!context.facts.outerFinished(normal)) { return; }
        for (var binding : context.bindings) {
            afterNative(binding);
            try { if (!normal) { binding.owner.nativeOperationFailed(binding.body); } }
            catch (RuntimeException | Error secondary) { observerFailed(); }
            finally { release(binding, P11ControlBudgets.Root.COMMAND_CONTEXT); }
        }
        context.bindings.clear();
    }

    private static void afterNative(Binding binding) {
        // An original save inside a native callback may have persisted only the prefix.
        // Keep that save valid, but do not let it discharge the subsequently executed tail.
        // The fixed body check in nativeMutation cannot transfer this duty to a successor.
        try { binding.owner.nativeMutation(binding.body); }
        catch (RuntimeException | Error secondary) { observerFailed(); }
    }

    private static void release(Binding binding, P11ControlBudgets.Root rootKind) {
        try { binding.owner.releaseNativeRoot(binding.body, rootKind); }
        catch (RuntimeException | Error secondary) { observerFailed(); }
    }

    private static void observerFailed() {
        if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
    }

    static long observerFailureCount() { return observerFailures; }

    /** Immutable observation for the excluded companion; never an admission or completion token. */
    static ContextObservation observe(ExecutionContext<?> exact) {
        if (!(exact instanceof ContextAccess access)) { throw new IllegalArgumentException("unobserved native context"); }
        var context = access.p11$nativeContext();
        if (context.exact != exact) { throw new IllegalArgumentException("wrong native context"); }
        return new ContextObservation(context.facts.drain().name(), context.facts.terminal(),
                context.facts.normal(), context.facts.tracerDidClose(),
                context.facts.discardedFrames(), context.bindings.size());
    }

    record ContextObservation(String drain, boolean terminal, boolean outerNormal,
            boolean tracerClosed, int discardedFrames, int retainedBindings) {}

    private static final class Binding {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final ServerPlayer recipient;
        final long epoch;
        Binding(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.owner = owner; this.body = body; recipient = body.actor; epoch = body.source.epoch();
        }
    }

    public static final class OperationScope {
        private final Binding binding;
        private final ServerPlayer origin;
        private final OperationScope previous;
        private boolean closed;
        private OperationScope(Binding binding, ServerPlayer origin, OperationScope previous) {
            this.binding = binding; this.origin = origin; this.previous = previous;
        }
    }

    public static final class Credit {
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body body;
        private final Entity holder;
        private final ServerPlayer actor;
        private boolean closed;
        private Credit(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                Entity holder, ServerPlayer actor) {
            this.owner = owner; this.body = body; this.holder = holder; this.actor = actor;
        }
    }

    public static final class CreditScope {
        private final Credit credit, previous;
        private final OperationScope operation;
        private boolean closed;
        private CreditScope(Credit credit, Credit previous, OperationScope operation) {
            this.credit = credit; this.previous = previous; this.operation = operation;
        }
    }

    public static final class Context {
        private final ExecutionContext<?> exact;
        private final P11NativeContextFacts facts = new P11NativeContextFacts();
        private final List<Binding> bindings = new ArrayList<>();
        private Context(ExecutionContext<?> exact) { this.exact = exact; }
    }

    private static final class CommandInvocation {
        final CommandInvocation previous;
        final ExecutionContext<?> existing;
        final OperationScope operation;
        Context context;
        CommandInvocation(CommandInvocation previous, ExecutionContext<?> existing, OperationScope operation) {
            this.previous = previous; this.existing = existing; this.operation = operation;
        }
    }
}
