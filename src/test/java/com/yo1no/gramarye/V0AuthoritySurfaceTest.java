package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class V0AuthoritySurfaceTest {
    private static final Path ROOT = projectRoot();
    private static final Path EVIDENCE = ROOT.resolve("docs/evidence/v0");
    private static final Path AUTHORITY = ROOT.resolve(
            "docs/codex-spec/22_DP2_V0平台證據、工程契約與整合交付修正案.md");
    private static final String STARTUP = "com.yo1no.gramarye.V0StartupLoadState$";
    private static final String PHASE = "com.yo1no.gramarye.SkillRuntimeService$V0CarrierBinding$Phase";

    @Test
    void evidenceAndSurfaceManifestsMatchAuthority() throws IOException, NoSuchAlgorithmException {
        var text = Files.readString(AUTHORITY);
        var registered = Set.of(
                "manifest.json", "member-hashes.csv", "compiled-anchor-manifest.json",
                "mixinextras-manifest.json", "historical-Z-unavailability-ledger.md",
                "java-surface-classfile-manifest.json", "java-surface-before.json", "java-surface-after.json",
                "java-surface-totals.json", "config-key-order.tsv", "projection-inventory.tsv");
        var digest = MessageDigest.getInstance("SHA-256");
        for (var file : registered) {
            var bytes = Files.readAllBytes(EVIDENCE.resolve(file));
            var sha256 = HexFormat.of().formatHex(digest.digest(bytes));
            assertTrue(text.contains("| [" + file + "](../evidence/v0/" + file + ") | "
                    + bytes.length + " | " + sha256 + " |"), file);
        }

        var platform = read("manifest.json");
        assertEquals(51, platform.getAsJsonArray("required_members").size());
        assertEquals(12, platform.getAsJsonArray("motion_supplement_members").size());
        assertEquals("NOT_RECOVERED", platform.getAsJsonObject("historical_z").get("status").getAsString());
    }

    @Test
    void correctedJavaDeclarationsAndBothArtifactCountsAreExact() throws IOException {
        var after = read("java-surface-after.json");
        var deltas = after.getAsJsonObject("declaration_counts");
        assertEquals(deltas, read("java-surface-totals.json")
                .getAsJsonObject("proposed_additions_to_existing_scoped_declaration_fence"));
        var fields = List.of("A", "A_implicit_public_nonexported_types", "B", "B_implicit_public_nonexported",
                "C", "D", "E", "F_exported", "F_nonexported", "G_exported", "G_nonexported",
                "H", "I_types", "I", "J", "J_private_owner_local_types", "K", "K_RECORD");
        assertCounts(deltas.getAsJsonObject("ordinary_delta"), fields,
                List.of(24, 4, 0, 2, 45, 15, 3, 12, 14, 40, 43, 6, 50, 80, 14, 1, 0, 110));
        assertCounts(deltas.getAsJsonObject("engineering_only_increment"), fields,
                List.of(4, 0, 0, 0, 5, 0, 0, 4, 4, 6, 7, 0, 7, 2, 6, 0, 0, 0));

        var types = after.getAsJsonArray("types");
        var declarations = after.getAsJsonArray("declarations");
        for (var name : List.of("Disabled", "Unavailable", "Ready", "Invalid")) {
            var type = exactType(types, STARTUP + name);
            assertEquals("public", type.get("visibility").getAsString());
            assertEquals("both", type.get("artifact").getAsString());
            assertFalse(type.get("exported_api").getAsBoolean());
            assertTrue(type.getAsJsonArray("inner_classes_flags").asList().stream()
                    .map(JsonElement::getAsString).toList().containsAll(List.of("ACC_PUBLIC", "ACC_STATIC")));
        }
        assertCanonicalConstructor(declarations, "Ready", "(Lcom/yo1no/gramarye/V0StartupLimits;)V");
        assertCanonicalConstructor(declarations, "Invalid", "(Lcom/yo1no/gramarye/V0ConfigFailure;)V");
        assertEquals("private", exactType(types, PHASE).get("visibility").getAsString());
        assertFalse(exactType(types, PHASE).get("exported_api").getAsBoolean());

        var phase = declarations.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> PHASE.equals(row.get("owner").getAsString())).toList();
        assertEquals(9, phase.size());
        var names = new HashSet<String>();
        for (var row : phase) {
            assertEquals("public", row.get("visibility").getAsString());
            assertEquals("both", row.get("artifact").getAsString());
            assertFalse(row.get("exported_api").getAsBoolean());
            var name = row.get("name").getAsString();
            assertTrue(names.add(name));
            var phaseDescriptor = "L" + PHASE.replace('.', '/') + ";";
            var descriptor = row.get("descriptor").getAsString();
            if ("values".equals(name)) {
                assertEquals("()[" + phaseDescriptor, descriptor);
                assertEquals("F", row.get("classification").getAsString());
            } else if ("valueOf".equals(name)) {
                assertEquals("(Ljava/lang/String;)" + phaseDescriptor, descriptor);
                assertEquals("F", row.get("classification").getAsString());
            } else {
                assertEquals(phaseDescriptor, descriptor);
                assertEquals("G", row.get("classification").getAsString());
            }
        }
        assertEquals(Set.of("RESERVED", "ADDING_INERT", "OPEN", "OBSERVED_PENDING_CLEANUP",
                "CLAIMED", "CLOSING", "CLOSED", "values", "valueOf"), names);
        var classfile = read("java-surface-classfile-manifest.json");
        assertEquals("PASS", classfile.get("status").getAsString());
        assertEquals(5, classfile.getAsJsonArray("classfiles").size());
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    private static JsonObject read(String file) throws IOException {
        return JsonParser.parseString(Files.readString(EVIDENCE.resolve(file))).getAsJsonObject();
    }

    private static void assertCounts(JsonObject actual, List<String> fields, List<Integer> expected) {
        assertEquals(expected, fields.stream().map(field -> actual.get(field).getAsInt()).toList());
    }

    private static JsonObject exactType(JsonArray types, String owner) {
        var matches = types.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> owner.equals(row.get("owner").getAsString())).toList();
        assertEquals(1, matches.size(), owner);
        return matches.getFirst();
    }

    private static void assertCanonicalConstructor(JsonArray declarations, String name, String descriptor) {
        var owner = STARTUP + name;
        var matches = declarations.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> owner.equals(row.get("owner").getAsString())
                        && "<init>".equals(row.get("name").getAsString())).toList();
        assertEquals(1, matches.size(), owner);
        var row = matches.getFirst();
        assertEquals(descriptor, row.get("descriptor").getAsString());
        assertEquals("public", row.get("visibility").getAsString());
        assertEquals("B_implicit_public_nonexported", row.get("classification").getAsString());
        assertFalse(row.get("exported_api").getAsBoolean());
    }
}
