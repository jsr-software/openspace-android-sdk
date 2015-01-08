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
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import rx.Subscriber;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.locations.LocationService;
import uk.co.ordnancesurvey.osmobilesdk.raster.BasicMapProjection;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Circle;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Marker;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSMap;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Polygon;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapFragment;

public class MainActivity extends Activity {

    private static final String MAP_TAG = "map_tag";

    private static final long DEMO_DELAY = 2000;
    private static final long LOCATION_FREQUENCY = 300000;

    private static final double CIRCLE_RADIUS = 3000;
    private static final double SQUARE_OFFSET = 10000;

    private static final float STROKE_WIDTH = 3;
    private static final float MIDDLE = 0.5f;

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
                    mMap.removePolygon(polygon);
                }
            }, DEMO_DELAY);
        }
    };
    private final OSMap.OnMarkerTapListener mMarkerTapListener = new OSMap.OnMarkerTapListener() {
        @Override
        public void onMarkerTap(Marker marker) {
            Toast.makeText(MainActivity.this, "Marker tapped", Toast.LENGTH_SHORT).show();
        }
    };
    private final Subscriber<Location> mLocationSubscriber = new Subscriber<Location>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onNext(Location location) {
            drawLocationMarker(location);
        }
    };

    @Inject
    LocationService mLocationService;

    private MapFragment mMapFragment;
    private OSMap mMap;
    private Marker mDraggableMarker;
    private Marker mLocationMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Perform injection so that when this call returns all dependencies will be available for use.
        ((DemoApplication) getApplication()).inject(this);

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
        mMap.addOnMarkerTapListener(mMarkerTapListener);

        drawDraggableMarker();

        mLocationService.subscribeToLocationChanges(mLocationSubscriber, LOCATION_FREQUENCY);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Remove listeners
        mMap.removeOnMapTouchListener(mTouchListener);
        mMap.removeOnSingleTapListener(mSingleTapListener);
        mMap.removeOnLongPressListener(mLongPressListener);
        mMap.removeOnMarkerTapListener(mMarkerTapListener);

        mLocationService.unsubscribeFromLocationChanges(mLocationSubscriber);
    }

    private Circle drawCircle(Point point) {
        Circle.Builder builder = new Circle.Builder(point)
                .setRadius(CIRCLE_RADIUS)
                .setFillColor(getResources().getColor(android.R.color.white))
                .setStrokeColor(getResources().getColor(android.R.color.black))
                .setStrokeWidth(STROKE_WIDTH);

        return mMap.addCircle(builder);
    }

    private void drawDraggableMarker() {
        Marker.Builder builder = new Marker.Builder(this, new Point(250000, 250000, Point.BNG))
                .setTitle("Draggable")
                .setSnippet("Long press me to drag me");

        mDraggableMarker = mMap.addMarker(builder);
        mDraggableMarker.setIsDraggable(true);
    }

    private void drawLocationMarker(Location location) {
        final Point point = mMap.getProjection()
                .toBng(new Point(location.getLatitude(), location.getLongitude(), Point.WGS84));

        if (mLocationMarker == null) {
            final Marker.Builder builder = new Marker.Builder(this, point)
                    .setIconAnchor(MIDDLE, MIDDLE);

            mLocationMarker = mMap.addMarker(builder);
        } else {
            mLocationMarker.setPoint(point);
        }
    }

    private Marker drawMarker(Point point) {
        Marker.Builder builder = new Marker.Builder(this, point)
                .setTitle("Some title")
                .setSnippet("Some snippet");

        return mMap.addMarker(builder);
    }

    private Polygon drawSquare(Point point) {

        final double x = point.getX();
        final double y = point.getY();

        List<Point> points = new ArrayList<>();
        points.add(new Point(x - SQUARE_OFFSET, y - SQUARE_OFFSET, Point.BNG));
        points.add(new Point(x - SQUARE_OFFSET, y + SQUARE_OFFSET, Point.BNG));
        points.add(new Point(x + SQUARE_OFFSET, y + SQUARE_OFFSET, Point.BNG));
        points.add(new Point(x + SQUARE_OFFSET, y - SQUARE_OFFSET, Point.BNG));

        Polygon.Builder builder = new  Polygon.Builder()
                .setPoints(points)
                .setFillColor(getResources().getColor(android.R.color.white))
                .setStrokeColor(getResources().getColor(android.R.color.black))
                .setStrokeWidth(STROKE_WIDTH);

        return mMap.addPolygon(builder);
    }
}
