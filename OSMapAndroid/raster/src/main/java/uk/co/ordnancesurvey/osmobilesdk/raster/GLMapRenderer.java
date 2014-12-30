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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import uk.co.ordnancesurvey.osmobilesdk.gis.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Layer;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.TileServiceDelegate;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLProgramService;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.TileRenderer;

import static android.opengl.GLES20.GL_BACK;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_CULL_FACE;
import static android.opengl.GLES20.GL_ONE;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glCullFace;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glReleaseShaderCompiler;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glViewport;

/**
 * There are three coordinate systems (at least) in use here.
 * <p/>
 * DisplayCoordinates are internal to this SDK, and have the origin at the centre of the screen, with x/y increasing towards the top-right. Units are pixels. Markers are rendered
 * in DisplayCoordinates.
 * <p/>
 * ScreenCoordinates are standard android, i.e. origin top-left, x/y increases towards the bottom-right. We use these as little as possible.
 * <p/>
 * Tiles are rendered in tile coordinates, which have the origin at the bottom left of the grid (not the screen). The units are tiles (i.e. a tile always has dimensions 1x1) and the
 * actual size of a tile is set up by modifying the projection transform.
 */
public final class GLMapRenderer extends GLSurfaceView implements GLSurfaceView.Renderer, TileServiceDelegate, OSMapPrivate {

    private static final String TAG = GLMapRenderer.class.getSimpleName();

    private final Context mContext;
    private final PositionManager mPositionManager = new PositionManager();

    // Renderere
    private final TileRenderer mTileRenderer;
    private final CircleRenderer mCircleRenderer;

    private final GLProgramService mProgramService;

    private MapConfiguration mMapConfiguration;

    public GLMapRenderer(Context context, MapScrollController scrollController, MapConfiguration mapConfiguration) {

        super(context);
        mContext = context;
        if (mapConfiguration == null) {
            throw new IllegalArgumentException("Null Map Configuration");
        }

        mMapConfiguration = mapConfiguration;
        mHandler = new Handler(context.getMainLooper());
        if (BuildConfig.DEBUG) {
            if (GLMapRenderer.class.desiredAssertionStatus()) {
                Log.v(TAG, "Assertions are enabled!");
            } else {
                String s = "Assertions are disabled! To enable them, run \"adb shell setprop debug.assert 1\" and reinstall app";
                Log.w(TAG, s);
                Toast.makeText(context, s, Toast.LENGTH_LONG).show();
                // Sanity check that we have the test the right way around.
                assert false;
            }
        }

        if (BuildConfig.DEBUG) {
            setDebugFlags(DEBUG_CHECK_GL_ERROR);
        }

        setEGLContextClientVersion(2);

        if (Utils.EMULATOR_GLES_WORKAROUNDS_ENABLED && Build.FINGERPRINT.matches("generic\\/(?:google_)?sdk\\/generic\\:.*") && Build.CPU_ABI.matches("armeabi(?:\\-.*)?")) {
            // A bug in the ARM emulator causes it to fail to choose a config, possibly if the backing GL context does not support no alpha channel.
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

            Log.w(TAG, "Setting an emulator-compatible GL config; this should not happen on devices!");
            setEGLConfigChooser(8, 8, 8, 8, 8, 0);
        }

        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mMinFramePeriodMillis = (int) (1000 / ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate());

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = activityManager.getMemoryClass();
        // The "memory class" is the recommended maximum per-app memory usage in MB. Let the GL tile cache use around half of this.
        mGLTileCache = new GLTileCache(memoryClass * (1048576 / 2));
        mGLImageCache = new GLImageCache();

        mScrollController = scrollController;

        mTileRenderer = new TileRenderer(mContext, this, mGLTileCache);
        mCircleRenderer = new CircleRenderer(this);

        mProgramService = new GLProgramService();
    }

