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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Circle;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Marker;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Polygon;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Polyline;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.gesture.MapGestureDetector;
import uk.co.ordnancesurvey.osmobilesdk.raster.gesture.MapGestureListener;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Layer;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.TileServiceDelegate;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.CircleRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLMatrixHandler;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLProgramService;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.MarkerRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.OverlayRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.RendererListener;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.ScrollRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.TileRenderer;

import static android.opengl.GLES20.GL_BACK;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glReleaseShaderCompiler;
import static android.opengl.GLES20.glViewport;

/**
 * There are three coordinate systems (at least) in use here.
 * DisplayCoordinates are internal to this SDK, and have the origin at the centre of the screen, with x/y increasing towards the top-right. Units are pixels. Markers are rendered
 * in DisplayCoordinates.
 * ScreenCoordinates are standard android, i.e. origin top-left, x/y increases towards the bottom-right. We use these as little as possible.
 * Tiles are rendered in tile coordinates, which have the origin at the bottom left of the grid (not the screen). The units are tiles (i.e. a tile always has dimensions 1x1) and the
 * actual size of a tile is set up by modifying the projection transform.
 */
public final class GLMapRenderer extends GLSurfaceView implements GLSurfaceView.Renderer,
        TileServiceDelegate, OSMap {

    private static final String CLASS_TAG = GLMapRenderer.class.getSimpleName();

    private final Context mContext;
    private final PositionManager mPositionManager = new PositionManager();

    private final GLTileCache mGLTileCache;
    private final GLImageCache mGLImageCache;

    private final ScrollRenderer mScrollRenderer;
    private final CircleRenderer mCircleRenderer;
    private final MarkerRenderer mMarkerRenderer;
    private final OverlayRenderer mOverlayRenderer;
    private final TileRenderer mTileRenderer;

    private final GLProgramService mProgramService;
    private final GLMatrixHandler mGLMatrixHandler;
    private final FrameHandler mFrameHandler;

    private final RendererListener mRendererListener = new RendererListener() {
        @Override
        public void onRenderRequested() {
            requestRender();
        }
    };

    private final MapGestureDetector mMapGestureDetector;
    private final MapGestureListener mMapGestureListener = new MapGestureListener() {
        @Override
        public void onDoubleTap(float screenX, float screenY) {
            processDoubleTap(screenX, screenY);
        }

        @Override
        public void onFling(float velocityX, float velocityY) {
            processFling(velocityX, velocityY);
        }

        @Override
        public void onLongPress(float screenX, float screenY) {
            processLongPress(screenX, screenY);
        }

        @Override
        public void onPan(float distanceX, float distanceY) {
            processPan(distanceX, distanceY);
        }

        @Override
        public void onPinch(float focusX, float focusY, float focusChangeX, float focusChangeY, float scale) {
            processPinch(focusX, focusY, focusChangeX, focusChangeY, scale);
        }

        @Override
        public void onSingleTap(float screenX, float screenY) {
            processSingleTap(screenX, screenY);
        }

        @Override
        public void onTouch(float screenX, float screenY) {
            processTouch(screenX, screenY);
        }

        @Override
        public void onTwoFingerTap() {
            processTwoFingerTap();
        }
    };

    private final ScrollRenderer.ScrollPosition mScrollPosition = new ScrollRenderer.ScrollPosition();

    private MapConfiguration mMapConfiguration;

    // TODO: This is an icky default, but ensures that it's not null.
    // This does not actually need to be volatile, but it encourages users to read it once.
    private volatile ScreenProjection mVolatileProjection = new ScreenProjection(320, 320, mScrollPosition);
    private int mGLViewportWidth, mGLViewportHeight;

    public GLMapRenderer(Context context, MapConfiguration mapConfiguration) {
        super(context);
        mContext = context;
        if (mapConfiguration == null) {
            throw new IllegalArgumentException("Null Map Configuration");
        }

        mMapConfiguration = mapConfiguration;

        if (BuildConfig.DEBUG) {
            setDebugFlags(DEBUG_CHECK_GL_ERROR);
        }

        setEGLContextClientVersion(2);

        setEmulatorConfiguration();

        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mGLTileCache = new GLTileCache(mContext);
        mGLImageCache = new GLImageCache();

        mCircleRenderer = new CircleRenderer(this, mRendererListener);
        mMarkerRenderer = new MarkerRenderer(mContext, this, mRendererListener, mGLImageCache);
        mOverlayRenderer = new OverlayRenderer(this, mRendererListener);
        mScrollRenderer = new ScrollRenderer(context, this, mRendererListener);
        mTileRenderer = new TileRenderer(mContext, this, mGLTileCache);

        mProgramService = new GLProgramService();
        mGLMatrixHandler = new GLMatrixHandler();
        mFrameHandler = new FrameHandler(mContext);

        mMapGestureDetector = new MapGestureDetector(context, mMapGestureListener);
    }

    public void setMapLayers(Layer[] layers) {
        float[] mpps = new float[layers.length];
        int i = 0;
        for (Layer layer : layers) {
            mpps[i++] = layer.getMetresPerPixel();
        }
        mScrollRenderer.setZoomScales(mpps);
        mTileRenderer.setLayers(layers);
    }

    @Override
    public void moveCamera(CameraPosition camera, boolean animated) {
        mScrollRenderer.zoomToCenterScale(camera.target, camera.zoom, animated);
    }

    public void onDestroy() {
        mTileRenderer.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPositionManager.setInitialMapPosition();
        mTileRenderer.init(mMapConfiguration);
    }

    @Override
    public void tileReadyAsyncCallback(final MapTile tile, final Bitmap bmp) {
        queueEvent(new Runnable() {
            public void run() {
                if (bmp != null) {
                    mGLTileCache.putTextureForTile(tile, bmp);
                }
//                mTileService.finishRequest(tile);
                if (bmp != null) {
                    requestRender();
                }
            }
        });
    }

    public ScreenProjection getProjection() {
        // TODO: Is this allowed to return null in the Google Maps v2 API?
        return mVolatileProjection;
    }

    @Override
    public void onDrawFrame(GL10 unused) {

        final long frameTime = mFrameHandler.getFrameTime();

        // Update the scroll position too, because it uses SystemClock.uptimeMillis() internally
        // (ideally we'd pass it the timestamp we got above).
        mScrollRenderer.getScrollPosition(mScrollPosition, true);
        mFrameHandler.roundToPixelBoundary();

        // And create a new projection.
        ScreenProjection projection = new ScreenProjection(mGLViewportWidth, mGLViewportHeight, mScrollPosition);

        mVolatileProjection = projection;
        float metresPerPixel = projection.getMetresPerPixel();


        boolean needRedraw = mTileRenderer.onDrawFrame(mProgramService, mGLMatrixHandler, projection, frameTime, mScrollPosition);
        mOverlayRenderer.onDrawFrame(projection, mProgramService, mGLMatrixHandler, metresPerPixel);
        mCircleRenderer.onDrawFrame(projection, mProgramService, mGLMatrixHandler, mGLViewportWidth, mGLViewportHeight);
        mMarkerRenderer.onDrawFrame(mProgramService, mGLMatrixHandler, projection);

        if (needRedraw) {
            requestRender();
        }

        mFrameHandler.finishFrame();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        if (BuildConfig.DEBUG) {
            Utils.logGLInfo();
        }

        mGLTileCache.resetForSurfaceCreated();
        mGLImageCache.resetForSurfaceCreated();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        // Blending is enabled for markers and disabled for tiles, but the blend function is always the same.
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

        // White "background colour"
        glClearColor(1, 1, 1, 1);

        mProgramService.onSurfaceCreated();

        glReleaseShaderCompiler();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        if (BuildConfig.DEBUG) {
            Log.v(CLASS_TAG, "Viewport " + width + "*" + height);
        }

        mGLViewportWidth = width;
        mGLViewportHeight = height;
        glViewport(0, 0, width, height);

        // The nominal order is "near,far", but somehow we need to list them backwards.
        Matrix.orthoM(mGLMatrixHandler.getMVPOrthoMatrix(), 0, 0, width, height, 0, 1, -1);

        mScrollRenderer.setWidthHeight(width, height);
    }

    // MAP CONFIGURATION
    @Override
    public void setMapConfiguration(MapConfiguration mapConfiguration) {
        mMapConfiguration = mapConfiguration;
        mTileRenderer.init(mMapConfiguration);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMarkerRenderer.isDragging()) {
            return mMarkerRenderer.onTouch(mVolatileProjection, event);
        } else {
            return mMapGestureDetector.onTouch(this, event);
        }
    }



    /**
     * NEW INTERFACE
     */
    private final List<OnBoundsChangeListener> mBoundsChangeListeners = new ArrayList<>();
    private final List<OnDoubleTapListener> mDoubleTapListeners = new ArrayList<>();
    private final List<OnFlingListener> mFlingListeners = new ArrayList<>();
    private final List<OnLongPressListener> mLongPressListeners = new ArrayList<>();
    private final List<OnPanListener> mPanListeners = new ArrayList<>();
    private final List<OnPinchListener> mPinchListeners = new ArrayList<>();
    private final List<OnSingleTapListener> mSingleTapListeners = new ArrayList<>();
    private final List<OnMapTouchListener> mTouchListeners = new ArrayList<>();
    private final List<OnZoomChangeListener> mZoomChangeListeners = new ArrayList<>();

    @Override
    public final Circle addCircle(Circle.Builder circleBuilder) {
        return mCircleRenderer.addCircle(circleBuilder);
    }

    @Override
    public Marker addMarker(Marker.Builder builder) {
        return mMarkerRenderer.addMarker(builder);
    }

    @Override
    public Polygon addPolygon(Polygon.Builder builder) {
        return mOverlayRenderer.addPolygon(builder);
    }

    @Override
    public Polyline addPolyline(Polyline.Builder builder) {
        return mOverlayRenderer.addPolyline(builder);
    }

    @Override
    public void addOnBoundsChangeListener(OnBoundsChangeListener onBoundsChangeListener) {
        mBoundsChangeListeners.add(onBoundsChangeListener);
    }

    @Override
    public void addOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener) {
        mDoubleTapListeners.add(onDoubleTapListener);
    }

    @Override
    public void addOnFlingListener(OnFlingListener onFlingListener) {
        mFlingListeners.add(onFlingListener);
    }

    @Override
    public void addOnInfoWindowTapListener(OnInfoWindowTapListener onInfoWindowTapListener) {
        mMarkerRenderer.addOnInfoWindowTapListener(onInfoWindowTapListener);
    }

    @Override
    public void addOnLongPressListener(OnLongPressListener onLongPressListener) {
        mLongPressListeners.add(onLongPressListener);
    }

    @Override
    public void addOnMapTouchListener(OnMapTouchListener onMapTouchListener) {
        mTouchListeners.add(onMapTouchListener);
    }

    @Override
    public void addOnMarkerDragListener(OnMarkerDragListener onMarkerDragListener) {
        mMarkerRenderer.addOnMarkerDragListener(onMarkerDragListener);
    }

    @Override
    public void addOnMarkerTapListener(OnMarkerTapListener onMarkerTapListener) {
        mMarkerRenderer.addOnMarkerTapListener(onMarkerTapListener);
    }

    @Override
    public void addOnPanListener(OnPanListener onPanListener) {
        mPanListeners.add(onPanListener);
    }

    @Override
    public void addOnPinchListener(OnPinchListener onPinchListener) {
        mPinchListeners.add(onPinchListener);
    }

    @Override
    public void addOnSingleTapListener(OnSingleTapListener onSingleTapListener) {
        mSingleTapListeners.add(onSingleTapListener);
    }

    @Override
    public void addOnZoomChangeListener(OnZoomChangeListener onZoomChangeListener) {
        mZoomChangeListeners.add(onZoomChangeListener);
    }

    @Override
    public void clear() {
        mCircleRenderer.clear();
        mMarkerRenderer.clear();
        mOverlayRenderer.clear();
        requestRender();
    }

    @Override
    public void removeInfoWindowAdapter() {
        mMarkerRenderer.removeInfoWindowAdapter();
    }

    @Override
    public void removeCircle(Circle circle) {
        mCircleRenderer.removeCircle(circle);
    }

    @Override
    public void removeMarker(Marker marker) {
        mMarkerRenderer.removeMarker(marker);
    }

    @Override
    public void removePolyline(Polyline polyline) {
        mOverlayRenderer.removePolyOverlay(polyline);
    }

    @Override
    public void removePolygon(Polygon polygon) {
        mOverlayRenderer.removePolyOverlay(polygon);
    }

    @Override
    public void removeOnBoundsChangeListener(OnBoundsChangeListener onBoundsChangeListener) {
        mBoundsChangeListeners.remove(onBoundsChangeListener);
    }

    @Override
    public void removeOnDoubleTapListener(OnDoubleTapListener onDoubleTapListener) {
        mDoubleTapListeners.remove(onDoubleTapListener);
    }

    @Override
    public void removeOnFlingListener(OnFlingListener onFlingListener) {
        mFlingListeners.remove(onFlingListener);
    }

    @Override
    public void removeOnInfoWindowTapListener(OnInfoWindowTapListener onInfoWindowTapListener) {
        mMarkerRenderer.removeOnInfoWindowTapListener(onInfoWindowTapListener);
    }

    @Override
    public void removeOnLongPressListener(OnLongPressListener onLongPressListener) {
        mLongPressListeners.remove(onLongPressListener);
    }

    @Override
    public void removeOnMapTouchListener(OnMapTouchListener onMapTouchListener) {
        mTouchListeners.remove(onMapTouchListener);
    }

    @Override
    public void removeOnMarkerDragListener(OnMarkerDragListener onMarkerDragListener) {
        mMarkerRenderer.removeOnMarkerDragListener(onMarkerDragListener);
    }

    @Override
    public void removeOnMarkerTapListener(OnMarkerTapListener onMarkerTapListener) {
        mMarkerRenderer.removeOnMarkerTapListener(onMarkerTapListener);
    }

    @Override
    public void removeOnPanListener(OnPanListener onPanListener) {
        mPanListeners.remove(onPanListener);
    }

    @Override
    public void removeOnPinchListener(OnPinchListener onPinchListener) {
        mPinchListeners.remove(onPinchListener);
    }

    @Override
    public void removeOnSingleTapListener(OnSingleTapListener onSingleTapListener) {
        mSingleTapListeners.remove(onSingleTapListener);
    }

    @Override
    public void removeOnZoomChangeListener(OnZoomChangeListener onZoomChangeListener) {
        mZoomChangeListeners.remove(onZoomChangeListener);
    }

    @Override
    public void setInfoWindowAdapter(InfoWindowAdapter infoWindowAdapter) {
        mMarkerRenderer.setInfoWindowAdapter(infoWindowAdapter);
    }

    private boolean hasChangeListeners() {
        return !mBoundsChangeListeners.isEmpty() || !mZoomChangeListeners.isEmpty();
    }

    private void processDoubleTap(float screenX, float screenY) {
        float scaleOffsetX = screenX - mGLViewportWidth / 2;
        float scaleOffsetY = screenY - mGLViewportHeight / 2;
        mScrollRenderer.onZoomIn(scaleOffsetX, scaleOffsetY);
        ScreenProjection projection = mVolatileProjection;
        Point point = projection.fromScreenLocation(screenX, screenY);
        for (OnDoubleTapListener listener : mDoubleTapListeners) {
            listener.onDoubleTap(point);
        }
    }

    private void processFling(float velocityX, float velocityY) {
        mScrollRenderer.onFling(velocityX, velocityY);
        for (OnFlingListener listener : mFlingListeners) {
            listener.onFling(velocityX, velocityY);
        }
    }

    private void processLongPress(float screenX, float screenY) {
        ScreenProjection projection = mVolatileProjection;
        Point point = projection.fromScreenLocation(screenX, screenY);

        Marker marker = mMarkerRenderer.longPress(projection, new PointF(screenX, screenY));
        if (marker == null) {
            for (OnLongPressListener listener : mLongPressListeners) {
                listener.onLongPress(point);
            }
            requestRender();
        }
    }

    private void processPan(float distanceX, float distanceY) {
        mScrollRenderer.onPan(distanceX, distanceY);
        for (OnPanListener listener : mPanListeners) {
            listener.onPan(distanceX, distanceY);
        }
    }

    private void processPinch(float focusX, float focusY, float focusChangeX, float focusChangeY, float scale) {
        float scaleOffsetX = focusX - mGLViewportWidth / 2;
        float scaleOffsetY = focusY - mGLViewportHeight / 2;

        mScrollRenderer.onPinch(focusChangeX, focusChangeY, scale, scaleOffsetX, scaleOffsetY);
        for (OnPinchListener listener : mPinchListeners) {
            listener.onPinch();
        }
    }

    private void processSingleTap(float screenx, float screeny) {
        ScreenProjection projection = mVolatileProjection;
        PointF screenLocation = new PointF(screenx, screeny);

        boolean handled = mMarkerRenderer.singleTap(projection, screenLocation);
        Point point = projection.fromScreenLocation(screenx, screeny);
        if (!handled) {
            for (OnSingleTapListener listener : mSingleTapListeners) {
                listener.onSingleTap(point);
            }
        } else {
            CameraPosition position
                    = new CameraPosition(point, projection.getMetresPerPixel());
            moveCamera(position, true);
        }
        requestRender();
    }

    private void processTouch(float screenX, float screenY) {
        ScreenProjection projection = mVolatileProjection;
        Point point = projection.fromScreenLocation(screenX, screenY);
        for (OnMapTouchListener listener : mTouchListeners) {
            listener.onMapTouch(point);
        }
    }

    private void processTwoFingerTap() {
        mScrollRenderer.onTwoFingerTap();
    }

    private void setEmulatorConfiguration() {
        if (Utils.EMULATOR_GLES_WORKAROUNDS_ENABLED
                && Build.FINGERPRINT.matches("generic\\/(?:google_)?sdk\\/generic\\:.*")
                && Build.CPU_ABI.matches("armeabi(?:\\-.*)?")) {
            // A bug in the ARM emulator causes it to fail to choose a config, possibly if the
            // backing GL context does not support no alpha channel.
            //
            // 4.2 with Google APIs, ARM:
            //  BOARD == BOOTLOADER == MANUFACTURER == SERIAL == unknown
            //  BRAND == DEVICE == generic
            //  CPU_ABI == armeabi-v7a
            //  CPU_ABI2 == armeabi
            //  DISPLAY == google_sdk-eng 4.2 JB_MR1 526865 test-keys
            //  FINGERPRINT == generic/google_sdk/generic:4.2/JB_MR1/526865:eng/test-keys
            //  HARDWARE == goldfish
            //  HOST == android-mac-sl-10.mtv.corp.google.com
            //  ID == JB_MR1
            //  MODEL == PRODUCT == google_sdk
            //  TAGS == test-keys
            //  TIME == 1352427062000
            //  TYPE == eng
            //  USER == android-build

            Log.w(CLASS_TAG, "Setting an emulator-compatible GL config; " +
                    "this should not happen on devices!");
            setEGLConfigChooser(8, 8, 8, 8, 8, 0);
        }
    }

    private class FrameHandler {

        private final Handler mHandler;
        private final Runnable mMapChangeRunnable = new Runnable() {
            public void run() {
                ScreenProjection projection = getProjection();
                float newZoom = projection.getMetresPerPixel();

                CameraPosition position = new CameraPosition(projection.getCenter(), newZoom);

                // Cache position;
                mPositionManager.storePosition(position);
                // TODO: move the position code

                for(OnBoundsChangeListener listener : mBoundsChangeListeners) {
                    listener.onBoundsChange(projection.getVisibleBounds());
                }

                for(OnZoomChangeListener listener : mZoomChangeListeners) {
                    listener.onZoomChange(newZoom);
                }
            }
        };
        private final int mMinFramePeriodMillis;

        private long mPreviousFrameUptimeMillis;
        private double lastx;
        private double lasty;
        private float lastMPP;

        public FrameHandler(Context context) {
            mHandler = new Handler(context.getMainLooper());
            mMinFramePeriodMillis = (int) (1000 / ((WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate());
        }

        public long getFrameTime() {
            long now = SystemClock.uptimeMillis();
            long timeToSleep = mMinFramePeriodMillis - (now - mPreviousFrameUptimeMillis);
            if (0 < timeToSleep && timeToSleep <= mMinFramePeriodMillis) {
                SystemClock.sleep(timeToSleep);
                now += timeToSleep;
            }

            mPreviousFrameUptimeMillis = now;

            return now;
        }

        public void finishFrame() {
            boolean animating = (mScrollPosition.animatingScroll || mScrollPosition.animatingZoom);

            // Only make a callback if the state of the map has changed.
            if (!animating) {
                if (lastx != mScrollPosition.x || lasty != mScrollPosition.y || lastMPP != mScrollPosition.metresPerPixel) {
                    if(hasChangeListeners()) {
                        // Notify on the main thread
                        mHandler.removeCallbacks(mMapChangeRunnable);
                        mHandler.post(mMapChangeRunnable);
                    }
                    // TODO: put position storing code here!
                    lastx = mScrollPosition.x;
                    lasty = mScrollPosition.y;
                    lastMPP = mScrollPosition.metresPerPixel;
                }
            }
        }

        public void roundToPixelBoundary() {
            // OS-56: A better pixel-aligned-drawing algorithm.
            float originalMPP = mScrollPosition.metresPerPixel;
            Layer layer = mTileRenderer.mapLayerForMPP(originalMPP);
            float originalSizeScreenPx = layer.getTileSizeInMetres() / originalMPP;
            float roundedSizeScreenPx = (float) Math.ceil(originalSizeScreenPx);
            float roundedMPP = layer.getTileSizeInMetres() / roundedSizeScreenPx;
            if (mTileRenderer.mapLayerForMPP(roundedMPP) != layer) {
                // If rounding up would switch layer boundaries, try rounding down.
                roundedSizeScreenPx = (float) Math.floor(originalSizeScreenPx);
                roundedMPP = layer.getTileSizeInMetres() / roundedSizeScreenPx;
                // If that breaks too, we're in trouble.
                if (roundedSizeScreenPx < 1 || mTileRenderer.mapLayerForMPP(roundedMPP) != layer) {
                    assert false : "This shouldn't happen!";
                    return;
                }
            }

            double tileOriginX = Math.floor(mScrollPosition.x / layer.getTileSizeInMetres()) * layer.getTileSizeInMetres();
            double tileOriginY = Math.floor(mScrollPosition.y / layer.getTileSizeInMetres()) * layer.getTileSizeInMetres();

            // OS-57: Fudge the rounding by half a pixel if the screen width is odd.
            double halfPixelX = (mGLViewportWidth % 2 == 0 ? 0 : 0.5);
            double halfPixelY = (mGLViewportHeight % 2 == 0 ? 0 : 0.5);
            double roundedOffsetPxX = Math.rint((mScrollPosition.x - tileOriginX) / roundedMPP - halfPixelX) + halfPixelX;
            double roundedOffsetPxY = Math.rint((mScrollPosition.y - tileOriginY) / roundedMPP - halfPixelY) + halfPixelY;

            mScrollPosition.metresPerPixel = roundedMPP;
            mScrollPosition.x = tileOriginX + roundedOffsetPxX * roundedMPP;
            mScrollPosition.y = tileOriginY + roundedOffsetPxY * roundedMPP;

            // TODO: tchan: Check that it's rounded correctly, to within about 1e-4 of a pixel boundary. Something like this.
            //assert Math.abs(Math.IEEEremainder((float)(tileRect.left-mapCenterX/mapTileSize)*screenTileSize, 1)) < 1.0e-4f;
            //assert Math.abs(Math.IEEEremainder((float)(tileRect.top-mapCenterY/mapTileSize)*screenTileSize, 1)) < 1.0e-4f;
        }
    }

    private class PositionManager {
        // POSITION CACHE
        private static final String POSITION_EASTINGS = "position_eastings";
        private static final String POSITION_NORTHINGS = "position_northings";
        private static final String POSITION_ZOOM = "position_zoom";

        private static final long DEFAULT_EASTINGS = 45000;
        private static final long DEFAULT_NORTHINGS = 45000;
        private static final long DEFAULT_ZOOM = 50000;

        public void setInitialMapPosition() {
            moveCamera(getPosition(), false);
        }

        public CameraPosition getPosition() {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            double eastings = prefs.getFloat(POSITION_EASTINGS, DEFAULT_EASTINGS);
            double northings = prefs.getFloat(POSITION_NORTHINGS, DEFAULT_NORTHINGS);
            float zoom = prefs.getFloat(POSITION_ZOOM, DEFAULT_ZOOM);

            boolean isValidPosition = isValidPosition(eastings, northings, zoom);
            CameraPosition position;
            if (isValidPosition) {
                position = new CameraPosition(new Point(eastings, northings, Point.BNG), zoom);
            } else {
                position = new CameraPosition(new Point(DEFAULT_EASTINGS,
                        DEFAULT_NORTHINGS, Point.BNG), DEFAULT_ZOOM);
            }

            return position;
        }

        public void storePosition(CameraPosition position) {
            if (position == null) {
                return;
            }

            boolean isValidPosition = isValidPosition(position.target.getX(), position.target.getY(),
                    position.zoom);
            if (!isValidPosition) {
                return;
            }

            PreferenceManager.getDefaultSharedPreferences(mContext)
                    .edit()
                    .putFloat(POSITION_EASTINGS, (float) position.target.getX())
                    .putFloat(POSITION_NORTHINGS, (float) position.target.getY())
                    .putFloat(POSITION_ZOOM, position.zoom)
                    .commit();
        }

        private boolean isValid(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }

        private boolean isValidPosition(double easting, double northing, float zoom) {
            return isValid(easting) && isValid(northing) && isValid(zoom);
        }
    }
}