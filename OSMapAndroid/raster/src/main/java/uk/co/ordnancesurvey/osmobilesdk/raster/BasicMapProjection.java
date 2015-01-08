/**
 * OpenSpace Android SDK Licence Terms
 *
 * The OpenSpace Android SDK is protected by © Crown copyright – Ordnance Survey 2013.[https://github.com/OrdnanceSurvey]
 *
 * All rights reserved (subject to the BSD licence terms as follows):.
 *
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * Neither the name of Ordnance Survey nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 *
 */
package uk.co.ordnancesurvey.osmobilesdk.raster;

import android.graphics.PointF;

import uk.co.ordnancesurvey.osmobilesdk.gis.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.gis.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.gis.Point;
import uk.co.ordnancesurvey.osmobilesdk.raster.renderer.logic.ScrollRenderer;

import static java.lang.Math.sin;
import static java.lang.Math.cos;

public final class BasicMapProjection implements Projection {

    private static final double AVERAGE_GPS_ALTITUDE = 53;
    private static final double DEG = Math.PI/180;
    // From http://www.ordnancesurvey.co.uk/oswebsite/gps/information/coordinatesystemsinfo/guidecontents/guidea.html
    private static final double AIRY1830_A = 6377563.396, AIRY1830_B = 6356256.910;
    private static final double WGS84_A = 6378137.000, WGS84_B = 6356752.3141;
    private static final double NATGRID_F0 = 0.9996012717, NATGRID_LAT0 = 49*DEG, NATGRID_LNG0 = -2*DEG, NATGRID_E0 = 400000, NATGRID_N0 = -100000;

    private final int mScreenWidth;
    private final int mScreenHeight;

    private final Point mCentre;
    private final float mMetresPerPixel;
    private final BoundingBox mVisibleBounds;

    public BasicMapProjection(int width, int height, ScrollRenderer.ScrollPosition scrollpos) {
        mScreenWidth = width;
        mScreenHeight = height;

        mCentre = new Point(scrollpos.x, scrollpos.y, Point.BNG);
        mMetresPerPixel = scrollpos.metresPerPixel;

        float mapWidth = mScreenWidth * mMetresPerPixel;
        float mapHeight = mScreenHeight * mMetresPerPixel;

        // Clip the rect in case the user has somehow scrolled off the map.
        BoundingBox raw = BoundingBox.fromCentreXYWH(mCentre.getX(), mCentre.getY(),
                mapWidth, mapHeight);
        mVisibleBounds = BngUtil.clippedToGridBounds(raw);
    }

    @Override
    public Point fromScreenLocation(PointF screenLocation) {
        float metresPerPixel = mMetresPerPixel;
        double mapx = mCentre.getX() + (screenLocation.x - mScreenWidth / 2.0f) * metresPerPixel;
        double mapy = mCentre.getY() + (mScreenHeight / 2.0f - screenLocation.y) * metresPerPixel;
        return new Point(mapx, mapy, Point.BNG);
    }

    @Override
    public PointF getScreenLocation(Point point) {
        float x = mScreenWidth / 2.0f + (float) (point.getX() - mCentre.getX()) / mMetresPerPixel;
        float y = mScreenHeight / 2.0f - (float) (point.getY() - mCentre.getY()) / mMetresPerPixel;
        return new PointF(x, y);
    }

    @Override
    public BoundingBox getVisibleBounds() {
        return mVisibleBounds;
    }

    /**
     * Converts a WGS84 latitude/longitude to the corresponding BNG Point.
     * @param point - the Point using WGS84 projection
     * @return newly created Point
     */
    @Override
    public Point toBng(Point point) {
        if(point.isBng()) {
            return point;
        }

        double[] temp = new double[3];

        getOSCoords(point.getX(), point.getY(), temp);
        return new Point(temp[0], temp[1], Point.BNG);
    }