    private final GLTileCache mGLTileCache;
    private final GLImageCache mGLImageCache;
    private final MapScrollController mScrollController;
    private final MapScrollController.ScrollPosition mScrollState = new MapScrollController.ScrollPosition();
    // TODO: This is an icky default, but ensures that it's not null.
    // This does not actually need to be volatile, but it encourages users to read it once.
    private volatile ScreenProjection mVolatileProjection = new ScreenProjection(320, 320, mScrollState);
    final float[] mMVPOrthoMatrix = new float[16];
    private int mGLViewportWidth, mGLViewportHeight;
//    ShaderProgram shaderProgram;
//    ShaderOverlayProgram shaderOverlayProgram;
//    ShaderCircleProgram shaderCircleProgram;
//    GLProgram mLastProgram = null;

    // Render thread temporaries. Do not use these outside of the GL thread.
    private final float[] rTempMatrix = new float[32];
    private final PointF rTempPoint = new PointF();
    private final FloatBuffer rTempFloatBuffer = Utils.directFloatBuffer(8);

    private final Handler mHandler;
    private final Runnable mCameraChangeRunnable = new Runnable() {
        public void run() {
            ScreenProjection projection = getProjection();
            CameraPosition position = new CameraPosition(projection.getCenter(), projection.getMetresPerPixel());
            // Cache position;
            mPositionManager.storePosition(position);
            // TODO: move the position code
            // This listener is set on the main thread, so no problem using it like this.
            if (mOnCameraChangeListener != null) {
                mOnCameraChangeListener.onCameraChange(position);
            }
        }
    };

    // Markers
    private final LinkedList<Marker> mMarkers = new LinkedList<>();
    private final ReentrantReadWriteLock mMarkersLock = new ReentrantReadWriteLock();
    private InfoWindowAdapter mInfoWindowAdapter;
    private OnMapClickListener mOnMapClickListener;
    private OnMapLongClickListener mOnMapLongClickListener;
    private OnMarkerClickListener mOnMarkerClickListener;
    private OnMarkerDragListener mOnMarkerDragListener;
    private OnInfoWindowClickListener mOnInfoWindowClickListener;
    private OnCameraChangeListener mOnCameraChangeListener;
    private Marker mExpandedMarker = null;
    // Overlays
    private final LinkedList<PolyOverlay> mPolyOverlays = new LinkedList<>();

    // FPS limiter
    private final int mMinFramePeriodMillis;
    private long mPreviousFrameUptimeMillis;

    // Debug variables.
    private final static boolean DEBUG_FRAME_TIMING = BuildConfig.DEBUG && false;
    private long debugPreviousFrameUptimeMillis;
    private long debugPreviousFrameNanoTime;

    // Avoid issuing too many location callbacks
    private double lastx;
    private double lasty;
    private float lastMPP;

    public void setMapLayers(Layer[] layers) {
        float[] mpps = new float[layers.length];
        int i = 0;
        for (Layer layer : layers) {
            mpps[i++] = layer.getMetresPerPixel();
        }
        mScrollController.setZoomScales(mpps);

        mTileRenderer.setLayers(layers);
    }

    public void setInfoWindowAdapter(InfoWindowAdapter adapter) {
        mInfoWindowAdapter = adapter;
    }

    public void setOnMapClickListener(OnMapClickListener listener) {
        mOnMapClickListener = listener;
    }

    public void setOnMapLongClickListener(OnMapLongClickListener listener) {
        mOnMapLongClickListener = listener;
    }

    public void setOnMarkerClickListener(OnMarkerClickListener listener) {
        mOnMarkerClickListener = listener;
    }

    public void setOnMarkerDragListener(OnMarkerDragListener listener) {
        mOnMarkerDragListener = listener;
    }

    public void setOnInfoWindowClickListener(OnInfoWindowClickListener listener) {
        mOnInfoWindowClickListener = listener;
    }

    // Called on main thread, and used on main thread
    @Override
    public void setOnCameraChangeListener(OnCameraChangeListener listener) {
        mOnCameraChangeListener = listener;
    }

    @Override
    public void moveCamera(CameraPosition camera, boolean animated) {
        mScrollController.zoomToCenterScale(null, camera.target, camera.zoom, animated);
    }


    public void onDestroy() {
        mTileRenderer.shutdown();
    }

    public void onResume() {
        super.onResume();
        mPositionManager.setInitialMapPosition();
        mTileRenderer.init(mMapConfiguration);
    }

