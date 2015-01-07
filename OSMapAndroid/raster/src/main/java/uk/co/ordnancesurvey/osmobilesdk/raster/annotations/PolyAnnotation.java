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

import android.opengl.Matrix;
import android.util.Log;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.ScreenProjection;
import uk.co.ordnancesurvey.osmobilesdk.raster.ShaderOverlayProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.GLMatrixHandler;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_LINE_STRIP;
import static android.opengl.GLES20.GL_TRIANGLE_STRIP;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glLineWidth;
import static android.opengl.GLES20.glUniformMatrix4fv;
import static android.opengl.GLES20.glVertexAttribPointer;

public abstract class PolyAnnotation extends ShapeAnnotation {

    protected volatile PolyPoints mPoints;
    protected boolean mClosed;

    public final void glDraw(ScreenProjection projection, GLMatrixHandler matrixHandler,
                             float metresPerPixel, ShaderOverlayProgram program) {
        if (mBaseRenderer == null) {
            return;
        }

        // Read mPoints once; it could change in another thread!
        PolyPoints points = getPolyPoints();

        glSetMatrix(program.uniformMVP, matrixHandler.getMVPOrthoMatrix(),
                matrixHandler.getTempMatrix(), projection, points, metresPerPixel);


        // Set up the line coordinates.
        glVertexAttribPointer(program.attribVCoord, 2, GL_FLOAT, false, 0, points.mVertexBuffer);
        glDrawPoints(program.uniformColor, points, metresPerPixel, program.attribVCoord);
    }

    public List<Point> getPoints() {
        return mPoints.getPoints();
    }

    PolyPoints getPolyPoints() {
        return mPoints;
    }

    public void setPoints(List<Point> points) {
        mPoints = new PolyPoints(points);
        calculatePolygonForLine(points);
        requestRender();
    }

    private void calculatePolygonForLine(List<Point> points) {
        PolyPoints polyPoints = getPolyPoints();
        int numPoints = points.size();
        int numVertices = numPoints * 11;
        if (mClosed) {
            numVertices += 11;
        }

        // Allow space for each normal (1 per point) and each vertex we need to generate later
        FloatBuffer polyData = Utils.directFloatBuffer((numVertices + numPoints) * 2);

        // Pre-compute the normals for each line segment
        for (int i = 0; i < numPoints; i++) {
            Point pt1 = points.get(i);
            // These initializers are just to avoid compiler warnings about possible failure
            // to initialize
            double ax = 0;
            double bx = 0;
            double ay = 0;
            double by = 0;
            boolean setup_a = false;
            boolean setup_b = false;
            // Incoming line
            if (mClosed || i > 0) {
                int j = (i + numPoints - 1) % numPoints;
                Point pt0 = points.get(j);

                ax = pt1.getX() - pt0.getX();
                ay = pt1.getY() - pt0.getY();

                double maga = Math.sqrt(ax * ax + ay * ay);
                // Normalize a
                ax = ax / maga;
                ay = ay / maga;
                setup_a = true;
            }

            // Outgoing line
            if (mClosed || i < (numPoints - 1)) {
                int k = (i + 1) % numPoints;
                Point pt2 = points.get(k);
                bx = pt2.getX() - pt1.getX();
                by = pt2.getY() - pt1.getY();
                // Normalize b.
                double magb = Math.sqrt(bx * bx + by * by);
                bx = bx / magb;
                by = by / magb;
                setup_b = true;
            }
            double cx;
            double cy;

            if (setup_a && !setup_b) {
                // Last point. Use the incoming normal
                cx = ay;
                cy = -ax;
            } else {
                // Use the normal for the outgoing line
                cx = by;
                cy = -bx;

            }

            Log.v("Corner", "Set " + cx + "," + cy);

            polyData.put((float) cx);
            polyData.put((float) cy);
        }
        polyPoints.mAdditionalData = polyData;
    }

