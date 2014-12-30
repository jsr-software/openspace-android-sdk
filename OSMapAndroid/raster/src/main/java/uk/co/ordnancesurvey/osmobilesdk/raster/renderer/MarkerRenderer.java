package uk.co.ordnancesurvey.osmobilesdk.raster.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import uk.co.ordnancesurvey.osmobilesdk.gis.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLImageCache;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLMapRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.Marker;
import uk.co.ordnancesurvey.osmobilesdk.raster.MarkerOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.ScreenProjection;

public class MarkerRenderer extends BaseRenderer {

    public interface MarkerRendererListener extends RendererListener {
        void onInfoWindowClick(Marker marker);

        boolean onMarkerClick(Marker marker);

        void onMarkerDrag(Marker marker);

        void onMarkerDragEnd(Marker marker);

        void onMarkerDragStart(Marker marker);
    }

    private interface MarkerDrawRunnable {
        // Return true if iteration should stop.
        boolean run(GLProgramService programService, Marker marker, PointF tempPoint, float[] tempMatrix, float[] mvpMatrix, GLImageCache glImageCache);
    }

    private interface MarkerClickRunnable {
        // Return true if iteration should stop.
       boolean run(Marker marker);
    }

    private final LinkedList<Marker> mMarkers = new LinkedList<>();
    private final ReentrantReadWriteLock mMarkersLock = new ReentrantReadWriteLock();
    private final Context mContext;
    private final MarkerDrawRunnable mDrawMarkerRunnable = new MarkerDrawRunnable() {
        @Override
        public boolean run(GLProgramService programService, Marker marker, PointF tempPoint, float[] tempMatrix, float[] mvpMatrix, GLImageCache glImageCache) {
            marker.glDraw(mvpMatrix, tempMatrix, glImageCache, tempPoint,
                    programService.getShaderProgram());
            return false;
        }
    };
    private final MarkerRendererListener mMarkerRendererListener;

    private Marker mExpandedMarker = null;

    public MarkerRenderer(Context context, GLMapRenderer mapRenderer, MarkerRendererListener listener) {
        super(mapRenderer, listener);
        mContext = context;
        mMarkerRendererListener = listener;
    }

    public Marker addMarker(MarkerOptions markerOptions) {
        Bitmap icon = markerOptions.getIcon().loadBitmap(mContext);
        Marker marker = new Marker(markerOptions, icon, mMapRenderer, this);
        mMarkersLock.writeLock().lock();
        try {
            mMarkers.add(marker);
        } finally {
            mMarkersLock.writeLock().unlock();
        }
        emitRenderRequest();
        return marker;
    }

    public void clear() {
        mMarkersLock.writeLock().lock();
        try {
            mMarkers.clear();
            mExpandedMarker = null;
        } finally {
            mMarkersLock.writeLock().unlock();
        }
    }

    public void onDrag(ScreenProjection projection, float screenx, float screeny, Marker marker) {
        emitMarkerDrag(marker);
        updateMarkerPosition(projection, marker, screenx, screeny);
    }

    public void onDragEnded(ScreenProjection projection, float screenx, float screeny, Marker marker) {
        emitMarkerDragEnd(marker);
        updateMarkerPosition(projection, marker, screenx, screeny);
    }

    public void onDrawFrame(GLProgramService programService, ScreenProjection projection, PointF tempPoint, float[] tempMatrix, float[] mvpMatrix, GLImageCache glImageCache) {
        programService.setActiveProgram(GLProgramService.GLProgramType.SHADER);

        drawMarkers(programService, projection, tempPoint, tempMatrix, mvpMatrix, glImageCache);
    }

    // Callback from marker when show/hideInfoWindow is called
    public void onInfoWindowShown(Marker marker) {
        // if we are setting a new expanded marker, hide the old one.
        assert !mMarkersLock.isWriteLockedByCurrentThread();
        mMarkersLock.writeLock().lock();
        try {
            if (mExpandedMarker != null && mExpandedMarker != marker) {
                // Need to hide the old one. This will cause a callback into this function
                // with a null parameter
                mExpandedMarker.hideInfoWindow();
            }
            mExpandedMarker = marker;
        } finally {
            mMarkersLock.writeLock().unlock();
        }
        emitRenderRequest();
    }

    public void removeMarker(Marker marker) {
        mMarkersLock.writeLock().lock();
        try {
            mMarkers.remove(marker);
            if (mExpandedMarker == marker) {
                mExpandedMarker = null;
            }
        } finally {
            mMarkersLock.writeLock().unlock();
        }
        emitRenderRequest();
    }

    public Marker longClick(ScreenProjection projection, PointF screenLocation) {
        // TODO do we need to handle stacked markers where one marker declines the touch?
        Marker marker = findDraggableMarker(projection, screenLocation);
        if (marker == null) {
            return null;
        }

        emitMarkerDragStart(marker);

        // Set position up a bit, so we can see the marker.
        updateMarkerPosition(projection, marker, screenLocation.x, screenLocation.y);
        // TODO scroll map up if necessary
        return marker;
    }

    public boolean singleClick(ScreenProjection projection, PointF screenLocation) {
        // Check for a click on an info window first.
        if (mExpandedMarker != null) {
            if (mExpandedMarker.isClickOnInfoWindow(screenLocation)) {
                emitInfoWindowClick(mExpandedMarker);
            }
        }

        boolean handled = false;

        // TODO do we need to handle stacked markers where one marker declines the touch?
        Marker marker = findMarker(projection, screenLocation);
        if (marker != null) {
            handled = emitMarkerClick(marker);
            if (!handled) {
                if (marker == mExpandedMarker) {
                    marker.hideInfoWindow();
                } else {
                    marker.showInfoWindow();
                }
                // TODO move map to ensure visible
                handled = true;
            }
        }

        return handled;
    }

