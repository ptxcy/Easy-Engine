package dev.ptxy.engine.map;

import lombok.Getter;
import lombok.Setter;

public final class VegetationController {
    @Getter @Setter private boolean organicClumpingEnabled = true;
    @Getter @Setter private boolean windEnabled = false;
    @Getter @Setter private boolean rocksEnabled = true;
}
