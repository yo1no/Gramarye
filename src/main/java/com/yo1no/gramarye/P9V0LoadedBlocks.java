package com.yo1no.gramarye;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Call-scoped witness to the same completed FULL chunks used by one V0 sweep. */
final class P9V0LoadedBlocks implements BlockGetter {
    private final ServerLevel level;
    private final LevelChunk[] chunks;

    private P9V0LoadedBlocks(ServerLevel level, LevelChunk[] chunks) {
        this.level = level;
        this.chunks = chunks;
    }

    static Optional<P9V0LoadedBlocks> capture(ServerLevel level, AABB segmentEnvelope) {
        if (!level.getServer().isSameThread() || !finiteEnvelope(segmentEnvelope)) {
            return Optional.empty();
        }
        int minX = Mth.floor(segmentEnvelope.minX) >> 4;
        int maxX = Mth.floor(segmentEnvelope.maxX) >> 4;
        int minZ = Mth.floor(segmentEnvelope.minZ) >> 4;
        int maxZ = Mth.floor(segmentEnvelope.maxZ) >> 4;
        if (maxX - minX > 1 || maxZ - minZ > 1) {
            return Optional.empty();
        }
        var witnesses = new LevelChunk[(maxX - minX + 1) * (maxZ - minZ + 1)];
        int index = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                var chunk = completedChunk(level, x, z);
                if (chunk == null) {
                    return Optional.empty();
                }
                witnesses[index++] = chunk;
            }
        }
        return Optional.of(new P9V0LoadedBlocks(level, witnesses));
    }

    static LevelChunk completedChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (!level.getServer().isSameThread()) {
            return null;
        }
        var source = level.getChunkSource();
        var holder = source.chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (holder == null || !(holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk chunk)
                || chunk.getLevel() != level || chunk.getPos().x != chunkX || chunk.getPos().z != chunkZ
                || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                || !source.hasChunk(chunkX, chunkZ) || source.getChunkNow(chunkX, chunkZ) != chunk) {
            return null;
        }
        return chunk;
    }

    static boolean dryCell(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || level.isOutsideBuildHeight(pos)
                || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        var chunk = completedChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && dryCell(chunk, pos);
    }

    boolean dryPath(Vec3 from, Vec3 to) {
        // The native DDA visits at most seven cells on this bounded segment.
        return BlockGetter.traverseBlocks(from, to, this,
                (blocks, pos) -> level.isInWorldBounds(pos) && !level.isOutsideBuildHeight(pos)
                        && level.getWorldBorder().isWithinBounds(pos)
                        && dryCell(blocks.witness(pos), pos) ? null : Boolean.FALSE,
                blocks -> Boolean.TRUE);
    }

    private static boolean dryCell(LevelChunk chunk, BlockPos pos) {
        var state = chunk.getBlockState(pos);
        return (state.isAir() || state.is(Blocks.STONE)) && chunk.getFluidState(pos).isEmpty();
    }

    private LevelChunk witness(BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            throw new IllegalArgumentException("V0 terrain read outside the proved build height");
        }
        for (var chunk : chunks) {
            if (chunk.getPos().x == pos.getX() >> 4 && chunk.getPos().z == pos.getZ() >> 4) {
                return chunk;
            }
        }
        throw new IllegalArgumentException("V0 terrain read outside the proved sweep");
    }

    private static boolean finiteEnvelope(AABB envelope) {
        return Double.isFinite(envelope.minX) && Double.isFinite(envelope.maxX)
                && Double.isFinite(envelope.minY) && Double.isFinite(envelope.maxY)
                && Double.isFinite(envelope.minZ) && Double.isFinite(envelope.maxZ)
                && envelope.minX >= -30_000_000.0 && envelope.maxX < 30_000_000.0
                && envelope.minZ >= -30_000_000.0 && envelope.maxZ < 30_000_000.0;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        throw new IllegalStateException("V0 corridor does not allow block entity reads");
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return witness(pos).getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return witness(pos).getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return level.getMinBuildHeight();
    }
}
