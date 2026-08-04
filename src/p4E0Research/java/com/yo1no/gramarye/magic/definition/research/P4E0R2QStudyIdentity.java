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
    static final int FORMAL_SCHEMA_VERSION = 1;
    static final int FORMAL_IMPLEMENTATION_SCHEMA_VERSION = 1;

    private final String gitHead;
    private final String gitTree;
    private final String profileManifestHash;
    private final String casePlanHash;
    private final String fixtureRootHash;
    private final int researchImplementationSchemaVersion;
    private final String formalRunOrderHash;
    private final int qualificationHeapMiB;
    private final long researchDiskBudgetBytes;
    private final boolean formal;
    private final String canonicalPayload;
    private final String studyId;

    private P4E0R2QStudyIdentity(
            String gitHead,
            String gitTree,
            String profileManifestHash,
            String casePlanHash,
            String fixtureRootHash,
            int researchImplementationSchemaVersion) {
        this(
                gitHead,
                gitTree,
                profileManifestHash,
                casePlanHash,
                fixtureRootHash,
                researchImplementationSchemaVersion,
                "",
                0,
                0L,
                false);
    }

    private P4E0R2QStudyIdentity(
            String gitHead,
            String gitTree,
            String profileManifestHash,
            String casePlanHash,
            String fixtureRootHash,
            int researchImplementationSchemaVersion,
            String formalRunOrderHash,
            int qualificationHeapMiB,
            long researchDiskBudgetBytes,
            boolean formal) {
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
        this.formal = formal;
        if (formal) {
            this.formalRunOrderHash = requireSha256(
                    formalRunOrderHash, "formalRunOrderHash");
            if (qualificationHeapMiB != 1_536
                    || researchDiskBudgetBytes != 12_884_901_888L
                    || researchImplementationSchemaVersion
                            != FORMAL_IMPLEMENTATION_SCHEMA_VERSION) {
                throw new IllegalArgumentException("formal R2Q execution tuple changed");
            }
            this.qualificationHeapMiB = qualificationHeapMiB;
            this.researchDiskBudgetBytes = researchDiskBudgetBytes;
        } else {
            if (!formalRunOrderHash.isEmpty()
                    || qualificationHeapMiB != 0
                    || researchDiskBudgetBytes != 0L) {
                throw new IllegalArgumentException("legacy R2Q-A identity carries formal facts");
            }
            this.formalRunOrderHash = "";
            this.qualificationHeapMiB = 0;
            this.researchDiskBudgetBytes = 0L;
        }
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

    static P4E0R2QStudyIdentity calculateFormal(
            String gitHead,
            String gitTree,
            String profileManifestHash,
            String casePlanHash,
            String fixtureRootHash,
            String formalRunOrderHash,
            int researchImplementationSchemaVersion,
            int qualificationHeapMiB,
            long researchDiskBudgetBytes) {
        return new P4E0R2QStudyIdentity(
                gitHead,
                gitTree,
                profileManifestHash,
                casePlanHash,
                fixtureRootHash,
                researchImplementationSchemaVersion,
                formalRunOrderHash,
                qualificationHeapMiB,
                researchDiskBudgetBytes,
                true);
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

    String formalRunOrderHash() {
        return formalRunOrderHash;
    }

    int qualificationHeapMiB() {
        return qualificationHeapMiB;
    }

    long researchDiskBudgetBytes() {
        return researchDiskBudgetBytes;
    }

    boolean formal() {
        return formal;
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
        var schema = formal ? FORMAL_SCHEMA_VERSION : SCHEMA_VERSION;
        var base = "{\"schema_version\":" + schema
                + ",\"git_head\":\"" + gitHead + '"'
                + ",\"git_tree\":\"" + gitTree + '"'
                + ",\"profile_manifest_sha256\":\"" + profileManifestHash + '"'
                + ",\"case_plan_sha256\":\"" + casePlanHash + '"'
                + ",\"fixture_root_sha256\":\"" + fixtureRootHash + '"'
                + ",\"research_implementation_schema_version\":"
                + researchImplementationSchemaVersion;
        if (!formal) {
            return base + '}';
        }
        return base
                + ",\"formal_run_order_sha256\":\"" + formalRunOrderHash + '"'
                + ",\"qualification_heap_mib\":" + qualificationHeapMiB
                + ",\"research_disk_budget_bytes\":" + researchDiskBudgetBytes
                + '}';
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
