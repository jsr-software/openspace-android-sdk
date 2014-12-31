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
import android.graphics.Rect;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.util.Arrays;

import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLTileCache;
import uk.co.ordnancesurvey.osmobilesdk.raster.MapScrollController;
import uk.co.ordnancesurvey.osmobilesdk.raster.MapTile;
import uk.co.ordnancesurvey.osmobilesdk.raster.ScreenProjection;
import uk.co.ordnancesurvey.osmobilesdk.raster.ShaderProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;
import uk.co.ordnancesurvey.osmobilesdk.raster.app.MapConfiguration;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.Layer;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.LayerCatalog;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.TileService;
import uk.co.ordnancesurvey.osmobilesdk.raster.layers.TileServiceDelegate;
import uk.co.ordnancesurvey.osmobilesdk.utilities.network.NetworkStateMonitor;

import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_TEST;
import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDisable;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUniform4f;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glVertexAttrib4f;
import static android.opengl.GLES20.glVertexAttribPointer;

public class TileRenderer {

    private static final String CLASS_TAG = TileRenderer.class.getSimpleName();
    private static final int ZOOM_FADE_DURATION = 400;
    private static final FloatBuffer VERTEX_BUFFER = Utils.directFloatBuffer(new float[]{
            0, 0,
            1, 0,
            0, -1,
            1, -1,
    });

    private final DirtyArea mDirtyArea = new DirtyArea();
    private final FetchQuota mFetchQuota = new FetchQuota();
    private final GLTileCache mGLTileCache;
    private final MapTile rTempTile = new MapTile();
    private final Rect rTempTileRect = new Rect();
    private final TileService mTileService;

    private Layer[] mLayers;
    private Layer mPreviousLayer;
    private Layer mFadingOutLayer;

    private long mFadingInStartUptimeMillis;

    public TileRenderer(Context context, TileServiceDelegate delegate, GLTileCache tileCache) {
        mTileService = new TileService(context, new NetworkStateMonitor(context), delegate);
        mGLTileCache = tileCache;
    }

    public void init(MapConfiguration mapConfiguration) {
        try {
            mTileService.start(mapConfiguration);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Unable to load offline tile sources in map configuration");
        }
    }

