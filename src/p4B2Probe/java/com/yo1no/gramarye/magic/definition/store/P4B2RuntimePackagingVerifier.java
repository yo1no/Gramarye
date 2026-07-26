package com.yo1no.gramarye.magic.definition.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import net.minecraft.core.RegistryAccess;

/** Bounded artifact and real packaged-server checks for the P4-B2-R dependency repair. */
final class P4B2RuntimePackagingVerifier {
    private static final String GROUP = "org.apache.commons";
    private static final String ARTIFACT = "commons-compress";
    private static final String NESTED_PREFIX = "META-INF/jarjar/";
    private static final String METADATA_PATH = NESTED_PREFIX + "metadata.json";
    private static final String GZIP_CLASS =
            "org/apache/commons/compress/compressors/gzip/"
                    + "GzipCompressorInputStream.class";
    private static final String MODULE_NAME = "org.apache.commons.compress";
    private static final String FIXTURE_MANIFEST = "p4-b2-packaged-runtime.properties";
    private static final long MAX_FIXTURE_MANIFEST_BYTES = 2_048;
    private static final Set<String> FIXTURE_KEYS = Set.of(
            "source_primary_sha256",
            "source_store_sha256",
            "canonical_store_sha256",
            "expected_store_bytes",
            "expected_histories",
            "expected_revisions");

    private P4B2RuntimePackagingVerifier() {
    }

