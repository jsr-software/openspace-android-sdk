package uk.co.ordnancesurvey.osmobilesdk.gis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Multipoint {

    private final List<Point> mPoints;

    /**
     * An ordered collection of points.  Sometimes synonymous with 'path'
     */
    public Multipoint() {
        mPoints = new ArrayList<Point>();
    }

    public Multipoint(List<Point> points) {
        mPoints = points;
    }

    public void addPoint(Point point) {
        mPoints.add(point);
    }

    /**
     * @return the extent of this Multipoint or null if the Multipoint has a single point.
     */
    public BoundingBox getBoundingBox() {
        if (mPoints.size() <= 1) {
            return null;
        }

        List<Double> listOfXs = new ArrayList<Double>();
        List<Double> listOfYs = new ArrayList<Double>();

        for (Point point : mPoints) {
            double currentX = point.getX();
            listOfXs.add(currentX);
            double currentY = point.getY();
            listOfYs.add(currentY);
        }

        long minX = (Collections.min(listOfXs)).longValue();
        long minY = (Collections.min(listOfYs)).longValue();
        long maxX = (Collections.max(listOfXs)).longValue();
        long maxY = (Collections.max(listOfYs)).longValue();

        return new BoundingBox(minX, minY, maxX, maxY);
    }

    public List<Point> getPoints() {
        return mPoints;
    }
}
