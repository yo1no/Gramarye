package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;

/** Excluded client fixture: only original scheduled portal producers/consumers qualify. */
public final class P11C4aPortalClientProbe {
    private static Run active;
    private P11C4aPortalClientProbe() { }

    /** Parent invokes this instead of its first-End Escape only in the frozen PORTAL mode. */
    static boolean tick(Minecraft minecraft, Connection connection, Path output, Path serverOutput) throws IOException {
        require(P11C4aPortalProbe.selected() && minecraft.isSameThread(), "PORTAL_CLIENT_OWNER");
        var run = active;
        if (run == null) {
            if (!P11C4aEvidence.receiptPresent(serverOutput, "portal-geometry.json")) { return false; }
            if (minecraft.level == null || minecraft.level.dimension() != Level.END || minecraft.player == null
                    || !minecraft.player.isAlive() || minecraft.player.isOnPortalCooldown()) { return false; }
            require(minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                    && connection.getPacketListener() == minecraft.getConnection(), "PORTAL_CLIENT_ACTUAL_CONNECTION");
            var file = serverOutput.resolve("portal-geometry.json");
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                    && Files.size(file) <= 1024, "PORTAL_GEOMETRY_BOUNDS");
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            require(json.size() == 6 && json.get("dimension").getAsString().equals("END")
                    && json.get("nativeShapeComplete").getAsBoolean(), "PORTAL_GEOMETRY_FORMAT");
            var origin = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
            var layout = P11C4aPortalProbe.layout(origin);
            require(json.get("blocks").getAsInt() == layout.size() && layout.size() == 24, "PORTAL_GEOMETRY_EXACT_SIZE");
            for (var entry : layout.entrySet()) {
                if (!minecraft.level.getBlockState(entry.getKey()).equals(entry.getValue())) { return false; }
            }
            run = new Run(minecraft.player, connection, output, origin);
            active = run;
            P11C4aEvidence.cue(output, "portal-blocks-observed.ready");
            return false;
        }
        require(++run.ticks <= 1200 && run.failure == null && connection == run.connection && connection.isConnected()
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection,
                "PORTAL_CLIENT_DEADLINE_OR_OBSERVER");
        if (!run.endClicked) {
            run.endClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.END_FINISH);
            return false;
        }
        if (run.may == null) { return false; }
        if (!run.retryClicked) {
            require(minecraft.player == run.actor && run.actor.isAlive() && minecraft.level != null
                    && minecraft.level.dimension() == Level.END && run.submits == 1 && run.wait != null
                    && sameAttempt(run.may, P11ClientTransitions.view()) && P11ClientTransitions.blocksCast()
                    && frames(run) == run.framesBefore, "PORTAL_PENDING_ALIVE_EXACT_END");
            switch (run.stage) {
                case 0 -> {
                    if (!(minecraft.screen instanceof P11ClientTransitionScreen)) { return false; }
                    run.expected = minecraft.screen; run.stage = 1;
                }
                case 1 -> {
                    if (run.statusCalls < 3) { return false; }
                    require(run.closes == 0 && minecraft.screen == run.expected, "PORTAL_STATUS_WAS_CLOSED_OR_REPLACED");
                    if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.STATUS_ESCAPE)) {
                        require(minecraft.screen instanceof P11ClientLeaveScreen, "PORTAL_ESCAPE_NOT_ORIGINAL_LEAVE");
                        run.expected = minecraft.screen; run.stage = 2;
                    }
                }
                case 2 -> {
                    if (run.leaveCalls < 3) { return false; }
                    require(run.closes == 0 && minecraft.screen == run.expected, "PORTAL_LEAVE_WAS_CLOSED_OR_REPLACED");
                    if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.LEAVE_STAY)) {
                        require(minecraft.screen instanceof P11ClientTransitionScreen && run.submits == 1,
                                "PORTAL_STAY_SENT_A_TRANSITION");
                        // Controlled ordinary-screen presentation, not a forged portal history or chat-key test.
                        minecraft.setScreen(new ChatScreen(""));
                        run.expected = minecraft.screen; run.stage = 3;
                    }
                }
                case 3 -> {
                    if (run.ordinaryCalls == 0) { return false; }
                    require(run.closes == 1 && run.closeReturns == 1 && minecraft.screen instanceof P11ClientTransitionScreen,
                            "PORTAL_ORDINARY_ORIGINAL_CLOSE_NOT_OBSERVED");
                    // Minecraft.pauseGame requires screen==null, which P11 correctly repairs.
                    // Use an explicitly labelled native PauseScreen presentation comparison;
                    // never null a field or claim an original pauseGame/OS key path here.
                    minecraft.setScreen(new PauseScreen(true));
                    run.expected = minecraft.screen; run.pauseTime = minecraft.level.getGameTime(); run.stage = 4;
                }
                case 4 -> {
                    if (run.pauseCalls < 3 || minecraft.level.getGameTime() <= run.pauseTime) { return false; }
                    require(minecraft.screen == run.expected && run.expected.isPauseScreen()
                            && !P11ClientTransitions.protectScreen(run.expected) && !minecraft.isPaused()
                            && run.closes == 1, "PORTAL_ORDINARY_PAUSE_SEMANTICS_CHANGED");
                    run.expected.onClose(); // Original PauseScreen callback, which closes to null and P11 repairs.
                    require(minecraft.screen instanceof P11ClientTransitionScreen, "PORTAL_PAUSE_CLOSE_NOT_REPAIRED");
                    run.expected = null; run.stage = 5;
                }
                case 5 -> {
                    // Fixture pacing only: keep an unrelated TRY token refill from masking
                    // the screen result. This reads monotonic time and changes no bucket.
                    if (System.nanoTime() - run.mayNanos < 1_000_000_000L) { return false; }
                    if (!P11C4aClientInputProbe.snapshot(minecraft).glfwActivationNeutral()
                            || !P11ClientTransitions.retryArmed()) { return false; }
                    run.retryClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
                    if (run.retryClicked) {
                        require(run.submits == 2 && run.second != null && run.first != null
                                && run.second.requestSeq() > run.first.requestSeq(), "PORTAL_RETRY_NOT_FRESH_ORIGINAL_SENDER");
                        run.stage = 6;
                    }
                }
                default -> throw new IllegalStateException("PORTAL_CLIENT_STAGE");
            }
            return false;
        }
        if (run.completed == null || frames(run) != run.framesBefore + 1) { return false; }
        require(P11ClientTransitions.view() == run.completed && sameScene(run.wait, run.completed)
                && run.completed.requestSeq() == run.second.requestSeq() && run.submits == 2
                && minecraft.player != null && minecraft.player != run.actor && minecraft.player.isAlive()
                && minecraft.level.dimension() == Level.OVERWORLD && run.failure == null
                && run.statusCalls >= 3 && run.leaveCalls >= 3 && run.ordinaryCalls == 1 && run.pauseCalls >= 3
                && run.collisions >= 10 && run.cooldownCalls >= 10 && run.closes == 1 && run.closeReturns == 1,
                "PORTAL_FINAL_NATIVE_PROOFS_MISSING");
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_SCHEDULED_PORTAL_NARROW_SCREEN_EXEMPTION_SUBSET");
        values.put("nativeNetherCollisionReturns", run.collisions); values.put("originalCooldownReturns", run.cooldownCalls);
        values.put("statusConfusionReturns", run.statusCalls); values.put("leaveConfusionReturns", run.leaveCalls);
        values.put("ordinaryChatConfusionReturns", run.ordinaryCalls); values.put("ordinaryPauseConfusionReturns", run.pauseCalls);
        values.put("originalPortalSetScreenNullCalls", run.closes); values.put("originalPortalSetScreenNullReturns", run.closeReturns);
        values.put("firstTry", run.first); values.put("freshTry", run.second); values.put("wait", run.wait); values.put("mayTry", run.may);
        values.put("completed", run.completed); values.put("sameAliveLocalPlayerThroughPending", true);
        values.put("ordinaryScreensControlledPresentation", true); values.put("portalProcessOrEffectSeeded", false);
        values.put("directPrivateConsumerCalled", false); values.put("physicalInputOrChatKeyOrSingleplayerPauseClaimed", false);
        values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(output, "portal-client.json", values);
        active = null;
        return true;
    }

    /** Original NetherPortalBlock.entityInside -> Entity.setAsInsidePortal normal return only. */
    public static void collision(Entity entity, Portal portal, BlockPos position) {
        var run = active;
        if (run == null || entity != run.actor || portal != Blocks.NETHER_PORTAL) { return; }
        if (!run.actor.isAlive() || run.actor.level().dimension() != Level.END
                || position.getX() != run.origin.getX() || !run.actor.level().getBlockState(position).is(Blocks.NETHER_PORTAL)
                || run.actor.portalProcess == null || !run.actor.portalProcess.isInsidePortalThisTick()
                || run.actor.getActivePortalLocalTransition() != Portal.Transition.CONFUSION) {
            run.failure = "PORTAL_COLLISION_DID_NOT_PRODUCE_REAL_PROCESS"; return;
        }
        if (++run.collisions > 4096) { run.failure = "PORTAL_COLLISION_BOUND"; }
    }

    /** Call-local token only around the original aiStep -> private confusion invocation. */
    public static Object beforeConfusion(LocalPlayer actor, boolean confusion) {
        var run = active;
        if (run == null || actor != run.actor || run.stage < 1 || run.stage > 4 || !confusion
                || actor.portalProcess == null || !actor.portalProcess.isInsidePortalThisTick()) { return null; }
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != run.expected || !actor.isAlive() || run.collisions == 0
                || !sameAttempt(run.may, P11ClientTransitions.view())) {
            run.failure = "PORTAL_CONFUSION_WRONG_ORIGINAL_CONTEXT"; return null;
        }
        return new Call(run, run.stage, run.expected, run.closes);
    }

    public static void afterConfusion(Object token, boolean normal) {
        if (!(token instanceof Call call)) { return; }
        var run = call.run;
        if (!normal) { run.failure = "PORTAL_ORIGINAL_CONFUSION_THROW"; return; }
        var screen = Minecraft.getInstance().screen;
        if (run.actor.portalProcess == null || run.actor.portalProcess.isInsidePortalThisTick()) {
            run.failure = "PORTAL_ORIGINAL_CONSUMER_DID_NOT_CLEAR_INSIDE"; return;
        }
        if (call.stage == 3) {
            if (!(call.screen instanceof ChatScreen) || call.screen.isPauseScreen()
                    || P11ClientTransitions.protectScreen(call.screen) || run.closes != call.closes + 1
                    || !(screen instanceof P11ClientTransitionScreen)) { run.failure = "PORTAL_ORDINARY_CLOSE_WAS_BYPASSED"; return; }
            run.ordinaryCalls++;
        } else {
            if (screen != call.screen || run.closes != call.closes) { run.failure = "PORTAL_PROTECTED_SCREEN_CLOSED"; return; }
            if (call.stage != 4 && (call.screen.isPauseScreen() || !P11ClientTransitions.protectScreen(call.screen))) {
                run.failure = "PORTAL_P11_SCREEN_NOT_NARROW_EXEMPTION"; return;
            }
            if (call.stage == 1) { run.statusCalls++; }
            else if (call.stage == 2) { run.leaveCalls++; }
            else if (call.stage == 4) { run.pauseCalls++; }
        }
    }

    public static void portalClose(Screen next, boolean returned) {
        var run = active;
        if (run == null || run.stage < 1 || run.stage > 4) { return; }
        if (next != null) { run.failure = "PORTAL_NATIVE_CLOSE_ARGUMENT"; return; }
        if (returned) { run.closeReturns++; } else { run.closes++; }
    }

    public static void cooldown(LocalPlayer actor, int before, boolean normal) {
        var run = active;
        if (run == null || actor != run.actor) { return; }
        if (!normal || actor.getPortalCooldown() != Math.max(0, before - 1)) {
            run.failure = "PORTAL_NATIVE_COOLDOWN_RESULT"; return;
        }
        if (++run.cooldownCalls > 4096) { run.failure = "PORTAL_COOLDOWN_BOUND"; }
    }

    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state || state.kind() != Kind.END) { return; }
        if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
            if (state.reason() != Reason.ACTIVE_OPERATION || run.first == null || !matches(run.first, state)
                    || frames(run) != run.framesBefore) { run.failure = "PORTAL_CLIENT_WRONG_REFUSAL"; return; }
            if (run.wait == null) { run.wait = state; }
        } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
            if (run.wait == null || !sameAttempt(run.wait, state)) { run.failure = "PORTAL_CLIENT_MAY_WITHOUT_WAIT"; return; }
            if (run.may == null) { run.mayNanos = System.nanoTime(); }
            run.may = state;
        } else if (state.outcome() == Outcome.COMPLETED) { run.completed = state; }
    }

    public static void submitted(Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request) || request.command() != Command.TRY || request.kind() != Kind.END) { return; }
        if (++run.submits == 1) { run.first = request; }
        else if (run.submits == 2) { run.second = request; }
        else { run.failure = "PORTAL_MORE_THAN_TWO_TRIES"; }
    }
    static void abort() { active = null; }
    private static long frames(Run run) { return P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN); }
    private static boolean matches(Request a, State b) { return a.connectionEpoch() == b.connectionEpoch()
            && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.requestSeq() == b.requestSeq(); }
    private static boolean sameAttempt(State a, State b) { return a != null && b != null && sameScene(a, b) && a.requestSeq() == b.requestSeq(); }
    private static boolean sameScene(State a, State b) { return a.connectionEpoch() == b.connectionEpoch()
            && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind(); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private record Call(Run run, int stage, Screen screen, int closes) { }
    private static final class Run {
        final LocalPlayer actor;
        final Connection connection;
        final Path output;
        final BlockPos origin;
        final long framesBefore;
        int ticks, stage, submits, collisions, cooldownCalls, statusCalls, leaveCalls, ordinaryCalls, pauseCalls, closes, closeReturns;
        long pauseTime, mayNanos;
        boolean endClicked, retryClicked;
        Screen expected;
        Request first, second;
        State wait, may, completed;
        String failure;
        Run(LocalPlayer actor, Connection connection, Path output, BlockPos origin) {
            this.actor = actor; this.connection = connection; this.output = output; this.origin = origin; framesBefore = frames(this);
        }
    }
}
