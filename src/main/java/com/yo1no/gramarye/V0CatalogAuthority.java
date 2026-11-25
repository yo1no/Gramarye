package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.util.Objects;
import java.util.UUID;

/** Exact, actor-free capability for one P5 slot. Catalog lease state consumes the permits. */
public final class V0CatalogAuthority {
    private final long slotGeneration;
    private final long ownerThreadId;
    private final int maxManifestEntries;

    private V0CatalogAuthority(long slotGeneration, long ownerThreadId, int maxManifestEntries) {
        this.slotGeneration = slotGeneration;
        this.ownerThreadId = ownerThreadId;
        this.maxManifestEntries = maxManifestEntries;
    }

    static V0CatalogAuthority open(long slotGeneration, V0StartupLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (slotGeneration <= 0) {
            throw new IllegalArgumentException("V0 catalog slot generation must be positive");
        }
        return new V0CatalogAuthority(slotGeneration, Thread.currentThread().threadId(),
                limits.maxManifestEntries());
    }

    /** Private-attempt scalar only; equal UUID bits do not qualify a receipt or another authority. */
    UUID cooldownAttemptId(long captureTicket) {
        requireOwnerThread();
        if (captureTicket <= 0) {
            throw new IllegalArgumentException("V0 cooldown capture ticket must be positive");
        }
        return new UUID(slotGeneration, captureTicket);
    }

    Reservation reserve(V0RuntimeCatalog.Candidate candidate) {
        requireOwnerThread();
        return new Reservation(this, Objects.requireNonNull(candidate, "candidate"));
    }

    ReleasePermit permitRelease(V0RuntimeCatalog.Lease lease) {
        requireOwnerThread();
        return new ReleasePermit(this, Objects.requireNonNull(lease, "lease"));
    }

    BootstrapRetirementPermit permitBootstrapRetirement(V0BootstrapRetirementCoordinator.DrainProof drain,
            P11CastCooldownService cooldowns, P11CastCooldownService.RetirementProof retirement,
            SkillOwnerId owner) {
        requireOwnerThread();
        if (drain == null || cooldowns == null || owner == null
                || owner.equals(V0BootstrapRetirementCoordinator.SEED_OWNER)) { return null; }
        var catalog = drain.claim(this, owner, cooldowns);
        if (catalog == null || !cooldowns.claimPolicyRetirement(retirement, owner.value(),
                NormalSpellPolicy.BOOTSTRAP.key())) { return null; }
        return new BootstrapRetirementPermit(this, owner, catalog);
    }

    BootstrapRetirementPermit permitBootstrapSeedRetirement(V0BootstrapRetirementCoordinator.DrainProof drain,
            P11CastCooldownService cooldowns, V0RuntimeCatalog.BootstrapRosterProof roster) {
        requireOwnerThread();
        if (drain == null || cooldowns == null || roster == null) { return null; }
        var catalog = drain.claim(this, V0BootstrapRetirementCoordinator.SEED_OWNER, cooldowns);
        if (catalog == null || !roster.authorizeSeed(this, catalog)) { return null; }
        return new BootstrapRetirementPermit(this, V0BootstrapRetirementCoordinator.SEED_OWNER, catalog);
    }

    public boolean isOwnerThread() {
        return Thread.currentThread().threadId() == ownerThreadId;
    }

    public boolean matchesCaptureLimits(int maxManifestEntries) {
        return this.maxManifestEntries == maxManifestEntries;
    }

    private void requireOwnerThread() {
        if (!isOwnerThread()) {
            throw new IllegalStateException("V0 catalog permit issued off its owning server thread");
        }
    }

    public static final class Reservation {
        private final V0CatalogAuthority authority;
        private final V0RuntimeCatalog.Candidate candidate;

        private Reservation(V0CatalogAuthority authority, V0RuntimeCatalog.Candidate candidate) {
            this.authority = authority;
            this.candidate = candidate;
        }

        public boolean matches(V0CatalogAuthority authority, V0RuntimeCatalog.Candidate candidate) {
            return this.authority == authority && this.candidate == candidate && authority.isOwnerThread();
        }
    }

    public static final class ReleasePermit {
        private final V0CatalogAuthority authority;
        private final V0RuntimeCatalog.Lease lease;

        private ReleasePermit(V0CatalogAuthority authority, V0RuntimeCatalog.Lease lease) {
            this.authority = authority;
            this.lease = lease;
        }

        public boolean matches(V0CatalogAuthority authority, V0RuntimeCatalog.Lease lease) {
            return this.authority == authority && this.lease == lease && authority.isOwnerThread();
        }
    }

    /** One cold cleanup operation; only the root's proven retirement coordinator can mint it. */
    public static final class BootstrapRetirementPermit {
        private final V0CatalogAuthority authority;
        private final SkillOwnerId owner;
        private final V0RuntimeCatalog catalog;
        private boolean consumed;

        private BootstrapRetirementPermit(V0CatalogAuthority authority, SkillOwnerId owner, V0RuntimeCatalog catalog) {
            this.authority = authority;
            this.owner = owner;
            this.catalog = catalog;
        }

        public synchronized boolean claim(V0CatalogAuthority exact, SkillOwnerId target, V0RuntimeCatalog source) {
            if (consumed || exact != authority || !owner.equals(target) || source != catalog
                    || exact.isOwnerThread()) { return false; }
            consumed = true;
            return true;
        }
    }
}