    public boolean onDrawFrame(GLProgramService programService, GLMatrixHandler matrixHandler, ScreenProjection projection, long nowUptimeMillis,
                               MapScrollController.ScrollPosition scrollPosition) {
        //leakGPUMemory();
        // At the start of each frame, mark each tile as off-screen.
        // They are marked on-screen as part of tile drawing.
        mGLTileCache.resetTileVisibility();
        mTileService.resetTileRequests();

        Utils.throwIfErrors();

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        programService.setActiveProgram(GLProgramService.GLProgramType.SHADER);
        ShaderProgram shaderProgram = programService.getShaderProgram();

        Utils.throwIfErrors();

        glActiveTexture(GL_TEXTURE0);
        glUniform1i(shaderProgram.uniformTexture, 0);

        float metresPerPixel = projection.getMetresPerPixel();

        boolean animating = (scrollPosition.animatingScroll || scrollPosition.animatingZoom);
        boolean needRedraw = animating;
        // OS-50: Ideally we'd set this to fadingToLayer during an animated fade+zoom, but this would require resetting it if we decide not to fade.
        // (Not resetting it could mean looping over several million tiles when animating from fully zoomed-out to fully zoomed-in.)
        // This only makes a difference if we fail to render a frame in the second half of the zoom animation.

        Layer currentLayer = mapLayerForMPP(metresPerPixel);
        Layer fadingFromLayer = null;
        Layer fadingToLayer = null;
        float fadeToAlpha = 0;
        if (scrollPosition.animatingZoom) {
            float startMPP = scrollPosition.animationStartMetresPerPixel;
            float finalMPP = scrollPosition.animationFinalMetresPerPixel;
            fadingFromLayer = mapLayerForMPP(startMPP);
            fadingToLayer = mapLayerForMPP(finalMPP);
            if (fadingFromLayer == fadingToLayer) {
                // If we're not actually changing layers, do this to avoid the assert crash.
                fadingFromLayer = null;
                fadingToLayer = null;
                mFadingOutLayer = null;
            } else {
                fadeToAlpha = (float) ((Math.log(metresPerPixel) - Math.log(startMPP)) / (Math.log(finalMPP) - Math.log(startMPP)));

                // OS-50 If the zoom gets interrupted, this will continue the fade roughly as if it was non-animated.
                if (currentLayer != fadingToLayer) {
                    mFadingOutLayer = currentLayer;
                }
                // OS-50: Also set the alpha such that if we subsequently calculate fadeToAlpha we get roughly the same result.
                // TODO: This also affects the end of an animated zoom! Sigh.
                mFadingInStartUptimeMillis = nowUptimeMillis - (long) (fadeToAlpha * ZOOM_FADE_DURATION);
                assert Math.abs(fadeToAlpha - (nowUptimeMillis - mFadingInStartUptimeMillis) / (float) ZOOM_FADE_DURATION) < 1 / 256.0f;
            }
        } else {
            if (mPreviousLayer != currentLayer && mFadingOutLayer != mPreviousLayer) {
                mFadingOutLayer = mPreviousLayer;
                mFadingInStartUptimeMillis = nowUptimeMillis;
            } else if (mFadingOutLayer != null && nowUptimeMillis >= mFadingInStartUptimeMillis + ZOOM_FADE_DURATION) {
                mFadingOutLayer = null;
            }

            if (mFadingOutLayer != null) {
                fadingToLayer = currentLayer;
                fadingFromLayer = mFadingOutLayer;
                fadeToAlpha = (nowUptimeMillis - mFadingInStartUptimeMillis) / (float) ZOOM_FADE_DURATION;
            }
        }
        mPreviousLayer = currentLayer;

        if (fadingToLayer != null && Math.abs(indexForMapLayerOrNegative(fadingFromLayer) - indexForMapLayerOrNegative(fadingToLayer)) != 1) {
            fadingFromLayer = null;
            fadingToLayer = null;
        }

        final boolean fading = (fadingToLayer != null);
        assert fading == (fadingFromLayer != null);
        // If we are fading, use the layer we're fading *from* as the "base layer" for deciding which fallbacks to render.
        // This matches what the (old) iOS maps app does.
        final Layer baseLayer = (fading ? fadingFromLayer : currentLayer);


        // Reset the fetch quota. It doesn't really matter where we do this, as long as we do it before drawLayer.
        mFetchQuota.reset(nowUptimeMillis);

        // We need to enable depth-testing to write to the depth buffer.
        glEnable(GL_DEPTH_TEST);
        // The default depth function is GL_LESS, so we don't actually need to pass in any depths (for now).

        if (fading) {
            mFetchQuota.setNoAsyncFetches();
        }


        float depth = 0.5f;
        float alpha = 1.0f;

        mDirtyArea.reset(projection);
        glVertexAttribPointer(shaderProgram.attribVCoord, 2, GL_FLOAT, false, 0, VERTEX_BUFFER);

        // Don't execute any fetches on a layer that is fading out.
        if (fadingToLayer != null) {
            mFetchQuota.setNoAsyncFetches();
        }
        needRedraw |= drawLayerWithFallbacks(shaderProgram, projection, baseLayer, mFetchQuota, alpha, depth, matrixHandler);
        depth = 0.0f;
        alpha = fadeToAlpha;
        if (!mDirtyArea.didDraw()) {
            Log.v(CLASS_TAG, "Failed to draw any tiles!");
        }
        mDirtyArea.reset(projection);
        drawLayerWithFallbacks(shaderProgram, projection, fadingToLayer, mFetchQuota, fadeToAlpha, depth, matrixHandler);

        glDisable(GL_DEPTH_TEST);

        // Always redraw if we're fading.
        needRedraw |= fading;

        mTileService.resetTileRequests();

        Utils.throwIfErrors();

        return needRedraw;
    }

    public void setLayers(Layer[] layers) {
        mLayers = layers;
    }

    public void shutdown() {
        mTileService.shutdown();
    }

    private int bindTextureForTile(MapTile tile, FetchQuota quota) {
        int textureId = mGLTileCache.bindTextureForTile(tile);
        if (textureId != 0) {
            return textureId;
        }

        // Don't fetch if there's no quota!
        if (quota == null) {
            return 0;
        }

        // Don't fetch if we've exceeded limits.
        if (quota.isExceeded()) {
            return 0;
        }

        Bitmap bmp = mTileService.requestBitmapForTile(tile);
        if (bmp == null) {
            quota.fetchFailure();
        } else {
            quota.fetchSuccess();
            textureId = mGLTileCache.putTextureForTile(tile, bmp);
        }
        return textureId;
    }