    public final void clear() {
        mMarkersLock.writeLock().lock();
        try {
            mMarkers.clear();
            mExpandedMarker = null;
        } finally {
            mMarkersLock.writeLock().unlock();
        }

        synchronized (mPolyOverlays) {
            mPolyOverlays.clear();
        }

        mCircleRenderer.clear();

        requestRender();
    }

    public final Marker addMarker(MarkerOptions markerOptions) {
        Bitmap icon = markerOptions.getIcon().loadBitmap(getContext());
        Marker marker = new Marker(markerOptions, icon, this);
        mMarkersLock.writeLock().lock();
        try {
            mMarkers.add(marker);
        } finally {
            mMarkersLock.writeLock().unlock();
        }
        requestRender();
        return marker;
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
        requestRender();
    }

    @Override
    public final Polyline addPolyline(PolylineOptions polylineOptions) {
        Polyline polyline = new Polyline(polylineOptions, this);
        synchronized (mPolyOverlays) {
            mPolyOverlays.add(polyline);
        }
        requestRender();
        return polyline;
    }

    @Override
    public final Polygon addPolygon(PolygonOptions polygonOptions) {
        Polygon polygon = new Polygon(polygonOptions, this);
        synchronized (mPolyOverlays) {
            mPolyOverlays.add(polygon);
        }
        requestRender();
        return polygon;
    }

    void removePolyOverlay(PolyOverlay polygon) {
        synchronized (mPolyOverlays) {
            mPolyOverlays.remove(polygon);
        }
        requestRender();
    }

    @Override
    public final Circle addCircle(CircleOptions circleOptions) {
        Circle circle = mCircleRenderer.addCircle(circleOptions);
        requestRender();
        return circle;
    }

    void removeCircle(Circle circle) {
        mCircleRenderer.removeCircle(circle);
        requestRender();
    }

