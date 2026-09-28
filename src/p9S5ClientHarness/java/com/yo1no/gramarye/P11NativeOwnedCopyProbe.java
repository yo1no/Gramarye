package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.definition.player.P11OwnedCopySkillObservation;
import com.yo1no.gramarye.magic.runtime.mana.P11OwnedCopyManaObservation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Excluded owned stopped-input fixture, followed only by authenticated native read/copy/writers. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeOwnedCopyProbe {
    private static final String SKILLS = "gramarye:player_skills";
    private static final String MANA = "gramarye:player_mana";
    private static final long READ_QUOTA = 32L * 1024 * 1024;
    private static Prepared prepared;
    private static Observation active;
    private static boolean used;

    private P11NativeOwnedCopyProbe() {}

    static boolean selected() {
        String value = System.getProperty("gramarye.p11.sourceWriter.case", "");
        return value.equals("owned-copy-raw") || value.equals("owned-copy-marker");
    }

    record Report(boolean passed, String observation, String failure) {
        @Override public String toString() {
            return "P11-NATIVE-OWNED-COPY-V1\n" + observation + "failure=" + failure
                    + "\nRESULT=" + (passed ? "PASS" : "FAIL") + '\n';
        }
    }

    static String prepareStopped(MinecraftServer stopped, Path world, UUID playerId, Path output) {
        try {
            require(selected() && System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) != null
                            && prepared == null && !used && stopped != null && stopped.isShutdown(),
                    "owned-copy fixture requires selected once-only fully stopped server");
            require(world.isAbsolute() && world.equals(world.normalize())
                            && world.equals(stopped.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize())
                            && Files.isDirectory(world, LinkOption.NOFOLLOW_LINKS), "wrong owned stopped world");
            freshDirectory(output);
            var playerFile = world.resolve("playerdata").resolve(playerId + ".dat");
            var levelFile = world.resolve("level.dat");
            var player = read(playerFile);
            var level = read(levelFile);
            require(level.get("Data") instanceof CompoundTag data && data.get("Player") instanceof CompoundTag,
                    "normal stopped level.dat has no native host Player");
            var host = level.getCompound("Data").getCompound("Player");
            require(player.hasUUID("UUID") && host.hasUUID("UUID")
                            && playerId.equals(player.getUUID("UUID")) && playerId.equals(host.getUUID("UUID")),
                    "stopped native inputs do not belong to authenticated player");
            // Preserve full original native files before any fixture mutation, including unrelated NBT.
            Files.copy(playerFile, output.resolve("before-player.dat"));
            Files.copy(levelFile, output.resolve("before-level.dat"));
            Tag skills = System.getProperty("gramarye.p11.sourceWriter.case").equals("owned-copy-raw")
                    ? ByteTag.valueOf((byte) 7) : marker();
            var mana = new CompoundTag();
            mana.putInt("schema_version", 0);
            mana.putLong("balance", -1L);
            replaceInputs(player, skills, mana);
            replaceInputs(host, skills, mana);
            NbtIo.writeCompressed(player, playerFile);
            NbtIo.writeCompressed(level, levelFile);
            prepared = new Prepared(stopped, world, playerId, skills.copy(), mana.copy());
            assertFiles(prepared);
            Files.copy(playerFile, output.resolve("prepared-player.dat"));
            Files.copy(levelFile, output.resolve("prepared-level.dat"));
            String result = "fixture=STOPPED_OWNED_NATIVE_INPUT_ONLY\nsourceRead=NOT_YET_OBSERVED\n"
                    + "changedFields=NeoForgeData.skills_and_mana_only\nskills=" + variant()
                    + "\nmana=CANONICAL_UNAVAILABLE_BALANCE_MINUS_ONE\nrawPreservingManaClaim=false\n"
                    + "liveAttachmentMutation=false\nproofMinted=false\n";
            write(output.resolve("preparation.txt"), result);
            return result;
        } catch (IOException failure) { throw new IllegalStateException("owned-copy stopped fixture IO", failure); }
    }

    static Report run(MinecraftServer server, ServerPlayer actor, Path output) {
        var observation = new Observation(actor);
        try {
            require(selected() && prepared != null && !used && active == null
                            && server != prepared.stopped && actor.getUUID().equals(prepared.player),
                    "owned-copy run has no exact stopped-input predecessor");
            exactActor(server, actor);
            freshDirectory(output);
            used = true;
            var before = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            requireHealthyUnavailable(before);
            require(before.sourceInput().equals("HOST_PRIMARY"), "owned-copy input did not use normal native host read");
            assertMaterials(actor);
            var skillsType = NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.parse(SKILLS));
            var manaType = NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.parse(MANA));
            require(skillsType != null && manaType != null && actor.hasData(skillsType) && actor.hasData(manaType),
                    "owned-copy cannot install missing material");
            Object originalSkills = actor.getExistingDataOrNull(skillsType);
            Object originalMana = actor.getExistingDataOrNull(manaType);
            var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
            require(owner != null && owner.body(actor) != null && owner.body(actor).complete,
                    "authenticated input lacks current complete source owner");
            long observers = P11NativeStorageBoundary.observerFailureCount();
            long cleanup = P11NativeCleanup.secondaryFailures();
            var connection = actor.connection;
            active = observation;
            actor.kill();
            require(actor.isDeadOrDying() && actor.getHealth() <= 0, "native kill did not reach death precondition");
            connection.handleClientCommand(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            var next = observation.next;
            require(next != null && next != actor && observation.clones == 1 && observation.respawns == 1
                            && actor.isRemoved() && connection.player == next && next.connection == connection,
                    "original native death/clone caller did not install exactly one B");
            exactActor(server, next);
            assertMaterials(next);
            require(next.getExistingDataOrNull(skillsType) != originalSkills
                            && next.getExistingDataOrNull(manaType) != originalMana,
                    "native owned copy aliased original attachment state");
            var after = P11NativeStorageBoundary.diagnostics(server, next.getUUID());
            requireHealthyUnavailable(after);
            var body = owner.body(next);
            require(body != null && body.complete && body.fault == P11QualifiedSourceOwner.Fault.NONE
                            && owner.body(actor) == null && owner.canSerialize(body)
                            && after.sourceEpoch() > before.sourceEpoch() && after.sourceInput().equals("MEMORY")
                            && next.getAdvancements() == actor.getAdvancements() && next.getStats() == actor.getStats()
                            && owner.canonicalAdvancements(next.getAdvancements()) == body
                            && owner.canonicalStats(next.getStats()) == body,
                    "legal quarantined datum was mistaken for an incomplete native B");
            require(P11NativeStorageBoundary.observerFailureCount() == observers
                            && P11NativeCleanup.secondaryFailures() == cleanup,
                    "owned-copy hid an observer or cleanup failure");
            String details = "case=" + variant() + "\nentry=AUTHENTICATED_HOST_READ_NATIVE_DEATH_CLONE\n"
                    + "syntheticActor=false\nmanualListenerAssignment=false\nmanualProof=false\n"
                    + "cloneEvents=1\nrespawnEvents=1\nbodyComplete=true\nskillsCapability=UNAVAILABLE\n"
                    + "manaCapability=UNAVAILABLE\nmanaEncoding=CANONICAL_MINUS_ONE_NOT_RAW_PRESERVING\n"
                    + "freshSkillAndManaObjects=true\noriginalRequiredCopyFailurePolicy=UNCHANGED\nsource=" + after + '\n';
            write(output.resolve("native-copy.txt"), details);
            return new Report(true, details, "NONE");
        } catch (IOException | RuntimeException | Error failure) {
            String text = failure.getClass().getName() + ": " + failure.getMessage();
            return new Report(false, "case=" + variant() + '\n', text.length() > 768 ? text.substring(0, 768) : text);
        } finally { active = null; }
    }

    static String verifySaved(MinecraftServer server, ServerPlayer actor, Path output) {
        try {
            require(used && prepared != null && server != prepared.stopped, "owned copy did not run");
            exactActor(server, actor);
            assertMaterials(actor);
            var source = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            requireHealthyUnavailable(source);
            for (String kind : List.of("PLAYER_DATA", "LEVEL_PLAYER")) {
                var matches = source.writers().stream().filter(row -> row.kind().equals(kind)).toList();
                require(matches.size() == 1, "missing or duplicate original writer " + kind);
                var writer = matches.getFirst();
                require(writer.attempt() > 0 && !writer.dirty() && writer.terminal().equals("COMPLETED")
                                && writer.encode().equals("SUCCEEDED") && writer.write().equals("SUCCEEDED")
                                && writer.close().equals("SUCCEEDED") && writer.replace().equals("SUCCEEDED"),
                        "quarantined complete B did not complete original writer " + writer);
            }
            require(source.resources().dirtyUuids() == 0, "original writers did not discharge source dirtiness");
            return retainFiles(output, "ORIGINAL_NATIVE_SAVE_WRITERS", source.toString());
        } catch (IOException failure) { throw new IllegalStateException("owned-copy native save observation", failure); }
    }

    static String verifyStopped(MinecraftServer stopped, Path world, UUID playerId, Path output) {
        try {
            require(used && prepared != null && stopped != prepared.stopped && stopped.isShutdown()
                            && world.equals(prepared.world) && playerId.equals(prepared.player),
                    "owned-copy final files were not observed after exact second native shutdown");
            return retainFiles(output, "ORIGINAL_NATIVE_FINAL_STOP", "parent records per-writer terminal snapshot");
        } catch (IOException failure) { throw new IllegalStateException("owned-copy final native files", failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void cloned(PlayerEvent.Clone event) {
        var value = active;
        if (value == null || event.getOriginal() != value.original) { return; }
        require(event.isWasDeath() && event.getEntity() instanceof ServerPlayer, "owned-copy expected original death Clone");
        value.next = (ServerPlayer) event.getEntity();
        value.clones++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        var value = active;
        if (value == null || event.getEntity() != value.next) { return; }
        require(!event.isEndConquered(), "owned-copy death unexpectedly became End respawn");
        value.respawns++;
    }

    private static void exactActor(MinecraftServer server, ServerPlayer actor) {
        require(server != null && server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer()
                        && server.isSingleplayerOwner(actor.getGameProfile())
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor && !actor.isRemoved()
                        && actor.connection != null && actor.connection.player == actor
                        && actor.connection.getConnection().isConnected()
                        && actor.getUUID().equals(prepared.player)
                        && server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(prepared.world),
                "owned-copy needs exact authenticated current actor on the owned native server");
    }

    private static void requireHealthyUnavailable(P11QualifiedSourceOwner.Diagnostics source) {
        require(source.active() && source.bodyComplete() && !source.candidatePresent()
                        && source.sourceEpoch() > 0 && source.sourceFault().equals("NONE")
                        && source.equippedSlot0().equals("UNAVAILABLE"),
                "quarantine must remain unavailable without losing whole-body completeness: " + source);
    }

    private static void assertMaterials(ServerPlayer actor) {
        var data = actor.serializeAttachments(actor.registryAccess());
        require(data != null && prepared.skills.equals(data.get(SKILLS)) && prepared.mana.equals(data.get(MANA)),
                "native serializer changed owned Raw/marker or canonical unavailable mana");
        require(P11OwnedCopySkillObservation.existingKind(actor).equals(
                        variant().equals("owned-copy-raw") ? "PRESERVED_RAW" : "OVERSIZE_MARKER"),
                "native read/copy did not retain the exact named quarantine variant");
        require(P11OwnedCopyManaObservation.isExistingUnavailable(actor), "native mana datum is not unavailable");
    }

    private static void replaceInputs(CompoundTag player, Tag skills, CompoundTag mana) {
        require(player.get(AttachmentHolder.ATTACHMENTS_NBT_KEY) instanceof CompoundTag attachments
                        && attachments.contains(SKILLS) && attachments.contains(MANA),
                "first native save has no existing skill/mana input slots");
        var attachments = player.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        attachments.put(SKILLS, skills.copy());
        attachments.put(MANA, mana.copy());
    }

    private static void assertFiles(Prepared input) throws IOException {
        var player = read(input.world.resolve("playerdata").resolve(input.player + ".dat"));
        var level = read(input.world.resolve("level.dat")).getCompound("Data").getCompound("Player");
        for (var root : List.of(player, level)) {
            require(input.player.equals(root.getUUID("UUID")), "native writer changed owned UUID");
            var data = root.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
            require(input.skills.equals(data.get(SKILLS)) && input.mana.equals(data.get(MANA)),
                    "native file failed exact idempotent Raw/marker and canonical mana payload");
        }
    }

    private static String retainFiles(Path output, String layer, String source) throws IOException {
        freshDirectory(output);
        assertFiles(prepared);
        Files.copy(prepared.world.resolve("playerdata").resolve(prepared.player + ".dat"), output.resolve("native-player.dat"));
        Files.copy(prepared.world.resolve("level.dat"), output.resolve("native-level.dat"));
        String result = "layer=" + layer + "\nskills=" + variant()
                + "\nrawOrMarkerExactAndNotRewrapped=true\nmanaCanonicalUnavailable=true\nsource=" + source + '\n';
        write(output.resolve("native-files.txt"), result);
        return result;
    }

    private static CompoundTag marker() {
        var value = new CompoundTag();
        value.putInt("schema_version", 0);
        value.putString("code", "encoded_capacity_exceeded");
        value.putLong("observed_at_least", MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES + 1L);
        value.putLong("maximum", MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES);
        var result = new CompoundTag();
        result.put("__gramarye_attachment_quarantine_v0", value);
        return result;
    }

    private static CompoundTag read(Path path) throws IOException {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "native input missing or symbolic: " + path);
        return NbtIo.readCompressed(path, NbtAccounter.create(READ_QUOTA));
    }
    private static void freshDirectory(Path path) throws IOException {
        require(path.isAbsolute() && path.equals(path.normalize()) && path.getParent() != null
                        && Files.isDirectory(path.getParent(), LinkOption.NOFOLLOW_LINKS), "invalid owned evidence child");
        Files.createDirectory(path);
    }
    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static String variant() { return System.getProperty("gramarye.p11.sourceWriter.case", "UNSELECTED"); }
    private static void require(boolean condition, String detail) { if (!condition) { throw new IllegalStateException(detail); } }
    private record Prepared(MinecraftServer stopped, Path world, UUID player, Tag skills, CompoundTag mana) {}
    private static final class Observation {
        final ServerPlayer original;
        ServerPlayer next;
        int clones, respawns;
        Observation(ServerPlayer original) { this.original = original; }
    }
}