    /**
     * Converts a BNG {@link uk.co.ordnancesurvey.osmobilesdk.gis.Point} to the corresponding
     * WGS84 Point.
     * @param point - the Point using BNG projection
     * @return newly created Point
     */
    @Override
    public Point toWGS84(Point point) {
        if(!point.isBng()) {
            return point;
        }

        double[] temp = new double[3];
        fromOSCoords(point.getX(), point.getY(), 0, temp);
        return new Point(temp[0], temp[1], Point.WGS84);
    }

    public float getMetresPerPixel() {
        return mMetresPerPixel;
    }

    public Point getCenter() {
        return mCentre;
    }

    public PointF displayPointFromPoint(Point gp, PointF displayPointOut) {
        double mapCenterX = mCentre.getX();
        double mapCenterY = mCentre.getY();
        float metresPerPixel = mMetresPerPixel;

        float xPixels = (float) (gp.getX() - mapCenterX) / metresPerPixel;
        float yPixels = (float) (gp.getY() - mapCenterY) / metresPerPixel;

        displayPointOut.x = xPixels;
        displayPointOut.y = yPixels;
        return displayPointOut;
    }

    BoundingBox getVisibleBoundsWithScreenInsets(float insetx, float insety) {
        float mpp = mMetresPerPixel;
        return mVisibleBounds.inset(insetx * mpp, insety * mpp);
    }

    public BoundingBox getExpandedVisibleBounds() {
        return getVisibleBoundsWithScreenInsets(-mScreenWidth / 2.0f, -mScreenHeight / 2.0f);
    }

	/**
	* Converts latitude/longitude (WGS84) to easting and northing (National Grid) and height above geoid (m).
	* Results are highly approximate (±5m).
	* @param lat latitude (deg)
	* @param lng longitude (deg)
	* @param eastNorthHeight Must be at least 3 elements long. On return, first 3 elements are { easting, northing, height }. Other elements are untouched.
	*/
	private void getOSCoords(double lat, double lng, double[] eastNorthHeight) {
        // Approximate average GPS altitude in the UK at a National Grid altitutde of 0.
        // It's somewhere between around 49 and 55, anyway.
		double[] temp = eastNorthHeight;//new double[3];
		latLngTo3D(WGS84_A, WGS84_B, lat, lng, AVERAGE_GPS_ALTITUDE, temp);

		double x = temp[0], y = temp[1], z = temp[2];
		// from http://www.ordnancesurvey.co.uk/oswebsite/gps/information/coordinatesystemsinfo/guidecontents/guide6.html
		// tx/m=-446.448 ty/m=+125.157 tz/m=-542.060 s/ppm=+20.4894 rx/sec=-0.1502 ry/sec=-0.2470 rz/sec=-0.8421
		double tx = -446.448;
		double ty = +125.157;
		double tz = -542.060;
		double s =  +20.4894e-6;
		final double sec = Math.PI/180/60/60;
		double rx = -0.1502*sec;
		double ry = -0.2470*sec;
		double rz = -0.8421*sec;

		// [x']   [tx]   [1+s -rz  ry] [x]
		// [y'] = [ty] + [ rz 1+s -rx] [y]
		// [z']   [tz]   [-ry  rx 1+s] [z]

		double xp = tx + (1+s)*x - rz*y + ry*z;
		double yp = ty + (1+s)*y - rx*z + rz*x;
		double zp = tz + (1+s)*z - ry*x + rx*y;

		// Reuse the same array!
		latLngFrom3D(AIRY1830_A, AIRY1830_B, xp, yp, zp, temp);

		// Reuse the same array again
		latLngToEastNorth(AIRY1830_A, AIRY1830_B, NATGRID_N0, NATGRID_E0, NATGRID_F0, NATGRID_LAT0, NATGRID_LNG0, temp[0], temp[1], temp);
	}

