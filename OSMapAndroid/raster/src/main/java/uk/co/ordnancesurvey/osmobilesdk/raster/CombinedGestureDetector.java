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

import android.content.Context;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import uk.co.ordnancesurvey.osmobilesdk.raster.gesture.MapGestureListener;

abstract class CombinedGestureDetector extends GestureDetector.SimpleOnGestureListener
        implements ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {

    public interface DragListener {
        public Object onDragBegin(MotionEvent e);

        public void onDrag(MotionEvent e, Object dragObject);

        public void onDragEnd(MotionEvent e, Object dragObject);

        public void onDragCancel(MotionEvent e, Object dragObject);
    }

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;
    private final MapGestureListener mMapGestureListener;
    private final float mTouchSlopSq;

    private boolean consumingScaleEvents;

    private View mCurrentView;
    private MotionEvent mCurrentEvent;
    private boolean mCurrentEventCalledOnScaleBegin;

    private boolean mTwoFingerTapPossible;
    private boolean mScaleAlreadyHasTouchSlop;

    private final DragListener mDragListener;
    private boolean mDragStarted;
    private float mDragInitialX, mDragInitialY;
    private Object mDragObject;

    private float mScaleInitialSpan;
    private float mPrevScaleFocusX;
    private float mPrevScaleFocusY;
    private boolean mScaleStarted;

    public CombinedGestureDetector(Context context, DragListener dragListener, MapGestureListener mapGestureListener) {
        mGestureDetector = new GestureDetector(context, this);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);

        mGestureDetector.setIsLongpressEnabled(true);
        mGestureDetector.setOnDoubleTapListener(this);

        float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTouchSlopSq = touchSlop * touchSlop;

        mDragListener = dragListener;
        mMapGestureListener = mapGestureListener;
    }

    /**
     * Combined scroll/zoom/fling method.
     */
    protected abstract void onScroll(float dx, float dy, float dScale, float scaleOffsetX, float scaleOffsetY, float flingVX, float flingVY, long eventTime);

    protected abstract void onTwoFingerTap();

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        mCurrentView = v;
        if (BuildConfig.DEBUG) {
            // Only save this on debug builds. It's potentially dangerous, since the event can get recycled!
            mCurrentEvent = e;
        }
        boolean consumedGestureEvent = mGestureDetector.onTouchEvent(e);
        mCurrentEventCalledOnScaleBegin = false;
        if (consumingScaleEvents) {
            consumingScaleEvents = mScaleGestureDetector.onTouchEvent(e);
        }
        mCurrentView = null;
        mCurrentEvent = null;

        int action = e.getActionMasked();
        boolean actionIsSecondaryDown = (action == MotionEvent.ACTION_POINTER_DOWN);
        boolean actionIsFinalUp = (action == MotionEvent.ACTION_UP);
        boolean actionIsCancel = action == MotionEvent.ACTION_CANCEL;
        assert !actionIsFinalUp || e.getPointerCount() == 1 : "actionIsFinalUp should imply only one pointer";

        if (actionIsSecondaryDown && e.getPointerCount() == 2) {
            // OS-44: On some Android versions, ScaleGestureDetector already applies touch slop.
            // We detect this by the lack of an onScaleBegin() callback on a secondary touch down.
            // On such a device, set mTwoFingerTapPossible here and clear it on onScaleBegin().
            boolean gotScaleBegin = mCurrentEventCalledOnScaleBegin;
            mScaleAlreadyHasTouchSlop = !gotScaleBegin;
            if (!gotScaleBegin) {
                mTwoFingerTapPossible = true;
            }
        }

        boolean twoFingerTap = !isDraggingItem() && mTwoFingerTapPossible && actionIsFinalUp && e.getEventTime() - e.getDownTime() < ViewConfiguration.getDoubleTapTimeout();
        //Log.v(TAG, String.format(Locale.ENGLISH, "%d isd=%b ifg=%b ttp=%b ifu=%b tt=%b", action, actionIsSecondaryDown, mIgnoreFurtherGestures, mTwoFingerTapPossible, actionIsFinalUp, twoFingerTap));
        if (twoFingerTap) {
            // TODO: This should cancel other gestures (e.g. double tap).
            mTwoFingerTapPossible = false;
            onTwoFingerTap();
        }

        boolean dragging = (mDragObject != null);
        if (dragging) {
            if (actionIsFinalUp) {
                mDragListener.onDragEnd(e, mDragObject);
                mDragObject = null;
            } else if (actionIsCancel) {
                mDragListener.onDragCancel(e, mDragObject);
                mDragObject = null;
            } else {
                if (!mDragStarted) {
                    float dx = e.getX() - mDragInitialX;
                    float dy = e.getY() - mDragInitialY;
                    if (dx * dx + dy * dy > mTouchSlopSq) {
                        mDragStarted = true;
                    }
                }
                if (mDragStarted) {
                    mDragListener.onDrag(e, mDragObject);
                }
            }
        }

        return consumingScaleEvents || consumedGestureEvent || twoFingerTap;
    }


    @Override
    public boolean onDown(MotionEvent event) {
        if (isDraggingItem()) {
            return false;
        }
        // On the initial touch down,
        //  - Start consuming scale events.
        //  - Cancel any pending two-finger tap.
        //  - Cancel any fling.
        consumingScaleEvents = true;
        mTwoFingerTapPossible = false;
        //CombinedGestureDetector.this.onScroll(0, 0, 1, 0, 0, 0, 0, event.getEventTime());
        if (mMapGestureListener != null) {
            mMapGestureListener.onTouch(event.getX(), event.getY());
        }
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (isDraggingItem()) {
            return false;
        }
        assert !mScaleStarted;
        CombinedGestureDetector.this.onScroll(0, 0, 1, 0, 0, -velocityX, -velocityY, e2.getEventTime());
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (isDraggingItem()) {
            return false;
        }
        if (mScaleStarted) {
            // This appears to happen since Android 4.1. Observed on
            //   Galaxy S III (GT-I9300, Android 4.1.2).
            //   Nexus 7 (Android 4.2.x)
            // Not observed on
            //   HTC One (Android 4.0.x)
            //   Galaxy S II (Android 4.0.x)
            if (BuildConfig.DEBUG) {
                assert Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
                //DebugHelpers.logAndroidBuild();
            }
            // Don't scroll; it's already handled by onScale().
            return false;
        }
        CombinedGestureDetector.this.onScroll(distanceX, distanceY, 1, 0, 0, 0, 0, e2.getEventTime());
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mCurrentEventCalledOnScaleBegin = true;
        if (isDraggingItem()) {
            return false;
        }
        mScaleStarted = true;

        // OS-44: On some Android versions, ScaleGestureDetector already applies touch slop.
        if (BuildConfig.DEBUG) {
            // On such devices, onScaleBegin() should happen on ACTION_MOVE, not ACTION_POINTER_DOWN
            assert mScaleAlreadyHasTouchSlop == (mCurrentEvent.getActionMasked() == MotionEvent.ACTION_MOVE);
        }
        // If ScaleGestureDetector already applies touch slop, then we must have exceeded the threshold. Two-finger tap is no longer possible.
        mTwoFingerTapPossible = (mScaleAlreadyHasTouchSlop ? false : true);

        float x = detector.getFocusX();
        float y = detector.getFocusY();
        mPrevScaleFocusX = x;
        mPrevScaleFocusY = y;
        mScaleInitialSpan = detector.getCurrentSpan();
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (isDraggingItem()) {
            return false;
        }
        float x = detector.getFocusX();
        float y = detector.getFocusY();
        float dX = x - mPrevScaleFocusX;
        float dY = y - mPrevScaleFocusY;
        float dScale;
        if (mTwoFingerTapPossible) {
            float currentSpan = detector.getCurrentSpan();
            float dSpan = (currentSpan - mScaleInitialSpan);
            float dSq = dSpan * dSpan + dX * dX + dY * dY;
            if (dSq < mTouchSlopSq) {
                return false;
            }

            mTwoFingerTapPossible = false;
            dScale = currentSpan / mScaleInitialSpan;
        } else {
            dScale = detector.getScaleFactor();
        }
        mPrevScaleFocusX = x;
        mPrevScaleFocusY = y;

        View v = mCurrentView;
        float scaleOffsetX = x - v.getWidth() / 2;
        float scaleOffsetY = y - v.getHeight() / 2;

        CombinedGestureDetector.this.onScroll(-dX, -dY, dScale, scaleOffsetX, scaleOffsetY, 0, 0, detector.getEventTime());
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mScaleStarted = false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        if (isDraggingItem()) {
            return false;
        }
        if (mMapGestureListener != null) {
            mMapGestureListener.onSingleTap(event.getX(), event.getY());
        }
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
        if (isDraggingItem()) {
            return false;
        }
        if (mMapGestureListener != null) {
            mMapGestureListener.onDoubleTap(event.getX(), event.getY());
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
        if (isDraggingItem()) {
            return;
        }
        // Drag code
        Object dragObject = mDragListener.onDragBegin(event);
        mDragObject = dragObject;
        mDragStarted = false;
        mDragInitialX = event.getX();
        mDragInitialY = event.getY();
        // End Drag code

        if (mMapGestureListener != null) {
            mMapGestureListener.onLongPress(event.getX(), event.getY());
        }
    }

    // New stuff
    private boolean isDraggingItem() {
        return mDragObject != null;
    }
}

