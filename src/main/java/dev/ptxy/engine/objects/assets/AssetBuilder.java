package dev.ptxy.engine.objects.assets;

public class AssetBuilder {
    private Asset asset;

    public Asset build() {
        return null;
    }

    private void checkAssetIntegrityBasedOnType() {
        AssetType typeForChecks = asset.getType();
        switch (typeForChecks) {
            case GRASS:
                checkGrassAssetIntegrity();
            case GROUND:
                checkGroundAssetIntegrity();
            default:
                checkBaseAssetIntegrity();
        }
    }

    private void checkGrassAssetIntegrity() {
        checkBaseAssetIntegrity();

    }

    private void checkGroundAssetIntegrity() {
        checkBaseAssetIntegrity();
        if(asset.getNoiseTexture() == null) throwAssetIntegrityException(asset, "Grass asset must have a noise texture");
    }

    private void checkBaseAssetIntegrity() {

    }

    private void throwAssetIntegrityException(Asset asset, String infoMessage) {
        throw new IllegalStateException("Asset integrity error while trying to construct Asset from type: " + asset.getType().name()+"\n InfoMessage: " + infoMessage);
    }
}