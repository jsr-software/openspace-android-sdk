package uk.co.ordnancesurvey.osmobilesdk.raster;

import android.graphics.Bitmap;

public interface TileFetcherDelegate {
    public abstract void tileReadyAsyncCallback(final MapTile tile, final Bitmap bmp);
}