	/**
	* TODO: Unchecked.
	*/
	private void fromOSCoords(double e, double n, double h, double[] latLngAlt) {
		// Save another malloc
		double[] temp = latLngAlt;
		eastNorthToLatLng(AIRY1830_A, AIRY1830_B, NATGRID_N0, NATGRID_E0, NATGRID_F0, NATGRID_LAT0, NATGRID_LNG0, e, n, temp);

		latLngTo3D(AIRY1830_A, AIRY1830_B, temp[0], temp[1], h, temp);

		double x = temp[0], y = temp[1], z = temp[2];
		// from http://www.ordnancesurvey.co.uk/oswebsite/gps/information/coordinatesystemsinfo/guidecontents/guide6.html
		// tx/m=-446.448 ty/m=+125.157 tz/m=-542.060 s/ppm=+20.4894 rx/sec=-0.1502 ry/sec=-0.2470 rz/sec=-0.8421

		// We can calculate the inverse easily enough with the following Python:
/*
import numpy,math
sec=math.pi/180/3600
s=+20.4894e-6
tx=-446.448;ty=+125.157;tz=-542.060
rx=-0.1502*sec;ry=-0.2470*sec;rz=-0.8421*sec
T2 = numpy.mat([[1+s,-rz,ry,tx],[rz,1+s,-rx,ty],[-ry,rx,1+s,tz],[0,0,0,1]])**-1
print "s/ppm", (1-sum((T2[0,0],T2[1,1],T2[2,2]))/3)*1e6
print "rx/sec", sum((T2[2,1],-T2[1,2]))/2/sec
print "ry/sec", sum((T2[0,2],-T2[2,0]))/2/sec
print "rz/sec", sum((T2[1,0],-T2[0,1]))/2/sec
print "t/m", T2[0,3],T2[1,3],T2[2,3]
*/
		// However, the forward transform is just a small translation, a small scale factor, and a small "rotation".
		// Instead, we approximate the inverse by reversing all the signs, which is accurate enough.
		double tx = -446.448 * -1;
		double ty = +125.157 * -1;
		double tz = -542.060 * -1;
		double s =  +20.4894e-6 * -1;
		final double sec = Math.PI/180/60/60;
		double rx = -0.1502*sec * -1;
		double ry = -0.2470*sec * -1;
		double rz = -0.8421*sec * -1;

		double xp = tx + (1+s)*x - rz*y + ry*z;
		double yp = ty + (1+s)*y - rx*z + rz*x;
		double zp = tz + (1+s)*z - ry*x + rx*y;

		latLngFrom3D(WGS84_A, WGS84_B, xp, yp, zp, temp);
	}

	/**
	* Converts latitude and longitude on an ellipsoid to 3D Cartesian coordinates. Angles are in degrees.
	* Results are highly approximate (±5m).
	* @param a
	* @param b
	* @param lat
	* @param lng
	* @param h
	* @param xyzOut Must be at least 3 elements long. On return, first 3 elements are { x, y, z }. Other elements are untouched.
	*/
	private void latLngTo3D(double a, double b, double lat, double lng, double h, double[] xyzOut) {
		// From http://www.ordnancesurvey.co.uk/oswebsite/gps/docs/convertingcoordinates3D.pdf
		double asq = a*a;
		double bsq = b*b;
		double esq = (asq-bsq)/asq;

		lat = Math.toRadians(lat);
		lng = Math.toRadians(lng);
		double sinlat = Math.sin(lat);
		double coslat = Math.cos(lat);
		double sinlng = Math.sin(lng);
		double coslng = Math.cos(lng);
		double v = a/Math.sqrt(1-esq*sinlat*sinlat);
		xyzOut[0] = (v+h)*coslat*coslng;
		xyzOut[1] = (v+h)*coslat*sinlng;
		xyzOut[2] = ((1-esq)*v+h)*sinlat; // TODO numerical wotsits.
	}

