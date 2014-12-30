package uk.co.ordnancesurvey.osmobilesdk.raster.gesture;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class MapGestureDetector {

    private final MapGestureListener mMapGestureListener;
    private final GestureDetector mGestureDetector;
    private final GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent event) {
            if(mMapGestureListener != null) {
                mMapGestureListener.onTouch(event.getX(), event.getY());
            }
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
    };


    public MapGestureDetector(Context context, MapGestureListener mapGestureListener) {
        mGestureDetector = new GestureDetector(context, mGestureListener);
        mMapGestureListener = mapGestureListener;

    }

    public boolean onTouch(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }
}
