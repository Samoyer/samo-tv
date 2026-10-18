package cn.samo.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Offline cinematic coast. Only a short focus tint animates; the photograph stays still. */
public final class AmbientView extends View {
    private static final int BASE = 0xFF071321;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix crop = new Matrix();
    private final Bitmap wallpaper;
    private final BitmapShader photograph;
    private int fromColor = muted(0xFF6099BA);
    private int accent = fromColor;
    private float transition = 1f;
    private Shader fromGlow;
    private Shader accentGlow;
    private Shader fallback;
    private Shader leftShade;
    private Shader topShade;
    private Shader bottomShade;
    private ValueAnimator animator;
    private boolean dimmed;

    public AmbientView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        wallpaper = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_coast, options);
        photograph = wallpaper == null ? null
                : new BitmapShader(wallpaper, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    public void setAccent(int color) {
        int next = muted(color);
        if (next == accent) return;
        int visibleColor = mix(fromColor, accent, transition);
        stopAnimator();
        fromColor = visibleColor;
        accent = next;
        transition = 1f;
        rebuildAccentGlows();
        if (isAttachedToWindow() && getWidth() > 0 && animationsEnabled()) {
            transition = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(180L);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(value -> {
                transition = (float) value.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
        invalidate();
    }

    /** A fixed legibility scrim for apps and settings; the home photograph stays open. */
    public void setDimmed(boolean value) {
        if (dimmed == value) return;
        dimmed = value;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        if (photograph != null) {
            float scale = Math.max((float) w / wallpaper.getWidth(),
                    (float) h / wallpaper.getHeight());
            crop.setScale(scale, scale);
            crop.postTranslate((w - wallpaper.getWidth() * scale) * .5f,
                    (h - wallpaper.getHeight() * scale) * .5f);
            photograph.setLocalMatrix(crop);
        }
        fallback = new LinearGradient(0f, 0f, w, h,
                new int[]{0xFF15263C, BASE, 0xFF162B40},
                new float[]{0f, .58f, 1f}, Shader.TileMode.CLAMP);
        leftShade = new LinearGradient(0f, 0f, w, 0f,
                new int[]{0x5C030B17, 0x20030B17, 0x00030B17},
                new float[]{0f, .46f, 1f}, Shader.TileMode.CLAMP);
        topShade = new LinearGradient(0f, 0f, 0f, h * .45f,
                new int[]{0x5C020915, 0x16020915, 0x00020915},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP);
        bottomShade = new LinearGradient(0f, h * .30f, 0f, h,
                new int[]{0x00040C18, 0x30040C18, 0xA8040C18},
                new float[]{0f, .44f, 1f}, Shader.TileMode.CLAMP);
        rebuildAccentGlows();
    }

    private void rebuildAccentGlows() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        fromGlow = glow(fromColor);
        accentGlow = glow(accent);
    }

    private Shader glow(int color) {
        int rgb = color & 0x00FFFFFF;
        return ellipticalGlow(getWidth() * .69f, getHeight() * .72f,
                getWidth() * .52f, .58f,
                new int[]{0x10000000 | rgb, 0x06000000 | rgb, rgb},
                new float[]{0f, .42f, 1f});
    }

    private static Shader ellipticalGlow(float x, float y, float radius, float heightScale,
            int[] colors, float[] positions) {
        RadialGradient gradient = new RadialGradient(x, y, radius, colors, positions,
                Shader.TileMode.CLAMP);
        Matrix shape = new Matrix();
        shape.setScale(1f, heightScale, x, y);
        gradient.setLocalMatrix(shape);
        return gradient;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BASE);
        drawLayer(canvas, photograph == null ? fallback : photograph, 255);
        // Static contrast scrims protect white type above the ice and dusk sky.
        drawLayer(canvas, leftShade, 255);
        drawLayer(canvas, topShade, 255);
        if (transition < 1f) drawLayer(canvas, fromGlow, Math.round(255f * (1f - transition)));
        drawLayer(canvas, accentGlow, Math.round(255f * transition));
        drawLayer(canvas, bottomShade, 255);
        if (dimmed) canvas.drawColor(0x55030B17);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void drawLayer(Canvas canvas, Shader shader, int alpha) {
        if (shader == null || alpha <= 0) return;
        paint.setShader(shader);
        paint.setAlpha(alpha);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
    }

    private boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private void stopAnimator() {
        if (animator == null) return;
        animator.removeAllUpdateListeners();
        animator.cancel();
        animator = null;
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimator();
        transition = 1f;
        super.onDetachedFromWindow();
    }

    private static int muted(int color) {
        int luminance = Math.round(Color.red(color) * .2126f
                + Color.green(color) * .7152f + Color.blue(color) * .0722f);
        int gray = Color.rgb(luminance, luminance, luminance);
        int desaturated = mix(gray,
                Color.rgb(Color.red(color), Color.green(color), Color.blue(color)), .18f);
        return mix(0xFF879DB6, desaturated, .30f);
    }

    private static int mix(int first, int second, float fraction) {
        return Color.rgb(Math.round(Color.red(first) + (Color.red(second) - Color.red(first)) * fraction),
                Math.round(Color.green(first) + (Color.green(second) - Color.green(first)) * fraction),
                Math.round(Color.blue(first) + (Color.blue(second) - Color.blue(first)) * fraction));
    }
}
