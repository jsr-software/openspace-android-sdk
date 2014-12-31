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

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import uk.co.ordnancesurvey.osmobilesdk.gis.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Layer;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.TileServiceDelegate;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.CircleRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLMatrixHandler;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLProgramService;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.MarkerRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.OverlayRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.RendererListener;
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

    private static final String CLASS_TAG = GLMapRenderer.class.getSimpleName();
    private static final int MARKER_DRAG_OFFSET = 70;

    private final Context mContext;
    private final PositionManager mPositionManager = new PositionManager();

    // Renderers and Helpers
    private final CircleRenderer mCircleRenderer;
    private final MarkerRenderer mMarkerRenderer;
    private final OverlayRenderer mOverlayRenderer;
    private final TileRenderer mTileRenderer;

    private final GLProgramService mProgramService;
    private final GLMatrixHandler mGLMatrixHandler;

    private final MarkerRenderer.MarkerRendererListener mMarkerRendererListener = new MarkerRenderer.MarkerRendererListener() {
        @Override
        public void onInfoWindowClick(Marker marker) {
            if (mOnInfoWindowClickListener != null) {
                mOnInfoWindowClickListener.onInfoWindowClick(marker);
            }
        }

        @Override
        public boolean onMarkerClick(Marker marker) {
            if (mOnMarkerClickListener != null) {
                return mOnMarkerClickListener.onMarkerClick(marker);
            }
            return false;
        }

        @Override
        public void onMarkerDrag(Marker marker) {
            if (mOnMarkerDragListener != null) {
                mOnMarkerDragListener.onMarkerDrag(marker);
            }
        }

        @Override
        public void onMarkerDragEnd(Marker marker) {
            if (mOnMarkerDragListener != null) {
                mOnMarkerDragListener.onMarkerDragEnd(marker);
            }
        }

        @Override
        public void onMarkerDragStart(Marker marker) {
            if (mOnMarkerDragListener != null) {
                mOnMarkerDragListener.onMarkerDragStart(marker);
            }
        }

        @Override
        public void onRenderRequested() {
            requestRender();
        }
    };
    private final RendererListener mRendererListener = new RendererListener() {
        @Override
        public void onRenderRequested() {
            requestRender();
        }
    };

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
                Log.v(CLASS_TAG, "Assertions are enabled!");
            } else {
                String s = "Assertions are disabled! To enable them, run \"adb shell setprop debug.assert 1\" and reinstall app";
                Log.w(CLASS_TAG, s);
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

            Log.w(CLASS_TAG, "Setting an emulator-compatible GL config; this should not happen on devices!");
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

        mCircleRenderer = new CircleRenderer(this, mRendererListener);
        mMarkerRenderer = new MarkerRenderer(mContext, this, mMarkerRendererListener);
        mOverlayRenderer = new OverlayRenderer(this, mRendererListener);
        mTileRenderer = new TileRenderer(mContext, this, mGLTileCache);

        mProgramService = new GLProgramService();
        mGLMatrixHandler = new GLMatrixHandler();

//        mGestureDetector = new MapGestureDetector(context, mGestureListener);
    }

    private final GLTileCache mGLTileCache;
    private final GLImageCache mGLImageCache;
    private final MapScrollController mScrollController;
    private final MapScrollController.ScrollPosition mScrollState = new MapScrollController.ScrollPosition();
    // TODO: This is an icky default, but ensures that it's not null.
    // This does not actually need to be volatile, but it encourages users to read it once.
    private volatile ScreenProjection mVolatileProjection = new ScreenProjection(320, 320, mScrollState);
    private int mGLViewportWidth, mGLViewportHeight;

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

    private InfoWindowAdapter mInfoWindowAdapter;
    private OnMarkerClickListener mOnMarkerClickListener;
    private OnMarkerDragListener mOnMarkerDragListener;
    private OnInfoWindowClickListener mOnInfoWindowClickListener;
    private OnCameraChangeListener mOnCameraChangeListener;

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

    @Override
    public void onResume() {
        super.onResume();
        mPositionManager.setInitialMapPosition();
        mTileRenderer.init(mMapConfiguration);
    }

    @Override
    public void clear() {
        mCircleRenderer.clear();
        mMarkerRenderer.clear();
        mOverlayRenderer.clear();
        requestRender();
    }

    @Override
    public Marker addMarker(MarkerOptions markerOptions) {
        return mMarkerRenderer.addMarker(markerOptions);
    }

    @Override
    public void removeMarker(Marker marker) {
        mMarkerRenderer.removeMarker(marker);
    }

    @Override
    public final Polyline addPolyline(PolylineOptions polylineOptions) {
        return mOverlayRenderer.addPolyline(polylineOptions);
    }

    @Override
    public final Polygon addPolygon(PolygonOptions polygonOptions) {
        return mOverlayRenderer.addPolygon(polygonOptions);
    }

    @Override
    public void removePolyOverlay(PolyOverlay polygon) {
        mOverlayRenderer.removePolyOverlay(polygon);
    }

    @Override
    public final Circle addCircle(CircleOptions circleOptions) {
        return mCircleRenderer.addCircle(circleOptions);
    }

    @Override
    public void removeCircle(Circle circle) {
        mCircleRenderer.removeCircle(circle);
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
            Log.v(CLASS_TAG, debugDiffUptimeMillis + " " + debugDiffNanoTime + " AS=" + mScrollState.animatingScroll + " dx=" + (projection.getCenter().getX() - oldProjection.getCenter().getX()) +
                    " dy=" + (projection.getCenter().getY() - oldProjection.getCenter().getY()));
        }
        mVolatileProjection = projection;
        float metresPerPixel = projection.getMetresPerPixel();
        boolean animating = (mScrollState.animatingScroll || mScrollState.animatingZoom);

        boolean needRedraw = mTileRenderer.onDrawFrame(mProgramService, mGLMatrixHandler, projection, nowUptimeMillis, mScrollState);
        mOverlayRenderer.onDrawFrame(mProgramService, mGLMatrixHandler, metresPerPixel);
        mCircleRenderer.onDrawFrame(mProgramService, mGLMatrixHandler, mGLViewportWidth, mGLViewportHeight);
        mMarkerRenderer.onDrawFrame(mProgramService, mGLMatrixHandler, projection, mGLImageCache);

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

    public void drag(float screenx, float screeny, Object draggable) {
        if (draggable instanceof Marker) {
            mMarkerRenderer.onDrag(mVolatileProjection, screenx, screeny, (Marker) draggable);
        }
    }

    public void dragEnded(float screenx, float screeny, Object draggable) {
        if (draggable instanceof Marker) {
            mMarkerRenderer.onDragEnded(mVolatileProjection, screenx, screeny, (Marker) draggable);
        }
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


    /**
     * NEW INTERFACE
     */
    private final List<OnLongPressListener> mLongPressListeners = new ArrayList<>();
    private final List<OnSingleTapListener> mSingleTapListeners = new ArrayList<>();
    private final List<OnMapTouchListener> mTouchListeners = new ArrayList<>();

    @Override
    public void addOnLongPressListener(OnLongPressListener onLongPressListener) {
        mLongPressListeners.add(onLongPressListener);
    }

    @Override
    public void addOnMapTouchListener(OnMapTouchListener onMapTouchListener) {
        mTouchListeners.add(onMapTouchListener);
    }

    @Override
    public void addOnSingleTapListener(OnSingleTapListener onSingleTapListener) {
        mSingleTapListeners.add(onSingleTapListener);
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
    public void removeOnSingleTapListener(OnSingleTapListener onSingleTapListener) {
        mSingleTapListeners.remove(onSingleTapListener);
    }


    // TODO: make the below interface private


    @Override
    public void processLongPress(float screenX, float screenY) {
        ScreenProjection projection = mVolatileProjection;
        Point point = projection.fromScreenLocation(screenX, screenY);
        Point offSetPoint = projection.fromScreenLocation(screenX, screenY - MARKER_DRAG_OFFSET);

        // Check offSetPoint as well, because we don't want to lift a marker out of bounds.
        if (!BngUtil.isInBngBounds(point) || !BngUtil.isInBngBounds(offSetPoint)) {
            //mDragObject = null;
        }

        Marker marker = mMarkerRenderer.longPress(projection, new PointF(screenX, screenY));
        if (marker != null) {
            //mDragObject = marker;
            // TODO: return drag object here?
            return;
        }

        for(OnLongPressListener listener : mLongPressListeners) {
            listener.onLongPress(point);
        }

        requestRender();
    }

    @Override
    public void processSingleTap(float screenx, float screeny) {
        ScreenProjection projection = mVolatileProjection;
        PointF screenLocation = new PointF(screenx, screeny);

        boolean handled = mMarkerRenderer.singleTap(projection, screenLocation);

        if (!handled) {
            Point point = projection.fromScreenLocation(screenx, screeny);
            for(OnSingleTapListener listener : mSingleTapListeners) {
                listener.onSingleTap(point);
            }
        }

        requestRender();
    }

    @Override
    public void processTouch(float screenX, float screenY) {
        ScreenProjection projection = mVolatileProjection;
        Point point = projection.fromScreenLocation(screenX, screenY);
        for(OnMapTouchListener listener : mTouchListeners) {
            listener.onMapTouch(point);
        }
    }
}