    private void drawMarkers(GLProgramService programService, final ScreenProjection projection, PointF tempPoint, float[] tempMatrix, float[] mvpMatrix, GLImageCache glImageCache) {
        // Draw from the bottom up, so that top most marker is fully visible even if overlapped
        iterateVisibleMarkers(programService, projection, tempPoint, tempMatrix, mvpMatrix, glImageCache);
    }

    private Marker findMarker(final ScreenProjection projection, final PointF screenLocation, final boolean draggableOnly) {
        final PointF tempPoint = new PointF();
        final RectF tempRect = new RectF();
        MarkerClickRunnable clickRunnable = new MarkerClickRunnable() {
            @Override
            public boolean run(Marker marker) {
                return (!draggableOnly || marker.isDraggable()) && marker.containsPoint(projection, screenLocation, tempPoint, tempRect);
            }
        };
        // Iterate from the top-down, since we're looking to capture a click.
        return iterateMarkersForClick(clickRunnable, projection);
    }

    private Marker findMarker(ScreenProjection projection, PointF screenLocation) {
        return findMarker(projection, screenLocation, false);
    }

    private Marker findDraggableMarker(ScreenProjection projection, PointF screenLocation) {
        return findMarker(projection, screenLocation, true);
    }

    private Marker iterateMarkersForClick(MarkerClickRunnable runnable, ScreenProjection projection) {
        Marker ret;
        BoundingBox boundingBox = projection.getExpandedVisibleBounds();

        mMarkersLock.readLock().lock();

        Iterator<Marker> iter = mMarkers.descendingIterator();
        ret = processMarkerForClick(runnable, mExpandedMarker, boundingBox);
        Marker marker;

        while (ret == null && iter.hasNext()) {
            marker = iter.next();
            // processMarker returns non-null if iteration should stop.
            ret = processMarkerForClick(runnable, marker, boundingBox);
        }

        if (ret == null) {
            ret = processMarkerForClick(runnable, mExpandedMarker, boundingBox);
        }

        mMarkersLock.readLock().unlock();
        return ret;
    }

    private Marker iterateVisibleMarkers(GLProgramService programService, ScreenProjection projection, PointF tempPoint, float[] tempMatrix,
                                             float[] mvpMatrix, GLImageCache glImageCache) {
        Marker ret = null;
        // Look at more markers than are nominally visible, in case their bitmap covers the relevant area.
        // We extend to four times the actual screen area.
        // TODO can we do something more intelligent than this... like remember the maximum bitmap size for markers, plus take
        // account of anchors?
        BoundingBox boundingBox = projection.getExpandedVisibleBounds();

        mMarkersLock.readLock().lock();
        Iterator<Marker> iter = mMarkers.iterator();
        Marker marker;

        while (ret == null && iter.hasNext()) {
            marker = iter.next();
            // processMarker returns non-null if iteration should stop.
            ret = processMarker(programService, marker, boundingBox, tempPoint, tempMatrix, mvpMatrix, glImageCache);
        }

        if (ret == null) {
            ret = processMarker(programService, mExpandedMarker, boundingBox, tempPoint, tempMatrix, mvpMatrix,  glImageCache);
        }

        mMarkersLock.readLock().unlock();
        return ret;
    }

    private Marker processMarker(GLProgramService programService, Marker marker,
                                     BoundingBox boundingBox, PointF tempPoint, float[] tempMatrix,
                                     float[] mvpMatrix, GLImageCache glImageCache) {
        // Check bounds
        if (marker == null) {
            return null;
        }

        Point gp = marker.getPoint();

        // Skip invisible or out of visible area markers
        if (!marker.isVisible() || !boundingBox.contains(gp)) {
            return null;
        }

        if (mDrawMarkerRunnable.run(programService, marker, tempPoint, tempMatrix, mvpMatrix, glImageCache)) {
            return marker;
        }

        return null;
    }

    private Marker processMarkerForClick(MarkerClickRunnable runnable, Marker marker, BoundingBox boundingBox) {
        if (marker == null) {
            return null;
        }

        Point gp = marker.getPoint();

        // Skip invisible or out of visible area markers
        if (!marker.isVisible() || !boundingBox.contains(gp)) {
            return null;
        }

        if (runnable.run(marker)) {
            return marker;
        }

        return null;
    }

    private void updateMarkerPosition(ScreenProjection projection, Marker marker, float x, float y) {
        Point unclamped = projection.fromScreenLocation(x, y);
        Point clamped = BngUtil.clampToBngBounds(unclamped);
        marker.setPoint(clamped);
    }

    private void emitInfoWindowClick(Marker marker) {
        if (mMarkerRendererListener != null) {
            mMarkerRendererListener.onInfoWindowClick(marker);
        }
    }

    private boolean emitMarkerClick(Marker marker) {
        if (mMarkerRendererListener != null) {
            return mMarkerRendererListener.onMarkerClick(marker);
        }
        return false;
    }

    private void emitMarkerDrag(Marker marker) {
        if (mMarkerRendererListener != null) {
            mMarkerRendererListener.onMarkerDrag(marker);
        }
    }

    private void emitMarkerDragEnd(Marker marker) {
        if (mMarkerRendererListener != null) {
            mMarkerRendererListener.onMarkerDragEnd(marker);
        }
    }

    private void emitMarkerDragStart(Marker marker) {
        if (mMarkerRendererListener != null) {
            mMarkerRendererListener.onMarkerDragStart(marker);
        }

    }
}
