package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;

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

    Reservation reserve(V0RuntimeCatalog.Candidate candidate) {
        requireOwnerThread();
        return new Reservation(this, Objects.requireNonNull(candidate, "candidate"));
    }

    ReleasePermit permitRelease(V0RuntimeCatalog.Lease lease) {
        requireOwnerThread();
        return new ReleasePermit(this, Objects.requireNonNull(lease, "lease"));
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
}
