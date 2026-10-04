package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aSubmissionRejectProbe;
import java.util.List;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService",remap=false)
abstract class P11C4aSubmissionLiveMixin {
    @Shadow @Final private Map<Connection,?> entries;
    @Shadow private volatile boolean stopping;
    @Inject(method="execute(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Lcom/yo1no/gramarye/P11TransitionControl$Drain;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void submit$execute(@Coerce Object entry,@Coerce Object drain,CallbackInfo ci) {
        P11C4aSubmissionRejectProbe.executing(entry);
    }
    @WrapMethod(method="ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",require=1,expect=1,allow=1)
    private void submit$ingress(@Coerce Object request,Connection connection,ICommonPacketListener listener,Operation<Void> original) {
        Object token=P11C4aSubmissionRejectProbe.ingressEntered(this,request,connection,listener);Throwable primary=null;
        try {original.call(request,connection,listener);}
        catch(RuntimeException|Error failure){primary=failure;throw failure;}
        finally {
            if(token!=null) {
                try {
                    List<?> snapshot; synchronized(entries){snapshot=List.copyOf(entries.values());}
                    P11C4aSubmissionRejectProbe.ingressEnded(token,stopping,snapshot,primary);
                } catch(RuntimeException|Error secondary) {
                    P11C4aSubmissionRejectProbe.observerFailed(token);
                }
            }
        }
    }
    @WrapOperation(method="requestPump()V",at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11ControlBudgets$MainWakeup;request()Z"),require=1,expect=1,allow=1)
    private boolean submit$request(@Coerce Object wakeup,Operation<Boolean> original) {
        boolean result=original.call(wakeup);P11C4aSubmissionRejectProbe.pumpRequested(result);return result;
    }
    @WrapOperation(method="submitPump()V",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;tell(Ljava/lang/Runnable;)V"),require=1,expect=1,allow=1)
    private void submit$tell(MinecraftServer server,Runnable task,Operation<Void> original) {
        P11C4aSubmissionRejectProbe.beforeTell(this,server,task);
        original.call(server,task);
    }
    @WrapOperation(method="submitPump()V",at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11ControlBudgets$MainWakeup;retire()V"),require=1,expect=1,allow=1)
    private void submit$wakeup(@Coerce Object wakeup,Operation<Void> original) {
        original.call(wakeup);P11C4aSubmissionRejectProbe.retired(true,true);
    }
    @WrapOperation(method="submitPump()V",at=@At(value="INVOKE",target="Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher;retireSlot()Z"),require=1,expect=1,allow=1)
    private boolean submit$dispatcher(@Coerce Object dispatcher,Operation<Boolean> original) {
        boolean result=original.call(dispatcher);P11C4aSubmissionRejectProbe.retired(false,result);return result;
    }
}
