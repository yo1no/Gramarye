package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Excluded exact-C first-cause window. Fixed metadata only; no text, payload or account reads. */
public final class P11C4aConfigPrimaryProbe {
    public enum Stage {
        CONNECTION_EXCEPTION, DIRECT_DISPATCH_FAILURE, PACKET_UTILS_ERROR, LIVE_PACKET_ERROR,
        COMMON_DISCONNECT, CONNECTION_DISCONNECT, HANDLE_DISCONNECTION, RELOAD_OWNER_FAILURE,
        CONFIG_FINISH_SUBMIT, STATE_AFTER_CONFIG_FINISH
    }
    private static volatile Run active;
    private P11C4aConfigPrimaryProbe() { }

    /** Parent arms before normal step4 begins on each dedicated process, with its existing output. */
    static void arm(Connection exact, Path output, boolean serverSide) {
        arm(exact, output, serverSide, "config-primary");
    }
    static void armSender(Connection exact, Path output, boolean serverSide) {
        P11C4aEvidence.require(P11C4aNativeSenderProbe.selected(), "SENDER_PRIMARY_EXACT_MODE");
        arm(exact, output, serverSide, "native-sender-primary");
    }
    static void armParkingReset(Connection exact, Path output, boolean serverSide) {
        P11C4aEvidence.require(P11C4aConfigParkingResetProbe.selected(), "CP_PRIMARY_EXACT_MODE");
        arm(exact, output, serverSide, "config-parking-primary");
    }
    private static void arm(Connection exact, Path output, boolean serverSide, String prefix) {
        P11C4aEvidence.require(active == null && exact != null && exact.isConnected()
                && !exact.isMemoryConnection() && exact.isEncrypted()
                && output != null, "CONFIG_PRIMARY_EXACT_ARM");
        active = new Run(exact, output, serverSide, prefix);
        try {P11C4aEvidence.write(output,prefix+"-armed.json",Map.of("status","EXACT_CONNECTION_DIAGNOSTIC_ARMED_NOT_ACCEPTANCE","serverSide",serverSide));}
        catch(IOException|RuntimeException|Error secondary) {failed();}
    }
    /** Success only ends this diagnostic window; never creates an acceptance verdict. */
    static void finish(Connection exact) {
        var run=active;if(run!=null && exact==run.connection) {
            try {P11C4aEvidence.write(run.output,run.prefix+"-window.json",Map.of("status","DIAGNOSTIC_WINDOW_CLOSED_NOT_ACCEPTANCE",
                    "stagesObserved",run.sequence,"diagnosticFailures",run.failures,"originalProducerDiagnostic",producerDiagnostic(run)));}
            catch(IOException|RuntimeException|Error secondary) {failed();}
            active=null;
        }
    }

    /** Existing Common.send -> Connection.send callsite, before the original asynchronous enqueue. */
    public static void scheduling(ServerCommonPacketListenerImpl caller, net.minecraft.network.protocol.Packet<?> packet) {
        try {
            var run = active;
            if (run == null || !run.serverSide || caller.getConnection() != run.connection) { return; }
            var kind = packetKind(packet.getClass().getName());
            if (kind == PacketKind.PLAY_START_CONFIGURATION || kind == PacketKind.CONFIG_FINISH_CLIENTBOUND) {
                synchronized (run) {
                    run.producerWindow = kind == PacketKind.PLAY_START_CONFIGURATION
                            ? ProducerWindow.AFTER_START_CALLSITE : ProducerWindow.AFTER_FINISH_CALLSITE;
                }
                return;
            }
            ProducerWindow window;
            synchronized (run) { window = run.producerWindow; }
            if (!producerEligible(window, kind)) { return; }
            var current = run.connection.getPacketListener();
            var encoder = run.connection.channel().pipeline().get(net.minecraft.network.PacketEncoder.class);
            var encoderProtocol = encoder == null ? "NONE" : protocolCode(encoder.getProtocolInfo().id());
            var sites = producerSites(Thread.currentThread().getStackTrace());
            boolean game = caller instanceof net.minecraft.server.network.ServerGamePacketListenerImpl;
            boolean removed = game && ((net.minecraft.server.network.ServerGamePacketListenerImpl) caller).player.isRemoved();
            boolean disconnected = game && ((net.minecraft.server.network.ServerGamePacketListenerImpl) caller).player.hasDisconnected();
            synchronized (run) {
                if (run.producerSequence != Long.MAX_VALUE) { run.producerSequence++; }
                var value = new Producer(run.producerSequence, window, kind, sites,
                        listenerCode(caller), listenerCode(current), caller == current,
                        encoderProtocol, caller.getMainThreadEventLoop().isSameThread(), game, removed, disconnected);
                run.lastProducer = value;
                // This is an observed header/encoder mismatch at the producer callsite, not an error attribution.
                if (producerMismatch(window, kind, encoderProtocol) && run.firstProducerMismatch == null) {
                    run.firstProducerMismatch = value;
                }
            }
        } catch (RuntimeException | Error secondary) { failed(); }
    }

