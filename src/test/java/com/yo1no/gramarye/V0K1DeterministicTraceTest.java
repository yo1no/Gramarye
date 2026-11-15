package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Deterministic K1 equation and native codec proof for a dry, zero-gravity flight. */
final class V0K1DeterministicTraceTest {
    private static final Vec3 ORIGIN = new Vec3(5.87521, 72.12504, -30.50007);
    private static final double DRY_DRAG = (double) 0.99F;
    private static final double POSITION_QUANTIZATION = 1.0 / 8192.0;
    private static final double VELOCITY_QUANTIZATION = 1.0 / 8000.0;

    @Test
    void initialNativePairingReplaysPositionMotionAndOldRotationForSixteenAndTwentyFourTicks() {
        for (int age : new int[] {16, 24}) {
            assertFullTrace(age, 0, 0, 32_767, 1_000);
            assertFullTrace(age, -18_919, 18_918, 18_917, 769);
            assertFullTrace(age, 0, -32_767, 0, 1_000);
            assertFullTrace(age, -1, 0, 32_767, 3);
        }
    }

    @Test
    void nativeCorrectionKeepsRelativeWireErrorBoundAndRecognizesAbsoluteOverflow() {
        var diagonal = initial(-18_919, 18_918, 18_917, 1_000);
        var server = subsequentServer(Flight.initial(ORIGIN, diagonal), 10);
        var serverCodec = new VecDeltaCodec();
        serverCodec.setBase(ORIGIN);
        var clientCodec = new VecDeltaCodec();
        clientCodec.setBase(ORIGIN);
        var relativePosition = clientCodec.decode(serverCodec.encodeX(server.position()),
                serverCodec.encodeY(server.position()), serverCodec.encodeZ(server.position()));
        var correctionVelocity = nativeMotionRoundTrip(server.velocity());
        assertPerAxisError(server.position(), relativePosition, POSITION_QUANTIZATION);
        assertPerAxisError(server.velocity(), correctionVelocity, VELOCITY_QUANTIZATION);

        var corrected = Flight.initial(relativePosition, correctionVelocity);
        double errorBound = POSITION_QUANTIZATION;
        for (int step = 0; step < 14; step++) {
            server = server.serverStep();
            corrected = corrected.clientStep();
            errorBound += VELOCITY_QUANTIZATION * Math.pow(DRY_DRAG, step);
            assertPerAxisError(server.position(), corrected.position(), errorBound + 1e-12);
        }

        var axial = initial(32_767, 0, 0, 1_000);
        var tenth = subsequentServer(Flight.initial(ORIGIN, axial), 10);
        assertTrue(serverCodec.encodeX(tenth.position()) > Short.MAX_VALUE,
                "native ServerEntity must use absolute position for this tracked interval");
    }

    @Test
    void latePairingKeepsItsPositionAndVelocitySampleOriginsSeparate() {
        var initialVelocity = initial(0, 0, 32_767, 3);
        var atPositionSample = subsequentServer(Flight.initial(ORIGIN, initialVelocity), 10);
        var nativePositionDelta = new VecDeltaCodec();
        nativePositionDelta.setBase(ORIGIN);
        assertTrue(nativePositionDelta.delta(atPositionSample.position()).lengthSqr() >= 7.6293945E-6F);
        assertTrue(initialVelocity.distanceToSqr(atPositionSample.velocity()) <= 1e-7,
                "native ServerEntity does not send a new velocity in this position interval");

        var receivedVelocity = nativeMotionRoundTrip(initialVelocity);
        assertEquals(initialVelocity, receivedVelocity);
        assertNotEquals(atPositionSample.velocity(), receivedVelocity);
        var pairingBase = Flight.initial(atPositionSample.position(), receivedVelocity);
        var client = pairingBase;
        var expectedFromPairing = pairingBase;
        for (int step = 0; step < 14; step++) {
            client = client.clientStep();
            expectedFromPairing = expectedFromPairing.serverStep();
            assertEquals(expectedFromPairing, client, "native samples drive the replay at step " + step);
        }
        assertNotEquals(subsequentServer(atPositionSample, 14).position(), client.position());
    }

