#!/usr/bin/env bash
set -euo pipefail

# Direct shared inventory for the existing P4/P7 consumers. This is not a phase Gate.
# §68 exact direct consumers; no directory or prefix admission.
is_p10_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/P10TemplateBody.java | \
        src/main/java/com/yo1no/gramarye/P10TemplateCodec.java | \
        src/main/java/com/yo1no/gramarye/P10TemplateService.java | \
        src/main/java/com/yo1no/gramarye/P10TemplateValidation.java | \
        src/main/java/com/yo1no/gramarye/P10TemplateValidateCommand.java | \
        src/main/java/com/yo1no/gramarye/P9DamageActionType.java | \
        src/p8S2GameTest/java/com/yo1no/gramarye/P8S2ReloadFailureHarness.java | \
        src/main/java/com/yo1no/gramarye/gametest/PlatformGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/migration/SkillCandidateResolver.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/migration/PayloadMigrator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/codec/ActionDefinitionCodec.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/codec/TriggerDefinitionCodec.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/inspection/NodeProjectionResolver.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/validation/SkillValidationAnalyzer.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/migration/SkillCandidateResolverOrderedTest.java | \
        scripts/run-dedicated-server-smoke.sh | \
        src/main/resources/data/gramarye/gramarye/skill_templates/starter_bolt_v0.json | \
        src/test/java/com/yo1no/gramarye/P10TemplateCodecTest.java | \
        src/test/java/com/yo1no/gramarye/P10TemplateServiceTest.java | \
        src/test/java/com/yo1no/gramarye/P10TemplateValidationTest.java | \
        src/test/java/com/yo1no/gramarye/P10TemplateValidateCommandTest.java | \
        src/test/java/com/yo1no/gramarye/P10StarterCommandTest.java | \
        src/test/java/com/yo1no/gramarye/P8ServerPresentationServiceTest.java | \
        src/test/java/com/yo1no/gramarye/P9StarterSkillContentTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# Exact P11 native lifecycle/reward, accepted-work L1 and direct companions, never prefix admission.
