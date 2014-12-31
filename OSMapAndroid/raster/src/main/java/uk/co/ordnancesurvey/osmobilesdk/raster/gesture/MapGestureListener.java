package uk.co.ordnancesurvey.osmobilesdk.raster.gesture;

public interface MapGestureListener {
    void onDoubleTap(float screenX, float screenY);
    void onLongPress(float screenX, float screenY);
    void onSingleTap(float screenX, float screenY);
    void onTouch(float screenX, float screenY);
    //void onPinch();
    //void onRotate();
    //void onDoubleTapAndHold();
    //void onPan();
    //void onFling();
}