	/**
	* Converts 3D Cartesian coordinates to latitude and longitude on an ellipsoid. Angles are in degrees.
	* @param a Ellipsoid major axis.
	* @param b Ellipsoid minor axis.
	* @param x
	* @param y
	* @param z
	* @param latLngHeightOut Must be at least 3 elements long. On return, first 3 elements are { latitude, longitude, altitude }. Other elements are untouched.
	*/
	private void latLngFrom3D(double a, double b, double x, double y, double z, double[] latLngHeightOut) {
		// From http://www.ordnancesurvey.co.uk/oswebsite/gps/docs/convertingcoordinates3D.pdf
		double asq = a*a;
		double bsq = b*b;
		double esq = (asq-bsq)/asq;

		double lng = Math.atan2(y, x);
		double p = Math.sqrt(x*x+y*y);
		double lat = Math.atan(z/p*(1-esq)); // TODO numerical wotsits.

		for (double oldDiff = Double.POSITIVE_INFINITY; /**/ ; /**/) {
			double sinlat = Math.sin(lat);
			double v = a/Math.sqrt(1-esq*sinlat*sinlat);
			double newlat = Math.atan2(z+esq*v*sinlat,p);
			double diff = Math.abs(newlat - lat);
			lat = newlat;
			// Assume it converges, and that successive differences always get smaller.
			// When they stop getting smaller, we're precise enough.
			if (diff >= oldDiff) {
				break;
			}
			oldDiff = diff;
		}

		double sinlat = Math.sin(lat);
		double v = a/Math.sqrt(1-esq*sinlat*sinlat);
		double h = p/Math.cos(lat)-v;

		latLngHeightOut[0] = Math.toDegrees(lat);
		latLngHeightOut[1] = Math.toDegrees(lng);
		latLngHeightOut[2] = h;
	}

	/**
	* TODO: BUGS: The northing is slightly inaccurate apparently due to rounding errors.
	* @param a Ellipsoid major axis.
	* @param b Ellipsoid minor axis.
	* @param n0 Northing of true origin (m).
	* @param e0 Easting of true origin (m).
	* @param f0 Scale factor at central meridian.
	* @param lat0 Latitude of true origin (radians).
	* @param lng0 Longitude of true origin (radians).
	* @param lat Latitude (deg).
	* @param lng Longitude (deg).
	* @param eastNorthOut Must be at least two elements long. On return, first two elements are { easting, northing }. Other elements are untouched.
	*/
	private void latLngToEastNorth(double a, double b, double n0, double e0, double f0, double lat0, double lng0, double lat, double lng, double[] eastNorthOut) {
		// From http://www.ordnancesurvey.co.uk/oswebsite/gps/docs/convertingcoordinatesEN.pdf
		double asq = a*a;
		double bsq = b*b;
		double esq = (asq-bsq)/asq;

		lat = Math.toRadians(lat);
		lng = Math.toRadians(lng);

		double sinlat = Math.sin(lat);
		double coslat = Math.cos(lat);
		double tanlat = sinlat/coslat;

		double n = (a-b)/(a+b);
		double v = a*f0/Math.sqrt(1-esq*sinlat*sinlat);
		double p = a*f0*(1-esq)*Math.pow(1-esq*sinlat*sinlat,-1.5);
		double etasq = v/p-1;

		double m = calculateM(b, f0, n, lat0, lat);
		double _I = m + n0;
		double _II = v/2 * sinlat * coslat;
		double _III = v/24 * sinlat * coslat * coslat * coslat * (5-tanlat*tanlat + 9*etasq);
		double _IIIA = v/720 * sinlat * coslat * coslat * coslat * coslat * coslat * (61-58*tanlat*tanlat + tanlat*tanlat*tanlat*tanlat);
		double _IV = v*coslat;
		double _V = v/6 * coslat * coslat * coslat * (v/p - tanlat*tanlat);
		double _VI = v/120 * coslat * coslat * coslat * coslat * coslat * (5-18*tanlat*tanlat + tanlat*tanlat*tanlat*tanlat + 14*etasq - 58*tanlat*tanlat*etasq);
		eastNorthOut[1] = _I + _II*(lng-lng0)*(lng-lng0) + _III*(lng-lng0)*(lng-lng0)*(lng-lng0)*(lng-lng0) + _IIIA*(lng-lng0)*(lng-lng0)*(lng-lng0)*(lng-lng0)*(lng-lng0)*(lng-lng0);
		eastNorthOut[0] = e0 + _IV*(lng-lng0) + _V*(lng-lng0)*(lng-lng0)*(lng-lng0) + _VI*(lng-lng0)*(lng-lng0)*(lng-lng0)*(lng-lng0)*(lng-lng0);
	}

