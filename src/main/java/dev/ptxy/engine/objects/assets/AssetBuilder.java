package dev.ptxy.engine.objects.assets;

import dev.ptxy.engine.objects.Triangle;
import dev.ptxy.engine.objects.properties.Material;
import dev.ptxy.engine.shader.Texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AssetBuilder {
    private Asset asset;

    public Asset build() {
        if (asset == null) {
            throw new IllegalStateException(
                    "Cannot build Asset without initializing it via startBuildingAssetOfType()");
        }

        checkAssetIntegrityBasedOnType();
        asset.prepareAssetForRendering();
        return asset;
    }

    public AssetBuilder startBuildingAssetOfType(AssetType type) {
        asset = new Asset();
        asset.setType(type);

        asset.setTriangles(new ArrayList<>());
        asset.setMaterials(new ArrayList<>());
        asset.setBaseColors(new ArrayList<>());
        asset.setMetallicRoughness(new ArrayList<>());
        asset.setNormalMaps(new ArrayList<>());
        return this;
    }

    public AssetBuilder setId(String id) {
        asset.setId(id);
        return this;
    }

    public AssetBuilder setTriangleMesh(List<Triangle> triangles) {
        asset.setTriangles(Objects.requireNonNullElseGet(triangles, ArrayList::new));
        return this;
    }

    public AssetBuilder addMaterial(Material material) {
        asset.getMaterials().add(material);
        return this;
    }

    public AssetBuilder setMaterials(List<Material> materials) {
        asset.setMaterials(Objects.requireNonNullElseGet(materials, ArrayList::new));
        return this;
    }

    public AssetBuilder addBaseColor(Texture texture) {
        asset.getBaseColors().add(texture);
        return this;
    }

    public AssetBuilder setBaseColors(List<Texture> textures) {
        asset.setBaseColors(Objects.requireNonNullElseGet(textures, ArrayList::new));
        return this;
    }

    public AssetBuilder addMetallicRoughness(Texture texture) {
        asset.getMetallicRoughness().add(texture);
        return this;
    }

    public AssetBuilder setMetallicRoughness(List<Texture> textures) {
        asset.setMetallicRoughness(Objects.requireNonNullElseGet(textures, ArrayList::new));
        return this;
    }

    public AssetBuilder addNormalMap(Texture texture) {
        asset.getNormalMaps().add(texture);
        return this;
    }

    public AssetBuilder setNormalMaps(List<Texture> textures) {
        asset.setNormalMaps(Objects.requireNonNullElseGet(textures, ArrayList::new));
        return this;
    }

    public AssetBuilder setNoiseTexture(Texture noiseTexture) {
        asset.setNoiseTexture(noiseTexture);
        return this;
    }

    private void checkAssetIntegrityBasedOnType() {
        checkGeneralAssetIntegrity();

        AssetType typeForChecks = asset.getType();
        switch (typeForChecks) {
            case GRASS -> checkGrassAssetIntegrity();
            case GROUND -> checkGroundAssetIntegrity();
        }
    }

    private void checkGrassAssetIntegrity() {
        //Until now only needs the base checks
    }

    private void checkGroundAssetIntegrity() {
        if (asset.getNoiseTexture() == null) {
            throwAssetIntegrityException(asset, "Ground asset must have a noise texture");
        }
    }

    private void checkGeneralAssetIntegrity() {
        if (asset.getId() == null || asset.getId().isBlank()) {
            throwAssetIntegrityException(asset, "Asset id was not set");
        }

        if (asset.getTriangles().isEmpty()) {
            throwAssetIntegrityException(asset, "Triangle mesh was not set");
        }

        if (asset.getBaseColors().isEmpty()) {
            throwAssetIntegrityException(asset, "BaseColor textures were not set");
        }
    }

    private void throwAssetIntegrityException(Asset asset, String infoMessage) {
        throw new IllegalStateException(
                "Asset integrity error while constructing Asset of type: "
                        + asset.getType().name()
                        + "\nAdditional Info: "
                        + infoMessage
        );
    }
}
