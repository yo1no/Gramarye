package com.yo1no.gramarye;

/** Reservation-owning immutable client-main work prepared on the NETWORK thread. */
interface P8ClientDispatchTask extends Runnable {
    /**
     * Releases the reservation when enqueue did not take ownership.
     * Implementations must be idempotence-guarded and must not throw.
     */
    void releaseAfterFailedEnqueue();
}
