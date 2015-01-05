package uk.co.ordnancesurvey.osmobilesdk.gis;


import com.goebl.simplify.PointExtractor;
import com.goebl.simplify.Simplify;

import java.util.Arrays;
import java.util.List;

public final class GeneralizationUtil {

    private GeneralizationUtil() {}

    /**
     * @param tolerance tolerance in the same measurement as the point coordinates
     */
    public static List<Point> simplify(double tolerance, List<Point> points) {
        Point[] gridPoints = points.toArray(new Point[points.size()]);
        Simplify<Point> simplify = new Simplify<Point>(new Point[0],
                new PointExtractor<Point>() {
            @Override
            public double getX(Point point) {
                return point.getX();
            }

            @Override
            public double getY(Point point) {
                return point.getY();
            }
        });

        if (gridPoints.length > 0) {
            gridPoints = simplify.simplify(gridPoints, tolerance, true);
        }
        return Arrays.asList(gridPoints);
    }

    public static Multipoint simplify(double tolerance, Multipoint multipoint) {
        return new Multipoint(simplify(tolerance, multipoint.getPoints()));
    }
}
