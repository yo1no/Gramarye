package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Checks only the explicitly supplied public product artifact, never any authentication input. */
final class P11OnlineInputs {
    private static final String HISTORICAL_PRODUCT_PIN = "4d79086f628daa137281f71312596d4dda861bafbaa9b62a65cd70e5257d248b";
    // Frozen only after the coordinated C4a product build. No property can substitute a pin.
    private static final String C4A_PRODUCT_PIN = "9f499356841f302b04d3846d85640ed273ec3104dbc0e743f8cea452243b4c97";

    private P11OnlineInputs() {}

    static boolean historicalCase() {
        return java.util.List.of("single", "qctx", "capacity")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }

    static boolean c4aCase() {
        return java.util.List.of("c4a-dedicated", "c4a-host-lan", "c4a-reward")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }

    static boolean currentContextCase() {
        return java.util.List.of("c4a-qctx", "c4a-capacity")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }

    static boolean onlineContextCase() { return historicalCase() || currentContextCase(); }

    static String semanticOnlineCase() {
        var selected = System.getProperty("gramarye.p11.online.case", "");
        return switch (selected) {
            case "single", "qctx", "capacity" -> selected;
            case "c4a-qctx" -> "qctx";
            case "c4a-capacity" -> "capacity";
            default -> throw new IllegalStateException("UNKNOWN_ONLINE_CONTEXT_CASE");
        };
    }

    private static String productPin() {
        if (historicalCase()) { return HISTORICAL_PRODUCT_PIN; }
        require(c4aCase() || currentContextCase(), "UNKNOWN_ONLINE_CASE");
        require(C4A_PRODUCT_PIN != null && C4A_PRODUCT_PIN.matches("[0-9a-f]{64}"), "C4A_PRODUCT_PIN_PENDING");
        return C4A_PRODUCT_PIN;
    }

    static String verifyFrozenJar() throws IOException {
        if (c4aCase()) {
            require("c4a-reward".equals(System.getProperty("gramarye.p11.online.case", ""))
                    == (P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_REWARD_CONTINUITY), "REWARD_CASE_MODE_MISMATCH");
        }
        var expectedPin = productPin();
        var text = System.getProperty("gramarye.p11.online.jar");
        require(text != null && Path.of(text).isAbsolute(), "MISSING_FROZEN_JAR");
        var jarPath = Path.of(text).toAbsolutePath().normalize();
        require(Files.isRegularFile(jarPath, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(jarPath), "BAD_FROZEN_JAR");
        var separator = java.util.regex.Pattern.quote(java.io.File.pathSeparator);
        var roots = Arrays.stream(System.getProperty("fml.modFolders", "").split(separator))
                .filter(value -> value.startsWith("p11OnlineHarness%%"))
                .map(value -> Path.of(value.substring("p11OnlineHarness%%".length())).toAbsolutePath().normalize()).toList();
        require(roots.contains(jarPath), "FROZEN_JAR_NOT_ACTUAL_MOD_ROOT");
        String entry = "com/yo1no/gramarye/Gramarye.class";
        for (var root : roots) {
            require(root.equals(jarPath) || !Files.exists(root.resolve(entry)), "EXPLODED_MAIN_MOD_ROOT");
        }
        for (var textRoot : System.getProperty("java.class.path", "").split(separator)) {
            if (!textRoot.isBlank()) {
                var root = Path.of(textRoot);
                require(!Files.isDirectory(root) || !Files.exists(root.resolve(entry)), "EXPLODED_MAIN_CLASSPATH");
            }
        }
        try (var jar = new JarFile(jarPath.toFile()); var loaded = Gramarye.class.getResourceAsStream("/" + entry)) {
            require(jar.getJarEntry(entry) != null && loaded != null, "PRODUCT_CLASS_MISSING");
            require(jar.stream().noneMatch(value -> value.getName().contains("P11Online")
                    || value.getName().contains("P11C4a")
                    || value.getName().equals("gramarye-p11-online-harness.mixins.json")
                    || value.getName().equals("gramarye-p11-c4a-harness.mixins.json")), "COMPANION_IN_PRODUCT");
            try (var archived = jar.getInputStream(jar.getJarEntry(entry))) {
                require(Arrays.equals(loaded.readAllBytes(), archived.readAllBytes()), "LOADED_PRODUCT_DIFFERS");
            }
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(jarPath)) {
                var block = new byte[8192];
                for (int count; (count = stream.read(block)) >= 0;) { digest.update(block, 0, count); }
            }
            var hash = HexFormat.of().formatHex(digest.digest());
            require(hash.equals(expectedPin), "FROZEN_PRODUCT_PIN_MISMATCH");
            return hash;
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void require(boolean condition, String code) {
        if (!condition) { throw new IllegalStateException(code); }
    }
}
