package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Exact clientbound P8 presentation-event payload value. */
final class PresentationEventPayload implements CustomPacketPayload {
    static final Type<PresentationEventPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "presentation_event"));
    static final StreamCodec<RegistryFriendlyByteBuf, PresentationEventPayload> STREAM_CODEC =
            StreamCodec.of(
                    P8PayloadCodecSupport::encodePresentationEvent,
                    P8PayloadCodecSupport::decodePresentationEvent);

    private final long catalogGeneration;
    private final PresentationEventKind kind;
    private final PresentationSourceSummary sourceSummary;
    private final ResourceLocation dimension;
    private final PresentationPosition position;
    private final PresentationDirection direction;
    private final PresentationAppearance appearance;
    private final long visualSeed;
    private final long sequence;
    private final int bodySize;

    PresentationEventPayload(PresentationEvent event) {
        this(
                Objects.requireNonNull(event, "event").catalogGeneration(),
                event.kind(),
                event.sourceSummary(),
                event.dimension(),
                event.position(),
                event.direction(),
                event.appearance(),
                event.visualSeed(),
                event.sequence());
        if (bodySize != event.bodySize()) {
            throw new IllegalArgumentException(
                    "presentation event body size disagrees with the wire schema");
        }
    }

    PresentationEventPayload(
            long catalogGeneration,
            PresentationEventKind kind,
            PresentationSourceSummary sourceSummary,
            ResourceLocation dimension,
            PresentationPosition position,
            PresentationDirection direction,
            PresentationAppearance appearance,
            long visualSeed,
            long sequence) {
        if (catalogGeneration < 1L) {
            throw new IllegalArgumentException("catalogGeneration must be positive");
        }
        this.catalogGeneration = catalogGeneration;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sourceSummary = Objects.requireNonNull(sourceSummary, "sourceSummary");
        this.dimension = P8PayloadCodecSupport.requireResourceLocation(
                dimension,
                PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                "dimension");
        this.position = Objects.requireNonNull(position, "position");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.visualSeed = visualSeed;
        if (sequence < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        this.sequence = sequence;
        bodySize = P8PayloadCodecSupport.presentationEventBodySize(this);
        if (bodySize > PresentationLimits.MAX_LEGAL_EVENT_BODY_BYTES
                || bodySize > PresentationLimits.MAX_EVENT_BODY_BYTES) {
            throw new IllegalArgumentException("presentation event body exceeds the P8 bound");
        }
    }

    long catalogGeneration() {
        return catalogGeneration;
    }

    PresentationEventKind kind() {
        return kind;
    }

    PresentationSourceSummary sourceSummary() {
        return sourceSummary;
    }

    ResourceLocation dimension() {
        return dimension;
    }

    PresentationPosition position() {
        return position;
    }

    PresentationDirection direction() {
        return direction;
    }

    PresentationAppearance appearance() {
        return appearance;
    }

    long visualSeed() {
        return visualSeed;
    }

    long sequence() {
        return sequence;
    }

    int bodySize() {
        return bodySize;
    }

    @Override
    public Type<PresentationEventPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PresentationEventPayload that
                        && catalogGeneration == that.catalogGeneration
                        && visualSeed == that.visualSeed
                        && sequence == that.sequence
                        && kind == that.kind
                        && sourceSummary.equals(that.sourceSummary)
                        && dimension.equals(that.dimension)
                        && position.equals(that.position)
                        && direction.equals(that.direction)
                        && appearance.equals(that.appearance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                catalogGeneration,
                kind,
                sourceSummary,
                dimension,
                position,
                direction,
                appearance,
                visualSeed,
                sequence);
    }
}
