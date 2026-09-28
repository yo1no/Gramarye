package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;

/** Named native presence endpoints only; source custody remains in the foundation owner. */
public final class P11NativePresence {
    private static final ThreadLocal<Outcome> CALLER = new ThreadLocal<>();
    private static final DynamicCommandExceptionType UNAVAILABLE = new DynamicCommandExceptionType(
            operation -> Component.literal("Gramarye: " + operation + " unavailable for a detached player"));

    private P11NativePresence() {}

    /** Called after the endpoint's native pre-event and before its first side effect. */
    public static boolean denied(Entity actor, String operation) {
        if (!(actor instanceof ServerPlayer player)
                || !P11NativeStorageBoundary.detachedPresence(player)) { return false; }
        for (var outcome = CALLER.get(); outcome != null; outcome = outcome.previous) {
            if (outcome.actor == actor && outcome.operation.equals(operation)) {
                outcome.denied = true;
                break;
            }
        }
        return true;
    }

    /** Void native helpers cannot truthfully report successful movement by silently returning. */
    public static void require(Entity actor, String operation) {
        if (denied(actor, operation)) { throw new Unavailable(operation); }
    }

    /** Command-only typed failure; invoked after that command's original pre-event/validation. */
    public static void requireCommand(Entity actor, String operation) throws CommandSyntaxException {
        if (denied(actor, operation)) { throw UNAVAILABLE.create(operation); }
    }

    public static boolean gameMode(CommandSourceStack source, ServerPlayer player,
            net.minecraft.world.level.GameType mode, Operation<Boolean> original) {
        var outcome = new Outcome(player, "gamemode", CALLER.get());
        CALLER.set(outcome);
        try {
            boolean result = original.call(player, mode);
            if (outcome.denied) {
                source.sendFailure(Component.literal("Gramarye: gamemode unavailable for a detached player"));
                return false;
            }
            return result;
        } finally { restore(outcome); }
    }

    public static boolean ride(Entity actor, Entity vehicle, boolean force,
            Operation<Boolean> original) throws CommandSyntaxException {
        var outcome = new Outcome(actor, "ride", CALLER.get());
        CALLER.set(outcome);
        try {
            boolean result = original.call(actor, vehicle, force);
            if (outcome.denied) { throw UNAVAILABLE.create("ride"); }
            return result;
        } finally { restore(outcome); }
    }

    /** Only the transient menu slots are wrapped; persistent inventory/ender access is native. */
    public static SlotAccess slot(Entity actor, int index, SlotAccess original) {
        if (!(actor instanceof ServerPlayer) || !transientSlot(index)) { return original; }
        return new SlotAccess() {
            @Override public ItemStack get() { return original.get(); }
            @Override public boolean set(ItemStack value) {
                return !denied(actor, "menu_slot") && original.set(value);
            }
        };
    }

    static boolean transientSlot(int index) { return index == 499 || (index >= 500 && index <= 503); }

    private static void restore(Outcome outcome) {
        if (outcome.previous == null) { CALLER.remove(); }
        else { CALLER.set(outcome.previous); }
    }

    private static final class Outcome {
        final Entity actor;
        final String operation;
        final Outcome previous;
        boolean denied;
        Outcome(Entity actor, String operation, Outcome previous) {
            this.actor = actor; this.operation = operation; this.previous = previous;
        }
    }

    public static final class Unavailable extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private Unavailable(String operation) { super("P11_DETACHED_UNAVAILABLE:" + operation); }
    }
}
