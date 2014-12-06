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

import static android.opengl.GLES20.*;

class ShaderCircleProgram extends GLProgram {
	final int uniformMVP;
	final int uniformFillColor;
	final int uniformStrokeColor;
	final int uniformCenterRadius;
	final int attribVCoord;

	ShaderCircleProgram()
	{
		super(Shaders.shader_overlay_vsh, Shaders.shader_circle_fsh);
		uniformMVP = glGetUniformLocation(program, "uMVPMatrix");
		uniformFillColor = glGetUniformLocation(program, "uFillColor");
		uniformStrokeColor = glGetUniformLocation(program, "uStrokeColor");
		uniformCenterRadius = glGetUniformLocation(program, "uCenterRadius");
		attribVCoord = glGetAttribLocation(program, "vCoord");
		Utils.throwIfErrors();

		
	}
	void use() {
		super.use();
		glEnableVertexAttribArray(attribVCoord);		
	}
	
	@Override
	void stopUsing() {
		glDisableVertexAttribArray(attribVCoord);

	}

}
