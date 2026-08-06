package com.alert.beacon.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class LoadingUtils {

    public static void startCustomAnimation(View loadingView) {
        View frame = loadingView.findViewById(com.alert.beacon.R.id.loading_frame);
        View dot1 = loadingView.findViewById(com.alert.beacon.R.id.dot1);
        View dot2 = loadingView.findViewById(com.alert.beacon.R.id.dot2);
        View dotCenter = loadingView.findViewById(com.alert.beacon.R.id.dot_center);

        if (frame == null || dot1 == null || dot2 == null || dotCenter == null) return;

        // Rotate the frame
        ObjectAnimator frameRotate = ObjectAnimator.ofFloat(frame, "rotation", 0f, 360f);
        frameRotate.setDuration(3000);
        frameRotate.setRepeatCount(ValueAnimator.INFINITE);
        frameRotate.setInterpolator(new LinearInterpolator());

        // Animate Dot 1 (Orbiting/Moving around)
        ValueAnimator dot1Anim = ValueAnimator.ofFloat(0f, 1f);
        dot1Anim.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            dot1.setTranslationX((float) Math.cos(val * 2 * Math.PI) * 40f);
            dot1.setTranslationY((float) Math.sin(val * 2 * Math.PI) * 40f);
        });
        dot1Anim.setDuration(2000);
        dot1Anim.setRepeatCount(ValueAnimator.INFINITE);
        dot1Anim.setInterpolator(new LinearInterpolator());

        // Animate Dot 2 (Inverse Orbit)
        ValueAnimator dot2Anim = ValueAnimator.ofFloat(0f, 1f);
        dot2Anim.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            dot2.setTranslationX((float) Math.sin(val * 2 * Math.PI) * 50f);
            dot2.setTranslationY((float) Math.cos(val * 2 * Math.PI) * 50f);
        });
        dot2Anim.setDuration(2500);
        dot2Anim.setRepeatCount(ValueAnimator.INFINITE);
        dot2Anim.setInterpolator(new LinearInterpolator());

        // Pulse Center Dot
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(dotCenter, "scaleX", 0.8f, 1.5f, 0.8f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(dotCenter, "scaleY", 0.8f, 1.5f, 0.8f);
        pulseX.setRepeatCount(ValueAnimator.INFINITE);
        pulseY.setRepeatCount(ValueAnimator.INFINITE);
        pulseX.setDuration(1500);
        pulseY.setDuration(1500);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(frameRotate, dot1Anim, dot2Anim, pulseX, pulseY);
        set.start();
        
        loadingView.setTag(set); // Store reference to stop later if needed
    }

    public static void stopCustomAnimation(View loadingView) {
        Object tag = loadingView.getTag();
        if (tag instanceof AnimatorSet) {
            ((AnimatorSet) tag).cancel();
        }
    }
}
