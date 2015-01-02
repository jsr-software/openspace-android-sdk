package uk.co.ordnancesurvey.osmobilesdk.locations;

import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

public class AzimuthServiceImpl implements AzimuthService {

    private static final float ALPHA = 0.08f;
    private static final int DATA_SIZE = 16;
    private static final int ORIENTATION_SIZE = 3;
    private static final double OFFSET = 360;

    private final Context mContext;
    private final SensorManager mSensorManager;
    private final Sensor mAccSensor;
    private final Sensor mMagSensor;
    private final Map<Subscriber<Float>, Subscription> mSubscriptions
            = new HashMap<Subscriber<Float>, Subscription>();

    private float[] mAccData;
    private float[] mMagData;

    @Inject
    public AzimuthServiceImpl(Context context, SensorManager sensorManager) {
        mContext = context;
        mSensorManager = sensorManager;
        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    @Override
    public void subscribeToAzimuthChanges(final Subscriber<Float> subscriber) {

        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float azimuth = getAzimuth(event);
                subscriber.onNext(azimuth);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        Subscription subscription = Observable
                .create(new Observable.OnSubscribe<Float>() {
                    @Override
                    public void call(Subscriber<? super Float> subscriber) {
                        mSensorManager.registerListener(listener,
                                mMagSensor, SensorManager.SENSOR_DELAY_UI);
                        mSensorManager.registerListener(listener,
                                mAccSensor, SensorManager.SENSOR_DELAY_UI);
                    }
                }).doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        mSensorManager.unregisterListener(listener);
                    }
                })
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(subscriber);

        mSubscriptions.put(subscriber, subscription);
    }

    @Override
    public void unsubscribeFromAzimuthChanges(Subscriber<Float> subscriber) {
        Subscription subscription = mSubscriptions.get(subscriber);
        subscription.unsubscribe();
    }

    private float calculateAzimuthRadians() {
        float[] rotation = new float[DATA_SIZE];
        float[] rotated = new float[DATA_SIZE];

        if (SensorManager.getRotationMatrix(rotation, null, mAccData, mMagData)) {
            int[] axes = getScreenAxes();
            SensorManager.remapCoordinateSystem(rotation, axes[0], axes[1], rotated);
            float[] orientation = new float[ORIENTATION_SIZE];
            SensorManager.getOrientation(rotated, orientation);

            return orientation[0];
        }
        return 0f;
    }

    private Float getAzimuth(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagData = lowPass(event.values.clone(), mMagData);
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccData = lowPass(event.values.clone(), mAccData);
        }

        boolean hasData = mAccData != null && mMagData != null;
        if (hasData) {
            float radians = calculateAzimuthRadians();
            return radiansToDegrees(radians);
        }
        return 0f;
    }

    private int[] getPhoneAxes(int rotation) {
        int[] result;
        switch (rotation) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                result = new int[]{SensorManager.AXIS_X, SensorManager.AXIS_Z};
                break;
            case Surface.ROTATION_180:
                result = new int[]{SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z};
                break;
            case Surface.ROTATION_0:
            default:
                result = new int[]{SensorManager.AXIS_X, SensorManager.AXIS_Y};
                break;
        }
        return result;
    }

    private int[] getScreenAxes() {
        Display display = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();

        final boolean isTablet = isTablet();
        final int rotation = display.getRotation();

        return isTablet ? getTabletAxes(rotation) : getPhoneAxes(rotation);
    }

    private int[] getTabletAxes(int rotation) {
        int[] result;
        switch (rotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_90:
                result = new int[]{SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Z};
                break;
            case Surface.ROTATION_180:
                result = new int[]{SensorManager.AXIS_X, SensorManager.AXIS_Z};
                break;
            case Surface.ROTATION_270:
            default:
                result = new int[]{SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_Z};
                break;
        }
        return result;
    }

    private boolean isTablet() {
        return (mContext.getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_MASK) >=
                Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    /**
     * Note: the low pass dampening filter is further explained in the SensorEvent API documentation
     * https://developer.android.com/reference/android/hardware/SensorEvent.html
     */
    private float[] lowPass(float[] newValues, float[] oldValues) {
        if (oldValues == null) {
            return newValues;
        }

        for (int i = 0; i < newValues.length; i++) {
            oldValues[i] = oldValues[i] + ALPHA * (newValues[i] - oldValues[i]);
        }

        return oldValues;
    }

    private float radiansToDegrees(double radians) {
        double raw = Math.toDegrees(radians);
        if (raw < 0) {
            raw = raw + OFFSET;
        }
        return (float) raw;
    }
}
