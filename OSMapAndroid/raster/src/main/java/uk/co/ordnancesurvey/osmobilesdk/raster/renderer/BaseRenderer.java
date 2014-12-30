package uk.co.ordnancesurvey.osmobilesdk.raster.renderer;

import uk.co.ordnancesurvey.osmobilesdk.raster.GLMapRenderer;

public abstract class BaseRenderer {

    protected final GLMapRenderer mMapRenderer;
    private final RendererListener mRendererListener;

    protected BaseRenderer(GLMapRenderer mapRenderer, RendererListener listener) {
        mMapRenderer = mapRenderer;
        mRendererListener = listener;
    }

    protected void emitRenderRequest() {
        if(mRendererListener != null) {
            mRendererListener.onRenderRequested();
        }
    }
}
