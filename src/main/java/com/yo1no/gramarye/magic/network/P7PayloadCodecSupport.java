package com.yo1no.gramarye.magic.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;

final class P7PayloadCodecSupport {
    private static final int CAST_FIXED_BODY_BYTES = 11;
    private static final int ACK_MINIMUM_BODY_BYTES = 10;
    private static final int MANA_BODY_BYTES = 17;
    private static final int MAX_COOLDOWN_BODY_BYTES = 393;

    private P7PayloadCodecSupport() {
        throw new AssertionError("no instances");
    }

    static void encodeCastIntent(
            RegistryFriendlyByteBuf buffer, CastIntentPayload payload) {
        var intent = payload.intent();
        if (!intent.hasProductValidSequence()) {
            throw new EncoderException("cast intent sequence is not product-valid");
        }
        requireEncodedSize(
                intent.encodedBodySize(),
                P7NetworkBounds.ACTUAL_MAX_CAST_INTENT_BODY_BYTES,
                P7NetworkBounds.MAX_C2S_INTENT_BYTES,
                "cast intent");
        try {
            buffer.writeLong(intent.sequence());
            buffer.writeByte(intent.slot());
            buffer.writeByte(intent.inputKind().semanticCode());
            buffer.writeByte(intent.presenceMask());
            intent.aimHint().ifPresent(aim -> {
                buffer.writeShort(aim.x());
                buffer.writeShort(aim.y());
                buffer.writeShort(aim.z());
            });
            intent.entityHint().ifPresent(entity -> buffer.writeVarInt(entity.networkId()));
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode cast intent", failure);
        }
    }

    static CastIntentPayload decodeCastIntent(RegistryFriendlyByteBuf buffer) {
        requireDecodeSize(
                buffer.readableBytes(),
                CAST_FIXED_BODY_BYTES,
                P7NetworkBounds.MAX_C2S_INTENT_BYTES,
                "cast intent");
        try {
            var sequence = buffer.readLong();
            var slot = buffer.readUnsignedByte();
            var rawInputKind = buffer.readUnsignedByte();
            var presenceMask = buffer.readUnsignedByte();
            if (!CastInputKind.isKnownCode(rawInputKind)
                    || (presenceMask & ~P7NetworkBounds.ALLOWED_PRESENCE_MASK) != 0) {
                throw malformed("cast intent enum or mask is invalid");
            }

            Integer aimX = null;
            Integer aimY = null;
            Integer aimZ = null;
            if ((presenceMask & (1 << P7NetworkBounds.AIM_PRESENT_BIT)) != 0) {
                aimX = (int) buffer.readShort();
                aimY = (int) buffer.readShort();
                aimZ = (int) buffer.readShort();
            }

            Integer entityNetworkId = null;
            if ((presenceMask & (1 << P7NetworkBounds.ENTITY_HINT_PRESENT_BIT)) != 0) {
                entityNetworkId = readPositiveCanonicalVarInt(buffer, "entity hint");
            }

            requireFullyConsumed(buffer, "cast intent");
            var validation = CastIntentValidation.validate(
                    sequence,
                    slot,
                    rawInputKind,
                    presenceMask,
                    aimX,
                    aimY,
                    aimZ,
                    entityNetworkId);
            if (!validation.valid()) {
                throw malformed("cast intent fields are invalid");
            }
            return new CastIntentPayload(validation.intent().orElseThrow());
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed cast intent", failure);
        }
    }

    static void encodeIntentAcknowledgement(
            RegistryFriendlyByteBuf buffer, IntentAckPayload payload) {
        var acknowledgement = payload.acknowledgement();
        requireEncodedSize(
                acknowledgement.encodedBodySize(),
                P7NetworkBounds.ACTUAL_MAX_ACK_BODY_BYTES,
                P7NetworkBounds.MAX_S2C_ACK_BYTES,
                "intent acknowledgement");
        try {
            buffer.writeLong(acknowledgement.sequence());
            buffer.writeByte(acknowledgement.disposition().semanticCode());
            buffer.writeByte(acknowledgement.flags());
            acknowledgement.expectedNext().ifPresent(buffer::writeLong);
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode intent acknowledgement", failure);
        }
    }

    static IntentAckPayload decodeIntentAcknowledgement(RegistryFriendlyByteBuf buffer) {
        requireDecodeSize(
                buffer.readableBytes(),
                ACK_MINIMUM_BODY_BYTES,
                P7NetworkBounds.MAX_S2C_ACK_BYTES,
                "intent acknowledgement");
        try {
            var sequence = buffer.readLong();
            var rawDisposition = buffer.readUnsignedByte();
            var flags = buffer.readUnsignedByte();
            if ((flags & ~IntentAcknowledgement.ALLOWED_FLAGS) != 0) {
                throw malformed("intent acknowledgement flags are reserved");
            }
            var disposition = IntentAcknowledgement.Disposition
                    .fromSemanticCode(rawDisposition)
                    .orElseThrow(() -> malformed(
                            "intent acknowledgement disposition is unknown"));
            Long expectedNext = null;
            if ((flags & IntentAcknowledgement.HAS_EXPECTED_NEXT) != 0) {
                expectedNext = buffer.readLong();
            }
            requireFullyConsumed(buffer, "intent acknowledgement");
            return new IntentAckPayload(
                    new IntentAcknowledgement(sequence, disposition, flags, expectedNext));
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed intent acknowledgement", failure);
        }
    }

