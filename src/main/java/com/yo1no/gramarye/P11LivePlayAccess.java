package com.yo1no.gramarye;

import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;

/** Named native entry; the wrapper still requires the root's exact active one-use ticket. */
public interface P11LivePlayAccess {
    void p11$performRespawn(ServerboundClientCommandPacket packet);
    void p11$switchToConfig();

    /** Read-only disqualification of this exact old listener; never an admission capability. */
    boolean p11$configurationActorRetired();
}
