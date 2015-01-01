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
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import uk.co.ordnancesurvey.osmobilesdk.gis.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLImageCache;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLMapRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.Marker;
import uk.co.ordnancesurvey.osmobilesdk.raster.MarkerOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.OSMap;
import uk.co.ordnancesurvey.osmobilesdk.raster.ScreenProjection;

/**
 * The Marker Renderer class encapsulates all aspects of marker rendering, including info windows
 * and drag actions.
 */
public class MarkerRenderer extends BaseRenderer {

    private static final int MARKER_DRAG_OFFSET = 70;

    private final Context mContext;
    private final DragHandler mDragHandler;
    private final MarkerGraphic mMarkerGraphic;
    private final LinkedList<Marker> mMarkers = new LinkedList<>();
    private final ReentrantReadWriteLock mMarkersLock = new ReentrantReadWriteLock();

    private Marker mExpandedMarker = null;

    public MarkerRenderer(Context context, GLMapRenderer mapRenderer, RendererListener listener, GLImageCache imageCache) {
        super(mapRenderer, listener);
        mContext = context;
        mDragHandler = new DragHandler(context);
        mMarkerGraphic = new MarkerGraphic(imageCache);
    }




    /**
     * OLD INFO WINDOW STUFF
     */




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






















    /**
     * NEW INTERFACE
     */
    private final List<OSMap.OnInfoWindowTapListener> mInfoWindowTapListeners = new ArrayList<>();
    private final List<OSMap.OnMarkerDragListener> mMarkerDragListeners = new ArrayList<>();
    private final List<OSMap.OnMarkerTapListener> mMarkerTapListeners = new ArrayList<>();

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

    public void addOnInfoWindowTapListener(OSMap.OnInfoWindowTapListener onInfoWindowTapListener) {
        mInfoWindowTapListeners.add(onInfoWindowTapListener);
    }

    public void addOnMarkerDragListener(OSMap.OnMarkerDragListener onMarkerDragListener) {
        mMarkerDragListeners.add(onMarkerDragListener);
    }

