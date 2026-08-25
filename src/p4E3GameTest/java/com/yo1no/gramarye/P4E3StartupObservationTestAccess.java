package com.yo1no.gramarye;

import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Test-only access to the bounded P4-E3 startup-observation cell. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class P4E3StartupObservationTestAccess {
    private P4E3StartupObservationTestAccess() {
    }

    /** Arms the exact existing facade before the production ServerStarting listener runs. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        retrieveExactFacade().armE3Startup(event.getServer());
    }

    /** Claims and immediately consumes one bounded observation from the exact facade. */
    public static Observation consume(MinecraftServer exactServer) {
        var facade = retrieveExactFacade();
        var session = facade.claimE3Startup(exactServer);
        var snapshot = facade.consumeE3Startup(exactServer, session);
        return new Observation(
                snapshot.sessionToken(),
                snapshot.auditInvocations(),
                snapshot.auditVariant(),
                snapshot.auditGeneration(),
                snapshot.completeConsumeInvocations(),
                snapshot.snapshotInvocations(),
                snapshot.snapshotVariant(),
                snapshot.completeRootCount(),
                snapshot.reclaimInvocations(),
                snapshot.reclaimVariant(),
                snapshot.historiesScanned(),
                snapshot.revisionsScanned(),
                snapshot.historiesChanged(),
                snapshot.revisionsReclaimed(),
                snapshot.dirtyBefore(),
                snapshot.dirtyAfter(),
                snapshot.indexTerminalObservations(),
                snapshot.indexTerminal(),
                snapshot.indexGeneration());
    }

    private static P4E2QualificationFacade retrieveExactFacade() {
        var first = retrieveFacade();
        var second = retrieveFacade();
        if (first != second) {
            throw new AssertionError("per-container qualification facade identity changed");
        }
        return first;
    }

    private static P4E2QualificationFacade retrieveFacade() {
        var container = ModList.get()
                .getModContainerById(Gramarye.MOD_ID)
                .orElseThrow(() -> new AssertionError("Gramarye mod container is absent"));
        return container.getCustomExtension(P4E2QualificationFacade.class)
                .orElseThrow(() -> new AssertionError(
                        "Gramarye qualification facade is absent"));
    }

    /** Runtime-reference-free copy for the store-package custom GameTest. */
    public record Observation(
            long sessionToken,
            int auditInvocations,
            P4E2QualificationFacade.E3AuditVariant auditVariant,
            long auditGeneration,
            int completeConsumeInvocations,
            int snapshotInvocations,
            P4E2QualificationFacade.E3SnapshotVariant snapshotVariant,
            int completeRootCount,
            int reclaimInvocations,
            P4E2QualificationFacade.E3ReclaimVariant reclaimVariant,
            int historiesScanned,
            int revisionsScanned,
            int historiesChanged,
            int revisionsReclaimed,
            boolean dirtyBefore,
            boolean dirtyAfter,
            int indexTerminalObservations,
            P4E2QualificationFacade.E3IndexTerminal indexTerminal,
            long indexGeneration) {
    }
}
