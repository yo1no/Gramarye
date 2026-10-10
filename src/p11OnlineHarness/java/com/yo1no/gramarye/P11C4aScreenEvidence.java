package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Owned game framebuffer evidence only; never desktop, authentication or native acceptance. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11C4aScreenEvidence {
    private static final int MAX_FRAMES = 120;
    private static final long MAX_PIXELS = 8_388_608L;
    private static final int MAX_PNG_BYTES = 40 * 1024 * 1024;
    private static final EnumMap<Label, Status> RESULTS = new EnumMap<>(Label.class);
    private static Pending pending;
    // The only screen/render/event references live between this frame's Pre and Post.
    private static Frame frame;

    private P11C4aScreenEvidence() {}

    private enum Label {
        STATUS_WAIT("status-wait"), STATUS_MAY("status-may"), LEAVE_CONFIRM("leave-confirm");
        private final String leaf;
        Label(String leaf) { this.leaf = leaf; }
    }
    private enum Status { NOT_REQUESTED, PENDING, CAPTURED_VISUAL_ONLY, FAILED }

    /** At most one request at a time, and at most one attempt for each closed label per process. */
    static boolean request(String name) throws IOException {
        var label = label(name);
        var minecraft = Minecraft.getInstance();
        if (!P11C4aEvidence.enabled() || !minecraft.isSameThread()
                || pending != null || RESULTS.containsKey(label)
                || !matches(minecraft, label, P11ClientTransitions.view())) { return false; }
        var cohort = cohort();
        pending = new Pending(label, cohort);
        RESULTS.put(label, Status.PENDING);
        return true;
    }

    /** A captured PNG is only a visual observation, not proof that an operation completed. */
    static String status(String name) {
        if (!Minecraft.getInstance().isSameThread()) { throw new IllegalStateException("SCREEN_EVIDENCE_WRONG_THREAD"); }
        return RESULTS.getOrDefault(label(name), Status.NOT_REQUESTED).name();
    }

    private static Label label(String name) {
        if (name == null) { throw new IllegalArgumentException("SCREEN_EVIDENCE_INVALID_LABEL"); }
        return switch (name) {
            case "status-wait" -> Label.STATUS_WAIT;
            case "status-may" -> Label.STATUS_MAY;
            case "leave-confirm" -> Label.LEAVE_CONFIRM;
            default -> throw new IllegalArgumentException("SCREEN_EVIDENCE_INVALID_LABEL");
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void beforeFrame(RenderFrameEvent.Pre event) {
        frame = null;
        var request = pending;
        if (request == null) { return; }
        var minecraft = Minecraft.getInstance();
        if (!sameCohort(request.cohort) || ++request.frames > MAX_FRAMES) {
            failed(request, "SCREEN_EVIDENCE_FRAME_OR_COHORT_EXPIRED"); return;
        }
        var state = P11ClientTransitions.view();
        if (!minecraft.isSameThread() || !RenderSystem.isOnRenderThread()
                || !matches(minecraft, request.label, state)) { return; }
        var target = minecraft.getMainRenderTarget();
        if (!dimensions(target.width, target.height)) {
            failed(request, "SCREEN_EVIDENCE_DIMENSION_BOUND"); return;
        }
        frame = new Frame(request, minecraft.screen, state, target);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void beforeScreen(ScreenEvent.Render.Pre event) {
        var observed = frame;
        if (observed == null) { return; }
        if (event.getScreen() != observed.screen || observed.screenPre != null) {
            observed.foreignOrRepeatedScreen = true; return;
        }
        // Keep the actual event: its final canceled state is read after dispatch finishes.
        observed.screenPre = event;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void afterScreen(ScreenEvent.Render.Post event) {
        var observed = frame;
        if (observed == null) { return; }
        if (event.getScreen() != observed.screen || observed.screenReturned) {
            observed.foreignOrRepeatedScreen = true; return;
        }
        observed.screenReturned = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void afterFrame(RenderFrameEvent.Post event) {
        var observed = frame;
        frame = null;
        if (observed == null) { return; }
        var minecraft = Minecraft.getInstance();
        if (pending != observed.request || !sameCohort(observed.request.cohort)
                || !minecraft.isSameThread() || !RenderSystem.isOnRenderThread()
                || minecraft.screen != observed.screen || P11ClientTransitions.view() != observed.state
                || minecraft.getMainRenderTarget() != observed.target
                || observed.target.width != observed.width || observed.target.height != observed.height
                || observed.screen.width != observed.screenWidth || observed.screen.height != observed.screenHeight
                || observed.screenPre == null || observed.screenPre.isCanceled()
                || !observed.screenReturned || observed.foreignOrRepeatedScreen
                || !matches(minecraft, observed.request.label, observed.state)) { return; }
        pending = null; // No callback, retry or asynchronous capture can reuse this attempt.
        try {
            capture(minecraft, observed);
            RESULTS.put(observed.request.label, Status.CAPTURED_VISUAL_ONLY);
        } catch (IOException | RuntimeException | LinkageError failure) {
            failed(observed.request, "SCREEN_EVIDENCE_CAPTURE_OR_WRITE_FAILED");
        }
    }

    private static boolean matches(Minecraft minecraft, Label label, P11TransitionProtocol.State state) {
        if (state == null || minecraft.screen == null || minecraft.getOverlay() != null
                || minecraft.noRender || !minecraft.isGameLoadFinished()) { return false; }
        if (label == Label.LEAVE_CONFIRM) { return minecraft.screen.getClass() == P11ClientLeaveScreen.class; }
        return minecraft.screen.getClass() == P11ClientTransitionScreen.class
                && state.outcome() == P11TransitionProtocol.Outcome.NOT_STARTED
                && state.availability() == (label == Label.STATUS_WAIT
                        ? P11TransitionProtocol.Availability.WAIT_NOTIFY : P11TransitionProtocol.Availability.MAY_TRY);
    }

    private static boolean dimensions(int width, int height) {
        return width > 0 && height > 0 && width <= 4096 && height <= 4096
                && (long) width * height <= MAX_PIXELS;
    }

    private static Cohort cohort() throws IOException {
        String run = P11C4aEvidence.property("runId");
        String role = P11C4aEvidence.property("role");
        String selectedCase = P11C4aEvidence.property("case");
        if (!run.matches("[A-Za-z0-9_-]{8,64}")
                || !((selectedCase.equals("c4a-dedicated") || selectedCase.equals("c4a-reward")
                    || selectedCase.equals("d3-c4a-baseline")) && Set.of("a", "b").contains(role)
                || selectedCase.equals("c4a-host-lan") && Set.of("host", "b").contains(role))) {
            throw new IllegalStateException("SCREEN_EVIDENCE_COHORT_IDENTITY");
        }
        var root = P11C4aEvidence.root();
        var directory = root.resolve("client-" + role);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)
                || !directory.toRealPath().getParent().equals(root)
                || !P11C4aEvidence.receiptPresent(directory, "inputs.json")) {
            throw new IllegalStateException("SCREEN_EVIDENCE_UNOWNED_CLIENT_DIRECTORY");
        }
        return new Cohort(run, role, selectedCase, directory);
    }

    private static boolean sameCohort(Cohort cohort) {
        return P11C4aEvidence.enabled()
                && cohort.run.equals(System.getProperty("gramarye.p11.online.runId"))
                && cohort.role.equals(System.getProperty("gramarye.p11.online.role"))
                && cohort.selectedCase.equals(System.getProperty("gramarye.p11.online.case"));
    }

    private static void capture(Minecraft minecraft, Frame observed) throws IOException {
        var request = observed.request;
        if (!cohort().equals(request.cohort)) { throw new IllegalStateException("SCREEN_EVIDENCE_DIRECTORY_CHANGED"); }
        byte[] png;
        try (var image = Screenshot.takeScreenshot(observed.target)) {
            if (image.getWidth() != observed.width || image.getHeight() != observed.height) {
                throw new IllegalStateException("SCREEN_EVIDENCE_IMAGE_DIMENSIONS_CHANGED");
            }
            png = image.asByteArray();
        }
        if (png.length < 8 || png.length > MAX_PNG_BYTES
                || png[0] != (byte) 137 || png[1] != 80 || png[2] != 78 || png[3] != 71
                || png[4] != 13 || png[5] != 10 || png[6] != 26 || png[7] != 10) {
            throw new IllegalStateException("SCREEN_EVIDENCE_PNG_BOUND_OR_SIGNATURE");
        }
        var path = request.cohort.directory.resolve("screen-" + request.label.leaf + ".png");
        writeNew(path, png);
        var receipt = new LinkedHashMap<String, Object>();
        receipt.put("status", "CAPTURED_GAME_FRAMEBUFFER_VISUAL_ONLY");
        receipt.put("nativeAcceptance", false);
        receipt.put("runId", request.cohort.run); receipt.put("role", request.cohort.role);
        receipt.put("case", request.cohort.selectedCase); receipt.put("label", request.label.leaf);
        receipt.put("path", path.toString()); receipt.put("sha256", P11C4aEvidence.hash(png));
        receipt.put("bytes", png.length); receipt.put("width", observed.width); receipt.put("height", observed.height);
        receipt.put("nonPausingScreen", !observed.screen.isPauseScreen());
        receipt.put("retryArmed", P11ClientTransitions.retryArmed());
        receipt.put("sceneSerial", observed.state.sceneSerial()); receipt.put("statusVersion", observed.state.statusVersion());
        receipt.put("outcome", observed.state.outcome().name()); receipt.put("availability", observed.state.availability().name());
        writeNew(request.cohort.directory.resolve("screen-" + request.label.leaf + ".json"),
                (new GsonBuilder().setPrettyPrinting().create().toJson(receipt) + '\n').getBytes(StandardCharsets.UTF_8));
    }

    private static void writeNew(Path path, byte[] bytes) throws IOException {
        try (var channel = Files.newByteChannel(path,
                Set.<OpenOption>of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) { channel.write(buffer); }
        }
    }

    private static void failed(Pending request, String code) {
        pending = null; frame = null;
        RESULTS.put(request.label, Status.FAILED);
        // Only fixed codes are retained; exceptions/messages, raw stacks and rendered text are not serialized.
        try {
            if (!sameCohort(request.cohort) || !cohort().equals(request.cohort)) { return; }
            var text = "{\"status\":\"VISUAL_CAPTURE_FAILED_NOT_NATIVE_RESULT\",\"code\":\"" + code + "\"}\n";
            writeNew(request.cohort.directory.resolve("screen-" + request.label.leaf + ".json"), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException | LinkageError ignored) { }
    }

    private record Cohort(String run, String role, String selectedCase, Path directory) {}
    private static final class Pending {
        private final Label label;
        private final Cohort cohort;
        private int frames;
        private Pending(Label label, Cohort cohort) { this.label = label; this.cohort = cohort; }
    }
    private static final class Frame {
        private final Pending request;
        private final Screen screen;
        private final P11TransitionProtocol.State state;
        private final RenderTarget target;
        private final int width;
        private final int height;
        private final int screenWidth;
        private final int screenHeight;
        private ScreenEvent.Render.Pre screenPre;
        private boolean screenReturned;
        private boolean foreignOrRepeatedScreen;
        private Frame(Pending request, Screen screen, P11TransitionProtocol.State state, RenderTarget target) {
            this.request = request; this.screen = screen; this.state = state; this.target = target;
            width = target.width; height = target.height; screenWidth = screen.width; screenHeight = screen.height;
        }
    }
}
