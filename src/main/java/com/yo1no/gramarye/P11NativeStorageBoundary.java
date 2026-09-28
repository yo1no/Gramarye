package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.runtime.mana.P11ManaMaterial;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Internal locked-native callsites only. Installation is root-package restricted. This is a
 * delegate to the root's one foundation slot, not a service registry or an actor locator.
 * Public visibility is necessary after Mixin moves handlers into the named native classes.
 * No public method accepts scalar facts as a persisted/save grant.
 */
public final class P11NativeStorageBoundary {
    private static volatile P11FoundationService root;
    private static final ThreadLocal<LoadScope> LOAD = new ThreadLocal<>();
    private static final ThreadLocal<SerializeScope> SERIALIZE = new ThreadLocal<>();
    private static final ThreadLocal<WriteScope> WRITE = new ThreadLocal<>();
    private static final ThreadLocal<WriteScope> IO = new ThreadLocal<>();
    private static final ThreadLocal<CopyScope> COPY = new ThreadLocal<>();
    private static final ThreadLocal<LevelReadScope> LEVEL_READ = new ThreadLocal<>();
    private static final ThreadLocal<CacheScope> CACHE = new ThreadLocal<>();
    private static final ThreadLocal<PrimaryReadRequest> PRIMARY_READ = new ThreadLocal<>();
    private static final ThreadLocal<ConfigurationScope> CONFIGURATION = new ThreadLocal<>();
    private static final ThreadLocal<P11QualifiedSourceOwner.Body> SELECTED_SERIALIZE = new ThreadLocal<>();
    private static final ResourceLocation SKILLS = ResourceLocation.fromNamespaceAndPath("gramarye", "player_skills");
    private static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath("gramarye", "player_mana");
    private static long observerFailures;

    private P11NativeStorageBoundary() {}

    static void install(P11FoundationService exactRoot) {
        if (root != null && root != exactRoot) { throw new IllegalStateException("P11_ROOT_ALREADY_INSTALLED"); }
        root = exactRoot;
    }

    private static P11QualifiedSourceOwner owner(ServerPlayer player) {
        return root == null ? null : root.sourceOwner(player.getServer());
    }

    static P11QualifiedSourceOwner.Diagnostics diagnostics(MinecraftServer server, java.util.UUID playerId) {
        var source = root == null ? null : root.sourceOwner(server);
        if (source == null) { throw new IllegalStateException("P11_SOURCE_NOT_ACTIVE"); }
        return source.diagnostics(playerId);
    }

    static P11QualifiedSourceOwner.Summary terminalDiagnostics() {
        return root == null ? null : root.terminalSummary();
    }

    static long observerFailureCount() { return observerFailures; }

    /** Owns the factory-to-placement gap, including original catch/early-return/error paths. */
    public static void configurationFinished(ServerConfigurationPacketListenerImpl listener,
            ServerboundFinishConfigurationPacket packet, Operation<Void> original) {
        if (!listener.getMainThreadEventLoop().isSameThread()) { original.call(packet); return; }
        var previous = CONFIGURATION.get();
        var scope = new ConfigurationScope(listener, previous);
        CONFIGURATION.set(scope);
        try { original.call(packet); }
        finally {
            try { closeSelection(scope.selection); }
            finally {
                if (previous == null) { CONFIGURATION.remove(); } else { CONFIGURATION.set(previous); }
            }
        }
    }

    /** Select before the original constructor can install successor JSON owners in native maps. */
    public static ServerPlayer loginPlayer(net.minecraft.server.players.PlayerList list,
            GameProfile profile, ClientInformation information, Operation<ServerPlayer> original) {
        var source = root == null ? null : root.writerOwner(list);
        var previous = source == null ? null : source.current(profile.getId());
        if (previous == null) { return original.call(profile, information); }
        var context = CONFIGURATION.get();
        if (context == null || context.selection != null
                || context.listener.getMainThreadEventLoop() != previous.actor.getServer()
                || !context.listener.getOwner().getId().equals(profile.getId())) { throw unavailable(); }
        for (var parent = context.previous; parent != null; parent = parent.previous) {
            if (parent.selection != null && parent.selection.owner == source
                    && parent.selection.previous.actor.getUUID().equals(profile.getId())) { throw unavailable(); }
        }
        var selection = new LoginSelection(source, previous);
        context.selection = selection;
        boolean normal = false;
        try {
            if (!source.canSerialize(previous)) { throw unavailable(); }
            source.flushIndependentBeforeLogin(previous);
            selection.version = previous.source;
            selection.lineage = source.lineage(previous).orElseThrow(P11NativeStorageBoundary::unavailable);
            selection.memory = source.seal(previous, selectedMaterial(previous));
            if (selection.memory != null) { selection.bodyMemory = source.sealForSelectedInput(selection.memory); }
            if (selection.bodyMemory == null) {
                source.release(selection.memory);
                selection.memory = null;
                if (!source.canSerialize(previous)) { throw unavailable(); }
                selection.primary = synchronousPrimary(source, previous);
            }
            selection.independent = source.beginLoginIndependent(profile.getId());
            source.constructorStarted(selection.independent);
            selection.constructorStarted = true;
            var actor = original.call(profile, information);
            source.finishLoginIndependent(selection.independent, actor);
            // Constructor callbacks may publish or mutate native fields. Never select a stale
            // root merely because an e/v pair failed to observe an ordinary vanilla change.
            if (!source.canInspectConstructedPredecessor(previous) || previous.source != selection.version) { throw unavailable(); }
            var latest = selectedMaterial(previous);
            NbtUtils.addCurrentDataVersion(latest);
            var selected = selection.memory == null ? selection.primary.material : selection.memory.root;
            if (!selected.equals(latest) || !source.canInspectConstructedPredecessor(previous)
                    || previous.source != selection.version
                    || (selection.primary != null
                            && !source.completedPlayerWrite(previous, selection.primary.receipt))) {
                throw unavailable();
            }
            selection.actor = actor;
            normal = true;
            return actor;
        } finally {
            if (!normal) {
                closeSelection(selection);
                if (!selection.constructorStarted) { retainWithoutReplacingPrimary(source, previous); }
            }
        }
    }