    static void writeFixtureManifest(
            Path worldRoot,
            String sourcePrimarySha256,
            String sourceStoreSha256,
            String canonicalStoreSha256,
            int storeBytes,
            int histories,
            int revisions) throws IOException {
        var facts = new RuntimeFixtureFacts(
                sourcePrimarySha256,
                sourceStoreSha256,
                canonicalStoreSha256,
                storeBytes,
                histories,
                revisions);
        var text = "source_primary_sha256=" + facts.sourcePrimarySha256() + "\n"
                + "source_store_sha256=" + facts.sourceStoreSha256() + "\n"
                + "canonical_store_sha256=" + facts.canonicalStoreSha256() + "\n"
                + "expected_store_bytes=" + facts.storeBytes() + "\n"
                + "expected_histories=" + facts.histories() + "\n"
                + "expected_revisions=" + facts.revisions() + "\n";
        var bytes = text.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_FIXTURE_MANIFEST_BYTES) {
            throw new AssertionError("packaged-runtime fixture manifest is unbounded");
        }
        Files.write(worldRoot.resolve(FIXTURE_MANIFEST), bytes);
    }

    static String verifyArtifact(Path artifactPath, String expectedVersion) throws IOException {
        if (expectedVersion.isBlank() || expectedVersion.length() > 32) {
            throw new IllegalArgumentException("Commons Compress version is missing or unbounded");
        }
        var expectedNestedPath = NESTED_PREFIX + ARTIFACT + "-" + expectedVersion + ".jar";
        try (var artifact = new JarFile(artifactPath.toFile(), true)) {
            var nestedJarCount = 0;
            var entries = artifact.entries();
            while (entries.hasMoreElements()) {
                var name = entries.nextElement().getName();
                if (name.startsWith("org/apache/commons/compress/")) {
                    throw new AssertionError("Commons Compress classes were shaded into the root jar");
                }
                if (name.startsWith(NESTED_PREFIX) && name.endsWith(".jar")) {
                    nestedJarCount++;
                }
            }
            if (nestedJarCount != 1) {
                throw new AssertionError("deployable artifact must contain one reviewed nested jar");
            }

            var metadataEntry = artifact.getJarEntry(METADATA_PATH);
            if (metadataEntry == null) {
                throw new AssertionError("Jar-in-Jar metadata is missing");
            }
            JsonObject metadata;
            try (Reader reader = new InputStreamReader(
                    artifact.getInputStream(metadataEntry), StandardCharsets.UTF_8)) {
                metadata = JsonParser.parseReader(reader).getAsJsonObject();
            }
            var jars = metadata.getAsJsonArray("jars");
            if (jars == null || jars.size() != 1) {
                throw new AssertionError("Jar-in-Jar metadata must describe one dependency");
            }
            var nestedMetadata = jars.get(0).getAsJsonObject();
            var identifier = nestedMetadata.getAsJsonObject("identifier");
            var version = nestedMetadata.getAsJsonObject("version");
            var nestedPath = requiredString(nestedMetadata, "path");
            if (!GROUP.equals(requiredString(identifier, "group"))
                    || !ARTIFACT.equals(requiredString(identifier, "artifact"))
                    || !expectedVersion.equals(requiredString(version, "artifactVersion"))
                    || !("[" + expectedVersion + "]").equals(requiredString(version, "range"))
                    || !expectedNestedPath.equals(nestedPath)) {
                throw new AssertionError("Jar-in-Jar metadata does not match the locked coordinate");
            }

            var nestedEntry = artifact.getJarEntry(nestedPath);
            if (nestedEntry == null || nestedEntry.isDirectory() || nestedEntry.getSize() <= 0) {
                throw new AssertionError("Jar-in-Jar metadata points to a missing nested jar");
            }
            verifyNestedJar(artifact, nestedEntry, expectedVersion);
        }
        return boundedLine(
                "P4B2_PACKAGING_OK artifact=gramarye dependency="
                        + ARTIFACT + " version=" + expectedVersion);
    }

    static String verifyRuntime(Path gameDirectory) throws IOException {
        var worldRoot = P4B2FixtureManifest.worldRoot(gameDirectory);
        var facts = readFixtureManifest(worldRoot);
        var primary = P4B2FixtureManifest.primary(worldRoot);
        var primaryHash = P4B2Hashing.sha256(primary);
        if (primaryHash.equals(facts.sourcePrimarySha256())) {
            throw new AssertionError("packaged server did not rewrite its noncanonical primary");
        }
        if (Files.exists(P4B2FixtureManifest.oldPrimary(worldRoot))) {
            throw new AssertionError("packaged server unexpectedly created or used .dat_old");
        }

        var loaded = SkillSavedDataPrimaryIngress.load(
                primary, java.util.Optional.of(RegistryAccess.EMPTY));
        if (!(loaded instanceof SkillSavedDataPrimaryLoadResult.Ready ready)
                || ready.candidate().rewriteRequired()
                || ready.candidate().facts().truncated()
                || !ready.candidate().facts().facts().isEmpty()) {
            throw new AssertionError("packaged server output is not canonical Ready");
        }
        var candidate = ready.candidate();
        var carrier = candidate.carrier().storeCarrier();
        if (candidate.carrier().pending().byteCount() != 0
                || carrier.storeByteCount() != facts.storeBytes()
                || !P4B2Hashing.sha256(carrier).equals(facts.canonicalStoreSha256())) {
            throw new AssertionError("packaged server restored a different Store carrier");
        }
        P4B2FixtureBuilder.requireCarrierDomain(
                carrier, candidate.store(), facts.histories(), facts.revisions());
        return boundedLine(
                "P4B2_PACKAGED_RUNTIME_OK primary_bytes=" + Files.size(primary)
                        + " store_bytes=" + carrier.storeByteCount()
                        + " histories=" + facts.histories()
                        + " revisions=" + facts.revisions()
                        + " checksum=" + P4B2Hashing.witness(primaryHash));
    }

    private static void verifyNestedJar(
            JarFile artifact,
            java.util.jar.JarEntry nestedEntry,
            String expectedVersion) throws IOException {
        var foundClass = false;
        var foundLicense = false;
        var foundNotice = false;
        try (var nested = new JarInputStream(new BufferedInputStream(
                artifact.getInputStream(nestedEntry)))) {
            var manifest = nested.getManifest();
            if (manifest == null
                    || !MODULE_NAME.equals(manifest.getMainAttributes()
                            .getValue("Automatic-Module-Name"))
                    || !expectedVersion.equals(manifest.getMainAttributes()
                            .getValue("Implementation-Version"))) {
                throw new AssertionError("nested jar manifest does not match its metadata");
            }
            java.util.jar.JarEntry entry;
            while ((entry = nested.getNextJarEntry()) != null) {
                var name = entry.getName();
                foundClass |= GZIP_CLASS.equals(name);
                foundLicense |= name.startsWith("META-INF/LICENSE");
                foundNotice |= name.startsWith("META-INF/NOTICE");
            }
        }
        if (!foundClass || !foundLicense || !foundNotice) {
            throw new AssertionError("nested jar lost its gzip class or legal notices");
        }
    }

    private static RuntimeFixtureFacts readFixtureManifest(Path worldRoot) throws IOException {
        var path = worldRoot.resolve(FIXTURE_MANIFEST);
        if (Files.size(path) > MAX_FIXTURE_MANIFEST_BYTES) {
            throw new IllegalArgumentException("packaged-runtime fixture manifest is unbounded");
        }
        var values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.US_ASCII)) {
            values.load(reader);
        }
        if (!values.stringPropertyNames().equals(FIXTURE_KEYS)) {
            throw new IllegalArgumentException("packaged-runtime manifest fields are not exact");
        }
        return new RuntimeFixtureFacts(
                required(values, "source_primary_sha256"),
                required(values, "source_store_sha256"),
                required(values, "canonical_store_sha256"),
                parsePositiveInt(values, "expected_store_bytes"),
                parsePositiveInt(values, "expected_histories"),
                parsePositiveInt(values, "expected_revisions"));
    }

    private static String requiredString(JsonObject object, String key) {
        var element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new AssertionError("Jar-in-Jar metadata field is missing or not a string");
        }
        var value = element.getAsString();
        if (value.isEmpty() || value.length() > 256) {
            throw new AssertionError("Jar-in-Jar metadata string is missing or unbounded");
        }
        return value;
    }

    private static String required(Properties values, String key) {
        var value = values.getProperty(key);
        if (value == null || value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("packaged-runtime manifest value is invalid");
        }
        return value;
    }

    private static int parsePositiveInt(Properties values, String key) {
        try {
            var value = Integer.parseInt(required(values, key));
            if (value <= 0) {
                throw new IllegalArgumentException("packaged-runtime fact is not positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("packaged-runtime integer is malformed");
        }
    }

    private static String boundedLine(String line) {
        if (line.length() > 320) {
            throw new IllegalStateException("packaging summary is unbounded");
        }
        return line;
    }

    private record RuntimeFixtureFacts(
            String sourcePrimarySha256,
            String sourceStoreSha256,
            String canonicalStoreSha256,
            int storeBytes,
            int histories,
            int revisions) {
        private RuntimeFixtureFacts {
            P4B2Hashing.requireSha256(sourcePrimarySha256);
            P4B2Hashing.requireSha256(sourceStoreSha256);
            P4B2Hashing.requireSha256(canonicalStoreSha256);
            if (sourceStoreSha256.equals(canonicalStoreSha256)
                    || storeBytes <= 0 || histories <= 0 || revisions <= 0) {
                throw new IllegalArgumentException("packaged-runtime fixture facts are invalid");
            }
        }
    }
}
