/**
 * OpenSpace Android SDK Licence Terms
 *
 * The OpenSpace Android SDK is protected by © Crown copyright – Ordnance Survey 2013.[https://github.com/OrdnanceSurvey]
 *
 * All rights reserved (subject to the BSD licence terms as follows):.
 *
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * Neither the name of Ordnance Survey nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 */
package uk.co.ordnancesurvey.osmobilesdk;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

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

public class MainActivity extends Activity {

    private static final String MAP_TAG = "map_tag";

    private static final long DEMO_DELAY = 2000;

    private static final double CIRCLE_RADIUS = 3000;
    private static final double SQUARE_OFFSET = 10000;

    private static final float STROKE_WIDTH = 3;

    private final OSMap.OnMapTouchListener mTouchListener = new OSMap.OnMapTouchListener() {
        @Override
        public void onMapTouch(Point point) {
            Toast.makeText(MainActivity.this,
                    "New point: " + point.getX() + ", " + point.getY(),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    };
    private final OSMap.OnSingleTapListener mSingleTapListener = new OSMap.OnSingleTapListener() {
        @Override
        public void onSingleTap(Point point) {
            final Marker marker = drawMarker(point);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mMap.removeMarker(marker);
                }
            }, DEMO_DELAY);
        }
    };
    private final OSMap.OnLongPressListener mLongPressListener = new OSMap.OnLongPressListener() {
        @Override
        public void onLongPress(Point point) {
            final Polygon polygon = drawSquare(point);
            final Circle circle = drawCircle(point);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mMap.removeCircle(circle);
                    mMap.removePolyOverlay(polygon);
                }
            }, DEMO_DELAY);
        }
    };

    private MapFragment mMapFragment;
    private OSMap mMap;

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
        // Add listeners
        mMap = mMapFragment.getMap().getMap();
        mMap.addOnMapTouchListener(mTouchListener);
        mMap.addOnSingleTapListener(mSingleTapListener);
        mMap.addOnLongPressListener(mLongPressListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remove listeners
        mMap.removeOnMapTouchListener(mTouchListener);
        mMap.removeOnSingleTapListener(mSingleTapListener);
        mMap.removeOnLongPressListener(mLongPressListener);
    }

    private Circle drawCircle(Point point) {
        CircleOptions options = new CircleOptions()
                .center(point)
                .radius(CIRCLE_RADIUS)
                .fillColor(getResources().getColor(android.R.color.white))
                .strokeColor(getResources().getColor(android.R.color.black))
                .strokeWidth(STROKE_WIDTH);

        return mMap.addCircle(options);
    }

    private Marker drawMarker(Point point) {
        MarkerOptions options = new MarkerOptions()
                .setPoint(point)
                .title("Some title")
                .snippet("Some snippet");
        return mMap.addMarker(options);
    }

    private Polygon drawSquare(Point point) {

        final double x = point.getX();
        final double y = point.getY();

        PolygonOptions polygonOptions = new PolygonOptions()
                .add(new Point(x - SQUARE_OFFSET, y - SQUARE_OFFSET, Point.BNG))
                .add(new Point(x - SQUARE_OFFSET, y + SQUARE_OFFSET, Point.BNG))
                .add(new Point(x + SQUARE_OFFSET, y + SQUARE_OFFSET, Point.BNG))
                .add(new Point(x + SQUARE_OFFSET, y - SQUARE_OFFSET, Point.BNG))
                .fillColor(getResources().getColor(android.R.color.white))
                .strokeColor(getResources().getColor(android.R.color.black))
                .strokeWidth(STROKE_WIDTH);

        return mMap.addPolygon(polygonOptions);
    } 
}
