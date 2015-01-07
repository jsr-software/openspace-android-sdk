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

import java.util.List;

import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;

import static android.opengl.GLES20.*;

public class Polygon extends PolyAnnotation {

    public static class Builder {
        private int mFillColor = 0xff000000;
        private int mStrokeColor = 0xff000000;
        private float mStrokeWidth = 10;
        private volatile PolyPoints mPoints;
        private boolean mClosed = true;

        public Polygon build() {
            return new Polygon(this);
        }

        public Builder setFillColor(int fillColor) {
            mFillColor = fillColor;
            return this;
        }

        public Builder setPoints(List<Point> points) {
            mPoints = new PolyPoints(points);
            return this;
        }

        public Builder setStrokeColor(int strokeColor) {
            mStrokeColor = strokeColor;
            return this;
        }

        public Builder setStrokeWidth(float strokeWidth) {
            mStrokeWidth = strokeWidth;
            return this;
        }
    }

    private Polygon(Builder builder) {
        mPoints = builder.mPoints;
        mClosed = builder.mClosed;
        mStrokeWidth = builder.mStrokeWidth;
        mStrokeColor = builder.mStrokeColor;
        mFillColor = builder.mFillColor;
    }

	@Override
    protected void glDrawPoints(int shaderOverlayUniformColor, PolyPoints points,
                                float metresPerPixel, int shaderOverlayAttribVCoord) {
		int fillColor = getFillColor();
		Utils.setUniformPremultipliedColorARGB(shaderOverlayUniformColor, fillColor);
		glDrawArrays(GL_TRIANGLE_FAN, 0, points.mVertexCount);
		
		super.glDrawPoints(shaderOverlayUniformColor, points, metresPerPixel,
                shaderOverlayAttribVCoord);
	}
}
