package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;

/**
 * One pure transformation of the logical {@code SkillDocument} outer schema. Plans accept it only
 * when it describes the adjacent edge {@code fromVersion() -> fromVersion() + 1}.
 *
 * <p>Trigger and Action payloads, unparsed appearance roots, and persistence token sentinels are
 * opaque slots. A production step must not inspect, traverse, type-test, compare, hash, branch on,
 * relocate, add, delete, duplicate, or otherwise depend on those slots or on their raw family,
 * registry context, compression policy, token identifier, or data. It must preserve each
 * DefinitionEnvelope type and payload schema version and carry every opaque slot through exactly
 * once at its original location. Payload transformation and payload schema advancement belong only
 * to payload migration.
 *
 * <p>Implementations may change only the skill schema version and document or node shell fields
 * explicitly owned by the adjacent skill-schema edge. They transform the supplied defensive copy
 * without mutating external state, emit no facts directly, and retain the input's exact {@link
 * com.mojang.serialization.DynamicOps} instance. Every production step requires
 * representation-independence tests covering JSON, NBT, RegistryOps, and persistence token views.
 */
public interface SkillMigrationStep {
    int fromVersion();

    int toVersion();

    DataResult<SkillMigrationStepOutput> migrate(Dynamic<?> defensiveLogicalDocumentCopy);
}
