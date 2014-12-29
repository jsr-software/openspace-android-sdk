package uk.co.ordnancesurvey.osmobilesdk;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;

import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapFragment;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Basemap;

public class MainActivity extends Activity {

    private static final String MAP_TAG = "map_tag";
    private Fragment mMapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            MapConfiguration.Builder builder = new MapConfiguration.Builder()
                    .setBaseMap(new OverviewBaseMap())
                    .setIsPro(true)
                    .setOfflineTileSource(getExternalFilesDir(null));

            mMapFragment = MapFragment.newInstance(builder.build());

            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, mMapFragment, MAP_TAG)
                    .commit();
        } else {
            mMapFragment = getFragmentManager().findFragmentByTag(MAP_TAG);
        }
    }
}
