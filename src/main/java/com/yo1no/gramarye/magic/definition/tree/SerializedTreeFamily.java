package com.yo1no.gramarye.magic.definition.tree;

/** Supported serialized tree families that can be defensively snapshotted. */
public enum SerializedTreeFamily {
    JSON("json"),
    NBT("nbt");

    private final String serializedName;

    SerializedTreeFamily(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
