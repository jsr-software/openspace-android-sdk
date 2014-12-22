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
package uk.co.ordnancesurvey.osmobilesdk.gis;

import java.text.ParseException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * A Utility class to allow calculations around British National Grid
 */
public final class BngUtil {

    /**
     * The height in metres of the BNG Grid
     */
    public static final int GRID_HEIGHT = 1300000;

    /**
     * The width in metres of the BNG Grid
     */
    public static final int GRID_WIDTH = 700000;

    private static final String[] NATGRID_LETTERS = {"VWXYZ","QRSTU","LMNOP","FGHJK","ABCDE"};

    private BngUtil() {
    }

    /**
     * Note: that if the {@link .Point} is a WGS84 {@link .Point} an
     * inner conversion is carried out prior to the clamping.
     * The {@link .Point} returned will always be a BNG {@link .Point}
     * @param point
     * @return the clamped BNG {@link .Point}
     */
    public static Point clampToBngBounds(Point point) {
        final Point innerPoint = convertToBngIfNeeded(point);

        if (isInBngBounds(innerPoint)) {
            return innerPoint;
        }

        return clampToBngBounds(innerPoint.getX(), innerPoint.getY());
    }

    /**
     * Construct a new {@link .BoundingBox}, clipped to the OS National Grid bounds.
     * @param boundingBox the original {@link .BoundingBox}
     * @return new {@link .BoundingBox} clamped to the BNG bounds
     */
    public static BoundingBox clippedToGridBounds(BoundingBox boundingBox) {
        double minX = Math.max(0, boundingBox.getMinX());
        double minY = Math.max(0, boundingBox.getMinY());
        double maxX = Math.min(GRID_WIDTH, boundingBox.getMaxX());
        double maxY = Math.min(GRID_HEIGHT, boundingBox.getMaxY());
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    public static boolean isInBngBounds(BoundingBox boundingBox) {
        if (0 <= boundingBox.getMinX() && boundingBox.getMaxX() <= GRID_WIDTH
                && 0 <= boundingBox.getMinY() && boundingBox.getMaxY() <= GRID_HEIGHT) {
            return true;
        }
        return false;
    }

    /**
     * Note: that if the {@link .Point} is a WGS84 {@link .Point} an
     * inner conversion is carried out and the evaluation is carried out on the BNG {@link .Point}.
     * The original point is unchanged.
     * @param point
     * @return true if the {@link .Point} is within the bounds of BNG
     */
    public static boolean isInBngBounds(Point point) {

        final Point innerPoint = convertToBngIfNeeded(point);

        final double x = innerPoint.getX();
        final double y = innerPoint.getY();

        if (0 <= x && x <= GRID_WIDTH && 0 <= y && y <= GRID_HEIGHT) {
            return true;
        }
        return false;
    }

    /**
     * Create a {@link .Point} from an OS Grid Reference (e.g. SK35)
     *
     * @param gridRef      An OS Grid Reference as a string
     * @return The newly constructed BNG {@link .Point} corresponding to grid reference given
     * @throws java.text.ParseException
     */
    public static Point convertBngGridReference(String gridRef) throws ParseException {
        gridRef = gridRef.toUpperCase(Locale.ENGLISH).trim();
        if (gridRef.length() < 2) {
            throw new ParseException("Grid Reference is too short", gridRef.length());
        }

        int big = 500000;
        int small = big / 5;

        // Read the first two digits, converting them into an easting and northing "index".
        char c0 = gridRef.charAt(0);
        char c1 = gridRef.charAt(1);
        int e0 = -1, e1 = -1;
        int n0 = -1, n1 = -1;

        for (int n = 0; n < 5; n++) {
            int e = NATGRID_LETTERS[n].indexOf(c0);
            if (e > -1) {
                // Offset relative to the S square. This means we immediately discard coordinates south/west of S.
                e0 = e - 2;
                n0 = n - 1;
            }
            e = NATGRID_LETTERS[n].indexOf(c1);
            if (e > -1) {
                e1 = e;
                n1 = n;
            }
        }

        if (!(e0 >= 0 && e1 >= 0 && n0 >= 0 && n1 >= 0)) {
            throw new ParseException("Not on the National Grid", gridRef.length());
        }
        double x = e0 * big + e1 * small;
        double y = n0 * big + n1 * small;

        // If it's off the grid, we also want to reject it.
        // We also want to reject coordinates on 700000e or 1300000n, since those would
        // use grid letters off the map.
        // Use the contrapositive to ensure NAN-safety.
        if (!(x < GRID_WIDTH && y < GRID_HEIGHT)) {
            throw new ParseException("Not on the National Grid", gridRef.length());
        }

        if (gridRef.length() <= 2) {
            // We'll fail to scan any digits below if there are no digits to scan, as
            // with the bare (digitless) "SV".
            // Handle it here.
            return new Point(x, y, Point.BNG);
        }

        gridRef = gridRef.substring(2).trim();
        Scanner s = new Scanner(gridRef);
        String eStr = null;
        String nStr = null;
        boolean success = false;

        try {
            eStr = s.next("\\d+");
            // Skip any white space
            if (s.hasNext()) {
                s.skip("\\s+");
                nStr = s.next("\\d+");
            }
            success = !s.hasNext();
        } catch (NoSuchElementException e) {
        }

        // We should be "successful". We should also have some digits.
        if (!success || eStr == null) {
            throw new ParseException("Failed to parse grid reference", 2);
        }
        int ndigs = eStr.length();

        // If we don't have separate northing digits, attempt to split the easting digits in half.
        if (nStr == null) {
            ndigs /= 2;
            nStr = eStr.substring(ndigs);
            eStr = eStr.substring(0, ndigs);
            assert (ndigs == eStr.length());
            // This should still be true.
        }

        // Handle an odd number of digits (NN123) or an inconsistent number of digits
        // (NN 12 3456). Also handle too few digits (NN), which should be taken care of above,
        // or too many digits (NN 123456 123456).
        if (ndigs != nStr.length() || ndigs < 1 || ndigs > 5) {
            throw new ParseException("Invalid number of digits in grid reference", 2);
        }

        x += small / Math.pow(10, ndigs) * Integer.valueOf(eStr);
        y += small / Math.pow(10, ndigs) * Integer.valueOf(nStr);

        return new Point(x, y, Point.BNG);
    }

    /**
     * Return a String containing a National Grid Reference containing two letters and an
     * even number of digits (e.g. SK35)
     * @param referenceLength Number of digits to use for reference.
     *                        For example, SK35 is a two digit grid reference.
     * @return OS Grid Reference, as a String
     */
    public static String toBngGridReferenceString(Point point, int referenceLength) {

        final Point innerPoint = convertToBngIfNeeded(point);

        if(!BngUtil.isInBngBounds(innerPoint)) {
            throw new IllegalArgumentException("Point is outside BNG grid");
        }

        int e = (int)innerPoint.getX();
        int n = (int)innerPoint.getY();
        int digits = referenceLength / 2;
        if (digits < 0) {
            return e + "," + n;
        }

        String ret = "";

        // 	The following code doesn't correctly handle e<0 or n<0 due to problems with / and %.
        int big = 500000;
        int small = big / 5;
        int firstdig = small / 10;

        int es = e / big;
        int ns = n / big;
        e = e % big;
        n = n % big;
        // move to the S square
        es += 2;
        ns += 1;
        if (es > 4 || ns > 4) {
            return null;
        }
        ret = ret + NATGRID_LETTERS[ns].charAt(es);

        es = e / small;
        ns = n / small;
        e = e % small;
        n = n % small;
        ret = ret + NATGRID_LETTERS[ns].charAt(es);

        // Only add spaces if there are digits too. This lets us have "zero-figure" grid references, e.g. "SK"
        if (digits > 0) {
            ret += ' ';

            for (int dig = firstdig, i = 0; dig != 0 && i < digits; i++, dig /= 10) {
                ret += (e / dig % 10);
            }

            ret += ' ';

            for (int dig = firstdig, i = 0; dig != 0 && i < digits; i++, dig /= 10) {
                ret += (n / dig % 10);
            }
        }

        return ret;
    }

    private static Point clampToBngBounds(double xIn, double yIn) {
        double x = Math.max(0, xIn);
        double y = Math.max(0, yIn);
        x = Math.min(GRID_WIDTH, x);
        y = Math.min(GRID_HEIGHT, y);
        return new Point(x, y, Point.BNG);
    }

    private static Point convertToBngIfNeeded(Point point) {
        if (!point.isBng()) {
            return new BasicMapProjection().toBng(point);
        }
        return point;
    }
}
