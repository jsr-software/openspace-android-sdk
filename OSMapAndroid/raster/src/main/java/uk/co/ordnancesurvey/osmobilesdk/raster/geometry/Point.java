package uk.co.ordnancesurvey.osmobilesdk.raster.geometry;

public class Point {

    public static final int WGS84 = 4326;
    public static final int BNG = 27700;

    private final double mX;
    private final double mY;
    private final int mSrid;

    public Point(double x, double y, int srid) {
        mX = x;
        mY = y;
        mSrid = srid;
    }

    public double getX() {
        return mX;
    }

    public double getY() {
        return mY;
    }

    public boolean isBng() {
        return mSrid == BNG;
    }

    public double distanceTo(Point other) {
        return Math.hypot(mX - other.getX(), mY - other.getY());
    }
}
