package uk.co.ordnancesurvey.osmobilesdk.raster.layers;

public class Layer {
    private final String mProductCode;
    private final String mLayerCode;
    private final int mTileSizePixels;
    private final float mTileSizeMetres;
    private final float mMetresPerPixel;

    public Layer(String productCode, String layerCode, int tileSizePixels, float tileSizeMetres) {
        mProductCode = productCode;
        mLayerCode = layerCode;
        mTileSizePixels = tileSizePixels;
        mTileSizeMetres = tileSizeMetres;
        mMetresPerPixel = tileSizeMetres/tileSizePixels;
    }

    public String getProductCode() {
        return mProductCode;
    }

    public float getMetresPerPixel() {
        return mMetresPerPixel;
    }

    public int getTileSizeInPixels() {
        return mTileSizePixels;
    }

    public float getTileSizeInMetres() {
        return mTileSizeMetres;
    }

    public String getLayerCode() {
        return mLayerCode;
    }
}
