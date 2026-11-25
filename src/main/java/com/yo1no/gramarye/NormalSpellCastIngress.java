package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Root-injected normal operation, never a legacy slot sentinel or engineering entry. */
final class NormalSpellCastIngress implements P7ServerAuthorizationBoundary.NormalRootIngressPort {
    private final SkillRuntimeService runtime;

    NormalSpellCastIngress(SkillRuntimeService runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public P7ServerAuthorizationBoundary.NormalIngressDisposition authorizeAndAdmit(
            MinecraftServer server, ServerPlayer actor,
            P7ServerAuthorizationBoundary.NormalReceipt receipt) {
        return runtime.admitNormalSpell(server, actor, receipt);
    }
}
