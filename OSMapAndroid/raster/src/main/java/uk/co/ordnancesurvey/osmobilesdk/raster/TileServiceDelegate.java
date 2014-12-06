package uk.co.ordnancesurvey.osmobilesdk.raster;

import android.graphics.Bitmap;

public interface TileServiceDelegate {
    public abstract void tileReadyAsyncCallback(final MapTile tile, final Bitmap bmp);
}
