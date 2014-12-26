package uk.co.ordnancesurvey.osmobilesdk.raster.layers.adapters;

import uk.co.ordnancesurvey.osmobilesdk.raster.MapTile;

public class LayerSource {

    /**
     * Blocking method to fetch a single tile.
     *
     * <b>Implementations must be thread-safe.</b>
     *
     * @param tile
     * @return
     */
    public byte[] tileData(MapTile tile){
        return null;
    }
}
