package uk.co.ordnancesurvey.osmobilesdk;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;

import uk.co.ordnancesurvey.osmobilesdk.gis.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.Circle;
import uk.co.ordnancesurvey.osmobilesdk.raster.CircleOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.Marker;
import uk.co.ordnancesurvey.osmobilesdk.raster.MarkerOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSMap;
import uk.co.ordnancesurvey.osmobilesdk.raster.Polygon;
import uk.co.ordnancesurvey.osmobilesdk.raster.PolygonOptions;
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
                final Marker marker = map.addMarker(options);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        map.removeMarker(marker);
                    }
                }, 2000);
                return true;
            }
        });

        map.setOnMapLongClickListener(new OSMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(Point point) {

                PolygonOptions polygonOptions = new PolygonOptions()
                        .add(new Point(point.getX() - 10000, point.getY() - 10000, Point.BNG))
                        .add(new Point(point.getX() - 10000, point.getY() + 10000, Point.BNG))
                        .add(new Point(point.getX() + 10000, point.getY() + 10000, Point.BNG))
                        .add(new Point(point.getX() + 10000, point.getY() - 10000, Point.BNG))
                        .fillColor(getResources().getColor(android.R.color.white))
                        .strokeColor(getResources().getColor(android.R.color.black))
                        .strokeWidth(3);

                final Polygon polygon = map.addPolygon(polygonOptions);

                CircleOptions options = new CircleOptions()
                        .center(point)
                        .radius(3000)
                        .fillColor(getResources().getColor(android.R.color.white))
                        .strokeColor(getResources().getColor(android.R.color.black))
                        .strokeWidth(3);

                final Circle circle = map.addCircle(options);

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        map.removeCircle(circle);
                        map.removePolyOverlay(polygon);
                    }
                }, 2000);
            }
        });
    }
}
