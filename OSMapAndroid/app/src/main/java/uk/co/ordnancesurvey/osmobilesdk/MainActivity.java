package uk.co.ordnancesurvey.osmobilesdk;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;

import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.Marker;
import uk.co.ordnancesurvey.osmobilesdk.raster.MarkerOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSMap;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapFragment;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Basemap;

public class MainActivity extends Activity {

    private static final String MAP_TAG = "map_tag";
    private MapFragment mMapFragment;

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
            mMapFragment = (MapFragment) getFragmentManager().findFragmentByTag(MAP_TAG);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        final OSMap map = mMapFragment.getMap().getMap();
        map.setOnMapClickListener(new OSMap.OnMapClickListener() {
            @Override
            public boolean onMapClick(Point point) {
                MarkerOptions options = new MarkerOptions()
                        .setPoint(point)
                        .title("Some title")
                        .snippet("Some snippet");
                map.addMarker(options);
                return false;
            }
        });
    }
}
