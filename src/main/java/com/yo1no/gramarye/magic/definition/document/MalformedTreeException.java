package com.yo1no.gramarye.magic.definition.document;

import java.io.IOException;

/** Internal checked signal for malformed or non-preservable serialized tree data. */
final class MalformedTreeException extends IOException {
    MalformedTreeException(String family) {
        super("Malformed or non-preservable " + family + " tree");
    }

    MalformedTreeException(String family, Throwable cause) {
        super("Malformed or non-preservable " + family + " tree", cause);
    }
}
