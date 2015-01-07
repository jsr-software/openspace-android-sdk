package uk.co.ordnancesurvey.osmobilesdk.raster.renderer;

import java.util.LinkedList;

import uk.co.ordnancesurvey.osmobilesdk.raster.GLMapRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.ScreenProjection;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.PolyAnnotation;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Polygon;
import uk.co.ordnancesurvey.osmobilesdk.raster.annotations.Polyline;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;

import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.glEnable;

public class OverlayRenderer extends BaseRenderer {

    private final LinkedList<PolyAnnotation> mPolyAnnotations = new LinkedList<>();

    public OverlayRenderer(GLMapRenderer mapRenderer, RendererListener listener) {
        super(mapRenderer, listener);
    }

    public Polygon addPolygon(Polygon.Builder builder) {
        Polygon polygon = builder.build();
        polygon.setBaseRenderer(this);
        synchronized (mPolyAnnotations) {
            mPolyAnnotations.add(polygon);
        }
        emitRenderRequest();
        return polygon;
    }

    public Polyline addPolyline(Polyline.Builder builder) {
        Polyline polyline = builder.build();
        polyline.setBaseRenderer(this);
        synchronized (mPolyAnnotations) {
            mPolyAnnotations.add(polyline);
        }
        emitRenderRequest();
        return polyline;
    }

    public void clear() {
        synchronized (mPolyAnnotations) {
            mPolyAnnotations.clear();
        }
    }

    public void onDrawFrame(ScreenProjection projection, GLProgramService programService,
                            GLMatrixHandler matrixHandler, float metresPerPixel) {
        // Enable alpha-blending
        glEnable(GL_BLEND);

        // Draw overlays
        programService.setActiveProgram(GLProgramService.GLProgramType.OVERLAY);

        synchronized (mPolyAnnotations) {
            for (PolyAnnotation poly : mPolyAnnotations) {
                poly.glDraw(projection, matrixHandler, metresPerPixel,
                        programService.getShaderOverlayProgram());
            }
        }
        Utils.throwIfErrors();
    }

    public void removePolyOverlay(PolyAnnotation polygon) {
        synchronized (mPolyAnnotations) {
            mPolyAnnotations.remove(polygon);
        }
        emitRenderRequest();
    }
}
