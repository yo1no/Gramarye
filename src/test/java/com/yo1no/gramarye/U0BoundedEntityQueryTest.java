package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

/** Actual section container/scan tests; these are not an ordinary authenticated runtime claim. */
final class U0BoundedEntityQueryTest {
    @Test
    void boundedKeysKeepTheNativeAccessibleSectionOrderAcrossNegativeAndPackedAxes() {
        for (var query : List.of(new AABB(14.0, 15.0, 14.0, 15.0, 16.0, 15.0),
                new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5),
                new AABB(-16.5, -16.5, -16.5, -15.5, -15.5, -15.5))) {
            var keys = U0BoundedEntityQuery.sectionKeys(query);
            assertNotNull(keys);
            assertEquals(8, keys.length);
            var storage = storage(Visibility.TRACKED);
            var identities = new IdentityHashMap<EntitySection<TestAccess>, Long>();
            for (int index = 0; index < keys.length; index++) {
                var section = storage.getOrCreateSection(keys[index]);
                section.add(new TestAccess(index));
                identities.put(section, keys[index]);
            }
            // Native iteration also visits/filter-checks irrelevant Y/Z keys; selected order must agree.
            storage.getOrCreateSection(SectionPos.asLong(SectionPos.x(keys[0]), 64, 64))
                    .add(new TestAccess(99));
            var nativeOrder = new ArrayList<Long>();
            storage.forEachAccessibleNonEmptySection(query, section -> {
                nativeOrder.add(identities.get(section));
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
            assertArrayEquals(keys, nativeOrder.stream().mapToLong(Long::longValue).toArray());
        }
    }

    @Test
    void malformedWideAndPackingAliasCoordinatesRejectBeforeAnyLookup() {
        assertNull(U0BoundedEntityQuery.sectionKeys(null));
        assertNull(U0BoundedEntityQuery.sectionKeys(new AABB(Double.NaN, 0, 0, 1, 1, 1)));
        assertNull(U0BoundedEntityQuery.sectionKeys(new AABB(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1)));
        assertNull(U0BoundedEntityQuery.sectionKeys(new AABB(-20, 0, 0, 20, 1, 1)));
        assertNull(U0BoundedEntityQuery.sectionKeys(new AABB(0, 8_388_608, 0, 0.25, 8_388_608.25, 0.25)));
        assertNull(U0BoundedEntityQuery.sectionKeys(new AABB(33_554_432, 0, 0, 33_554_432.25, 0.25, 0.25)));
        assertNull(U0BoundedEntityQuery.sectionKeys(new AABB(Integer.MIN_VALUE, 0, 0, Integer.MIN_VALUE + 1.0, 1, 1)));
    }

    @Test
    void emptyQueryUsesOnlyItsAtMostEightExistingMapLookups() {
        var keys = U0BoundedEntityQuery.sectionKeys(new AABB(14, 15, 14, 15, 16, 15));
        var storage = new CountingStorage(Visibility.TRACKED);
        var result = U0BoundedEntityQuery.scan(storage, keys);
        assertTrue(result.complete);
        assertEquals(0, result.rawCount);
        assertEquals(8, result.sectionLookups);
        assertEquals(Arrays.stream(keys).boxed().toList(), storage.lookups);
        assertEquals(0, storage.count(), "getSection must never create a section");
        assertTrue(result.entries.isEmpty());
    }

    @Test
    void exactSixtyFourRawEntriesAreCompleteWithoutTouchingTheirBoundingBoxes() {
        var key = SectionPos.asLong(0, 0, 0);
        var storage = storage(Visibility.TRACKED);
        var expected = new ArrayList<EntityAccess>();
        for (int index = 0; index < 64; index++) {
            var access = new TestAccess(index);
            expected.add(access);
            storage.getOrCreateSection(key).add(access);
        }
        var result = U0BoundedEntityQuery.scan(storage, new long[]{key});
        assertTrue(result.complete);
        assertEquals(64, result.rawCount);
        assertEquals(1, result.sectionLookups);
        assertEquals(expected, result.entries);
        assertThrows(UnsupportedOperationException.class, () -> result.entries.clear());
    }

    @Test
    void sixtyFifthRawEntryDiscardsTheWholePrefixBeforeAnyCandidateFilter() {
        var key = SectionPos.asLong(0, 0, 0);
        var storage = storage(Visibility.TRACKED);
        for (int index = 0; index < 65; index++) {
            storage.getOrCreateSection(key).add(new TestAccess(index));
        }
        var result = U0BoundedEntityQuery.scan(storage, new long[]{key});
        assertFalse(result.complete);
        assertEquals(65, result.rawCount);
        assertEquals(1, result.sectionLookups);
        assertTrue(result.entries.isEmpty(), "no retained partial candidate set");
    }

    @Test
    void rawLimitIsAggregateAcrossSectionsAndHiddenSectionsRemainExcluded() {
        long first = SectionPos.asLong(0, 0, 0);
        long second = SectionPos.asLong(0, 1, 0);
        var storage = storage(Visibility.TRACKED);
        for (int index = 0; index < 32; index++) {
            storage.getOrCreateSection(first).add(new TestAccess(index));
        }
        for (int index = 0; index < 33; index++) {
            storage.getOrCreateSection(second).add(new TestAccess(100 + index));
        }
        var result = U0BoundedEntityQuery.scan(storage, new long[]{first, second});
        assertFalse(result.complete);
        assertEquals(65, result.rawCount);
        assertEquals(2, result.sectionLookups);
        storage.getSection(second).updateChunkStatus(Visibility.HIDDEN);
        result = U0BoundedEntityQuery.scan(storage, new long[]{first, second});
        assertTrue(result.complete);
        assertEquals(32, result.rawCount);
        assertEquals(2, result.sectionLookups);
    }

    @Test
    void duplicateUnorderedOrMoreThanEightKeysNeverStartNativeTraversal() {
        var storage = new CountingStorage(Visibility.TRACKED);
        for (long[] keys : List.of(new long[0], new long[9], new long[]{1, 1}, new long[]{2, 1})) {
            var result = U0BoundedEntityQuery.scan(storage, keys);
            assertFalse(result.complete);
            assertEquals(0, result.rawCount);
            assertEquals(0, result.sectionLookups);
        }
        assertTrue(storage.lookups.isEmpty());
    }

    @Test
    void inactiveNativeBridgeHasOnlyTheDelegateSentinelAndNoPublicMint() {
        assertNull(U0BoundedEntityQuery.scopedCandidates(null, null, null, null));
        U0BoundedEntityQuery.collect(null, null, null);
        assertNull(U0BoundedEntityQuery.scopedCandidates(null, null, null, null));
    }

    @Test
    void adoptedExactMixinsAndNormalFailureFencePreserveTheNativeOperation() throws Exception {
        var root = root();
        var main = root.resolve("src/main/java/com/yo1no/gramarye");
        var wrapper = Files.readString(main.resolve("mixin/U0ProjectileQueryMixin.java"));
        assertTrue(wrapper.contains(";Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;"));
        assertTrue(wrapper.contains("require = 1, expect = 1, allow = 1"));
        assertEquals(1, occurrences(wrapper, "original.call("));
        assertTrue(wrapper.contains("candidates == null ? original.call(level, carrier, query, predicate) : candidates"));
        var adapter = Files.readString(main.resolve("mixin/U0SectionQueryMixin.java"));
        assertTrue(adapter.contains("@Shadow @Final private EntitySectionStorage<?> sectionStorage;"));
        assertTrue(adapter.contains("implements U0BoundedQueryAccess"));
        assertTrue(adapter.contains("U0BoundedEntityQuery.collect(permit, this, sectionStorage)"));
        var access = Files.readString(main.resolve("U0BoundedQueryAccess.java"));
        assertTrue(access.startsWith("package com.yo1no.gramarye;"));
        assertEquals(1, occurrences(access, "void gramarye$u0Collect("));
        for (var call : List.of("U0BoundedEntityQuery.collect(", "U0BoundedEntityQuery.scopedCandidates(",
                "U0BoundedEntityQuery.query(", ".gramarye$u0Collect(")) {
            var callers = new ArrayList<String>();
            try (var paths = Files.walk(main)) {
                for (var path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    int count = occurrences(Files.readString(path), call);
                    for (int index = 0; index < count; index++) {
                        callers.add(main.relativize(path).toString());
                    }
                }
            }
            String expected = switch (call) {
                case "U0BoundedEntityQuery.collect(" -> "mixin/U0SectionQueryMixin.java";
                case "U0BoundedEntityQuery.scopedCandidates(" -> "mixin/U0ProjectileQueryMixin.java";
                case "U0BoundedEntityQuery.query(" -> "P9StarterProjectile.java";
                case ".gramarye$u0Collect(" -> "U0BoundedEntityQuery.java";
                default -> throw new AssertionError(call);
            };
            assertEquals(List.of(expected), callers, call + " must retain its sole production caller");
        }
        var shared = Files.readString(root.resolve("src/main/resources/gramarye.p11.mixins.json"));
        var engineering = Files.readString(root.resolve("src/v0Engineering/resources/gramarye.v0.engineering.mixins.json"));
        for (var name : List.of("P9V0LoadedPositionMixin", "U0SectionQueryMixin", "U0ProjectileQueryMixin")) {
            assertEquals(1, occurrences(shared, "\"" + name + "\""));
        }
        assertFalse(engineering.contains("P9V0LoadedPositionMixin"));
        assertFalse(Files.exists(root.resolve("src/v0Engineering/java/com/yo1no/gramarye/v0engineering/mixin/P9V0LoadedPositionMixin.java")));
        var projectile = Files.readString(main.resolve("P9StarterProjectile.java"));
        int query = projectile.indexOf("var result = U0BoundedEntityQuery.query(");
        int qualified = projectile.indexOf("if (!result.qualified)", query);
        int age = projectile.indexOf("v0Age++;", query);
        assertTrue(query > 0 && qualified > query && age > qualified);
        assertTrue(projectile.substring(qualified, age).contains("closeV0(V0TerminalReason.OWNER_INVALIDATED);\n                return;"));
    }

    private static EntitySectionStorage<TestAccess> storage(Visibility visibility) {
        return new EntitySectionStorage<>(TestAccess.class, ignored -> visibility);
    }

    private static int occurrences(String source, String fragment) {
        int count = 0;
        for (int at = source.indexOf(fragment); at >= 0; at = source.indexOf(fragment, at + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Path root() {
        for (var path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) {
                return path;
            }
        }
        throw new AssertionError("project root unavailable");
    }

    private static final class CountingStorage extends EntitySectionStorage<TestAccess> {
        private final List<Long> lookups = new ArrayList<>();

        private CountingStorage(Visibility visibility) {
            super(TestAccess.class, ignored -> visibility);
        }

        @Override
        public EntitySection<TestAccess> getSection(long key) {
            lookups.add(key);
            return super.getSection(key);
        }
    }

    /** No position/type predicate may run while the raw bounded prefix is being collected. */
    private static final class TestAccess implements EntityAccess {
        private final int id;

        private TestAccess(int id) { this.id = id; }

        @Override public int getId() { return id; }
        @Override public UUID getUUID() { return new UUID(0, id); }
        @Override public BlockPos blockPosition() { throw new AssertionError("raw position was filtered"); }
        @Override public AABB getBoundingBox() { throw new AssertionError("raw AABB was filtered"); }
        @Override public void setLevelCallback(EntityInLevelCallback ignored) { throw new AssertionError(); }
        @Override public Stream<? extends EntityAccess> getSelfAndPassengers() { throw new AssertionError(); }
        @Override public Stream<? extends EntityAccess> getPassengersAndSelf() { throw new AssertionError(); }
        @Override public void setRemoved(Entity.RemovalReason ignored) { throw new AssertionError(); }
        @Override public boolean shouldBeSaved() { throw new AssertionError(); }
        @Override public boolean isAlwaysTicking() { throw new AssertionError(); }
    }
}