is_p11_native_slice_path() {
    case "$1" in
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aAckRejectionClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aAckRejectionProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aC6ClientFlow.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aC6ClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aC6Coordinator.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aC6EarlyGateProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aC6NativeProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigDepartureClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigDepartureProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigResetClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigResetProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aHostExpiryClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aHostExpiryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aHostLeaveClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aHostLeaveProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aLoadedConfiguration.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aMetadataHClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aMetadataHProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aNativeSenderClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aNativeSenderProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aParkingClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aParkingProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aParkingTransferProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aOldTickProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aPeerSafetyProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aPortalClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aPortalProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aPreplayChatClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aPreplayChatProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigPrimaryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aC6ExpiryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aNativeErrorClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigCatchProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigCatchClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigCatchNativeMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigCatchLiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigCatchStorageMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigCatchGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConstructorWithdrawalMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aNativeErrorProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aCompleteBFaultProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aCompleteBFaultClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aCompleteBLoadMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aCompleteBAttachmentCopyMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aCompleteBRemoveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aCompleteBConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aRewardContinuityProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aRewardContinuityClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRewardContinuitySendMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRewardContinuityClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aSubmissionRejectProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aSubmissionClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aSubmissionLiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aSubmissionConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aSubmissionGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aSubmissionClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aHeldProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aHeldClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aErrorStatusProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aErrorStatusClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aTerminalStatusProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigParkingResetProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aConfigParkingResetClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigParkingPotionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigParkingCloudMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigParkingFinishMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aLoginDisconnectDiagnosticMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aTerminalStatusClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aTerminalHandoffMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aTerminalClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHeldBoundaryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHeldControlMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHeldReserveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHeldManagedMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHeldEncoderMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHeldWireMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorEntryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorLiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorPlayMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorSendMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorServerMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeErrorTaskMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aMalformedClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aMalformedServerProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aMalformedWireProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedClientStateMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedCodecMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedDecoderMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedEncoderMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMalformedIngressMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigPrimaryConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigPrimaryCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigPrimaryPacketUtilsMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigPrimaryLiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aRateFairClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aRateFairProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aRequiredClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aRequiredProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aScenario.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aServerTerminalProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aSynchronousWriterProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aUiContextProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aUiHeldInputProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aUiInputProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aAckRejectionClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aAckRejectionCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aAckRejectionLockMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6BucketMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6ClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6ControlMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6DispatcherMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6EntryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6EntryViewMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6GateMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6LiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aC6WaitMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigDepartureClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigDepartureEntryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigDepartureGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigDepartureLockMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigDeparturePlayerMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigDepartureServiceMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigResetClientCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigResetClientConfigurationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigResetCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigResetConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigResetLockMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryControlMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryEntryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryFoundationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryIntegratedMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryNetworkMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryServiceMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostExpiryStateMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostLeaveCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostLeaveGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostLeaveIntegratedMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostLeaveNetworkMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostLeaveScreenMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aHostLeaveStateMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMetadataHBoundaryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMetadataHClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMetadataHManagedMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMetadataHObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeSenderFlushMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeSenderScheduleMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeSenderMinecraftMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeSenderPacketMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aNativeSenderPlayerMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatDebugMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatParkingMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatPingMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatPlayerMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatRefreshMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPreplayChatValidationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingBoundaryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingClientKeepAliveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingClientStateMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingContinuationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingEntryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingKeepAliveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingLiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingPlayerListMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingReloadMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingTransferBoundaryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingTransferClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingTransferConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingTransferFieldsMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aParkingTransferLockMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aOldTickCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aOldTickSwapMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aOldTickBoundaryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aOldTickGateMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aOldTickManagedMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aOldTickConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRateFairBucketMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRateFairClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRateFairCommonMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRateFairControlMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRateFairDispatcherMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRateFairLiveMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPortalPlayerMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aPortalCollisionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRequiredRegistrarMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRequiredClientConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRequiredClientConfigurationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRequiredClientDisconnectMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRequiredNegotiationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aRequiredGameMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aServerTerminalMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aSynchronousWriterMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aUiHeldKeyboardMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aUiInputControllerMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aUiInputDeathMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aUiInputP9Mixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aUiWindowFocusMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/magic/network/P11C4aUiInputP9Observation.java | \
        src/p11OnlineHarness/resources/gramarye-p11-c6-observers.mixins.json | \
        scripts/fixtures/p11-c4a-c6-startup.toml | \
        scripts/fixtures/p11-c4a-handoff-startup.toml | \
        scripts/fixtures/p11-c4a-c6-rate-startup.toml | \
        src/main/java/com/yo1no/gramarye/GramaryeClient.java | \
        src/main/java/com/yo1no/gramarye/P11ClientLeaveScreen.java | \
        src/main/java/com/yo1no/gramarye/P11ClientTransitionDispatch.java | \
        src/main/java/com/yo1no/gramarye/P11ClientTransitionScreen.java | \
        src/main/java/com/yo1no/gramarye/P11ClientTransitionState.java | \
        src/main/java/com/yo1no/gramarye/P11ClientTransitions.java | \
        src/main/java/com/yo1no/gramarye/P11ConfigurationBoundary.java | \
        src/main/java/com/yo1no/gramarye/P11ConfigurationTask.java | \
        src/main/java/com/yo1no/gramarye/P11ControlBudgets.java | \
        src/main/java/com/yo1no/gramarye/P11IdentityOwner.java | \
        src/main/java/com/yo1no/gramarye/P11KeepAliveBoundary.java | \
        src/main/java/com/yo1no/gramarye/P11LivePlayAccess.java | \
        src/main/java/com/yo1no/gramarye/P11LiveTransitionBoundary.java | \
        src/main/java/com/yo1no/gramarye/P11LiveTransitionService.java | \
        src/main/java/com/yo1no/gramarye/P11P9TrackingCleanup.java | \
        src/main/java/com/yo1no/gramarye/P5LoadedReferenceResolver.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11P9EntitySectionMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11P9EntityMoveMixin.java | \
        src/main/java/com/yo1no/gramarye/P11ParkingPacketListener.java | \
        src/main/java/com/yo1no/gramarye/P11TransitionControl.java | \
        src/main/java/com/yo1no/gramarye/P11TransitionPayloadRegistrar.java | \
        src/main/java/com/yo1no/gramarye/P11TransitionPayloads.java | \
        src/main/java/com/yo1no/gramarye/P11TransitionProtocol.java | \
        src/main/java/com/yo1no/gramarye/P11TransitionWire.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ClientKeyboardMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ClientConfigurationMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ClientMinecraftMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ClientPacketListenerMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ClientPlayerMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ClientSceneScreenMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ConfigurationAdmissionMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11KeepAliveCommonMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11KeepAliveConnectionMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11LiveCommonSendMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11LivePlayMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ServerBossEventMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ParkingPlacementMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aBossProducerProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ServerHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ClientHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/magic/network/P11L1InputObservation.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1RuntimeMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1InstanceMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1DispatchMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ProjectileMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1DamageMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1RewardMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ClientInputMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ClientDeliveryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ClientMirrorMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1FoundationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ScoreMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1WorkBoundaryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1HostStopProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1RestartProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1RestartRuntimeMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1RestartCompositionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1HostStopClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1HostStopBoundaryMixin.java | \
        src/p11OnlineHarness/resources/data/gramarye_p11_engineering/advancement/l1_first_kill.json | \
        src/p11OnlineHarness/resources/data/gramarye_p11_engineering/loot_table/l1_loot.json | \
        src/p11OnlineHarness/resources/data/gramarye_p11_engineering/function/l1_reward.mcfunction | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1WorkRewardProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1WorkRewardMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1WorkFunctionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1WorkCommandMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1WorkFailureCountMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1StatsMemoryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1StatsFailureCountMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ContextRefusalProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ContextRefusalClientProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ContextBoundaryMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ContextManagedBlockMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ContextTaskMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ContextClientMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1NaturalUnloadProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1UnloadLogoutMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1UnloadRuntimeMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1OnlinePeerProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1OnlinePeerPlayerMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1OnlinePeerP8Mixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ImpactCustodyProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ImpactCustodyMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1ImpactClaimMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1CapacityWorkProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1CapacityRuntimeMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1CapacityStarterMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1CapacityResourcesMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1CapacityGameMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1TrackingBoundaryProbe.java | src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1TrackingLevelMixin.java | src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1TerminalBoundaryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1TerminalAddMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1TerminalRuntimeMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1TerminalLogoutMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1TerminalConnectionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1TerminalServerMixin.java | \
        src/p11OnlineHarness/fixtures/l1/advancement/l1_partial_kill.json | \
        src/p11OnlineHarness/fixtures/l1/advancement/l1_qctx_kill.json | \
        src/p11OnlineHarness/fixtures/l1/advancement/l1_qctx_peer.json | \
        src/p11OnlineHarness/fixtures/l1/function/l1_partial_reward.mcfunction | \
        src/p11OnlineHarness/fixtures/l1/function/l1_qctx_outer.mcfunction | \
        src/p11OnlineHarness/fixtures/l1/function/l1_qctx_peer_reward.mcfunction | \
        src/p11OnlineHarness/fixtures/l1/stats-memory-startup.toml | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1LifecycleBoundaryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1LifecycleClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1LifecycleReleaseMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1LifecycleClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1WorkReleaseMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1SupplementalHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1RevisionProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1PacketProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11L1ClientResourceProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/magic/network/P11L1AckObservation.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1RevisionCompositionMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1P8SendMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1P8PolicyMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11L1AckPolicyMixin.java | \
        src/p11OnlineHarness/resources/gramarye-p11-l1-harness.mixins.json | \
        src/p11OnlineHarness/fixtures/l1/advancement/l1_first_kill.json | \
        src/p11OnlineHarness/fixtures/l1/advancement/l1_late_kill.json | \
        src/p11OnlineHarness/fixtures/l1/loot_table/l1_loot.json | \
        src/p11OnlineHarness/fixtures/l1/function/l1_reward.mcfunction | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aBossProducerClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aBossProducerSwitchMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aBossProducerSendMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aBossProducerClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aClientHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aClientInputProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aEvidence.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aFirstTryProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aFirstTryClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aReloadBlockerProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aReloadClientProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aScreenEvidence.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aNativeObservations.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11C4aServerHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aBoundaryObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aConfigurationFailureMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aClientConfigurationObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aFirstTryBoundaryMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aFirstTryControlMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aFirstTryClientMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aReloadBoundaryObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aReloadManagedBlockMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aReloadClientObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aClientPacketObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aClientStateObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aMouseInputMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11C4aWireObservationMixin.java | \
        src/p11OnlineHarness/resources/gramarye-p11-c4a-harness.mixins.json | \
        src/test/java/com/yo1no/gramarye/P11ClientTransitionStateTest.java | \
        src/test/java/com/yo1no/gramarye/P11ConfigurationNativeBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P11ControlBudgetsTest.java | \
        src/test/java/com/yo1no/gramarye/P11FoundationBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P11IdentityOwnerTest.java | \
        src/test/java/com/yo1no/gramarye/P11LiveNativeBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P11LiveNativeErrorPolicyTest.java | \
        src/test/java/com/yo1no/gramarye/P11TransitionControlTest.java | \
        src/test/java/com/yo1no/gramarye/P11TransitionWireTest.java | \
        scripts/p11-online-runtime.rb | \
        scripts/test-p11-online-runtime.rb | \
        scripts/test-p11-online-launch-diagnostic.rb | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineClientHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineInputs.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineLaunchDiagnostic.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineLoginAccess.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineNativeContextProbe.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/P11OnlineServerHarness.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineAuthenticatorMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineClientObservationMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineLoginMixin.java | \
        src/p11OnlineHarness/java/com/yo1no/gramarye/harnessmixin/P11OnlineRewardObservationMixin.java | \
        src/p11OnlineHarness/resources/gramarye-p11-online-harness.mixins.json | \
        src/p11OnlineHarness/resources/gramarye-p11-online-private-console.xml | \
        src/main/java/com/yo1no/gramarye/P11FoundationService.java | \
        src/main/java/com/yo1no/gramarye/P11NativeStorageBoundary.java | \
        src/main/java/com/yo1no/gramarye/P11NativeWorldAccess.java | \
        src/main/java/com/yo1no/gramarye/P11QualifiedSourceOwner.java | \
        src/main/java/com/yo1no/gramarye/P11CanonicalAdvancements.java | \
        src/main/java/com/yo1no/gramarye/P11NativeCleanup.java | \
        src/main/java/com/yo1no/gramarye/P11NativeContextFacts.java | \
        src/main/java/com/yo1no/gramarye/P11NativeCreditLifetime.java | \
        src/main/java/com/yo1no/gramarye/P11NativeOperationBoundary.java | \
        src/main/java/com/yo1no/gramarye/P11NativePresence.java | \
        src/main/java/com/yo1no/gramarye/P11ProvisionalAssociation.java | \
        src/main/java/com/yo1no/gramarye/P11RecipeDelivery.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11AdvancementsMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11PlayerListMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ServerPlayerMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11MinecraftServerMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11AdvancementRewardsMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11BuildContextsMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11CallFunctionMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11CommandsMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11EnderDragonCreditMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11EntityCreditRemovalMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11EntityLookupMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11EntityManagerCleanupMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11EntityPresenceMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11EntityRemovalMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ExecuteCommandMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ExecutionContextMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11GameModeCommandMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11LevelEntityCleanupMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11LivingEntityCreditMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11PlayerSlotMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11RecipeBookMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11RideCommandMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11SculkCatalystCreditMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11ServerPlayerScoreMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11SimpleCriterionTriggerMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11SpectateCommandMixin.java | \
        src/main/java/com/yo1no/gramarye/mixin/P11TeleportCommandMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11SourceWriterClientHarness.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11AssociationFaultProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeCanonicalProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeCleanupProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeCloneProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeEndProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeOwnedCopyProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/magic/definition/player/P11OwnedCopySkillObservation.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/magic/runtime/mana/P11OwnedCopyManaObservation.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeDeliveryProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeMetadataProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativePresenceProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P11NativeRewardProbe.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11CanonicalSendFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11CanonicalCopyFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11CanonicalPacketMeasurementMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11ClientDeliveryObservationMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11CloneLoadObserverMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11ConstructorFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11MetadataOwnerFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11MetadataRecoveryFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11MetadataStageFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11RewardLootFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11RewardFunctionFaultMixin.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/harnessmixin/P11RewardFunctionCatchMixin.java | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/advancement/delivery_recipe.json | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/advancement/delivery_root.json | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/advancement/native_partial.json | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/advancement/native_end_return.json | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/advancement/native_prepared.json | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/advancement/native_reward.json | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/function/delivery_tail.mcfunction | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/function/reward.mcfunction | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/function/prepared_reward.mcfunction | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/function/partial_reward.mcfunction | \
        src/p9S5ClientHarness/resources/data/gramarye_p11_engineering/loot_table/native_reward.json | \
        src/test/java/com/yo1no/gramarye/P11CanonicalAdvancementsTest.java | \
        src/test/java/com/yo1no/gramarye/P11ExactCleanupTest.java | \
        src/test/java/com/yo1no/gramarye/P11DetachedStopBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P11DetachedPlayerStopTest.java | \
        src/test/java/com/yo1no/gramarye/P11P9TrackingCleanupTest.java | \
        src/test/java/com/yo1no/gramarye/P11AcceptedWorkBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P11AcceptedWorkCleanupPendingTest.java | \
        src/test/java/com/yo1no/gramarye/P11AcceptedWorkReloadCompletionTest.java | \
        src/test/java/com/yo1no/gramarye/P11AcceptedWorkObservedHitTest.java | \
        src/test/java/com/yo1no/gramarye/P7AuthenticatedPlayerCastIngressTest.java | \
        src/test/java/com/yo1no/gramarye/SkillRuntimeAuthenticatedCastIngressTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3FixtureTest.java | \
        src/test/java/com/yo1no/gramarye/P11NativeContextFactsTest.java | \
        src/test/java/com/yo1no/gramarye/P11NativeCreditLifetimeTest.java | \
        src/test/java/com/yo1no/gramarye/P11PresenceSlotTest.java | \
        src/test/java/com/yo1no/gramarye/P11ProvisionalAssociationTest.java | \
        src/test/java/com/yo1no/gramarye/P11ReceiptLedgerTest.java | \
        src/test/java/com/yo1no/gramarye/P11SynchronousSourceBoundaryTest.java | \
        src/main/resources/gramarye.p11.mixins.json | \
        src/p9S5ClientHarness/resources/gramarye-p11-native-harness.mixins.json | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7NetworkComposition.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/submission/P11MetadataStagesTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2VisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundaryTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

is_s4_path() {
    case "$1" in
        scripts/verify-p7-s4-source-contracts.sh | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/P7GameTestInventory.java | \
        src/test/java/com/yo1no/gramarye/P7ManaSnapshotBridgeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S4ServerBehaviorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7ClientMirrorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LoginReadyHandoffTest.java | \
        src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P7ManaSnapshotBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7SyncSequence.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerSyncState.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7AuthoritativeSyncService.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ServerLifecycleEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ReloadStartEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7Diagnostics.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientMirror.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientMirrorDispatchFactory.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientLifecycleEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7S4NetworkGameTests.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# Exact P9-S3 pre-commit source projection consumed by the historical
# configuration verifiers. No directory or prefix admission is intentional.
is_p9_s3_path() {
    case "$1" in
        build.gradle | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d2-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        scripts/verify-p7-s4-source-contracts.sh | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P5RuntimeVocabulary.java | \
        src/main/java/com/yo1no/gramarye/P6RuntimeExecutionPortAdapter.java | \
        src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java | \
        src/main/java/com/yo1no/gramarye/P8S3PresentationGameTests.java | \
        src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java | \
        src/main/java/com/yo1no/gramarye/P9StarterProjectile.java | \
        src/main/java/com/yo1no/gramarye/P9StarterProjectileClientEvents.java | \
        src/main/java/com/yo1no/gramarye/P9StarterProjectileRegistration.java | \
        src/main/java/com/yo1no/gramarye/P9WorldEffectHandoff.java | \
        src/main/java/com/yo1no/gramarye/SkillRuntimeService.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionInvocation.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionInvocation.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/SpawnProjectileActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/SpawnProjectileActionInvocation.java | \
        src/main/resources/META-INF/accesstransformer.cfg | \
        src/p9S3ClientHarness/java/com/yo1no/gramarye/P9S3ClientRuntimeHarness.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeKernelTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java | \
        src/test/java/com/yo1no/gramarye/P5RuntimeVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/P6RuntimeExecutionAdapterTest.java | \
        src/test/java/com/yo1no/gramarye/P6RuntimeExecutionCapabilityTest.java | \
        src/test/java/com/yo1no/gramarye/P6S4BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P7GameTestInventory.java | \
        src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P9S1BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionCompensationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionThrowableTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionTraceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutorRegistryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionTransactionTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectResolverTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPortTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectEngineTestDoubles.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngineFailureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngineSuccessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionGuardTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectSemanticBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/EffectTestFixtures.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6EffectVocabularyTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridgeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/P6S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/SpawnProjectileActionExecutorTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S3-DC1 adds one direct regression path without rewriting the immutable
# S2-to-S3 exact-85 projection above.
is_p9_s3_dc1_path() {
    case "$1" in
        src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S4 adds the exact six dirty paths that were not already admitted by the
# immutable S3/DC1 projections above. Keep this as a separate closed extension.
is_p9_s4_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/P8AppliedFactHandoff.java | \
        src/main/java/com/yo1no/gramarye/P8ServerPresentationService.java | \
        src/test/java/com/yo1no/gramarye/P8S4ServerTransportTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionDebitTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionPreDebitTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectRequestTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S4-WC1 adds three tracked warning-attribution surfaces outside the
# original exact-27 candidate. The two authorized B-to-F intersections remain
# admitted by the immutable P9-S4 extension above.
is_p9_s4_wc1_path() {
    case "$1" in
        .github/workflows/build.yml | \
        scripts/collect-p9-s3-rd1-unit-test-diagnostics.sh | \
        scripts/verify-p9-s4-warning-attribution.py)
            return 0 ;;
        *) return 1 ;;
    esac
}

# P9-S5-R2 adds one closed candidate projection for the normal-player
# provisioning/input/composition slice. Earlier phase projections intentionally
# remain unchanged; overlap here records the exact final S5 candidate rather
# than admitting a directory, package, or filename prefix.
is_p9_s5_path() {
    case "$1" in
        AGENTS.md | \
        build.gradle | \
        scripts/verify-p4-a3-b-configuration.sh | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d1-configuration.sh | \
        scripts/verify-p4-d2-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        scripts/verify-p7-s4-source-contracts.sh | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPayloadDispatchFactory.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPayloadDispatchPort.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPayloadHandlers.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPresentationLifecycle.java | \
        src/main/java/com/yo1no/gramarye/P8ClientPresentationState.java | \
        src/main/java/com/yo1no/gramarye/P9StarterCommand.java | \
        src/main/java/com/yo1no/gramarye/P9StarterSkillContent.java | \
        src/main/java/com/yo1no/gramarye/P9StarterSkillIdentityV0.java | \
        src/main/java/com/yo1no/gramarye/P9S5ProvisioningGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7ClientLifecycleEvents.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P9ClientCastInput.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P9ClientKeyMappings.java | \
        src/main/resources/assets/gramarye/lang/en_us.json | \
        src/main/resources/assets/gramarye/lang/zh_tw.json | \
        src/p8S5ClientHarness/java/com/yo1no/gramarye/P8S5ClientRuntimeHarness.java | \
        src/p9S5ClientHarness/java/com/yo1no/gramarye/P9S5ClientRuntimeHarness.java | \
        src/test/java/com/yo1no/gramarye/P6RuntimeExecutionCapabilityTest.java | \
        src/test/java/com/yo1no/gramarye/P7GameTestInventory.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPayloadHandlersTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPlayConnection.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPlayEpochAcceptanceTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPlayEpochTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationExecutionTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationLifecycleTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationStateConcurrencyTest.java | \
        src/test/java/com/yo1no/gramarye/P8ClientPresentationStateTest.java | \
        src/test/java/com/yo1no/gramarye/P8S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P8S4PayloadBoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P9S1BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/P9S3DirectConsumerContractTest.java | \
        src/test/java/com/yo1no/gramarye/P9S5BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7ClientMirrorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrarTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S2BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S2DedicatedRegistrationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P7S3BoundaryTest.java | \
        src/test/java/com/yo1no/gramarye/magic/network/P9ClientCastInputTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java)
            return 0 ;;
        *) return 1 ;;
    esac
}

