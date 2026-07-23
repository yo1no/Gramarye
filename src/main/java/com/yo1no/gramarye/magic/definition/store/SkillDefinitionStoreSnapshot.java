package com.yo1no.gramarye.magic.definition.store;

import java.util.List;
import java.util.Objects;

final class SkillDefinitionStoreSnapshot {
    private final List<SkillHistorySnapshot> histories;

    SkillDefinitionStoreSnapshot(List<SkillHistorySnapshot> histories) {
        this.histories = List.copyOf(Objects.requireNonNull(histories, "histories"));
    }

    List<SkillHistorySnapshot> histories() {
        return histories;
    }

    @Override
    public String toString() {
        return "SkillDefinitionStoreSnapshot[historyCount=" + histories.size() + "]";
    }
}