    private static void closeSelection(LoginSelection selection) {
        if (selection == null || selection.closed) { return; }
        selection.closed = true;
        try {
            try { selection.owner.release(selection.bodyMemory); }
            finally {
                try { selection.owner.release(selection.memory); }
                finally {
                    if (selection.primary != null) { selection.primary.clear(); }
                    selection.owner.abortLoginIndependent(selection.independent);
                }
            }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static void constructed(ServerPlayer player) {
        var copy = COPY.get();
        if (copy != null && copy.old.getServer() == player.getServer()
                && copy.old.getUUID().equals(player.getUUID())) {
            var oldBody = copy.owner.body(copy.old);
            if (!copy.owner.canCopy(oldBody)) { throw unavailable(); }
            // Position callbacks in original respawn precede this constructor and may legally
            // publish new data. Capture the latest lineage before candidate custody changes.
            copy.lineage = copy.owner.lineage(oldBody).orElseThrow(P11NativeStorageBoundary::unavailable);
            copy.next = copy.owner.candidate(player);
        }
    }

    public static void place(MinecraftServer server, Connection connection, ServerPlayer player,
            CommonListenerCookie cookie, Operation<Void> original) {
        var source = root == null ? null : root.sourceOwner(server);
        // Constructor identity alone is not admission. This is the real configuration→PLAY call.
        boolean authenticated = connection.isConnected()
                && connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener
                && listener.getMainThreadEventLoop() == server
                && listener.getOwner().getId().equals(player.getUUID());
        if (source == null || !authenticated) {
            if (source != null && source.account(player) != null) { throw unavailable(); }
            original.call(connection, player, cookie);
            return;
        }
        if (LOAD.get() != null || PRIMARY_READ.get() != null) { throw unavailable(); }
        var account = source.account(player);
        LoginSelection selection = null;
        LoadScope scope = null;
        boolean normal = false;
        try {
            if (account != null && account.current != null && account.current.actor != player) {
                var context = CONFIGURATION.get();
                selection = context == null ? null : context.selection;
                if (selection == null || selection.closed || selection.consumed
                        || selection.owner != source || selection.actor != player
                        || selection.previous != account.current || selection.version != account.current.source
                        || !source.canInspectConstructedPredecessor(account.current)
                        || connection.getPacketListener() != context.listener) { throw unavailable(); }
                selection.consumed = true;
            }
            var body = source.candidate(player);
            if (body == null) { original.call(connection, player, cookie); normal = true; return; }
            scope = new LoadScope(source, body, selection == null ? null : selection.memory,
                    selection == null ? null : selection.bodyMemory,
                    selection == null ? null : selection.primary, selection == null ? null : selection.lineage);
            LOAD.set(scope);
            original.call(connection, player, cookie);
            normal = true;
        } finally {
            LOAD.remove();
            closeSelection(selection);
            if (scope != null && !normal) { failWithoutReplacingPrimary(source, scope.body, P11QualifiedSourceOwner.Fault.PARTIAL); }
        }
    }

    public static Optional<CompoundTag> load(MinecraftServer server, ServerPlayer player,
            Operation<Optional<CompoundTag>> original) {
        var scope = LOAD.get();
        if (scope == null || scope.body.actor != player) {
            var source = owner(player);
            if (source != null && source.account(player) != null) { throw unavailable(); }
            return original.call(player);
        }
        if (scope.loadStarted) { throw unavailable(); }
        scope.loadStarted = true;
        if (scope.memory != null) {
            if (!scope.owner.selectedInputCurrent(scope.memory)
                    || !scope.owner.selectedInputCurrent(scope.bodyMemory)) { throw unavailable(); }
            scope.kind = P11QualifiedSourceOwner.InputKind.MEMORY;
            scope.body.inputKind = scope.kind;
            scope.input = root.provenance().volatileInput(scope.lineage);
            // The original coherent root and the added immutable copy were reserved before B
            // mutation. The caller view is never obtained by copying B's post-load aliases.
            var callerInput = scope.memory.root;
            var bodyInput = scope.bodyMemory.root;
            loadBody(player, bodyInput, () -> player.load(bodyInput));
            return Optional.of(callerInput);
        }
        if (scope.preparedPrimary != null) {
            // This explicitly selects the verified physical primary, never stale host cache.
            var request = scope.preparedPrimary;
            ((P11NativeWorldAccess.PlayerStorage) server.getPlayerList()).p11$loadPreparedPrimary(request);
            return request.result;
        }
        var result = original.call(player);
        if (result.isEmpty()) {
            if (scope.primary != ReadState.ABSENT || scope.backup != ReadState.ABSENT
                    || !hostAbsenceKnown(server, player)) {
                scope.owner.fail(scope.body, P11QualifiedSourceOwner.Fault.READ);
                throw unavailable();
            }
            scope.kind = P11QualifiedSourceOwner.InputKind.ABSENT;
            scope.body.inputKind = scope.kind;
            scope.input = root.provenance().absentInput();
            scope.owner.beginInput(scope.body, scope.input);
            scope.readResult = scope.owner.missingInput(scope.body);
            scope.owner.loaded(scope.body, scope.input, scope.readResult);
            scope.owner.loadReturned(scope.body, true);
        }
        return result;
    }

    private static CompoundTag selectedMaterial(P11QualifiedSourceOwner.Body body) {
        if (SELECTED_SERIALIZE.get() != null) { throw unavailable(); }
        SELECTED_SERIALIZE.set(body);
        try { return body.actor.saveWithoutId(new CompoundTag()); }
        finally { SELECTED_SERIALIZE.remove(); }
    }

    private static PrimaryReadRequest synchronousPrimary(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body previous) {
        if (PRIMARY_READ.get() != null || WRITE.get() != null || CACHE.get() != null) { throw unavailable(); }
        var request = new PrimaryReadRequest(source, previous);
        PRIMARY_READ.set(request);
        boolean complete = false;
        try {
            ((P11NativeWorldAccess.PlayerStorage) previous.actor.getServer().getPlayerList())
                    .p11$preparePrimary(request);
            if (request.stage != PrimaryStage.VERIFIED || request.material == null) { throw unavailable(); }
            complete = true;
            return request;
        } finally {
            PRIMARY_READ.remove();
            if (!complete) {
                request.clear();
                retainWithoutReplacingPrimary(source, previous);
            }
        }
    }

    /** Internal opaque bridge: invokes the original virtual PlayerList.save, never another writer. */
    public static void preparePrimary(PlayerList list, PlayerDataStorage storage,
            PrimaryReadRequest request, Operation<Void> originalSave) {
        if (request == null || PRIMARY_READ.get() != request || request.stage != PrimaryStage.CREATED
                || list != request.previous.actor.getServer().getPlayerList()
                || root == null || root.playerStorageOwner(storage, request.previous.actor) != request.owner
                || !((P11NativeWorldAccess.PlayerStorage) list).p11$independentOwnersMatch(request.previous.actor)
                || !request.owner.canSerialize(request.previous)) { throw unavailable(); }
        request.storage = storage;
        request.stage = PrimaryStage.SAVING;
        originalSave.call(request.previous.actor);
        if (request.duplicateWriter || !request.owner.completedPlayerWrite(request.previous, request.receipt)) {
            throw unavailable();
        }
        request.stage = PrimaryStage.READ_REQUESTED;
        ((P11NativeWorldAccess.PrimaryReader) storage).p11$readPrimary(request);
        if (request.stage != PrimaryStage.READ || request.material == null
                || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
        // Saving callbacks can change ordinary native fields without a Gramarye version bump.
        // Compare the real disk read to a fresh serializer result after the entire save tail.
        var latest = selectedMaterial(request.previous);
        NbtUtils.addCurrentDataVersion(latest);
        if (!request.material.equals(latest)
                || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
        request.witness = request.owner.captureSelection(request.previous);
        request.stage = PrimaryStage.VERIFIED;
    }

    /** Only the original private .dat reader may fill this root-owned request. No DFU/load/event here. */
    public static void readPrimary(PlayerDataStorage storage, PrimaryReadRequest request,
            Operation<Optional<CompoundTag>> originalRead) {
        if (request == null || PRIMARY_READ.get() != request || request.storage != storage
                || request.stage != PrimaryStage.READ_REQUESTED
                || root == null || root.playerStorageOwner(storage, request.previous.actor) != request.owner
                || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
        request.stage = PrimaryStage.READING;
        var result = originalRead.call(request.previous.actor, ".dat");
        if (!request.readEntered || request.readState != ReadState.READ || result.isEmpty()) { throw unavailable(); }
        request.material = result.orElseThrow();
        request.stage = PrimaryStage.READ;
    }

    /** The original public load supplies DFU, one body load, one event and its same caller result. */
    public static void loadPreparedPrimary(PlayerList list, PlayerDataStorage storage,
            PrimaryReadRequest request) {
        var scope = LOAD.get();
        if (request == null || scope == null || scope.preparedPrimary != request
                || !scope.loadStarted || request.stage != PrimaryStage.VERIFIED
                || request.storage != storage || list != scope.body.actor.getServer().getPlayerList()
                || root == null || root.playerStorageOwner(storage, scope.body.actor) != scope.owner
                || !request.owner.completedPlayerWrite(request.previous, request.receipt, request.witness)) { throw unavailable(); }
        request.stage = PrimaryStage.CONSUMING;
        var result = storage.load(scope.body.actor);
        if (request.material != null || scope.primary != ReadState.READ || !scope.bodyLoaded
                || result.isEmpty()) { throw unavailable(); }
        request.result = result;
        request.stage = PrimaryStage.CONSUMED;
    }

    private static boolean hostAbsenceKnown(MinecraftServer server, ServerPlayer player) {
        if (!server.isSingleplayerOwner(player.getGameProfile())) { return true; }
        return root.hostInputKind(server) == P11QualifiedSourceOwner.InputKind.ABSENT;
    }

    public static Optional<CompoundTag> loadPlayer(PlayerDataStorage storage, Player player,
            Operation<Optional<CompoundTag>> original) {
        var scope = playerLoadScope(storage, player, false);
        if (scope == null) { return original.call(player); }
        if (scope.diskLoadActive || scope.bodyLoaded) { throw unavailable(); }
        scope.diskLoadActive = true;
        try { return original.call(player); }
        finally { scope.diskLoadActive = false; }
    }

    private static LoadScope playerLoadScope(PlayerDataStorage storage, Player player,
            boolean withinDiskLoad) {
        var source = root == null ? null : root.playerStorageOwner(storage, player);
        if (source == null || !source.hasAccount(player.getUUID())) { return null; }
        var scope = LOAD.get();
        if (scope == null || scope.owner != source || scope.body.actor != player
                || !scope.loadStarted || scope.memory != null
                || (withinDiskLoad && !scope.diskLoadActive)) { throw unavailable(); }
        return scope;
    }

    public static Optional<CompoundTag> readPlayer(PlayerDataStorage storage, Player player,
            File directory, String suffix,
            Operation<Optional<CompoundTag>> original) {
        var request = PRIMARY_READ.get();
        if (request != null && request.stage == PrimaryStage.READING) {
            if (request.storage != storage || request.previous.actor != player || !".dat".equals(suffix)
                    || request.readEntered || root == null
                    || root.playerStorageOwner(storage, player) != request.owner
                    || !request.owner.completedPlayerWrite(request.previous, request.receipt)) { throw unavailable(); }
            request.readEntered = true;
            var state = readState(directory, player, suffix);
            var result = original.call(player, suffix);
            request.readState = readResult(state, result);
            return result;
        }
        var scope = playerLoadScope(storage, player, true);
        if (scope == null) { return original.call(player, suffix); }
        if (scope.preparedPrimary != null) {
            var prepared = scope.preparedPrimary;
            if (!".dat".equals(suffix) || prepared.storage != storage
                    || prepared.stage != PrimaryStage.CONSUMING || prepared.material == null
                    || !prepared.owner.completedPlayerWrite(prepared.previous, prepared.receipt, prepared.witness)) { throw unavailable(); }
            var material = prepared.material;
            prepared.material = null;
            scope.primary = ReadState.READ;
            return Optional.of(material);
        }
        var state = readState(directory, player, suffix);
        var result = original.call(player, suffix);
        state = readResult(state, result);
        if (".dat".equals(suffix)) { scope.primary = state; }
        else if (".dat_old".equals(suffix)) { scope.backup = state; }
        else { throw unavailable(); }
        return result;
    }

    private static ReadState readState(File directory, Player player, String suffix) {
        var path = directory.toPath().resolve(player.getStringUUID() + suffix);
        try {
            var attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return attributes.isRegularFile() ? ReadState.PRESENT : ReadState.ERROR;
        } catch (NoSuchFileException missing) {
            return ReadState.ABSENT;
        } catch (IOException denied) {
            return ReadState.ERROR;
        }
    }

    private static ReadState readResult(ReadState state, Optional<CompoundTag> result) {
        return result.isPresent() ? state == ReadState.PRESENT ? ReadState.READ : ReadState.ERROR
                : state == ReadState.ABSENT ? ReadState.ABSENT : ReadState.ERROR;
    }

    public static void diskLoad(PlayerDataStorage storage, Player player, CompoundTag input,
            Operation<Void> original) {
        var scope = playerLoadScope(storage, player, true);
        if (scope == null) { original.call(player, input); return; }
        if (scope.primary != ReadState.READ) {
            scope.owner.fail(scope.body, scope.backup == ReadState.READ
                    ? P11QualifiedSourceOwner.Fault.FALLBACK : P11QualifiedSourceOwner.Fault.READ);
            throw unavailable();
        }
        scope.kind = P11QualifiedSourceOwner.InputKind.PRIMARY;
        scope.body.inputKind = scope.kind;
        scope.input = root.provenance().persistedInput();
        loadBody(scope.body.actor, input, () -> original.call(player, input));
    }

    public static void hostLoad(ServerPlayer player, CompoundTag input, Operation<Void> original) {
        var scope = LOAD.get();
        if (scope == null || scope.body.actor != player) { original.call(player, input); return; }
        if (root.hostInputKind(player.getServer()) != P11QualifiedSourceOwner.InputKind.HOST_PRIMARY) {
            scope.owner.fail(scope.body, P11QualifiedSourceOwner.Fault.READ);
            throw unavailable();
        }
        scope.kind = P11QualifiedSourceOwner.InputKind.HOST_PRIMARY;
        scope.body.inputKind = scope.kind;
        scope.input = root.provenance().persistedInput();
        loadBody(player, input, () -> original.call(player, input));
    }

    private static void loadBody(ServerPlayer player, CompoundTag input, Runnable original) {
        var scope = LOAD.get();
        if (scope == null || scope.body.actor != player || scope.bodyLoaded) { throw unavailable(); }
        scope.bodyLoaded = true;
        scope.expectedSkills = input.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(SKILLS.toString());
        scope.expectedMana = input.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).contains(MANA.toString());
        if (input.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                && !input.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY, Tag.TAG_COMPOUND)) { throw unavailable(); }
        scope.owner.beginInput(scope.body, scope.input);
        if (!scope.expectedSkills) {
            scope.readResult = scope.owner.missingInput(scope.body);
            scope.owner.loaded(scope.body, scope.input, scope.readResult);
        }
        boolean normal = false;
        try {
            original.run();
            normal = true;
        } finally {
            if (normal) {
                boolean complete = (!scope.expectedSkills || scope.skillsRead)
                        && (!scope.expectedMana || scope.manaRead);
                scope.owner.loadReturned(scope.body, complete);
            } else { failWithoutReplacingPrimary(scope.owner, scope.body, P11QualifiedSourceOwner.Fault.MATERIAL); }
        }
    }

    public static void callerMaterial(ServerPlayer player) {
        var scope = LOAD.get();
        if (scope != null && scope.body.actor == player) { scope.owner.callerComplete(scope.body); }
    }

    public static void readAttachments(AttachmentHolder holder, HolderLookup.Provider provider,
            CompoundTag input, Operation<Void> original) {
        original.call(provider, input);
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor && scope.expectedSkills && scope.skillsRead) {
            scope.owner.loaded(scope.body, scope.input, scope.readResult);
        }
    }

    public static void playerSkillsReadCompleted(IAttachmentHolder holder,
            PlayerSkillAttachmentService.P11AttachmentReadResult readResult) {
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor) {
            scope.skillsRead = root.provenance().readCompleted(scope.body.actor, readResult);
            scope.readResult = readResult;
        }
        var copy = COPY.get();
        if (copy != null && copy.next != null && holder == copy.next.actor) {
            copy.readResult = readResult;
            copy.skillsRead = root.provenance().readCompleted(copy.next.actor, readResult);
        }
    }

    public static void manaReadCompleted(IAttachmentHolder holder, P11ManaMaterial.Read result) {
        var scope = LOAD.get();
        if (scope != null && holder == scope.body.actor) {
            scope.manaRead = !scope.manaReadObserved && scope.owner.manaRead(scope.body, result);
            scope.manaReadObserved = true;
        }
        var copy = COPY.get();
        if (copy != null && copy.next != null && holder == copy.next.actor) {
            copy.manaRead = !copy.manaReadObserved && copy.owner.manaRead(copy.next, result);
            copy.manaReadObserved = true;
        }
    }

    public static void manaPublished(ServerPlayer actor,
            P11ManaMaterial.Publication publication) {
        var source = owner(actor);
        var body = source == null ? null : source.body(actor);
        if (body != null) { source.manaPublication(body, publication); }
    }

    public static void playerSkillsWritten(
            PlayerSkillAttachmentService.P11AttachmentWriteResult result) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.attachments != null
                && scope.attachments.holder == scope.body.actor) {
            scope.attachments.skills = result;
        }
    }

    public static void manaWritten(P11ManaMaterial.Write result) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.attachments != null
                && scope.attachments.holder == scope.body.actor) {
            scope.attachments.mana = result;
        }
    }

    public static CompoundTag writeAttachments(AttachmentHolder holder,
            HolderLookup.Provider provider, Operation<CompoundTag> original) {
        var scope = SERIALIZE.get();
        if (scope == null) { return original.call(provider); }
        var previous = scope.attachments;
        var observed = new AttachmentWriteScope(holder);
        scope.attachments = observed;
        try {
            boolean exactHolder = holder == scope.body.actor;
            boolean skills = exactHolder && holder.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS));
            boolean mana = exactHolder && holder.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA));
            var result = original.call(provider);
            if (exactHolder) {
                scope.requiredComplete &= (!skills || (result != null && observed.skills != null
                        && observed.skills.matches(scope.body.actor, result.get(SKILLS.toString()))))
                        && (!mana || (result != null && observed.mana != null
                        && observed.mana.matches(scope.body.actor, result.get(MANA.toString()))));
            }
            return result;
        } finally { scope.attachments = previous; }
    }

    public static void brainEncoded(LivingEntity actor, boolean complete) {
        var scope = SERIALIZE.get();
        if (scope != null && scope.body.actor == actor) {
            scope.brainObserved = true;
            scope.requiredComplete &= complete;
        }
    }

    public static CompoundTag serialize(Entity entity, CompoundTag input, Operation<CompoundTag> original) {
        if (!(entity instanceof ServerPlayer actor)) { return original.call(input); }
        var source = owner(actor);
        var body = source == null ? null : source.body(actor);
        if (body == null) {
            if (source != null && source.account(actor) != null) { throw unavailable(); }
            return original.call(input);
        }
        boolean selectedComparison = SELECTED_SERIALIZE.get() == body
                && source.canInspectConstructedPredecessor(body);
        if ((!source.canSerialize(body) && !selectedComparison) || SERIALIZE.get() != null) { throw unavailable(); }
        var scope = new SerializeScope(source, body);
        SERIALIZE.set(scope);
        boolean normal = false;
        long started = System.nanoTime();
        try {
            var result = original.call(input);
            if (!scope.requiredComplete || !scope.brainObserved || body.source != scope.version
                    || !(source.canSerialize(body) || (selectedComparison
                            && source.canInspectConstructedPredecessor(body)))) {
                source.fail(body, P11QualifiedSourceOwner.Fault.MATERIAL);
                throw unavailable();
            }
            var writer = WRITE.get();
            if (body.envelope != null && !body.logoutActive) {
                // Retained lifecycle fields never escape through an arbitrary serializer call.
                // Owned physical consumers only read; selected load copies under reservation.
                if ((writer == null || writer.body != body) && SELECTED_SERIALIZE.get() != body) {
                    throw unavailable();
                }
                body.envelope.apply(result);
            }
            if (body.logoutActive && writer != null && writer.body == body
                    && writer.kind == P11ReceiptLedger.WriterKind.PLAYER_DATA) {
                body.pendingEnvelope = new P11QualifiedSourceOwner.Envelope(result);
            }
            if (writer != null && writer.body == body) { writer.material = result; }
            normal = true;
            return result;
        } finally {
            SERIALIZE.remove();
            source.serialized(System.nanoTime() - started);
            if (!normal) { failWithoutReplacingPrimary(source, body, P11QualifiedSourceOwner.Fault.MATERIAL); }
        }
    }

    public static void remove(ServerPlayer player, Operation<Void> original) {
        var source = owner(player);
        var body = source == null ? null : source.body(player);
        if (body == null) { original.call(player); return; }
        if (body.logoutActive) { throw unavailable(); }
        body.logoutActive = true;
        boolean normal = false;
        try { original.call(player); normal = true; }
        finally {
            body.logoutActive = false;
            if (normal && body.pendingEnvelope != null) { body.envelope = body.pendingEnvelope; }
            body.pendingEnvelope = null;
            if (!normal) { failWithoutReplacingPrimary(source, body, P11QualifiedSourceOwner.Fault.CLEANUP); }
        }
    }

    public static ServerPlayer respawn(ServerPlayer old, boolean keepEverything,
            Entity.RemovalReason reason, Operation<ServerPlayer> original) {
        var source = owner(old);
        var body = source == null ? null : source.body(old);
        if (body == null) { return original.call(old, keepEverything, reason); }
        if (COPY.get() != null || !source.canCopy(body)) { throw unavailable(); }
        var lineage = source.lineage(body).orElseThrow(P11NativeStorageBoundary::unavailable);
        var scope = new CopyScope(source, old, lineage);
        COPY.set(scope);
        boolean normal = false;
        try {
            var result = original.call(old, keepEverything, reason);
            if (scope.next == null || scope.next.actor != result) { throw unavailable(); }
            source.callerComplete(scope.next);
            normal = true;
            return result;
        } finally {
            COPY.remove();
            if (!normal) {
                if (scope.next != null) {
                    failWithoutReplacingPrimary(source, scope.next, P11QualifiedSourceOwner.Fault.PARTIAL);
                } else {
                    try { source.constructorEscaped(body, null); }
                    catch (RuntimeException | Error secondary) {
                        if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
                    }
                }
            }
        }
    }

    public static void copy(ServerPlayer next, ServerPlayer old, boolean keepEverything,
            Operation<Void> original) {
        var scope = COPY.get();
        if (scope == null || scope.old != old || scope.next == null || scope.next.actor != next) {
            original.call(old, keepEverything); return;
        }
        if (scope.lineage == null) {
            scope.owner.fail(scope.next, P11QualifiedSourceOwner.Fault.MATERIAL);
            throw unavailable();
        }
        var input = root.provenance().volatileInput(scope.lineage);
        scope.next.inputKind = P11QualifiedSourceOwner.InputKind.MEMORY;
        scope.owner.beginInput(scope.next, input);
        boolean skills = old.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS));
        boolean mana = old.hasData(NeoForgeRegistries.ATTACHMENT_TYPES.get(MANA));
        if (!skills) { scope.readResult = scope.owner.missingInput(scope.next); }
        original.call(old, keepEverything);
        scope.owner.loaded(scope.next, input, scope.readResult);
        scope.owner.loadReturned(scope.next, (!skills || scope.skillsRead) && (!mana || scope.manaRead));
    }

    private static P11QualifiedSourceOwner.SourceUnavailable unavailable() {
        return new P11QualifiedSourceOwner.SourceUnavailable();
    }

    private static void failWithoutReplacingPrimary(P11QualifiedSourceOwner owner,
            P11QualifiedSourceOwner.Body body, P11QualifiedSourceOwner.Fault reason) {
        try { owner.fail(body, reason); }
        catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    private static void retainWithoutReplacingPrimary(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body) {
        try { source.retainSynchronousDuty(body); }
        catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static void savePlayer(PlayerDataStorage storage, Player player, Operation<Void> original) {
        var source = root == null ? null : root.playerStorageOwner(storage, player);
        var body = source != null && player instanceof ServerPlayer actor ? source.body(actor) : null;
        boolean managed = source != null && player instanceof ServerPlayer actor && source.account(actor) != null;
        var previous = WRITE.get();
        if (managed && previous != null) { return; }
        var request = PRIMARY_READ.get();
        boolean synchronous = request != null && request.stage == PrimaryStage.SAVING
                && request.previous == body && request.storage == storage;
        var receipt = !managed ? null : synchronous ? source.beginSynchronousPlayerWriter(body)
                : source.beginWriter(body, P11ReceiptLedger.WriterKind.PLAYER_DATA);
        if (managed && receipt == null) { return; } // Do not truncate the caller's stats/PA tail.
        if (synchronous) {
            if (request.receipt != null) { request.duplicateWriter = true; }
            else { request.receipt = receipt; }
        }
        // An unmanaged original writer still masks a surrounding scope's callbacks.
        var scope = new WriteScope(source, body, receipt, previous);
        WRITE.set(scope);
        boolean normal = false;
        try { original.call(player); normal = true; }
        finally { scope.facts.nativeReturned(normal); finish(scope); }
    }

    public static WriteScope beginNbtWrite(CompoundTag material, Path path) {
        var writer = WRITE.get();
        boolean managed = writer != null && writer.receipt != null && !writer.independent
                && writer.material == material && !writer.finished;
        if (managed && (IO.get() != null
                || !writer.owner.mayWrite(writer.body, writer.receipt))) {
            writer.facts.rejected();
            throw unavailable();
        }
        var scope = WriteScope.io(material, managed ? writer : null, IO.get(), false);
        if (managed) {
            writer.incoming = path;
            String name = writer.kind == P11ReceiptLedger.WriterKind.LEVEL_PLAYER
                    ? "level.dat" : writer.body.actor.getStringUUID() + ".dat";
            writer.currentPath = path.resolveSibling(name).toAbsolutePath().normalize();
            writer.backupPath = path.resolveSibling(name + "_old").toAbsolutePath().normalize();
        }
        IO.set(scope);
        return scope;
    }

    public static void endNbtWrite(WriteScope scope, boolean normal) {
        endIo(scope, normal, false);
    }

    public static WriteScope beginNbtStream(CompoundTag material) {
        var previous = IO.get();
        var writer = previous != null && !previous.stream && previous.ioOnly
                && previous.material == material ? previous.ioWriter : null;
        var scope = WriteScope.io(material, writer, previous, true);
        IO.set(scope);
        return scope;
    }

    public static void endNbtStream(WriteScope scope, boolean normal) {
        endIo(scope, normal, true);
    }

    private static void endIo(WriteScope scope, boolean normal, boolean stream) {
        if (scope == null || scope.finished || !scope.ioOnly || scope.stream != stream
                || IO.get() != scope) { return; }
        scope.finished = true;
        if (scope.previous == null) { IO.remove(); } else { IO.set(scope.previous); }
        var writer = scope.ioWriter;
        if (writer != null && WRITE.get() == writer && !writer.finished) {
            if (stream) { writer.facts.nativeReturned(normal); }
            else { writer.facts.ioReturned(normal); }
        }
    }

    private static WriteScope observedIoWriter() {
        var scope = IO.get();
        var writer = scope == null || scope.finished ? null : scope.ioWriter;
        return writer != null && !writer.finished && WRITE.get() == writer ? writer : null;
    }

    public static void nbtBodyWritten(boolean normal) {
        var writer = observedIoWriter();
        if (writer != null) { writer.facts.nbtBody(normal); }
    }

    public static void nbtStreamClosed(boolean normal) {
        var writer = observedIoWriter();
        if (writer != null) { writer.facts.closed(normal); }
    }

    public static void stringEncodingFallback() {
        var writer = observedIoWriter();
        if (writer != null) { writer.facts.encoded(false); }
    }

    public static boolean beforeReplace(Path current, Path incoming, Path backup, boolean noRestore) {
        var scope = WRITE.get();
        if (scope == null || scope.finished || scope.receipt == null || scope.independent) { return true; }
        scope.observingReplace = scope.incoming != null && scope.incoming.equals(incoming);
        if (!scope.observingReplace) { return true; } // An unrelated original writer is not ours.
        boolean permitted = !noRestore && current.toAbsolutePath().normalize().equals(scope.currentPath)
                && backup.toAbsolutePath().normalize().equals(scope.backupPath)
                && scope.facts.readyToReplace() && IO.get() == null
                && scope.owner.mayWrite(scope.body, scope.receipt);
        if (!permitted) { scope.observingReplace = false; scope.facts.rejected(); }
        return permitted;
    }

    public static void replaceResult(boolean actualResult) {
        var scope = WRITE.get();
        if (scope == null || scope.finished || !scope.observingReplace) { return; }
        scope.observingReplace = false;
        scope.facts.replaced(actualResult);
    }

    public static WriteScope beginStats(ServerStatsCounter stats, MinecraftServer server, File file) {
        var source = root == null ? null : root.statsWriterOwner(stats, server, file);
        var body = source == null ? null : source.canonicalStats(stats);
        return independent(source, body, P11ReceiptLedger.WriterKind.STATISTICS,
                stats instanceof P11IndependentMaterialWitness witness ? witness : null);
    }

    public static WriteScope beginAdvancements(PlayerAdvancements advancements, ServerPlayer actor, Path file) {
        var source = root == null ? null : root.advancementWriterOwner(advancements, actor, file);
        var body = source == null ? null : source.canonicalAdvancements(advancements);
        return independent(source, body, P11ReceiptLedger.WriterKind.ADVANCEMENTS,
                advancements instanceof P11IndependentMaterialWitness witness ? witness : null);
    }

    public static void statsMutated(ServerStatsCounter stats, MinecraftServer server) {
        try {
            var source = root == null ? null : root.sourceOwner(server);
            var body = source == null ? null : source.canonicalStats(stats);
            if (body != null) { source.independentMutation(body, P11ReceiptLedger.WriterKind.STATISTICS); }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static void advancementsMutated(PlayerAdvancements advancements, ServerPlayer actor) {
        try {
            var source = owner(actor);
            var body = source == null ? null : source.canonicalAdvancements(advancements);
            if (body != null) { source.independentMutation(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS); }
        } catch (RuntimeException | Error secondary) {
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    private static WriteScope independent(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body, P11ReceiptLedger.WriterKind kind,
            P11IndependentMaterialWitness witness) {
        var previous = WRITE.get();
        boolean managed = source != null && body != null;
        var receipt = managed && previous == null ? source.beginIndependentWriter(body, kind, witness) : null;
        var scope = new WriteScope(source, body, receipt, previous, kind, managed && receipt == null);
        // Blocked scopes execute no original body, so they must not disturb an outer writer.
        if (!scope.blocked) { WRITE.set(scope); }
        return scope;
    }

    public static boolean independentPermitted(WriteScope scope) {
        return scope == null || (scope.independent && !scope.blocked && !scope.finished && WRITE.get() == scope);
    }

    private static WriteScope observedIndependent() {
        var scope = WRITE.get();
        return scope != null && scope.independent && !scope.blocked && !scope.finished
                && scope.receipt != null ? scope : null;
    }

    public static void independentEncoded(boolean normal) {
        var scope = observedIndependent();
        if (scope != null) { scope.facts.encoded(normal); }
    }

    public static void independentWritten(boolean normal) {
        var scope = observedIndependent();
        if (scope != null) { scope.facts.written(normal); }
    }

    public static void independentClosed(boolean normal) {
        var scope = observedIndependent();
        if (scope != null) { scope.facts.closed(normal); }
    }

    public static void endIndependent(WriteScope scope, boolean normal) {
        if (scope == null || !scope.independent || scope.finished) { return; }
        if (scope.blocked) {
            if (!scope.owner.owns(scope.body.actor.getServer())) { return; }
            scope.finished = true;
            try { scope.owner.independentSkipped(scope.body, scope.kind); }
            catch (RuntimeException | Error secondary) {
                if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
            }
            return;
        }
        if (WRITE.get() != scope) { return; }
        scope.facts.nativeReturned(normal);
        finish(scope);
    }

    private static void finish(WriteScope scope) {
        if (scope == null || scope.finished || scope.ioOnly || WRITE.get() != scope) { return; }
        scope.finished = true;
        if (scope.previous == null) { WRITE.remove(); } else { WRITE.set(scope.previous); }
        if (scope.receipt == null) { return; }
        try {
            var source = scope.owner;
            var receipt = scope.receipt;
            var result = scope.facts.settle();
            source.physical(receipt, P11ReceiptLedger.PhysicalStep.ENCODE, result.encode());
            source.physical(receipt, P11ReceiptLedger.PhysicalStep.WRITE, result.write());
            source.physical(receipt, P11ReceiptLedger.PhysicalStep.CLOSE, result.close());
            if (receipt.kind() == P11ReceiptLedger.WriterKind.PLAYER_DATA
                    || receipt.kind() == P11ReceiptLedger.WriterKind.LEVEL_PLAYER) {
                source.physical(receipt, P11ReceiptLedger.PhysicalStep.REPLACE, result.replace());
            }
            source.finishWriter(scope.body, receipt, result.successful(), System.nanoTime() - scope.started);
        } catch (RuntimeException | Error secondary) {
            // This observation finalizer must not replace a primary native exception/Error.
            if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
        }
    }

    public static CompoundTag hostCacheMaterial(net.minecraft.server.players.PlayerList list,
            ServerPlayer player, CompoundTag target,
            Operation<CompoundTag> original) {
        var source = root == null ? null : root.writerOwner(list, player);
        if (source == null || source.account(player) == null) { return original.call(player, target); }
        var body = source.body(player);
        if (synchronousCacheSkip(source, body)) { return null; }
        var scope = CACHE.get();
        if (scope == null || scope.list != list || scope.body != body || scope.receipt == null
                || !source.mayWrite(body, scope.receipt)) { return null; }
        var material = original.call(player, target);
        scope.material = material;
        source.physical(scope.receipt, P11ReceiptLedger.PhysicalStep.ENCODE,
                P11ReceiptLedger.Observation.SUCCEEDED);
        return material;
    }

    public static void hostCacheAssignment(net.minecraft.server.players.PlayerList list,
            CompoundTag material, Operation<Void> original) {
        var source = root == null ? null : root.writerOwner(list);
        var request = PRIMARY_READ.get();
        if (request != null && request.owner == source && request.stage == PrimaryStage.SAVING
                && list == request.previous.actor.getServer().getPlayerList()) { return; }
        var scope = CACHE.get();
        if (scope == null) {
            if (source != null && root.hostBody(source) != null) { return; }
            original.call(list, material); return;
        }
        if (scope.source != source || scope.list != list) { throw unavailable(); }
        if (scope.receipt == null || material == null || material != scope.material
                || !scope.source.mayWrite(scope.body, scope.receipt)) { return; }
        original.call(list, material);
        scope.assigned = true;
        scope.source.physical(scope.receipt, P11ReceiptLedger.PhysicalStep.CACHE_ASSIGNMENT,
                P11ReceiptLedger.Observation.SUCCEEDED);
    }

    public static void integratedSave(net.minecraft.server.players.PlayerList list,
            ServerPlayer player, Operation<Void> original) {
        var source = root == null ? null : root.writerOwner(list, player);
        var body = source == null ? null : source.body(player);
        if (body == null || !player.getServer().isSingleplayerOwner(player.getGameProfile())) {
            original.call(player); return;
        }
        if (synchronousCacheSkip(source, body)) {
            // Preserve old cache; only the original super PD/stats/PA writer tail runs.
            // There is no attempted cache writer and thus no invented cache failure receipt.
            original.call(player); return;
        }
        if (CACHE.get() != null) { return; }
        var receipt = source.beginWriter(body, P11ReceiptLedger.WriterKind.CACHE);
        var scope = new CacheScope(list, source, body, receipt);
        CACHE.set(scope);
        try { original.call(player); }
        finally {
            CACHE.remove();
            if (receipt != null) {
                try { source.finishWriter(body, receipt, scope.assigned, System.nanoTime() - scope.started); }
                catch (RuntimeException | Error secondary) {
                    if (observerFailures != Long.MAX_VALUE) { observerFailures++; }
                }
            }
        }
    }

    private static boolean synchronousCacheSkip(P11QualifiedSourceOwner source,
            P11QualifiedSourceOwner.Body body) {
        var request = PRIMARY_READ.get();
        return request != null && request.owner == source && request.previous == body
                && request.stage == PrimaryStage.SAVING;
    }

    public static Dynamic<?> readLevel(LevelStorageSource.LevelStorageAccess storage,
            boolean fallback, Operation<Dynamic<?>> original) {
        var previous = LEVEL_READ.get();
        var scope = new LevelReadScope();
        LEVEL_READ.set(scope);
        var witness = (P11NativeWorldAccess.ReadStorage) storage;
        witness.p11$readWitness(null);
        try {
            var input = original.call(fallback);
            witness.p11$readWitness(new P11NativeWorldAccess.ReadWitness(input, !fallback, scope.envelope));
            return input;
        } finally {
            if (previous == null) { LEVEL_READ.remove(); } else { LEVEL_READ.set(previous); }
        }
    }

    public static void levelReadRoot(CompoundTag rootTag) {
        var scope = LEVEL_READ.get();
        if (scope != null) {
            scope.envelope = rootTag.contains("Data", Tag.TAG_COMPOUND)
                    && (!rootTag.getCompound("Data").contains("Player")
                            || rootTag.getCompound("Data").contains("Player", Tag.TAG_COMPOUND));
        }
    }

    public static void saveWorld(LevelStorageSource.LevelStorageAccess storage,
            RegistryAccess registries, WorldData data, CompoundTag player, Operation<Void> original) {
        var source = root == null ? null : root.writerOwner(storage, data);
        var body = source == null ? null : root.hostBody(source);
        if (body == null) { original.call(registries, data, player); return; }
        if (WRITE.get() != null) { return; }
        var receipt = source.beginWriter(body, P11ReceiptLedger.WriterKind.LEVEL_PLAYER);
        if (receipt == null) { return; }
        var scope = new WriteScope(source, body, receipt, null);
        WRITE.set(scope);
        boolean normal = false;
        try {
            var selected = body.actor.saveWithoutId(new CompoundTag());
            original.call(registries, data, selected);
            normal = true;
        } finally { scope.facts.nativeReturned(normal); finish(scope); }
    }

    public static void saveLevelRoot(LevelStorageSource.LevelStorageAccess storage,
            CompoundTag data, Operation<Void> original) {
        var scope = WRITE.get();
        var source = root == null ? null : root.writerOwner(storage, null);
        if (scope != null && scope.receipt != null && scope.owner != source) { throw unavailable(); }
        if (source == null) { original.call(data); return; }
        var body = root.hostBody(source);
        if (body == null) { original.call(data); return; }
        if (scope == null || scope.owner != source || scope.body != body
                || data.getCompound("Data").get("Player") != scope.material
                || !source.mayWrite(body, scope.receipt)) { return; }
        scope.material = data;
        original.call(data);
    }

    public static void stop(MinecraftServer server, Operation<Void> original) {
        boolean normal = false;
        try { original.call(); normal = true; }
        finally {
            if (root != null) { root.nativeStopTerminal(server, normal); }
        }
    }

    private enum ReadState { UNOBSERVED, ABSENT, PRESENT, READ, ERROR }
    private static final class ConfigurationScope {
        final ServerConfigurationPacketListenerImpl listener;
        final ConfigurationScope previous;
        LoginSelection selection;
        ConfigurationScope(ServerConfigurationPacketListenerImpl listener, ConfigurationScope previous) {
            this.listener = listener; this.previous = previous;
        }
    }

    /** Lives only within the original configuration-final caller; never a queue or account root. */
    private static final class LoginSelection {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body previous;
        P11ReceiptLedger.Source version;
        P11QualifiedSourceOwner.LoginIndependent independent;
        P11QualifiedSourceOwner.Sealed memory, bodyMemory;
        P11SourceProvenance.Lineage lineage;
        PrimaryReadRequest primary;
        ServerPlayer actor;
        boolean consumed, closed, constructorStarted;
        LoginSelection(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body previous) {
            this.owner = owner; this.previous = previous; this.version = previous.source;
        }
    }

    private enum PrimaryStage { CREATED, SAVING, READ_REQUESTED, READING, READ, VERIFIED, CONSUMING, CONSUMED, CLOSED }

    /** Root-only minted, one call-local native save/read handoff. No public material accessor. */
    public static final class PrimaryReadRequest {
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body previous;
        private PlayerDataStorage storage;
        private P11ReceiptLedger.PhysicalWriterReceipt receipt;
        private P11QualifiedSourceOwner.SelectionWitness witness;
        private PrimaryStage stage = PrimaryStage.CREATED;
        private ReadState readState = ReadState.UNOBSERVED;
        private boolean readEntered, duplicateWriter;
        private CompoundTag material;
        private Optional<CompoundTag> result;

        private PrimaryReadRequest(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body previous) {
            this.owner = owner; this.previous = previous;
        }

        private void clear() {
            material = null;
            result = null;
            stage = PrimaryStage.CLOSED;
        }
    }

    private static final class LoadScope {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final P11QualifiedSourceOwner.Sealed memory;
        final P11QualifiedSourceOwner.Sealed bodyMemory;
        final PrimaryReadRequest preparedPrimary;
        final P11SourceProvenance.Lineage lineage;
        P11SourceProvenance.SelectedInput input;
        PlayerSkillAttachmentService.P11AttachmentReadResult readResult;
        P11QualifiedSourceOwner.InputKind kind = P11QualifiedSourceOwner.InputKind.UNKNOWN;
        ReadState primary = ReadState.UNOBSERVED;
        ReadState backup = ReadState.UNOBSERVED;
        boolean loadStarted, bodyLoaded, diskLoadActive, expectedSkills, expectedMana,
                skillsRead, manaRead, manaReadObserved;
        LoadScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11QualifiedSourceOwner.Sealed memory, P11QualifiedSourceOwner.Sealed bodyMemory,
                PrimaryReadRequest preparedPrimary, P11SourceProvenance.Lineage lineage) {
            this.owner = owner; this.body = body; this.memory = memory; this.bodyMemory = bodyMemory;
            this.preparedPrimary = preparedPrimary; this.lineage = lineage;
        }
    }

    private static final class SerializeScope {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final P11ReceiptLedger.Source version;
        AttachmentWriteScope attachments;
        boolean brainObserved;
        boolean requiredComplete = true;
        SerializeScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.owner = owner; this.body = body; this.version = body.source;
        }
    }

    private static final class AttachmentWriteScope {
        final AttachmentHolder holder;
        PlayerSkillAttachmentService.P11AttachmentWriteResult skills;
        P11ManaMaterial.Write mana;
        AttachmentWriteScope(AttachmentHolder holder) { this.holder = holder; }
    }

    private static final class CopyScope {
        final P11QualifiedSourceOwner owner;
        final ServerPlayer old;
        P11SourceProvenance.Lineage lineage;
        P11QualifiedSourceOwner.Body next;
        PlayerSkillAttachmentService.P11AttachmentReadResult readResult;
        boolean skillsRead, manaRead, manaReadObserved;
        CopyScope(P11QualifiedSourceOwner owner, ServerPlayer old, P11SourceProvenance.Lineage lineage) {
            this.owner = owner; this.old = old; this.lineage = lineage;
        }
    }

    private static final class LevelReadScope { boolean envelope; }

    private static final class CacheScope {
        final net.minecraft.server.players.PlayerList list;
        final P11QualifiedSourceOwner source;
        final P11QualifiedSourceOwner.Body body;
        final P11ReceiptLedger.PhysicalWriterReceipt receipt;
        final long started = System.nanoTime();
        CompoundTag material;
        boolean assigned;
        CacheScope(net.minecraft.server.players.PlayerList list,
                P11QualifiedSourceOwner source, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.PhysicalWriterReceipt receipt) {
            this.list = list; this.source = source; this.body = body; this.receipt = receipt;
        }
    }

    /** Only actual original writer scopes can mint this token; it has no public constructor. */
    public static final class WriteScope {
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body body;
        final P11ReceiptLedger.PhysicalWriterReceipt receipt;
        final WriteScope previous;
        final P11ReceiptLedger.WriterKind kind;
        final P11NativeWriteFacts facts;
        final boolean independent;
        final boolean blocked;
        final boolean ioOnly;
        final boolean stream;
        final WriteScope ioWriter;
        final long started = System.nanoTime();
        CompoundTag material;
        Path incoming, currentPath, backupPath;
        boolean observingReplace, finished;
        private WriteScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.PhysicalWriterReceipt receipt, WriteScope previous) {
            this(owner, body, receipt, previous, receipt == null
                    ? P11ReceiptLedger.WriterKind.PLAYER_DATA : receipt.kind(), false);
        }
        private WriteScope(P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                P11ReceiptLedger.PhysicalWriterReceipt receipt, WriteScope previous,
                P11ReceiptLedger.WriterKind kind, boolean blocked) {
            this.owner = owner; this.body = body; this.receipt = receipt; this.previous = previous;
            this.kind = kind; this.blocked = blocked;
            independent = kind == P11ReceiptLedger.WriterKind.STATISTICS
                    || kind == P11ReceiptLedger.WriterKind.ADVANCEMENTS;
            facts = new P11NativeWriteFacts(kind == P11ReceiptLedger.WriterKind.STATISTICS
                    ? P11NativeWriteFacts.Kind.STATISTICS : kind == P11ReceiptLedger.WriterKind.ADVANCEMENTS
                            ? P11NativeWriteFacts.Kind.ADVANCEMENTS : P11NativeWriteFacts.Kind.NBT);
            ioOnly = false; stream = false; ioWriter = null;
        }
        private WriteScope(CompoundTag material, WriteScope writer, WriteScope previous, boolean stream) {
            this.material = material; this.ioWriter = writer; this.previous = previous; this.stream = stream;
            owner = null; body = null; receipt = null; kind = null; facts = null;
            independent = false; blocked = false; ioOnly = true;
        }
        private static WriteScope io(CompoundTag material, WriteScope writer, WriteScope previous, boolean stream) {
            return new WriteScope(material, writer, previous, stream);
        }
    }
}