    public void addOnMarkerTapListener(OSMap.OnMarkerTapListener onMarkerTapListener) {
        mMarkerTapListeners.add(onMarkerTapListener);
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

    public boolean isDragging() {
        return mDragHandler.isDragging();
    }

    public Marker longPress(ScreenProjection projection, PointF screenLocation) {
        Marker marker = findMarker(projection, screenLocation, true);
        if (marker == null) {
            return null;
        }

        Point point = projection.fromScreenLocation(screenLocation.x, screenLocation.y);
        Point offSetPoint = projection.fromScreenLocation(screenLocation.x, screenLocation.y - MARKER_DRAG_OFFSET);
        // Check offSetPoint as well, because we don't want to lift a marker out of bounds.
        if (!BngUtil.isInBngBounds(point) || !BngUtil.isInBngBounds(offSetPoint)) {
            return null;
        }

        mDragHandler.startDrag(projection, marker, screenLocation.x, screenLocation.y);

        return marker;
    }

    public void onDrawFrame(GLProgramService programService, GLMatrixHandler matrixHandler, ScreenProjection projection) {
        programService.setActiveProgram(GLProgramService.GLProgramType.SHADER);

        // Draw from the bottom up, so that top most marker is fully visible even if overlapped
        // Look at more markers than are nominally visible, in case their bitmap covers the relevant area.
        // We extend to four times the actual screen area.
        // TODO can we do something more intelligent than this... like remember the maximum bitmap size for markers, plus take
        // account of anchors?
        BoundingBox boundingBox = projection.getExpandedVisibleBounds();

        mMarkerGraphic.drawVisibleMarkers(programService, matrixHandler, boundingBox);
    }

    public boolean onTouch(ScreenProjection projection, MotionEvent event) {
        return mDragHandler.onDrag(projection, event);
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

    public void removeOnInfoWindowTapListener(OSMap.OnInfoWindowTapListener onInfoWindowTapListener) {
        mInfoWindowTapListeners.remove(onInfoWindowTapListener);
    }

    public void removeOnMarkerDragListener(OSMap.OnMarkerDragListener onMarkerDragListener) {
        mMarkerDragListeners.remove(onMarkerDragListener);
    }

    public void removeOnMarkerTapListener(OSMap.OnMarkerTapListener onMarkerTapListener) {
        mMarkerTapListeners.remove(onMarkerTapListener);
    }

    public boolean singleTap(ScreenProjection projection, PointF screenLocation) {
        // Check for a click on an info window first.
        if (mExpandedMarker != null) {
            if (mExpandedMarker.isClickOnInfoWindow(screenLocation)) {
                emitInfoWindowTap(mExpandedMarker);
            }
        }

        boolean handled = false;

        Marker marker = findMarker(projection, screenLocation, false);
        if (marker != null) {
            if (marker == mExpandedMarker) {
                marker.hideInfoWindow();
            } else {
                marker.showInfoWindow();
            }
            emitTap(marker);

            handled = true;
        }

        return handled;
    }





    private void emitInfoWindowTap(Marker marker) {
        for(OSMap.OnInfoWindowTapListener listener : mInfoWindowTapListeners) {
            listener.onInfoWindowTap(marker);
        }
    }

    private void emitTap(Marker marker) {
        for(OSMap.OnMarkerTapListener listener : mMarkerTapListeners) {
            listener.onMarkerTap(marker);
        }
    }

    // TODO do we need to handle stacked markers where one marker declines the touch?
    private Marker findMarker(ScreenProjection projection, PointF screenLocation, boolean draggableOnly) {
        final PointF tempPoint = new PointF();
        final RectF tempRect = new RectF();
        final BoundingBox boundingBox = projection.getExpandedVisibleBounds();

        Marker ret = null;
        mMarkersLock.readLock().lock();

        Iterator<Marker> iter = mMarkers.descendingIterator();

        if(mExpandedMarker != null) {
            ret = processMarker(projection, mExpandedMarker, boundingBox, screenLocation, tempPoint, tempRect, draggableOnly);
        }

        while (ret == null && iter.hasNext()) {
            Marker marker = iter.next();
            if (marker != null) {
                ret = processMarker(projection, marker, boundingBox, screenLocation, tempPoint, tempRect, draggableOnly);
            }
        }

        if (ret == null && mExpandedMarker != null) {
            ret = processMarker(projection, mExpandedMarker, boundingBox, screenLocation, tempPoint, tempRect, draggableOnly);
        }
        mMarkersLock.readLock().unlock();
        return ret;
    }

    private Marker processMarker(ScreenProjection projection, Marker marker, BoundingBox boundingBox,
                                 PointF screenLocation, PointF tempPoint, RectF tempRect, boolean onlyDraggable) {
        Point gp = marker.getPoint();

        if (!marker.isVisible() || !boundingBox.contains(gp)) {
            return null;
        }

        boolean markerValid = marker.containsPoint(projection, screenLocation, tempPoint, tempRect);

        if(onlyDraggable) {
            markerValid = marker.isDraggable() && markerValid;
        }

        if (markerValid) {
            return marker;
        }
        return null;
    }

    private class DragHandler {

        private final float mTouchSlopSq;

        private Marker mDraggingMarker;

        private float mDragInitialX;
        private float mDragInitialY;

        private boolean mDragStarted;

        public DragHandler(Context context) {
            float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            mTouchSlopSq = touchSlop * touchSlop;
        }

        public boolean isDragging() {
            return mDraggingMarker != null;
        }

        public boolean onDrag(ScreenProjection projection, MotionEvent event) {
            int action = event.getActionMasked();

            switch (action) {
                case MotionEvent.ACTION_UP: {
                    endDrag(projection, event);
                    break;
                }
                case MotionEvent.ACTION_CANCEL: {
                    endDrag(projection, event);
                    break;
                }
                default: {
                    if (mDragStarted) {
                        for(OSMap.OnMarkerDragListener listener : mMarkerDragListeners) {
                            listener.onMarkerDrag(mDraggingMarker);
                        }
                        updateMarkerPosition(projection, mDraggingMarker, event.getX(), event.getY());
                    } else {
                        float dx = event.getX() - mDragInitialX;
                        float dy = event.getY() - mDragInitialY;
                        if (dx * dx + dy * dy > mTouchSlopSq) {
                            mDragStarted = true;
                        }
                    }
                }
            }
            return true;
        }

        public void startDrag(ScreenProjection projection, Marker marker, float screenX, float screenY) {
            mDraggingMarker = marker;
            mDragStarted = false;
            mDragInitialX = screenX;
            mDragInitialY = screenY;
            emitDragStart(marker);
            updateMarkerPosition(projection, marker, screenX, screenY);
        }

        private void emitDragStart(Marker marker) {
            for(OSMap.OnMarkerDragListener listener : mMarkerDragListeners) {
                listener.onMarkerDragStart(marker);
            }
        }

        private void endDrag(ScreenProjection projection, MotionEvent event) {
            for(OSMap.OnMarkerDragListener listener : mMarkerDragListeners) {
                listener.onMarkerDragEnd(mDraggingMarker);
            }
            updateMarkerPosition(projection, mDraggingMarker, event.getX(), event.getY());
            mDraggingMarker = null;
        }

        private void updateMarkerPosition(ScreenProjection projection, Marker marker, float x, float y) {
            Point unclamped = projection.fromScreenLocation(x, y);
            Point clamped = BngUtil.clampToBngBounds(unclamped);
            marker.setPoint(clamped);
        }
    }

    private class MarkerGraphic {

        private final GLImageCache mGlImageCache;

        public MarkerGraphic(GLImageCache imageCache) {
            mGlImageCache = imageCache;
        }

        private void drawVisibleMarkers(GLProgramService programService, GLMatrixHandler matrixHandler, BoundingBox boundingBox) {
            mMarkersLock.readLock().lock();
            Iterator<Marker> iter = mMarkers.iterator();
            Marker marker;

            while (iter.hasNext()) {
                marker = iter.next();
                drawMarker(programService, matrixHandler, marker, boundingBox);
            }

            drawMarker(programService, matrixHandler, mExpandedMarker, boundingBox);

            mMarkersLock.readLock().unlock();
        }

        private void drawMarker(GLProgramService programService, GLMatrixHandler matrixHandler, Marker marker,
                                BoundingBox boundingBox) {
            if (marker != null && marker.isVisible()) {
                Point gp = marker.getPoint();

                // Skip invisible or out of visible area markers
                if (boundingBox.contains(gp)) {
                    marker.glDraw(programService, matrixHandler, mGlImageCache);
                }
            }
        }
    }

    private class InfoWindowHandler {

    }
}
