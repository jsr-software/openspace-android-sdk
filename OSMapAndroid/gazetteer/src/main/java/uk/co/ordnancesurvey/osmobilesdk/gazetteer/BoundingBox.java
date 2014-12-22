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
package uk.co.ordnancesurvey.osmobilesdk.gazetteer;

import java.io.Serializable;

public class BoundingBox implements Serializable {

    private static final long serialVersionUID = 4040767301261100823L;
    private static final BoundingBox NULL_BOUNDING_BOX = new BoundingBox(Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

    private double mMinX;
    private double mMinY;
    private double mMaxX;
    private double mMaxY;

    public BoundingBox(double minX, double minY, double maxX, double maxY) {
        mMinX = minX;
        mMinY = minY;
        mMaxX = maxX;
        mMaxY = maxY;
    }

    public double getHeight() {
        return mMaxY - mMinY;
    }

    public double getMinX() {
        return mMinX;
    }

    public double getMinY() {
        return mMinY;
    }

    public double getMaxX() {
        return mMaxX;
    }

    public double getMaxY() {
        return mMaxY;
    }

    public double getWidth() {
        return mMaxX - mMinX;
    }

    @Override
    public String toString() {
        return String.valueOf(mMinX) + String.valueOf(mMinY);
    }

    public static BoundingBox fromCentreXYWH(double cX, double cY, double w, double h) {
        double x0 = cX-w/2;
        double y0 = cY-h/2;
        double x1 = cX+w/2;
        double y1 = cY+h/2;
        return new BoundingBox(x0, y0, x1, y1);
    }

    public static boolean isValidBoundingBox(double maxX, double maxY, double minX, double minY) {
        return maxX != 0 && maxY != 0 && minX != 0 && minY != 0;
    }

    public boolean contains(Point point) {
        boolean result = false;

        double easting = point.getX();
        double northing = point.getY();

        if ((easting >= mMinX) && (easting <= mMaxX)) {
            if ((northing >= mMinY) && (northing <= mMaxY)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Check if the region defined by <code>other</code>
     * overlaps (intersects) the region of this <code>Envelope</code>.
     *
     * @param other the <code>BoundingBox</code> which this <code>BoundingBox</code> is
     *              being checked for overlapping
     * @return <code>true</code> if the <code>BoungdingBox</code>s overlap
     */
    public boolean intersects(BoundingBox other) {
        if (other == null) {
            return false;
        }
        return !(other.getMinX() > mMaxX || other.getMaxX() < mMinX || other.getMinY() > mMaxY
                || other.getMaxY() < mMinY);
    }

    // Assumes normalized rects.
    public BoundingBox inset(double dx, double dy) {
        double x0 = mMinX + dx;
        double x1 = mMaxX - dx;
        double y0 = mMinY + dy;
        double y1 = mMaxY - dy;
        if (x1 > x0 && y1 > y0) {
            return new BoundingBox(x0, y0, x1, y1);
        }
        return NULL_BOUNDING_BOX;
    }

    public BoundingBox intersect(BoundingBox boundingBox) {
        double x0 = Math.max(mMinX, boundingBox.getMinX());
        double x1 = Math.min(mMaxX, boundingBox.getMaxX());
        double y0 = Math.max(mMinY, boundingBox.getMinY());
        double y1 = Math.min(mMaxY, boundingBox.getMaxY());
        if (x1 > x0 && y1 > y0) {
            return new BoundingBox(x0, y0, x1, y1);
        }
        return NULL_BOUNDING_BOX;
    }

    boolean isNull() {
        return Double.isInfinite(mMinX) || Double.isInfinite(mMinY);
    }

    @Override
    public int hashCode() {
        // CS:OFF MagicNumber FOR 6 LINES
        //Algorithm from Effective Java by Joshua Bloch [Jon Aquino]
        int result = 17;
        result = 37 * result + hashCode(mMinX);
        result = 37 * result + hashCode(mMaxX);
        result = 37 * result + hashCode(mMinY);
        result = 37 * result + hashCode(mMaxY);
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof BoundingBox)) {
            return false;
        }
        BoundingBox boundingBox = (BoundingBox) other;

        return mMaxX == boundingBox.getMaxX()
                && mMaxY == boundingBox.getMaxY()
                && mMinX == boundingBox.getMinX()
                && mMinY == boundingBox.getMinY();
    }

    /**
     * Returns a hash code for a double value, using the algorithm from
     * Joshua Bloch's book <i>Effective Java"</i>
     */
    public static int hashCode(double x) {
        // CS:OFF MagicNumber FOR 2 LINES
        long f = Double.doubleToLongBits(x);
        return (int) (f ^ (f >>> 32));
    }
}

