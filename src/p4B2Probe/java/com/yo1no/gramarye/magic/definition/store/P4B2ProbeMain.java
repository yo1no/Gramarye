package com.yo1no.gramarye.magic.definition.store;

import java.nio.file.Path;
import net.minecraft.SharedConstants;

/** Command-line entry point for isolated fixture preparation and post-process checks. */
public final class P4B2ProbeMain {
    private P4B2ProbeMain() {
    }

    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        if (arguments.length < 2) {
            throw new IllegalArgumentException("P4-B2 probe command and path are required");
        }
        switch (arguments[0]) {
            case "prepare-full" -> {
                requireArgumentCount(arguments, 2);
                P4B2FixtureBuilder.prepareFull(Path.of(arguments[1]));
                System.out.println("P4B2_FIXTURE_OK case=full");
            }
            case "prepare-hostile-fname" -> {
                requireArgumentCount(arguments, 2);
                P4B2FixtureBuilder.prepareHostileFname(Path.of(arguments[1]));
                System.out.println("P4B2_FIXTURE_OK case=hostile-fname");
            }
            case "prepare-invalid" -> {
                requireArgumentCount(arguments, 4);
                P4B2FixtureBuilder.prepareInvalidWorlds(
                        Path.of(arguments[1]),
                        Path.of(arguments[2]),
                        Path.of(arguments[3]));
                System.out.println("P4B2_FIXTURE_OK case=invalid-matrix");
            }
            case "prepare-packaged-runtime" -> {
                requireArgumentCount(arguments, 2);
                P4B2FixtureBuilder.preparePackagedRuntime(Path.of(arguments[1]));
                System.out.println("P4B2_FIXTURE_OK case=packaged-runtime");
            }
            case "verify-packaged-artifact" -> {
                requireArgumentCount(arguments, 3);
                System.out.println(P4B2RuntimePackagingVerifier.verifyArtifact(
                        Path.of(arguments[1]), arguments[2]));
            }
            case "verify-packaged-runtime" -> {
                requireArgumentCount(arguments, 2);
                System.out.println(P4B2RuntimePackagingVerifier.verifyRuntime(
                        Path.of(arguments[1])));
            }
            case "verify-full-first" -> verify(
                    arguments, P4B2RunMode.FULL_FIRST);
            case "verify-full-restart" -> verify(
                    arguments, P4B2RunMode.FULL_RESTART);
            case "verify-hostile-fname-first" -> verify(
                    arguments, P4B2RunMode.HOSTILE_FNAME_FIRST);
            case "verify-hostile-fname-restart" -> verify(
                    arguments, P4B2RunMode.HOSTILE_FNAME_RESTART);
            case "verify-malformed-first" -> verify(
                    arguments, P4B2RunMode.MALFORMED_FIRST);
            case "verify-malformed-restart" -> verify(
                    arguments, P4B2RunMode.MALFORMED_RESTART);
            case "verify-trailing-first" -> verify(
                    arguments, P4B2RunMode.TRAILING_FIRST);
            case "verify-trailing-restart" -> verify(
                    arguments, P4B2RunMode.TRAILING_RESTART);
            case "verify-second-member-first" -> verify(
                    arguments, P4B2RunMode.SECOND_MEMBER_FIRST);
            case "verify-second-member-restart" -> verify(
                    arguments, P4B2RunMode.SECOND_MEMBER_RESTART);
            default -> throw new IllegalArgumentException("unknown P4-B2 probe command");
        }
    }

    private static void verify(String[] arguments, P4B2RunMode mode) throws Exception {
        requireArgumentCount(arguments, 2);
        System.out.println(P4B2FileVerifier.verify(Path.of(arguments[1]), mode).line());
    }

    private static void requireArgumentCount(String[] arguments, int expected) {
        if (arguments.length != expected) {
            throw new IllegalArgumentException("wrong number of P4-B2 probe arguments");
        }
    }
}
