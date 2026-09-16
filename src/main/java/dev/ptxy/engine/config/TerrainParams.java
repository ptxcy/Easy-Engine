package dev.ptxy.engine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true)
public final class TerrainParams {
    private volatile int octaves;
    private volatile double heightScale;
    private volatile float heightAmplitude;
    private volatile double tempScale;
    private volatile double humidityScale;
    private volatile double heightTempLapse;

    private volatile double minRow;
    private volatile double maxRow;
    private volatile double minCol;
    private volatile double maxCol;

    public static TerrainParams fromTerrainConfig(TerrainConfig config) {
        TerrainParams params = new TerrainParams();
        params.octaves(config.heightOctaves());
        params.heightScale(config.heightScale());
        params.heightAmplitude(config.heightAmplitude());
        params.tempScale(config.tempScale());
        params.humidityScale(config.humidityScale());
        params.heightTempLapse(config.heightTempLapse());
        params.minRow(0);
        params.maxRow(2);
        params.minCol(0);
        params.maxCol(2);
        return params;
    }
}
