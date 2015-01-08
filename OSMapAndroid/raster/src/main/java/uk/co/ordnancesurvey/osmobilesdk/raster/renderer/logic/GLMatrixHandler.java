package uk.co.ordnancesurvey.osmobilesdk.raster.renderer.logic;

import android.graphics.PointF;

import java.nio.FloatBuffer;

import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;

public class GLMatrixHandler {
    private final float[] mMVPOrthoMatrix = new float[16];
    private final float[] mTempMatrix = new float[32];
    private final FloatBuffer mTempFloatBuffer = Utils.directFloatBuffer(8);
    private final PointF mTempPoint = new PointF();

    public float[] getMVPOrthoMatrix() {
        return mMVPOrthoMatrix;
    }

    public float[] getTempMatrix() {
        return mTempMatrix;
    }

    public FloatBuffer getTempBuffer() {
        return mTempFloatBuffer;
    }

    public PointF getTempPoint() {
        return mTempPoint;
    }
}
