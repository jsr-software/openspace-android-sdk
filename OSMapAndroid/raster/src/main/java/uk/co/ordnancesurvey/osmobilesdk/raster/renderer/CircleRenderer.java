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

import android.opengl.Matrix;

import java.util.LinkedList;

import uk.co.ordnancesurvey.osmobilesdk.raster.Circle;
import uk.co.ordnancesurvey.osmobilesdk.raster.CircleOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.GLMapRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.ShaderCircleProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;

import static android.opengl.GLES20.glUniformMatrix4fv;

public class CircleRenderer extends BaseRenderer {

    private final LinkedList<Circle> mCircleOverlays = new LinkedList<>();

    public CircleRenderer(GLMapRenderer mapRenderer, RendererListener rendererListener) {
        super(mapRenderer, rendererListener);
    }

    public Circle addCircle(CircleOptions circleOptions) {
        Circle circle = new Circle(circleOptions, mMapRenderer);
        synchronized (mCircleOverlays) {
            mCircleOverlays.add(circle);
        }
        emitRenderRequest();
        return circle;
    }

    public void clear() {
        synchronized (mCircleOverlays) {
            mCircleOverlays.clear();
        }
    }

    public void onDrawFrame(GLProgramService programService, GLMatrixHandler matrixHandler, int viewportWidth, int viewportHeight) {
        programService.setActiveProgram(GLProgramService.GLProgramType.CIRCLE);
        ShaderCircleProgram program = programService.getShaderCircleProgram();

        // TODO: Render circles in screen coordinates!
        float[] innerMatrix = matrixHandler.getTempMatrix();
        Matrix.translateM(innerMatrix, 0, matrixHandler.getMVPOrthoMatrix(), 0, viewportWidth / 2.0f, viewportHeight / 2.0f, 0);
        Matrix.scaleM(innerMatrix, 0, 1, -1, 1);
        glUniformMatrix4fv(program.uniformMVP, 1, false, innerMatrix, 0);

        Utils.throwIfErrors();
        synchronized (mCircleOverlays) {
            for (Circle circle : mCircleOverlays) {
                circle.glDraw(matrixHandler.getTempPoint(), matrixHandler.getTempBuffer(), program);
            }
        }
        Utils.throwIfErrors();
    }

    public void removeCircle(Circle circle) {
        synchronized (mCircleOverlays) {
            mCircleOverlays.remove(circle);
        }
        emitRenderRequest();
    }
}
