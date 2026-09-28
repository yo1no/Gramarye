package com.yo1no.gramarye.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11CanonicalAdvancements;
import com.yo1no.gramarye.P11CanonicalAdvancements.Flush;
import com.yo1no.gramarye.P11CanonicalAdvancements.ReloadInput;
import com.yo1no.gramarye.P11IndependentMaterialWitness;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Original canonical data, reload, delivery and writer bodies keep their native ordering. */
@Mixin(PlayerAdvancements.class)
abstract class P11AdvancementsMixin implements P11IndependentMaterialWitness, P11CanonicalAdvancements.Access {
    @Shadow private ServerPlayer player;
    @Shadow @Final private Path playerSavePath;
    @Shadow private AdvancementTree tree;
    @Shadow @Final private Set<AdvancementHolder> visible;
    @Shadow @Final private Set<AdvancementHolder> progressChanged;
    @Shadow @Final private Set<AdvancementNode> rootsToUpdate;
    @Shadow private AdvancementHolder lastSelectedTab;
    @Shadow private boolean isFirstPacket;
    @Shadow protected abstract PlayerAdvancements.Data asData();
    @Shadow protected abstract void applyFrom(ServerAdvancementManager manager, PlayerAdvancements.Data data);
    @Unique private boolean p11$inputComplete;
    @Unique private boolean p11$materialFault;
    @Unique private boolean p11$loading;
    @Unique private long p11$treeGeneration;
    @Unique private long p11$holderGeneration;
    @Unique private boolean p11$holderActive;
    @Unique private ReloadInput p11$reload;
    @Unique private Flush p11$flush;
    @Unique private Connection p11$deliveryConnection;
    @Unique private final P11CanonicalAdvancements.Delivery p11$delivery =
            new P11CanonicalAdvancements.Delivery(true);

    @Override
    public ServerPlayer p11$associatedPlayer() { return player; }

    @Override
    public long p11$treeGeneration() { return p11$treeGeneration; }

    @Override
    public boolean p11$materialComplete() {
        return p11$inputComplete && !p11$materialFault && !p11$loading;
    }

