package uk.co.ordnancesurvey.osmobilesdk.raster.annotations;

import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.BaseRenderer;

public class Annotation {

    protected static final float ZERO_COMPARISON = 0.001f;

    protected BaseRenderer mBaseRenderer = null;
    protected boolean mIsVisible = true;
    protected float mBearing = 0;
    protected float mZIndex = 0;

    /**
     * Returns the bearing of the overlay in degrees clockwise from north.
     * @return The zIndex of this shape.
     */
    public float getBearing() {
        return mBearing;
    }

    /**
     * Returns the zIndex.
     * @return The zIndex of this shape.
     */
    public float getZIndex() {
        return mZIndex;
    }

    /**
     * Checks whether the shape is visible.
     * @return True if the shape is visible; false if it is invisible.
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * Sets a reference to the renderer handling changes to this
     * {@link Annotation}
     * @param baseRenderer Renderer responsible for handling drawing changes
     */
    public void setBaseRenderer(BaseRenderer baseRenderer) {
        mBaseRenderer = baseRenderer;
    }

    /**
     * Sets the bearing of the ground overlay (the direction that the vertical axis of the marker points) in degrees
     * clockwise from north. The rotation is performed about the anchor point.
     *
     * @param bearing bearing in degrees clockwise from north
     */
    public void setBearing(float bearing) {
        mBearing = bearing;
        requestRender();
    }

    /**
     * Sets the visibility of the annotation.
     * If this shape is not visible then it will not be drawn.
     * All other state is preserved. Defaults to True.
     * @param isVisible	false to make this shape invisible.
     */
    public void setIsVisible(boolean isVisible) {
        mIsVisible = isVisible;
        requestRender();
    }

    /**
     * Sets the zIndex.
     * Overlays (such as shapes) with higher zIndices are drawn above those with lower indices.
     * @param zIndex the z index of the overlay
     */
    public void setZIndex(float zIndex) {
        mZIndex = zIndex;
        requestRender();
    }

    protected final void requestRender() {
        if(mBaseRenderer != null) {
            mBaseRenderer.emitRenderRequest();
        }
    }
}