    void removeOverlay(ShapeOverlay shapeOverlay) {
        if (shapeOverlay instanceof Circle) {
            removeCircle((Circle) shapeOverlay);
        } else {
            removePolyOverlay((PolyOverlay) shapeOverlay);
        }
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

    private void roundToPixelBoundary() {
        // OS-56: A better pixel-aligned-drawing algorithm.
        float originalMPP = mScrollState.metresPerPixel;
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

        double tileOriginX = Math.floor(mScrollState.x / layer.getTileSizeInMetres()) * layer.getTileSizeInMetres();
        double tileOriginY = Math.floor(mScrollState.y / layer.getTileSizeInMetres()) * layer.getTileSizeInMetres();

        // OS-57: Fudge the rounding by half a pixel if the screen width is odd.
        double halfPixelX = (mGLViewportWidth % 2 == 0 ? 0 : 0.5);
        double halfPixelY = (mGLViewportHeight % 2 == 0 ? 0 : 0.5);
        double roundedOffsetPxX = Math.rint((mScrollState.x - tileOriginX) / roundedMPP - halfPixelX) + halfPixelX;
        double roundedOffsetPxY = Math.rint((mScrollState.y - tileOriginY) / roundedMPP - halfPixelY) + halfPixelY;

        mScrollState.metresPerPixel = roundedMPP;
        mScrollState.x = tileOriginX + roundedOffsetPxX * roundedMPP;
        mScrollState.y = tileOriginY + roundedOffsetPxY * roundedMPP;

        // TODO: tchan: Check that it's rounded correctly, to within about 1e-4 of a pixel boundary. Something like this.
        //assert Math.abs(Math.IEEEremainder((float)(tileRect.left-mapCenterX/mapTileSize)*screenTileSize, 1)) < 1.0e-4f;
        //assert Math.abs(Math.IEEEremainder((float)(tileRect.top-mapCenterY/mapTileSize)*screenTileSize, 1)) < 1.0e-4f;
    }

//    void setProgram(GLProgram program) {
//        if (program == mLastProgram) {
//            return;
//        }
//
//        if (mLastProgram != null) {
//            mLastProgram.stopUsing();
//        }
//
//        program.use();
//        mLastProgram = program;
//    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // Get the timestamp ASAP. Future code might use this to time animations; we want them to be as smooth as possible.
        final long nowUptimeMillis;

        final boolean LIMIT_FRAMERATE = true;
        if (LIMIT_FRAMERATE) {
            // OS-62: This seems to make scrolling smoother.
            long now = SystemClock.uptimeMillis();
            long timeToSleep = mMinFramePeriodMillis - (now - mPreviousFrameUptimeMillis);
            if (0 < timeToSleep && timeToSleep <= mMinFramePeriodMillis) {
                SystemClock.sleep(timeToSleep);
                now += timeToSleep;
            }
            nowUptimeMillis = now;
            mPreviousFrameUptimeMillis = nowUptimeMillis;
        } else {
            nowUptimeMillis = SystemClock.uptimeMillis();
        }

        final long debugDiffUptimeMillis, debugDiffNanoTime;
        if (DEBUG_FRAME_TIMING) {
            // OS-60: Support code. Do this "ASAP" too, so the times are as accurate as possible.
            final long nowNanoTime = System.nanoTime();
            debugDiffUptimeMillis = nowUptimeMillis - debugPreviousFrameUptimeMillis;
            debugDiffNanoTime = nowNanoTime - debugPreviousFrameNanoTime;
            debugPreviousFrameUptimeMillis = nowUptimeMillis;
            debugPreviousFrameNanoTime = nowNanoTime;
        } else {
            debugDiffUptimeMillis = 0;
            debugDiffNanoTime = 0;
        }

        // Update the scroll position too, because it uses SystemClock.uptimeMillis() internally (ideally we'd pass it the timestamp we got above).
        mScrollController.getScrollPosition(mScrollState, true);
        roundToPixelBoundary();
        // And create a new projection.
        ScreenProjection projection = new ScreenProjection(mGLViewportWidth, mGLViewportHeight, mScrollState);
        if (DEBUG_FRAME_TIMING) {
            // OS-60 OS-62: Print inter-frame time (based on time at entry to onDrawFrame(), whether we're "animating" (e.g. flinging), and the distance scrolled in metres.
            ScreenProjection oldProjection = mVolatileProjection;
            Log.v(TAG, debugDiffUptimeMillis + " " + debugDiffNanoTime + " AS=" + mScrollState.animatingScroll + " dx=" + (projection.getCenter().getX() - oldProjection.getCenter().getX()) +
                    " dy=" + (projection.getCenter().getY() - oldProjection.getCenter().getY()));
        }
        mVolatileProjection = projection;

        boolean needRedraw = mTileRenderer.onDrawFrame(mProgramService, projection, nowUptimeMillis, mScrollState, rTempMatrix, mMVPOrthoMatrix);

        float metresPerPixel = projection.getMetresPerPixel();
        boolean animating = (mScrollState.animatingScroll || mScrollState.animatingZoom);

        // Enable alpha-blending
        glEnable(GL_BLEND);

        // Draw overlays
        mProgramService.setActiveProgram(GLProgramService.GLProgramType.OVERLAY);

        synchronized (mPolyOverlays) {
            for (PolyOverlay poly : mPolyOverlays) {
                poly.glDraw(mMVPOrthoMatrix, rTempMatrix, rTempPoint, metresPerPixel, mProgramService.getShaderOverlayProgram());
            }
        }
        Utils.throwIfErrors();

        mCircleRenderer.onDrawFrame(mProgramService);

        mProgramService.setActiveProgram(GLProgramService.GLProgramType.SHADER);

        drawMarkers(projection);

        if (needRedraw) {
            requestRender();
        }

        // Only make a callback if the state of the map has changed.
        if (!animating) {
            if (lastx != mScrollState.x || lasty != mScrollState.y || lastMPP != mScrollState.metresPerPixel) {
                if (mOnCameraChangeListener != null) {
                    // Notify on the main thread
                    mHandler.removeCallbacks(mCameraChangeRunnable);
                    mHandler.post(mCameraChangeRunnable);
                }
                // TODO: put position storing code here!
                lastx = mScrollState.x;
                lasty = mScrollState.y;
                lastMPP = mScrollState.metresPerPixel;
            }
        }
    }