    enum ProducerWindow { NONE, AFTER_START_CALLSITE, AFTER_FINISH_CALLSITE }
    enum ProducerSite {
        SERVER_GAME_TICK, SERVER_PLAYER_DO_TICK, SERVER_PLAYER_WORLD_TICK, PLAYER_BASE_TICK,
        SERVER_PLAYER_SYSTEM_MESSAGE, SERVER_PLAYER_ABILITIES, SERVER_ENTITY_SEND_CHANGES,
        PLAYER_LIST_BROADCAST, SERVER_TICK_CHILDREN, SERVER_TICK, P11_NOTIFY_LATEST
    }
    static boolean producerEligible(ProducerWindow window, PacketKind kind) {
        return window != ProducerWindow.NONE && kind != PacketKind.PLAY_START_CONFIGURATION
                && kind.name().startsWith("PLAY_");
    }
    static boolean producerMismatch(ProducerWindow window, PacketKind kind, String encoder) {
        return producerEligible(window, kind) && !"PLAY".equals(encoder);
    }
    static List<ProducerSite> producerSites(StackTraceElement[] trace) {
        var result = new ArrayList<ProducerSite>();
        for (var frame : trace) {
            if (result.size() == 8) { break; }
            ProducerSite site = switch (frame.getClassName()) {
                case "net.minecraft.server.network.ServerGamePacketListenerImpl" ->
                        frame.getMethodName().equals("tick") ? ProducerSite.SERVER_GAME_TICK : null;
                case "net.minecraft.server.level.ServerPlayer" -> switch (frame.getMethodName()) {
                    case "doTick" -> ProducerSite.SERVER_PLAYER_DO_TICK;
                    case "tick" -> ProducerSite.SERVER_PLAYER_WORLD_TICK;
                    case "sendSystemMessage" -> ProducerSite.SERVER_PLAYER_SYSTEM_MESSAGE;
                    case "onUpdateAbilities" -> ProducerSite.SERVER_PLAYER_ABILITIES;
                    default -> null;
                };
                case "net.minecraft.world.entity.player.Player" ->
                        frame.getMethodName().equals("tick") ? ProducerSite.PLAYER_BASE_TICK : null;
                case "net.minecraft.server.level.ServerEntity" ->
                        frame.getMethodName().equals("sendChanges") ? ProducerSite.SERVER_ENTITY_SEND_CHANGES : null;
                case "net.minecraft.server.players.PlayerList" -> switch (frame.getMethodName()) {
                    case "broadcastAll", "broadcast", "broadcastSystemMessage" -> ProducerSite.PLAYER_LIST_BROADCAST;
                    default -> null;
                };
                case "net.minecraft.server.MinecraftServer" -> switch (frame.getMethodName()) {
                    case "tickChildren" -> ProducerSite.SERVER_TICK_CHILDREN;
                    case "tickServer" -> ProducerSite.SERVER_TICK;
                    default -> null;
                };
                case "com.yo1no.gramarye.P11LiveTransitionService" ->
                        frame.getMethodName().equals("notifyLatest") ? ProducerSite.P11_NOTIFY_LATEST : null;
                default -> null;
            };
            if (site != null && !result.contains(site)) { result.add(site); }
        }
        return List.copyOf(result);
    }
    private static Map<String,Object> producerDiagnostic(Run run) {
        Producer first, last; ProducerWindow window;
        synchronized (run) { first=run.firstProducerMismatch; last=run.lastProducer; window=run.producerWindow; }
        return Map.of("terminalCallsiteWindow",window.name(),"firstHeaderEncoderMismatch",producerValue(first),
                "lastKnownBuiltinPlayProducer",producerValue(last),"samePacketAsDoSendOrExceptionProven",false,
                "enqueueOrSendSuccessProven",false,"protocolSamplesAreNotAtomic",true,
                "directConnectionSendBypassesThisCallsite",true,"packetOrCallbackRetained",false);
    }
    private static Map<String,Object> producerValue(Producer value) {
        if (value == null) { return Map.of("present",false); }
        var result = new LinkedHashMap<String,Object>();
        result.put("present",true);result.put("sequence",value.sequence());result.put("terminalWindow",value.window().name());
        result.put("packetKind",value.packet().name());result.put("fixedCallerSites",value.sites());
        result.put("callerListener",value.caller());result.put("currentListener",value.current());
        result.put("callerIsCurrent",value.callerCurrent());result.put("encoderProtocol",value.encoder());
        result.put("serverThread",value.serverThread());result.put("callerHasGamePlayer",value.game());
        result.put("gamePlayerRemoved",value.removed());result.put("gamePlayerDisconnected",value.disconnected());
        return result;
    }

    /** Observes the actual native doSendPacket invocation, never changes or retains a packet. */
    public static void submitting(Connection exact, net.minecraft.network.protocol.Packet<?> packet) {
        try {
            var run = active;
            if (run == null || exact != run.connection) { return; }
            // Fixed header only. No packet/payload graph survives the current callback.
            var packetKind = packetKind(packet.getClass().getName());
            var customKind = customKind(packet);
            var listener = exact.getPacketListener();
            var listenerKind = listenerCode(listener);
            var listenerProtocol = protocolCode(listener);
            var encoder = exact.channel().pipeline().get(net.minecraft.network.PacketEncoder.class);
            var encoderProtocol = encoder == null ? "NONE" : protocolCode(encoder.getProtocolInfo().id());
            synchronized (run) {
                if (run.submissionSequence != Long.MAX_VALUE) { run.submissionSequence++; }
                run.lastSubmission = new Submission(run.submissionSequence, packetKind, customKind,
                        listenerKind, listenerProtocol, encoderProtocol);
            }
            if (!run.prefix.equals("config-parking-primary")) { return; }
            Stage stage;
            synchronized (run) {
                if (packet instanceof net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket) {
                    if (run.finishSubmits != Integer.MAX_VALUE) { run.finishSubmits++; }
                    stage = Stage.CONFIG_FINISH_SUBMIT;
                } else if (run.finishSubmits > 0
                        && packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof P11TransitionStatePayload) {
                    if (run.stateSubmitsAfterFinish != Integer.MAX_VALUE) { run.stateSubmitsAfterFinish++; }
                    stage = Stage.STATE_AFTER_CONFIG_FINISH;
                } else { return; }
            }
            event(stage, exact, exact.getPacketListener(), null);
        } catch (RuntimeException | Error secondary) { failed(); }
    }

