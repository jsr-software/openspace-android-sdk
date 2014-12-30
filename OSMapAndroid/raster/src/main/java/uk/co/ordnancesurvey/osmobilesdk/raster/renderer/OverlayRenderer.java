package uk.co.ordnancesurvey.osmobilesdk.raster.renderer;

import java.util.LinkedList;

import uk.co.ordnancesurvey.osmobilesdk.raster.GLMapRenderer;
import uk.co.ordnancesurvey.osmobilesdk.raster.PolyOverlay;
import uk.co.ordnancesurvey.osmobilesdk.raster.Polygon;
import uk.co.ordnancesurvey.osmobilesdk.raster.PolygonOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.Polyline;
import uk.co.ordnancesurvey.osmobilesdk.raster.PolylineOptions;
import uk.co.ordnancesurvey.osmobilesdk.raster.Utils;

import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.glEnable;

public class OverlayRenderer extends BaseRenderer {

    private final LinkedList<PolyOverlay> mPolyOverlays = new LinkedList<>();

    public OverlayRenderer(GLMapRenderer mapRenderer, RendererListener listener) {
        super(mapRenderer, listener);
    }

    public Polygon addPolygon(PolygonOptions polygonOptions) {
        Polygon polygon = new Polygon(polygonOptions, mMapRenderer);
        synchronized (mPolyOverlays) {
            mPolyOverlays.add(polygon);
        }
        emitRenderRequest();
        return polygon;
    }

    public Polyline addPolyline(PolylineOptions polylineOptions) {
        Polyline polyline = new Polyline(polylineOptions, mMapRenderer);
        synchronized (mPolyOverlays) {
            mPolyOverlays.add(polyline);
        }
        emitRenderRequest();
        return polyline;
    }

    public void clear() {
        synchronized (mPolyOverlays) {
            mPolyOverlays.clear();
        }
    }

    public void onDrawFrame(GLProgramService programService, GLMatrixHandler matrixHandler, float metresPerPixel) {
        // Enable alpha-blending
        glEnable(GL_BLEND);

        // Draw overlays
        programService.setActiveProgram(GLProgramService.GLProgramType.OVERLAY);

        synchronized (mPolyOverlays) {
            for (PolyOverlay poly : mPolyOverlays) {
                poly.glDraw(matrixHandler, metresPerPixel, programService.getShaderOverlayProgram());
            }
        }
        Utils.throwIfErrors();
    }

    public void removePolyOverlay(PolyOverlay polygon) {
        synchronized (mPolyOverlays) {
            mPolyOverlays.remove(polygon);
        }
        emitRenderRequest();
    }
}
