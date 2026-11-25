package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The actual query-owner source against small typed native doubles; never a Minecraft runtime claim. */
final class U0BoundedQueryScopeTest {
    @TempDir Path temporary;

    @Test
    void actualOwnerRejectsWrongSourceReuseAndReentryWithoutAnUnboundedFallback() throws Exception {
        var sources = new ArrayList<Path>();
        var main = root().resolve("src/main/java/com/yo1no/gramarye");
        write(sources, "com/yo1no/gramarye/U0BoundedEntityQuery.java",
                Files.readString(main.resolve("U0BoundedEntityQuery.java")));
        write(sources, "com/yo1no/gramarye/U0BoundedQueryAccess.java",
                Files.readString(main.resolve("U0BoundedQueryAccess.java")));
        var fixtures = new LinkedHashMap<String, String>();
        fixtures.put("net/minecraft/world/phys/Vec3.java", """
                package net.minecraft.world.phys;
                public final class Vec3 {
                  public final double x,y,z;
                  public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
                  public double lengthSqr(){return x*x+y*y+z*z;}
                  public double distanceToSqr(Vec3 b){double a=x-b.x,c=y-b.y,d=z-b.z;return a*a+c*c+d*d;}
                }
                """);
        fixtures.put("net/minecraft/world/phys/AABB.java", """
                package net.minecraft.world.phys;
                public final class AABB {
                  public final double minX,minY,minZ,maxX,maxY,maxZ;
                  public AABB(double a,double b,double c,double d,double e,double f){minX=a;minY=b;minZ=c;maxX=d;maxY=e;maxZ=f;}
                  public boolean intersects(AABB q){return minX<q.maxX&&maxX>q.minX&&minY<q.maxY&&maxY>q.minY&&minZ<q.maxZ&&maxZ>q.minZ;}
                }
                """);
        fixtures.put("net/minecraft/core/SectionPos.java", """
                package net.minecraft.core;
                public final class SectionPos {
                  public static int posToSectionCoord(double n){return ((int)Math.floor(n))>>4;}
                  public static long asLong(int x,int y,int z){return ((long)x&4194303L)<<42|((long)z&4194303L)<<20|((long)y&1048575L);}
                  public static int x(long n){return (int)(n>>42);}
                  public static int y(long n){return (int)(n<<44>>44);}
                  public static int z(long n){return (int)(n<<22>>42);}
                }
                """);
        fixtures.put("net/minecraft/world/level/entity/EntityAccess.java",
                "package net.minecraft.world.level.entity; public interface EntityAccess {}");
        fixtures.put("net/minecraft/world/entity/Entity.java", """
                package net.minecraft.world.entity;
                public class Entity implements net.minecraft.world.level.entity.EntityAccess {
                  public final net.minecraft.world.phys.AABB box=new net.minecraft.world.phys.AABB(0,0,0,.1,.1,.1);
                  public net.minecraft.world.phys.AABB getBoundingBox(){return box;}
                  public net.minecraft.world.phys.Vec3 getDeltaMovement(){return new net.minecraft.world.phys.Vec3(1,0,0);}
                }
                """);
        fixtures.put("net/minecraft/world/phys/EntityHitResult.java", """
                package net.minecraft.world.phys;
                public final class EntityHitResult { public final net.minecraft.world.entity.Entity entity;
                  public EntityHitResult(net.minecraft.world.entity.Entity e){entity=e;}
                }
                """);
        fixtures.put("net/minecraft/world/level/entity/EntitySectionStorage.java", """
                package net.minecraft.world.level.entity;
                public class EntitySectionStorage<T extends EntityAccess> {
                  public final java.util.Map<Long,Section<T>> sections=new java.util.HashMap<>();
                  public int lookups;
                  public Section<T> getSection(long key){lookups++;return sections.get(key);}
                  public static final class Section<T>{
                    public final java.util.List<T> entries=new java.util.ArrayList<>();
                    public boolean isEmpty(){return entries.isEmpty();}
                    public Status getStatus(){return new Status();}
                    public java.util.stream.Stream<T> getEntities(){return entries.stream();}
                  }
                  public static final class Status {public boolean isAccessible(){return true;}}
                }
                """);
        fixtures.put("net/minecraft/world/level/entity/LevelEntityGetterAdapter.java", """
                package net.minecraft.world.level.entity;
                import com.yo1no.gramarye.*;
                public class LevelEntityGetterAdapter implements U0BoundedQueryAccess {
                  public final EntitySectionStorage<net.minecraft.world.entity.Entity> storage=new EntitySectionStorage<>();
                  public java.util.function.Consumer<U0BoundedEntityQuery.Permit> hook;
                  public void gramarye$u0Collect(U0BoundedEntityQuery.Permit p){if(hook!=null)hook.accept(p);U0BoundedEntityQuery.collect(p,this,storage);}
                }
                """);
        fixtures.put("net/minecraft/world/level/Level.java", """
                package net.minecraft.world.level;
                public class Level {
                  public net.minecraft.world.level.entity.LevelEntityGetterAdapter adapter=new net.minecraft.world.level.entity.LevelEntityGetterAdapter();
                  public net.minecraft.world.level.entity.LevelEntityGetterAdapter getEntities(){return adapter;}
                }
                """);
        fixtures.put("net/minecraft/server/level/ServerLevel.java", """
                package net.minecraft.server.level;
                public final class ServerLevel extends net.minecraft.world.level.Level {
                  public final Server server=new Server();
                  public Server getServer(){return server;}
                  public static final class Server { public int tick=3;public boolean main=true;
                    public boolean isSameThread(){return main;} public int getTickCount(){return tick;}}
                }
                """);
        fixtures.put("com/yo1no/gramarye/P9StarterProjectile.java", """
                package com.yo1no.gramarye;
                final class P9StarterProjectile extends net.minecraft.world.entity.Entity {
                  final net.minecraft.server.level.ServerLevel level;
                  boolean valid=true;
                  P9StarterProjectile(net.minecraft.server.level.ServerLevel level){this.level=level;}
                  boolean normalQueryCurrent(net.minecraft.server.level.ServerLevel supplied,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.AABB query){return valid&&supplied==level&&level.server.main;}
                }
                """);
        fixtures.put("net/minecraft/world/entity/projectile/ProjectileUtil.java", """
                package net.minecraft.world.entity.projectile;
                import net.minecraft.world.entity.*; import net.minecraft.world.level.*; import net.minecraft.world.phys.*;
                public final class ProjectileUtil {
                  public static int nativeCalls,delegates; public static Runnable before;
                  public static EntityHitResult getEntityHitResult(Level level,Entity carrier,Vec3 start,Vec3 end,AABB query,java.util.function.Predicate<Entity> predicate,float margin){
                    nativeCalls++;if(before!=null)before.run();
                    var candidates=com.yo1no.gramarye.U0BoundedEntityQuery.scopedCandidates(level,carrier,query,predicate);
                    if(candidates==null){delegates++;return null;}
                    return candidates.isEmpty()?null:new EntityHitResult(candidates.getFirst());
                  }
                }
                """);
        fixtures.put("com/yo1no/gramarye/U0QueryFixture.java", FIXTURE);
        for (var row : fixtures.entrySet()) {
            write(sources, row.getKey(), row.getValue());
        }
        var classes = Files.createDirectory(temporary.resolve("classes"));
        var args = new ArrayList<>(List.of("--release", "21", "-proc:none", "-d", classes.toString()));
        sources.forEach(path -> args.add(path.toString()));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            try {
                loader.loadClass("com.yo1no.gramarye.U0QueryFixture").getMethod("run").invoke(null);
            } catch (java.lang.reflect.InvocationTargetException failure) {
                throw new AssertionError(failure.getCause());
            }
        }
    }

    private void write(List<Path> sources, String relative, String source) throws Exception {
        var path = temporary.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source);
        sources.add(path);
    }

    private static Path root() {
        for (var path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) {
                return path;
            }
        }
        throw new AssertionError("project root unavailable");
    }

    private static final String FIXTURE = """
            package com.yo1no.gramarye;
            import java.util.*; import java.util.function.*;
            import net.minecraft.server.level.ServerLevel;
            import net.minecraft.world.entity.Entity;
            import net.minecraft.world.entity.projectile.ProjectileUtil;
            import net.minecraft.world.level.entity.*;
            import net.minecraft.world.phys.*;
            public final class U0QueryFixture {
              static final Vec3 START=new Vec3(0,0,0),END=new Vec3(1,0,0);
              static final AABB QUERY=new AABB(0,0,0,.25,.25,.25);
              static final Predicate<Entity> TRUE=e->true;
              static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
              static U0BoundedEntityQuery.Result query(ServerLevel level,P9StarterProjectile carrier,Predicate<Entity> predicate){return U0BoundedEntityQuery.query(level,carrier,START,END,QUERY,predicate);}
              static ServerLevel level(int count){
                var level=new ServerLevel();var section=new EntitySectionStorage.Section<Entity>();
                for(int i=0;i<count;i++)section.entries.add(new Entity());
                level.adapter.storage.sections.put(0L,section);return level;
              }
              static void clear(){ProjectileUtil.before=null;check(U0BoundedEntityQuery.scopedCandidates(null,null,null,null)==null,"active permit escaped finally");}
              public static void run(){
                var l=level(64);var p=new P9StarterProjectile(l);int[] filtered={0};
                var good=query(l,p,e->{filtered[0]++;return true;});
                check(good.qualified&&good.hit!=null&&filtered[0]==64,"complete64 source");
                check(l.adapter.storage.lookups<=8&&ProjectileUtil.delegates==0,"normal unbounded fallback");clear();
                l=level(65);p=new P9StarterProjectile(l);filtered[0]=0;
                var over=query(l,p,e->{filtered[0]++;return true;});
                check(!over.qualified&&over.hit==null&&filtered[0]==0,"65 raw must fail before all predicates");clear();
                l=level(0);p=new P9StarterProjectile(l);check(query(l,p,TRUE).qualified,"valid empty is not refused");clear();
                l=level(1);p=new P9StarterProjectile(l);var wrong=l;
                ProjectileUtil.before=()->U0BoundedEntityQuery.scopedCandidates(wrong,new Entity(),QUERY,TRUE);
                check(!query(l,p,TRUE).qualified,"wrong tuple delegated or survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var recurseLevel=l;var recurseCarrier=p;
                ProjectileUtil.before=()->check(!query(recurseLevel,recurseCarrier,TRUE).qualified,"reentrant mint succeeded");
                check(!query(l,p,TRUE).qualified,"outer reentrant query survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var adapter=l.adapter;
                adapter.hook=permit->U0BoundedEntityQuery.collect(permit,new Object(),adapter.storage);
                check(!query(l,p,TRUE).qualified,"wrong adapter source survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var twice=l.adapter;
                twice.hook=permit->U0BoundedEntityQuery.collect(permit,twice,twice.storage);
                check(!query(l,p,TRUE).qualified,"second collection survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var stale=l;
                ProjectileUtil.before=()->stale.server.tick++;
                check(!query(l,p,TRUE).qualified,"native tick change survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var changed=l;
                ProjectileUtil.before=()->changed.adapter=new LevelEntityGetterAdapter();
                check(!query(l,p,TRUE).qualified,"adapter identity change survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var threaded=l.adapter;
                threaded.hook=permit->{var t=new Thread(()->U0BoundedEntityQuery.collect(permit,threaded,threaded.storage));t.start();try{t.join();}catch(InterruptedException x){throw new AssertionError(x);}};
                check(!query(l,p,TRUE).qualified,"wrong thread permit survived");clear();
                l=level(1);p=new P9StarterProjectile(l);var failure=new IllegalStateException("native primary");
                ProjectileUtil.before=()->{throw failure;};
                try{query(l,p,TRUE);throw new AssertionError("native exception disappeared");}catch(IllegalStateException actual){check(actual==failure,"native primary replaced");}clear();
                l=level(1);p=new P9StarterProjectile(l);var predicateFailure=new AssertionError("predicate primary");
                try{query(l,p,e->{throw predicateFailure;});throw new AssertionError("predicate exception disappeared");}catch(AssertionError actual){check(actual==predicateFailure,"predicate primary replaced");}clear();
                check(ProjectileUtil.delegates==0,"normal refusal delegated");
                ProjectileUtil.getEntityHitResult(l,new Entity(),START,END,QUERY,TRUE,.3F);
                check(ProjectileUtil.delegates==1,"inactive branch must delegate once");clear();
              }
            }
            """;
}
