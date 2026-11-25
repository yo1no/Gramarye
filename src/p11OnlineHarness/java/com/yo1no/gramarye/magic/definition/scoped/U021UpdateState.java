package com.yo1no.gramarye.magic.definition.scoped;

/** Qualification-only pure correlation. Synchronized sections contain RAM checks/stores only. */
final class U021UpdateState {
    enum Phase { READY, CAPTURED, ACCEPTED, UPDATING, FINISHED, UNKNOWN, CLOSED }
    record Capture(Object catalog, Object authority, Object owner, Object selection,
                   Object candidate, Object oldReference, long ticket) { }
    private Phase phase = Phase.READY;
    private Capture captured;
    synchronized boolean capture(Capture actual) {
        if (phase != Phase.READY || actual == null || actual.ticket() <= 0
                || actual.catalog() == null || actual.authority() == null || actual.owner() == null
                || actual.selection() == null || actual.candidate() == null || actual.oldReference() == null) { return false; }
        captured = actual; phase = Phase.CAPTURED; return true;
    }
    synchronized Capture pending() { return phase == Phase.CAPTURED ? captured : null; }
    synchronized boolean accepted(Object authority, long ticket, Object candidate) {
        if (phase != Phase.CAPTURED || !matches(authority, ticket, candidate)) { return false; }
        phase = Phase.ACCEPTED; return true;
    }
    synchronized void other(Object authority, long ticket, Object candidate) {
        if (phase == Phase.CAPTURED && matches(authority, ticket, candidate)) { captured = null; phase = Phase.UNKNOWN; }
    }
    synchronized Capture claim(Object catalog, Object authority, Object owner, Object selection, long nextTicket) {
        if (phase != Phase.ACCEPTED || captured.catalog() != catalog || captured.authority() != authority
                || !captured.owner().equals(owner) || captured.selection() != selection
                || nextTicket <= captured.ticket()) { return null; }
        phase = Phase.UPDATING; return captured;
    }
    synchronized void finish() { if (phase == Phase.UPDATING) { captured = null; phase = Phase.FINISHED; } }
    synchronized void unknown() { captured = null; if (phase != Phase.CLOSED) { phase = Phase.UNKNOWN; } }
    synchronized void close() { captured = null; phase = Phase.CLOSED; }
    synchronized Phase phase() { return phase; }
    private boolean matches(Object authority, long ticket, Object candidate) {
        return captured != null && captured.authority() == authority && captured.ticket() == ticket
                && captured.candidate() == candidate;
    }
}
