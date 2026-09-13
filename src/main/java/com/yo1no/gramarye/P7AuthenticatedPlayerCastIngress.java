package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Root-owned authorization bridge into the existing P4/P5 service graph. */
final class P7AuthenticatedPlayerCastIngress
        implements P7ServerAuthorizationBoundary.RootIngressPort {
    private final SkillRuntimeService runtimeService;
    private final PlayerSkillAttachmentService attachmentService;
    private final SkillDefinitionStoreService storeService;

    P7AuthenticatedPlayerCastIngress(
            SkillRuntimeService runtimeService,
            PlayerSkillAttachmentService attachmentService,
            SkillDefinitionStoreService storeService) {
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.attachmentService = Objects.requireNonNull(attachmentService, "attachmentService");
        this.storeService = Objects.requireNonNull(storeService, "storeService");
    }

    @Override
    public P7ServerAuthorizationBoundary.AdmissionDisposition authorizeAndAdmit(
            MinecraftServer server,
            ServerPlayer actor,
            int slot,
            P7ServerAuthorizationBoundary.AdvisoryTargetCheck targetCheck) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(targetCheck, "targetCheck");
        if (slot < 0 || slot > 63) {
            throw new IllegalArgumentException("slot is outside 0..63");
        }
        if (!server.isSameThread()) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT;
        }
        if (actor.getServer() != server) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.UNAUTHORIZED_INTENT;
        }
        var actorId = actor.getUUID();
        if (server.getPlayerList().getPlayer(actorId) != actor) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.UNAUTHORIZED_INTENT;
        }

        var equippedResult = attachmentService.equippedAt(actor, slot);
        if (equippedResult
                instanceof PlayerSkillAttachmentService.Unavailable<Optional<SkillReference>>) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
        }
        var exactReference = ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>)
                        equippedResult)
                .value();
        if (exactReference.isEmpty()) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL;
        }
        var reference = exactReference.orElseThrow();

        var actorOwnerResult = attachmentService.ownerId(actor);
        if (actorOwnerResult instanceof PlayerSkillAttachmentService.Unavailable<?>) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
        }
        var actorOwner = ((PlayerSkillAttachmentService.Available<SkillOwnerId>) actorOwnerResult)
                .value();
        var skillOwnerResult = storeService.ownerOf(server, reference.skillId());
        if (skillOwnerResult instanceof SkillSubsystemResult.Unavailable<?>) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
        }
        var skillOwner = ((SkillSubsystemResult.Available<Optional<SkillOwnerId>>)
                        skillOwnerResult)
                .value();
        if (skillOwner.isEmpty()) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL;
        }
        if (!actorOwner.equals(skillOwner.orElseThrow())) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.UNAUTHORIZED_INTENT;
        }

        var definitionResult = storeService.find(server, reference);
        if (definitionResult instanceof SkillSubsystemResult.Unavailable<?>) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
        }
        var definition = ((SkillSubsystemResult.Available<Optional<com.yo1no.gramarye.magic.definition.document.SkillDocument>>)
                        definitionResult)
                .value();
        if (definition.isEmpty()) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.UNKNOWN_SKILL;
        }

        var targetDisposition = Objects.requireNonNull(
                targetCheck.validate(server, actor), "targetCheck result");
        if (targetDisposition
                == P7ServerAuthorizationBoundary.TargetDisposition.INVALID_TARGET) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.INVALID_TARGET;
        }
        if (targetDisposition
                == P7ServerAuthorizationBoundary.TargetDisposition.TARGET_UNAVAILABLE) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.TARGET_UNAVAILABLE;
        }
        var geometry = sampleGeometry(actor);
        if (geometry.isEmpty()) {
            return P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
        }
        return mapAdmission(
                runtimeService.admitAuthenticatedPlayerCast(
                        server, actor, reference, geometry.orElseThrow()));
    }

    private static Optional<CastGeometryExecutionDataV0> sampleGeometry(ServerPlayer actor) {
        var sampledLevel = actor.serverLevel();
        var dimension = sampledLevel.dimension().location();
        var eye = actor.getEyePosition();
        var look = actor.getLookAngle();
        if (!finite(eye.x, eye.y, eye.z) || !finite(look.x, look.y, look.z)) {
            return Optional.empty();
        }

        var lengthSquared = look.x * look.x + look.y * look.y + look.z * look.z;
        if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) {
            return Optional.empty();
        }
        var inverseLength = 1.0 / StrictMath.sqrt(lengthSquared);
        var directionX = look.x * inverseLength;
        var directionY = look.y * inverseLength;
        var directionZ = look.z * inverseLength;
        if (!finite(directionX, directionY, directionZ)) {
            return Optional.empty();
        }

        var originX = eye.x + directionX * 0.10;
        var originY = eye.y + directionY * 0.10 - 0.10;
        var originZ = eye.z + directionZ * 0.10;
        if (!finite(originX, originY, originZ)
                || originX < -30_000_000.0
                || originX >= 30_000_000.0
                || originY < -20_000_000.0
                || originY >= 20_000_000.0
                || originZ < -30_000_000.0
                || originZ >= 30_000_000.0) {
            return Optional.empty();
        }
        var directionXQ15 = encodeQ15(directionX);
        var directionYQ15 = encodeQ15(directionY);
        var directionZQ15 = encodeQ15(directionZ);
        if (directionXQ15 == 0 && directionYQ15 == 0 && directionZQ15 == 0) {
            return Optional.empty();
        }

        var currentLevel = actor.serverLevel();
        if (!currentLevel.dimension().location().equals(dimension)) {
            return Optional.empty();
        }
        var origin = BlockPos.containing(originX, originY, originZ);
        if (!currentLevel.isInWorldBounds(origin)
                || !currentLevel.isLoaded(origin)
                || !currentLevel.getWorldBorder().isWithinBounds(originX, originZ)) {
            return Optional.empty();
        }
        return Optional.of(new CastGeometryExecutionDataV0(
                dimension,
                originX,
                originY,
                originZ,
                directionXQ15,
                directionYQ15,
                directionZQ15,
                0));
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private static int encodeQ15(double component) {
        var rounded = StrictMath.rint(component * 32_767.0);
        return (int) Math.max(
                -32_767.0,
                Math.min(32_767.0, rounded));
    }

    static P7ServerAuthorizationBoundary.AdmissionDisposition mapAdmission(
            RuntimeAdmissionResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result) {
            case RuntimeAdmissionResult.AcceptedMemoryOnly ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED;
            case RuntimeAdmissionResult.PersistentScheduleUnsupported ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.DelayOutOfRange ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.DelayOverflow ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.DeadlineOutOfRange ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.DeadlineOverflow ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.DeadlineBeforeScheduledTick ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.InvalidRuntimeReference ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.SkillRevisionUnavailable ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.InvalidEvent ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.OwnerInstanceUnavailable ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.ActiveLineageCapacityExceeded ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.RootAdmissionBudgetExceeded ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.CircuitBroken ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.SequenceExhausted ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.TickExhausted ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_ADMISSION_REJECTED;
            case RuntimeAdmissionResult.ServerNotRunning ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
            case RuntimeAdmissionResult.ServerStopping ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
            case RuntimeAdmissionResult.KernelFaulted ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.P5_UNAVAILABLE;
            case RuntimeAdmissionResult.WrongThread ignored ->
                    P7ServerAuthorizationBoundary.AdmissionDisposition.INTERNAL_SERVER_FAULT;
        };
    }
}
