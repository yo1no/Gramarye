package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import com.google.gson.GsonBuilder;
import com.yo1no.gramarye.V0CatalogAuthority;
import com.yo1no.gramarye.U0OrdinaryTrace;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Excluded qualification controller. No actor/server/level/transport is captured or stored. */
public final class U021FutureCatalogUpdate {
    private static final AtomicReference<Session> CURRENT = new AtomicReference<>();
    private U021FutureCatalogUpdate() { }
    private record Session(V0RuntimeCatalog catalog, V0CatalogAuthority authority, SkillOwnerId owner,
            V0RuntimeCatalog.Selection selection, SpellReference expectedOld, long expectedGeneration,
            String expectedDocumentHash, Path output, String runId, U021UpdateState state,
            AtomicReference<Published> published, AtomicReference<NewCapture> newCapture, AtomicBoolean newAccepted, AtomicBoolean bootstrapBlocked) { }
    private record Published(SpellReference exact, long ticket) { }
    private record NewCapture(V0RuntimeCatalog.Candidate candidate, Published publication, long ticket) { }

    public static void register(V0RuntimeCatalog catalog, V0CatalogAuthority authority, SkillOwnerId owner,
            V0RuntimeCatalog.Selection selection, long stableId, long revision, long generation,
            String documentHash, Path output, String runId) {
        if (catalog == null || authority == null || !authority.isOwnerThread() || owner == null || selection == null
                || stableId <= 0 || revision <= 0 || generation <= 0 || !documentHash.matches("[0-9a-f]{64}")
                || output == null || !output.isAbsolute() || !runId.matches("[a-zA-Z0-9_-]{8,64}")) {
            throw new IllegalStateException("U021_EXACT_PREPARED_REGISTRATION");
        }
        var exact = new SpellReference(new SpellIdentity(owner, new SpellDefinitionId(stableId)), new SpellRevision(revision));
        var session = new Session(catalog, authority, owner, selection, exact, generation, documentHash,
                output.normalize(), runId, new U021UpdateState(), new AtomicReference<>(), new AtomicReference<>(), new AtomicBoolean(), new AtomicBoolean());
        if (!CURRENT.compareAndSet(null, session)) { throw new IllegalStateException("U021_PREVIOUS_FIXTURE_NOT_CLEARED"); }
    }

    /** Observation only. Errors cannot replace the original prepare result or arm a successful case. */
    public static void observeOriginalPrepare(V0RuntimeCatalog catalog, V0CatalogAuthority authority,
            long ticket, SkillOwnerId owner, V0RuntimeCatalog.Selection selection, V0RuntimeCatalog.PrepareResult actual) {
        var s = CURRENT.get();
        if (!exact(s, catalog, authority, owner, selection)) { return; }
        if (s.state().phase() == U021UpdateState.Phase.FINISHED) {
            observeUpdatedCandidate(s, ticket, actual); return;
        }
        if (s.state().phase() != U021UpdateState.Phase.READY) { return; }
        try {
            if (!(actual instanceof V0RuntimeCatalog.Candidate candidate)) { s.state().unknown(); return; }
            var image = catalog.engineeringReadback(authority, owner);
            var body = image == null ? null : image.revisions().get(s.expectedOld());
            if (authority.isOwnerThread() || image == null || image.generation().value() != s.expectedGeneration()
                    || !s.expectedOld().equals(image.routes().get(s.expectedOld().identity()))
                    || !(body instanceof SpellDefinition spell) || spell.provenance().isPresent()
                    || !spell.body().equals(damageBody(3)) || !V0NormalBootstrap.digest(spell).equals(s.expectedDocumentHash())
                    || candidate.lease().root().hitDamage().orElseThrow().damageHp() != 3) {
                s.state().unknown(); return;
            }
            if (!s.bootstrapBlocked().get()) { s.state().unknown(); return; }
            if (s.state().capture(new U021UpdateState.Capture(catalog, authority, owner, selection,
                    candidate, s.expectedOld(), ticket))) {
                U0OrdinaryTrace.note("FUTURE_BOOTSTRAP_BLOCKED",ticket,0,0,0,0,0,1,0,0,"");
            }
        } catch (IOException | RuntimeException | Error observationFailure) { s.state().unknown(); }
    }

    /** Called only by the exact root-package bridge after the original ACCEPTED enum return. RAM only. */
    public static void afterActualAccepted(V0CatalogAuthority authority, long ticket, V0RuntimeCatalog.Candidate candidate) {
        var s = CURRENT.get(); if (s == null || s.authority() != authority) { return; }
        try {
            var pending = s.state().pending();
            if (pending != null && pending.authority() == authority && pending.ticket() == ticket
                    && pending.candidate() == candidate && authority.isOwnerThread()
                    && s.catalog().usable(authority, candidate.lease())) {
                if (s.state().accepted(authority, ticket, candidate)) {
                    U0OrdinaryTrace.note("FUTURE_OLD_ACCEPTED",ticket,s.expectedOld().identity().id().value(),s.expectedOld().revision().value(),0,0,0,3,0,0,"");
                }
            } else {
                var next = s.newCapture().get();
                if (next != null && next.candidate() == candidate && next.ticket() == ticket
                        && authority.isOwnerThread() && s.catalog().usable(authority, candidate.lease())
                        && s.newCapture().compareAndSet(next, null)) {
                    s.newAccepted().set(true);
                    U0OrdinaryTrace.note("FUTURE_NEW_ACCEPTED",ticket,next.publication().exact().identity().id().value(),next.publication().exact().revision().value(),0,0,0,4,0,0,"");
                }
            }
        } catch (RuntimeException | Error observationFailure) { s.state().unknown(); }
    }

