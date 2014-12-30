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
        boolean run(GLProgramService programService, GLMatrixHandler matrixHandler, Marker marker, GLImageCache glImageCache);
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
        public boolean run(GLProgramService programService, GLMatrixHandler matrixHandler, Marker marker, GLImageCache glImageCache) {
            marker.glDraw(programService, matrixHandler, glImageCache);
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

    public void onDrawFrame(GLProgramService programService, GLMatrixHandler matrixHandler, ScreenProjection projection, GLImageCache glImageCache) {
        programService.setActiveProgram(GLProgramService.GLProgramType.SHADER);

        drawMarkers(programService, matrixHandler, projection, glImageCache);
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

    private void drawMarkers(GLProgramService programService, GLMatrixHandler matrixHandler, final ScreenProjection projection, GLImageCache glImageCache) {
        // Draw from the bottom up, so that top most marker is fully visible even if overlapped
        iterateVisibleMarkers(programService, matrixHandler, projection, glImageCache);
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

    private Marker iterateVisibleMarkers(GLProgramService programService, GLMatrixHandler matrixHandler, ScreenProjection projection, GLImageCache glImageCache) {
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
            ret = processMarker(programService, matrixHandler, marker, boundingBox, glImageCache);
        }

        if (ret == null) {
            ret = processMarker(programService, matrixHandler, mExpandedMarker, boundingBox, glImageCache);
        }

        mMarkersLock.readLock().unlock();
        return ret;
    }

    private Marker processMarker(GLProgramService programService, GLMatrixHandler matrixHandler, Marker marker,
                                     BoundingBox boundingBox, GLImageCache glImageCache) {
        // Check bounds
        if (marker == null) {
            return null;
        }

        Point gp = marker.getPoint();

        // Skip invisible or out of visible area markers
        if (!marker.isVisible() || !boundingBox.contains(gp)) {
            return null;
        }

        if (mDrawMarkerRunnable.run(programService, matrixHandler, marker, glImageCache)) {
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
