package com.yo1no.gramarye.magic.network;

final class AimHint {
    private final int x;
    private final int y;
    private final int z;

    AimHint(int x, int y, int z) {
        if (!componentsValid(x, y, z)) {
            throw new P7SemanticInvariantException("unvalidated Q15 aim hint");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static boolean componentsValid(int x, int y, int z) {
        return componentValid(x)
                && componentValid(y)
                && componentValid(z)
                && (x != 0 || y != 0 || z != 0);
    }

    private static boolean componentValid(int component) {
        return component >= P7NetworkBounds.Q15_MIN
                && component <= P7NetworkBounds.Q15_MAX
                && component != P7NetworkBounds.Q15_RESERVED;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int z() {
        return z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AimHint that)) {
            return false;
        }
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        var result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        return 31 * result + Integer.hashCode(z);
    }

    @Override
    public String toString() {
        return "AimHint[x=" + x + ", y=" + y + ", z=" + z + ']';
    }
}