    private boolean drawLayer(ShaderProgram shaderProgram, ScreenProjection projection, Layer layer, FetchQuota quota, float alpha, float depth, GLMatrixHandler matrixHandler) {
        if (layer == null) {
            return false;
        }

        float metresPerPixel = projection.getMetresPerPixel();

        BoundingBox visibleBounds = projection.getVisibleBounds();

        float mapTileSize = layer.getTileSizeInMetres();
        float screenTileSize = mapTileSize / metresPerPixel;

        double mapTopLeftX = visibleBounds.getMinX();
        double mapTopLeftY = visibleBounds.getMaxY();

        // Draw only the dirty area.
        Rect tileRect = rTempTileRect;
        tileRect.left = (int) Math.floor(mDirtyArea.minX / mapTileSize);
        tileRect.top = (int) Math.floor(mDirtyArea.minY / mapTileSize);
        tileRect.right = (int) Math.ceil(mDirtyArea.maxX / mapTileSize);
        tileRect.bottom = (int) Math.ceil(mDirtyArea.maxY / mapTileSize);

        mDirtyArea.zero();


        // Set alpha.
        glUniform4f(shaderProgram.uniformTintColor, -1, -1, -1, alpha);

        // Blend if alpha is not 1.
        if (alpha == 1.0f) {
            glDisable(GL_BLEND);
        } else {
            glEnable(GL_BLEND);
        }

        // Set up projection matrix	so we can refer to things in tile coordinates.
        {
            float[] mvpTempMatrix = matrixHandler.getTempMatrix();
            Matrix.scaleM(mvpTempMatrix, 0, matrixHandler.getMVPOrthoMatrix(), 0, screenTileSize, screenTileSize, 1);
            glUniformMatrix4fv(shaderProgram.uniformMVP, 1, false, mvpTempMatrix, 0);
        }

        // Render from the centre, spiralling out.
        MapTile tile = rTempTile;
        tile.set(tileRect.centerX(), tileRect.centerY(), layer);

        int tilesWide = tileRect.width();
        int tilesHigh = tileRect.height();
        int numTiles = Math.max(tilesWide, tilesHigh);
        if ((numTiles & 1) == 0) {
            numTiles++;
        }
        numTiles = numTiles * numTiles;
        int offy = 1;
        int offx = 0;
        if (tilesWide > tilesHigh) {
            offx = 1;
            offy = 0;
        }

        int tilesNeeded = 1;
        int tilesDrawn = 0;
        int line = 0;

        boolean needRedraw = false;
        for (int i = 0; i < numTiles; i++) {
            // Is the tile actually visible?
            if (tileRect.contains(tile.x, tile.y)) {
                if (bindTextureForTile(tile, quota) != 0) {
                    // Draw this texture in the correct place. The offset is expressed in tiles (and can be a fraction of a tile).
                    // tchan: We cast to float at the end to avoid losing too much precision.
                    glVertexAttrib4f(shaderProgram.attribVOffset, (float) (tile.x - mapTopLeftX / mapTileSize), -(float) (tile.y - mapTopLeftY / mapTileSize), depth, 1);
                    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                    // Note that we drew something
                    mDirtyArea.drewRect();
                } else {
                    // Failed to draw this bit.
                    mDirtyArea.addDirtyRect(mapTileSize, tile);

                    if (quota == null || quota.isExceeded()) {
                        needRedraw = true;
                        // Still continue because we can draw tiles from the GL cache
                    }
                }
            }
            // Draw in a spiral manner.
            tile.y += offy;
            tile.x += offx;
            tilesDrawn++;
            if (tilesDrawn == tilesNeeded) {
                // Gone sufficient tiles in this direction.
                tilesDrawn = 0;
                int tmp = offx;
                offx = offy;
                offy = -tmp;
                line++;
                // Every 2nd line, increase the line length
                if (line == 2) {
                    line = 0;
                    tilesNeeded++;
                }
            }
        }
        return needRedraw;
    }

    public Layer mapLayerForMPP(float metresPerPixel) {
        Layer bestLayer = null;
        float bestScore = Float.POSITIVE_INFINITY;
        // Precalculate log(mpp). This costs an extra log() but means we don't have to do float division (which might overflow/underflow).
        float logMPP = (float) Math.log(metresPerPixel);
        for (Layer layer : mLayers) {
            float score = Math.abs((float) Math.log(layer.getMetresPerPixel()) - logMPP);
            if (score < bestScore) {
                bestScore = score;
                bestLayer = layer;
            }
        }
        return bestLayer;
    }


