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
package uk.co.ordnancesurvey.osmobilesdk.raster;

import android.view.View;

import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;

/**
 * This is the main class of the Raster map module of the OS Mobile SDK and is the entry point for all methods related to the map. You cannot
 * instantiate an {@link OSMap} object directly, rather, you must obtain one from the {@link uk.co.ordnancesurvey.osmobilesdk.raster.app.MapView#getMap()} method on a
 * {@link .MapFragment} or {@link uk.co.ordnancesurvey.osmobilesdk.raster.app.MapView} that you have added to your application.
 * <p/>
 * <b>Note:</b> Similar to a android.view.View View object, an {@link OSMap} can only be read and modified from the main thread.
 * Calling {@link OSMap} methods from another thread may result in an exception.
 */
public interface OSMap {

    /**
     * Defines signatures for methods that are called when the camera changes position.
     */
    interface OnCameraChangeListener {
        /**
         * Called after the camera position has changed. During an animation, this listener may not be notified of intermediate camera positions.
         * It is always called for the final position in the animation.
         * <p/>
         * This is called on the main thread.
         *
         * @param position The CameraPosition at the end of the last camera change.
         */
        public void onCameraChange(CameraPosition position);
    }

    /**
     * Adds a marker to this map.
     * <p>The marker's icon is rendered on the map at the location Marker.position. Clicking the marker centers the camera on the marker.
     * If Marker.title is defined, the map shows an info box with the marker's title and snippet. If the marker is draggable,
     * long-clicking and then dragging the marker moves it.
     *
     * @param options A marker options object that defines how to render the marker.
     * @return The Marker that was added to the map.
     */
    public Marker addMarker(MarkerOptions options);

    public void removeMarker(Marker marker);

    /**
     * Removes all markers, overlays, and polylines from the map.
     */
    public void clear();

    /**
     * Adds a polyline to this map.
     *
     * @param options A polyline options object that defines how to render the Polyline.
     * @return The Polyline object that was added to the map.
     */
    Polyline addPolyline(PolylineOptions options);

    /**
     * Adds a polygon to this map.
     *
     * @param options A polygon options object that defines how to render the Polygon.
     * @return The Polygon object that is added to the map.
     */
    Polygon addPolygon(PolygonOptions options);

    void removePolyOverlay(PolyOverlay polyOverlay);

    /**
     * Add a circle to this map.
     *
     * @param options A circle options object that defines how to render the Circle
     * @return The Circle object that is added to the map
     */
    Circle addCircle(CircleOptions options);

    void removeCircle(Circle circle);




    /*
     * Repositions the camera. The move may be animated.
     * @param camera The new camera position and zoom level
     * @param animated If the camera move is to be animated, or made instantenously
     */
    public void moveCamera(CameraPosition camera, boolean animated);

    /**
     * Sets a callback that's invoked when the camera changes.
     *
     * @param listener The callback that's invoked when the camera changes. To unset the callback, use null.
     */
    public void setOnCameraChangeListener(OnCameraChangeListener listener);

    /**
     * Set the {@link uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration} for the current view
     *
     * @param mapConfiguration
     */
    void setMapConfiguration(MapConfiguration mapConfiguration);





    /**
     * NEW INTERFACE
     */

    /**
     * Methods on this provider are called when it is time to show an info window for a marker, regardless of the cause
     * (either a user gesture or a programmatic call to {@link Marker#showInfoWindow()}. Since there is only one info window shown at
     * any one time, this provider may choose to reuse views, or it may choose to create new views on each method invocation.
     * <p/>
     * When constructing an info-window, methods in this class are called in a defined order. To replace the default info-window,
     * override {@link #getInfoWindow(Marker)} with your custom rendering. To replace just the info-window contents, inside the default info-window
     * frame (the callout bubble), leave the default implementation of {@link #getInfoWindow(Marker)} in place and override
     * {@link #getInfoContents(Marker)} instead.
     */
    public interface InfoWindowAdapter {
        /**
         * Provides custom contents for the default info-window frame of a marker. This method is only called if
         * {@link #getInfoWindow(Marker)} first returns null. If this method returns a view, it will be placed inside
         * the default info-window frame. If you change this view after this method is called, those changes will not
         * necessarily be reflected in the rendered info-window. If this method returns null, the default rendering will be used instead.
         *
         * @param marker The marker for which an info window is being populated.
         * @return A custom view to display as contents in the info window for marker, or null to use the default content rendering instead.
         */
        View getInfoContents(Marker marker);