reject_game_test_worker_surface() {
    printf 'Production-packaged GameTest raw worker/task/process surface: %s\n' \
        "$1" >&2
    return 1
}

reject_game_test_liveness_receiver() {
    printf 'Production-packaged GameTest unverified liveness receiver: %s\n' \
        "$1" >&2
    return 1
}

# The sole production-packaged GameTest liveness use is the P9-S3 age-boundary
# assertion. Bind the exception to its enclosing method, local factory call, and
# the factory's concrete Breeze return type. Any extra, moved, shadowed, or
# otherwise unresolved receiver remains fail-closed.
verify_p9_s3_entity_liveness() {
    local source="$1"
    local logical_source="$2"

    if ! LC_ALL=C awk '
        function occurrences(text, pattern, count) {
            count = 0
            while (match(text, pattern)) {
                count++
                text = substr(text, RSTART + RLENGTH)
            }
            return count
        }
        function append_source_token(token) {
            if (token != "") {
                source_tokens[++source_token_count] = token
                source_token_line[source_token_count] = NR
            }
        }
        function append_structural_tokens(text, cursor, character, token) {
            token = ""
            for (cursor = 1; cursor <= length(text); cursor++) {
                character = substr(text, cursor, 1)
                if (character ~ /[[:alnum:]_$]/) {
                    token = token character
                } else {
                    append_source_token(token)
                    token = ""
                    if (character !~ /[[:space:]]/) {
                        append_source_token(character)
                    }
                }
            }
            append_source_token(token)
        }
        function update_method_blocks(text, cursor, character) {
            for (cursor = 1; cursor <= length(text); cursor++) {
                character = substr(text, cursor, 1)
                if (character == "{") {
                    method_depth++
                    method_block_id[method_depth] = ++next_block_id
                } else if (character == "}") {
                    delete method_block_id[method_depth]
                    method_depth--
                }
            }
        }
        BEGIN {
            liveness_pattern = "[.][[:space:]]*isAlive[[:space:]]*[(][[:space:]]*[)]"
            accepted_liveness_pattern = "(^|[^[:alnum:]_$.])target[[:space:]]*[.][[:space:]]*isAlive[[:space:]]*[(][[:space:]]*[)]"
            pending_method = 0
            in_method = 0
            method_depth = 0
            method_count = 0
            closed_method_count = 0
            binding_count = 0
            target_var_count = 0
            all_liveness_count = 0
            method_liveness_count = 0
            accepted_liveness_count = 0
            accepted_same_block_count = 0
            binding_block_id = -1
            binding_line = -1
            scenario_binding_count = 0
            exact_import_count = 0
            scenario_class_count = 0
            helper_count = 0
            constructor_count = 0
            breeze_token_count = 0
            is_alive_token_count = 0
            source_token_count = 0
            comment_ambiguity_count = 0
            quoted_brace_count = 0
            text_block_count = 0
            next_block_id = 0
        }
        {
            line = $0
            if (line ~ /\/\*|\*\/|\/\// \
                    && line != "/** Direct server-world proof for the P9-S3 projectile continuation lifecycle. */") {
                comment_ambiguity_count++
            }
            if (line ~ /[{}]/ && line ~ /["\047]/) {
                quoted_brace_count++
            }
            if (line ~ /"""/) {
                text_block_count++
            }
            token_line = line
            append_structural_tokens(line)
            gsub(/[^[:alnum:]_$]+/, " ", token_line)
            token_count = split(token_line, tokens, /[[:space:]]+/)
            for (token_index = 1; token_index <= token_count; token_index++) {
                if (tokens[token_index] == "Breeze") {
                    breeze_token_count++
                }
                if (tokens[token_index] == "isAlive") {
                    is_alive_token_count++
                }
            }
            if (line ~ /^[[:space:]]*import[[:space:]]+net[.]minecraft[.]world[.]entity[.]monster[.]breeze[.]Breeze[[:space:]]*;[[:space:]]*$/) {
                exact_import_count++
            }
            if (line ~ /^[[:space:]]*private[[:space:]]+static[[:space:]]+final[[:space:]]+class[[:space:]]+ProductionScenario[[:space:]]+implements[[:space:]]+AutoCloseable[[:space:]]*\{[[:space:]]*$/) {
                scenario_class_count++
            }
            if (line ~ /^[[:space:]]*private[[:space:]]+Breeze[[:space:]]+addDeflectingTarget[[:space:]]*\([[:space:]]*Vec3[[:space:]]+position[[:space:]]*\)[[:space:]]*\{[[:space:]]*$/) {
                helper_count++
            }
            if (line ~ /new[[:space:]]+Breeze[[:space:]]*\(/) {
                constructor_count++
            }
            liveness = occurrences(line, liveness_pattern)
            all_liveness_count += liveness

            if (!in_method && !pending_method \
                    && line ~ /private[[:space:]]+static[[:space:]]+void[[:space:]]+exerciseAgeTerminalBeforeSweep[[:space:]]*\(/) {
                pending_method = 1
                method_count++
            }
            if (pending_method && !in_method && line ~ /\{/) {
                pending_method = 0
                in_method = 1
                method_depth = 0
            }

            if (in_method) {
                current_block_id = method_depth > 0 \
                        ? method_block_id[method_depth] : -1
                method_liveness_count += liveness
                accepted_liveness = occurrences(line, accepted_liveness_pattern)
                accepted_liveness_count += accepted_liveness
                if (accepted_liveness > 0 \
                        && line !~ /[{}]/ \
                        && current_block_id == binding_block_id) {
                    accepted_same_block_count += accepted_liveness
                }
                if (line ~ /^[[:space:]]*try[[:space:]]*\([[:space:]]*var[[:space:]]+scenario[[:space:]]*=[[:space:]]*new[[:space:]]+ProductionScenario[[:space:]]*\([[:space:]]*helper[[:space:]]*,[[:space:]]*fixtureId[[:space:]]*\)[[:space:]]*\)[[:space:]]*\{[[:space:]]*$/) {
                    scenario_binding_count++
                }
                if (line ~ /^[[:space:]]*var[[:space:]]+target[[:space:]]*=/) {
                    target_var_count++
                    if (line ~ /^[[:space:]]*var[[:space:]]+target[[:space:]]*=[[:space:]]*scenario[[:space:]]*\.[[:space:]]*addDeflectingTarget[[:space:]]*\(/) {
                        binding_count++
                        binding_block_id = current_block_id
                        binding_line = NR
                    }
                }
                update_method_blocks(line)
                if (method_depth == 0) {
                    in_method = 0
                    closed_method_count++
                }
            }
        }
        END {
            helper_declaration_count = 0
            helper_other_count = 0
            target_binding_shape_count = 0
            for (source_index = 1; source_index <= source_token_count; source_index++) {
                if (source_tokens[source_index] == "addDeflectingTarget") {
                    if (source_tokens[source_index - 1] == "Breeze" \
                            && source_tokens[source_index + 1] == "(" \
                            && source_tokens[source_index + 2] == "Vec3" \
                            && source_tokens[source_index + 3] == "position" \
                            && source_tokens[source_index + 4] == ")" \
                            && source_tokens[source_index + 5] == "{") {
                        helper_declaration_count++
                    } else if (source_tokens[source_index - 1] != ".") {
                        helper_other_count++
                    }
                }
                if (source_tokens[source_index] == "target" \
                        && source_token_line[source_index] == binding_line \
                        && source_tokens[source_index - 1] == "var" \
                        && source_tokens[source_index + 1] == "=" \
                        && source_tokens[source_index + 2] == "scenario" \
                        && source_tokens[source_index + 3] == "." \
                        && source_tokens[source_index + 4] == "addDeflectingTarget" \
                        && source_tokens[source_index + 5] == "(" \
                        && source_tokens[source_index + 6] == "projectile" \
                        && source_tokens[source_index + 7] == "." \
                        && source_tokens[source_index + 8] == "position" \
                        && source_tokens[source_index + 9] == "(" \
                        && source_tokens[source_index + 10] == ")" \
                        && source_tokens[source_index + 11] == "." \
                        && source_tokens[source_index + 12] == "add" \
                        && source_tokens[source_index + 13] == "(" \
                        && source_tokens[source_index + 14] == "projectile" \
                        && source_tokens[source_index + 15] == "." \
                        && source_tokens[source_index + 16] == "getDeltaMovement" \
                        && source_tokens[source_index + 17] == "(" \
                        && source_tokens[source_index + 18] == ")" \
                        && source_tokens[source_index + 19] == "." \
                        && source_tokens[source_index + 20] == "scale" \
                        && source_tokens[source_index + 21] == "(" \
                        && source_tokens[source_index + 22] == "0" \
                        && source_tokens[source_index + 23] == "." \
                        && source_tokens[source_index + 24] == "5" \
                        && source_tokens[source_index + 25] == ")" \
                        && source_tokens[source_index + 26] == ")" \
                        && source_tokens[source_index + 27] == ")" \
                        && source_tokens[source_index + 28] == ";") {
                    target_binding_shape_count++
                }
            }
            valid = method_count == 1 \
                    && closed_method_count == 1 \
                    && !pending_method \
                    && !in_method \
                    && exact_import_count == 1 \
                    && scenario_class_count == 1 \
                    && helper_count == 1 \
                    && constructor_count == 1 \
                    && breeze_token_count == 3 \
                    && comment_ambiguity_count == 0 \
                    && quoted_brace_count == 0 \
                    && text_block_count == 0 \
                    && helper_declaration_count == 1 \
                    && helper_other_count == 0 \
                    && scenario_binding_count == 1 \
                    && binding_count == 1 \
                    && target_var_count == 1 \
                    && target_binding_shape_count == 1 \
                    && binding_block_id >= 0 \
                    && is_alive_token_count == 2 \
                    && all_liveness_count == 2 \
                    && method_liveness_count == 2 \
                    && accepted_liveness_count == 2 \
                    && accepted_same_block_count == 2
            exit(valid ? 0 : 1)
        }
    ' "${source}"; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
}

# Bind the narrow source proof above to javac's resolved owner. The required
# qualification routes produce this class before invoking the source consumer.
verify_p9_s3_compiled_liveness() {
    local source="$1"
    local logical_source="$2"
    local classes_root="$3"
    local class_file bytecode

    class_file="${classes_root}/com/yo1no/gramarye/P9S3ProjectileGameTests.class"
    if [[ ! -d "${classes_root}" || -L "${classes_root}" \
            || ! -f "${class_file}" || -L "${class_file}" \
            || "${source}" -nt "${class_file}" ]] \
            || ! command -v javap >/dev/null 2>&1; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
    if ! bytecode="$(javap \
            -classpath "${classes_root}" \
            -c \
            -p \
            com.yo1no.gramarye.P9S3ProjectileGameTests 2>/dev/null)"; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
    if ! printf '%s\n' "${bytecode}" | LC_ALL=C awk '
        BEGIN {
            invoke_prefix = "^[[:space:]]*[0-9]+:[[:space:]]+invoke(virtual|interface|special|static)[[:space:]]+#[0-9]+[[:space:]]+// (InterfaceMethod|Method) "
        }
        /^  private static void exerciseAgeTerminalBeforeSweep[(]/ {
            method_count++
            in_method = 1
            next
        }
        in_method && /^  (public|protected|private) / {
            in_method = 0
        }
        in_method {
            if ($0 ~ invoke_prefix \
                    "com/yo1no/gramarye/P9S3ProjectileGameTests[$]ProductionScenario[.]addDeflectingTarget:[(][^)]*[)]Lnet/minecraft/world/entity/monster/breeze/Breeze;") {
                helper_return_count++
            }
            if ($0 ~ invoke_prefix ".*[.]isAlive:") {
                all_liveness_owner_count++
            }
            if ($0 ~ invoke_prefix \
                    "net/minecraft/world/entity/monster/breeze/Breeze[.]isAlive:[(][)]Z") {
                breeze_liveness_owner_count++
            }
        }
        END {
            valid = method_count == 1 \
                    && helper_return_count == 1 \
                    && all_liveness_owner_count == 2 \
                    && breeze_liveness_owner_count == 2
            exit(valid ? 0 : 1)
        }
    '; then
        reject_game_test_liveness_receiver "${logical_source}"
        return 1
    fi
}

verify_game_test_worker_source() {
    local source="$1"
    local logical_source="$2"
    local classes_root="$3"
    local status=0

    LC_ALL=C grep -Eq \
        '(^|[^[:alnum:]_$])Thread([^[:alnum:]_$]|$)|Thread\.(ofPlatform|ofVirtual)|Thread\.currentThread[[:space:]]*\([[:space:]]*\)[[:space:]]*\.[[:space:]]*isAlive[[:space:]]*\(|new[[:space:]]+Thread[[:space:]]*\(|\.unstarted[[:space:]]*\(|\.join[[:space:]]*\([[:space:]]*[0-9]|\.interrupt[[:space:]]*\(|AtomicReference|(^|[^[:alnum:]_])(Executor|Future|ProcessBuilder)([^[:alnum:]_]|$)' \
        "${source}" || status=$?
    case "${status}" in
        0) reject_game_test_worker_surface "${logical_source}"; return 1 ;;
        1) ;;
        *) return "${status}" ;;
    esac

    status=0
    LC_ALL=C grep -Fq '\u' "${source}" || status=$?
    case "${status}" in
        0) reject_game_test_liveness_receiver "${logical_source}"; return 1 ;;
        1) ;;
        *) return "${status}" ;;
    esac

    status=0
    LC_ALL=C grep -Eq '[^	 -~]' "${source}" || status=$?
    case "${status}" in
        0) reject_game_test_liveness_receiver "${logical_source}"; return 1 ;;
        1) ;;
        *) return "${status}" ;;
    esac

    status=0
    LC_ALL=C grep -Eq \
        '(^|[^[:alnum:]_$])isAlive([^[:alnum:]_$]|$)' \
        "${source}" || status=$?
    case "${status}" in
        0)
            if [[ "${logical_source}" != \
                    'src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java' ]]; then
                reject_game_test_liveness_receiver "${logical_source}"
                return 1
            fi
            verify_p9_s3_entity_liveness "${source}" "${logical_source}" \
                && verify_p9_s3_compiled_liveness \
                    "${source}" "${logical_source}" "${classes_root}"
            ;;
        1) return 0 ;;
        *) return "${status}" ;;
    esac
}

verify_game_tests() {
    local expected_non_p8 actual actual_non_p8 p8_actual annotation_count source
    local expected_holder_count inspected_holder_count marker_status
    expected_non_p8="$(printf '%s\n' \
        'P7S4LoginManaGameTests.java:manaObservationPreservesAvailableAndMalformedAttachmentTruth' \
        'P7S4LoginManaGameTests.java:loginPortRejectsNoncurrentPlayerBeforeSessionOpen' \
        'P7S4LoginManaGameTests.java:e2NormalAndChangedTerminalsHandoffOnceAndQuarantineNeverHandoffs' \
        'P7S4LoginManaGameTests.java:e2LoginPortRuntimeFailurePropagatesTheSameObject' \
        'P7S4LoginManaGameTests.java:e2LoginPortErrorPropagatesTheSameObject' \
        'P7S4LoginManaGameTests.java:actualP9ReservedContinuationSurvivesRootAndClosesLateWithoutWorldEffects' \
        'P7S4LoginManaGameTests.java:actualP9ActorWitnessRejectsRespawnDimensionAndLogoutBeforeTransfer' \
        'P9S3ProjectileGameTests.java:cancelledSweepContinuesAndInvalidImpactsTerminal' \
        'P9S3ProjectileGameTests.java:falseAndThrowingInsertionNeverOpenOrPublish' \
        'P9S3ProjectileGameTests.java:realSpawnTransferHitAndNextDrainUseTheHeldChild' \
        'P9S3ProjectileGameTests.java:replacementRemovalAndDeadlineCloseWithoutDamage' \
        'P9S3ProjectileGameTests.java:reservedClaimAndWrongTransferWitnessesAreOneShot' \
        'P9S3ProjectileGameTests.java:sixteenthOpenPermitIsThePerPlayerMaximum' \
        'P9S5ProvisioningGameTests.java:registeredStarterCommandCoversProvisioningBranches' \
        'gametest/PlatformGameTests.java:customDescriptorRegistriesLoadEmpty' \
        'gametest/PlatformGameTests.java:dedicatedServerLoads' \
        'gametest/PlatformGameTests.java:descriptorMigrationCoverageAuditPassesAfterRegistryFreeze' \
        'gametest/PlatformGameTests.java:productionDefinitionLookupsResolveMissingTypesSafely' \
        'magic/definition/player/PlayerSkillAttachmentGameTests.java:registeredAttachmentPersistsThroughActualPlayerdataSaveAndReload' \
        'magic/definition/player/PlayerSkillAttachmentGameTests.java:registeredQuarantineAndCopyLifecycleRemainTotal' \
        'magic/definition/store/SkillSavedDataLifecycleGameTests.java:startupInstalledExactReadyAdapterInOverworldCache' \
        'magic/definition/store/SkillSubmissionRecoveryGameTests.java:persistedBaseReplaysPendingChainOnLogin' \
        'magic/definition/store/SkillSubmissionRecoveryGameTests.java:persistedFinalClearsPendingChainWithoutReplayOnLogin' \
        'magic/definition/store/SkillSubmissionRecoveryGameTests.java:persistedIntermediateClearsPrefixBeforeReplayOnLogin' \
        'magic/definition/submission/SkillDefinitionSubmissionGameTests.java:fullSubmissionCommitsStoreJournalThenAttachmentExactlyOnce' \
        'magic/definition/submission/SkillDefinitionSubmissionGameTests.java:postCommitAttachmentDriftReturnsPendingRecovery' \
        'magic/network/P7S4NetworkGameTests.java:actualPostE2LoginOpensOneSessionAndSubmitsOneInitialFullSet' \
        'magic/network/P7S4NetworkGameTests.java:actualRespawnDimensionAndReconnectPreserveThenReplaceSessionIdentity' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:deathCloneCopiesExactManaState' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:dimensionTravelKeepsSingleManaTruth' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:duplicatePersistentManaTruthIsAbsent' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:malformedAttachmentRemainsUnavailableWithoutMutation' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:newPlayerAbsentStateIsAvailableZero' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:nonDeathCloneCopiesExactManaState' \
        'magic/runtime/mana/ManaLifecycleGameTests.java:validAttachmentSerializesAndLoadsExactly' \
        | LC_ALL=C sort)"
    [[ "$(printf '%s\n' "${expected_non_p8}" | wc -l | tr -d ' ')" -eq 35 ]] || {
        printf '%s\n' 'Non-P8 GameTest inventory must remain exact 35' >&2
        return 1
    }
    actual="$(find src/main/java/com/yo1no/gramarye -type f -name '*.java' \
        -exec awk '
            FNR == 1 { pending = 0 }
            /@GameTest[[:space:]]*\(/ { pending = 1 }
            pending && /public[[:space:]]+static[[:space:]]+void[[:space:]]+/ {
                method = $0
                sub(/^.*public[[:space:]]+static[[:space:]]+void[[:space:]]+/, "", method)
                sub(/[[:space:]]*\(.*$/, "", method)
                file = FILENAME
                sub(/^src\/main\/java\/com\/yo1no\/gramarye\//, "", file)
                print file ":" method
                pending = 0
            }
        ' {} + | LC_ALL=C sort)"
    actual_non_p8="$(printf '%s\n' "${actual}" \
        | awk -F: '$1 != "P8S3PresentationGameTests.java"')"
    p8_actual="$(printf '%s\n' "${actual}" \
        | awk -F: '$1 == "P8S3PresentationGameTests.java"')"
    [[ "${actual_non_p8}" == "${expected_non_p8}" ]] || {
        printf '%s\n' 'Non-P8 GameTest source path/method inventory mismatch' >&2
        return 1
    }
    [[ -n "${p8_actual}" ]] || {
        printf '%s\n' 'Exact P8-S3 GameTest holder is empty or missing' >&2
        return 1
    }
    annotation_count="$(find src/main/java/com/yo1no/gramarye -type f -name '*.java' \
        -exec awk '/@GameTest[[:space:]]*\(/ { count++ } END { print count + 0 }' {} + \
        | awk '{ sum += $1 } END { print sum + 0 }')"
    [[ "${annotation_count}" -eq "$(printf '%s\n' "${actual}" | wc -l | tr -d ' ')" ]] \
        || { printf '%s\n' 'Unsupported or duplicate GameTest declaration' >&2; return 1; }
    expected_holder_count="$(printf '%s\n' "${actual}" \
        | awk -F: 'NF >= 2 { print $1 }' | LC_ALL=C sort -u | wc -l | tr -d ' ')"
    [[ "${expected_holder_count}" -eq 11 ]] || {
        printf 'Production-packaged GameTest holder inventory must remain exact 11 (found %s)\n' \
            "${expected_holder_count}" >&2
        return 1
    }
    inspected_holder_count=0
    while IFS= read -r -d '' source; do
        marker_status=0
        LC_ALL=C grep -Eq '@GameTest[[:space:]]*\(' "${source}" || marker_status=$?
        case "${marker_status}" in
        0)
            inspected_holder_count=$((inspected_holder_count + 1))
            verify_game_test_worker_source \
                "${source}" "${source}" build/classes/java/main || return 1
            ;;
        1) ;;
        *) return "${marker_status}" ;;
        esac
    done < <(find src/main/java/com/yo1no/gramarye -type f -name '*.java' -print0)
    [[ "${inspected_holder_count}" -eq "${expected_holder_count}" \
            && "${inspected_holder_count}" -gt 0 ]] || {
        printf 'Production-packaged GameTest worker scan coverage mismatch: expected %s, inspected %s\n' \
            "${expected_holder_count}" "${inspected_holder_count}" >&2
        return 1
    }
    printf '%s\n' "${annotation_count}"
}

case "${1:-}" in
    --is-s4-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P7S4LoginManaGameTests.java | \
            src/main/java/com/yo1no/gramarye/P4E2RecoveryGameTestObservation.java | \
            src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
            src/main/java/com/yo1no/gramarye/magic/network/P7S4NetworkGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-p8-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P8S3PresentationGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-p9-s3-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P9S3ProjectileGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-p9-s5-harness)
        [[ "$#" -eq 2 ]]
        repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
        source_path="${2#"${repository_root}/"}"
        case "${source_path}" in
            src/main/java/com/yo1no/gramarye/P9S5ProvisioningGameTests.java) exit 0 ;;
            *) exit 1 ;;
        esac ;;
    --is-s4-path)
        [[ "$#" -eq 2 ]] \
            && { is_s4_path "$2" || is_p9_s3_path "$2" \
                || is_p9_s3_dc1_path "$2" || is_p9_s4_path "$2" \
                || is_p9_s4_wc1_path "$2" || is_p9_s5_path "$2" || is_p10_path "$2" \
                || is_p11_native_slice_path "$2"; } ;;
    --is-p9-s5-path)
        [[ "$#" -eq 2 ]] && is_p9_s5_path "$2" ;;
    --check-game-test-worker-source)
        [[ "$#" -eq 4 && -f "$2" && ! -L "$2" ]] \
            && verify_game_test_worker_source "$2" "$3" "$4" ;;
    --game-test-count) [[ "$#" -eq 1 ]] && verify_game_tests ;;
    *) printf '%s\n' 'Expected --is-s4-path PATH, --is-p9-s5-path PATH, --is-s4-harness PATH, --is-p8-harness PATH, --is-p9-s3-harness PATH, --is-p9-s5-harness PATH, --check-game-test-worker-source FILE LOGICAL_PATH CLASSES_ROOT, or --game-test-count' >&2; exit 2 ;;
esac
