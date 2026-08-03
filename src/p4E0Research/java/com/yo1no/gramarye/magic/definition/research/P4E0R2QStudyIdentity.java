package com.yo1no.gramarye.magic.definition.research;

import java.util.Objects;

/**
 * Research-only canonical identity for a future formal R2Q study.
 *
 * <p>R2Q-A only locks and tests this calculation. It does not publish a provenance artifact or
 * start a qualification child process.
 */
final class P4E0R2QStudyIdentity {
    static final int SCHEMA_VERSION = 0;
    static final int CURRENT_IMPLEMENTATION_SCHEMA_VERSION = 0;

    private final String gitHead;
    private final String gitTree;
    private final String profileManifestHash;
    private final String casePlanHash;
    private final String fixtureRootHash;
    private final int researchImplementationSchemaVersion;
    private final String canonicalPayload;
    private final String studyId;

    private P4E0R2QStudyIdentity(
            String gitHead,
            String gitTree,
            String profileManifestHash,
            String casePlanHash,
            String fixtureRootHash,
            int researchImplementationSchemaVersion) {
        this.gitHead = requireGitObjectId(gitHead, "gitHead");
        this.gitTree = requireGitObjectId(gitTree, "gitTree");
        this.profileManifestHash = requireSha256(profileManifestHash, "profileManifestHash");
        this.casePlanHash = requireSha256(casePlanHash, "casePlanHash");
        this.fixtureRootHash = requireSha256(fixtureRootHash, "fixtureRootHash");
        if (researchImplementationSchemaVersion < 0) {
            throw new IllegalArgumentException(
                    "researchImplementationSchemaVersion must be non-negative");
        }
        this.researchImplementationSchemaVersion = researchImplementationSchemaVersion;
        this.canonicalPayload = buildCanonicalPayload();
        this.studyId = P4E0ResearchHashing.sha256(canonicalPayload);
    }

    static P4E0R2QStudyIdentity calculate(
            String gitHead,
            String gitTree,
            String profileManifestHash,
            String casePlanHash,
            String fixtureRootHash,
            int researchImplementationSchemaVersion) {
        return new P4E0R2QStudyIdentity(
                gitHead,
                gitTree,
                profileManifestHash,
                casePlanHash,
                fixtureRootHash,
                researchImplementationSchemaVersion);
    }

    String gitHead() {
        return gitHead;
    }

    String gitTree() {
        return gitTree;
    }

    String profileManifestHash() {
        return profileManifestHash;
    }

    String casePlanHash() {
        return casePlanHash;
    }

    String fixtureRootHash() {
        return fixtureRootHash;
    }

    int researchImplementationSchemaVersion() {
        return researchImplementationSchemaVersion;
    }

    String canonicalPayload() {
        return canonicalPayload;
    }

    String studyId() {
        return studyId;
    }

    String canonicalJson() {
        return canonicalPayload.substring(0, canonicalPayload.length() - 1)
                + ",\"study_id_sha256\":\"" + studyId + "\"}";
    }

    private String buildCanonicalPayload() {
        return "{\"schema_version\":" + SCHEMA_VERSION
                + ",\"git_head\":\"" + gitHead + '"'
                + ",\"git_tree\":\"" + gitTree + '"'
                + ",\"profile_manifest_sha256\":\"" + profileManifestHash + '"'
                + ",\"case_plan_sha256\":\"" + casePlanHash + '"'
                + ",\"fixture_root_sha256\":\"" + fixtureRootHash + '"'
                + ",\"research_implementation_schema_version\":"
                + researchImplementationSchemaVersion + '}';
    }

    private static String requireGitObjectId(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-1 object id");
        }
        return value;
    }

    private static String requireSha256(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
        return value;
    }
}
