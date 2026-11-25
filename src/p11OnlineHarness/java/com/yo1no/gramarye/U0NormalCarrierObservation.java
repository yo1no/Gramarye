package com.yo1no.gramarye;

/** Qualification-only scalar copy of the original terminal-call arguments; no getter or retained binding. */
public final class U0NormalCarrierObservation {
    private U0NormalCarrierObservation() { }
    public static void closed(Object binding, Object reason) {
        if (binding instanceof SkillRuntimeService.V0CarrierBinding actual && reason instanceof V0TerminalReason closed) {
            U0OrdinaryTrace.note("CARRIER_TERMINAL_RETURN", actual.lineage.value(), actual.id.value(), 0, 0, 0, 0,
                    actual.child ? 1 : 0, 0, 0, closed.name());
        } else { U0OrdinaryTrace.note("INVALID_CARRIER_TERMINAL", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
    }
    /** Read-only post-return snapshot. Unknown predicates remain unknown, never observed-false. */
    public static void actorCurrent(Object source, Object body, Object proof, Object actor, boolean result) {
        int typed = (source instanceof P11QualifiedSourceOwner ? 1 : 0)
                | (body instanceof P11QualifiedSourceOwner.Body ? 2 : 0)
                | (proof instanceof P11ReceiptLedger.Source ? 4 : 0)
                | (actor instanceof net.minecraft.server.level.ServerPlayer ? 8 : 0);
        if (!(source instanceof P11QualifiedSourceOwner owner)
                || !(body instanceof P11QualifiedSourceOwner.Body capturedBody)
                || !(proof instanceof P11ReceiptLedger.Source capturedProof)
                || !(actor instanceof net.minecraft.server.level.ServerPlayer exact)) {
            U0OrdinaryTrace.note("ACTOR_CURRENT_RETURN", -1, -1, -1, -1, 0, 0, result ? 1 : 0, typed, 0, "");
            return;
        }
        var server = exact.getServer();
        if (server == null || !server.isSameThread() || !owner.owns(server)) {
            U0OrdinaryTrace.note("ACTOR_CURRENT_RETURN", -1, -1, -1, -1, 0, 0, result ? 1 : 0, typed, 0, "");
            return;
        }
        var currentBody = owner.body(exact);
        long values = (currentBody == capturedBody ? 1 : 0)
                | (capturedBody.source == capturedProof ? 2 : 0)
                | (owner.qualifiedForV0Capture(capturedBody) ? 4 : 0)
                | (owner.canCopy(capturedBody) ? 8 : 0)
                | (exact.isAlive() ? 16 : 0) | (exact.isRemoved() ? 32 : 0);
        U0OrdinaryTrace.note("ACTOR_CURRENT_RETURN", capturedProof.epoch(), capturedProof.version(),
                currentBody == null || currentBody.source == null ? -1 : currentBody.source.epoch(),
                currentBody == null || currentBody.source == null ? -1 : currentBody.source.version(),
                63, values, result ? 1 : 0, typed, 1, "");
    }
}
