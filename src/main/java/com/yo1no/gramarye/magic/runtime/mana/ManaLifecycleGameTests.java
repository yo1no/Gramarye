package com.yo1no.gramarye.magic.runtime.mana;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.Gramarye;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Direct server-bound GameTests for the permanent P6-S2 mana truth. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaLifecycleGameTests {
    private static final String PLAYER_MANA_KEY = "gramarye:player_mana";
    private static final ResourceLocation PLAYER_MANA_ID =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_mana");
    private static final UUID FRESH_PLAYER_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000001");
    private static final UUID VALID_SOURCE_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000002");
    private static final UUID VALID_TARGET_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000003");
    private static final UUID MALFORMED_PLAYER_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000004");
    private static final UUID DEATH_PLAYER_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000005");
    private static final UUID NON_DEATH_PLAYER_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000006");
    private static final UUID DIMENSION_PLAYER_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000007");
    private static final UUID SINGLE_TRUTH_PLAYER_ID =
            UUID.fromString("62000000-0000-4000-8000-000000000008");

    private ManaLifecycleGameTests() {}

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void newPlayerAbsentStateIsAvailableZero(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        var player = unplacedPlayer(server, FRESH_PLAYER_ID, "p6s2-fresh");

        helper.assertFalse(player.hasData(ManaAttachments.type()),
                "a new player must begin without a materialized mana Attachment");
        var state = ManaAttachments.state(player);
        assertAvailable(helper, state, 0L, "new-player default");
        helper.assertTrue(player.hasData(ManaAttachments.type()),
                "observing the absent mana truth must materialize the registered default");
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void validAttachmentSerializesAndLoadsExactly(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        var source = unplacedPlayer(server, VALID_SOURCE_ID, "p6s2-valid-a");
        ManaAttachments.replace(source, ManaState.available(731L));

        var root = source.saveWithoutId(new CompoundTag());
        var serialized = requireManaPayload(root);
        assertExactAvailableEncoding(helper, serialized, 731L);

        var target = unplacedPlayer(server, VALID_TARGET_ID, "p6s2-valid-b");
        var previousTargetState = ManaState.available(912L);
        ManaAttachments.replace(target, previousTargetState);
        var sourceState = ManaAttachments.state(source);
        target.copyAttachmentsFrom(source, false);
        var copiedTargetState = ManaAttachments.state(target);
        helper.assertTrue(copiedTargetState.equals(sourceState)
                        && copiedTargetState != sourceState
                        && copiedTargetState != previousTargetState,
                "Attachment copy must exactly replace the target truth with a fresh source copy");
        assertAvailable(helper, ManaAttachments.state(source), 731L,
                "unchanged copy source");

        target.load(root.copy());
        helper.assertTrue(target.hasData(ManaAttachments.type()),
                "valid serialized mana must install the registered Attachment");
        assertAvailable(helper, ManaAttachments.state(target), 731L,
                "valid in-memory save/load roundtrip");
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void malformedAttachmentRemainsUnavailableWithoutMutation(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        var player = unplacedPlayer(server, MALFORMED_PLAYER_ID, "p6s2-invalid");
        var malformed = new CompoundTag();
        malformed.putInt(ManaStateCodec.SCHEMA_VERSION_FIELD,
                ManaStateCodec.CURRENT_SCHEMA_VERSION);
        loadManaPayload(player, malformed);

        helper.assertTrue(player.hasData(ManaAttachments.type()),
                "present malformed mana must remain a materialized fail-closed state");
        var before = ManaAttachments.state(player);
        helper.assertTrue(before.availability() == ManaAvailability.UNAVAILABLE,
                "present malformed mana must not become the absent-state zero default");
        helper.assertTrue(before.unavailableReason() == ManaDecodeFailure.MISSING_BALANCE,
                "malformed fixture must retain its closed decode classification");

        var result = new ManaTransactionService().debit(
                new PlayerManaAccountAccess(player), ManaReason.SKILL_COST, 1L);
        helper.assertTrue(
                result instanceof ManaTransactionResult.Rejected rejected
                        && rejected.rejectReason() == ManaRejectReason.MANA_STATE_UNAVAILABLE,
                "malformed mana debit must reject as MANA_STATE_UNAVAILABLE");
        helper.assertTrue(ManaAttachments.state(player) == before,
                "rejected malformed-state debit must not replace or mutate the Attachment");
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 300)
    public static void deathCloneCopiesExactManaState(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        removeOnlinePlayer(server, DEATH_PLAYER_ID);
        var connected = placePlayer(server, DEATH_PLAYER_ID, "p6s2-death");
        try {
            var original = connected.player();
            ManaAttachments.replace(original, ManaState.available(814L));
            var sourceState = ManaAttachments.state(original);

            var replacement = server.getPlayerList().respawn(
                    original, false, Entity.RemovalReason.KILLED);
            replacement.connection.player = replacement;
            helper.assertTrue(replacement != original,
                    "death respawn must create a replacement ServerPlayer");
            helper.assertTrue(sourceState.equals(ManaAttachments.state(replacement)),
                    "copyOnDeath must preserve the exact mana state");
            assertAvailable(helper, ManaAttachments.state(replacement), 814L,
                    "death-clone mana");
        } finally {
            removeOnlinePlayer(server, DEATH_PLAYER_ID);
            connected.channel().finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 300)
    public static void nonDeathCloneCopiesExactManaState(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        removeOnlinePlayer(server, NON_DEATH_PLAYER_ID);
        var connected = placePlayer(server, NON_DEATH_PLAYER_ID, "p6s2-endclone");
        try {
            var original = connected.player();
            ManaAttachments.replace(original, ManaState.available(915L));
            var sourceState = ManaAttachments.state(original);

            var replacement = server.getPlayerList().respawn(
                    original, true, Entity.RemovalReason.CHANGED_DIMENSION);
            replacement.connection.player = replacement;
            helper.assertTrue(replacement != original,
                    "End-equivalent non-death respawn must replace the ServerPlayer");
            helper.assertTrue(sourceState.equals(ManaAttachments.state(replacement)),
                    "non-death clone must preserve the exact mana state");
            assertAvailable(helper, ManaAttachments.state(replacement), 915L,
                    "non-death-clone mana");
        } finally {
            removeOnlinePlayer(server, NON_DEATH_PLAYER_ID);
            connected.channel().finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 300)
    public static void dimensionTravelKeepsSingleManaTruth(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        var end = server.getLevel(Level.END);
        helper.assertTrue(end != null, "the GameTest server must expose the End level");
        removeOnlinePlayer(server, DIMENSION_PLAYER_ID);
        var connected = placePlayer(server, DIMENSION_PLAYER_ID, "p6s2-travel");
        try {
            var player = connected.player();
            helper.assertTrue(player.serverLevel() == server.overworld(),
                    "the dimension-travel fixture must begin in the Overworld");
            ManaAttachments.replace(player, ManaState.available(1_024L));
            var singleTruth = ManaAttachments.state(player);

            var transferred = player.changeDimension(new DimensionTransition(
                    end, player, DimensionTransition.DO_NOTHING));
            player.hasChangedDimension();
            helper.assertTrue(transferred == player && player.serverLevel() == end,
                    "ordinary dimension travel must retain the same ServerPlayer");
            helper.assertTrue(ManaAttachments.state(player) == singleTruth,
                    "ordinary dimension travel must retain the same mana Attachment truth");
            assertAvailable(helper, ManaAttachments.state(player), 1_024L,
                    "End dimension mana");

            var returned = player.changeDimension(new DimensionTransition(
                    server.overworld(), player, DimensionTransition.DO_NOTHING));
            player.hasChangedDimension();
            helper.assertTrue(returned == player && player.serverLevel() == server.overworld(),
                    "ordinary return travel must retain the same ServerPlayer");
            helper.assertTrue(ManaAttachments.state(player) == singleTruth,
                    "return travel must not create a dimension-keyed mana truth");
        } finally {
            removeOnlinePlayer(server, DIMENSION_PLAYER_ID);
            connected.channel().finishAndReleaseAll();
        }
        helper.succeed();
    }

    @GameTest(
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 200)
    public static void duplicatePersistentManaTruthIsAbsent(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        assertServerThread(helper, server);
        var type = ManaAttachments.type();
        helper.assertTrue(PLAYER_MANA_ID.equals(NeoForgeRegistries.ATTACHMENT_TYPES.getKey(type)),
                "the sole mana Attachment must have the exact gramarye:player_mana identity");
        helper.assertTrue(NeoForgeRegistries.ATTACHMENT_TYPES.get(PLAYER_MANA_ID) == type,
                "the exact mana registry identity must resolve to the production Attachment");

        var player = unplacedPlayer(server, SINGLE_TRUTH_PLAYER_ID, "p6s2-one-truth");
        var state = ManaState.available(2_048L);
        ManaAttachments.replace(player, state);
        helper.assertTrue(ManaAttachments.state(player) == state,
                "repeated account access must resolve the same player Attachment truth");
        var root = player.saveWithoutId(new CompoundTag());
        var attachments = requireAttachments(root);
        helper.assertTrue(attachments.getAllKeys().equals(Set.of(PLAYER_MANA_KEY)),
                "an otherwise fresh player must persist exactly one mana truth");
        helper.assertFalse(root.contains(ManaStateCodec.SCHEMA_VERSION_FIELD),
                "schema_version must not be duplicated outside the mana Attachment");
        helper.assertFalse(root.contains(ManaStateCodec.BALANCE_FIELD),
                "balance must not be duplicated outside the mana Attachment");
        assertExactAvailableEncoding(helper, requireManaPayload(root), 2_048L);
        helper.succeed();
    }

    private static void assertServerThread(GameTestHelper helper, MinecraftServer server) {
        helper.assertTrue(server.isSameThread(),
                "P6-S2 mana lifecycle GameTests must execute on the server thread");
    }

    private static void assertAvailable(
            GameTestHelper helper, ManaState state, long expectedBalance, String label) {
        helper.assertTrue(state.availability() == ManaAvailability.AVAILABLE,
                label + " must be AVAILABLE");
        helper.assertTrue(state.balance() == expectedBalance,
                label + " has the wrong balance");
    }

    private static void assertExactAvailableEncoding(
            GameTestHelper helper, Tag serialized, long expectedBalance) {
        helper.assertTrue(serialized instanceof CompoundTag,
                "valid mana serialization must have a compound root");
        var root = (CompoundTag) serialized;
        helper.assertTrue(root.getAllKeys().equals(Set.of(
                        ManaStateCodec.SCHEMA_VERSION_FIELD,
                        ManaStateCodec.BALANCE_FIELD)),
                "valid mana serialization must contain exactly two fields");
        helper.assertTrue(root.get(ManaStateCodec.SCHEMA_VERSION_FIELD)
                        instanceof IntTag version
                        && version.getAsInt() == ManaStateCodec.CURRENT_SCHEMA_VERSION,
                "schema_version must be exact signed int 0");
        helper.assertTrue(root.get(ManaStateCodec.BALANCE_FIELD)
                        instanceof LongTag balance
                        && balance.getAsLong() == expectedBalance,
                "balance must be an exact signed long");
    }

    private static void loadManaPayload(ServerPlayer player, Tag payload) {
        var root = player.saveWithoutId(new CompoundTag());
        var attachments = new CompoundTag();
        attachments.put(PLAYER_MANA_KEY, payload.copy());
        root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        player.load(root);
    }

    private static CompoundTag requireAttachments(CompoundTag root) {
        if (!(root.get(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                instanceof CompoundTag attachments)) {
            throw new AssertionError("serialized player has no Attachment container");
        }
        return attachments;
    }

    private static Tag requireManaPayload(CompoundTag root) {
        var payload = requireAttachments(root).get(PLAYER_MANA_KEY);
        if (payload == null) {
            throw new AssertionError("serialized player has no player_mana payload");
        }
        return payload;
    }

    private static ConnectedPlayer placePlayer(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(playerId, name), false);
        var player = new ServerPlayer(
                server,
                server.overworld(),
                cookie.gameProfile(),
                cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return new ConnectedPlayer(player, channel);
    }

    private static ServerPlayer unplacedPlayer(
            MinecraftServer server, UUID playerId, String name) {
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(playerId, name), false);
        return new ServerPlayer(
                server,
                server.overworld(),
                cookie.gameProfile(),
                cookie.clientInformation());
    }

    private static void removeOnlinePlayer(MinecraftServer server, UUID playerId) {
        var online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            server.getPlayerList().remove(online);
        }
    }

    private record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) {}
}
