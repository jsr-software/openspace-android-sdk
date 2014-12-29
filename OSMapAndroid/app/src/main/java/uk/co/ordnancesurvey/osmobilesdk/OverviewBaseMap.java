package uk.co.ordnancesurvey.osmobilesdk;

import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Basemap;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.LayerCatalog;

public class OverviewBaseMap extends Basemap {

    private static final String OVERVIEW_NAME = "Overview";
    private static final String[] OVERVIEW_LAYERS = new String[] {"CS00", "CS02", "CS04", "CS05"};

    public OverviewBaseMap() {
        super(OVERVIEW_NAME, LayerCatalog.getLayers(OVERVIEW_LAYERS));
    }
}
