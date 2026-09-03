package com.yo1no.gramarye;

public final class P6RuntimeExecutionCapability {
    private static final P6RuntimeExecutionCapability INSTANCE =
            new P6RuntimeExecutionCapability();

    private P6RuntimeExecutionCapability() {}

    static P6RuntimeExecutionCapability forRuntimeAdapter() {
        return INSTANCE;
    }
}
