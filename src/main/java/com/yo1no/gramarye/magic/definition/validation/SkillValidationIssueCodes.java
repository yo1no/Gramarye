package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.validation.ValidationIssueCode;

/** Stable Gramarye-owned issue identities for skill validation analysis. */
public final class SkillValidationIssueCodes {
    public static final ValidationIssueCode SKILL_UNSUPPORTED_SCHEMA = code("skill.unsupported_schema");
    public static final ValidationIssueCode SKILL_EMPTY_NODES = code("skill.empty_nodes");
    public static final ValidationIssueCode SKILL_NODE_COUNT_POLICY_EXCEEDED =
            code("skill.node_count_policy_exceeded");

    public static final ValidationIssueCode DEFINITION_UNKNOWN_TYPE = code("definition.unknown_type");
    public static final ValidationIssueCode DEFINITION_PAYLOAD_SCHEMA_FUTURE =
            code("definition.payload_schema_future");
    public static final ValidationIssueCode DEFINITION_PAYLOAD_MIGRATION_MISSING_EDGE =
            code("definition.payload_migration_missing_edge");
    public static final ValidationIssueCode DEFINITION_PAYLOAD_MIGRATION_FAILED =
            code("definition.payload_migration_failed");
    public static final ValidationIssueCode DEFINITION_PAYLOAD_DECODE_ERROR =
            code("definition.payload_decode_error");
    public static final ValidationIssueCode DEFINITION_PAYLOAD_CODEC_EXCEPTION =
            code("definition.payload_codec_exception");

    public static final ValidationIssueCode DESCRIPTOR_INSPECTOR_MISSING =
            code("descriptor.inspector_missing");
    public static final ValidationIssueCode DESCRIPTOR_INSPECTOR_EXCEPTION =
            code("descriptor.inspector_exception");
    public static final ValidationIssueCode DESCRIPTOR_INSPECTOR_CONTRACT_VIOLATION =
            code("descriptor.inspector_contract_violation");
    public static final ValidationIssueCode DESCRIPTOR_VALIDATOR_EXCEPTION =
            code("descriptor.validator_exception");
    public static final ValidationIssueCode DESCRIPTOR_VALIDATOR_CONTRACT_VIOLATION =
            code("descriptor.validator_contract_violation");
    public static final ValidationIssueCode DESCRIPTOR_CAPABILITIES_EXCEPTION =
            code("descriptor.capabilities_exception");
    public static final ValidationIssueCode DESCRIPTOR_CAPABILITIES_CONTRACT_VIOLATION =
            code("descriptor.capabilities_contract_violation");

    public static final ValidationIssueCode REFERENCE_NEGATIVE_INDEX = code("reference.negative_index");
    public static final ValidationIssueCode REFERENCE_NOT_PRIOR = code("reference.not_prior");
    public static final ValidationIssueCode REFERENCE_DUPLICATE = code("reference.duplicate");
    public static final ValidationIssueCode REFERENCE_FIRST_NODE_PRIOR_DEPENDENCY =
            code("reference.first_node_prior_dependency");
    public static final ValidationIssueCode REFERENCE_PRIOR_SOURCE_MISSING =
            code("reference.prior_source_missing");
    public static final ValidationIssueCode REFERENCE_PRIOR_TARGET_MISSING =
            code("reference.prior_target_missing");
    public static final ValidationIssueCode REFERENCE_UNEXPECTED_PRIOR_SOURCE =
            code("reference.unexpected_prior_source");
    public static final ValidationIssueCode REFERENCE_UNEXPECTED_PRIOR_TARGET =
            code("reference.unexpected_prior_target");
    public static final ValidationIssueCode REFERENCE_PRODUCER_UNRESOLVED =
            code("reference.producer_unresolved");
    public static final ValidationIssueCode REFERENCE_PRODUCER_INSPECTION_UNAVAILABLE =
            code("reference.producer_inspection_unavailable");
    public static final ValidationIssueCode REFERENCE_REQUIRED_OUTPUT_MISSING =
            code("reference.required_output_missing");
    public static final ValidationIssueCode REFERENCE_PRODUCER_ROLE_UNSUPPORTED =
            code("reference.producer_role_unsupported");
    public static final ValidationIssueCode REFERENCE_CURRENT_TARGET_UNAVAILABLE =
            code("reference.current_target_unavailable");

    public static final ValidationIssueCode TRIGGER_CONTINUATION_NOT_ALLOWED_ON_FIRST_NODE =
            code("trigger.continuation_not_allowed_on_first_node");

    public static final ValidationIssueCode CAPABILITY_REQUIRED_SOURCE_MISSING =
            code("capability.required_source_missing");
    public static final ValidationIssueCode CAPABILITY_FORBIDDEN_PRIOR_SOURCE =
            code("capability.forbidden_prior_source");
    public static final ValidationIssueCode CAPABILITY_REQUIRED_TARGET_MISSING =
            code("capability.required_target_missing");
    public static final ValidationIssueCode CAPABILITY_UNEXPECTED_TARGET =
            code("capability.unexpected_target");
    public static final ValidationIssueCode CAPABILITY_SELF_TARGET_FORBIDDEN =
            code("capability.self_target_forbidden");
    public static final ValidationIssueCode CAPABILITY_UNDECLARED_OUTPUT =
            code("capability.undeclared_output");

    public static final ValidationIssueCode APPEARANCE_UNPARSED_FALLBACK =
            code("appearance.unparsed_fallback");
    public static final ValidationIssueCode APPEARANCE_REJECTED_DEPTH_FALLBACK =
            code("appearance.rejected_depth_fallback");
    public static final ValidationIssueCode APPEARANCE_REJECTED_NODE_LIMIT_FALLBACK =
            code("appearance.rejected_node_limit_fallback");
    public static final ValidationIssueCode APPEARANCE_POLICY_DEPTH_EXCEEDED =
            code("appearance.policy_depth_exceeded");
    public static final ValidationIssueCode APPEARANCE_POLICY_NODE_COUNT_EXCEEDED =
            code("appearance.policy_node_count_exceeded");
    public static final ValidationIssueCode APPEARANCE_PROFILE_MISSING =
            code("appearance.profile_missing");
    public static final ValidationIssueCode APPEARANCE_PROFILE_AVAILABILITY_EXCEPTION =
            code("appearance.profile_availability_exception");

    public static final ValidationIssueCode READ_INTENSITY_CLAMPED_LOW =
            code("read.intensity_clamped_low");
    public static final ValidationIssueCode READ_INTENSITY_CLAMPED_HIGH =
            code("read.intensity_clamped_high");
    public static final ValidationIssueCode READ_LEGACY_NULL_PROFILE_NORMALIZED =
            code("read.legacy_null_profile_normalized");
    public static final ValidationIssueCode READ_LEGACY_NULL_SCALAR_NORMALIZED =
            code("read.legacy_null_scalar_normalized");
    public static final ValidationIssueCode READ_LEGACY_NULL_APPEARANCE_DEFAULTED =
            code("read.legacy_null_appearance_defaulted");
    public static final ValidationIssueCode READ_LEGACY_NULL_OVERRIDE_NORMALIZED =
            code("read.legacy_null_override_normalized");
    public static final ValidationIssueCode READ_UNKNOWN_APPEARANCE_FIELD_IGNORED =
            code("read.unknown_appearance_field_ignored");
    public static final ValidationIssueCode READ_REPORT_TRUNCATED = code("read.report_truncated");

    private SkillValidationIssueCodes() {
    }

    private static ValidationIssueCode code(String path) {
        return ValidationIssueCode.fromNamespaceAndPath("gramarye", path);
    }
}
