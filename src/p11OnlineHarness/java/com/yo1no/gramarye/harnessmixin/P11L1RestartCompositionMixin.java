package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1RestartProbe;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P9StarterCommand")
abstract class P11L1RestartCompositionMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService;Lcom/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionService;Lcom/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService;Lcom/yo1no/gramarye/P10TemplateService;Lcom/yo1no/gramarye/P10TemplateValidation;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1ActualComposition(PlayerSkillAttachmentService attachments, SkillDefinitionSubmissionService submissions,
            SkillDefinitionStoreService store, @Coerce Object templates, @Coerce Object validation, CallbackInfo callback) {
        P11L1RestartProbe.composition(attachments, store);
    }
}
