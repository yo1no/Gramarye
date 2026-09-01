package com.yo1no.gramarye.magic.runtime.mana;

import java.util.UUID;

/** Synchronous, call-scoped access to exactly one mana account. */
interface ManaAccountAccess {
    boolean isLogicThread();

    UUID accountId();

    ManaAvailability availability();

    long balance();

    void writeBalance(long balance);
}