        /**
         * Provides a custom info-window for a marker. If this method returns a view, it is used for the entire info-window.
         * If you change this view after this method is called, those changes will not necessarily be reflected in the rendered
         * info-window. If this method returns null , the default info-window frame will be used, with contents provided by
         * {@link #getInfoContents(Marker)}.
         *
         * @param marker The marker for which an info window is being populated.
         * @return A custom info-window for marker, or null to use the default info-window frame with custom contents.
         */
        View getInfoWindow(Marker marker);
    }

    /**
     * Callback interface for when the user makes a double tap gesture on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnDoubleTapListener {
        /**
         * Called when the user makes a double tap gesture on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param point The point on the ground (projected from the screen point) that was
         *              double tapped.
         */
        void onDoubleTap(Point point);
    }

    /**
     * Callback interface for when the user makes a fling gesture on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnFlingListener {
        /**
         * Called when the user makes a fling gesture on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param velocityX The velocity of the fling in the x direction
         * @param velocityY The velocity of the fling in the y direction
         */
        void onFling(float velocityX, float velocityY);
    }

    /**
     * Callback interface for tap events on a marker's info window.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnInfoWindowTapListener {
        /**
         * Called when the marker's info window is tapped.
         *
         * @param marker The marker of the info window that was tapped.
         */
        void onInfoWindowTap(Marker marker);
    }

    /**
     * Callback interface for when the user makes a long press gesture on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnLongPressListener {
        /**
         * Called when the user makes a long press gesture on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param point The point on the ground (projected from the screen point) that was long
         *              pressed.
         */
        void onLongPress(Point point);
    }

    /**
     * Callback interface for when the user touches on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnMapTouchListener {
        /**
         * Called when the user makes or begins a touch gesture on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param point The point on the ground (projected from the screen point) that was touched.
         */
        void onMapTouch(Point point);
    }

    /**
     * Callback interface for drag events on markers.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnMarkerDragListener {
        /**
         * Called repeatedly while a marker is being dragged. The marker's location can be accessed via {@link Marker#getPoint()}.
         *
         * @param marker The marker being dragged
         */
        void onMarkerDrag(Marker marker);

        /**
         * Called when a marker has finished being dragged. The marker's location can be accessed via {@link Marker#getPoint()}.
         *
         * @param marker The marker being dragged
         */
        void onMarkerDragEnd(Marker marker);

        /**
         * Called when a marker starts being dragged. The marker's location can be accessed via {@link Marker#getPoint()};
         * this position may be different to the position prior to the start of the drag because the marker is popped up above the touch point.
         *
         * @param marker The marker being dragged
         */
        void onMarkerDragStart(Marker marker);
    }

    /**
     * Callback interface for when the user taps a marker on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnMarkerTapListener {
        /**
         * Called when the user taps a marker on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param marker The marker that was tapped.
         */
        void onMarkerTap(Marker marker);
    }

    /**
     * Callback interface for when the user makes a pan gesture on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnPanListener {
        /**
         * Called when the user makes a pan gesture on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param distanceX The distance to pan in the x direction
         * @param distanceY The distance to pan in the x direction
         */
        void onPan(float distanceX, float distanceY);
    }

    /**
     * Callback interface for when the user makes a pinch in or pinch out gesture on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnPinchListener {
        /**
         * Called when the user makes a pinch in or pinch out on the map.
         * Implementations of this method are always invoked on the main thread.
         */
        void onPinch();
    }

    /**
     * Callback interface for when the user makes a single tap on the map.
     * <p/>
     * Listeners will be invoked on the main thread.
     */
    public interface OnSingleTapListener {
        /**
         * Called when the user makes a single tap gesture on the map.
         * Implementations of this method are always invoked on the main thread.
         *
         * @param point The point on the ground (projected from the screen point) that was tapped.
         */
        void onSingleTap(Point point);
    }

    /**
     * Sets a callback object for when the Map is double tapped. Note that there can be multiple
     * callbacks added. Each callback will receive the touch event.
     *
     * @param onDoubleTapListener The callback that will be invoked on a Map double tap event
     */
    public void addOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener);

    /**
     * Sets a callback object for when the Map is flung. Note that there can be multiple
     * callbacks added. Each callback will receive the touch event.
     *
     * @param onFlingListener The callback that will be invoked on a Map fling event
     */
    public void addOnFlingListener(OnFlingListener onFlingListener);

    /**
     * Sets a callback that's invoked when a marker info window is tapped. Note that there can be multiple
     * callbacks added. Each callback will receive the touch event.
     *
     * @param onInfoWindowTapListener The callback that's invoked when a marker info window is tapped.
     */
    public void addOnInfoWindowTapListener(OnInfoWindowTapListener onInfoWindowTapListener);

    /**
     * Sets a callback object for when the Map is touched. Note that there can be multiple callbacks
     * added. Each callback will receive the touch event.
     *
     * @param onLongPressListener The callback that will be invoked on a Map long press event
     */
    public void addOnLongPressListener(OnLongPressListener onLongPressListener);

    /**
     * Sets a callback object for when the Map is touched. Note that there can be multiple callbacks
     * added. Each callback will receive the touch event.
     *
     * @param onMapTouchListener The callback that will be invoked on a Map touch event
     */
    public void addOnMapTouchListener(OnMapTouchListener onMapTouchListener);

    /**
     * Sets a callback object for when a {@link .Marker} is dragged.
     * Note that there can be multiple callbacks added. Each callback will receive the events.
     *
     * @param onMarkerDragListener The callback that will be invoked on a Marker drag event
     */
    public void addOnMarkerDragListener(OnMarkerDragListener onMarkerDragListener);

    /**
     * Sets a callback object for when a {@link .Marker} is tapped.
     * Note that there can be multiple callbacks added. Each callback will receive the event.
     *
     * @param onMarkerTapListener The callback that will be invoked on a Marker tap event
     */
    public void addOnMarkerTapListener(OnMarkerTapListener onMarkerTapListener);

    /**
     * Sets a callback object for when the Map is panned. Note that there can be multiple
     * callbacks added. Each callback will receive the touch event.
     *
     * @param onPanListener The callback that will be invoked on a Map pan event
     */
    public void addOnPanListener(OnPanListener onPanListener);

    /**
     * Sets a callback object for when the Map is pinched in or out. Note that there can be multiple
     * callbacks added. Each callback will receive the touch event.
     *
     * @param onPinchListener The callback that will be invoked on a Map pinch event
     */
    public void addOnPinchListener(OnPinchListener onPinchListener);

    /**
     * Sets a callback object for when the Map is single tapped.
     * Note that there can be multiple callbacks added.
     * Each callback will receive the single tap event.
     *
     * @param onSingleTapListener The callback that will be invoked on a Map single tap event
     */
    public void addOnSingleTapListener(OnSingleTapListener onSingleTapListener);

    /**
     * Removes the custom adapter for the rendering of contents of info windows.
     */
    public void removeInfoWindowAdapter();

    /**
     * Removes a callback object for when the Map is double tapped.
     *
     * @param onDoubleTapListener The callback that will be removed
     */
    public void removeOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener);

    /**
     * Removes a callback object for when the Map is flung.
     *
     * @param onFlingListener The callback that will be removed
     */
    public void removeOnFlingListener(OnFlingListener onFlingListener);

    /**
     * Removes a callback object for when the Map's info window is tapped.
     *
     * @param onInfoWindowTapListener The callback that will be removed
     */
    public void removeOnInfoWindowTapListener(OnInfoWindowTapListener onInfoWindowTapListener);

    /**
     * Removes a callback object for when the Map is long pressed.
     *
     * @param onLongPressListener The callback that will be removed
     */
    public void removeOnLongPressListener(OnLongPressListener onLongPressListener);

    /**
     * Removes a callback object for when the Map is touched.
     *
     * @param onMapTouchListener The callback that will be removed
     */
    public void removeOnMapTouchListener(OnMapTouchListener onMapTouchListener);

    /**
     * Removes a callback object for when a {@link .Marker} is dragged.
     *
     * @param onMarkerDragListener The callback that will be removed
     */
    public void removeOnMarkerDragListener(OnMarkerDragListener onMarkerDragListener);

    /**
     * Removes a callback object for when a {@link .Marker} is tapped.
     *
     * @param onMarkerTapListener The callback that will be removed
     */
    public void removeOnMarkerTapListener(OnMarkerTapListener onMarkerTapListener);

    /**
     * Removes a callback object for when the Map is panned.
     *
     * @param onPanListener The callback that will be removed
     */
    public void removeOnPanListener(OnPanListener onPanListener);

    /**
     * Removes a callback object for when the Map is pinched in or out.
     *
     * @param onPinchListener The callback that will be removed
     */
    public void removeOnPinchListener(OnPinchListener onPinchListener);

    /**
     * Removes a callback object for when the Map is single tapped.
     *
     * @param onSingleTapListener The callback that will be removed
     */
    public void removeOnSingleTapListener(OnSingleTapListener onSingleTapListener);

    /**
     * Sets a custom renderer for the contents of info windows.
     * <p/>
     * Like the map's event listeners, this state is not serialized with the map. If the map gets re-created
     * (e.g., due to a configuration change), you must ensure that you call this method again in order to preserve the customization.
     *
     * @param infoWindowAdapter The adapter to use for info window contents, or null to use the default content rendering in info windows.
     */
    public void setInfoWindowAdapter(InfoWindowAdapter infoWindowAdapter);


}
