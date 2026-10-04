package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Excluded healthy continuity assertions around the existing real End and CONFIG scenes. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aRewardContinuityProbe {
    private static final ResourceLocation PREPARED = id("native_prepared"), REWARD = id("native_reward"),
            ROOT = id("delivery_root"), RECIPE = id("delivery_recipe"),
            BREAD = ResourceLocation.withDefaultNamespace("bread"), POTATO = ResourceLocation.withDefaultNamespace("baked_potato");
    private static final String PREPARED_TAG = "p11_native_prepared_function", REWARD_TAG = "p11_native_reward_function",
            DELIVERY_TAG = "p11_native_delivery_tail";
    private static Run active;
    private P11C4aRewardContinuityProbe() {}

    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.NATIVE_REWARD_CONTINUITY; }

    /** Actual End client view is ready; A is still live, before the original exit portal collision. */
    static void beforeEndExit(ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(selected() && "c4a-reward".equals(System.getProperty("gramarye.p11.online.case")) && active == null,
                "REWARD_EXACT_COHORT_ONLY");
        var server = actor.getServer();
        require(server != null && server.isSameThread() && server.isDedicatedServer() && server.usesAuthentication()
                && actor != peer && !actor.getUUID().equals(peer.getUUID()) && actor.isAlive() && !actor.isRemoved()
                && !actor.wonGame && !actor.seenCredits && actor.connection.player == actor
                && actor.connection.getConnection().isConnected() && actor.connection.getConnection().isEncrypted()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && peer.isAlive()
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "REWARD_REAL_LIVE_END_ACTOR");
        var r = new Run(actor, peer, output); active = r;
        require(r.owner != null && r.owner.canSerialize(r.owner.body(actor)) && r.peerBody != null
                && r.server.getFunctions().get(id("reward")).isPresent()
                && r.server.getFunctions().get(id("prepared_reward")).isPresent()
                && r.server.getFunctions().get(id("delivery_tail")).isPresent(), "REWARD_NATIVE_FIXTURE_MISSING");
        var prepared = server.getAdvancements().get(PREPARED);
        require(prepared != null && server.getAdvancements().get(REWARD) != null
                && server.getAdvancements().get(RECIPE) != null && !actor.hasEffect(MobEffects.LUCK)
                && !actor.getAdvancements().getOrStartProgress(server.getAdvancements().get(REWARD)).isDone()
                && !actor.getTags().contains(REWARD_TAG), "REWARD_FRESH_CRITERIA_REQUIRED");
        require(actor.addEffect(new MobEffectInstance(MobEffects.LUCK, 400)), "REWARD_ORIGINAL_EFFECT_NOT_ADDED");
        require(actor.getAdvancements().getOrStartProgress(prepared).isDone()
                && actor.getAdvancements().revoke(prepared, "native_effect"), "REWARD_PREPARED_SETUP_NOT_NATIVE");
        actor.resetRecipes(List.of(server.getRecipeManager().byKey(POTATO).orElseThrow(),
                server.getRecipeManager().byKey(BREAD).orElseThrow()));
        require(actor.removeTag(PREPARED_TAG) && !actor.getRecipeBook().contains(POTATO), "REWARD_PREPARED_RESET_FAILED");
        r.preparedXp = actor.totalExperience; r.preparedScore = actor.getScore(); r.originalEpoch = source(actor).sourceEpoch();
        r.endVictim = credit(actor); r.endCreditTick = server.getTickCount();
        r.preparedArmed = true;
        P11C4aEvidence.write(output, "reward-end-armed.json", report(r, "REAL_NATIVE_CREDIT_AND_EFFECT_PREPARED_NOT_HANDOFF_PROOF"));
    }

    /** Called only after the genuine WinScreen callback's original native body publishes B. */
    static void endReturned(ServerPlayer next) throws IOException {
        var r = active; require(r != null && r.server.isSameThread(), "REWARD_END_NOT_ARMED");
        if (r.endDone) { require(r.next == next, "REWARD_END_DUPLICATE_DIFFERENT_ACTOR"); return; }
        healthy(r); r.preparedArmed = false;
        require(next != r.original && next.connection == r.original.connection && next.connection.player == next
                && r.connection.isConnected() && r.original.isRemoved() && !next.wonGame && next.seenCredits
                && r.server.getPlayerList().getPlayer(next.getUUID()) == next
                && next.getAdvancements() == r.original.getAdvancements() && next.getStats() == r.original.getStats()
                && r.owner.body(r.original) == null && r.owner.nativeRecipient(r.original) == null
                && source(next).sourceEpoch() > r.originalEpoch && source(next).bodyComplete(), "REWARD_END_NATIVE_B_CUSTODY");
        require(r.preparedXpCalls == 1 && r.preparedFunctionCalls == 1 && r.preparedSendEntries == 1
                && r.preparedSendReturns == 1 && r.preparedPacket == null
                && next.totalExperience == r.preparedXp && next.getScore() == r.preparedScore
                && !next.getRecipeBook().contains(POTATO) && next.getTags().contains(PREPARED_TAG)
                && !r.original.getTags().contains(PREPARED_TAG)
                && next.getAdvancements().getOrStartProgress(r.server.getAdvancements().get(PREPARED)).isDone(),
                "REWARD_PREPARED_B_OR_NATIVE_OVERWRITE");
        require(r.server.getTickCount() - r.endCreditTick < 100 && r.endVictim.getKillCredit() == r.original
                && root(next, "NATIVE_CREDIT") > 0, "REWARD_NATIVE_CREDIT_NATURALLY_EXPIRED");
        r.next = next;
        int oldXp = r.original.totalExperience, oldScore = r.original.getScore(), xp = next.totalExperience,
                score = next.getScore(), emeralds = next.getInventory().countItem(Items.EMERALD),
                oldEmeralds = r.original.getInventory().countItem(Items.EMERALD);
        var held = new ExecutionContext<?>[1];
        r.wholeArmed = true;
        try {
            Commands.executeCommandInContext(next.createCommandSourceStack().withPermission(2).withSuppressedOutput(), context -> {
                held[0] = context;
                long fop = root(next, "OPERATION");
                require(root(next, "COMMAND_CONTEXT") > 0, "REWARD_ORIGINAL_CONTEXT_NOT_RETAINED");
                lethalWithoutMobLoot(r, r.endVictim);
                require(r.scoreCalls == 1 && next.totalExperience == xp + 37 && r.original.totalExperience == oldXp
                        && next.getInventory().countItem(Items.EMERALD) == emeralds + 1
                        && r.original.getInventory().countItem(Items.EMERALD) == oldEmeralds
                        && next.getRecipeBook().contains(BREAD) && !next.getTags().contains(REWARD_TAG)
                        && root(next, "OPERATION") == fop && root(next, "COMMAND_CONTEXT") > 0,
                        "REWARD_WHOLE_FIXED_B_PREFIX_OR_EARLY_DRAIN");
                context.close();
                require(root(next, "COMMAND_CONTEXT") > 0 && !P11NativeOperationBoundary.observe(context).terminal(),
                        "REWARD_TRACER_CLOSE_CLEARED_OWNER");
            });
        } finally { r.wholeArmed = false; }
        var terminal = P11NativeOperationBoundary.observe(held[0]);
        require(terminal.terminal() && terminal.outerNormal() && terminal.tracerClosed()
                && terminal.retainedBindings() == 0 && "EMPTY".equals(terminal.drain())
                && next.totalExperience == xp + 40 && next.getScore() == score + 51
                && next.getTags().contains(REWARD_TAG) && r.original.totalExperience == oldXp
                && r.original.getScore() == oldScore && !r.original.getTags().contains(REWARD_TAG)
                && next.getAdvancements().getOrStartProgress(r.server.getAdvancements().get(REWARD)).isDone(),
                "REWARD_NATIVE_OWNER_TERMINAL_OR_OLD_A_MUTATION");
        r.endVictim.remove(Entity.RemovalReason.DISCARDED);
        require(root(next, "OPERATION") == 0 && root(next, "COMMAND_CONTEXT") == 0
                && r.endVictim.isRemoved(), "REWARD_SCOPE_OR_NATIVE_HOLDER_LEAK");
        r.endDone = true;
        r.savedEmeralds = next.getInventory().countItem(Items.EMERALD);
        P11C4aEvidence.write(r.output, "reward-end-complete.json", report(r, "PREPARED_B_AND_REAL_N_TO_FIXED_B_WHOLE_REWARD_OBSERVED"));
    }

    /** Live current B, immediately before the parent's one original switchToConfig call. */
    static void beforeConfiguration(ServerPlayer actor) {
        var r = active; require(r != null && r.endDone && !r.configurationArmed && r.next == actor,
                "REWARD_CONFIG_PREPARATION_ORDER"); healthy(r);
        require(P11NativeStorageBoundary.nativeDeliveryEligible(actor), "REWARD_CONFIG_PREPARATION_NOT_LIVE");
        actor.resetRecipes(List.of(r.server.getRecipeManager().byKey(BREAD).orElseThrow()));
        var recipe = r.server.getAdvancements().get(RECIPE);
        require(recipe != null && actor.getAdvancements().revoke(recipe, "bread")
                && !actor.getRecipeBook().contains(BREAD) && !actor.getTags().contains(DELIVERY_TAG), "REWARD_DELIVERY_FRESH_RESET");
        require(actor.getAdvancements().award(r.server.getAdvancements().get(ROOT), "manual"), "REWARD_DELIVERY_ROOT_NOT_FRESH");
        actor.getAdvancements().flushDirty(actor);
        r.configVictim = credit(actor); r.configCreditTick = r.server.getTickCount();
        r.configActor = actor; r.configEpoch = source(actor).sourceEpoch(); r.configXp = actor.totalExperience;
        r.configurationArmed = true;
    }

    /** Normal switch return only. Native die opens the actual retained N/Fop; no synthetic scope. */
    static void configurationSwitched() throws IOException {
        var r = active; require(r != null && r.configurationArmed && !r.detachedDone, "REWARD_CONFIG_SWITCH_ORDER"); healthy(r);
        require(r.configActor.isRemoved() && r.server.getPlayerList().getPlayer(r.configActor.getUUID()) == null
                && !P11NativeStorageBoundary.nativeDeliveryEligible(r.configActor)
                && P11NativeStorageBoundary.detachedPresence(r.configActor)
                && r.server.getTickCount() - r.configCreditTick < 100
                && r.configVictim.getKillCredit() == r.configActor, "REWARD_DETACHED_REAL_N_SOURCE_REQUIRED");
        lethalWithoutMobLoot(r, r.configVictim);
        r.configVictim.remove(Entity.RemovalReason.DISCARDED);
        require(r.detachedGrantCalls == 1 && r.detachedFop > 0 && r.recipeProgress == 1
                && r.configActor.getRecipeBook().contains(BREAD) && r.configActor.getTags().contains(DELIVERY_TAG)
                && ((P11RecipeDelivery.Access) r.configActor.getRecipeBook()).p11$needsRecipeSync()
                && r.breadAddEntries == 0 && r.configActor.totalExperience == r.configXp
                && root(r.configActor, "OPERATION") == 0 && root(r.configActor, "COMMAND_CONTEXT") == 0,
                "REWARD_DETACHED_LOGICAL_TAIL_OR_DELIVERY_SINK");
        r.detachedDone = true;
        P11C4aEvidence.write(r.output, "reward-detached-logical.json", report(r, "REAL_CREDIT_CONSUMER_LOGICAL_RECIPE_FUNCTION_WITHOUT_ADD_SEND"));
    }

    static boolean configurationReturned(ServerPlayer current) throws IOException {
        var r = active; require(r != null && r.detachedDone, "REWARD_RETURN_WITHOUT_NATIVE_GRANT"); healthy(r);
        if (r.complete) { require(current == r.result, "REWARD_RETURN_REPLAY"); return true; }
        require(current != r.configActor && current.connection.getConnection() == r.connection
                && current.connection.player == current && current.getAdvancements() == r.configActor.getAdvancements()
                && current.getStats() == r.configActor.getStats() && r.owner.body(r.configActor) == null
                && source(current).sourceEpoch() > r.configEpoch && source(current).bodyComplete()
                && current.totalExperience == r.configXp && current.getRecipeBook().contains(BREAD)
                && current.getTags().contains(DELIVERY_TAG)
                && !((P11RecipeDelivery.Access) current.getRecipeBook()).p11$needsRecipeSync()
                && r.initEntries == 1 && r.initReturns == 1 && r.breadAddEntries == 0
                && r.recipeProgress == 1 && r.detachedGrantCalls == 1, "REWARD_RETURN_LOGICAL_DATA_OR_INIT_REPLAY");
        if (!P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-a"), "reward-delivery-client.json")) { return false; }
        require(r.server.saveEverything(true, false, false), "REWARD_ORIGINAL_FINAL_SAVE_FAILED");
        var disk = NbtIo.readCompressed(r.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(current.getUUID() + ".dat"), NbtAccounter.unlimitedHeap());
        require(disk.getInt("XpTotal") == current.totalExperience
                && disk.getList("Tags", 8).stream().anyMatch(tag -> DELIVERY_TAG.equals(tag.getAsString()))
                && disk.getList("Tags", 8).stream().anyMatch(tag -> REWARD_TAG.equals(tag.getAsString()))
                && disk.getCompound("recipeBook").getList("recipes", 8).stream()
                        .anyMatch(tag -> BREAD.toString().equals(tag.getAsString())), "REWARD_ORIGINAL_PLAYER_DATA_READBACK");
        int storedEmeralds = 0;
        var inventory = disk.getList("Inventory", 10);
        for (int index = 0; index < inventory.size(); index++) {
            var stack = net.minecraft.world.item.ItemStack.parseOptional(r.server.registryAccess(), inventory.getCompound(index));
            if (stack.is(Items.EMERALD)) { storedEmeralds += stack.getCount(); }
        }
        require(storedEmeralds == r.savedEmeralds && current.getInventory().countItem(Items.EMERALD) == r.savedEmeralds,
                "REWARD_ORIGINAL_NATIVE_ITEMSTACK_READBACK");
        r.result = current; r.complete = true;
        P11C4aEvidence.write(r.output, "reward-continuity-server.json", report(r, "PASS_NAMED_HEALTHY_NATIVE_CONTINUITY_AND_ORIGINAL_PLAYER_DATA_READBACK"));
        return true;
    }

    static void finish() throws IOException {
        var r = active; require(r != null && r.complete, "REWARD_FINISH_WITHOUT_BOTH_HALVES"); healthy(r);
        // Existing parent terminal/logout order observes B first, then cues A; no synthetic close.
        if (!r.finishCued) { r.finishCued = true; P11C4aEvidence.cue(r.output, "b-normal-finish.ready"); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void xp(PlayerXpEvent.XpChange event) {
        var r = active; if (r == null || !r.preparedArmed || !(event.getEntity() instanceof ServerPlayer actor)
                || actor == r.original || !actor.getUUID().equals(r.original.getUUID())) { return; }
        observe(r, () -> {
            var body = r.owner.nativeRecipient(actor);
            require(body != null && !body.complete && body.actor == actor && actor.connection == r.original.connection
                    && actor.connection.player == r.original && root(actor, "OPERATION") > 0,
                    "REWARD_PREPARED_CALLBACK_NOT_PARTIAL_FIXED_B");
            if (event.getAmount() == 23) { r.preparedXpCalls++; }
            else if (event.getAmount() == 5) { require(root(actor, "COMMAND_CONTEXT") > 0, "REWARD_PREPARED_FUNCTION_NO_OWNER"); r.preparedFunctionCalls++; }
            else { throw new IllegalStateException("REWARD_PREPARED_UNEXPECTED_XP"); }
        });
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void death(LivingDeathEvent event) {
        var r = active; if (r == null) { return; }
        if (r.wholeArmed && event.getEntity() == r.endVictim) {
            require(r.endVictim.getKillCredit() == r.original && root(r.next, "OPERATION") > 0, "REWARD_SCORE_ORIGINAL_CAUSE");
            r.scoreCalls++; r.original.awardKillScore(r.endVictim, 11, event.getSource());
        }
        if (r.configurationArmed && !r.detachedDone && event.getEntity() == r.configVictim) {
            r.detachedFop = root(r.configActor, "OPERATION");
            require(r.detachedFop > 0 && r.configVictim.getKillCredit() == r.configActor && r.detachedGrantCalls == 0,
                    "REWARD_DETACHED_GRANT_WITHOUT_REAL_CONSUMER");
            r.detachedGrantCalls++;
            AdvancementRewards.Builder.recipe(BREAD).runs(id("delivery_tail")).build().grant(r.configActor);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void progress(AdvancementEvent.AdvancementProgressEvent event) {
        var r = active;
        if (r != null && r.configurationArmed && event.getEntity() == r.configActor
                && event.getAdvancement().id().equals(RECIPE)
                && event.getProgressType() == AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) { r.recipeProgress++; }
    }

    /** Exact original native send; no packet or callback retained beyond this synchronous call. */
    public static void send(ServerCommonPacketListenerImpl listener, Packet<?> packet, boolean returned) {
        var r = active; if (r == null || listener.getConnection() != r.connection || !(packet instanceof ClientboundRecipePacket recipe)) { return; }
        observe(r, () -> {
            if (r.preparedArmed && recipe.getState() == ClientboundRecipePacket.State.ADD && recipe.getRecipes().contains(POTATO)) {
                var receiver = ((P11CanonicalAdvancements.Access) r.original.getAdvancements()).p11$associatedPlayer();
                var body = r.owner.nativeRecipient(receiver);
                require(receiver != null && receiver != r.original && receiver.getUUID().equals(r.original.getUUID())
                        && body != null && body.actor == receiver && !body.complete
                        && receiver.connection == r.original.connection && receiver.connection.player == r.original
                        && receiver.getRecipeBook().contains(POTATO) && P11NativeStorageBoundary.nativeDeliveryEligible(receiver),
                        "REWARD_PREPARED_SEND_WRONG_RECEIVER");
                if (!returned) { require(r.preparedPacket == null && r.preparedSendEntries++ == 0, "REWARD_PREPARED_SEND_REPLAY"); r.preparedPacket = packet; }
                else { require(r.preparedPacket == packet && r.preparedSendReturns++ == 0, "REWARD_PREPARED_SEND_NOT_SAME_CALL"); r.preparedPacket = null; }
            }
            if (r.configurationArmed && recipe.getRecipes().contains(BREAD)) {
                if (recipe.getState() == ClientboundRecipePacket.State.ADD && !returned) { r.breadAddEntries++; }
                if (recipe.getState() == ClientboundRecipePacket.State.INIT) {
                    if (returned) { r.initReturns++; } else { r.initEntries++; }
                }
            }
        });
    }
    static boolean complete() { return active != null && active.complete; }
    static void release() { active = null; }
    private static Cow credit(ServerPlayer actor) {
        var cow = EntityType.COW.create(actor.serverLevel()); require(cow != null, "REWARD_NATIVE_COW_MISSING");
        cow.moveTo(actor.getX() + 2, actor.getY(), actor.getZ(), 0, 0);
        require(actor.serverLevel().addFreshEntity(cow) && cow.hurt(actor.damageSources().playerAttack(actor), 1.0F)
                && cow.getLastHurtByMob() == actor && cow.getKillCredit() == actor
                && cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == actor,
                "REWARD_NATIVE_CREDIT_PRODUCERS_FAILED");
        return cow;
    }
    private static void lethalWithoutMobLoot(Run r, Cow cow) {
        boolean before = r.server.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);
        var rule = r.server.getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        rule.set(false, r.server);
        try { require(cow.hurt(cow.damageSources().magic(), 1000.0F) && cow.isDeadOrDying(), "REWARD_NATIVE_DEATH_FAILED"); }
        finally { rule.set(before, r.server); }
        require(r.server.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT) == before, "REWARD_MOB_LOOT_RULE_NOT_RESTORED");
        r.mobLootRestores++;
    }
    private static void healthy(Run r) {
        require(r.failure == null && r.server.isSameThread() && r.peer.isAlive() && r.peer.connection.getConnection() == r.peerConnection
                && r.peerConnection.isConnected() && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer
                && r.owner.body(r.peer) == r.peerBody && r.peerBody.account.current == r.peerBody
                && r.peerBody.source.epoch() == r.peerEpoch && r.peer.getAdvancements() == r.peerBody.advancements
                && r.peer.getStats() == r.peerBody.stats, "REWARD_OBSERVER_OR_PEER_CHANGED");
    }
    private static void observe(Run r, Runnable body) {
        try { body.run(); } catch (RuntimeException | Error failure) { if (r.failure == null) { r.failure = "REWARD_READONLY_OBSERVER_FAILED"; } }
    }
    private static P11QualifiedSourceOwner.Diagnostics source(ServerPlayer actor) { return P11NativeStorageBoundary.diagnostics(actor.getServer(), actor.getUUID()); }
    private static long root(ServerPlayer actor, String kind) { return source(actor).nativeResponsibilities().roots().stream().filter(value -> value.kind().equals(kind)).mapToLong(value -> value.count()).sum(); }
    private static Map<String,Object> report(Run r, String status) {
        var m = new LinkedHashMap<String,Object>(); m.put("status", status); m.put("failure", r.failure);
        m.put("preparedXp23Calls", r.preparedXpCalls); m.put("preparedFunctionXp5Calls", r.preparedFunctionCalls);
        m.put("preparedOriginalSendEntries", r.preparedSendEntries); m.put("preparedOriginalSendReturns", r.preparedSendReturns);
        m.put("nativeScoreCalls", r.scoreCalls); m.put("wholeRewardComplete", r.endDone);
        m.put("detachedNativeGrantCalls", r.detachedGrantCalls); m.put("detachedActualFop", r.detachedFop);
        m.put("recipeCriterionGrants", r.recipeProgress); m.put("breadAddSendsAfterSwitch", r.breadAddEntries);
        m.put("recipeInitEntries", r.initEntries); m.put("recipeInitNormalReturns", r.initReturns);
        m.put("originalMobLootRuleRestoredCalls", r.mobLootRestores); m.put("ttlChanged", false);
        m.put("engineeringAuthorityUsed", false); m.put("rawHandlerCalled", false);
        m.put("physicalScope", "DEDICATED_ORIGINAL_PLAYER_DATA_NOT_HOST_LEVEL_CACHE"); return m;
    }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", value); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer original, peer; final Connection connection, peerConnection;
        final Path output; final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body peerBody; final long peerEpoch;
        ServerPlayer next, configActor, result; Cow endVictim, configVictim; Packet<?> preparedPacket;
        long originalEpoch, configEpoch, detachedFop; int preparedXp, preparedScore, configXp, endCreditTick, configCreditTick, savedEmeralds;
        int preparedXpCalls, preparedFunctionCalls, preparedSendEntries, preparedSendReturns, scoreCalls,
                detachedGrantCalls, recipeProgress, breadAddEntries, initEntries, initReturns, mobLootRestores;
        boolean preparedArmed, wholeArmed, endDone, configurationArmed, detachedDone, complete, finishCued; String failure;
        Run(ServerPlayer actor, ServerPlayer peer, Path output) {
            server = actor.getServer(); original = actor; this.peer = peer; this.output = output;
            connection = actor.connection.getConnection(); peerConnection = peer.connection.getConnection();
            owner = P11NativeStorageBoundary.nativeSourceOwner(actor); peerBody = owner == null ? null : owner.body(peer);
            peerEpoch = peerBody == null ? -1 : peerBody.source.epoch();
        }
    }
}
