package uk.co.ordnancesurvey.osmobilesdk.raster;

import android.graphics.PointF;

import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;

/**
 * Encapsulates the conversion between a {@link uk.co.ordnancesurvey.osmobilesdk.gis.Point}
 * in WGS84 to a {@link uk.co.ordnancesurvey.osmobilesdk.gis.Point} in BNG.
 */
public interface Projection {

    /**
     * Converts a WGS84 latitude/longitude to the corresponding BNG Point.
     * @param point - the Point using WGS84 projection
     * @return newly created Point
     */
    public abstract Point toBng(Point point);

    /**
     * Converts a BNG {@link uk.co.ordnancesurvey.osmobilesdk.gis.Point} to the corresponding WGS84 Point.
     * @param point - the Point using BNG projection
     * @return newly created Point
     */
    public abstract Point toWGS84(Point point);


    public BoundingBox getVisibleBounds();

    public PointF getScreenLocation(Point point);

    public Point fromScreenLocation(PointF screenLocation);
}