    @WrapMethod(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$load(ServerAdvancementManager manager, Operation<Void> original) {
        boolean outerLoading = p11$loading;
        var reload = p11$reload;
        boolean memoryReload = reload != null && reload.manager == manager
                && reload.generation == p11$treeGeneration && !reload.inputClaimed;
        if (outerLoading && !memoryReload) { p11$materialFault = true; }
        p11$loading = true;
        boolean normal = false;
        try {
            original.call(manager);
            normal = true;
        } finally {
            p11$loading = outerLoading;
            // Once the complete reload input is installed, an automatic reward failure is
            // a real partial reward, not evidence that the canonical progress became empty.
            if (!normal && !(memoryReload && reload.inputApplied)) { p11$materialFault = true; }
            if (normal && !p11$materialFault) { p11$inputComplete = true; }
        }
    }

    @WrapOperation(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            at = @At(value = "INVOKE",
                    target = "Ljava/nio/file/Files;isRegularFile(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$inputExists(Path input, LinkOption[] options, Operation<Boolean> original,
            ServerAdvancementManager manager) {
        var reload = p11$reload;
        if (reload != null && reload.manager == manager && reload.generation == p11$treeGeneration
                && !reload.inputClaimed) {
            reload.inputClaimed = true;
            applyFrom(manager, reload.data);
            reload.inputApplied = true;
            p11$inputComplete = true;
            // Only the original disk-input branch is bypassed. The original automatic and
            // listener tail below this branch still executes once for this actual reload.
            return false;
        }
        boolean regular = original.call(input, options);
        if (!regular) {
            boolean absent;
            try {
                absent = Files.notExists(input, options);
            } catch (SecurityException ignored) {
                absent = false;
            }
            if (!absent) { p11$materialFault = true; }
        }
        return regular;
    }

    @Inject(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 2, expect = 2, allow = 2)
    private void p11$readFailed(ServerAdvancementManager manager, CallbackInfo callback) {
        p11$materialFault = true;
    }

    @WrapMethod(method = "reload(Lnet/minecraft/server/ServerAdvancementManager;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$reload(ServerAdvancementManager manager, Operation<Void> original) {
        var canonical = (PlayerAdvancements) (Object) this;
        if (!P11NativeStorageBoundary.isManagedCanonicalAdvancements(canonical, player)) {
            original.call(manager);
            return;
        }
        if (!p11$inputComplete || p11$materialFault || (p11$loading && p11$reload == null)) {
            throw new IllegalStateException("P11_ADVANCEMENT_RELOAD_INPUT_UNAVAILABLE");
        }
        long priorGeneration = p11$treeGeneration;
        // Native Data.asData is shallow. The codec copy completes before stopListening or
        // any clear; failure preserves the entire original canonical progress and listeners.
        var input = P11CanonicalAdvancements.copy(PlayerAdvancements.Data.CODEC, asData());
        if (priorGeneration != p11$treeGeneration) {
            throw new IllegalStateException("P11_ADVANCEMENT_RELOAD_INPUT_CHANGED");
        }
        long generation = Math.incrementExact(priorGeneration);
        var previous = p11$reload;
        long previousHolderGeneration = p11$holderGeneration;
        boolean previousHolderActive = p11$holderActive;
        var scope = new ReloadInput(manager, input, generation);
        var operation = P11NativeOperationBoundary.beginAdvancement(canonical, player);
        p11$treeGeneration = generation;
        p11$holderGeneration = generation;
        p11$holderActive = true;
        p11$reload = scope;
        p11$delivery.requireInitial();
        boolean normal = false;
        try {
            original.call(manager);
            normal = true;
        } finally {
            p11$reload = previous;
            p11$holderGeneration = previousHolderGeneration;
            p11$holderActive = previousHolderActive;
            P11NativeOperationBoundary.end(operation, normal);
        }
    }

    @WrapOperation(method = "load(Lnet/minecraft/server/ServerAdvancementManager;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerAdvancements;registerListeners(Lnet/minecraft/server/ServerAdvancementManager;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$currentListenerTail(PlayerAdvancements canonical,
            ServerAdvancementManager manager, Operation<Void> original) {
        var reload = p11$reload;
        if (reload != null && (reload.manager != manager || reload.generation != p11$treeGeneration)) {
            return; // A nested real reload installed a newer tree; do not reattach the old graph.
        }
        original.call(canonical, manager);
    }

    @WrapMethod(method = {
            "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
            "revoke(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z"},
            require = 2, expect = 2, allow = 2)
    private boolean p11$progressChanged(AdvancementHolder advancement, String criterion,
            Operation<Boolean> original) {
        var scope = P11NativeOperationBoundary.beginAdvancement((PlayerAdvancements) (Object) this, player);
        long previousHolderGeneration = p11$holderGeneration;
        boolean previousHolderActive = p11$holderActive;
        p11$holderGeneration = P11CanonicalAdvancements.listenerGeneration(
                (PlayerAdvancements) (Object) this, p11$treeGeneration);
        p11$holderActive = true;
        boolean normal = false;
        try {
            boolean changed = original.call(advancement, criterion);
            normal = true;
            return changed;
        } finally {
            p11$holderGeneration = previousHolderGeneration;
            p11$holderActive = previousHolderActive;
            P11NativeOperationBoundary.end(scope, normal);
        }
    }

    @WrapMethod(method = {
            "registerListeners(Lnet/minecraft/advancements/AdvancementHolder;)V",
            "unregisterListeners(Lnet/minecraft/advancements/AdvancementHolder;)V",
            "markForVisibilityUpdate(Lnet/minecraft/advancements/AdvancementHolder;)V"},
            require = 3, expect = 3, allow = 3)
    private void p11$currentHolderConsumer(AdvancementHolder holder, Operation<Void> original) {
        if (p11$currentHolder(holder)) { original.call(holder); }
    }

    @WrapMethod(method = "startProgress(Lnet/minecraft/advancements/AdvancementHolder;Lnet/minecraft/advancements/AdvancementProgress;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$currentProgress(AdvancementHolder holder, AdvancementProgress progress,
            Operation<Void> original) {
        if (p11$currentHolder(holder)) { original.call(holder, progress); }
        else {
            // A captured old native callback may finish using its own local progress, but
            // cannot publish the obsolete holder/progress into the new canonical graph.
            progress.update(holder.value().requirements());
        }
    }

    @WrapOperation(method = "getOrStartProgress(Lnet/minecraft/advancements/AdvancementHolder;)Lnet/minecraft/advancements/AdvancementProgress;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1, expect = 1, allow = 1)
    private Object p11$isolatedObsoleteRead(Map<AdvancementHolder, AdvancementProgress> progress,
            Object key, Operation<Object> original) {
        var value = (AdvancementProgress) original.call(progress, key);
        var holder = (AdvancementHolder) key;
        if (value == null || p11$currentHolder(holder)) { return value; }
        // Holder.equals is ID-based: a stale captured callback can hit the new tree's
        // progress without invoking startProgress. Keep its native body/reward execution
        // but never expose that new canonical mutable value to the obsolete callback.
        var current = tree.get(holder.id());
        return P11CanonicalAdvancements.detachedProgress(value,
                (current == null ? holder : current.holder()).value().requirements());
    }

    @WrapOperation(method = {
            "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
            "revoke(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z"},
            at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"),
            require = 2, expect = 2, allow = 2)
    private boolean p11$currentProgressChanged(Set<AdvancementHolder> changes, Object holder,
            Operation<Boolean> original) {
        return p11$currentHolder((AdvancementHolder) holder) && original.call(changes, holder);
    }

    @Unique
    private boolean p11$currentHolder(AdvancementHolder holder) {
        if (!P11NativeStorageBoundary.isManagedCanonicalAdvancements((PlayerAdvancements) (Object) this, player)) {
            return true;
        }
        return P11CanonicalAdvancements.currentHolder(tree, holder,
                p11$holderActive ? p11$holderGeneration : p11$treeGeneration,
                p11$treeGeneration);
    }

    @WrapMethod(method = "setPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$recipient(ServerPlayer next, Operation<Void> original) {
        var previous = player;
        original.call(next);
        if (previous != player) {
            p11$deliveryConnection = null;
            p11$delivery.requireInitial();
        }
    }

    @WrapMethod(method = "flushDirty(Lnet/minecraft/server/level/ServerPlayer;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$flush(ServerPlayer recipient, Operation<Void> original) {
        if (!P11NativeStorageBoundary.isManagedCanonicalAdvancements((PlayerAdvancements) (Object) this, player)) {
            original.call(recipient);
            return;
        }
        if (recipient != player || p11$flush != null || !p11$delivery.usable()) { return; }
        if (!P11NativeStorageBoundary.nativeDeliveryEligible(recipient)) {
            if (!p11$delivery.pending()) { p11$delivery.requireInitial(); }
            return;
        }
        var connection = recipient.connection.getConnection();
        if (p11$deliveryConnection != connection) {
            p11$deliveryConnection = connection;
            p11$delivery.requireInitial();
        }
        if (p11$delivery.pending()) {
            visible.clear();
            rootsToUpdate.clear();
            progressChanged.clear();
            lastSelectedTab = null;
            for (var root : tree.roots()) { rootsToUpdate.add(root); }
            isFirstPacket = true;
        }
        var scope = new Flush(recipient, connection, p11$treeGeneration,
                p11$delivery.generation(), p11$delivery.pending());
        p11$flush = scope;
        boolean normal = false;
        try {
            original.call(recipient);
            if (scope.initial && scope.submitted && p11$current(scope)) {
                // Native ClientAdvancements.update(reset=true) does not clear selectedTab.
                // This native presentation packet is part of the same initial obligation.
                recipient.connection.send(new ClientboundSelectAdvancementsTabPacket(null));
            }
            normal = true;
        } finally {
            p11$flush = null;
            if (!normal || !p11$current(scope) || (scope.initial && !scope.submitted)) {
                // The original clears delivery sets before send. Rebuild current state on
                // the next eligible flush instead of retaining/replaying the failed packet.
                if (p11$delivery.current(scope.deliveryGeneration)) { p11$delivery.requireInitial(); }
            } else if (scope.initial) {
                p11$delivery.submitted(scope.deliveryGeneration);
            }
            if (p11$delivery.pending()) { isFirstPacket = true; }
        }
    }

    @WrapOperation(method = "flushDirty(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$emptyReset(Map<?, ?> progress, Operation<Boolean> original) {
        boolean empty = original.call(progress);
        return empty && !(p11$flush != null && p11$flush.initial);
    }

    @WrapOperation(method = "flushDirty(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$submit(ServerGamePacketListenerImpl listener, Packet<?> packet, Operation<Void> original) {
        var scope = p11$flush;
        if (scope == null) { original.call(listener, packet); return; }
        if (!p11$current(scope) || scope.recipient.connection != listener) { return; }
        original.call(listener, packet);
        scope.submitted = packet instanceof ClientboundUpdateAdvancementsPacket advancement
                && (!scope.initial || advancement.shouldReset());
    }

    @Unique
    private boolean p11$current(Flush scope) {
        return player == scope.recipient && p11$treeGeneration == scope.treeGeneration
                && p11$delivery.current(scope.deliveryGeneration)
                && player.connection != null && player.connection.getConnection() == scope.connection
                && P11NativeStorageBoundary.nativeDeliveryEligible(player);
    }

    @WrapOperation(method = "setSelectedTab(Lnet/minecraft/advancements/AdvancementHolder;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$tabPresentation(ServerGamePacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original) {
        if (P11NativeStorageBoundary.isManagedCanonicalAdvancements((PlayerAdvancements) (Object) this, player)
                && !P11NativeStorageBoundary.nativeDeliveryEligible(player)) {
            p11$delivery.requireInitial();
            return;
        }
        original.call(listener, packet);
    }

    @WrapMethod(method = "save()V", require = 1, expect = 1, allow = 1)
    private void p11$save(Operation<Void> original) {
        var scope = P11NativeStorageBoundary.beginAdvancements(
                (PlayerAdvancements) (Object) this, player, playerSavePath);
        if (!P11NativeStorageBoundary.independentPermitted(scope)) {
            P11NativeStorageBoundary.endIndependent(scope, false);
            return;
        }
        boolean normal = false;
        try {
            original.call();
            normal = true;
        } finally {
            // The native method swallows some write/close failures. Normal return alone is
            // never a success receipt; only the stage observations below provide evidence.
            P11NativeStorageBoundary.endIndependent(scope, normal);
        }
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/FileUtil;createDirectoriesSafe(Ljava/nio/file/Path;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$afterEncoding(Path parent, Operation<Void> original) throws IOException {
        // Reaching this first IO call proves the original codec encode/getOrThrow completed.
        // An earlier codec exception never reaches here and leaves encoding UNKNOWN, not
        // an invented failed stage. Directory creation itself is not a write/close receipt.
        P11NativeStorageBoundary.independentEncoded(true);
        original.call(parent);
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/gson/Gson;toJson(Lcom/google/gson/JsonElement;Lcom/google/gson/stream/JsonWriter;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$write(Gson gson, JsonElement encoded, JsonWriter writer,
            Operation<Void> original) {
        boolean normal = false;
        try {
            original.call(gson, encoded, writer);
            normal = true;
        } finally {
            P11NativeStorageBoundary.independentWritten(normal);
        }
    }

    @WrapOperation(method = "save()V",
            at = @At(value = "INVOKE", target = "Ljava/io/Writer;close()V"),
            require = 2, expect = 2, allow = 2)
    private void p11$close(Writer writer, Operation<Void> original) throws IOException {
        boolean normal = false;
        try {
            original.call(writer);
            normal = true;
        } finally {
            P11NativeStorageBoundary.independentClosed(normal);
        }
    }
}
