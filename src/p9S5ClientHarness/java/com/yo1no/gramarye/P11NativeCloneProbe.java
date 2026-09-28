package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Excluded death-clone parity and complete-B post-material callback failure, actual native actors only. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeCloneProbe {
    private static final String MARKER = "p11_native_death_clone";
    private static final String TAIL = "p11_full_b_prefix";
    private static final ResourceLocation SKILLS = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_skills");
    private static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_mana");
    private static Observation active;
    private static boolean used;

    private P11NativeCloneProbe() {}

    record Report(boolean passed, int expectedFailures, String observation, String failure) {
        @Override public String toString() {
            return "P11-NATIVE-CLONE-PROBE-V1\n" + observation + "failure=" + failure
                    + "\nRESULT=" + (passed ? "PASS" : "FAIL") + '\n';
        }
    }

    static Report run(MinecraftServer server, ServerPlayer actor, Path output) {
        String selected = System.getProperty("gramarye.p11.sourceWriter.case", "");
        boolean fullFault = selected.equals("full-b-fault");
        var value = new Observation(actor, fullFault);
        try {
            require(System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) != null
                            && (fullFault || selected.equals("clone-parity")) && !used && active == null,
                    "clone probe requires its selected excluded one-shot run");
            require(server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer()
                            && server.isSingleplayerOwner(actor.getGameProfile())
                            && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                            && actor.connection != null && actor.connection.player == actor
                            && actor.connection.getConnection().isConnected() && !actor.wonGame,
                    "clone probe needs the exact current authenticated ordinary actor");
            require(output.isAbsolute() && output.equals(output.normalize()) && output.getParent() != null
                            && Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS),
                    "clone evidence needs a fresh child of owned output");
            Files.createDirectory(output);
            used = true; active = value;
            var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
            var before = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            require(owner != null && before.bodyComplete() && !before.candidatePresent()
                            && before.sourceFault().equals("NONE")
                            && !actor.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY),
                    "clone needs a healthy complete source and original keepInventory=false fixture");
            var skills = NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS);
            var mana = NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA);
            require(skills != null && mana != null && actor.hasData(skills) && actor.hasData(mana),
                    "clone probe cannot provision missing skill/mana materials");
            value.skillsBefore = actor.getExistingDataOrNull(skills);
            value.attachmentBefore = actor.serializeAttachments(server.registryAccess());
            require(value.attachmentBefore != null && value.attachmentBefore.contains(SKILLS.toString())
                            && value.attachmentBefore.contains(MANA.toString()), "native attachment evidence missing");
            var persisted = actor.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG);
            persisted.putString(MARKER, "original-native-persisted-section");
            actor.getPersistentData().put(ServerPlayer.PERSISTED_NBT_TAG, persisted);
            value.persistedSection = persisted;
            long observerFailures = P11NativeStorageBoundary.observerFailureCount();
            long cleanupFailures = P11NativeCleanup.secondaryFailures();
            var connection = actor.connection;
            actor.kill();
            require(actor.isDeadOrDying() && actor.getHealth() <= 0 && value.deaths == 1,
                    "actual native kill did not establish original death precondition");
            boolean samePrimary = false;
            try {
                connection.handleClientCommand(new ServerboundClientCommandPacket(
                        ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            } catch (RuntimeException primary) {
                if (primary != value.fault) { throw primary; }
                samePrimary = true;
            }
            var next = value.next;
            require(next != null && next != actor && value.clones == 1 && value.respawns == 1
                            && value.originalLeaves == 1 && actor.isRemoved()
                            && next.getAdvancements() == actor.getAdvancements() && next.getStats() == actor.getStats(),
                    "true native death/clone/respawn ownership or event count mismatch");
            require(next.getEnderChestInventory() == actor.getEnderChestInventory()
                            && next.getFoodData() != actor.getFoodData()
                            && next.getPersistentData().get(ServerPlayer.PERSISTED_NBT_TAG) == value.persistedSection,
                    "native death alias/new-food behavior changed");
            var copied = next.serializeAttachments(server.registryAccess());
            require(next.getExistingDataOrNull(skills) != value.skillsBefore
                            && copied != null && value.attachmentBefore.get(SKILLS.toString()).equals(copied.get(SKILLS.toString()))
                            && value.attachmentBefore.get(MANA.toString()).equals(copied.get(MANA.toString())),
                    "actual copyOnDeath skill/mana material differs from native input");
            var after = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
            var body = owner.body(next);
            require(body != null && body.complete && body.fault == P11QualifiedSourceOwner.Fault.NONE
                            && after.bodyComplete() && !after.candidatePresent() && after.sourceEpoch() > before.sourceEpoch()
                            && owner.body(actor) == null && owner.canonicalAdvancements(next.getAdvancements()) == body
                            && owner.canonicalStats(next.getStats()) == body,
                    "complete B failed to become sole source/canonical owner");
            if (fullFault) {
                require(samePrimary && value.loggedOut == 1 && value.nextLeaves == 1
                                && next.isRemoved() && body.logoutAttempted && !body.logoutActive
                                && body.envelope != null && body.pendingEnvelope == null && !body.account.cleanupUnknown
                                && server.getPlayerList().getPlayer(actor.getUUID()) == null
                                && next.serverLevel().getEntity(next.getId()) != next
                                && next.serverLevel().getEntity(next.getUUID()) != next
                                && connection.player == actor,
                        "full-B fault did not keep original caller assignment and complete one fresh exact-B logout");
                require(next.totalExperience == 17 && next.getTags().contains(TAIL) && owner.canSerialize(body),
                        "full-B supported native callback prefix was lost or unwriteable");
                var playerPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
                var saved = NbtIo.readCompressed(playerPath, NbtAccounter.create(32L * 1024 * 1024));
                require(saved.getInt("XpTotal") == 17 && saved.getFloat("Health") == 10.0F
                                && saved.getList("Tags", net.minecraft.nbt.Tag.TAG_STRING).stream()
                                        .anyMatch(tag -> tag.getAsString().equals(TAIL)),
                        "fresh native B logout writer did not preserve full callback prefix");
                Files.copy(playerPath, output.resolve("native-full-B-logout.dat"));
            } else {
                require(!samePrimary && value.loggedOut == 0 && value.nextLeaves == 0
                                && connection.player == next && next.connection == connection && !next.isRemoved()
                                && server.getPlayerList().getPlayer(actor.getUUID()) == next
                                && next.serverLevel().getEntity(next.getId()) == next
                                && next.serverLevel().getEntity(next.getUUID()) == next
                                && next.totalExperience == 0 && next.getHealth() == 10.0F,
                        "normal native death respawn did not publish exact clamped B");
            }
            require(P11NativeStorageBoundary.observerFailureCount() == observerFailures
                            && P11NativeCleanup.secondaryFailures() == cleanupFailures && value.loads == 0,
                    "secondary observer/cleanup failure was hidden behind native outcome");
            String details = "case=" + selected + "\nentry=ORIGINAL_NATIVE_KILL_AND_SERVER_RESPAWN_HANDLER\n"
                    + "newClientPacket=false\nkeepEverything=false\nendConquered=false\nwasDeath=true\n"
                    + "syntheticActor=false\nmanualListenerAssignment=false\nmanualMaterialReceipt=false\n"
                    + "deathEvents=" + value.deaths + "\ncloneEvents=" + value.clones
                    + "\nrespawnEvents=" + value.respawns + "\noriginalLeaveEvents=" + value.originalLeaves
                    + "\nnewLeaveEvents=" + value.nextLeaves + "\nloggedOutEvents=" + value.loggedOut
                    + "\ncopyOnDeath=ACTUAL_NATIVE_SERIALIZED_SKILLS_MANA_EQUAL_FRESH_SKILLS_OBJECT\n"
                    + "healthClamp=CLONE_CALLBACK_MAX10_ORIGINAL_HEALTH20_TO_NATIVE_FINAL10\n"
                    + "aliases=ORIGINAL_ENDER_CHEST_AND_PERSISTED_SECTION_NEW_DEATH_FOOD\n"
                    + "samePrimaryPropagated=" + samePrimary + "\nfullBFault=" + fullFault
                    + "\nARevived=false\nactualEntityLoadCalls=" + value.loads + "\nfullRewardClaimed=false\n"
                    + "source=" + after + '\n';
            Files.writeString(output.resolve("native-clone-observation.txt"), details,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return new Report(true, fullFault ? 1 : 0, details, "NONE");
        } catch (IOException | RuntimeException | Error failure) {
            String detail = failure.getClass().getName() + ": " + failure.getMessage();
            return new Report(false, fullFault ? 1 : 0, "case=" + selected + '\n',
                    detail.length() > 768 ? detail.substring(0, 768) : detail);
        } finally { active = null; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void died(LivingDeathEvent event) {
        var value = active;
        if (value != null && event.getEntity() == value.original) { value.deaths++; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void clone(PlayerEvent.Clone event) {
        var value = active;
        if (value == null || event.getOriginal() != value.original || !(event.getEntity() instanceof ServerPlayer next)) { return; }
        value.next = next; value.clones++;
        require(event.isWasDeath() && next.getAdvancements() == value.original.getAdvancements()
                        && next.getStats() == value.original.getStats() && next.getHealth() == next.getMaxHealth()
                        && next.getHealth() > 10.0F,
                "clone callback is not actual original death-copy prefix");
        next.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0);
        require(next.getHealth() > next.getMaxHealth(), "callback did not establish native later-clamp input");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        var value = active;
        if (value == null || event.getEntity() != value.next) { return; }
        value.respawns++;
        require(!event.isEndConquered() && value.next.getHealth() == 10.0F
                        && value.next.getServer().getPlayerList().getPlayer(value.next.getUUID()) == value.next,
                "respawn callback did not follow native membership and health clamp");
        if (value.fullFault) {
            new AdvancementRewards(17, List.of(), List.of(), Optional.empty()).grant(value.next);
            value.next.addTag(TAIL);
            throw value.fault;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var value = active;
        if (value != null && event.getEntity() == value.next) { value.loggedOut++; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void leave(EntityLeaveLevelEvent event) {
        var value = active;
        if (value == null) { return; }
        if (event.getEntity() == value.original) { value.originalLeaves++; }
        if (event.getEntity() == value.next) { value.nextLeaves++; }
    }

    /** Read-only excluded observer at the actual Entity.load entry, including constructor-time B. */
    public static void loaded(net.minecraft.world.entity.Entity entity) {
        var value = active;
        if (value != null && entity instanceof ServerPlayer player
                && player.getServer() == value.original.getServer()
                && player.getUUID().equals(value.original.getUUID())) { value.loads++; }
    }

    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }

    private static final class Observation {
        final ServerPlayer original;
        final boolean fullFault;
        final RuntimeException fault = new IllegalStateException("P11_NATIVE_FULL_B_RESPAWN_CALLBACK_FAULT");
        ServerPlayer next;
        CompoundTag attachmentBefore, persistedSection;
        Object skillsBefore;
        int deaths, clones, respawns, loggedOut, originalLeaves, nextLeaves, loads;
        Observation(ServerPlayer original, boolean fullFault) { this.original = original; this.fullFault = fullFault; }
    }
}