	/**
	* TODO: Unchecked.
	*/
	private void eastNorthToLatLng(double a, double b, double n0, double e0, double f0, double lat0, double lng0, double e, double N, double[] latLngOut) {
		// From http://www.ordnancesurvey.co.uk/oswebsite/gps/docs/convertingcoordinatesEN.pdf
		double asq = a*a;
		double bsq = b*b;
		double esq = (asq-bsq)/asq;

		double n = (a-b)/(a+b);

		double lat = (N-n0)/(a*f0) + lat0;
		for (;;) {
			double m = calculateM(b, f0, n, lat0, lat);
			// "0.01 mm"
			if (Math.abs(N-n0-m) < 0.01e-3) {
				// TODO: What if this never terminates?
				break;
			}
			lat = (N-n0-m)/(a*f0) + lat;
		}
		double sinlat = Math.sin(lat);
		double coslat = Math.cos(lat);
		double seclat = 1/coslat;
		double tanlat = sinlat/coslat;
		double tan2lat = tanlat*tanlat;
		double tan4lat = tan2lat*tan2lat;
		double tan6lat = tan4lat*tan2lat;

		double v = a*f0/Math.sqrt(1-esq*sinlat*sinlat);
		double p = a*f0*(1-esq)*Math.pow(1-esq*sinlat*sinlat,-1.5);
		double etasq = v/p-1;

		double v3 = v*(v*v);
		double v5 = v3*(v*v);
		double v7 = v5*(v*v);

		double _VII = tanlat/(2*p*v);
		double _VIII = tanlat/(24*p*v3) * (5 + 3*tan2lat + etasq - 9*tan2lat*etasq);
		double _IX = tanlat/(720*p*v5) * (61 + 90*tan2lat + 45*tan4lat);
		double _X = seclat/v;
		double _XI = seclat/(6*v3) * (v/p + 2*tan2lat);
		double _XII = seclat/(120*v5) * (5+28*tan2lat+24*tan4lat);
		double _XIIA = seclat/(5040*v7) * (61 + 662*tan2lat + 1320*tan4lat + 720*tan6lat);

		double e_e0 = e-e0;
		double e_e0_2 = e_e0*e_e0;
		double e_e0_3 = e_e0_2*e_e0;
		double e_e0_4 = e_e0_2*e_e0_2;
		double e_e0_5 = e_e0_4*e_e0;
		double e_e0_6 = e_e0_4*e_e0_2;
		double e_e0_7 = e_e0_6*e_e0;

		latLngOut[0] = Math.toDegrees(lat - _VII*e_e0_2 + _VIII*e_e0_4 - _IX*e_e0_6);
		latLngOut[1] = Math.toDegrees(lng0 + _X*e_e0 - _XI*e_e0_3 + _XII*e_e0_5 - _XIIA*e_e0_7);
	}

	// A largeish equation common to both calculations.
	private static double calculateM(double b, double f0, double n, double lat0, double lat) {
		double m = b*f0*(
				(1 + n + 5.0/4*n*n + 5.0/4*n*n*n)*(lat-lat0)
				- (3*n + 3*n*n + 21.0/8*n*n*n)*sin(lat-lat0)*cos(lat+lat0)
				+ (15.0/8)*(n*n + n*n*n)*sin(2*(lat-lat0))*cos(2*(lat+lat0))
				- 35.0/24*n*n*n*sin(3*(lat-lat0))*cos(3*(lat+lat0))
			);
		return m;
	}

    //public static MapProjection getDefault() {
        //return new BasicMapProjection();
    //}


}
