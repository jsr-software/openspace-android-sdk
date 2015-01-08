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
package uk.co.ordnancesurvey.osmobilesdk.raster.renderer.logic;

import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.shaders.GLProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.shaders.ShaderCircleProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.shaders.ShaderOverlayProgram;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.shaders.ShaderProgram;

public class GLProgramService {

    public enum GLProgramType {
        CIRCLE,
        OVERLAY,
        SHADER
    }

    private ShaderProgram mShaderProgram;
    private ShaderOverlayProgram mShaderOverlayProgram;
    private ShaderCircleProgram mShaderCircleProgram;
    private GLProgram mLastProgram = null;
    private GLProgramType mLastType = null;

    public void onSurfaceCreated() {
        mShaderProgram = new ShaderProgram();
        mShaderOverlayProgram = new ShaderOverlayProgram();
        mShaderCircleProgram = new ShaderCircleProgram();
    }

    public void setActiveProgram(GLProgramType programType) {
        if (programType == mLastType) {
            return;
        }

        if (mLastProgram != null) {
            mLastProgram.stopUsing();
        }

        switch (programType) {
            case CIRCLE: {
                mShaderCircleProgram.use();
                mLastProgram = mShaderCircleProgram;
                break;
            }
            case OVERLAY: {
                mShaderOverlayProgram.use();
                mLastProgram = mShaderOverlayProgram;
                break;
            }
            case SHADER: {
                mShaderProgram.use();
                mLastProgram = mShaderProgram;
                break;
            }
            default:
                throw new IllegalStateException("Unknown program type");
        }
    }

    public ShaderProgram getShaderProgram() {
        return mShaderProgram;
    }

    public ShaderOverlayProgram getShaderOverlayProgram() {
        return mShaderOverlayProgram;
    }

    public ShaderCircleProgram getShaderCircleProgram() {
        return mShaderCircleProgram;
    }
}
