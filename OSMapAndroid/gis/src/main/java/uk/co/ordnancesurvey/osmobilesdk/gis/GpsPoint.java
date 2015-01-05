//package uk.co.ordnancesurvey.osmobilesdk.gis;
//
//public class GpsPoint extends Point {
//
//    private final Double mAltitude;
//    private final double mLatitude;
//    private final double mLongitude;
//    private final Long mTime;
//    private final String mDescription;
//    private final String mName;
//
//    // TODO: replace with builder
//    public GpsPoint(double latitude, double longitude, Double altitude, Long time) {
//        super(Projection.getDefault().toGridPoint(latitude, longitude).x,
//                Projection.getDefault().toGridPoint(latitude, longitude).y);
//        mAltitude = altitude;
//        mLatitude = latitude;
//        mLongitude = longitude;
//        mTime = time;
//        mDescription = null;
//        mName = null;
//    }
//
//    public GpsPoint(double latitude, double longitude,
//                    double altitude, long time,
//                    String name, String description) {
//        super(Projection.getDefault().toGridPoint(latitude, longitude).x,
//                Projection.getDefault().toGridPoint(latitude, longitude).y);
//        mAltitude = altitude;
//        mLatitude = latitude;
//        mLongitude = longitude;
//        mTime = time;
//        mName = name;
//        mDescription = description;
//    }
//
//    public Double getAltitude() {
//        return mAltitude;
//    }
//
//    public double getLatitude() {
//        return mLatitude;
//    }
//
//    public double getLongitude() {
//        return mLongitude;
//    }
//
//    public Long getTime() {
//        return mTime;
//    }
//
//    public String getName() {
//        return mName;
//    }
//
//    public String getDescription() {
//        return mDescription;
//    }
//
//    public boolean hasAltitude() {
//        return mAltitude != null;
//    }
//
//    public boolean hasTime() {
//        return mTime != null;
//    }
//
//    public boolean hasDescription() {
//        return mDescription != null;
//    }
//
//    public boolean hasName() {
//        return mName != null;
//    }
//
//    public boolean equals(Object object) {
//        if (object == null) {
//            return false;
//        }
//
//        if (!(object instanceof GpsPoint)) {
//            return false;
//        }
//
//        GpsPoint gpsPoint = (GpsPoint) object;
//
//        boolean latitudeCompa = this.getLatitude() == gpsPoint.getLatitude();
//        boolean longitudeCompa = this.getLongitude() == gpsPoint.getLongitude();
//        boolean altitudeCompa = equalsWithNulls(this.getAltitude(), gpsPoint.getAltitude());
//        boolean timeCompa = equalsWithNulls(this.getTime(), gpsPoint.getTime());
//        boolean nameCompa = equalsWithNulls(this.getName(), gpsPoint.getName());
//        boolean descCompa = equalsWithNulls(this.getDescription(), gpsPoint.getDescription());
//        boolean result = latitudeCompa && longitudeCompa && altitudeCompa && timeCompa &&
//                nameCompa && descCompa;
//        return result;
//    }
//
//    @Override
//    public int hashCode() {
//        // CS:OFF MagicNumber FOR 8 LINES
//        //Algorithm from Effective Java by Joshua Bloch [Jon Aquino]
//        int result = 17;
//        result = 37 * result + hashCode(mAltitude);
//        result = 37 * result + mDescription.hashCode();
//        result = 37 * result + hashCode(mLatitude);
//        result = 37 * result + hashCode(mLongitude);
//        result = 37 * result + mName.hashCode();
//        result = 37 * result + hashCode(mTime);
//        return result;
//    }
//
//    private static boolean equalsWithNulls(Object a, Object b) {
//        if (a == b) {
//            return true;
//        }
//        if ((a == null) || (b == null)) {
//            return false;
//        }
//        return a.equals(b);
//    }
//
//    /**
//     * Returns a hash code for a double value, using the algorithm from
//     * Joshua Bloch's book <i>Effective Java"</i>
//     */
//    private static int hashCode(double x) {
//        // CS:OFF MagicNumber FOR 2 LINES
//        long f = Double.doubleToLongBits(x);
//        return (int)(f ^ (f >>> 32));
//    }
//}
