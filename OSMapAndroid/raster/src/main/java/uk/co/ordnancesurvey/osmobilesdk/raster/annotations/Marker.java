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
package uk.co.ordnancesurvey.osmobilesdk.raster.annotations;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.Matrix;
import android.view.View;

import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.BitmapDescriptor;
import uk.co.ordnancesurvey.osmobilesdk.raster.BitmapDescriptorFactory;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLImageCache;
import uk.co.ordnancesurvey.osmobilesdk.raster.ScreenProjection;
import uk.co.ordnancesurvey.osmobilesdk.raster.ShaderProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLMatrixHandler;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLProgramService;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.MarkerRenderer;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glUniform4f;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glVertexAttrib4f;
import static android.opengl.GLES20.glVertexAttribPointer;

/**
 * An icon placed at a particular point on the map's surface. A marker icon is drawn oriented against
 * the device's screen rather than the map's surface; i.e., it will not necessarily change orientation
 * due to map rotations, tilting, or zooming.
 * A marker has the following properties:
 * <b>Anchor</b>
 * <br>The point on the image that will be placed at the LatLng position of the marker.
 * This defaults to 50% from the left of the image and at the bottom of the image.
 * <p><b>Position</b>
 * <br>The {@link Point} value for the marker's position on the map. You can change this
 * value at any time if you want to move the marker.
 * <p><b>Title</b>
 * <br>A text string that's displayed in an info window when the user taps the marker.
 * You can change this value at any time.
 * <p><b>Snippet</b>
 * <br>Additional text that's displayed below the title. You can change this value at any time.
 * <p><b>Icon</b>
 * <br>A bitmap that's displayed for the marker. If the icon is left unset, a default icon is displayed.
 * You can specify an alternative coloring of the default icon using defaultMarker(float).
 * You can't change the icon once you've created the marker.
 * <p><b>Drag Status</b>
 * <br>If you want to allow the user to drag the marker, set this property to true.
 * You can change this value at any time. The default is false.
 * <p><b>Visibility</b>
 * <br>By default, the marker is visible. To make the marker invisible, set this property to false.
 * You can change this value at any time.
 * <b>Example</b>
 * <pre>
 * <code>
 * OSMap map = ... // get a map.
 * // Add a marker at Scafell Pike
 * Marker marker = map.addMarker(new MarkerOptions()
 * .position(Point.parse("NY2154807223"))
 * .title("Scafell Pike")
 * .snippet("Highest Mountain in England"));
 * </code></pre>
 *
 * <b>Developer Guide</b>
 * <p>For more information, read the Markers developer guide.
 */
public class Marker extends Annotation {

    public static class Builder {

        private final Context mContext;
        private final Point mPoint;

        private float mAnchorU = 0.5f;
        private float mAnchorV = 1.0f;
        private BitmapDescriptor mIconDescriptor = BitmapDescriptorFactory.defaultMarker();
        private String mSnippet = "";
        private String mTitle = "";

        public Builder(Context context, Point point) {
            mContext = context;
            mPoint = point;
        }

        public Marker build() {
            return new Marker(this, mContext);
        }

        /**
         * Specifies the anchor to be at a particular point in the marker image.
         * <p>
         The anchor specifies the point in the icon image that is anchored to the marker's position on the Earth's surface.
         The anchor point is specified in the continuous space [0.0, 1.0] x [0.0, 1.0], where (0, 0) is the top-left corner of the image, and (1, 1) is the bottom-right corner. The anchoring point in a W x H image is the nearest discrete grid point in a (W + 1) x (H + 1) grid, obtained by scaling the then rounding. For example, in a 4 x 2 image, the anchor point (0.7, 0.6) resolves to the grid point at (3, 1).
         <pre><code>
         * *-----+-----+-----+-----*
         * |     |     |     |     |
         * |     |     |     |     |
         * +-----+-----+-----+-----+
         * |     |     |   X |     |   (U, V) = (0.7, 0.6)
         * |     |     |     |     |
         * *-----+-----+-----+-----*
         *
         * *-----+-----+-----+-----*
         * |     |     |     |     |
         * |     |     |     |     |
         * +-----+-----+-----X-----+   (X, Y) = (3, 1)
         * |     |     |     |     |
         * |     |     |     |     |
         * *-----+-----+-----+-----*
         * </code></pre>
         * @param anchorU u-coordinate of the anchor, as a ratio of the image width (in the range [0, 1])
         * @param anchorV v-coordinate of the anchor, as a ratio of the image height (in the range [0, 1])
         * @return the object for which the method was called, with the new anchor set.
         */
        public Builder setIconAnchor(float anchorU, float anchorV){
            mAnchorU = anchorU;
            mAnchorV = anchorV;
            return this;
        }

