package com.yo1no.gramarye.magic.definition.player;

import java.nio.file.Path;
import net.minecraft.SharedConstants;

/** Command-line fixture and post-run entry point for the isolated P4-C2 gate. */
public final class P4C2ProbeMain {
    private P4C2ProbeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        if (arguments.length < 2) {
            throw new IllegalArgumentException("P4-C2 probe command and paths are required");
        }
        switch (arguments[0]) {
            case "prepare-worlds" -> {
                requireCount(arguments, 4);
                P4C2FixtureBuilder.prepareWorlds(
                        Path.of(arguments[1]),
                        Path.of(arguments[2]),
                        Path.of(arguments[3]));
                System.out.println("P4C2_FIXTURE_OK case=world-matrix");
            }
            case "verify-ready-first" -> verify(
                    arguments, P4C2RunMode.READY_FIRST);
            case "verify-ready-restart" -> verify(
                    arguments, P4C2RunMode.READY_RESTART);
            case "verify-preserved-raw-first" -> verify(
                    arguments, P4C2RunMode.PRESERVED_RAW_FIRST);
            case "verify-preserved-raw-restart" -> verify(
                    arguments, P4C2RunMode.PRESERVED_RAW_RESTART);
            case "verify-oversize-first" -> verify(
                    arguments, P4C2RunMode.OVERSIZE_FIRST);
            case "verify-oversize-restart" -> verify(
                    arguments, P4C2RunMode.OVERSIZE_RESTART);
            default -> throw new IllegalArgumentException("unknown P4-C2 probe command");
        }
    }

    private static void verify(String[] arguments, P4C2RunMode mode)
            throws Exception {
        requireCount(arguments, 2);
        System.out.println(P4C2FileVerifier.verify(
                Path.of(arguments[1]), mode).line());
    }

    private static void requireCount(String[] arguments, int expected) {
        if (arguments.length != expected) {
            throw new IllegalArgumentException("wrong number of P4-C2 probe arguments");
        }
    }
}