    /* Default implementation renders points as a line, possibly a wide line */
    protected void glDrawPoints(int shaderOverlayUniformColor, PolyPoints points,
                                float metresPerPixel, int shaderOverlayAttribVCoord) {
        // Calculate triangles on the fly. We have a normal vector, and a corner vector.
        // Both are normalized.
        // First thing to do is work out what lineWidth is in metres.

        float width = getStrokeWidth();
        if (width == 1) {
            glDrawArrays(GL_LINE_STRIP, 0, points.mVertexCount);
            return;
        }

        FloatBuffer polyData = points.mAdditionalData;


        int numPoints = points.mVertexCount;

        // Offset to triangles.
        int toff = numPoints * 2;
        float lineWidthM = getStrokeWidth() / 2;

        polyData.rewind();
        // Write an additional pair of points for a closed line.
        int writePoints = numPoints;
        if (mClosed) {
            writePoints++;
        }
        int vertexStep = 22;
        for (int i = 0; (i + 1) < writePoints; i++) {
            // Get point A (index i)
            // Not good - we need to synchronize the read of points with the read of polydata.
            float ax = points.mVertexBuffer.get((i % numPoints) * 2);
            float ay = points.mVertexBuffer.get((i % numPoints) * 2 + 1);

            // Get Point B (index i + 1)
            float bx = points.mVertexBuffer.get(((i + 1) % numPoints) * 2);
            float by = points.mVertexBuffer.get(((i + 1) % numPoints) * 2 + 1);

            // Normal to vector A->B
            float nabx = polyData.get((i % numPoints) * 2);
            float naby = polyData.get((i % numPoints) * 2 + 1);

            // Normal to vector B->C (where C is index i + 2)
            float nbcx = polyData.get(((i + 1) % numPoints) * 2);
            float nbcy = polyData.get(((i + 1) % numPoints) * 2 + 1);

            // Compute the normalised B->C vector by going CCW PI/2 from its normal
            // Used in dot product to determine which way we are turning at this corner
            float bcx = -nbcy;
            float bcy = nbcx;

            // Compute the vertices for the end cap "fill" triangle for this corner
            // This depends on whether we are turning CCW or CW
            float c1x, c1y, c2x, c2y, c3x, c3y;
            if (bcx * nabx + bcy * naby < 0) {
                // ab -> bc is turning CCW
                c1x = bx;
                c1y = by;
                c2x = (bx + nabx * lineWidthM);
                c2y = (by + naby * lineWidthM);
                c3x = (bx + nbcx * lineWidthM);
                c3y = (by + nbcy * lineWidthM);
            } else {
                // ab -> bc is turning CW
                c1x = bx;
                c1y = by;
                c2x = (bx - nabx * lineWidthM);
                c2y = (by - naby * lineWidthM);
                c3x = (bx - nbcx * lineWidthM);
                c3y = (by - nbcy * lineWidthM);
            }


//			Log.v("Render", "Original " + ax + "," + ay);
//			Log.v("Render", "Normal " + nx + "," + ny);

            // The line segment itself
            float p1x = (ax + nabx * lineWidthM);
            float p1y = (ay + naby * lineWidthM);
            float p2x = (ax - nabx * lineWidthM);
            float p2y = (ay - naby * lineWidthM);
            float p3x = (bx + nabx * lineWidthM);
            float p3y = (by + naby * lineWidthM);
            float p4x = (bx - nabx * lineWidthM);
            float p4y = (by - naby * lineWidthM);

            // The corner filling triangle two null triangle groups
            float p5x = p4x; // Repeat p4 to terminate that triangle
            float p5y = p4y;
            float p6x = c1x; // Repeat c1 twice to prevent an incipient triangle
            float p6y = c1y;
            float p7x = c1x;
            float p7y = c1y;
            float p8x = c2x;
            float p8y = c2y;
            float p9x = c3x;
            float p9y = c3y;
            float p10x = c3x; // Repeat c3 to terminate triangle
            float p10y = c3y;
            float p11x = (bx + nbcx * lineWidthM); // Next triangle will start with this, so pre-fill it
            float p11y = (by + nbcy * lineWidthM);

//			Log.v("Render", "Point " + p1x + "," + p1y);
//			Log.v("Render", "Point " + p2x + "," + p2y);

            int offset = toff + i * vertexStep;
            polyData.put(offset, p1x);
            polyData.put(offset + 1, p1y);
            polyData.put(offset + 2, p2x);
            polyData.put(offset + 3, p2y);
            polyData.put(offset + 4, p3x);
            polyData.put(offset + 5, p3y);
            polyData.put(offset + 6, p4x);
            polyData.put(offset + 7, p4y);
            polyData.put(offset + 8, p5x);
            polyData.put(offset + 9, p5y);
            polyData.put(offset + 10, p6x);
            polyData.put(offset + 11, p6y);
            polyData.put(offset + 12, p7x);
            polyData.put(offset + 13, p7y);
            polyData.put(offset + 14, p8x);
            polyData.put(offset + 15, p8y);
            polyData.put(offset + 16, p9x);
            polyData.put(offset + 17, p9y);
            polyData.put(offset + 18, p10x);
            polyData.put(offset + 19, p10y);
            polyData.put(offset + 20, p11x);
            polyData.put(offset + 21, p11y);

        }

        float strokeWidth = getStrokeWidth();
        glLineWidth(strokeWidth);

        int strokeColor = getStrokeColor();
        Utils.setUniformPremultipliedColorARGB(shaderOverlayUniformColor, strokeColor);

        polyData.position(toff);
        glVertexAttribPointer(shaderOverlayAttribVCoord, 2, GL_FLOAT, false, 0, polyData.slice());

        glDrawArrays(GL_TRIANGLE_STRIP, 0, (writePoints - 1) * vertexStep / 2);
    }