    // Callback from marker when show/hideInfoWindow is called
    void onInfoWindowShown(Marker marker) {
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
        requestRender();
    }

    public boolean singleClick(float screenx, float screeny) {
        boolean handled = false;

        ScreenProjection projection = mVolatileProjection;
        PointF screenLocation = new PointF(screenx, screeny);

        // Check for a click on an info window first.
        if (mExpandedMarker != null) {
            if (mExpandedMarker.isClickOnInfoWindow(screenLocation)) {
                if (mOnInfoWindowClickListener != null) {
                    mOnInfoWindowClickListener.onInfoWindowClick(mExpandedMarker);
                }
            }
        }

        // TODO do we need to handle stacked markers where one marker declines the touch?
        Marker marker = findMarker(projection, screenLocation);
        if (marker != null) {
            handled = false;
            if (mOnMarkerClickListener != null) {
                handled = mOnMarkerClickListener.onMarkerClick(marker);
            }
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

        if (!handled) {
            if (mOnMapClickListener != null) {
                Point gp = projection.fromScreenLocation(screenx, screeny);
                handled = mOnMapClickListener.onMapClick(gp);
            }
            if (!handled) {
                // TODO move camera here
            }
        }
        return handled;
    }

    private static final int MARKER_DRAG_OFFSET = 70;

    /**
     * Return the object handling the long click, if we want to initiate a drag
     */
    public Object longClick(float screenx, float screeny) {
        ScreenProjection projection = mVolatileProjection;
        Point gp = projection.fromScreenLocation(screenx, screeny);
        Point gp2 = projection.fromScreenLocation(screenx, screeny - MARKER_DRAG_OFFSET);

        // Check gp2 as well, because we don't want to lift a marker out of bounds.
        if (!BngUtil.isInBngBounds(gp) || !BngUtil.isInBngBounds(gp2)) {
            return null;
        }

        // TODO do we need to handle stacked markers where one marker declines the touch?
        Marker marker = findDraggableMarker(projection, new PointF(screenx, screeny));
        if (marker != null) {
            if (mOnMarkerDragListener != null) {
                mOnMarkerDragListener.onMarkerDragStart(marker);
            }

            // Set position up a bit, so we can see the marker.
            updateMarkerPosition(marker, screenx, screeny);
            // TODO scroll map up if necessary
            return marker;
        }

        if (mOnMapLongClickListener != null) {
            mOnMapLongClickListener.onMapLongClick(gp);
        }
        return null;
    }

    public void drag(float screenx, float screeny, Object draggable) {
        if (draggable instanceof Marker) {

            Marker marker = (Marker) draggable;
            if (mOnMarkerDragListener != null) {
                mOnMarkerDragListener.onMarkerDrag(marker);
            }

            updateMarkerPosition(marker, screenx, screeny);
        }
    }

    private void updateMarkerPosition(Marker marker, float x, float y) {
        ScreenProjection projection = mVolatileProjection;
        Point unclamped = projection.fromScreenLocation(x, y);
        Point clamped = BngUtil.clampToBngBounds(unclamped);
        marker.setPoint(clamped);
    }

    public void dragEnded(float screenx, float screeny, Object draggable) {
        if (draggable instanceof Marker) {
            Marker marker = (Marker) draggable;
            if (mOnMarkerDragListener != null) {
                mOnMarkerDragListener.onMarkerDragEnd(marker);
            }

            updateMarkerPosition(marker, screenx, screeny);
        }
    }

    private interface MarkerCallable<T> {
        // Return true if iteration should stop.
        abstract boolean run(Marker marker, T params);
    }

    public View getInfoWindow(Marker marker) {
        View view = null;
        View contentView = null;
        if (mInfoWindowAdapter != null) {
            view = mInfoWindowAdapter.getInfoWindow(marker);
            if (view == null) {
                contentView = mInfoWindowAdapter.getInfoContents(marker);
            }
        }
        if (view == null) {
            view = defaultInfoWindow(marker, contentView);
        }

        // OS-80 The view might be null here (if there's nothing to display in the info window)
        if (view != null && (view.getWidth() == 0 || view.getHeight() == 0)) {
            // Force a layout if the width or height is 0. Should we do this all the time?
            layoutInfoWindow(view);
        }
        return view;
    }

    private View defaultInfoWindow(Marker marker, View contentView) {
        String title = marker.getTitle();
        String snippet = marker.getSnippet();
        if (contentView == null && title == null && snippet == null) {
            // Can't show anything
            return null;
        }

        Context context = getContext();

        // use the default info window, with title and snippet
        LinearLayout layout = new LinearLayout(context);
        layout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        layout.setOrientation(LinearLayout.VERTICAL);
        NinePatchDrawable drawable = Images.getInfoBgDrawable(context.getResources());
        viewSetBackgroundCompat(layout, drawable);

        if (contentView != null) {
            layout.addView(contentView);
        } else {
            // Need a background image for the marker.
            if (title != null) {
                TextView text = new TextView(context);
                text.setText(title);
                text.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                text.setGravity(Gravity.CENTER);
                layout.addView(text);
            }

            // Add snippet if present
            if (snippet != null) {
                TextView text = new TextView(context);
                text.setText(snippet);
                text.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                text.setTextColor(0xffbdbdbd);
                text.setGravity(Gravity.CENTER);
                layout.addView(text);
            }
        }

        layoutInfoWindow(layout);
        return layout;
    }

    private void layoutInfoWindow(View v) {
        measureAndLayout(v, 500, 500);
    }

    private void measureAndLayout(View v, int width, int height) {
        // Do an unconstrained layout first, because...
        int unconstrained = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        v.measure(unconstrained, unconstrained);

        int measuredW = v.getMeasuredWidth();
        int measuredH = v.getMeasuredHeight();
        if (measuredW > width || measuredH >= height) {
            // ... If the LinearLayout has children with unspecified LayoutParams,
            // the LinearLayout seems to fill the space available.
            v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
            measuredW = v.getMeasuredWidth();
            measuredH = v.getMeasuredHeight();
        }

        v.layout(0, 0, measuredW, measuredH);
    }

    @SuppressWarnings("deprecation")
    private void viewSetBackgroundCompat(View view, Drawable bg) {
        if (Build.VERSION.SDK_INT >= 16) {
            viewSetBackgroundAPI16(view, bg);
        } else {
            // Deprecated in API level 16, but we need to support 10.
            view.setBackgroundDrawable(bg);
        }
    }

    @TargetApi(16)
    private void viewSetBackgroundAPI16(View view, Drawable bg) {
        view.setBackground(bg);
    }

    private final MarkerCallable<PointF> mDrawMarkerCallable = new MarkerCallable<PointF>() {
        @Override
        public boolean run(Marker marker, PointF tempPoint) {
            marker.glDraw(mMVPOrthoMatrix, rTempMatrix, mGLImageCache, tempPoint, mProgramService.getShaderProgram());
            return false;
        }
    };

    private void drawMarkers(final ScreenProjection projection) {
        // Draw from the bottom up, so that top most marker is fully visible even if overlapped
        iterateVisibleMarkers(true, projection, mDrawMarkerCallable, rTempPoint);
    }

    private Marker findMarker(final ScreenProjection projection, PointF screenLocation, final boolean draggableOnly) {
        final PointF tempPoint = new PointF();
        final RectF tempRect = new RectF();
        MarkerCallable<PointF> callable = new MarkerCallable<PointF>() {
            @Override
            public boolean run(Marker marker, PointF screenLocation) {
                return (!draggableOnly || marker.isDraggable()) && marker.containsPoint(projection, screenLocation, tempPoint, tempRect);
            }
        };
        // Iterate from the top-down, since we're looking to capture a click.
        return iterateVisibleMarkers(false, projection, callable, screenLocation);
    }

    private Marker findMarker(ScreenProjection projection, PointF screenLocation) {
        return findMarker(projection, screenLocation, false);
    }

    private Marker findDraggableMarker(ScreenProjection projection, PointF screenLocation) {
        return findMarker(projection, screenLocation, true);
    }

    private <T> Marker processMarker(Marker marker, BoundingBox boundingBox, MarkerCallable<T> callable, T params) {
        // Check bounds
        if (marker == null) {
            return null;
        }

        Point gp = marker.getPoint();

        // Skip invisible or out of visible area markers
        if (!marker.isVisible() || !boundingBox.contains(gp)) {
            return null;
        }

        if (callable.run(marker, params)) {
            return marker;
        }

        return null;
    }

    private <T> Marker iterateVisibleMarkers(boolean bottomUp, ScreenProjection projection, MarkerCallable<T> callable, T params) {
        Marker ret = null;
        // Look at more markers than are nominally visible, in case their bitmap covers the relevant area.
        // We extend to four times the actual screen area.
        // TODO can we do something more intelligent than this... like remember the maximum bitmap size for markers, plus take
        // account of anchors?
        BoundingBox boundingBox = projection.getExpandedVisibleBounds();

        mMarkersLock.readLock().lock();
        {
            Iterator<Marker> iter;
            if (bottomUp) {
                iter = mMarkers.iterator();
            } else {
                iter = mMarkers.descendingIterator();
                ret = processMarker(mExpandedMarker, boundingBox, callable, params);
            }
            Marker marker = null;

            while (ret == null && iter.hasNext()) {
                marker = iter.next();
                // processMarker returns non-null if iteration should stop.
                ret = processMarker(marker, boundingBox, callable, params);
            }

            if (ret == null && bottomUp) {
                ret = processMarker(mExpandedMarker, boundingBox, callable, params);
            }

        }
        mMarkersLock.readLock().unlock();
        return ret;
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
            Log.v(TAG, "Viewport " + width + "*" + height);
        }

        mGLViewportWidth = width;
        mGLViewportHeight = height;
        glViewport(0, 0, width, height);

        // The nominal order is "near,far", but somehow we need to list them backwards.
        Matrix.orthoM(mMVPOrthoMatrix, 0, 0, width, height, 0, 1, -1);

        mScrollController.setWidthHeight(width, height);
    }


