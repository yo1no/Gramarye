package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.gametest.framework.GameTestHelper;

/** Excluded GameTest binding to the one original composition; never constructs another root. */
public final class P11CooldownGameTestHarness {
    private static P11FoundationService foundation;
    private static P11CastCooldownService cooldowns;
    private static P4E2QualificationFacade rootFacade;
    private static MinecraftServer scheduledServer;
    private static long nextFixtureTick;

    private P11CooldownGameTestHarness() { }

    public interface RuntimeAccess {
        java.util.IdentityHashMap<MinecraftServer, ?> p11$gameTestSlots();
    }

    public static void zeroCooldown(Object exactRuntime, MinecraftServer server,
            net.minecraft.server.level.ServerPlayer actor, Object exactAccepted) {
        attachments(server);
        if (!(exactRuntime instanceof SkillRuntimeService runtime)
                || !(exactAccepted instanceof RuntimeAdmissionResult.AcceptedMemoryOnly accepted)
                || !(exactRuntime instanceof RuntimeAccess access)
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor) {
            throw new AssertionError("D0 observer requires original exact runtime/actor/admission");
        }
        var slot = (ServerSlot) access.p11$gameTestSlots().get(server);
        var instance = slot == null ? null : slot.instances.get(accepted.eventToken().skillInstanceId());
        var source = foundation.sourceOwner(server);
        var body = source == null ? null : source.body(actor);
        var datum = P11CastCooldownAttachments.existing(actor);
        if (slot == null || !slot.token.equals(accepted.eventToken().serverSlotToken())
                || slot.dispatching || slot.currentEvent != null || instance == null
                || instance.inFlight || instance.terminal || instance.activeProjectileContinuation != null
                || instance.cooldownPreparation != null || instance.cooldownReceipt != null
                || body == null || !source.canCopy(body) || body.cooldown == null
                || !body.cooldown.current(actor) || body.cooldown.data != datum
                || datum == null || datum.kind != P11CastCooldownData.Kind.ROUTED || !datum.entries.isEmpty()) {
            throw new AssertionError("D0 admission must have empty original routed cooldown and no pending receipt before spawn");
        }
    }

    public static void facade(Object actual) {
        if (!(actual instanceof P4E2QualificationFacade facade) || rootFacade != null || foundation != null) {
            throw new AssertionError("GameTest root facade must be observed exactly once before foundation");
        }
        rootFacade = facade;
    }

    public static Object facade(MinecraftServer server) {
        attachments(server);
        if (rootFacade == null) { throw new AssertionError("original root facade not observed"); }
        return rootFacade;
    }

    public static void constructed(Object actualOwner, Object actualService) {
        var actual = (P11FoundationService) actualOwner;
        var service = (P11CastCooldownService) actualService;
        if (foundation != null || cooldowns != null || actual == null || service == null) {
            throw new AssertionError("GameTest cooldown composition must be captured exactly once");
        }
        foundation = actual;
        cooldowns = service;
    }

    public static Object runtime(SkillDefinitionStoreService store,
            SkillSubmissionPolicyProvider policy, Object projector,
            Object resolver, Object port) {
        if (foundation == null || cooldowns == null) {
            throw new AssertionError("GameTest requires the original cooldown composition");
        }
        return new SkillRuntimeService(store, policy, (P5RuntimeProjector) projector,
                (RuntimeReferenceResolver) resolver, (RuntimeExecutionPort) port, foundation, cooldowns);
    }

    public static Object defaultRuntime(SkillDefinitionStoreService store, Object port) {
        return runtime(store, SkillSubmissionPolicyProvider.defaults(),
                new P5RuntimeProjector(com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView.unknown()),
                new P5LoadedReferenceResolver(), port);
    }

    public static PlayerSkillAttachmentService attachments(MinecraftServer server) {
        if (foundation == null || !server.isSameThread()
                || !(foundation.startupState(server).orElse(null) instanceof P11StartupLoadState.Ready)) {
            throw new AssertionError("GameTest requires original loaded Ready P11 configuration");
        }
        var source = foundation.sourceOwner(server);
        if (source == null) { throw new AssertionError("GameTest canonical source is unavailable"); }
        return source.cooldownEquipmentOwner();
    }

    public static void schedule(GameTestHelper helper, Runnable originalBody) {
        var server = helper.getLevel().getServer();
        if (!server.isSameThread()) { throw new AssertionError("GameTest scheduling requires main"); }
        if (scheduledServer != server) {
            scheduledServer = server;
            nextFixtureTick = 0;
        }
        long now = Integer.toUnsignedLong(server.getTickCount());
        nextFixtureTick = Math.max(now, nextFixtureTick) + 1;
        long delay = nextFixtureTick - now;
        if (delay > 12) { throw new AssertionError("closed twelve-fixture bootstrap bound exceeded"); }
        helper.runAfterDelay(delay, originalBody);
    }
}
