package com.yo1no.gramarye.magic.network;

@FunctionalInterface
interface P7ServerIntentDispatchPort {
    void dispatch(P7QueuedCastIntent intent);
}