    static void encodePlayerManaSnapshot(
            RegistryFriendlyByteBuf buffer, PlayerManaSyncPayload payload) {
        var snapshot = payload.snapshot();
        requireEncodedSize(
                MANA_BODY_BYTES,
                MANA_BODY_BYTES,
                P7NetworkBounds.MAX_S2C_SYNC_BYTES,
                "player mana snapshot");
        try {
            buffer.writeLong(snapshot.syncSequence());
            buffer.writeByte(snapshot.availability().wireCode());
            buffer.writeLong(snapshot.balance());
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode player mana snapshot", failure);
        }
    }

    static PlayerManaSyncPayload decodePlayerManaSnapshot(
            RegistryFriendlyByteBuf buffer) {
        requireDecodeSize(
                buffer.readableBytes(),
                MANA_BODY_BYTES,
                P7NetworkBounds.MAX_S2C_SYNC_BYTES,
                "player mana snapshot");
        try {
            var syncSequence = buffer.readLong();
            var availability = PlayerManaSnapshot.Availability
                    .fromWireCode(buffer.readUnsignedByte())
                    .orElseThrow(() -> malformed("mana availability is unknown"));
            var balance = buffer.readLong();
            requireFullyConsumed(buffer, "player mana snapshot");
            return new PlayerManaSyncPayload(
                    new PlayerManaSnapshot(syncSequence, availability, balance));
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed player mana snapshot", failure);
        }
    }

    static void encodeSkillCooldownSnapshot(
            RegistryFriendlyByteBuf buffer, SkillCooldownSyncPayload payload) {
        var snapshot = payload.snapshot();
        requireEncodedSize(
                snapshot.encodedBodySize(),
                MAX_COOLDOWN_BODY_BYTES,
                P7NetworkBounds.MAX_S2C_SYNC_BYTES,
                "skill cooldown snapshot");
        try {
            buffer.writeLong(snapshot.syncSequence());
            buffer.writeVarInt(snapshot.entries().size());
            for (var entry : snapshot.entries()) {
                buffer.writeByte(entry.slot());
                buffer.writeVarInt(entry.remainingTicks());
            }
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode skill cooldown snapshot", failure);
        }
    }

    static SkillCooldownSyncPayload decodeSkillCooldownSnapshot(
            RegistryFriendlyByteBuf buffer) {
        requireDecodeSize(
                buffer.readableBytes(),
                Long.BYTES + 1,
                P7NetworkBounds.MAX_S2C_SYNC_BYTES,
                "skill cooldown snapshot");
        try {
            var syncSequence = buffer.readLong();
            var count = readCanonicalVarInt(buffer, "cooldown entry count");
            if (count < 0 || count > P7NetworkBounds.MAX_SYNC_ENTRIES_PER_PACKET) {
                throw malformed("cooldown entry count exceeds its bound");
            }
            var entries = new ArrayList<CooldownSnapshotEntry>(count);
            var previousSlot = -1;
            for (var index = 0; index < count; index++) {
                var slot = buffer.readUnsignedByte();
                var remainingTicks = readPositiveCanonicalVarInt(
                        buffer, "cooldown remaining ticks");
                if (slot <= previousSlot) {
                    throw malformed("cooldown entries are not strictly ordered");
                }
                entries.add(new CooldownSnapshotEntry(slot, remainingTicks));
                previousSlot = slot;
            }
            requireFullyConsumed(buffer, "skill cooldown snapshot");
            return new SkillCooldownSyncPayload(
                    new SkillCooldownSnapshot(syncSequence, entries));
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed skill cooldown snapshot", failure);
        }
    }

    private static void requireEncodedSize(
            int actualSize, int maximumActual, int hardCeiling, String label) {
        if (actualSize < 0 || actualSize > maximumActual || actualSize > hardCeiling) {
            throw new EncoderException(label + " exceeds its encoded-size bound");
        }
    }

    private static void requireDecodeSize(
            int readableBytes, int minimum, int hardCeiling, String label) {
        if (readableBytes < minimum || readableBytes > hardCeiling) {
            throw malformed(label + " body size is invalid");
        }
    }

    private static int readPositiveCanonicalVarInt(
            RegistryFriendlyByteBuf buffer, String label) {
        var value = readCanonicalVarInt(buffer, label);
        if (value <= 0) {
            throw malformed(label + " is not positive");
        }
        return value;
    }

    private static int readCanonicalVarInt(
            RegistryFriendlyByteBuf buffer, String label) {
        var value = 0;
        for (var position = 0; position < 5; position++) {
            var rawByte = buffer.readUnsignedByte();
            if (position == 4 && (rawByte & 0xf0) != 0) {
                throw malformed(label + " overflows a signed integer");
            }
            value |= (rawByte & 0x7f) << (position * 7);
            if ((rawByte & 0x80) == 0) {
                if (position + 1 != VarInt.getByteSize(value)) {
                    throw malformed(label + " is not canonically encoded");
                }
                return value;
            }
        }
        throw malformed(label + " exceeds five bytes");
    }

    private static void requireFullyConsumed(
            RegistryFriendlyByteBuf buffer, String label) {
        if (buffer.readableBytes() != 0) {
            throw malformed(label + " contains trailing bytes");
        }
    }

    private static DecoderException malformed(String message) {
        return new DecoderException(message);
    }
}