    public static void afterActualOther(V0CatalogAuthority authority, long ticket, V0RuntimeCatalog.Candidate candidate) {
        var s = CURRENT.get(); if (s != null && s.authority() == authority) {
            s.state().other(authority, ticket, candidate);
            var next = s.newCapture().get();
            if (next != null && next.candidate() == candidate && next.ticket() == ticket) { s.newCapture().compareAndSet(next, null); }
        }
    }

    /** The mutation is deliberately outside cosmetic-observer shielding, on the already owned cold call. */
    public static void beforeOriginalPrepare(V0RuntimeCatalog catalog, V0CatalogAuthority authority,
            long ticket, SkillOwnerId owner, V0RuntimeCatalog.Selection selection) {
        var s = CURRENT.get(); if (!exact(s, catalog, authority, owner, selection)) { return; }
        if (!s.bootstrapBlocked().get()) {
            require(!authority.isOwnerThread(), "U027_BOOTSTRAP_PROBE_ORIGINAL_COLD_ONLY");
            // The factory already sealed this exact catalog on its main owner. This negative
            // uses the actual API, then the original generic prepare must still succeed.
            // It is a control operation outside observer shielding, not a supplied proof.
            var blocked = catalog.ensureNormalBootstrap(authority, owner);
            require(!blocked.ready() && blocked.selection().isEmpty()
                    && blocked.refusal().orElse(null) == V0RuntimeCatalog.Refusal.SLOT_STALE,
                    "U027_ACTUAL_BOOTSTRAP_BARRIER_NOT_PROVED");
            s.bootstrapBlocked().set(true);
        }
        var old = s.state().claim(catalog, authority, owner, selection, ticket); if (old == null) { return; }
        try {
            require(!authority.isOwnerThread(), "U021_ORIGINAL_COLD_ONLY");
            var before = catalog.engineeringReadback(authority, owner);
            require(before != null && s.expectedOld().equals(before.routes().get(s.expectedOld().identity()))
                    && before.generation().value() == s.expectedGeneration(), "U021_OLD_CURRENT_CHANGED");
            var document = before.revisions().get(s.expectedOld());
            require(document instanceof SpellDefinition, "U021_OLD_BODY_MISSING");
            var spell = (SpellDefinition) document;
            require(spell.provenance().isEmpty() && spell.body().equals(damageBody(3))
                    && V0NormalBootstrap.digest(spell).equals(s.expectedDocumentHash()), "U021_PREPARED_BODY_CHANGED");
            require(catalog.publishFixture(authority, owner, new Revision(spell.reference())), "U021_REAL_PUBLICATION_REFUSED");
            var after = catalog.engineeringReadback(authority, owner);
            require(after != null, "U021_NEW_READBACK_MISSING");
            var next = after.routes().get(spell.identity());
            require(next instanceof SpellReference && !next.equals(spell.reference())
                    && ((SpellReference)next).revision().value() > spell.reference().revision().value()
                    && after.revisions().get(next) instanceof SpellDefinition changed
                    && changed.provenance().isEmpty() && changed.body().equals(damageBody(4)), "U021_NEW_EXACT_BODY_UNPROVED");
            require(preserved(before, after, spell.reference().identity()), "U021_FOREIGN_CONTENT_CHANGED");
            require(catalog.sweep(authority, owner), "U021_ACTUAL_PIN_SWEEP_REFUSED");
            var swept = catalog.engineeringReadback(authority, owner);
            require(swept != null && next.equals(swept.routes().get(spell.identity()))
                    && swept.revisions().containsKey(next) && preserved(before, swept, spell.reference().identity()), "U021_SWEPT_READBACK_UNPROVED");
            boolean oldPin = swept.revisions().containsKey(spell.reference());
            var result = new LinkedHashMap<String,Object>();
            result.put("format", "U0_21_27_COLD_FUTURE_PUBLICATION_CONTROL_V1");
            result.put("status", oldPin ? "ACTUAL_ACCEPTED_OLD_PIN_RETAINED_AFTER_UPDATE" : "MISSED_OLD_PIN_WINDOW_NOT_PROVED");
            result.put("runId", s.runId()); result.put("ownerLabel", hash((s.runId()+"\n"+owner.value()).getBytes(StandardCharsets.UTF_8)));
            result.put("oldCaptureTicket", old.ticket()); result.put("updateRequestTicket", ticket);
            result.put("oldReference", reference(spell.reference())); result.put("newReference", reference((SpellReference)next));
            result.put("oldDamageHP", 3); result.put("newDamageHP", 4); result.put("oldAcceptedPinAfterActualSweep", oldPin);
            result.put("otherCurrentBodiesAndExternalRootsPreserved", true); result.put("actualBeforeGeneration", before.generation().value());
            result.put("actualAfterSweepGeneration", swept.generation().value()); result.put("retainedRevisionCount", swept.revisions().size());
            result.put("newFormalDocumentSHA256", V0NormalBootstrap.digest((SpellDefinition)swept.revisions().get(next)));
            result.put("actualBootstrapProbeRefusedBeforeOriginalPrepare", s.bootstrapBlocked().get());
            result.put("nativeOverlapOrRowPASSClaim", false); result.put("noLeaseHoldOrForcedCooldownOrRetry", true);
            // A new exclusive result; no raw UUID, live object or exception text is exported.
            Files.writeString(s.output(), new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n", StandardOpenOption.CREATE_NEW);
            s.published().set(new Published((SpellReference)next, ticket));
            s.state().finish();
        } catch (IOException ioFailure) {
            s.state().unknown(); throw new UncheckedIOException("U021_CONTROL_IO_FAILED", ioFailure);
        } catch (RuntimeException | Error primary) { s.state().unknown(); throw primary; }
    }

    /** Closed scalar readiness label only; no owner UUID, catalog or runtime capability escapes. */
    public static String targetOwnerLabel() {
        var s = CURRENT.get();
        return s == null ? "UNAVAILABLE" : hash((s.runId()+"\n"+s.owner().value()).getBytes(StandardCharsets.UTF_8));
    }

    public static void stopped(V0CatalogAuthority authority) {
        var s = CURRENT.get();
        if (s != null && s.authority() == authority && authority.isOwnerThread() && CURRENT.compareAndSet(s, null)) {
            s.state().close(); s.newCapture().set(null); s.published().set(null);
        }
    }
    private static void observeUpdatedCandidate(Session s, long ticket, V0RuntimeCatalog.PrepareResult actual) {
        var publication = s.published().get();
        if (publication == null || ticket < publication.ticket() || s.newAccepted().get()) { return; }
        try {
            if (!(actual instanceof V0RuntimeCatalog.Candidate candidate)
                    || candidate.lease().root().hitDamage().orElseThrow().damageHp() != 4) {
                s.state().unknown(); return;
            }
            var image = s.catalog().engineeringReadback(s.authority(), s.owner());
            if (image == null || !publication.exact().equals(image.routes().get(publication.exact().identity()))
                    || !(image.revisions().get(publication.exact()) instanceof SpellDefinition spell)
                    || !spell.body().equals(damageBody(4)) || spell.provenance().isPresent()) {
                s.state().unknown(); return;
            }
            U0OrdinaryTrace.note("FUTURE_NEW_CANDIDATE",ticket,publication.exact().identity().id().value(),publication.exact().revision().value(),image.generation().value(),0,0,4,0,0,"");
            s.newCapture().compareAndSet(null, new NewCapture(candidate, publication, ticket));
        } catch (IOException | RuntimeException | Error observationFailure) { s.state().unknown(); }
    }
    private static boolean exact(Session s, V0RuntimeCatalog c, V0CatalogAuthority a, SkillOwnerId o, V0RuntimeCatalog.Selection selection) {
        return s != null && s.catalog() == c && s.authority() == a && s.owner().equals(o) && s.selection() == selection;
    }
    private static boolean preserved(ScopeDCatalogImage before, ScopeDCatalogImage after, SpellIdentity changed) {
        if (!before.routes().keySet().equals(after.routes().keySet()) || !before.externalRoots().equals(after.externalRoots())) { return false; }
        for (var row : before.currentBodies().entrySet()) {
            if (!row.getKey().equals(changed) && !row.getValue().equals(after.currentBodies().get(row.getKey()))) { return false; }
        }
        return true;
    }
    private static final class Revision extends V0RuntimeCatalog.PublicationPlan {
        private final SpellReference old;
        private Revision(SpellReference old) { this.old = old; }
        @Override List<FormalDocument> material(ScopeDCatalog.Reservation reservation) {
            return List.of(new SpellDefinition(reservation.nextSpell(old.identity()), damageBody(4), Optional.empty()));
        }
    }
    private static SpellBody damageBody(int hp) {
        var body = V0NormalBootstrap.body();
        var effect = new EffectBlock(new BlockId(1), new Descriptor("gramarye:v0_hit_damage", 1,
                List.of(new Argument("damage_hp", new IntegerValue(hp)))));
        return new SpellBody(body.backend(), body.activation(), body.settings(),
                new Known<>(List.of(new Handler(new HandlerId(1), "gramarye:on_create", 1, List.of()),
                        new Handler(new HandlerId(2), "gramarye:on_hit", 1, List.of(effect)))), body.appearance(), body.phenomenon());
    }
    private static Map<String,Object> reference(SpellReference r) { return Map.of("stableId",r.identity().id().value(),"revision",r.revision().value()); }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void require(boolean exact, String code) { if (!exact) { throw new IllegalStateException(code); } }
}
