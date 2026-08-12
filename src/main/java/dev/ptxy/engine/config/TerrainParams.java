package dev.ptxy.engine.config;

/*
 * Mutable Gegenstück zu TerrainConfig: dieselben Werte, aber zur Laufzeit veränderbar (Editor-
 * Panel schreibt hier hinein, Map/ChunkManager lesen live). chunkSize/chunkResolution/
 * renderDistance/workerThreads bleiben bewusst in TerrainConfig -- die Chunk-Topologie ist
 * klassenlade-fest und nicht Teil des Editors.
 */
public final class TerrainParams {
    private int octaves;
    private double heightScale;
    private float heightAmplitude;
    private double tempScale;
    private double humidityScale;
    private double heightTempLapse;

    // Klemmt die aus Temperatur/Feuchtigkeit abgeleitete Gitterposition (0..2 je Achse, vor der
    // Bilinear-Interpolation) auf diesen Bereich -- die ganze sichtbare Fläche zeigt dann nur
    // noch Zellen innerhalb dieses Rechtecks, stetig verblendet, ohne Nähte. min=max auf beiden
    // Achsen isoliert eine einzelne Zelle (Spezialfall). Default 0..2 = unverändertes Verhalten,
    // die komplette Tabelle ist sichtbar.
    private double minRow;
    private double maxRow;
    private double minCol;
    private double maxCol;

    public static TerrainParams fromTerrainConfig(TerrainConfig config) {
        TerrainParams params = new TerrainParams();
        params.octaves = config.heightOctaves();
        params.heightScale = config.heightScale();
        params.heightAmplitude = config.heightAmplitude();
        params.tempScale = config.tempScale();
        params.humidityScale = config.humidityScale();
        params.heightTempLapse = config.heightTempLapse();
        params.minRow = 0;
        params.maxRow = 2;
        params.minCol = 0;
        params.maxCol = 2;
        return params;
    }

    public int octaves() {
        return octaves;
    }

    public void setOctaves(int octaves) {
        this.octaves = octaves;
    }

    public double heightScale() {
        return heightScale;
    }

    public void setHeightScale(double heightScale) {
        this.heightScale = heightScale;
    }

    public float heightAmplitude() {
        return heightAmplitude;
    }

    public void setHeightAmplitude(float heightAmplitude) {
        this.heightAmplitude = heightAmplitude;
    }

    public double tempScale() {
        return tempScale;
    }

    public void setTempScale(double tempScale) {
        this.tempScale = tempScale;
    }

    public double humidityScale() {
        return humidityScale;
    }

    public void setHumidityScale(double humidityScale) {
        this.humidityScale = humidityScale;
    }

    public double heightTempLapse() {
        return heightTempLapse;
    }

    public void setHeightTempLapse(double heightTempLapse) {
        this.heightTempLapse = heightTempLapse;
    }

    public double minRow() {
        return minRow;
    }

    public void setMinRow(double minRow) {
        this.minRow = minRow;
    }

    public double maxRow() {
        return maxRow;
    }

    public void setMaxRow(double maxRow) {
        this.maxRow = maxRow;
    }

    public double minCol() {
        return minCol;
    }

    public void setMinCol(double minCol) {
        this.minCol = minCol;
    }

    public double maxCol() {
        return maxCol;
    }

    public void setMaxCol(double maxCol) {
        this.maxCol = maxCol;
    }
}
