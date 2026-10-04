package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real cleanup implementation with small native-container doubles; not transformed game evidence. */
final class P11P9TrackingCleanupTest {
    @TempDir Path temporary;

    @Test void nativeTraversalScopesRetireClosedObjectsAndPreserveOriginalFailures() throws Exception {
        var sources = new ArrayList<Path>();
        var root = root();
        var projectile = Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/P9StarterProjectile.java"));
        String predicate = projectile.substring(projectile.indexOf("    boolean closedForTrackingRemoval()"),
                projectile.indexOf("    private boolean validServerState("));
        var runtime = Files.readString(root.resolve("src/main/java/com/yo1no/gramarye/SkillRuntimeService.java"));
        String detached = runtime.substring(runtime.indexOf("    private static void closeDetachedContinuation("),
                runtime.indexOf("    private RuntimeExecutionOutcome processCompletedPlan("));
        write(sources, "com/yo1no/gramarye/P11P9TrackingCleanup.java", Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/P11P9TrackingCleanup.java")));
        for (var entry : Map.of(
                "com/llamalad7/mixinextras/injector/wrapoperation/Operation.java",
                "package com.llamalad7.mixinextras.injector.wrapoperation; public interface Operation<T>{T call(Object...args);}",
                "net/minecraft/world/level/ChunkPos.java",
                "package net.minecraft.world.level; public record ChunkPos(long value){public long toLong(){return value;}}",
                "net/minecraft/world/level/entity/EntityAccess.java",
                "package net.minecraft.world.level.entity; public interface EntityAccess{}",
                "net/minecraft/world/level/entity/Visibility.java",
                "package net.minecraft.world.level.entity; public enum Visibility{HIDDEN,TRACKED,TICKING}",
                "net/minecraft/world/level/entity/EntitySectionStorage.java", """
                    package net.minecraft.world.level.entity;
                    public class EntitySectionStorage<T extends EntityAccess>{
                      public final java.util.List<Section<T>> sections=new java.util.ArrayList<>();
                      public RuntimeException collectionFailure;
                      public java.util.stream.Stream<Section<T>> getExistingSectionsInChunk(long ignored){
                        if(collectionFailure!=null)return java.util.stream.Stream.concat(sections.stream(),
                            java.util.stream.Stream.<Section<T>>generate(()->{throw collectionFailure;}).limit(1));
                        return sections.stream();
                      }
                      public static class Section<T>{
                        public final java.util.List<T> entities=new java.util.ArrayList<>();
                        public java.util.stream.Stream<T> getEntities(){return entities.stream();}
                      }
                    }
                    """).entrySet()) { write(sources, entry.getKey(), entry.getValue()); }
        write(sources, "com/yo1no/gramarye/P9StarterProjectile.java", """
                package com.yo1no.gramarye;
                import java.util.UUID;
                import net.minecraft.world.level.entity.EntityAccess;
                final class P9StarterProjectile implements EntityAccess {
                  final Server server=new Server(); Object world=new ServerLevel(server);
                  ServerPlayer authenticatedCasterIdentity=new ServerPlayer(server);
                  String dimension="overworld"; RuntimeProjectileContinuationPermit continuationPermit=new RuntimeProjectileContinuationPermit();
                  UUID id=continuationPermit.plannedProjectileId; boolean removed; int discards; Runnable removal=()->{};
                  Object level(){return world;} UUID getUUID(){return id;} boolean isRemoved(){return removed;}
                  boolean hasContinuationPermitIdentity(RuntimeProjectileContinuationPermit p){return continuationPermit==p;}
                  void discard(){removed=true;discards++;removal.run();}
                """ + predicate + """
                }
                final class Server {boolean sameThread=true; boolean isSameThread(){return sameThread;}}
                record ServerPlayer(Server server){Server getServer(){return server;}}
                record ServerLevel(Server server){Server getServer(){return server;} Dimension dimension(){return new Dimension("overworld");}}
                record Dimension(String location){}
                final class RuntimeProjectileContinuationPermit {
                  enum Mode{REAL,INERT} enum State{RESERVED,OPEN,CLAIMED_PENDING_DAMAGE,CLOSED_NO_HIT,CLOSED_AFTER_HIT}
                  Mode mode=Mode.REAL; State state=State.CLOSED_NO_HIT; UUID plannedProjectileId=UUID.randomUUID();
                  int closes; RuntimeException closeFailure; RuntimePermitCloseDisposition disposition=RuntimePermitCloseDisposition.CLOSED;
                  RuntimePermitCloseDisposition closeWithoutHit(MinecraftServer server,ProjectileClosureReason reason){closes++;if(closeFailure!=null)throw closeFailure;state=State.CLOSED_NO_HIT;return disposition;}
                }
                final class MinecraftServer{}
                record ChildReservation(RuntimeProjectileContinuationPermit detachedPermit){}
                enum ProjectileClosureReason{RELOAD_INVALIDATED}
                enum RuntimePermitCloseDisposition{CLOSED,ALREADY_CLOSED,REJECTED}
                final class RuntimeKernelException{enum Code{RESERVATION_ACCOUNTING_INVARIANT}}
                """);
        write(sources, "com/yo1no/gramarye/TrackingFixture.java", FIXTURE.replace("/*DETACHED*/", detached));
        Path classes = Files.createDirectory(temporary.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-proc:none", "-d", classes.toString()));
        sources.forEach(path -> args.add(path.toString()));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            try { loader.loadClass("com.yo1no.gramarye.TrackingFixture").getMethod("run").invoke(null); }
            catch (java.lang.reflect.InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
        }
    }

    @Test void sourceContractsBindOnlyTheExactClosedServerProjectileAndNativeFinally() throws Exception {
        var root = root().resolve("src/main/java/com/yo1no/gramarye");
        var projectile = Files.readString(root.resolve("P9StarterProjectile.java"));
        var predicate = projectile.substring(projectile.indexOf("boolean closedForTrackingRemoval()"),
                projectile.indexOf("private boolean validServerState("));
        assertPredicate(predicate);
        for (var clause : List.of("level() instanceof ServerLevel serverLevel", "serverLevel.getServer().isSameThread()",
                "authenticatedCasterIdentity != null", "authenticatedCasterIdentity.getServer() == serverLevel.getServer()",
                "serverLevel.dimension().location().equals(dimension)", "continuationPermit.mode == RuntimeProjectileContinuationPermit.Mode.REAL",
                "continuationPermit.plannedProjectileId.equals(getUUID())", "State.CLOSED_NO_HIT", "State.CLOSED_AFTER_HIT", "!isRemoved()")) {
            assertThrows(AssertionError.class, () -> assertPredicate(predicate.replace(clause, "REMOVED")), clause);
        }
        assertTrue(projectile.contains("private final transient ServerPlayer authenticatedCasterIdentity;"));
        String removed = projectile.substring(projectile.indexOf("public void onRemovedFromLevel()"),
                projectile.indexOf("boolean hasAuthenticatedCasterIdentity("));
        assertFalse(removed.contains("discard("), "never mutate the native section iterator from the callback");
        var chunk = Files.readString(root.resolve("mixin/P11P9EntitySectionMixin.java"));
        var move = Files.readString(root.resolve("mixin/P11P9EntityMoveMixin.java"));
        assertHooks(chunk, move);
        assertThrows(AssertionError.class, () -> assertHooks(chunk.replace("entity/Visibility;)V", "entity/FullChunkStatus;)V"), move));
        assertThrows(AssertionError.class, () -> assertHooks(chunk, move.replace("onMove()V", "updateStatus()V")));
        assertThrows(AssertionError.class, () -> assertHooks(chunk.replace("allow = 1", "allow = 2"), move));
        assertEquals(net.minecraft.world.level.entity.EntityAccess.class,
                Class.forName("net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback").getDeclaredField("entity").getType());
        assertEquals(net.minecraft.world.level.entity.EntitySectionStorage.class,
                net.minecraft.world.level.entity.PersistentEntitySectionManager.class.getDeclaredField("sectionStorage").getType());
        var mixins = Files.readString(root().resolve("src/main/resources/gramarye.p11.mixins.json"));
        assertTrue(mixins.contains("\"P11P9EntitySectionMixin\""));
        assertTrue(mixins.contains("\"P11P9EntityMoveMixin\""));
    }

    @Test void postTransferCancellationReloadAndRejectedPortCloseTheExactPhysicalCandidate() throws Exception {
        String runtime = Files.readString(root().resolve("src/main/java/com/yo1no/gramarye/SkillRuntimeService.java"));
        String detached = runtime.substring(runtime.indexOf("private static void closeDetachedContinuation("),
                runtime.indexOf("private RuntimeExecutionOutcome processCompletedPlan("));
        assertDetached(detached);
        for (var clause : List.of("var projectile = loadedProjectile(server, permit);",
                "!projectile.hasContinuationPermitIdentity(permit)", "projectile = null;",
                "RuntimePermitCloseDisposition.ALREADY_CLOSED", "P11P9TrackingCleanup.discardClosed(projectile);")) {
            String changed = detached.replace(clause, "REMOVED");
            assertNotEquals(detached, changed);
            assertThrows(AssertionError.class, () -> assertDetached(changed));
        }
        String port = runtime.substring(runtime.indexOf("private RuntimeExecutionOutcome finishPort("),
                runtime.indexOf("static RuntimeExecutionOutcome referenceFailureOutcome("));
        assertTrue(port.contains("instance.cancellationRequested") && port.contains("p9ReloadCloseRequested.get()")
                && port.contains("batch.outcome() instanceof RuntimePortOutcome.Rejected"));
        assertEquals(3, port.split("closeDetachedContinuation\\(", -1).length - 1);
    }

    private static void assertDetached(String value) {
        assertTrue(value.contains("var projectile = loadedProjectile(server, permit);"));
        assertTrue(value.contains("!projectile.hasContinuationPermitIdentity(permit)"));
        assertTrue(value.contains("projectile = null;"));
        assertTrue(value.contains("RuntimePermitCloseDisposition.ALREADY_CLOSED"));
        assertTrue(value.contains("P11P9TrackingCleanup.discardClosed(projectile);"));
        assertTrue(value.indexOf("loadedProjectile(server, permit)") < value.indexOf("permit.closeWithoutHit(server, reason)"));
        assertTrue(value.indexOf("permit.closeWithoutHit(server, reason)") < value.indexOf("P11P9TrackingCleanup.discardClosed(projectile)"));
        assertFalse(value.contains("catch (") || value.contains("new P9StarterProjectile") || value.contains("getOwner()"));
    }

    private static void assertPredicate(String value) {
        for (var clause : List.of("level() instanceof ServerLevel serverLevel", "serverLevel.getServer().isSameThread()",
                "authenticatedCasterIdentity != null", "authenticatedCasterIdentity.getServer() == serverLevel.getServer()",
                "serverLevel.dimension().location().equals(dimension)", "continuationPermit.mode == RuntimeProjectileContinuationPermit.Mode.REAL",
                "continuationPermit.plannedProjectileId.equals(getUUID())", "State.CLOSED_NO_HIT", "State.CLOSED_AFTER_HIT", "!isRemoved()")) {
            assertTrue(value.contains(clause), clause);
        }
        for (var forbidden : List.of("isRunning()", "isAlive()", "State.OPEN", "State.CLAIMED_PENDING_DAMAGE", "getOwner()", "getPlayerList()")) {
            assertFalse(value.contains(forbidden), forbidden);
        }
    }

    private static void assertHooks(String chunk, String move) {
        assertTrue(chunk.contains("updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V"));
        assertTrue(move.contains("PersistentEntitySectionManager$Callback"));
        assertTrue(move.contains("@WrapMethod(method = \"onMove()V\""));
        for (var hook : List.of(chunk, move)) {
            assertTrue(hook.contains("require = 1, expect = 1, allow = 1"));
            assertFalse(hook.contains("@Overwrite") || hook.contains("@Accessor") || hook.contains("@At(\"RETURN\")"));
        }
    }

    private void write(List<Path> paths, String name, String source) throws Exception {
        Path path = temporary.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        paths.add(path);
    }

    private static Path root() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle"))) { current = current.getParent(); }
        if (current == null) { throw new IllegalStateException("repository root unavailable"); }
        return current;
    }

    private static final String FIXTURE = """
            package com.yo1no.gramarye;
            import net.minecraft.world.level.ChunkPos;
            import net.minecraft.world.level.entity.*;
            import static com.yo1no.gramarye.RuntimeProjectileContinuationPermit.State.*;
            public final class TrackingFixture {
              static P9StarterProjectile loaded;
              static P9StarterProjectile loadedProjectile(MinecraftServer s,RuntimeProjectileContinuationPermit p){return loaded;}
              static RuntimeException kernel(RuntimeKernelException.Code code){return new IllegalStateException("kernel");}
              /*DETACHED*/
              static void check(boolean condition){if(!condition)throw new AssertionError();}
              static void scopeEmpty()throws Exception{var f=P11P9TrackingCleanup.class.getDeclaredField("CURRENT");f.setAccessible(true);check(((ThreadLocal<?>)f.get(null)).get()==null);}
              static EntitySectionStorage<EntityAccess> storage(){var s=new EntitySectionStorage<EntityAccess>();s.sections.add(new EntitySectionStorage.Section<>());return s;}
              static P9StarterProjectile add(EntitySectionStorage<EntityAccess>s){var p=new P9StarterProjectile();s.sections.get(0).entities.add(p);p.removal=()->s.sections.get(0).entities.remove(p);return p;}
              public static void run()throws Exception{
                var s=storage();var p=add(s);var active=add(s);active.continuationPermit.state=OPEN;var claimed=add(s);claimed.continuationPermit.state=CLAIMED_PENDING_DAMAGE;
                int[] calls={0};
                P11P9TrackingCleanup.chunkStatus(s,new ChunkPos(1),Visibility.HIDDEN,args->{
                  calls[0]++;
                  s.sections.get(0).entities.stream().forEach(e->{P11P9TrackingCleanup.moved(e,a->{check(!p.removed);return null;});check(!p.removed);});
                  // HIDDEN -> TRACKED before outer traversal returns; no entity tick needed.
                  P11P9TrackingCleanup.chunkStatus(s,new ChunkPos(1),Visibility.TRACKED,a->{check(!p.removed);return null;});
                  check(!p.removed);return null;
                });
                check(calls[0]==1&&p.discards==1&&!active.removed&&!claimed.removed&&s.sections.get(0).entities.size()==2);scopeEmpty();
                for(var state:new RuntimeProjectileContinuationPermit.State[]{RESERVED,OPEN,CLAIMED_PENDING_DAMAGE}){var x=new P9StarterProjectile();x.continuationPermit.state=state;P11P9TrackingCleanup.moved(x,a->null);check(!x.removed);}
                for(int n=0;n<7;n++){var x=new P9StarterProjectile();switch(n){case 0->x.world=new Object();case 1->x.server.sameThread=false;case 2->x.authenticatedCasterIdentity=null;case 3->x.authenticatedCasterIdentity=new ServerPlayer(new Server());case 4->x.dimension="other";case 5->x.continuationPermit.mode=RuntimeProjectileContinuationPermit.Mode.INERT;case 6->x.id=java.util.UUID.randomUUID();}P11P9TrackingCleanup.moved(x,a->null);check(!x.removed);}
                var afterHit=new P9StarterProjectile();afterHit.continuationPermit.state=CLOSED_AFTER_HIT;P11P9TrackingCleanup.moved(afterHit,a->null);check(afterHit.discards==1);scopeEmpty();
                var reentrant=new P9StarterProjectile();var nested=new P9StarterProjectile();reentrant.removal=()->{P11P9TrackingCleanup.moved(nested,a->null);P11P9TrackingCleanup.moved(nested,a->null);check(!nested.removed);};P11P9TrackingCleanup.moved(reentrant,a->null);check(reentrant.discards==1&&nested.discards==1);scopeEmpty();
                var primary=new AssertionError("primary");var secondary=new IllegalStateException("secondary");var failing=storage();var first=add(failing);var last=add(failing);first.removal=()->{throw secondary;};
                try{P11P9TrackingCleanup.chunkStatus(failing,new ChunkPos(1),Visibility.HIDDEN,a->{P11P9TrackingCleanup.moved(first,b->{throw primary;});return null;});throw new AssertionError();}catch(AssertionError seen){check(seen==primary);}check(first.discards==1&&last.discards==1);scopeEmpty();
                var collecting=storage();var captured=add(collecting);collecting.collectionFailure=secondary;try{P11P9TrackingCleanup.chunkStatus(collecting,new ChunkPos(1),Visibility.HIDDEN,a->null);throw new AssertionError();}catch(IllegalStateException seen){check(seen==secondary);}check(captured.discards==1);scopeEmpty();
                var overflow=storage();var objects=new java.util.ArrayList<P9StarterProjectile>();for(int n=0;n<129;n++)objects.add(add(overflow));try{P11P9TrackingCleanup.chunkStatus(overflow,new ChunkPos(1),Visibility.HIDDEN,a->null);throw new AssertionError();}catch(IllegalStateException expected){}check(objects.stream().filter(x->x.removed).count()==128);scopeEmpty();
                var overflowPrimary=storage();for(int n=0;n<129;n++)add(overflowPrimary);try{P11P9TrackingCleanup.chunkStatus(overflowPrimary,new ChunkPos(1),Visibility.HIDDEN,a->{throw primary;});throw new AssertionError();}catch(AssertionError seen){check(seen==primary);}scopeEmpty();
                var ordinary=new EntityAccess(){};P11P9TrackingCleanup.moved(ordinary,a->{try{scopeEmpty();}catch(Exception e){throw new AssertionError(e);}return null;});scopeEmpty();
                var server=new MinecraftServer();
                for(var disposition:new RuntimePermitCloseDisposition[]{RuntimePermitCloseDisposition.CLOSED,RuntimePermitCloseDisposition.ALREADY_CLOSED}){loaded=new P9StarterProjectile();loaded.continuationPermit.state=OPEN;loaded.continuationPermit.disposition=disposition;var permit=loaded.continuationPermit;closeDetachedContinuation(server,new ChildReservation(permit),ProjectileClosureReason.RELOAD_INVALIDATED);check(permit.closes==1&&loaded.discards==1);}
                loaded=new P9StarterProjectile();var wrongPermit=new RuntimeProjectileContinuationPermit();closeDetachedContinuation(server,new ChildReservation(wrongPermit),ProjectileClosureReason.RELOAD_INVALIDATED);check(wrongPermit.closes==1&&!loaded.removed);
                loaded=new P9StarterProjectile();var failedPermit=loaded.continuationPermit;failedPermit.closeFailure=secondary;try{closeDetachedContinuation(server,new ChildReservation(failedPermit),ProjectileClosureReason.RELOAD_INVALIDATED);throw new AssertionError();}catch(IllegalStateException seen){check(seen==secondary);}check(!loaded.removed&&failedPermit.closes==1);
                loaded=new P9StarterProjectile();loaded.continuationPermit.state=OPEN;var deferred=loaded;var nativeSection=storage();nativeSection.sections.get(0).entities.add(deferred);P11P9TrackingCleanup.chunkStatus(nativeSection,new ChunkPos(1),Visibility.HIDDEN,a->{closeDetachedContinuation(server,new ChildReservation(deferred.continuationPermit),ProjectileClosureReason.RELOAD_INVALIDATED);check(!deferred.removed);return null;});check(deferred.discards==1);scopeEmpty();
              }
            }
            """;
}
