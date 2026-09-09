package com.yo1no.gramarye;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Render-thread consumer of the bounded immutable trail snapshot. */
final class P8ClientTrailRenderer {
    private static final double MIN_NORMAL_SQUARED = 1.0E-12D;

    private P8ClientTrailRenderer() {
        throw new AssertionError("no instances");
    }

    static void render(
            P8ClientTrailRenderSnapshot snapshot, RenderLevelStageEvent event) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(event, "event");
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || snapshot.strips().isEmpty()
                || Minecraft.getInstance().level == null) {
            return;
        }

        var poseStack = event.getPoseStack();
        var camera = event.getCamera();
        var cameraPosition = camera.getPosition();
        var renderType = RenderType.debugQuads();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var batchAttempted = false;
        var popAttempted = false;
        poseStack.pushPose();
        try {
            var matrix = poseStack.last().pose();
            var vertices = buffers.getBuffer(renderType);
            for (var strip : snapshot.strips()) {
                renderStrip(strip, vertices, matrix, cameraPosition, camera);
            }
            batchAttempted = true;
            buffers.endBatch(renderType);
            popAttempted = true;
            poseStack.popPose();
        } catch (RuntimeException failure) {
            cleanupAfterFailure(
                    buffers, renderType, poseStack, batchAttempted, popAttempted);
            throw failure;
        } catch (Error failure) {
            cleanupAfterFailure(
                    buffers, renderType, poseStack, batchAttempted, popAttempted);
            throw failure;
        }
    }

    private static void renderStrip(
            P8ClientTrailStrip strip,
            VertexConsumer vertices,
            Matrix4f matrix,
            Vec3 cameraPosition,
            net.minecraft.client.Camera camera) {
        var positions = strip.positions();
        var halfWidth = strip.size() * 0.5D;
        if (positions.size() == 1) {
            renderBillboard(
                    positions.getFirst(),
                    strip.argb(),
                    halfWidth,
                    vertices,
                    matrix,
                    cameraPosition,
                    camera);
            return;
        }

        for (var index = 1; index < positions.size(); index++) {
            var first = positions.get(index - 1);
            var second = positions.get(index);
            var tangentX = second.x() - first.x();
            var tangentY = second.y() - first.y();
            var tangentZ = second.z() - first.z();
            var tangentSquared = tangentX * tangentX
                    + tangentY * tangentY
                    + tangentZ * tangentZ;
            if (!Double.isFinite(tangentSquared)
                    || tangentSquared < MIN_NORMAL_SQUARED) {
                renderBillboard(
                        second,
                        strip.argb(),
                        halfWidth,
                        vertices,
                        matrix,
                        cameraPosition,
                        camera);
                continue;
            }
            var midpointX = (first.x() + second.x()) * 0.5D;
            var midpointY = (first.y() + second.y()) * 0.5D;
            var midpointZ = (first.z() + second.z()) * 0.5D;
            var viewX = cameraPosition.x() - midpointX;
            var viewY = cameraPosition.y() - midpointY;
            var viewZ = cameraPosition.z() - midpointZ;
            var sideX = tangentY * viewZ - tangentZ * viewY;
            var sideY = tangentZ * viewX - tangentX * viewZ;
            var sideZ = tangentX * viewY - tangentY * viewX;
            var sideSquared = sideX * sideX + sideY * sideY + sideZ * sideZ;
            if (!Double.isFinite(sideSquared) || sideSquared < MIN_NORMAL_SQUARED) {
                sideX = strip.directionY() * viewZ - strip.directionZ() * viewY;
                sideY = strip.directionZ() * viewX - strip.directionX() * viewZ;
                sideZ = strip.directionX() * viewY - strip.directionY() * viewX;
                sideSquared = sideX * sideX + sideY * sideY + sideZ * sideZ;
            }
            if (!Double.isFinite(sideSquared) || sideSquared < MIN_NORMAL_SQUARED) {
                var left = camera.getLeftVector();
                sideX = left.x;
                sideY = left.y;
                sideZ = left.z;
                sideSquared = sideX * sideX + sideY * sideY + sideZ * sideZ;
            }
            var scale = halfWidth / Math.sqrt(sideSquared);
            sideX *= scale;
            sideY *= scale;
            sideZ *= scale;
            vertex(
                    vertices,
                    matrix,
                    first.x() + sideX,
                    first.y() + sideY,
                    first.z() + sideZ,
                    strip.argb(),
                    cameraPosition);
            vertex(
                    vertices,
                    matrix,
                    first.x() - sideX,
                    first.y() - sideY,
                    first.z() - sideZ,
                    strip.argb(),
                    cameraPosition);
            vertex(
                    vertices,
                    matrix,
                    second.x() - sideX,
                    second.y() - sideY,
                    second.z() - sideZ,
                    strip.argb(),
                    cameraPosition);
            vertex(
                    vertices,
                    matrix,
                    second.x() + sideX,
                    second.y() + sideY,
                    second.z() + sideZ,
                    strip.argb(),
                    cameraPosition);
        }
    }

    private static void renderBillboard(
            P8ClientPosition center,
            int argb,
            double halfWidth,
            VertexConsumer vertices,
            Matrix4f matrix,
            Vec3 cameraPosition,
            net.minecraft.client.Camera camera) {
        var left = camera.getLeftVector();
        var up = camera.getUpVector();
        vertex(
                vertices,
                matrix,
                center.x() + left.x * halfWidth + up.x * halfWidth,
                center.y() + left.y * halfWidth + up.y * halfWidth,
                center.z() + left.z * halfWidth + up.z * halfWidth,
                argb,
                cameraPosition);
        vertex(
                vertices,
                matrix,
                center.x() - left.x * halfWidth + up.x * halfWidth,
                center.y() - left.y * halfWidth + up.y * halfWidth,
                center.z() - left.z * halfWidth + up.z * halfWidth,
                argb,
                cameraPosition);
        vertex(
                vertices,
                matrix,
                center.x() - left.x * halfWidth - up.x * halfWidth,
                center.y() - left.y * halfWidth - up.y * halfWidth,
                center.z() - left.z * halfWidth - up.z * halfWidth,
                argb,
                cameraPosition);
        vertex(
                vertices,
                matrix,
                center.x() + left.x * halfWidth - up.x * halfWidth,
                center.y() + left.y * halfWidth - up.y * halfWidth,
                center.z() + left.z * halfWidth - up.z * halfWidth,
                argb,
                cameraPosition);
    }

    private static void vertex(
            VertexConsumer target,
            Matrix4f matrix,
            double x,
            double y,
            double z,
            int argb,
            Vec3 cameraPosition) {
        target.addVertex(
                        matrix,
                        (float) (x - cameraPosition.x()),
                        (float) (y - cameraPosition.y()),
                        (float) (z - cameraPosition.z()))
                .setColor(argb);
    }

    private static void cleanupAfterFailure(
            MultiBufferSource.BufferSource buffers,
            RenderType renderType,
            PoseStack poseStack,
            boolean batchAttempted,
            boolean popAttempted) {
        if (!batchAttempted) {
            try {
                buffers.endBatch(renderType);
            } catch (RuntimeException | Error cleanupFailure) {
                // Preserve the primary render failure without Throwable history.
            }
        }
        if (!popAttempted) {
            try {
                poseStack.popPose();
            } catch (RuntimeException | Error cleanupFailure) {
                // Preserve the primary render failure without Throwable history.
            }
        }
    }
}