    public static void listener(Stage stage, PacketListener caller, Throwable failure) {
        try {
            if(caller instanceof ICommonPacketListener common) {event(stage,common.getConnection(),caller,failure);}
        } catch(RuntimeException|Error secondary) {failed();}
    }
    public static void connection(Stage stage, Connection exact, Throwable failure) {
        try {event(stage,exact,exact.getPacketListener(),failure);}
        catch(RuntimeException|Error secondary) {failed();}
    }
    private static void event(Stage stage, Connection exact, PacketListener captured, Throwable failure) {
        var run=active;
        if(run==null || exact!=run.connection || failure instanceof net.minecraft.server.RunningOnDifferentThreadException) {return;}
        int sequence;
        synchronized(run) {
            if(run.seen[stage.ordinal()]) {return;}
            run.seen[stage.ordinal()]=true; sequence=++run.sequence;
        }
        try {
            var current=exact.getPacketListener();
            var values=new LinkedHashMap<String,Object>();
            values.put("status","ORIGINAL_NATIVE_BOUNDARY_DIAGNOSTIC_NOT_ACCEPTANCE");
            values.put("stage",stage.name());values.put("sequence",sequence);values.put("serverSide",run.serverSide);
            values.put("connectionStillOpen",exact.isConnected());values.put("currentListener",listenerCode(current));
            values.put("capturedListener",listenerCode(captured));values.put("capturedIsCurrent",captured==current);
            values.put("currentProtocol",protocolCode(current));
            Submission submitted;
            synchronized (run) { submitted=run.lastSubmission; }
            values.put("lastOriginalDoSendPacket",submitted==null?Map.of("present",false):Map.of(
                    "present",true,"sequence",submitted.sequence(),"packetKind",submitted.packet().name(),
                    "knownCustomHeader",submitted.custom().name(),"listenerAtSubmit",submitted.listener(),
                    "listenerProtocolAtSubmit",submitted.protocol(),"encoderProtocolAtSubmit",submitted.encoderProtocol()));
            values.put("lastSubmissionIsNotGuaranteedThrowingPacket",true);
            values.put("submissionProtocolSamplesAreNotAtomic",true);
            values.put("originalProducerDiagnostic",producerDiagnostic(run));
            if (run.prefix.equals("config-parking-primary")) {
                var pipeline = exact.channel().pipeline();
                values.put("nativeOutboundEncoderPresent", pipeline.get(net.minecraft.network.PacketEncoder.class) != null);
                values.put("nativeOutboundUnconfiguredPresent", pipeline.get(net.minecraft.network.UnconfiguredPipelineHandler.Outbound.class) != null);
                synchronized (run) {
                    values.put("originalFinishSubmitCalls", run.finishSubmits);
                    values.put("stateSubmitCallsAfterFinish", run.stateSubmitsAfterFinish);
                }
                values.put("pipelineSamplesAreDiagnosticNotAtomicSnapshot", true);
            }
            values.put("serverThread",captured instanceof ServerCommonPacketListenerImpl common
                    && common.getMainThreadEventLoop().isSameThread());
            values.put("throwablePresent",failure!=null);values.put("throwableChain",causes(failure));
            values.put("nativeCallingFrames",frames(Thread.currentThread().getStackTrace()));
            values.put("nativeAndWireCounters",P11C4aNativeObservations.snapshot(exact));
            values.put("diagnosticFailures",run.failures);values.put("exceptionOrDisconnectTextRead",false);
            P11C4aEvidence.write(run.output,run.prefix+"-"+stage.name().toLowerCase(java.util.Locale.ROOT).replace('_','-')+".json",values);
        } catch(IOException|RuntimeException|Error secondary) {failed();}
    }
    private static void failed() {var run=active;if(run!=null) {synchronized(run) {if(run.failures!=Integer.MAX_VALUE) {run.failures++;}}}}

