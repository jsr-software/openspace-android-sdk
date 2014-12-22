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

import uk.co.ordnancesurvey.osmobilesdk.raster.geometry.BngUtil;
import uk.co.ordnancesurvey.osmobilesdk.raster.geometry.BoundingBox;
import uk.co.ordnancesurvey.osmobilesdk.raster.geometry.Point;

final class ScreenProjection {
    private final int mScreenWidth;
    private final int mScreenHeight;

    private final Point mCentre;
    private final float mMetresPerPixel;
    private final BoundingBox mVisibleBounds;

    ScreenProjection(int width, int height, MapScrollController.ScrollPosition scrollpos) {
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

    public PointF toScreenLocation(Point gp) {
        return toScreenLocation(gp, new PointF());
    }

    public PointF toScreenLocation(Point gp, PointF pointOut) {
        float metresPerPixel = mMetresPerPixel;

        pointOut.x = mScreenWidth / 2.0f + (float) (gp.getX() - mCentre.getX()) / metresPerPixel;
        pointOut.y = mScreenHeight / 2.0f - (float) (gp.getY() - mCentre.getY()) / metresPerPixel;
        return pointOut;
    }

    public Point fromScreenLocation(PointF point) {
        return fromScreenLocation(point.x, point.y);
    }

    public Point fromScreenLocation(float x, float y) {
        float metresPerPixel = mMetresPerPixel;

        double mapx = mCentre.getX() + (x - mScreenWidth / 2.0f) * metresPerPixel;
        double mapy = mCentre.getY() + (mScreenHeight / 2.0f - y) * metresPerPixel;
        return new Point(mapx, mapy, Point.BNG);
    }

    BoundingBox getVisibleBounds() {
        return mVisibleBounds;
    }

    BoundingBox getVisibleBoundsWithScreenInsets(float insetx, float insety) {
        float mpp = mMetresPerPixel;
        return mVisibleBounds.inset(insetx * mpp, insety * mpp);
    }

    BoundingBox getExpandedVisibleBounds() {
        return getVisibleBoundsWithScreenInsets(-mScreenWidth / 2.0f, -mScreenHeight / 2.0f);
    }

    Point getCenter() {
        return mCentre;
    }

    PointF displayPointFromPoint(Point gp, PointF displayPointOut) {
        double mapCenterX = mCentre.getX();
        double mapCenterY = mCentre.getY();
        float metresPerPixel = mMetresPerPixel;

        float xPixels = (float) (gp.getX() - mapCenterX) / metresPerPixel;
        float yPixels = (float) (gp.getY() - mapCenterY) / metresPerPixel;

        displayPointOut.x = xPixels;
        displayPointOut.y = yPixels;
        return displayPointOut;
    }

    float getMetresPerPixel() {
        return mMetresPerPixel;
    }
}