        public Builder setIcon(BitmapDescriptor iconDescriptor) {
            mIconDescriptor = iconDescriptor;
            return this;
        }

        public Builder setSnippet(String snippet) {
            mSnippet = snippet;
            return this;
        }

        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }
    }



    private final Bitmap mIconBitmap;
    private final float mAnchorU;
    private final float mAnchorV;
    private final float mIconTintR;
    private final float mIconTintG;
    private final float mIconTintB;

    private boolean mIsDraggable = false;
    private Point mPoint;
    private String mSnippet;
    private String mTitle;

    private volatile Bitmap mVolatileInfoBitmap;
    private boolean mInfoWindowHighlighted;

    private MarkerRenderer mMarkerRenderer;

    private Marker(Builder builder, Context context) {
        mAnchorU = builder.mAnchorU;
        mAnchorV = builder.mAnchorV;
        mPoint = builder.mPoint;
        mIconBitmap = builder.mIconDescriptor.loadBitmap(context);
        mIconTintR = builder.mIconDescriptor.mTintR;
        mIconTintG = builder.mIconDescriptor.mTintG;
        mIconTintB = builder.mIconDescriptor.mTintB;
        mSnippet = builder.mSnippet;
        mTitle = builder.mTitle;
    }

    public boolean containsPoint(ScreenProjection projection, PointF testPoint, PointF tempPoint, RectF tempRect) {
        // tempPoint is used to save memory allocation - the alternative is allocating a new object
        // for every call.
        getScreenLocation(projection, tempPoint);

        tempRect.left = tempPoint.x;
        tempRect.top = tempPoint.y;
        tempRect.right = tempPoint.x + mIconBitmap.getWidth();
        tempRect.bottom = tempPoint.y + mIconBitmap.getHeight();

        return tempRect.contains(testPoint.x, testPoint.y);
    }

    public void glDraw(GLProgramService programService, GLMatrixHandler matrixHandler,
                       GLImageCache imageCache, ScreenProjection projection) {
        ShaderProgram shaderProgram = programService.getShaderProgram();

        glUniform4f(shaderProgram.uniformTintColor, mIconTintR, mIconTintG, mIconTintB, 1);

        // Render the marker. For the moment, use the standard marker - and load it every time too!!
        GLImageCache.ImageTexture tex = imageCache.bindTextureForBitmap(mIconBitmap);
        if (tex == null) {
            return;
        }

        // Draw this texture in the correct place
        glVertexAttribPointer(shaderProgram.attribVCoord, 2, GL_FLOAT, false, 0, tex.vertexCoords);

        projection.toScreenLocation(mPoint, matrixHandler.getTempPoint());
        PointF screenLocation = matrixHandler.getTempPoint();
        final float OFFSET = 1 / 3.0f;
        float xPixels = (float) Math.rint(screenLocation.x + OFFSET);
        float yPixels = (float) Math.rint(screenLocation.y + OFFSET);
        float[] tempMatrix = matrixHandler.getTempMatrix();

        Matrix.translateM(tempMatrix, 0, matrixHandler.getMVPOrthoMatrix(), 0, xPixels, yPixels, 0);
        if (mBearing != ZERO_COMPARISON) {
            Matrix.rotateM(tempMatrix, 0, mBearing, 0, 0, 1);
        }

        glUniformMatrix4fv(shaderProgram.uniformMVP, 1, false, tempMatrix, 0);

        int height = mIconBitmap.getHeight();
        int width = mIconBitmap.getWidth();

        // Render the marker, anchored at the correct position.
        xPixels = -width * mAnchorU;
        yPixels = -height * mAnchorV;
        glVertexAttrib4f(shaderProgram.attribVOffset, xPixels, yPixels, 0, 1);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        Utils.throwIfErrors();

        // Draw the info window if necessary
        Bitmap infoBitmap = mVolatileInfoBitmap;
        if (infoBitmap != null) {
            if (mBearing != ZERO_COMPARISON) {
                Matrix.rotateM(tempMatrix, 0, -mBearing, 0, 0, 1);
            }

            glUniformMatrix4fv(shaderProgram.uniformMVP, 1, false, tempMatrix, 0);

            // Draw centered above the marker
            tex = imageCache.bindTextureForBitmap(infoBitmap);
            if (tex == null) {
                return;
            }

            yPixels -= tex.height;
            xPixels -= ((tex.width / 2) - (width * mAnchorU));

            glUniform4f(shaderProgram.uniformTintColor, -1, -1, -1, 1);

            glVertexAttribPointer(shaderProgram.attribVCoord, 2, GL_FLOAT, false, 0, tex.vertexCoords);
            glVertexAttrib4f(shaderProgram.attribVOffset, xPixels, yPixels, 0, 1);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            Utils.throwIfErrors();
        }
    }


    /**
     * Returns the current position of the marker.
     * @return A {@link Point} object specifying the marker's current position
     */
    public Point getPoint() {
        return mPoint;
    }

    /**
     * Gets the snippet of the marker.
     *
     * @return A string containing the marker's snippet.
     */
    public String getSnippet() {
        return mSnippet;
    }

    /**
     * Gets the title of the marker.
     *
     * @return A string containing the marker's title.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Hides the info window if it is shown from this marker.
     * This method has no effect if this marker is not visible.
     */
    public void hideInfoWindow() {
        // Check to see if our info window is shown - if not do nothing
        if (mVolatileInfoBitmap == null || !isVisible()) {
            return;
        }
        mInfoWindowHighlighted = false;
        mVolatileInfoBitmap = null;
        mMarkerRenderer.onInfoWindowShown(null);
    }

    public boolean isClickOnInfoWindow(PointF tapLocation, ScreenProjection projection) {
        PointF temp = new PointF();
        PointF markerLocation = getScreenLocation(projection, temp);


        // Edge effects don't matter; no one will notice a 1 px difference on a click.
        RectF checkRect = new RectF(markerLocation.x + mIconBitmap.getWidth() / 2 - mVolatileInfoBitmap.getWidth() / 2, markerLocation.y - mIconBitmap.getHeight() - 30, 0, 0);
        checkRect.right = checkRect.left + mVolatileInfoBitmap.getWidth();
        checkRect.bottom = checkRect.top + mVolatileInfoBitmap.getHeight();
        boolean ret = checkRect.contains(tapLocation.x, tapLocation.y);
        if (ret) {
            setInfoWindowHighlighted(true);
        }
        return ret;
    }

    /**
     * Gets whether the marker is draggable. When a marker is draggable, it can be moved by the user by long pressing on the marker.
     *
     * @return true if the marker is draggable; otherwise, returns false.
     */
    public boolean isDraggable() {
        return mIsDraggable;
    }

    /**
     * Sets the draggability of the marker. When a marker is draggable, it can be moved by the user by long pressing on the marker.
     * @param draggable true if overlay is draggable, false otherwise
     */
    public void setIsDraggable(boolean draggable) {
        mIsDraggable = draggable;
    }

    /**
     * Sets the {@link uk.co.ordnancesurvey.osmobilesdk.gis.Point} that the marker is anchored on.
     * @param point the new anchor point.
     */
    public void setPoint(Point point) {
        mPoint = point;
        requestRender();
    }

    public void setRenderer(MarkerRenderer markerRenderer) {
        super.setBaseRenderer(markerRenderer);
        mMarkerRenderer = markerRenderer;
    }

    /**
     * Sets the snippet of the marker.
     * @param snippet the snippet text to display in an info window
     */
    public void setSnippet(String snippet) {
        mSnippet = snippet;
        // TODO: Update info bitmap atomically?
    }

    /**
     * Sets the title of the marker.
     * @param title the title text to display in an info window
     */
    public void setTitle(String title) {
        mTitle = title;
        // TODO: Update info bitmap atomically?
    }

    /**
     * Shows the info window of this marker on the map, if this marker {@link #isVisible()}.
     */
    public void showInfoWindow() {
        if (!isVisible()) {
            return;
        }
        // Get a view
        View view = mMarkerRenderer.getInfoWindow(this);

        if (mInfoWindowHighlighted) {
            // This is holo light blue
            view.setBackgroundColor(0xff33b5e5);
        }
        if (view == null) {
            return;
        }

        if (view.getWidth() == 0 || view.getHeight() == 0) {
            assert false : "View width/height should be nonzero";
            return;
        }
        // Convert to a bitmap.
        Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        view.draw(canvas);

        // Save the bitmap, but only after we're done drawing!
        mVolatileInfoBitmap = bmp;

        mMarkerRenderer.onInfoWindowShown(this);
    }

    private PointF getScreenLocation(ScreenProjection projection, PointF screenLocationOut) {
        projection.toScreenLocation(mPoint, screenLocationOut);

        // U and V are in the range 0..1 where 0,0 is the top left. Since we draw the marker from the bottom left,
        // convert V' = 1 - V.
        int height = mIconBitmap.getHeight();
        int width = mIconBitmap.getWidth();

        screenLocationOut.x -= width * mAnchorU;
        screenLocationOut.y -= height * mAnchorV;

        return screenLocationOut;
    }

    private void setInfoWindowHighlighted(boolean highlighted) {
        mInfoWindowHighlighted = highlighted;
        showInfoWindow();
    }
}
