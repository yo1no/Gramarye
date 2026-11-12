package com.yo1no.gramarye.magic.definition.scoped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** Charges an encoded byte boundary before copying into the staging buffer. */
final class ScopeDBoundedOutput extends OutputStream {
    private final int max;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    ScopeDBoundedOutput(int max) {
        this.max = max;
    }

    @Override
    public void write(int value) throws IOException {
        requireAvailable(1);
        bytes.write(value);
    }

    @Override
    public void write(byte[] source, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, source.length);
        requireAvailable(length);
        bytes.write(source, offset, length);
    }

    byte[] copyBytes() {
        return bytes.toByteArray();
    }

    private void requireAvailable(int length) throws IOException {
        if (length > max - bytes.size()) {
            throw new IOException("encoded byte capacity exceeded");
        }
    }
}
