package uk.co.ordnancesurvey.osmobilesdk.gis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Polyline {

    private final List<Multipoint> mPaths;

    /**
     * Note: ESRI state that "[a] polyline is composed of one or more paths (part) and a path
     * (part) is formed of one or more segments.
     * Source:
     * http://resources.esri.com/help/9.3/arcgisengine/dotnet/570ae98c-5e4a-495b-9894-a16e45fee5ff.htm
     */
    public Polyline() {
        mPaths = new ArrayList<Multipoint>();
    }

    /**
     * @param paths An array of paths where each path is a multipoint (array of MapPoints).
     */
    public Polyline(List<Multipoint> paths) {
        mPaths = paths;
    }

    /**
     * @return A box encapsulation all the paths or null if no paths or the paths only contain
     * single points.
     */
    public BoundingBox getBoundingBox() {

        List<Double> listOfXs = new ArrayList<Double>();
        List<Double> listOfYs = new ArrayList<Double>();

        for (Multipoint path : mPaths) {
            BoundingBox pathBox = path.getBoundingBox();
            if (pathBox == null) {
                continue;
            }
            listOfXs.add(pathBox.getMinX());
            listOfXs.add(pathBox.getMaxX());
            listOfYs.add(pathBox.getMinY());
            listOfYs.add(pathBox.getMaxY());
        }

        if (listOfXs.size() == 0) {
            return null;
        }

        double minX = (Collections.min(listOfXs));
        double minY = (Collections.min(listOfYs));
        double maxX = (Collections.max(listOfXs));
        double maxY = (Collections.max(listOfYs));

        return new BoundingBox(minX, minY, maxX, maxY);
    }

    public List<Multipoint> getPaths() {
        return mPaths;
    }

    public void addPath(Multipoint path) {
        mPaths.add(path);
    }
}
