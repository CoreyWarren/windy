// SPDX-License-Identifier: AGPL-3.0-or-later
package net.pgaskin.windy;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Shake and tilt from the accelerometer. It only listens while the wallpaper
 * is visible (the renderer turns it off whenever it pauses), at a low rate.
 */
final class Interaction implements SensorEventListener {
    private static final int SAMPLING_US = 66_000; // ~15 Hz, plenty for shakes
    private static final float GRAVITY_SMOOTHING = 0.85f; // low-pass to separate gravity from shakes

    private final SensorManager sensors;
    private final Sensor accelerometer;
    private boolean listening;

    // guarded by this
    private final float[] gravity = {0, SensorManager.GRAVITY_EARTH, 0};
    private float sensitivity = 0.5f;
    private float energy; // 0 to 1, from the last shake, decaying
    private long lastStep;

    Interaction(Context context) {
        sensors = context.getSystemService(SensorManager.class);
        accelerometer = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
    }

    /** Starts or stops listening (call with false whenever the wallpaper isn't visible). */
    void setListening(boolean listen) {
        if (accelerometer == null || listen == listening) {
            return;
        }
        if (listen) {
            sensors.registerListener(this, accelerometer, SAMPLING_US);
        } else {
            sensors.unregisterListener(this);
            synchronized (this) {
                energy = 0;
            }
        }
        listening = listen;
    }

    synchronized void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }

    /** Decays the shake energy (over calm seconds) and returns it. */
    synchronized float step(float calm) {
        final long now = System.nanoTime();
        final float dt = lastStep == 0 ? 0 : Math.min((now - lastStep) / 1e9f, 1f);
        lastStep = now;
        energy *= (float) Math.exp(-dt / Math.max(calm, 0.1f));
        if (energy < 0.01f) {
            energy = 0;
        }
        return energy;
    }

    /** Which way is down on the screen (x right, y down), about unit length when upright. */
    synchronized float[] down() {
        // the accelerometer reads the opposite of gravity, and the screen's y
        // points the other way from the sensor's
        return new float[]{
                -gravity[0] / SensorManager.GRAVITY_EARTH,
                gravity[1] / SensorManager.GRAVITY_EARTH,
        };
    }

    @Override
    public synchronized void onSensorChanged(SensorEvent event) {
        float shake = 0;
        for (int i = 0; i < 3; i++) {
            gravity[i] = GRAVITY_SMOOTHING * gravity[i] + (1 - GRAVITY_SMOOTHING) * event.values[i];
            final float d = event.values[i] - gravity[i];
            shake += d * d;
        }
        shake = (float) Math.sqrt(shake); // m/s^2 beyond gravity
        final float threshold = 14f - 11.5f * sensitivity; // 14 (hard) to 2.5 (gentle)
        if (shake > threshold) {
            energy = Math.max(energy, Math.min(1f, 0.3f + (shake - threshold) / 10f));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
