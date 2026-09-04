package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;

final class P7S2CodecTestSupport {
    private P7S2CodecTestSupport() {
        throw new AssertionError("no instances");
    }

    static <T> byte[] encode(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        Objects.requireNonNull(codec, "codec");
        try (var owned = emptyBuffer()) {
            codec.encode(owned.buffer(), value);
            owned.assertCallerStillOwnsExactlyOneReference();
            return ByteBufUtil.getBytes(
                    owned.buffer(),
                    owned.buffer().readerIndex(),
                    owned.buffer().readableBytes(),
                    false);
        }
    }

    static <T> T decode(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, byte[] body) {
        Objects.requireNonNull(codec, "codec");
        try (var owned = bufferContaining(body)) {
            var decoded = codec.decode(owned.buffer());
            owned.assertCallerStillOwnsExactlyOneReference();
            return decoded;
        }
    }

    static <T> DecoderException assertDecodeFailure(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, byte[] body) {
        Objects.requireNonNull(codec, "codec");
        try (var owned = bufferContaining(body)) {
            var failure = assertThrows(
                    DecoderException.class,
                    () -> codec.decode(owned.buffer()));
            owned.assertCallerStillOwnsExactlyOneReference();
            return failure;
        }
    }

    static <T> EncoderException assertEncodeFailure(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        Objects.requireNonNull(codec, "codec");
        try (var owned = emptyBuffer()) {
            var failure = assertThrows(
                    EncoderException.class,
                    () -> codec.encode(owned.buffer(), value));
            owned.assertCallerStillOwnsExactlyOneReference();
            return failure;
        }
    }

    static byte[] body(Consumer<RegistryFriendlyByteBuf> writer) {
        Objects.requireNonNull(writer, "writer");
        try (var owned = emptyBuffer()) {
            writer.accept(owned.buffer());
            owned.assertCallerStillOwnsExactlyOneReference();
            return ByteBufUtil.getBytes(
                    owned.buffer(),
                    owned.buffer().readerIndex(),
                    owned.buffer().readableBytes(),
                    false);
        }
    }

    static byte[] append(byte[] body, int... unsignedBytes) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(unsignedBytes, "unsignedBytes");
        var result = Arrays.copyOf(body, body.length + unsignedBytes.length);
        for (var index = 0; index < unsignedBytes.length; index++) {
            var value = unsignedBytes[index];
            if (value < 0 || value > 0xff) {
                throw new IllegalArgumentException("byte value is outside 0..255");
            }
            result[body.length + index] = (byte) value;
        }
        return result;
    }

    static OwnedBuffer emptyBuffer() {
        return new OwnedBuffer(new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE));
    }

    static OwnedBuffer bufferContaining(byte[] body) {
        Objects.requireNonNull(body, "body");
        var byteBuffer = Unpooled.buffer(Math.max(1, body.length));
        byteBuffer.writeBytes(body);
        return new OwnedBuffer(new RegistryFriendlyByteBuf(
                byteBuffer, RegistryAccess.EMPTY, ConnectionType.NEOFORGE));
    }

    static final class OwnedBuffer implements AutoCloseable {
        private final RegistryFriendlyByteBuf buffer;
        private boolean closed;

        private OwnedBuffer(RegistryFriendlyByteBuf buffer) {
            this.buffer = buffer;
            assertEquals(1, buffer.refCnt(), "new test buffer reference count");
        }

        RegistryFriendlyByteBuf buffer() {
            if (closed) {
                throw new IllegalStateException("test buffer is closed");
            }
            return buffer;
        }

        int referenceCount() {
            return buffer.refCnt();
        }

        void assertCallerStillOwnsExactlyOneReference() {
            assertEquals(
                    1,
                    buffer.refCnt(),
                    "codec must neither retain nor release its caller-owned buffer");
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("test buffer released more than once");
            }
            assertCallerStillOwnsExactlyOneReference();
            assertTrue(buffer.release(), "the caller's one release must deallocate the buffer");
            assertEquals(0, buffer.refCnt(), "test terminal must retain zero references");
            closed = true;
        }
    }
}
