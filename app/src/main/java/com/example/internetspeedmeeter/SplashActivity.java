package com.example.internetspeedmeeter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Splash screen shown on cold launch (when SpeedService is NOT already running).
 * If the service is already in background, goes directly to MainActivity.
 *
 * Animation sequence:
 *  0ms  — glow circle fades in
 *  150ms — icon scales in with overshoot
 *  400ms — app title slides up and fades in
 *  600ms — accent line scales in from center
 *  750ms — tagline fades in
 *  950ms — "Developed by Jaiswal jii" fades in
 * 2700ms — fade out whole screen → launch MainActivity
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION_MS = 2700;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If service is already running (app in background), skip splash
        if (isServiceRunning()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_splash);

        View     root        = findViewById(R.id.splashRoot);
        View     glow        = findViewById(R.id.glowCircle);
        TextView icon        = findViewById(R.id.splashIcon);
        TextView title       = findViewById(R.id.splashTitle);
        View     line        = findViewById(R.id.accentLine);
        TextView tagline     = findViewById(R.id.splashTagline);
        TextView developer   = findViewById(R.id.splashDeveloper);

        runAnimation(root, glow, icon, title, line, tagline, developer);
    }

    private void runAnimation(View root, View glow, TextView icon,
                              TextView title, View line,
                              TextView tagline, TextView developer) {

        // --- 1. Glow fade in ---
        ObjectAnimator glowFade = ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 1f);
        glowFade.setDuration(500);
        glowFade.setInterpolator(new DecelerateInterpolator());

        // Glow pulse loop
        ObjectAnimator glowPulse = ObjectAnimator.ofFloat(glow, View.ALPHA, 1f, 0.5f);
        glowPulse.setDuration(900);
        glowPulse.setRepeatMode(ValueAnimator.REVERSE);
        glowPulse.setRepeatCount(ValueAnimator.INFINITE);
        glowPulse.setInterpolator(new AccelerateDecelerateInterpolator());

        // --- 2. Icon scale + fade ---
        ObjectAnimator iconScaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.4f, 1f);
        ObjectAnimator iconScaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.4f, 1f);
        ObjectAnimator iconAlpha  = ObjectAnimator.ofFloat(icon, View.ALPHA, 0f, 1f);
        iconScaleX.setDuration(450);
        iconScaleY.setDuration(450);
        iconAlpha.setDuration(350);
        iconScaleX.setInterpolator(new OvershootInterpolator(1.8f));
        iconScaleY.setInterpolator(new OvershootInterpolator(1.8f));

        // --- 3. Title slide up + fade ---
        ObjectAnimator titleTransY = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 40f, 0f);
        ObjectAnimator titleAlpha  = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f);
        titleTransY.setDuration(380);
        titleAlpha.setDuration(380);
        titleTransY.setInterpolator(new DecelerateInterpolator());

        // --- 4. Accent line scale from center ---
        ObjectAnimator lineScale = ObjectAnimator.ofFloat(line, View.SCALE_X, 0f, 1f);
        ObjectAnimator lineAlpha = ObjectAnimator.ofFloat(line, View.ALPHA, 0f, 1f);
        lineScale.setDuration(300);
        lineAlpha.setDuration(250);
        lineScale.setInterpolator(new AccelerateDecelerateInterpolator());

        // --- 5. Tagline fade ---
        ObjectAnimator tagAlpha = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f);
        tagAlpha.setDuration(350);

        // --- 6. Developer credit ---
        ObjectAnimator devAlpha = ObjectAnimator.ofFloat(developer, View.ALPHA, 0f, 1f);
        devAlpha.setDuration(500);

        // --- Sequence ---
        AnimatorSet set = new AnimatorSet();
        set.play(glowFade).before(iconAlpha);
        set.play(iconScaleX).with(iconScaleY).with(iconAlpha).after(150);
        set.play(titleTransY).with(titleAlpha).after(iconAlpha).after(250);
        set.play(lineScale).with(lineAlpha).after(titleTransY).after(50);
        set.play(tagAlpha).after(lineScale).after(50);
        set.play(devAlpha).after(tagAlpha).after(150);

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Start glow pulse after intro completes
                glowPulse.start();
            }
        });

        set.start();

        // --- Fade out and launch MainActivity ---
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            glowPulse.cancel();
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f);
            fadeOut.setDuration(400);
            fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());
            fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    goToMain();
                }
            });
            fadeOut.start();
        }, SPLASH_DURATION_MS);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        // No overridePendingTransition — the fade-out handles the transition
        finish();
    }

    /** Returns true if SpeedService is already running in the background. */
    @SuppressWarnings("deprecation")
    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return false;
        for (ActivityManager.RunningServiceInfo info
                : am.getRunningServices(Integer.MAX_VALUE)) {
            if (SpeedService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