    @Test
    void worstDownwardDryExtrapolationRetainsBelowWorldSafetyMargin() {
        var downward = nativeMotionRoundTrip(initial(0, -32_767, 0, 1_000));
        double totalExtrapolation = Math.abs(downward.y) / (1.0 - DRY_DRAG);
        assertTrue(totalExtrapolation < 101.0);
        double minBuildHeight = -64.0;
        double minimumLaunch = minBuildHeight + 64.0;
        double oldestPairingBase = minimumLaunch - 24.0;
        double conservativeLowestPrediction = oldestPairingBase - totalExtrapolation - 1.0;
        assertTrue(conservativeLowestPrediction >= minBuildHeight - 64.0 + 2.0);
    }

    private static void assertFullTrace(int ticks, int x, int y, int z, int milli) {
        var velocity = initial(x, y, z, milli);
        var decoded = nativeMotionRoundTrip(velocity);
        assertEquals(velocity, decoded);
        var server = Flight.initial(ORIGIN, velocity);
        var client = Flight.initial(ORIGIN, decoded);
        for (int tick = 1; tick <= ticks; tick++) {
            server = server.serverStep();
            client = client.clientStep();
            assertEquals(server, client, "dry replay at tick " + tick);
        }
    }

    private static Vec3 initial(int x, int y, int z, int milli) {
        var velocity = P9StarterProjectile.v0InitialVelocity(x, y, z, milli);
        assertNotNull(velocity);
        return velocity;
    }

    private static Vec3 nativeMotionRoundTrip(Vec3 velocity) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var codec = ClientboundSetEntityMotionPacket.STREAM_CODEC;
            codec.encode(buffer, new ClientboundSetEntityMotionPacket(61, velocity));
            var decoded = codec.decode(buffer);
            assertEquals(61, decoded.getId());
            assertEquals(0, buffer.readableBytes());
            return new Vec3(decoded.getXa(), decoded.getYa(), decoded.getZa());
        } finally {
            buffer.release();
        }
    }

    private static Flight subsequentServer(Flight flight, int ticks) {
        for (int step = 0; step < ticks; step++) {
            flight = flight.serverStep();
        }
        return flight;
    }

    private static void assertPerAxisError(Vec3 expected, Vec3 seen, double bound) {
        assertEquals(expected.x, seen.x, bound);
        assertEquals(expected.y, seen.y, bound);
        assertEquals(expected.z, seen.z, bound);
    }

    private record Flight(Vec3 position, Vec3 velocity, float pitch, float yaw,
            float oldPitch, float oldYaw) {
        static Flight initial(Vec3 position, Vec3 velocity) {
            float pitch = pitch(velocity);
            float yaw = yaw(velocity);
            return new Flight(position, velocity, pitch, yaw, pitch, yaw);
        }

        // P9StarterProjectile.stepV0 uses Vec3 operations and the inherited native rotation.
        Flight serverStep() {
            return advance(position.add(velocity), velocity.scale(DRY_DRAG));
        }

        // ThrowableProjectile.tick adds each axis, rotates, scales dry float drag, and applies zero gravity.
        Flight clientStep() {
            var endpoint = new Vec3(position.x + velocity.x, position.y + velocity.y, position.z + velocity.z);
            var nextVelocity = velocity.scale((double) 0.99F);
            return advance(endpoint, nextVelocity);
        }

        private Flight advance(Vec3 endpoint, Vec3 nextVelocity) {
            return new Flight(endpoint, nextVelocity, NativeRotation.interpolate(pitch, pitch(velocity)),
                    NativeRotation.interpolate(yaw, yaw(velocity)), pitch, yaw);
        }

        private static float pitch(Vec3 velocity) {
            return (float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * 180.0F / (float) Math.PI);
        }

        private static float yaw(Vec3 velocity) {
            return (float) (Mth.atan2(velocity.x, velocity.z) * 180.0F / (float) Math.PI);
        }
    }

    private abstract static class NativeRotation extends Projectile {
        private NativeRotation(EntityType<? extends Projectile> type, Level level) {
            super(type, level);
        }

        static float interpolate(float before, float after) {
            return lerpRotation(before, after);
        }
    }
}
