package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Excluded companion: actual native calls on the authenticated host, never receipt fabrication. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeRewardProbe {
    private static final ResourceLocation REWARD = id("native_reward");
    private static final ResourceLocation PARTIAL = id("native_partial");
    private static final ResourceLocation PREPARED = id("native_prepared");
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static final ResourceLocation BAKED_POTATO = ResourceLocation.withDefaultNamespace("baked_potato");
    private static final ResourceLocation PARTIAL_FUNCTION = id("partial_reward");
    private static final String PARTIAL_PREFIX = "p11_native_partial_function_prefix";
    private static final String PARTIAL_TAIL = "p11_native_partial_function_tail";
    private static final String PARTIAL_COMMAND = "experience add @s 4 points";
    private static final String FUNCTION_TAG = "p11_native_reward_function";
    private static final String PREPARED_TAG = "p11_native_prepared_function";
    private static State active;

    private P11NativeRewardProbe() {}

    record Report(boolean passed, int expectedFailures, List<String> observations, String failure) {
        Report { observations = List.copyOf(observations); }
        @Override public String toString() {
            return "P11-NATIVE-REWARD-PROBE-V1\n"
                    + "scope=REAL_NATIVE_INTEGRATED_HOST_COMPONENTS\n"
                    + "respawnEntry=ORIGINAL_SERVER_HANDLER_OWNED_WON_GAME_PRECONDITION\n"
                    + "newClientRespawnPacket=false\nendTravelQualified=false\n"
                    + "generalExtendedAdmission=CLOSED_PENDING_C4A\np5L1=false\n"
                    + "syntheticPlayer=false\nsourceReceiptsFabricated=false\n"
                    + "creditDeferralConsumer=EXACT_TRANSFORMED_FIELD_ENGINEERING_ORDER_INSIDE_REAL_DIE\n"
                    + "differentAuthenticatedUuidContext=NOT_PROVEN_SINGLE_INTEGRATED_HOST_CONNECTION\n"
                    + "nativeOverflow=ACTUAL_QUEUE_BOUNDARY_CASE_INCLUDED_SEE_OBSERVATIONS\n"
                    + "expectedFailuresLowerBound=" + expectedFailures + '\n'
                    + String.join("\n", observations) + '\n' + "failure=" + failure + '\n'
                    + "RESULT=" + (passed ? "PASS" : "FAIL") + '\n';
        }
    }

    static Report run(MinecraftServer server, ServerPlayer actor, Path output) {
        var state = new State(actor, output);
        String failureDetail = "NONE";
        try {
            require(active == null && server.isSameThread() && actor.getServer() == server
                            && !actor.isFakePlayer() && server.isSingleplayerOwner(actor.getGameProfile())
                            && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                            && actor.connection != null && actor.connection.player == actor
                            && actor.connection.getConnection().isConnected(),
                    "probe needs the exact authenticated integrated host");
            require(output.isAbsolute() && output.equals(output.normalize()) && output.getParent() != null
                            && Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS),
                    "probe evidence must be a fresh child of the owned output directory");
            Files.createDirectory(output);
            active = state;
            P11NativeOperationBoundary.engineering(actor, () -> {
                nativeCreditAndReward(server, state);
                nestedRewards(server, state);
                commandTargets(server, state);
                creditConsumerDeferral(server, state);
                partialMutations(server, state);
                reentrantSave(server, state, false);
                reentrantSave(server, state, true);
                partialRewardComponents(server, state);
                commandTerminals(server, state);
                return null;
            });
            saveEvidence(server, state);
        } catch (IOException | RuntimeException | Error failure) {
            String detail = failure.getClass().getName() + ": " + failure.getMessage();
            failureDetail = detail.length() > 768 ? detail.substring(0, 768) : detail;
        } finally {
            active = null;
            // Removal is the real entity lifecycle terminal; no token release is called by the probe.
            if (state.victim != null && !state.victim.isRemoved()) {
                try { state.victim.remove(Entity.RemovalReason.DISCARDED); }
                catch (RuntimeException | Error cleanupFailure) {
                    state.observations.add("cleanup=FAILED_NATIVE_REMOVAL_" + cleanupFailure.getClass().getName());
                    if ("NONE".equals(failureDetail)) { failureDetail = "native fixture removal failed"; }
                }
            }
            if (state.marker != null && !state.marker.isRemoved()) {
                try { state.marker.discard(); }
                catch (RuntimeException | Error cleanupFailure) {
                    state.observations.add("markerCleanup=FAILED_NATIVE_REMOVAL_" + cleanupFailure.getClass().getName());
                    if ("NONE".equals(failureDetail)) { failureDetail = "native marker removal failed"; }
                }
            }
        }
        return new Report("NONE".equals(failureDetail), state.expectedFailures, state.observations, failureDetail);
    }

    private static void nativeCreditAndReward(MinecraftServer server, State state) {
        var original = state.actor;
        var owner = P11NativeStorageBoundary.nativeSourceOwner(original);
        require(owner != null && owner.nativeRecipient(original) != null, "missing current native source");
        var before = diagnostic(server, original);
        long nativeBefore = count(before, "NATIVE_CREDIT");
        state.observerFailures = P11NativeOperationBoundary.observerFailureCount();
        var advancement = server.getAdvancements().get(REWARD);
        require(advancement != null && server.getAdvancements().get(PARTIAL) != null
                        && server.getFunctions().get(id("reward")).isPresent(),
                "excluded native reward advancement/function pack was not loaded");
        require(!original.getAdvancements().getOrStartProgress(advancement).isDone(),
                "reward advancement was already complete");
        require(!original.getTags().contains(FUNCTION_TAG), "reward function already ran");
        prepareCloneCallback(server, state);
        // Known recipe removal goes through its native PA/book boundary before the baseline.
        var recipe = server.getRecipeManager().byKey(BREAD).orElseThrow();
        original.resetRecipes(List.of(recipe));
        var victim = EntityType.COW.create(original.serverLevel());
        require(victim != null, "native cow fixture unavailable");
        state.victim = victim;
        victim.moveTo(original.getX() + 2, original.getY(), original.getZ(), 0, 0);
        require(original.serverLevel().addFreshEntity(victim), "native cow insertion failed");
        require(victim.hurt(original.damageSources().playerAttack(original), 1.0F),
                "actual native player damage was not applied");
        require(victim.getLastHurtByMob() == original && victim.getKillCredit() == original
                        && victim.getLastDamageSource() != null
                        && victim.getLastDamageSource().getEntity() == original,
                "native damage fields do not retain exact A");
        require(count(diagnostic(server, original), "NATIVE_CREDIT") >= nativeBefore + 3,
                "three actual native player-bearing fields did not retain N");
        long epoch = before.sourceEpoch();
        int preparedXp = original.totalExperience, preparedScore = original.getScore();
        var connection = original.connection;
        require(!original.wonGame, "unexpected prior wonGame precondition");
        original.wonGame = true;
        state.preparedArmed = true;
        connection.handleClientCommand(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        state.preparedArmed = false;
        var recipient = server.getPlayerList().getPlayer(original.getUUID());
        require(recipient != null && recipient != original && recipient.connection == connection
                        && connection.player == recipient && connection.getConnection().isConnected()
                        && original.isRemoved() && !recipient.wonGame,
                "original handler failed to publish exact B on the same native host connection");
        require(state.preparedRecipeAttempts == 1 && state.preparedRecipeSubmissions == 1
                        && state.preparedRecipePacket == null,
                "legal prepared B did not submit its original recipe ADD exactly once before native copy overwrite");
        state.actor = recipient;
        require(state.preparedCalls == 1 && state.preparedFunctionCalls == 1
                        && recipient.totalExperience == preparedXp && recipient.getScore() == preparedScore
                        && !recipient.getRecipeBook().contains(BAKED_POTATO)
                        && recipient.getTags().contains(PREPARED_TAG) && !original.getTags().contains(PREPARED_TAG)
                        && recipient.getAdvancements().getOrStartProgress(server.getAdvancements().get(PREPARED)).isDone(),
                "prepared B reward/function or native XP/Score/recipe overwrite parity failed");
        state.observations.add("preparedB=ACTUAL_RESTOREFROM_EFFECT_CRITERION_XP23_RECIPE_FUNCTION_XP5_ON_PARTIAL_B_THEN_NATIVE_XP_SCORE_RECIPE_OVERWRITE");
        state.observations.add("preparedRecipe=EXACT_PREPARED_B_NATIVE_ADD_BAKED_POTATO_HEAD_AND_RETURN_ON_ORIGINAL_CONNECTION_BEFORE_COPY_OVERWRITE"
                + ";attempts=" + state.preparedRecipeAttempts + ";normalSubmissions=" + state.preparedRecipeSubmissions
                + ";clientReceiptClaimed=false");
        roots(state, server, recipient, "prepared-B-published");
        require(recipient.getAdvancements() == original.getAdvancements()
                        && recipient.getStats() == original.getStats()
                        && diagnostic(server, recipient).sourceEpoch() > epoch
                        && owner.nativeRecipient(original) == null,
                "canonical PA/stats or source epoch handoff is incorrect; naked stale A must not route");
        int originalXp = original.totalExperience, recipientXp = recipient.totalExperience;
        int originalScore = original.getScore(), recipientScore = recipient.getScore();
        int originalEmerald = original.getInventory().countItem(Items.EMERALD);
        int recipientEmerald = recipient.getInventory().countItem(Items.EMERALD);
        var context = new ExecutionContext<?>[1];
        state.scoreCause = original;
        var source = recipient.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        Commands.executeCommandInContext(source, nativeContext -> {
            context[0] = nativeContext;
            long operationBefore = count(diagnostic(server, recipient), "OPERATION");
            require(count(diagnostic(server, recipient), "COMMAND_CONTEXT") > 0,
                    "Qctx was not held before first native enqueue");
            // Native magic damage preserves player kill credit but overwrites lastDamageSource.
            require(victim.hurt(victim.damageSources().magic(), 1000.0F), "native lethal damage failed");
            require(victim.isDeadOrDying() && state.scoreCalls == 1,
                    "native death credit / narrow nonzero Score consumer was not reached");
            require(recipient.totalExperience == recipientXp + 37 && original.totalExperience == originalXp,
                    "whole reward XP did not use fixed B before its function drained");
            require(recipient.getInventory().countItem(Items.EMERALD) == recipientEmerald + 1
                            && original.getInventory().countItem(Items.EMERALD) == originalEmerald
                            && recipient.getRecipeBook().contains(BREAD),
                    "whole reward loot or recipe did not use B");
            require(!recipient.getTags().contains(FUNCTION_TAG)
                            && count(diagnostic(server, recipient), "OPERATION") == operationBefore
                            && count(diagnostic(server, recipient), "COMMAND_CONTEXT") > 0,
                    "grant return drained the reused engine or leaked Fop instead of Qctx");
            nativeContext.close();
            require(count(diagnostic(server, recipient), "COMMAND_CONTEXT") > 0
                            && !P11NativeOperationBoundary.observe(nativeContext).terminal(),
                    "tracer-only close incorrectly terminated Qctx");
        });
        state.scoreCause = null;
        require(recipient.totalExperience == recipientXp + 40 && recipient.getTags().contains(FUNCTION_TAG)
                        && original.totalExperience == originalXp && original.getScore() == originalScore
                        && recipient.getScore() == recipientScore + 51,
                "B reward function/XP or exact Score delta failed; A must remain unchanged");
        require(recipient.getAdvancements().getOrStartProgress(advancement).isDone(),
                "native listener/PA advancement did not complete");
        terminal(state, context[0], "EMPTY", true);
        victim.remove(Entity.RemovalReason.DISCARDED);
        require(count(diagnostic(server, recipient), "NATIVE_CREDIT") == nativeBefore,
                "normal native victim removal did not release its retained fields");
        requireNoOperations(server, recipient);
        roots(state, server, recipient, "native-N-consumed");
        state.observations.add("nativeN=A_HURT_FIELDS_TO_RESPAWN_B_TO_DEATH_TRUE_FINALLY_RELEASE");
        state.observations.add("reward=XP37_LOOT_EMERALD_RECIPE_BREAD_FUNCTION_XP3_TAG_ON_B_ONLY");
        state.observations.add("score=EXACT_NATIVE_AWARD_KILL_SCORE_INVOKE_11_ON_B_ONLY");
    }

    private static void prepareCloneCallback(MinecraftServer server, State state) {
        var actor = state.actor;
        var prepared = server.getAdvancements().get(PREPARED);
        require(prepared != null && server.getFunctions().get(id("prepared_reward")).isPresent()
                        && !actor.hasEffect(MobEffects.LUCK), "missing clean prepared-B fixture");
        require(actor.addEffect(new MobEffectInstance(MobEffects.LUCK, 400)), "native effect setup failed");
        require(actor.getAdvancements().getOrStartProgress(prepared).isDone(), "native effect criterion setup failed");
        require(actor.getAdvancements().revoke(prepared, "native_effect"), "native prepared criterion reset failed");
        actor.resetRecipes(List.of(server.getRecipeManager().byKey(BAKED_POTATO).orElseThrow()));
        require(actor.removeTag(PREPARED_TAG) && !actor.getRecipeBook().contains(BAKED_POTATO),
                "native prepared fixture reset failed");
        requireNoOperations(server, actor);
    }

    private static void nestedRewards(MinecraftServer server, State state) {
        var actor = state.actor;
        var source = actor.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        int xp = actor.totalExperience;
        var held = new ExecutionContext<?>[1];
        Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            long outer = count(diagnostic(server, actor), "OPERATION");
            long qctx = count(diagnostic(server, actor), "COMMAND_CONTEXT");
            state.nestedArmed = true;
            try { AdvancementRewards.Builder.experience(13).build().grant(actor); }
            finally { state.nestedArmed = false; }
            require(state.nestedCalls == 1 && actor.totalExperience == xp + 20
                            && count(diagnostic(server, actor), "OPERATION") == outer
                            && count(diagnostic(server, actor), "COMMAND_CONTEXT") == qctx,
                    "nested grant must retain outer scope and deduplicate the existing Qctx");
            roots(state, server, actor, "nested-grant-return-before-drain");
        });
        require(actor.totalExperience == xp + 25, "nested reward function did not drain exactly once");
        terminal(state, held[0], "EMPTY", true);
        requireNoOperations(server, actor);
        state.observations.add("nestedReward=REAL_XP_EVENT_INNER_GRANT_XP7_FUNCTION_ENQUEUE_OUTER_XP13_THEN_FUNCTION_XP5_SAME_QCTX");
    }

    private static void creditConsumerDeferral(MinecraftServer server, State state) {
        for (boolean throwsAfterRemoval : new boolean[] {false, true}) {
            var actor = state.actor;
            long baseline = count(diagnostic(server, actor), "NATIVE_CREDIT");
            var victim = EntityType.COW.create(actor.serverLevel());
            require(victim != null, "missing native consumer fixture");
            state.victim = victim;
            victim.moveTo(actor.getX() + 2, actor.getY(), actor.getZ(), 0, 0);
            require(actor.serverLevel().addFreshEntity(victim)
                            && victim.hurt(actor.damageSources().playerAttack(actor), 1.0F),
                    "native consumer fixture did not acquire actual damage fields");
            state.deferralBaseline = baseline;
            state.deferralArmed = true;
            state.deferralThrow = throwsAfterRemoval;
            int calls = state.deferralCalls;
            try {
                if (throwsAfterRemoval) {
                    expectSentinel(state, () -> victim.hurt(victim.damageSources().magic(), 1000.0F));
                    state.expectedFailures++;
                } else {
                    require(victim.hurt(victim.damageSources().magic(), 1000.0F), "native deferral death failed");
                }
            } finally { state.deferralArmed = false; state.deferralThrow = false; }
            require(state.deferralCalls == calls + 1 && victim.isRemoved()
                            && count(diagnostic(server, actor), "NATIVE_CREDIT") == baseline,
                    "final native die finally failed to release the removed holder exactly once");
            requireNoOperations(server, actor);
            roots(state, server, actor, throwsAfterRemoval ? "removed-die-throw-finally" : "removed-die-normal-finally");
        }
        state.observations.add("creditDeferral=REAL_DIE_EVENT_DISCARD_POST_REMOVE_EXACT_MOB_FIELD_CONSUMER_NORMAL_AND_SAME_PRIMARY_THROW_FINALLY_ROOT_ZERO");
    }

    private static void commandTargets(MinecraftServer server, State state) {
        var actor = state.actor;
        var level = actor.serverLevel();
        var origin = actor.position();
        var expected = origin.add(4, 2, 6);
        String markerTag = "p11_native_context_owned_marker";
        require(level.getEntities(EntityType.MARKER, entity -> entity.getTags().contains(markerTag)).isEmpty(),
                "owned native command marker already exists");
        var source = actor.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var function = CommandFunction.fromLines(id("native_targets"), server.getCommands().getDispatcher(), source,
                List.of("execute as @s at @s positioned ~4 ~2 ~6 run summon minecraft:marker ~ ~ ~ {Tags:[\""
                                + markerTag + "\"]}",
                        "execute as @e[type=minecraft:marker,tag=" + markerTag
                                + ",limit=1] at @s run tag @s add p11_native_nonplayer_executor"));
        int retained = diagnostic(server, actor).resources().retainedUuids();
        var held = new ExecutionContext<?>[1];
        Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            server.getFunctions().execute(function, source);
            context.queueNext(new net.minecraft.commands.execution.CommandQueueEntry<>(
                    new net.minecraft.commands.execution.Frame(0, net.minecraft.commands.CommandResultCallback.EMPTY, () -> {}),
                    (queue, frame) -> {
                        var found = level.getEntities(EntityType.MARKER, entity -> entity.getTags().contains(markerTag));
                        require(found.size() == 1, "native as/at/positioned function did not create exactly one marker");
                        state.marker = found.getFirst();
                        require(!state.marker.getUUID().equals(actor.getUUID())
                                        && state.marker.getTags().contains("p11_native_nonplayer_executor")
                                        && state.marker.position().distanceToSqr(expected) < 1.0E-10,
                                "derived native executor or coordinates were rewritten to the player");
                        require(actor.position().equals(origin)
                                        && diagnostic(server, actor).resources().retainedUuids() == retained
                                        && count(diagnostic(server, actor), "COMMAND_CONTEXT") == 1
                                        && P11NativeOperationBoundary.observe(context).retainedBindings() == 1,
                                "non-player executor invented an account or changed original host/Qctx ownership");
                    }));
        });
        terminal(state, held[0], "EMPTY", true);
        require(state.marker != null && actor.position().equals(origin), "native target observation did not run");
        state.marker.discard();
        require(state.marker.isRemoved() && level.getEntity(state.marker.getUUID()) == null,
                "owned marker did not reach normal native removal");
        state.marker = null;
        requireNoOperations(server, actor);
        state.observations.add("nativeCommandTargets=REAL_INTEGRATED_HOST_AS_AT_POSITIONED_AND_DISTINCT_NONPLAYER_EXECUTOR_COORDINATES_PRESERVED_ONE_ACCOUNT_QCTX_NATIVE_MARKER_CLEANUP");
    }

    private static void partialMutations(MinecraftServer server, State state) {
        var actor = state.actor;
        var partial = server.getAdvancements().get(PARTIAL);
        require(partial != null, "missing partial advancement");
        int xpBefore = actor.totalExperience;
        state.progressThrow = AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT;
        expectSentinel(state, () -> actor.getAdvancements().award(partial, "manual"));
        state.progressThrow = null;
        state.expectedFailures++;
        require(actor.getAdvancements().getOrStartProgress(partial).isDone()
                        && actor.totalExperience == xpBefore,
                "partial award must preserve criterion prefix without retrying reward");
        requireDirty(server, actor, "ADVANCEMENTS");
        state.progressThrow = AdvancementEvent.AdvancementProgressEvent.ProgressType.REVOKE;
        expectSentinel(state, () -> actor.getAdvancements().revoke(partial, "manual"));
        state.progressThrow = null;
        state.expectedFailures++;
        require(!actor.getAdvancements().getOrStartProgress(partial).isDone(),
                "partial revoke mutation was rolled back or lost");
        requireDirty(server, actor, "ADVANCEMENTS");
        int amount = actor.getXpNeededForNextLevel() + 1;
        state.levelThrow = true;
        expectSentinel(state, () -> new AdvancementRewards(amount, List.of(), List.of(), Optional.empty()).grant(actor));
        state.levelThrow = false;
        state.expectedFailures++;
        require(actor.totalExperience == xpBefore + amount,
                "XP mutation prefix before native LevelChange throw was not preserved");
        requireDirty(server, actor, "PLAYER_DATA");
        requireNoOperations(server, actor);
        state.observations.add("partial=AWARD_CRITERION_AND_REVOKE_AND_XP_PREFIX_DIRTY_NO_RETRY_NO_ROLLBACK");
    }

    private static void partialRewardComponents(MinecraftServer server, State state) {
        var actor = state.actor;
        require(server.getFunctions().get(PARTIAL_FUNCTION).isPresent()
                        && !actor.getTags().contains(PARTIAL_PREFIX) && !actor.getTags().contains(PARTIAL_TAIL)
                        && P11NativeStorageBoundary.nativeDeliveryEligible(actor),
                "partial reward requires its clean native function and eligible recipient");
        var recipe = server.getRecipeManager().byKey(BREAD).orElseThrow();
        actor.resetRecipes(List.of(recipe));
        require(!actor.getRecipeBook().contains(BREAD), "native recipe reset failed before partial reward");
        var reward = AdvancementRewards.Builder.experience(3)
                .addLootTable(ResourceKey.create(Registries.LOOT_TABLE, REWARD))
                .addRecipe(BREAD).runs(PARTIAL_FUNCTION).build();
        int xp = actor.totalExperience;
        int emeralds = actor.getInventory().countItem(Items.EMERALD);
        state.lootFaultArmed = true;
        try { expectExactSentinel(state, () -> reward.grant(actor)); }
        finally { state.lootFaultArmed = false; }
        state.expectedFailures++;
        require(state.lootFaults == 1 && actor.totalExperience == xp + 3
                        && actor.getInventory().countItem(Items.EMERALD) == emeralds + 1
                        && !actor.getRecipeBook().contains(BREAD) && !actor.getTags().contains(PARTIAL_PREFIX),
                "loot/menu fault lost native XP/inventory prefix or continued recipe/function tail");
        requireDirty(server, actor, "PLAYER_DATA");
        requireNoOperations(server, actor);
        state.observations.add("partialLoot=REAL_GRANT_XP3_EMERALD1_MENU_RETURN_SAME_SENTINEL_RECIPE_AND_FUNCTION_UNREACHED_NO_ROLLBACK");

        state.recipeFaultArmed = true;
        try { expectExactSentinel(state, () -> reward.grant(actor)); }
        finally { state.recipeFaultArmed = false; }
        state.expectedFailures++;
        require(state.recipeFaults == 1 && actor.totalExperience == xp + 6
                        && actor.getInventory().countItem(Items.EMERALD) == emeralds + 2
                        && actor.getRecipeBook().contains(BREAD) && !actor.getTags().contains(PARTIAL_PREFIX),
                "recipe send fault lost native logical/XP/loot prefix or continued function tail");
        requireDirty(server, actor, "PLAYER_DATA");
        requireNoOperations(server, actor);
        state.observations.add("partialRecipe=REAL_KNOWN_HIGHLIGHT_AND_CRITERION_THEN_ELIGIBLE_ADD_SEND_SAME_SENTINEL_XP3_LOOT1_FUNCTION_UNREACHED");

        var functionReward = AdvancementRewards.Builder.experience(2).runs(PARTIAL_FUNCTION).build();
        state.functionProbe = true;
        state.functionFaultArmed = true;
        int functionXp = actor.totalExperience;
        try { functionReward.grant(actor); } // Native manager catches Exception; this MUST return normally.
        finally { state.functionFaultArmed = false; }
        state.expectedFailures += 2;
        require(state.functionFaults == 1 && state.functionCommands == 1 && state.functionCaught == state.sentinel
                        && state.functionCatchCount == 1 && actor.totalExperience == functionXp + 6
                        && actor.getTags().contains(PARTIAL_PREFIX) && !actor.getTags().contains(PARTIAL_TAIL),
                "fresh function native Exception catch lost same primary/prefix or ran later command");
        terminal(state, state.functionContext, "THREW", false);
        requireNoOperations(server, actor);
        state.observations.add("partialFunctionFresh=REAL_REWARD_FUNCTION_TAG_XP4_THEN_RUNTIME_SENTINEL_NATIVE_MANAGER_CAUGHT_SAME_OBJECT_GRANT_NORMAL_TAIL_NOT_RUN");

        require(actor.removeTag(PARTIAL_PREFIX), "fresh function prefix reset failed");
        var source = actor.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        int beforeReused = actor.totalExperience;
        var held = new ExecutionContext<?>[1];
        state.functionFaultArmed = true;
        try {
            expectExactSentinel(state, () -> Commands.executeCommandInContext(source, context -> {
                held[0] = context;
                functionReward.grant(actor);
                require(actor.totalExperience == beforeReused + 2 && !actor.getTags().contains(PARTIAL_PREFIX)
                                && state.functionCommands == 1 && count(diagnostic(server, actor), "COMMAND_CONTEXT") > 0,
                        "reused-context reward drained function or lost context before grant returned");
            }));
        } finally { state.functionFaultArmed = false; }
        state.expectedFailures += 2;
        require(state.functionContext == held[0] && state.functionFaults == 2 && state.functionCommands == 2
                        && state.functionCatchCount == 1 && actor.totalExperience == beforeReused + 6
                        && actor.getTags().contains(PARTIAL_PREFIX) && !actor.getTags().contains(PARTIAL_TAIL),
                "reused real reward function lost same primary/prefix or replayed function tail");
        terminal(state, held[0], "THREW", false);
        requireDirty(server, actor, "PLAYER_DATA");
        requireNoOperations(server, actor);
        // A new real owning drain cannot revive either failed function's leftover tail.
        Commands.executeCommandInContext(source, context -> {});
        require(state.functionCommands == 2 && actor.totalExperience == beforeReused + 6
                        && !actor.getTags().contains(PARTIAL_TAIL), "failed reward function replayed on later drain");
        state.partialEmeralds = emeralds + 2;
        state.partialXp = actor.totalExperience;
        state.functionContext = null;
        state.observations.add("partialFunctionReused=GRANT_RETURN_BEFORE_REAL_FUNCTION_DRAIN_SAME_RUNTIME_SENTINEL_ESCAPES_OWNING_FINALLY_PREFIX_RETAINED_EMPTY_NEW_DRAIN_NO_REPLAY");
    }

    private static void reentrantSave(MinecraftServer server, State state, boolean queued) {
        var actor = state.actor;
        var label = queued ? "qctx" : "fop";
        state.reentrantAmount = queued ? 19 : 17;
        state.reentrantQueued = queued;
        state.reentrantSaved = null;
        state.reentrantCalls = 0;
        state.reentrantArmed = true;
        int beforeXp = actor.totalExperience;
        try {
            if (!queued) {
                AdvancementRewards.Builder.experience(state.reentrantAmount).build().grant(actor);
            } else {
                var playerSource = actor.createCommandSourceStack().withPermission(2).withSuppressedOutput();
                var enrollment = CommandFunction.fromLines(id("native_reentrant_enrollment"),
                        server.getCommands().getDispatcher(), playerSource,
                        List.of("tag @s add p11_native_reentrant_enrollment"));
                var held = new ExecutionContext<?>[1];
                // The outer console source has no player/Fop. The real function invocation
                // enrolls this player in the existing native context before returning.
                Commands.executeCommandInContext(server.createCommandSourceStack(), context -> {
                    held[0] = context;
                    server.getFunctions().execute(enrollment, playerSource);
                    context.queueNext(new net.minecraft.commands.execution.CommandQueueEntry<>(
                            new net.minecraft.commands.execution.Frame(0,
                                    net.minecraft.commands.CommandResultCallback.EMPTY, () -> {}),
                            (queue, frame) -> actor.giveExperiencePoints(state.reentrantAmount)));
                });
                terminal(state, held[0], "EMPTY", true);
                require(actor.getTags().contains("p11_native_reentrant_enrollment"),
                        "real command context enrollment function did not execute");
            }
        } finally { state.reentrantArmed = false; }
        var after = diagnostic(server, actor);
        require(state.reentrantCalls == 1 && state.reentrantSaved != null
                        && actor.totalExperience == beforeXp + state.reentrantAmount
                        && after.sourceEpoch() == state.reentrantSaved.sourceEpoch()
                        && after.sourceVersion() > state.reentrantSaved.sourceVersion()
                        && after.resources().dirtyUuids() > 0,
                "native " + label + " tail reused the callback's prefix-only persistence proof");
        for (var kind : List.of("PLAYER_DATA", "LEVEL_PLAYER")) {
            var prefix = writer(state.reentrantSaved, kind);
            var tail = writer(after, kind);
            require(!prefix.dirty() && "COMPLETED".equals(prefix.terminal())
                            && tail.dirty() && tail.attempt() > prefix.attempt(),
                    "native " + label + " tail did not invalidate physical " + kind + " generation");
        }
        requireNoOperations(server, actor);
        try {
            write(state.output.resolve("reentrant-" + label + "-after-tail.txt"), after + "\n");
            require(server.saveEverything(true, false, false), "native tail save failed");
            var saved = diagnostic(server, actor);
            require(saved.sourceVersion() == after.sourceVersion() && saved.resources().dirtyUuids() == 0,
                    "original tail save did not discharge exact current responsibility");
            requireNativeXpReadback(server, actor, actor.totalExperience);
            write(state.output.resolve("reentrant-" + label + "-after-save.txt"), saved + "\n");
            var playerPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
            Files.copy(playerPath, state.output.resolve("reentrant-" + label + "-tail-player.dat"));
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        state.observations.add("reentrantSave=" + label + ":ACTUAL_XP_CALLBACK_ORIGINAL_SAVE_OLD_XP_CLEAN_PHYSICAL_GENERATION_"
                + "THEN_NATIVE_XP_TAIL_NEW_VERSION_DIRTY_THEN_ORIGINAL_SAVE_PLAYER_LEVEL_CACHE_READBACK_EXACT_XP");
        state.reentrantSaved = null;
    }

    private static P11QualifiedSourceOwner.WriterDiagnostic writer(
            P11QualifiedSourceOwner.Diagnostics diagnostic, String kind) {
        return diagnostic.writers().stream().filter(value -> value.kind().equals(kind)).findFirst().orElseThrow();
    }

    private static void requireNativeXpReadback(MinecraftServer server, ServerPlayer actor, int expected) throws IOException {
        var player = NbtIo.readCompressed(server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(actor.getUUID() + ".dat"), NbtAccounter.create(32L * 1024 * 1024));
        var level = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT).resolve("level.dat"),
                NbtAccounter.create(32L * 1024 * 1024));
        var cache = server.getPlayerList().getSingleplayerData();
        require(player.getInt("XpTotal") == expected && cache != null && cache.getInt("XpTotal") == expected
                        && level.getCompound("Data").getCompound("Player").getInt("XpTotal") == expected,
                "original player/level/cache readback differs from native XP prefix or tail");
    }

    public static void afterLootMenu(ServerPlayer actor) {
        var state = active;
        if (state != null && actor == state.actor && state.lootFaultArmed) {
            state.lootFaultArmed = false;
            state.lootFaults++;
            throw state.sentinel;
        }
    }

    public static void beforeRecipeSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var state = active;
        if (state != null && state.preparedArmed && listener == state.actor.connection
                && packet instanceof ClientboundRecipePacket recipe
                && recipe.getState() == ClientboundRecipePacket.State.ADD && recipe.getRecipes().contains(BAKED_POTATO)) {
            requirePreparedRecipeReceiver(state, listener);
            require(state.preparedRecipePacket == null && state.preparedRecipeAttempts == 0,
                    "prepared B recipe ADD was duplicated or overlapped");
            state.preparedRecipeAttempts++;
            state.preparedRecipePacket = packet;
        }
        if (state != null && state.recipeFaultArmed && listener == state.actor.connection
                && packet instanceof ClientboundRecipePacket recipe && recipe.getState() == ClientboundRecipePacket.State.ADD
                && recipe.getRecipes().contains(BREAD)) {
            require(state.actor.getRecipeBook().contains(BREAD)
                            && P11NativeStorageBoundary.nativeDeliveryEligible(state.actor),
                    "recipe fault did not reach actual eligible post-mutation native sink");
            state.recipeFaultArmed = false;
            state.recipeFaults++;
            throw state.sentinel;
        }
    }

    public static void afterRecipeSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var state = active;
        if (state == null || packet != state.preparedRecipePacket) { return; }
        require(state.preparedArmed && listener == state.actor.connection,
                "prepared recipe transport returned outside its exact native constructor callback");
        requirePreparedRecipeReceiver(state, listener);
        state.preparedRecipeSubmissions++;
        state.preparedRecipePacket = null;
    }

    private static void requirePreparedRecipeReceiver(State state, ServerCommonPacketListenerImpl listener) {
        var prepared = ((P11CanonicalAdvancements.Access) state.actor.getAdvancements()).p11$associatedPlayer();
        var source = P11NativeStorageBoundary.nativeSourceOwner(prepared);
        var body = source == null ? null : source.nativeRecipient(prepared);
        require(prepared != state.actor && prepared.getUUID().equals(state.actor.getUUID())
                        && prepared.connection == listener && prepared.connection.player == state.actor
                        && body != null && body.actor == prepared && !body.complete
                        && prepared.getRecipeBook().contains(BAKED_POTATO)
                        && P11NativeStorageBoundary.nativeDeliveryEligible(prepared),
                "recipe observation was not the exact eligible prepared-B logical mutation before native overwrite");
    }

    public static void afterFunctionCommand(ExecutionCommandSource<?> source, ExecutionContext<?> context, String command) {
        var state = active;
        if (state != null && state.functionProbe && source instanceof CommandSourceStack nativeSource
                && nativeSource.getEntity() == state.actor && PARTIAL_COMMAND.equals(command)) {
            state.functionCommands++;
            if (state.functionFaultArmed) {
                state.functionFaultArmed = false;
                state.functionFaults++;
                state.functionContext = context;
                throw state.sentinel;
            }
        }
    }

    public static void caughtFunction(Object function, Object failure) {
        var state = active;
        if (state != null && state.functionProbe && PARTIAL_FUNCTION.equals(function)) {
            state.functionCatchCount++;
            state.functionCaught = failure;
        }
    }

    private static void commandTerminals(MinecraftServer server, State state) {
        var actor = state.actor;
        var source = actor.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var held = new ExecutionContext<?>[1];
        Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            require(count(diagnostic(server, actor), "COMMAND_CONTEXT") > 0, "empty context lacks Qctx");
        });
        terminal(state, held[0], "EMPTY", true);
        var returnFunction = CommandFunction.fromLines(id("native_return"), server.getCommands().getDispatcher(),
                source, List.of("tag @s add p11_native_before_return", "return 1", "tag @s add p11_native_after_return"));
        Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            server.getFunctions().execute(returnFunction, source);
        });
        require(actor.getTags().contains("p11_native_before_return")
                        && !actor.getTags().contains("p11_native_after_return"), "native function return failed");
        terminal(state, held[0], "EMPTY", true);
        int quota = server.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH);
        require(quota > 0 && quota <= 1_000_000, "engineering quota probe is resource bounded");
        Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            server.getFunctions().execute(returnFunction, source);
            // Consume the actual engine's own cost, without changing gamerules or production limits.
            for (int i = 0; i < quota; i++) { context.incrementCost(); }
        });
        terminal(state, held[0], "QUOTA", true);
        Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            // Native queueNext accepts repeated immutable entry references. This crosses its
            // real >10,000,000 check without ten million distinct actions/frames or reflection.
            var sameEntry = new net.minecraft.commands.execution.CommandQueueEntry<net.minecraft.commands.CommandSourceStack>(
                    new net.minecraft.commands.execution.Frame(0, net.minecraft.commands.CommandResultCallback.EMPTY, () -> {}),
                    (queue, frame) -> { throw new IllegalStateException("overflow-cleared entry executed"); });
            for (int i = 0; i < 10_000_002; i++) { context.queueNext(sameEntry); }
            require(count(diagnostic(server, actor), "COMMAND_CONTEXT") > 0,
                    "overflow queue clear was mistaken for owning outer finally");
        });
        terminal(state, held[0], "OVERFLOW", true);
        expectSentinel(state, () -> Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            actor.addTag("p11_native_before_consumer_throw");
            throw state.sentinel;
        }));
        state.expectedFailures += 2;
        terminal(state, held[0], "NOT_RUN", false);
        require(actor.getTags().contains("p11_native_before_consumer_throw"), "native consumer prefix missing");
        // This original queued action throws from runCommandQueue, after a native mutation.
        var throwing = CommandFunction.fromLines(id("native_throw"), server.getCommands().getDispatcher(), source,
                List.of("tag @s add p11_native_before_action_throw"));
        expectSentinel(state, () -> Commands.executeCommandInContext(source, context -> {
            held[0] = context;
            server.getFunctions().execute(throwing, source);
            context.queueNext(new net.minecraft.commands.execution.CommandQueueEntry<>(
                    new net.minecraft.commands.execution.Frame(0, net.minecraft.commands.CommandResultCallback.EMPTY, () -> {}),
                    (queue, frame) -> { actor.addTag("p11_native_action_prefix"); throw state.sentinel; }));
        }));
        state.expectedFailures += 2;
        terminal(state, held[0], "THREW", false);
        require(actor.getTags().contains("p11_native_action_prefix"), "actual queued action prefix missing");
        requireNoOperations(server, actor);
        require(P11NativeOperationBoundary.observerFailureCount() == state.observerFailures,
                "native observer failed during component probes");
        state.observations.add("Qctx=REUSED_FUNCTION_GRANT_RETURN_CLOSE_EMPTY_RETURN_QUOTA_OVERFLOW_CONSUMER_THROW_ACTION_THROW");
    }

    private static void saveEvidence(MinecraftServer server, State state) throws IOException {
        var actor = state.actor;
        var before = diagnostic(server, actor);
        write(state.output.resolve("before-save.txt"), before + "\n");
        require(server.saveEverything(true, false, false), "native save did not return normally");
        var saved = diagnostic(server, actor);
        write(state.output.resolve("after-save.txt"), saved + "\n");
        require(saved.sourceEpoch() == before.sourceEpoch() && saved.resources().dirtyUuids() == 0,
                "native writer did not discharge current-source dirty responsibility");
        for (var writer : saved.writers()) {
            require(!writer.dirty() && "COMPLETED".equals(writer.terminal()),
                    "native writer completion absent: " + writer);
        }
        var playerPath = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
        var advancementPath = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(actor.getUUID() + ".json");
        var player = NbtIo.readCompressed(playerPath, NbtAccounter.create(32L * 1024 * 1024));
        require(player.getInt("XpTotal") == actor.totalExperience && player.getInt("Score") == actor.getScore(),
                "actual player file does not contain latest B XP/Score");
        require(actor.totalExperience == state.partialXp && state.lootFaults == 1 && state.recipeFaults == 1
                        && state.functionCommands == 2 && state.functionFaults == 2 && state.functionCatchCount == 1,
                "partial reward unexpectedly replayed before physical save");
        requirePartialReadback(server, state, player, "player.dat");
        var cache = server.getPlayerList().getSingleplayerData();
        require(cache != null && cache.getInt("XpTotal") == actor.totalExperience
                        && cache.getInt("Score") == actor.getScore(), "host cache does not contain latest B");
        requirePartialReadback(server, state, cache, "host cache");
        var levelPath = server.getWorldPath(LevelResource.ROOT).resolve("level.dat");
        var level = NbtIo.readCompressed(levelPath, NbtAccounter.create(32L * 1024 * 1024));
        require(level.getCompound("Data").getCompound("Player").getInt("XpTotal") == actor.totalExperience,
                "level.dat host player is not latest B");
        requirePartialReadback(server, state, level.getCompound("Data").getCompound("Player"), "level.dat");
        var advancementJson = JsonParser.parseString(Files.readString(advancementPath, StandardCharsets.UTF_8)).getAsJsonObject();
        require(advancementJson.has(REWARD.toString())
                        && advancementJson.getAsJsonObject(REWARD.toString()).get("done").getAsBoolean(),
                "canonical advancement file is missing the native reward completion");
        Files.copy(playerPath, state.output.resolve("latest-B-player.dat"));
        Files.copy(advancementPath, state.output.resolve("canonical-advancements.json"));
        Files.copy(levelPath, state.output.resolve("latest-B-level.dat"));
        state.observations.add("save=ORIGINAL_WRITERS_CURRENT_B_PLAYER_LEVEL_CACHE_CANONICAL_PA_PHYSICAL_FILES");
        state.observations.add("partialReadback=PLAYER_DAT_HOST_CACHE_LEVEL_DAT_NATIVE_ITEMSTACK_EMERALD_PLUS2_RECIPE_BREAD_FUNCTION_PREFIX_WITHOUT_TAIL_XP_EXACT_NO_REPLAY");
        roots(state, server, actor, "latest-physical-save");
    }

    private static void requirePartialReadback(MinecraftServer server, State state, CompoundTag saved, String source) {
        int emeralds = 0;
        var inventory = saved.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            var stack = ItemStack.parseOptional(server.registryAccess(), inventory.getCompound(i));
            if (stack.is(Items.EMERALD)) { emeralds += stack.getCount(); }
        }
        var tags = saved.getList("Tags", Tag.TAG_STRING);
        require(emeralds == state.partialEmeralds && saved.getInt("XpTotal") == state.partialXp
                        && saved.getCompound("recipeBook").getList("recipes", Tag.TAG_STRING).contains(StringTag.valueOf(BREAD.toString()))
                        && tags.contains(StringTag.valueOf(PARTIAL_PREFIX)) && !tags.contains(StringTag.valueOf(PARTIAL_TAIL)),
                "actual " + source + " lost partial reward prefix or persisted an unexecuted tail");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void progress(AdvancementEvent.AdvancementProgressEvent event) {
        var state = active;
        if (state != null && event.getEntity() == state.actor && event.getAdvancement().id().equals(PARTIAL)
                && event.getProgressType() == state.progressThrow) { throw state.sentinel; }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void level(PlayerXpEvent.LevelChange event) {
        var state = active;
        if (state != null && event.getEntity() == state.actor && state.levelThrow) { throw state.sentinel; }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void xp(PlayerXpEvent.XpChange event) {
        var state = active;
        if (state == null || !(event.getEntity() instanceof ServerPlayer actor)) { return; }
        var server = actor.getServer();
        if (actor == state.actor && state.reentrantArmed && event.getAmount() == state.reentrantAmount) {
            state.reentrantArmed = false;
            state.reentrantCalls++;
            var before = diagnostic(server, actor);
            require(state.reentrantQueued
                            ? count(before, "OPERATION") == 0 && count(before, "COMMAND_CONTEXT") > 0
                            : count(before, "OPERATION") > 0 && count(before, "COMMAND_CONTEXT") == 0,
                    "XP callback did not run under the intended actual Fop/Qctx responsibility");
            int prefixXp = actor.totalExperience;
            require(server.saveEverything(true, false, false), "original save inside XP callback failed");
            var saved = diagnostic(server, actor);
            require(saved.sourceVersion() == before.sourceVersion() && saved.resources().dirtyUuids() == 0
                            && actor.totalExperience == prefixXp,
                    "callback save did not genuinely persist and clean the pre-XP prefix");
            for (var value : saved.writers()) {
                require(!value.dirty() && "COMPLETED".equals(value.terminal()),
                        "callback prefix has no actual clean physical writer: " + value);
            }
            try {
                requireNativeXpReadback(server, actor, prefixXp);
                var label = state.reentrantQueued ? "qctx" : "fop";
                write(state.output.resolve("reentrant-" + label + "-callback-prefix.txt"), saved + "\n");
                Files.copy(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat"),
                        state.output.resolve("reentrant-" + label + "-prefix-player.dat"));
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            state.reentrantSaved = saved;
        }
        if (state.preparedArmed && actor != state.actor && actor.getUUID().equals(state.actor.getUUID())) {
            var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
            var body = owner == null ? null : owner.nativeRecipient(actor);
            require(body != null && body.actor == actor && !body.complete && actor.connection == state.actor.connection
                            && actor.connection.player == state.actor && count(diagnostic(server, actor), "OPERATION") > 0,
                    "native prepared callback was rerouted or given complete-body authority");
            if (event.getAmount() == 23) { state.preparedCalls++; }
            else if (event.getAmount() == 5) {
                state.preparedFunctionCalls++;
                require(count(diagnostic(server, actor), "COMMAND_CONTEXT") > 0,
                        "prepared reward new native context lacks Qctx before action");
            }
        }
        if (state.nestedArmed && actor == state.actor && event.getAmount() == 13) {
            state.nestedArmed = false;
            state.nestedCalls++;
            long outer = count(diagnostic(server, actor), "OPERATION");
            require(outer >= 2, "nested event lacks its original command/reward scopes");
            AdvancementRewards.Builder.experience(7).runs(id("prepared_reward")).build().grant(actor);
            require(count(diagnostic(server, actor), "OPERATION") == outer
                            && count(diagnostic(server, actor), "COMMAND_CONTEXT") > 0,
                    "inner reward finally released outer responsibility");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void death(LivingDeathEvent event) {
        var state = active;
        if (state != null && event.getEntity() == state.victim && state.deferralArmed) {
            var actor = state.actor;
            state.deferralCalls++;
            state.victim.discard();
            require(state.victim.isRemoved()
                            && count(diagnostic(actor.getServer(), actor), "NATIVE_CREDIT") > state.deferralBaseline,
                    "normal setRemoved released N before the actual die consumer stack ended");
            var access = (P11NativeOperationBoundary.CreditAccess) state.victim;
            var scope = access.p11$beginMobCredit(actor);
            require(scope != null, "removed native holder lost its exact later mob-field consumer");
            boolean normal = false;
            try {
                AdvancementRewards.Builder.experience(2).build().grant(actor);
                normal = true;
            } finally { P11NativeOperationBoundary.endCredit(scope, normal); }
            require(count(diagnostic(actor.getServer(), actor), "NATIVE_CREDIT") > state.deferralBaseline,
                    "nested consumer released outer native holder responsibility");
            if (state.deferralThrow) { throw state.sentinel; }
        }
        if (state != null && event.getEntity() == state.victim && state.scoreCause != null) {
            // Actual native method consumer inside actual N-derived die scope, with a nonzero amount.
            // No boundary callback is invoked and native surrounding stats/predicate semantics remain A.
            state.scoreCalls++;
            state.scoreCause.awardKillScore(state.victim, 11, event.getSource());
        }
    }

    private static void terminal(State state, ExecutionContext<?> context, String drain, boolean normal) {
        var observation = P11NativeOperationBoundary.observe(context);
        state.observations.add("context=" + observation);
        require(drain.equals(observation.drain()) && observation.terminal() && observation.outerNormal() == normal
                        && observation.tracerClosed() && observation.retainedBindings() == 0,
                "actual command engine terminal mismatch: " + observation);
    }

    private static void expectSentinel(State state, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | Error failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause == state.sentinel) { return; }
            }
            throw failure;
        }
        throw new IllegalStateException("expected original native callback throw did not escape");
    }

    private static void expectExactSentinel(State state, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            require(failure == state.sentinel, "native reward path replaced the exact primary exception");
            return;
        }
        throw new IllegalStateException("expected native reward component throw did not escape");
    }

    private static P11QualifiedSourceOwner.Diagnostics diagnostic(MinecraftServer server, ServerPlayer actor) {
        return P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
    }

    private static long count(P11QualifiedSourceOwner.Diagnostics value, String kind) {
        return value.nativeResponsibilities().roots().stream().filter(root -> root.kind().equals(kind))
                .findFirst().orElseThrow().count();
    }

    private static void roots(State state, MinecraftServer server, ServerPlayer actor, String stage) {
        var value = diagnostic(server, actor);
        for (var root : value.nativeResponsibilities().roots()) {
            require(root.count() >= 0 && root.accountPeakSum() >= root.count() && root.oldestAgeMillis() >= 0,
                    "native root diagnostic is inconsistent");
        }
        state.observations.add("rootMeasures=" + stage + ":e=" + value.sourceEpoch() + ":v="
                + value.sourceVersion() + ":" + value.nativeResponsibilities());
    }

    private static void requireDirty(MinecraftServer server, ServerPlayer actor, String kind) {
        var value = diagnostic(server, actor);
        require(value.resources().dirtyUuids() > 0 && value.writers().stream()
                        .anyMatch(writer -> writer.kind().equals(kind) && writer.dirty()),
                "native partial mutation was not independently dirty: " + kind);
    }

    private static void requireNoOperations(MinecraftServer server, ServerPlayer actor) {
        var value = diagnostic(server, actor);
        require(count(value, "OPERATION") == 0 && count(value, "COMMAND_CONTEXT") == 0,
                "native operation/context responsibility leaked after true finally");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", path); }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
    private static void write(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static final class State {
        ServerPlayer actor;
        final Path output;
        final List<String> observations = new ArrayList<>();
        final RuntimeException sentinel = new IllegalStateException("P11_NATIVE_OWNED_CALLBACK_THROW");
        Cow victim;
        Entity marker;
        ServerPlayer scoreCause;
        int scoreCalls, expectedFailures;
        int preparedCalls, preparedFunctionCalls, nestedCalls, deferralCalls;
        int preparedRecipeAttempts, preparedRecipeSubmissions;
        Packet<?> preparedRecipePacket;
        long observerFailures;
        long deferralBaseline;
        boolean levelThrow;
        boolean preparedArmed, nestedArmed, deferralArmed, deferralThrow;
        boolean lootFaultArmed, recipeFaultArmed, functionFaultArmed, functionProbe;
        int lootFaults, recipeFaults, functionFaults, functionCommands, functionCatchCount;
        int partialEmeralds, partialXp;
        boolean reentrantArmed, reentrantQueued;
        int reentrantAmount, reentrantCalls;
        P11QualifiedSourceOwner.Diagnostics reentrantSaved;
        Object functionCaught;
        ExecutionContext<?> functionContext;
        AdvancementEvent.AdvancementProgressEvent.ProgressType progressThrow;
        State(ServerPlayer actor, Path output) { this.actor = actor; this.output = output; }
    }
}