    void glSetMatrix(int shaderOverlayUniformMVP, float[] orthoMatrix, float[] mvpTempMatrix,
                     ScreenProjection projection, PolyPoints points, float metresPerPixel) {
        BoundingBox visibleBounds = projection.getVisibleBounds();
        double topLeftX = visibleBounds.getMinX();
        double topLeftY = visibleBounds.getMaxY();

        float tx = (float) (points.mVertexCentre.getX() - topLeftX);
        float ty = (float) (points.mVertexCentre.getY() - topLeftY);

        // Convert from metres to pixels.
        // Translate by the appropriate number of metres
        Matrix.scaleM(mvpTempMatrix, 0, orthoMatrix, 0, 1 / metresPerPixel, -1 / metresPerPixel, 1);
        Matrix.translateM(mvpTempMatrix, 0, tx, ty, 0);
        Utils.throwIfErrors();

        if (mBearing != ZERO_COMPARISON) {
            // mRotation is clockwise, so change the sign because rotateM is counter clockwise.
            Matrix.rotateM(mvpTempMatrix, 0, -mBearing, 0, 0, 1);
        }

        glUniformMatrix4fv(shaderOverlayUniformMVP, 1, false, mvpTempMatrix, 0);
        Utils.throwIfErrors();
    }

    protected final static class PolyPoints {
        private final Point[] mArray;
        public final int mVertexCount;
        public final Point mVertexCentre;
        public final FloatBuffer mVertexBuffer;
        public FloatBuffer mAdditionalData;

        public PolyPoints(List<Point> points) {
            mArray = points.toArray(new Point[0]);
            mVertexCount = mArray.length;
            mVertexCentre = getMidpoint(mArray);
            mVertexBuffer = getVertexBuffer(mArray, mVertexCentre, 1);
        }

        public List<Point> getPoints() {
            return Collections.unmodifiableList(Arrays.asList(mArray));
        }

        private static Point getMidpoint(Point[] points) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;

            for (Point p : points) {
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
            }
            return new Point((minX + maxX) / 2, (minY + maxY) / 2, Point.BNG);
        }

        private static FloatBuffer getVertexBuffer(Point[] points, Point center, float scale) {
            // Set up line coordinates.
            FloatBuffer vertexBuffer = Utils.directFloatBuffer(points.length * 2);
            double centerX = center.getX();
            double centerY = center.getY();
            for (Point gp : points) {
                float scaledX = (float) (gp.getX() - centerX) / scale;
                float scaledY = (float) (gp.getY() - centerY) / scale;
                vertexBuffer.put(scaledX);
                vertexBuffer.put(scaledY);
            }
            // Reset the position; this is where glVertexAttribPointer() starts from!
            vertexBuffer.position(0);

            return vertexBuffer;
        }
    }
}