package uk.co.ordnancesurvey.osmobilesdk.raster.gesture;

public interface MapGestureListener {
    void onDoubleTap(float screenX, float screenY);
    void onFling(float velocityX, float velocityY);
    void onLongPress(float screenX, float screenY);
    void onPan(float distanceX, float distanceY);
    void onPinch(float focusX, float focusY, float focusChangeX, float focusChangeY, float scale);
    void onSingleTap(float screenX, float screenY);
    void onTouch(float screenX, float screenY);
    void onTwoFingerTap();
    //void onRotate();
    //void onDoubleTapAndHold();
}
