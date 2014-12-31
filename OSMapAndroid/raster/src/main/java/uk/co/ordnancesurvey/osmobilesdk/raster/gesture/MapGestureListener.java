package uk.co.ordnancesurvey.osmobilesdk.raster.gesture;

public interface MapGestureListener {
    void onDoubleTap(float screenX, float screenY);
    void onFling(float velocityX, float velocityY);
    void onLongPress(float screenX, float screenY);
    void onPan(float distanceX, float distanceY);
    void onSingleTap(float screenX, float screenY);
    void onTouch(float screenX, float screenY);
    //void onPinch();
    //void onRotate();
    //void onDoubleTapAndHold();
}
