package uk.co.ordnancesurvey.osmobilesdk.raster.gesture;

public interface MapGestureListener {
    void onLongPress(float screenX, float screenY);
    void onSingleTap(float screenX, float screenY);
    void onTouch(float screenX, float screenY);
    //void onTap();
    //void onLongPress();
    //void onDoubleTap();
    //void onPinch();
    //void onRotate();
    //void onDoubleTapAndHold();
    //void onPan();
    //void onFling();
}
