package com.jme3.shadow;

public enum EdgeFilteringMode {
    Nearest(10),
    Bilinear(1),
    Dither(2),
    PCF4(3),
    PCFPOISSON(4),
    PCF8(5);

    int materialParamValue;

    private EdgeFilteringMode(int val) {
        this.materialParamValue = val;
    }

    public int getMaterialParamValue() {
        return this.materialParamValue;
    }
}