    private boolean drawLayerWithFallbacks(ShaderProgram shaderProgram, ScreenProjection projection, Layer layer, FetchQuota quota, float alpha, float depth, GLMatrixHandler matrixHandler) {
        if (layer == null) {
            return false;
        }

        int baseLayerIndex = indexForMapLayerOrNegative(layer);

        boolean needsRedraw = drawLayer(shaderProgram, projection, layer, quota, alpha, depth, matrixHandler);

        Layer fallbackLayer = null;
        // Fallback in preference to +1, -1, -2, -3.
        for (int i = 1; !mDirtyArea.isEmpty() && i >= -3; i--) {
            // If we are rendering with alpha != 1.0, then only draw one layer at most, to avoid overlapping transparency.
            if (alpha < 1.0 && mDirtyArea.didDraw()) {
                break;
            }

            // Skip over 0 difference, as that's the same as the desired layer.
            if (i == 0) {
                i = i - 1;
            }
            depth += 0.1f;


            fallbackLayer = mapLayerForIndexOrNull(baseLayerIndex + i);
            needsRedraw |= drawLayer(shaderProgram, projection, fallbackLayer, quota, alpha, depth, matrixHandler);
        }

        return needsRedraw;
    }

    private int indexForMapLayerOrNegative(Layer layer) {
        assert layer != null;
        int index = Arrays.binarySearch(mLayers, layer, LayerCatalog.getReverseComparator());
        if (index < 0) {
            assert false : "This might happen if mLayers is changed in another thread. If this happens frequently enough when debugging, onDrawFrame() should be changed to only read mLayers once.";
            return Integer.MIN_VALUE / 2;
        }
        return index;
    }

    private Layer mapLayerForIndexOrNull(int index) {
        Layer[] layers = mLayers;
        if (0 <= index && index < layers.length) {
            return layers[index];
        }
        return null;
    }

    private static class FetchQuota {
        // Always perform at least 4 async fetches or 1 sync fetch, even after the render time limit is exceeded.
        public int remainingAsyncFetches;
        public int remainingSyncFetches;
        private long limitUptimeMillis;
        private long hardLimitUptimeMillis;
        private boolean noAsyncFetches;

        public void reset(long now) {
            // Allow 10mS per frame for loading tiles to prevent scroll judder. We could similarly throttle annotations....
            remainingAsyncFetches = 4;
            remainingSyncFetches = 1;
            limitUptimeMillis = now + 10;
            hardLimitUptimeMillis = now + 200;
            noAsyncFetches = false;
        }

        public void setNoAsyncFetches() {
            noAsyncFetches = true;
        }

        public boolean canAsyncFetch() {
            return !noAsyncFetches;
        }

        public void fetchSuccess() {
            remainingSyncFetches--;
        }

        public void fetchFailure() {
            if (!noAsyncFetches) {
                remainingAsyncFetches--;
            }
        }

        public boolean isExceeded() {
            long t = SystemClock.uptimeMillis();
            if (t > limitUptimeMillis) {
                if (remainingSyncFetches <= 0) {
                    return true;
                }
                if (t > hardLimitUptimeMillis) {
                    return true;
                }
            }
            return false;
        }
    }

    private class DirtyArea {

        private double minX, minY, maxX, maxY;

        private boolean mDidDraw;

        boolean isEmpty() {
            return Double.isInfinite(minX);
        }

        void zero() {
            minX = Double.POSITIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
        }

        void drewRect() {
            mDidDraw = true;
        }

        boolean didDraw() {
            return mDidDraw;
        }

        void addDirtyRect(float tilesize, MapTile tile) {
            minX = Math.min(minX, tile.x * tilesize);
            minY = Math.min(minY, tile.y * tilesize);
            maxX = Math.max(maxX, tile.x * tilesize + tilesize);
            maxY = Math.max(maxY, tile.y * tilesize + tilesize);
        }


        void reset(ScreenProjection projection) {
            BoundingBox visible = projection.getVisibleBounds();
            // Set to full visible area.
            maxX = visible.getMaxX();
            minX = visible.getMinX();
            maxY = visible.getMaxY();
            minY = visible.getMinY();
            mDidDraw = false;
        }
    }
}
