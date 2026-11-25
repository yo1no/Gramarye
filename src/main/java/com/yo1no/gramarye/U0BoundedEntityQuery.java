package com.yo1no.gramarye;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Call-scoped, bounded candidate source for the adopted ordinary projectile profile. */
public final class U0BoundedEntityQuery {
    private static final int MAX_RAW = 64;
    private static final int MAX_SECTIONS = 8;
    private static final ThreadLocal<Permit> CURRENT = new ThreadLocal<>();

    private U0BoundedEntityQuery() {}

    static Result query(ServerLevel level, P9StarterProjectile carrier, Vec3 start, Vec3 end,
            AABB query, Predicate<Entity> predicate) {
        var outer = CURRENT.get();
        if (outer != null) {
            fail(outer);
            return new Result(false, null);
        }
        if (level == null || carrier == null || predicate == null || !finite(start) || !finite(end)
                || !carrier.normalQueryCurrent(level, start, query)
                || start.distanceToSqr(end) > carrier.getDeltaMovement().lengthSqr() + 1.0E-9) {
            return new Result(false, null);
        }
        var keys = sectionKeys(query);
        var adapter = level.getEntities();
        if (keys == null || adapter == null || adapter.getClass() != LevelEntityGetterAdapter.class
                || !(adapter instanceof U0BoundedQueryAccess)) {
            return new Result(false, null);
        }
        var permit = new Permit(level, carrier, start, end, query, predicate, adapter, keys);
        CURRENT.set(permit);
        try {
            // Only the candidate source is replaced. Native clipping, margin and tie order remain native.
            var hit = ProjectileUtil.getEntityHitResult(level, carrier, start, end, query, predicate, 0.3F);
            boolean qualified = !permit.failed && permit.wrapperConsumed && permit.collectionCompleted
                    && current(permit) && carrier.normalQueryCurrent(level, start, query);
            return new Result(qualified, qualified ? hit : null);
        } finally {
            CURRENT.remove();
        }
    }

    /** The sole native adapter override hands over its actual shadow source for this one call. */
    public static void collect(Permit permit, Object adapter, EntitySectionStorage<?> storage) {
        var active = CURRENT.get();
        if (permit == null || active != permit || !current(permit)
                || permit.adapter != adapter || storage == null
                || storage.getClass() != EntitySectionStorage.class
                || !permit.wrapperConsumed || permit.collectionStarted) {
            fail(active);
            fail(permit);
            return;
        }
        permit.collectionStarted = true;
        var raw = scan(storage, permit.keys);
        if (!raw.complete) {
            fail(permit);
            return;
        }
        var candidates = new ArrayList<Entity>(raw.entries.size());
        for (var access : raw.entries) {
            if (!(access instanceof Entity entity)) {
                fail(permit);
                return;
            }
            if (entity.getBoundingBox().intersects(permit.query) && entity != permit.carrier
                    && permit.predicate.test(entity)) {
                candidates.add(entity);
            }
            if (permit.failed || !current(permit)) {
                fail(permit);
                return;
            }
        }
        permit.candidates = List.copyOf(candidates);
        permit.collectionCompleted = true;
    }

    /** Null means no normal call is active; every active-call failure returns a refused empty source. */
    public static List<Entity> scopedCandidates(Level level, Entity carrier, AABB query,
            Predicate<? super Entity> predicate) {
        var permit = CURRENT.get();
        if (permit == null) {
            return null;
        }
        if (!current(permit) || permit.wrapperConsumed || level != permit.level
                || carrier != permit.carrier || query != permit.query || predicate != permit.predicate) {
            fail(permit);
            return List.of();
        }
        permit.wrapperConsumed = true;
        ((U0BoundedQueryAccess) permit.adapter).gramarye$u0Collect(permit);
        if (permit.failed || !permit.collectionCompleted || !current(permit)) {
            fail(permit);
            return List.of();
        }
        return permit.candidates;
    }

    private static boolean current(Permit permit) {
        return !permit.failed && permit.thread == Thread.currentThread()
                && permit.level.getServer().isSameThread()
                && permit.level.getServer().getTickCount() == permit.nativeTick
                && permit.level.getEntities() == permit.adapter
                && permit.carrier.normalQueryCurrent(permit.level, permit.start, permit.query);
    }