    private static List<Map<String,Object>> causes(Throwable first) {
        var result=new ArrayList<Map<String,Object>>();
        var seen=new java.util.IdentityHashMap<Throwable,Boolean>();
        for(var value=first;value!=null && result.size()<4 && seen.put(value,true)==null;value=value.getCause()) {
            result.add(Map.of("category",category(value),"publicSourceFrames",frames(value.getStackTrace())));
        }
        return List.copyOf(result);
    }
    static String category(Throwable failure) {
        return failure instanceof net.minecraft.ReportedException ? "REPORTED_EXCEPTION"
            : failure instanceof io.netty.handler.codec.DecoderException ? "DECODER_EXCEPTION"
            : failure instanceof io.netty.handler.codec.EncoderException ? "ENCODER_EXCEPTION"
            : failure instanceof io.netty.handler.timeout.TimeoutException ? "NETWORK_TIMEOUT"
            : failure instanceof java.util.concurrent.TimeoutException ? "FUTURE_TIMEOUT"
            : failure instanceof java.util.concurrent.CompletionException ? "FUTURE_COMPLETION"
            : failure instanceof java.util.concurrent.RejectedExecutionException ? "REJECTED_EXECUTION"
            : failure instanceof java.nio.channels.ClosedChannelException ? "CLOSED_CHANNEL"
            : failure instanceof IOException ? "IO_EXCEPTION"
            : failure instanceof ClassCastException ? "CLASS_CAST"
            : failure instanceof IllegalStateException ? "ILLEGAL_STATE"
            : failure instanceof IllegalArgumentException ? "ILLEGAL_ARGUMENT"
            : failure instanceof NullPointerException ? "NULL_POINTER"
            : failure instanceof java.util.NoSuchElementException ? "NO_SUCH_ELEMENT"
            : failure instanceof LinkageError ? "LINKAGE_ERROR"
            : failure instanceof OutOfMemoryError ? "OUT_OF_MEMORY"
            : failure instanceof StackOverflowError ? "STACK_OVERFLOW"
            : failure instanceof Error ? "OTHER_ERROR" : "OTHER_EXCEPTION";
    }
    private static String listenerCode(PacketListener listener) {
        if(listener==null) {return "NONE";}
        return switch(listener.getClass().getName()) {
            case "net.minecraft.server.network.ServerGamePacketListenerImpl" -> "SERVER_GAME";
            case "net.minecraft.server.network.ServerConfigurationPacketListenerImpl" -> "SERVER_CONFIG";
            case "com.yo1no.gramarye.P11ParkingPacketListener" -> "SERVER_PARKING";
            case "net.minecraft.client.multiplayer.ClientPacketListener" -> "CLIENT_GAME";
            case "net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl" -> "CLIENT_CONFIG";
            default -> "OTHER";
        };
    }
    private static String protocolCode(PacketListener listener) {
        if(listener==null) {return "NONE";}
        return protocolCode(listener.protocol());
    }
    private static String protocolCode(net.minecraft.network.ConnectionProtocol protocol) {
        return switch(protocol) {case PLAY -> "PLAY";case CONFIGURATION -> "CONFIGURATION";default -> "OTHER";};
    }
    enum PacketKind {
        COMMON_CUSTOM_CLIENTBOUND,COMMON_CUSTOM_SERVERBOUND,CONFIG_FINISH_CLIENTBOUND,CONFIG_FINISH_SERVERBOUND,
        PLAY_START_CONFIGURATION,COMMON_KEEPALIVE_CLIENTBOUND,COMMON_KEEPALIVE_SERVERBOUND,COMMON_DISCONNECT,
        PLAY_LOGIN,PLAY_RESPAWN,PLAY_ADVANCEMENTS,PLAY_RECIPES,PLAY_STATS,PLAY_SYSTEM_CHAT,PLAY_PLAYER_CHAT,
        PLAY_PLAYER_INFO_UPDATE,PLAY_PLAYER_INFO_REMOVE,PLAY_EXPERIENCE,PLAY_HEALTH,PLAY_ENTITY_DATA,PLAY_TIME,
        PLAY_GAME_EVENT,PLAY_BUNDLE,PLAY_BUNDLE_DELIMITER,
        // Exact additional built-in clientbound classes registered by locked GameProtocols.
        PLAY_ADD_ENTITY,
        PLAY_ADD_EXPERIENCE_ORB,
        PLAY_ANIMATE,
        PLAY_BLOCK_CHANGED_ACK,
        PLAY_BLOCK_DESTRUCTION,
        PLAY_BLOCK_ENTITY_DATA,
        PLAY_BLOCK_EVENT,
        PLAY_BLOCK_UPDATE,
        PLAY_BOSS_EVENT,
        PLAY_CHANGE_DIFFICULTY,
        PLAY_CHUNK_BATCH_FINISHED,
        PLAY_CHUNK_BATCH_START,
        PLAY_CHUNKS_BIOMES,
        PLAY_CLEAR_TITLES,
        PLAY_COMMAND_SUGGESTIONS,
        PLAY_COMMANDS,
        PLAY_CONTAINER_CLOSE,
        PLAY_CONTAINER_SET_CONTENT,
        PLAY_CONTAINER_SET_DATA,
        PLAY_CONTAINER_SET_SLOT,
        COOKIE_COOKIE_REQUEST,
        PLAY_COOLDOWN,
        PLAY_CUSTOM_CHAT_COMPLETIONS,
        PLAY_DAMAGE_EVENT,
        PLAY_DEBUG_SAMPLE,
        PLAY_DELETE_CHAT,
        PLAY_DISGUISED_CHAT,
        PLAY_ENTITY_EVENT,
        PLAY_EXPLODE,
        PLAY_FORGET_LEVEL_CHUNK,
        PLAY_HORSE_SCREEN_OPEN,
        PLAY_HURT_ANIMATION,
        PLAY_INITIALIZE_BORDER,
        PLAY_LEVEL_CHUNK_WITH_LIGHT,
        PLAY_LEVEL_EVENT,
        PLAY_LEVEL_PARTICLES,
        PLAY_LIGHT_UPDATE,
        PLAY_MAP_ITEM_DATA,
        PLAY_MERCHANT_OFFERS,
        PLAY_MOVE_ENTITY_POS,
        PLAY_MOVE_ENTITY_POS_ROT,
        PLAY_MOVE_ENTITY_ROT,
        PLAY_MOVE_VEHICLE,
        PLAY_OPEN_BOOK,
        PLAY_OPEN_SCREEN,
        PLAY_OPEN_SIGN_EDITOR,
        COMMON_PING,
        PING_PONG_RESPONSE,
        PLAY_PLACE_GHOST_RECIPE,
        PLAY_PLAYER_ABILITIES,
        PLAY_PLAYER_COMBAT_END,
        PLAY_PLAYER_COMBAT_ENTER,
        PLAY_PLAYER_COMBAT_KILL,
        PLAY_PLAYER_LOOK_AT,
        PLAY_PLAYER_POSITION,
        PLAY_REMOVE_ENTITIES,
        PLAY_REMOVE_MOB_EFFECT,
        PLAY_RESET_SCORE,
        COMMON_RESOURCE_PACK_POP,
        COMMON_RESOURCE_PACK_PUSH,
        PLAY_ROTATE_HEAD,
        PLAY_SECTION_BLOCKS_UPDATE,
        PLAY_SELECT_ADVANCEMENTS_TAB,
        PLAY_SERVER_DATA,
        PLAY_SET_ACTION_BAR_TEXT,
        PLAY_SET_BORDER_CENTER,
        PLAY_SET_BORDER_LERP_SIZE,
        PLAY_SET_BORDER_SIZE,
        PLAY_SET_BORDER_WARNING_DELAY,
        PLAY_SET_BORDER_WARNING_DISTANCE,
        PLAY_SET_CAMERA,
        PLAY_SET_CARRIED_ITEM,
        PLAY_SET_CHUNK_CACHE_CENTER,
        PLAY_SET_CHUNK_CACHE_RADIUS,
        PLAY_SET_DEFAULT_SPAWN_POSITION,
        PLAY_SET_DISPLAY_OBJECTIVE,
        PLAY_SET_ENTITY_LINK,
        PLAY_SET_ENTITY_MOTION,
        PLAY_SET_EQUIPMENT,
        PLAY_SET_OBJECTIVE,
        PLAY_SET_PASSENGERS,
        PLAY_SET_PLAYER_TEAM,
        PLAY_SET_SCORE,
        PLAY_SET_SIMULATION_DISTANCE,
        PLAY_SET_SUBTITLE_TEXT,
        PLAY_SET_TITLE_TEXT,
        PLAY_SET_TITLES_ANIMATION,
        PLAY_SOUND_ENTITY,
        PLAY_SOUND,
        PLAY_STOP_SOUND,
        COMMON_STORE_COOKIE,
        PLAY_TAB_LIST,
        PLAY_TAG_QUERY,
        PLAY_TAKE_ITEM_ENTITY,
        PLAY_TELEPORT_ENTITY,
        PLAY_TICKING_STATE,
        PLAY_TICKING_STEP,
        COMMON_TRANSFER,
        PLAY_UPDATE_ATTRIBUTES,
        PLAY_UPDATE_MOB_EFFECT,
        PLAY_UPDATE_RECIPES,
        COMMON_UPDATE_TAGS,
        PLAY_PROJECTILE_POWER,
        COMMON_CUSTOM_REPORT_DETAILS,
        COMMON_SERVER_LINKS,
        OTHER
    }
    enum CustomKind { NONE,P11_STATE,P11_REQUEST,P7_MANA,P7_COOLDOWN,P7_ACK,P7_CAST_INTENT,P8_PROFILE,P8_PRESENTATION,KNOWN_HEADER_MISMATCH,OTHER_CUSTOM }
    static PacketKind packetKind(String name) {
        return switch(name) {
            case "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket" -> PacketKind.COMMON_CUSTOM_CLIENTBOUND;
            case "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket" -> PacketKind.COMMON_CUSTOM_SERVERBOUND;
            case "net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket" -> PacketKind.CONFIG_FINISH_CLIENTBOUND;
            case "net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket" -> PacketKind.CONFIG_FINISH_SERVERBOUND;
            case "net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket" -> PacketKind.PLAY_START_CONFIGURATION;
            case "net.minecraft.network.protocol.common.ClientboundKeepAlivePacket" -> PacketKind.COMMON_KEEPALIVE_CLIENTBOUND;
            case "net.minecraft.network.protocol.common.ServerboundKeepAlivePacket" -> PacketKind.COMMON_KEEPALIVE_SERVERBOUND;
            case "net.minecraft.network.protocol.common.ClientboundDisconnectPacket" -> PacketKind.COMMON_DISCONNECT;
            case "net.minecraft.network.protocol.game.ClientboundLoginPacket" -> PacketKind.PLAY_LOGIN;
            case "net.minecraft.network.protocol.game.ClientboundRespawnPacket" -> PacketKind.PLAY_RESPAWN;
            case "net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket" -> PacketKind.PLAY_ADVANCEMENTS;
            case "net.minecraft.network.protocol.game.ClientboundRecipePacket" -> PacketKind.PLAY_RECIPES;
            case "net.minecraft.network.protocol.game.ClientboundAwardStatsPacket" -> PacketKind.PLAY_STATS;
            case "net.minecraft.network.protocol.game.ClientboundSystemChatPacket" -> PacketKind.PLAY_SYSTEM_CHAT;
            case "net.minecraft.network.protocol.game.ClientboundPlayerChatPacket" -> PacketKind.PLAY_PLAYER_CHAT;
            case "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket" -> PacketKind.PLAY_PLAYER_INFO_UPDATE;
            case "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket" -> PacketKind.PLAY_PLAYER_INFO_REMOVE;
            case "net.minecraft.network.protocol.game.ClientboundSetExperiencePacket" -> PacketKind.PLAY_EXPERIENCE;
            case "net.minecraft.network.protocol.game.ClientboundSetHealthPacket" -> PacketKind.PLAY_HEALTH;
            case "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket" -> PacketKind.PLAY_ENTITY_DATA;
            case "net.minecraft.network.protocol.game.ClientboundSetTimePacket" -> PacketKind.PLAY_TIME;
            case "net.minecraft.network.protocol.game.ClientboundGameEventPacket" -> PacketKind.PLAY_GAME_EVENT;
            case "net.minecraft.network.protocol.game.ClientboundBundlePacket" -> PacketKind.PLAY_BUNDLE;
            case "net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket" -> PacketKind.PLAY_BUNDLE_DELIMITER;
            case "net.minecraft.network.protocol.game.ClientboundAddEntityPacket" -> PacketKind.PLAY_ADD_ENTITY;
            case "net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket" -> PacketKind.PLAY_ADD_EXPERIENCE_ORB;
            case "net.minecraft.network.protocol.game.ClientboundAnimatePacket" -> PacketKind.PLAY_ANIMATE;
            case "net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket" -> PacketKind.PLAY_BLOCK_CHANGED_ACK;
            case "net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket" -> PacketKind.PLAY_BLOCK_DESTRUCTION;
            case "net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket" -> PacketKind.PLAY_BLOCK_ENTITY_DATA;
            case "net.minecraft.network.protocol.game.ClientboundBlockEventPacket" -> PacketKind.PLAY_BLOCK_EVENT;
            case "net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket" -> PacketKind.PLAY_BLOCK_UPDATE;
            case "net.minecraft.network.protocol.game.ClientboundBossEventPacket" -> PacketKind.PLAY_BOSS_EVENT;
            case "net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket" -> PacketKind.PLAY_CHANGE_DIFFICULTY;
            case "net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket" -> PacketKind.PLAY_CHUNK_BATCH_FINISHED;
            case "net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket" -> PacketKind.PLAY_CHUNK_BATCH_START;
            case "net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket" -> PacketKind.PLAY_CHUNKS_BIOMES;
            case "net.minecraft.network.protocol.game.ClientboundClearTitlesPacket" -> PacketKind.PLAY_CLEAR_TITLES;
            case "net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket" -> PacketKind.PLAY_COMMAND_SUGGESTIONS;
            case "net.minecraft.network.protocol.game.ClientboundCommandsPacket" -> PacketKind.PLAY_COMMANDS;
            case "net.minecraft.network.protocol.game.ClientboundContainerClosePacket" -> PacketKind.PLAY_CONTAINER_CLOSE;
            case "net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket" -> PacketKind.PLAY_CONTAINER_SET_CONTENT;
            case "net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket" -> PacketKind.PLAY_CONTAINER_SET_DATA;
            case "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket" -> PacketKind.PLAY_CONTAINER_SET_SLOT;
            case "net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket" -> PacketKind.COOKIE_COOKIE_REQUEST;
            case "net.minecraft.network.protocol.game.ClientboundCooldownPacket" -> PacketKind.PLAY_COOLDOWN;
            case "net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket" -> PacketKind.PLAY_CUSTOM_CHAT_COMPLETIONS;
            case "net.minecraft.network.protocol.game.ClientboundDamageEventPacket" -> PacketKind.PLAY_DAMAGE_EVENT;
            case "net.minecraft.network.protocol.game.ClientboundDebugSamplePacket" -> PacketKind.PLAY_DEBUG_SAMPLE;
            case "net.minecraft.network.protocol.game.ClientboundDeleteChatPacket" -> PacketKind.PLAY_DELETE_CHAT;
            case "net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket" -> PacketKind.PLAY_DISGUISED_CHAT;
            case "net.minecraft.network.protocol.game.ClientboundEntityEventPacket" -> PacketKind.PLAY_ENTITY_EVENT;
            case "net.minecraft.network.protocol.game.ClientboundExplodePacket" -> PacketKind.PLAY_EXPLODE;
            case "net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket" -> PacketKind.PLAY_FORGET_LEVEL_CHUNK;
            case "net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket" -> PacketKind.PLAY_HORSE_SCREEN_OPEN;
            case "net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket" -> PacketKind.PLAY_HURT_ANIMATION;
            case "net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket" -> PacketKind.PLAY_INITIALIZE_BORDER;
            case "net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket" -> PacketKind.PLAY_LEVEL_CHUNK_WITH_LIGHT;
            case "net.minecraft.network.protocol.game.ClientboundLevelEventPacket" -> PacketKind.PLAY_LEVEL_EVENT;
            case "net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket" -> PacketKind.PLAY_LEVEL_PARTICLES;
            case "net.minecraft.network.protocol.game.ClientboundLightUpdatePacket" -> PacketKind.PLAY_LIGHT_UPDATE;
            case "net.minecraft.network.protocol.game.ClientboundMapItemDataPacket" -> PacketKind.PLAY_MAP_ITEM_DATA;
            case "net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket" -> PacketKind.PLAY_MERCHANT_OFFERS;
            case "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Pos" -> PacketKind.PLAY_MOVE_ENTITY_POS;
            case "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$PosRot" -> PacketKind.PLAY_MOVE_ENTITY_POS_ROT;
            case "net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot" -> PacketKind.PLAY_MOVE_ENTITY_ROT;
            case "net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket" -> PacketKind.PLAY_MOVE_VEHICLE;
            case "net.minecraft.network.protocol.game.ClientboundOpenBookPacket" -> PacketKind.PLAY_OPEN_BOOK;
            case "net.minecraft.network.protocol.game.ClientboundOpenScreenPacket" -> PacketKind.PLAY_OPEN_SCREEN;
            case "net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket" -> PacketKind.PLAY_OPEN_SIGN_EDITOR;
            case "net.minecraft.network.protocol.common.ClientboundPingPacket" -> PacketKind.COMMON_PING;
            case "net.minecraft.network.protocol.ping.ClientboundPongResponsePacket" -> PacketKind.PING_PONG_RESPONSE;
            case "net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket" -> PacketKind.PLAY_PLACE_GHOST_RECIPE;
            case "net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket" -> PacketKind.PLAY_PLAYER_ABILITIES;
            case "net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket" -> PacketKind.PLAY_PLAYER_COMBAT_END;
            case "net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket" -> PacketKind.PLAY_PLAYER_COMBAT_ENTER;
            case "net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket" -> PacketKind.PLAY_PLAYER_COMBAT_KILL;
            case "net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket" -> PacketKind.PLAY_PLAYER_LOOK_AT;
            case "net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket" -> PacketKind.PLAY_PLAYER_POSITION;
            case "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket" -> PacketKind.PLAY_REMOVE_ENTITIES;
            case "net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket" -> PacketKind.PLAY_REMOVE_MOB_EFFECT;
            case "net.minecraft.network.protocol.game.ClientboundResetScorePacket" -> PacketKind.PLAY_RESET_SCORE;
            case "net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket" -> PacketKind.COMMON_RESOURCE_PACK_POP;
            case "net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket" -> PacketKind.COMMON_RESOURCE_PACK_PUSH;
            case "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket" -> PacketKind.PLAY_ROTATE_HEAD;
            case "net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket" -> PacketKind.PLAY_SECTION_BLOCKS_UPDATE;
            case "net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket" -> PacketKind.PLAY_SELECT_ADVANCEMENTS_TAB;
            case "net.minecraft.network.protocol.game.ClientboundServerDataPacket" -> PacketKind.PLAY_SERVER_DATA;
            case "net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket" -> PacketKind.PLAY_SET_ACTION_BAR_TEXT;
            case "net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket" -> PacketKind.PLAY_SET_BORDER_CENTER;
            case "net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket" -> PacketKind.PLAY_SET_BORDER_LERP_SIZE;
            case "net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket" -> PacketKind.PLAY_SET_BORDER_SIZE;
            case "net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket" -> PacketKind.PLAY_SET_BORDER_WARNING_DELAY;
            case "net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket" -> PacketKind.PLAY_SET_BORDER_WARNING_DISTANCE;
            case "net.minecraft.network.protocol.game.ClientboundSetCameraPacket" -> PacketKind.PLAY_SET_CAMERA;
            case "net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket" -> PacketKind.PLAY_SET_CARRIED_ITEM;
            case "net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket" -> PacketKind.PLAY_SET_CHUNK_CACHE_CENTER;
            case "net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket" -> PacketKind.PLAY_SET_CHUNK_CACHE_RADIUS;
            case "net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket" -> PacketKind.PLAY_SET_DEFAULT_SPAWN_POSITION;
            case "net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket" -> PacketKind.PLAY_SET_DISPLAY_OBJECTIVE;
            case "net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket" -> PacketKind.PLAY_SET_ENTITY_LINK;
            case "net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket" -> PacketKind.PLAY_SET_ENTITY_MOTION;
            case "net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket" -> PacketKind.PLAY_SET_EQUIPMENT;
            case "net.minecraft.network.protocol.game.ClientboundSetObjectivePacket" -> PacketKind.PLAY_SET_OBJECTIVE;
            case "net.minecraft.network.protocol.game.ClientboundSetPassengersPacket" -> PacketKind.PLAY_SET_PASSENGERS;
            case "net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket" -> PacketKind.PLAY_SET_PLAYER_TEAM;
            case "net.minecraft.network.protocol.game.ClientboundSetScorePacket" -> PacketKind.PLAY_SET_SCORE;
            case "net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket" -> PacketKind.PLAY_SET_SIMULATION_DISTANCE;
            case "net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket" -> PacketKind.PLAY_SET_SUBTITLE_TEXT;
            case "net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket" -> PacketKind.PLAY_SET_TITLE_TEXT;
            case "net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket" -> PacketKind.PLAY_SET_TITLES_ANIMATION;
            case "net.minecraft.network.protocol.game.ClientboundSoundEntityPacket" -> PacketKind.PLAY_SOUND_ENTITY;
            case "net.minecraft.network.protocol.game.ClientboundSoundPacket" -> PacketKind.PLAY_SOUND;
            case "net.minecraft.network.protocol.game.ClientboundStopSoundPacket" -> PacketKind.PLAY_STOP_SOUND;
            case "net.minecraft.network.protocol.common.ClientboundStoreCookiePacket" -> PacketKind.COMMON_STORE_COOKIE;
            case "net.minecraft.network.protocol.game.ClientboundTabListPacket" -> PacketKind.PLAY_TAB_LIST;
            case "net.minecraft.network.protocol.game.ClientboundTagQueryPacket" -> PacketKind.PLAY_TAG_QUERY;
            case "net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket" -> PacketKind.PLAY_TAKE_ITEM_ENTITY;
            case "net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket" -> PacketKind.PLAY_TELEPORT_ENTITY;
            case "net.minecraft.network.protocol.game.ClientboundTickingStatePacket" -> PacketKind.PLAY_TICKING_STATE;
            case "net.minecraft.network.protocol.game.ClientboundTickingStepPacket" -> PacketKind.PLAY_TICKING_STEP;
            case "net.minecraft.network.protocol.common.ClientboundTransferPacket" -> PacketKind.COMMON_TRANSFER;
            case "net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket" -> PacketKind.PLAY_UPDATE_ATTRIBUTES;
            case "net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket" -> PacketKind.PLAY_UPDATE_MOB_EFFECT;
            case "net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket" -> PacketKind.PLAY_UPDATE_RECIPES;
            case "net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket" -> PacketKind.COMMON_UPDATE_TAGS;
            case "net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket" -> PacketKind.PLAY_PROJECTILE_POWER;
            case "net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket" -> PacketKind.COMMON_CUSTOM_REPORT_DETAILS;
            case "net.minecraft.network.protocol.common.ClientboundServerLinksPacket" -> PacketKind.COMMON_SERVER_LINKS;
            default -> PacketKind.OTHER;
        };
    }
    static CustomKind customKind(net.minecraft.network.protocol.Packet<?> packet) {
        if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom) {
            return customKind(custom.payload());
        }
        if (packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket custom) {
            return customKind(custom.payload());
        }
        return CustomKind.NONE;
    }
    static CustomKind customKind(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        String owner=payload.getClass().getName();
        String path=knownPath(owner);
        // Never invoke an unknown third-party type() implementation or inspect any body fields.
        if(path==null) {return CustomKind.OTHER_CUSTOM;}
        return customHeader(owner,payload.type().id());
    }
    static CustomKind customHeader(String owner,net.minecraft.resources.ResourceLocation id) {
        String path=knownPath(owner);
        if(path==null) {return CustomKind.OTHER_CUSTOM;}
        if(!net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gramarye",path).equals(id)) {
            return CustomKind.KNOWN_HEADER_MISMATCH;
        }
        return switch(path) {
            case "transition_state" -> CustomKind.P11_STATE;
            case "transition_request" -> CustomKind.P11_REQUEST;
            case "player_mana_sync" -> CustomKind.P7_MANA;
            case "skill_cooldown_sync" -> CustomKind.P7_COOLDOWN;
            case "intent_ack" -> CustomKind.P7_ACK;
            case "cast_intent" -> CustomKind.P7_CAST_INTENT;
            case "profile_catalog" -> CustomKind.P8_PROFILE;
            case "presentation_event" -> CustomKind.P8_PRESENTATION;
            default -> CustomKind.KNOWN_HEADER_MISMATCH;
        };
    }
    private static String knownPath(String owner) {
        return switch(owner) {
            case "com.yo1no.gramarye.P11TransitionStatePayload" -> "transition_state";
            case "com.yo1no.gramarye.P11TransitionRequestPayload" -> "transition_request";
            case "com.yo1no.gramarye.magic.network.PlayerManaSyncPayload" -> "player_mana_sync";
            case "com.yo1no.gramarye.magic.network.SkillCooldownSyncPayload" -> "skill_cooldown_sync";
            case "com.yo1no.gramarye.magic.network.IntentAckPayload" -> "intent_ack";
            case "com.yo1no.gramarye.magic.network.CastIntentPayload" -> "cast_intent";
            case "com.yo1no.gramarye.ProfileCatalogPayload" -> "profile_catalog";
            case "com.yo1no.gramarye.PresentationEventPayload" -> "presentation_event";
            default -> null;
        };
    }
    static List<Map<String,Object>> frames(StackTraceElement[] trace) {
        var result=new ArrayList<Map<String,Object>>();
        for(var frame:trace) {
            if(result.size()==12) {break;}
            String owner=ownerCode(frame.getClassName()), method=methodCode(frame.getMethodName());
            if(owner==null || method==null) {continue;}
            result.add(Map.of("owner",owner,"method",method,"line",frame.getLineNumber()));
        }
        return List.copyOf(result);
    }
    private static String ownerCode(String name) {
        return switch(name) {
            case "net.minecraft.network.Connection" -> "CONNECTION";
            case "net.minecraft.network.UnconfiguredPipelineHandler$Outbound" -> "OUTBOUND_UNCONFIGURED";
            case "net.minecraft.network.PacketEncoder" -> "PACKET_ENCODER";
            case "net.neoforged.neoforge.network.filters.GenericPacketSplitter" -> "GENERIC_PACKET_SPLITTER";
            case "net.minecraft.network.codec.IdDispatchCodec" -> "ID_DISPATCH_CODEC";
            case "net.minecraft.network.ProtocolSwapHandler" -> "PROTOCOL_SWAP";
            case "net.minecraft.network.protocol.PacketUtils" -> "PACKET_UTILS";
            case "net.minecraft.network.PacketListener" -> "PACKET_LISTENER";
            case "net.minecraft.server.network.ServerCommonPacketListenerImpl" -> "SERVER_COMMON";
            case "net.minecraft.server.network.ServerGamePacketListenerImpl" -> "SERVER_GAME";
            case "net.minecraft.server.network.ServerConfigurationPacketListenerImpl" -> "SERVER_CONFIG";
            case "net.minecraft.server.network.ServerConnectionListener" -> "SERVER_CONNECTIONS";
            case "net.minecraft.server.players.PlayerList" -> "PLAYER_LIST";
            case "net.minecraft.server.MinecraftServer" -> "SERVER";
            case "net.minecraft.util.thread.BlockableEventLoop" -> "EVENT_LOOP";
            case "net.minecraft.client.Minecraft" -> "CLIENT";
            case "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl" -> "CLIENT_COMMON";
            case "net.minecraft.client.multiplayer.ClientPacketListener" -> "CLIENT_GAME";
            case "net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl" -> "CLIENT_CONFIG";
            case "net.neoforged.neoforge.network.registration.NetworkRegistry" -> "NETWORK_REGISTRY";
            case "com.yo1no.gramarye.P11LiveTransitionService" -> "LIVE_SERVICE";
            case "com.yo1no.gramarye.P11LiveTransitionBoundary" -> "LIVE_BOUNDARY";
            case "com.yo1no.gramarye.P11NativeStorageBoundary" -> "SOURCE_BOUNDARY";
            case "com.yo1no.gramarye.P11KeepAliveBoundary" -> "KEEPALIVE_BOUNDARY";
            case "com.yo1no.gramarye.P11ClientTransitions" -> "CLIENT_CONTROL";
            case "com.yo1no.gramarye.P11C4aNativeSenderProbe" -> "FIXTURE_NATIVE_SENDER";
            case "com.yo1no.gramarye.P11C4aReloadBlockerProbe" -> "FIXTURE_RELOAD";
            default -> null;
        };
    }
    private static String methodCode(String name) {
        return switch(name) {
            case "exceptionCaught", "disconnect", "handleDisconnection", "channelInactive", "channelRead0", "genericsFtw",
                "onPacketError", "ensureRunningOnSameThread", "makeReportedException", "onDisconnect", "removePlayerFromWorld",
                "handleConfigurationStart", "handleConfigurationAcknowledged", "handleConfigurationFinished", "switchToConfig",
                "setupInboundProtocol", "setupOutboundProtocol", "validateListener", "handleLogin", "clearClientLevel",
                "initializeOtherConnection", "checkPacket", "handleModdedPayload", "remove", "unavailable",
                "configurationAcknowledged", "completeConfigurationAcknowledgement", "initializeConfiguration",
                "runPacketBody", "execute", "pump", "prepareAndDispatch", "submitPump", "nativeSend", "finish",
                "handle", "tick", "tickChildren", "tickServer", "runServer", "doRunTask", "pollTask", "send",
                "handleKeepAlive", "keepConnectionAlive", "beginAck", "beginKeepAlive", "closeRegion",
                "advanceReload", "start", "xp", "reloadResources", "managedBlock",
                "write", "encode", "doSendPacket", "handleOutboundTerminalPacket" -> name;
            case "lambda$ensureRunningOnSameThread$0" -> "PACKET_UTILS_SCHEDULED_HANDLER";
            default -> name.startsWith("wrapMethod$") || name.startsWith("wrapOperation$") || name.startsWith("handler$")
                ? "INJECTED_NATIVE_WRAPPER" : null;
        };
    }
    private static final class Run {
        final Connection connection;final Path output;final boolean serverSide;final String prefix;final boolean[] seen=new boolean[Stage.values().length];
        int sequence, finishSubmits, stateSubmitsAfterFinish;volatile int failures;
        long submissionSequence;Submission lastSubmission;
        ProducerWindow producerWindow=ProducerWindow.NONE;
        long producerSequence;Producer firstProducerMismatch,lastProducer;
        Run(Connection connection,Path output,boolean serverSide,String prefix) {this.connection=connection;this.output=output;this.serverSide=serverSide;this.prefix=prefix;}
    }
    private record Submission(long sequence,PacketKind packet,CustomKind custom,String listener,String protocol,String encoderProtocol) {}
    private record Producer(long sequence,ProducerWindow window,PacketKind packet,List<ProducerSite> sites,
            String caller,String current,boolean callerCurrent,String encoder,boolean serverThread,
            boolean game,boolean removed,boolean disconnected) {}
}
