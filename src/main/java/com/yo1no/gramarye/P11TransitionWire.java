package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Exact body framing. Enum declaration order is deliberately not a wire contract. */
final class P11TransitionWire {
    static final String REGISTRAR_VERSION = "gramarye-p11-transition-v1";
    static final int VERSION = 1;
    static final int REQUEST_BYTES = 36;
    static final int STATE_BYTES = 54;

    private P11TransitionWire() { }

    static void encodeRequest(FriendlyByteBuf buffer, Request request) {
        buffer.writeByte(VERSION);
        buffer.writeByte(scopeCode(request.scope()));
        buffer.writeLong(request.connectionEpoch());
        buffer.writeLong(request.sceneSerial());
        buffer.writeLong(request.actorGeneration());
        buffer.writeLong(request.requestSeq());
        buffer.writeByte(commandCode(request.command()));
        buffer.writeByte(kindCode(request.kind()));
    }

    static Request decodeRequest(FriendlyByteBuf buffer) {
        header(buffer, REQUEST_BYTES);
        try {
            return new Request(scope(buffer.readUnsignedByte()), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readLong(), command(buffer.readUnsignedByte()),
                    kind(buffer.readUnsignedByte()));
        } catch (IllegalArgumentException invalid) {
            throw new DecoderException("P11_INVALID_TRANSITION_REQUEST", invalid);
        }
    }

    static void encodeState(FriendlyByteBuf buffer, State state) {
        buffer.writeByte(VERSION);
        buffer.writeByte(scopeCode(state.scope()));
        buffer.writeLong(state.connectionEpoch());
        buffer.writeLong(state.sceneSerial());
        buffer.writeLong(state.actorGeneration());
        buffer.writeLong(state.requestSeq());
        buffer.writeLong(state.statusVersion());
        buffer.writeByte(kindCode(state.kind()));
        buffer.writeByte(outcomeCode(state.outcome()));
        buffer.writeByte(availabilityCode(state.availability()));
        buffer.writeByte(reasonCode(state.reason()));
        buffer.writeLong(state.targetActorGeneration());
    }

    static State decodeState(FriendlyByteBuf buffer) {
        header(buffer, STATE_BYTES);
        try {
            return new State(scope(buffer.readUnsignedByte()), buffer.readLong(), buffer.readLong(),
                    buffer.readLong(), buffer.readLong(), buffer.readLong(),
                    kind(buffer.readUnsignedByte()), outcome(buffer.readUnsignedByte()),
                    availability(buffer.readUnsignedByte()), reason(buffer.readUnsignedByte()), buffer.readLong());
        } catch (IllegalArgumentException invalid) {
            throw new DecoderException("P11_INVALID_TRANSITION_STATE", invalid);
        }
    }

    private static void header(FriendlyByteBuf buffer, int exactLength) {
        if (buffer.readableBytes() != exactLength) {
            throw new DecoderException("P11_TRANSITION_BODY_LENGTH");
        }
        if (buffer.readUnsignedByte() != VERSION) {
            throw new DecoderException("P11_TRANSITION_BODY_VERSION");
        }
    }

    static int scopeCode(Scope scope) {
        return switch (scope) { case CONFIG -> 0; case PREPLAY -> 1; case PLAY -> 2; };
    }

    static Scope scope(int code) {
        return switch (code) { case 0 -> Scope.CONFIG; case 1 -> Scope.PREPLAY;
            case 2 -> Scope.PLAY; default -> throw unknown(); };
    }

    static int commandCode(Command command) {
        return switch (command) { case TRY -> 0; case STATUS -> 1; };
    }

    static Command command(int code) {
        return switch (code) { case 0 -> Command.TRY; case 1 -> Command.STATUS;
            default -> throw unknown(); };
    }

    static int kindCode(Kind kind) {
        return switch (kind) { case DEATH -> 0; case END -> 1; case JOIN -> 2;
            case RETURN_TO_WORLD -> 3; case ENTER_CONFIG -> 4; };
    }

    static Kind kind(int code) {
        return switch (code) { case 0 -> Kind.DEATH; case 1 -> Kind.END; case 2 -> Kind.JOIN;
            case 3 -> Kind.RETURN_TO_WORLD; case 4 -> Kind.ENTER_CONFIG; default -> throw unknown(); };
    }

    static int outcomeCode(Outcome outcome) {
        return switch (outcome) {
            case BINDING -> 0; case PENDING -> 1; case NOT_STARTED -> 2; case RUNNING -> 3;
            case NATIVE_FRAME -> 4; case COMPLETED -> 5; case NOT_APPLICABLE -> 6;
            case FAULT -> 7; case UNKNOWN -> 8; case SUPERSEDED -> 9; case EXPIRED -> 10;
        };
    }

    static Outcome outcome(int code) {
        return switch (code) {
            case 0 -> Outcome.BINDING; case 1 -> Outcome.PENDING; case 2 -> Outcome.NOT_STARTED;
            case 3 -> Outcome.RUNNING; case 4 -> Outcome.NATIVE_FRAME; case 5 -> Outcome.COMPLETED;
            case 6 -> Outcome.NOT_APPLICABLE; case 7 -> Outcome.FAULT; case 8 -> Outcome.UNKNOWN;
            case 9 -> Outcome.SUPERSEDED; case 10 -> Outcome.EXPIRED; default -> throw unknown();
        };
    }

    static int availabilityCode(Availability availability) {
        return switch (availability) {
            case DISABLED -> 0; case WAIT_NOTIFY -> 1; case MAY_TRY -> 2;
        };
    }

    static Availability availability(int code) {
        return switch (code) { case 0 -> Availability.DISABLED; case 1 -> Availability.WAIT_NOTIFY;
            case 2 -> Availability.MAY_TRY; default -> throw unknown(); };
    }

    static int reasonCode(Reason reason) {
        return switch (reason) {
            case NONE -> 0; case ACTIVE_OPERATION -> 1; case ACTIVE_CONTEXT -> 2;
            case ACTIVE_TRANSITION -> 3; case NOT_APPLICABLE -> 4; case SCENE_CHANGED -> 5;
            case SOURCE_UNAVAILABLE -> 6; case NATIVE_FAILURE -> 7; case STATUS_UNAVAILABLE -> 8;
            case WAIT_EXPIRED -> 9; case CONTROL_EXHAUSTED -> 10; case CONTROL_RATE_LIMIT -> 11;
            case CONTROL_DISPATCH_BUSY -> 12;
        };
    }

    static Reason reason(int code) {
        return switch (code) {
            case 0 -> Reason.NONE; case 1 -> Reason.ACTIVE_OPERATION; case 2 -> Reason.ACTIVE_CONTEXT;
            case 3 -> Reason.ACTIVE_TRANSITION; case 4 -> Reason.NOT_APPLICABLE; case 5 -> Reason.SCENE_CHANGED;
            case 6 -> Reason.SOURCE_UNAVAILABLE; case 7 -> Reason.NATIVE_FAILURE; case 8 -> Reason.STATUS_UNAVAILABLE;
            case 9 -> Reason.WAIT_EXPIRED; case 10 -> Reason.CONTROL_EXHAUSTED; case 11 -> Reason.CONTROL_RATE_LIMIT;
            case 12 -> Reason.CONTROL_DISPATCH_BUSY; default -> throw unknown();
        };
    }

    private static IllegalArgumentException unknown() {
        return new IllegalArgumentException("P11_UNKNOWN_TRANSITION_CODE");
    }
}
