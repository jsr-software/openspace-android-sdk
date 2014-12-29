package uk.co.ordnancesurvey.osmobilesdk.raster.layers;

import android.graphics.Bitmap;

import uk.co.ordnancesurvey.osmobilesdk.raster.MapTile;

public interface TileServiceDelegate {
    public abstract void tileReadyAsyncCallback(final MapTile tile, final Bitmap bmp);
}
