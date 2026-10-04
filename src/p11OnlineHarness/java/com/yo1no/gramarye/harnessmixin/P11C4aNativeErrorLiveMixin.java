package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aNativeErrorProbe;
import net.minecraft.network.PacketListener;
import net.minecraft.ReportedException;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService",remap=false)
abstract class P11C4aNativeErrorLiveMixin {
    @WrapMethod(method="execute(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Lcom/yo1no/gramarye/P11TransitionControl$Drain;)V",require=1,expect=1,allow=1)
    private void error$execute(@Coerce Object entry,@Coerce Object drain,Operation<Void> original) {
        Object token=P11C4aNativeErrorProbe.executing(entry,drain); Throwable primary=null;
        try { original.call(entry,drain); }
        catch(RuntimeException|Error failure) { primary=failure; throw failure; }
        finally { P11C4aNativeErrorProbe.executed(token,this,primary); }
    }
    @WrapMethod(method="finish(Lcom/yo1no/gramarye/P11LiveTransitionService$Ticket;ZLjava/lang/Throwable;)V",require=1,expect=1,allow=1)
    private void error$finish(@Coerce Object ticket,boolean normal,Throwable primary,Operation<Void> original) {
        Object token=P11C4aNativeErrorProbe.finishing(this,ticket,normal,primary); Throwable escaping=null;
        try { original.call(ticket,normal,primary); }
        catch(RuntimeException|Error failure) { escaping=failure; throw failure; }
        finally { P11C4aNativeErrorProbe.finished(token,this,escaping); }
    }
    @WrapOperation(method="finish(Lcom/yo1no/gramarye/P11LiveTransitionService$Ticket;ZLjava/lang/Throwable;)V",
            at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11QualifiedSourceOwner;closeControlCustody(Lcom/yo1no/gramarye/P11QualifiedSourceOwner$ControlCustody;)V"),require=3,expect=3,allow=3)
    private void error$custody(@Coerce Object source,@Coerce Object custody,Operation<Void> original) {
        original.call(source,custody);
        P11C4aNativeErrorProbe.custodyClosed(source,custody);
    }
    @WrapMethod(method="runPacketBody(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Ljava/lang/Runnable;)Z",require=1,expect=1,allow=1)
    private static boolean error$body(Packet<?> packet,PacketListener listener,Runnable body,Operation<Boolean> original) {
        boolean normal=false,result=false; Throwable primary=null;
        try { result=original.call(packet,listener,body); normal=true; return result; }
        catch(RuntimeException|Error failure) { primary=failure; throw failure; }
        finally { P11C4aNativeErrorProbe.packetBodyEnded(normal,result,primary); }
    }
    @WrapOperation(method="runPacketBody(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Ljava/lang/Runnable;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/PacketListener;onPacketError(Lnet/minecraft/network/protocol/Packet;Ljava/lang/Exception;)V"),require=1,expect=1,allow=1)
    private static void error$policy(PacketListener listener,Packet<?> packet,Exception failure,Operation<Void> original) {
        P11C4aNativeErrorProbe.policyEntered(listener,packet,failure);
        try { original.call(listener,packet,failure); P11C4aNativeErrorProbe.policyReturned(); }
        catch(RuntimeException|Error escaping) { P11C4aNativeErrorProbe.policyEscaped(escaping); throw escaping; }
    }
    @WrapOperation(method="runPacketBody(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Ljava/lang/Runnable;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;makeReportedException(Ljava/lang/Exception;Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)Lnet/minecraft/ReportedException;"),require=1,expect=1,allow=1)
    private static ReportedException error$special(Exception failure,Packet<?> packet,PacketListener listener,Operation<ReportedException> original) {
        P11C4aNativeErrorProbe.specialEntered(failure,packet,listener);
        ReportedException result=original.call(failure,packet,listener);
        P11C4aNativeErrorProbe.specialReturned(result);
        return result;
    }

}
