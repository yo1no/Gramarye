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
    // Local development candidate e22a5cde; this pin is not a runtime acceptance claim.
    private static final String L1_PRODUCT_PIN = "fc15bf045905285c4b8dfe661b41d34e890f628c3399c5824070194ae395aeb8";
    private static final String COOLDOWN_PRODUCT_PIN = "ec5158a5051c8913d30f765bbb8ecee2f5c98a6bde4d1c13ae17a6a5719be030";

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

    static boolean l1ContextCase() {
        return java.util.List.of("l1-qctx", "l1-capacity")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }

    static boolean onlineContextCase() { return historicalCase() || currentContextCase() || l1ContextCase(); }

    static String semanticOnlineCase() {
        var selected = System.getProperty("gramarye.p11.online.case", "");
        return switch (selected) {
            case "single", "qctx", "capacity" -> selected;
            case "c4a-qctx", "l1-qctx" -> "qctx";
            case "c4a-capacity", "l1-capacity" -> "capacity";
            default -> throw new IllegalStateException("UNKNOWN_ONLINE_CONTEXT_CASE");
        };
    }

    private static String productPin() {
        if (P11CooldownServerHarness.selected() || P11CooldownHostProbe.selected() || java.util.List.of("cooldown-l1-pre-spawn", "cooldown-l1-open", "cooldown-l1-claimed")
                .contains(System.getProperty("gramarye.p11.online.case", ""))) {
            require(COOLDOWN_PRODUCT_PIN != null && COOLDOWN_PRODUCT_PIN.matches("[0-9a-f]{64}"), "COOLDOWN_PRODUCT_PIN_PENDING");
            return COOLDOWN_PRODUCT_PIN;
        }
        if (P11L1ServerHarness.enabled() || l1ContextCase() || P11L1HostStopProbe.selected()) {
            require(L1_PRODUCT_PIN != null && L1_PRODUCT_PIN.matches("[0-9a-f]{64}"), "L1_PRODUCT_PIN_PENDING");
            return L1_PRODUCT_PIN;
        }
        if (historicalCase()) { return HISTORICAL_PRODUCT_PIN; }
        require(c4aCase() || currentContextCase(), "UNKNOWN_ONLINE_CASE");
        require(C4A_PRODUCT_PIN != null && C4A_PRODUCT_PIN.matches("[0-9a-f]{64}"), "C4A_PRODUCT_PIN_PENDING");
        return C4A_PRODUCT_PIN;
    }

    static String verifyFrozenJar() throws IOException {
        require(P11C4aScenario.MODE != P11C4aScenario.Mode.L1_HOST_STOP || P11L1HostStopProbe.selected(),
                "L1_HOST_CASE_MODE_MISMATCH");
        require(P11C4aScenario.MODE != P11C4aScenario.Mode.COOLDOWN_HOST || P11CooldownHostProbe.selected(),
                "COOLDOWN_HOST_CASE_MODE_MISMATCH");
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
                    || value.getName().contains("P11L1")
                    || value.getName().contains("P11C4a")
                    || value.getName().contains("P11CooldownServerHarness")
                    || value.getName().contains("P11CooldownClientHarness")
                    || value.getName().contains("P11CooldownInputObservation")
                    || value.getName().contains("P11CooldownRestartProbe")
                    || value.getName().contains("P11CooldownL1Probe")
                    || value.getName().contains("P11CooldownCloneProbe")
                    || value.getName().contains("P11CooldownCloneClientProbe")
                    || value.getName().contains("P11CooldownFaultProbe")
                    || value.getName().contains("P11CooldownCostProbe")
                    || value.getName().contains("P11CooldownDurabilityProbe")
                    || value.getName().contains("P11CooldownDualProbe")
                    || value.getName().contains("P11CooldownHostProbe")
                    || value.getName().contains("P11CooldownHostClientProbe")
                    || value.getName().contains("P11CooldownDurabilityClientProbe")
                    || value.getName().contains("P11CooldownGameTest")
                    || value.getName().contains("/harnessmixin/")
                    || value.getName().equals("gramarye-p11-online-harness.mixins.json")
                    || value.getName().equals("gramarye-p11-cooldown-harness.mixins.json")
                    || value.getName().equals("gramarye-p11-gametest-harness.mixins.json")
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