    private static void fail(Permit permit) {
        if (permit != null) {
            permit.failed = true;
            permit.candidates = List.of();
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    /** Same native coordinates, bounded before packing, allocation or section lookup. */
    static long[] sectionKeys(AABB query) {
        if (query == null) {
            return null;
        }
        double[] lows = {query.minX - 2.0, query.minY - 4.0, query.minZ - 2.0};
        double[] highs = {query.maxX + 2.0, query.maxY, query.maxZ + 2.0};
        int[] min = new int[3];
        int[] max = new int[3];
        int count = 1;
        for (int axis = 0; axis < 3; axis++) {
            if (!Double.isFinite(lows[axis]) || !Double.isFinite(highs[axis])
                    || lows[axis] < Integer.MIN_VALUE || highs[axis] >= Integer.MAX_VALUE
                    || lows[axis] > highs[axis]) {
                return null;
            }
            min[axis] = SectionPos.posToSectionCoord(lows[axis]);
            max[axis] = SectionPos.posToSectionCoord(highs[axis]);
            long span = (long) max[axis] - min[axis] + 1L;
            if (span < 1L || span > 2L) {
                return null;
            }
            count *= (int) span;
        }
        if (count > MAX_SECTIONS) {
            return null;
        }
        var keys = new long[count];
        int next = 0;
        for (int x = min[0]; x <= max[0]; x++) {
            for (int y = min[1]; y <= max[1]; y++) {
                for (int z = min[2]; z <= max[2]; z++) {
                    long key = SectionPos.asLong(x, y, z);
                    if (SectionPos.x(key) != x || SectionPos.y(key) != y || SectionPos.z(key) != z) {
                        return null;
                    }
                    keys[next++] = key;
                }
            }
        }
        Arrays.sort(keys);
        return keys;
    }

    /** Enumerates the complete bounded raw prefix before any candidate predicate is evaluated. */
    static Scan scan(EntitySectionStorage<?> storage, long[] keys) {
        if (storage == null || keys == null || keys.length < 1 || keys.length > MAX_SECTIONS) {
            return new Scan(false, 0, 0, List.of());
        }
        for (int index = 1; index < keys.length; index++) {
            if (keys[index - 1] >= keys[index]) {
                return new Scan(false, 0, 0, List.of());
            }
        }
        var entries = new ArrayList<EntityAccess>(MAX_RAW);
        int raw = 0;
        int lookups = 0;
        for (long key : keys) {
            var section = storage.getSection(key);
            lookups++;
            if (section == null || section.isEmpty() || !section.getStatus().isAccessible()) {
                continue;
            }
            try (var stream = section.getEntities()) {
                var iterator = stream.iterator();
                while (iterator.hasNext()) {
                    var access = iterator.next();
                    if (++raw > MAX_RAW) {
                        return new Scan(false, raw, lookups, List.of());
                    }
                    entries.add(access);
                }
            }
        }
        return new Scan(true, raw, lookups, List.copyOf(entries));
    }

    static final class Result {
        final boolean qualified;
        final EntityHitResult hit;

        private Result(boolean qualified, EntityHitResult hit) {
            this.qualified = qualified;
            this.hit = hit;
        }
    }

    static final class Scan {
        final boolean complete;
        final int rawCount;
        final int sectionLookups;
        final List<EntityAccess> entries;

        private Scan(boolean complete, int rawCount, int sectionLookups, List<EntityAccess> entries) {
            this.complete = complete;
            this.rawCount = rawCount;
            this.sectionLookups = sectionLookups;
            this.entries = entries;
        }
    }

    public static final class Permit {
        private final ServerLevel level;
        private final P9StarterProjectile carrier;
        private final Vec3 start;
        private final Vec3 end;
        private final AABB query;
        private final Predicate<Entity> predicate;
        private final Object adapter;
        private final long[] keys;
        private final Thread thread;
        private final int nativeTick;
        private boolean wrapperConsumed;
        private boolean collectionStarted;
        private boolean collectionCompleted;
        private volatile boolean failed;
        private List<Entity> candidates = List.of();

        private Permit(ServerLevel level, P9StarterProjectile carrier, Vec3 start, Vec3 end,
                AABB query, Predicate<Entity> predicate, Object adapter, long[] keys) {
            this.level = level;
            this.carrier = carrier;
            this.start = start;
            this.end = end;
            this.query = query;
            this.predicate = predicate;
            this.adapter = adapter;
            this.keys = keys;
            thread = Thread.currentThread();
            nativeTick = level.getServer().getTickCount();
        }
    }
}