    public ScreenProjection getProjection() {
        // TODO: Is this allowed to return null in the Google Maps v2 API?
        return mVolatileProjection;
    }

    // MAP CONFIGURATION
    @Override
    public void setMapConfiguration(MapConfiguration mapConfiguration) {
        mMapConfiguration = mapConfiguration;
        mTileRenderer.init(mMapConfiguration);
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

    private class CircleRenderer {
        private final LinkedList<Circle> mCircleOverlays = new LinkedList<>();
        private final GLMapRenderer mMapRenderer;

        public CircleRenderer(GLMapRenderer mapRenderer) {
            mMapRenderer = mapRenderer;
        }

        public Circle addCircle(CircleOptions circleOptions) {
            Circle circle = new Circle(circleOptions, mMapRenderer);
            synchronized (mCircleOverlays) {
                mCircleOverlays.add(circle);
            }
            return circle;
        }

        public void clear() {
            synchronized (mCircleOverlays) {
                mCircleOverlays.clear();
            }
        }

        public void onDrawFrame(GLProgramService programService) {
            programService.setActiveProgram(GLProgramService.GLProgramType.CIRCLE);
            ShaderCircleProgram program = programService.getShaderCircleProgram();

            // TODO: Render circles in screen coordinates!
            float[] tempMatrix = rTempMatrix;
            Matrix.translateM(tempMatrix, 0, mMVPOrthoMatrix, 0, mGLViewportWidth / 2.0f, mGLViewportHeight / 2.0f, 0);
            Matrix.scaleM(tempMatrix, 0, 1, -1, 1);
            glUniformMatrix4fv(program.uniformMVP, 1, false, tempMatrix, 0);

            Utils.throwIfErrors();
            synchronized (mCircleOverlays) {
                for (Circle circle : mCircleOverlays) {
                    circle.glDraw(rTempPoint, rTempFloatBuffer, program);
                }
            }
            Utils.throwIfErrors();
        }

        public void removeCircle(Circle circle) {
            synchronized (mCircleOverlays) {
                mCircleOverlays.remove(circle);
            }
        }
    }


}