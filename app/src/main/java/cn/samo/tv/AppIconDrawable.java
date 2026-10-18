package cn.samo.tv;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import java.util.Arrays;

/**
 * Keeps an application's real artwork and extends its edge pixels over a TV tile.
 * The caller supplies full tile bounds and clips rounded corners. Use FIT_XY on an
 * ImageView: this drawable contains the source at its original aspect ratio internally.
 * Build once per app and copy through ConstantState for independent shader matrices.
 */
public final class AppIconDrawable extends Drawable {
    private static final int RASTER_SIZE = 384;
    private static final int FALLBACK_COLOR = 0xFF17202C;
    private static final int NO_COLOR = 0;
    private final IconState state;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint artworkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final BitmapShader shader;
    private final Matrix matrix = new Matrix();
    private final RectF destination = new RectF();
    private int alpha = 255;

    /** Rasterizes and prepares the source once; the center logo is never replaced. */
    public static AppIconDrawable create(Drawable source) {
        if (source == null) return null;
        int intrinsicWidth = source.getIntrinsicWidth();
        int intrinsicHeight = source.getIntrinsicHeight();
        int sourceWidth = intrinsicWidth > 0 ? intrinsicWidth : 256;
        int sourceHeight = intrinsicHeight > 0 ? intrinsicHeight : 256;
        float factor = RASTER_SIZE / (float) Math.max(sourceWidth, sourceHeight);
        int width = Math.max(1, Math.round(sourceWidth * factor));
        int height = Math.max(1, Math.round(sourceHeight * factor));
        Bitmap raster = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Drawable.ConstantState originalState = source.getConstantState();
        Drawable artwork = originalState != null ? originalState.newDrawable().mutate() : source;
        Rect originalBounds = new Rect(artwork.getBounds());
        try {
            artwork.setBounds(0, 0, width, height);
            artwork.draw(new Canvas(raster));
        } finally {
            artwork.setBounds(originalBounds);
        }
        int[] pixels = new int[width * height];
        raster.getPixels(pixels, 0, width, 0, 0, width, height);
        Rect content = visibleBounds(pixels, width, height);
        boolean plated = hasOuterPlate(pixels, width, content);
        // Transparent marks can have intentional breathing room. Only remove padding
        // outside a confirmed plate; preserve the whole original image otherwise.
        if (!plated) content = new Rect(0, 0, width, height);
        EdgeProfile edges = edgeProfile(pixels, width, content, plated);
        // Cache the original pixels separately: a TV badge touching an edge must never
        // be stretched into the outside fill. Background contains only two RGB columns.
        Bitmap bitmap = Bitmap.createBitmap(pixels, content.top * width + content.left, width,
                content.width(), content.height(), Bitmap.Config.ARGB_8888);
        int[] background = new int[content.height() * 2];
        for (int y = 0; y < content.height(); y++) {
            background[y * 2] = edges.left[y]; background[y * 2 + 1] = edges.right[y];
        }
        Bitmap backgroundBitmap = Bitmap.createBitmap(background, 2, content.height(),
                Bitmap.Config.ARGB_8888);
        raster.recycle();
        return new AppIconDrawable(new IconState(bitmap, backgroundBitmap, edges.representativeColor,
                intrinsicWidth > 0 ? intrinsicWidth : width,
                intrinsicHeight > 0 ? intrinsicHeight : height,
                source.getChangingConfigurations()));
    }

    private AppIconDrawable(IconState state) {
        this.state = state;
        // The cached bitmap is immutable/shared; shader and matrix must stay per copy.
        shader = new BitmapShader(state.background, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        backgroundPaint.setShader(shader);
    }

    /** Representative edge color for parent outlines; the rendered fill uses all rows. */
    public int getEdgeColor() { return state.edgeColor; }

    private static Rect visibleBounds(int[] pixels, int width, int height) {
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Color.alpha(pixels[y * width + x]) < 32) continue;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        if (right < left || bottom < top) return new Rect(0, 0, width, height);
        int rim = Math.max(1, Math.round(Math.min(width, height) * .015f));
        return new Rect(Math.max(0, left - rim), Math.max(0, top - rim),
                Math.min(width, right + 1 + rim), Math.min(height, bottom + 1 + rim));
    }

    /**
     * Opacity coverage distinguishes a background plate from an isolated transparent
     * glyph. Color uniformity is deliberately irrelevant: a real plate can be a gradient.
     */
    private static boolean hasOuterPlate(int[] pixels, int stride, Rect content) {
        final int steps = 32;
        final float[] insets = {.025f, .065f};
        int[] coverage = new int[4];
        for (float inset : insets) {
            for (int step = 0; step < steps; step++) {
                float across = .16f + .68f * step / (steps - 1f);
                for (int side = 0; side < 4; side++) {
                    float fx = side == 0 ? inset : side == 1 ? 1f - inset : across;
                    float fy = side == 2 ? inset : side == 3 ? 1f - inset : across;
                    int x = content.left + Math.round((content.width() - 1) * fx);
                    int y = content.top + Math.round((content.height() - 1) * fy);
                    if (Color.alpha(pixels[y * stride + x]) >= 236) coverage[side]++;
                }
            }
        }
        int sides = 0;
        for (int samples : coverage) if (samples >= steps / 2) sides++;
        return sides >= 3;
    }

    /**
     * Keep left and right colors separately for every scanline. Corner rows missing a
     * reliable opaque edge borrow the nearest valid row, rather than a global brand color.
     */
    private static EdgeProfile edgeProfile(int[] pixels, int stride, Rect content, boolean plated) {
        int height = content.height();
        int[] left = new int[height], right = new int[height];
        if (!plated) {
            Arrays.fill(left, FALLBACK_COLOR); Arrays.fill(right, FALLBACK_COLOR);
            return new EdgeProfile(left, right, FALLBACK_COLOR);
        }
        int band = Math.max(1, Math.round(content.width() * .15f));
        int red = 0, green = 0, blue = 0, samples = 0;
        for (int y = 0; y < height; y++) {
            int row = (content.top + y) * stride;
            for (int offset = 0; offset < band; offset++) {
                int color = pixels[row + content.left + offset];
                if (Color.alpha(color) >= 236) { left[y] = color | 0xFF000000; break; }
            }
            for (int offset = 0; offset < band; offset++) {
                int color = pixels[row + content.right - 1 - offset];
                if (Color.alpha(color) >= 236) { right[y] = color | 0xFF000000; break; }
            }
            if (left[y] != NO_COLOR) {
                red += Color.red(left[y]); green += Color.green(left[y]);
                blue += Color.blue(left[y]); samples++;
            }
            if (right[y] != NO_COLOR) {
                red += Color.red(right[y]); green += Color.green(right[y]);
                blue += Color.blue(right[y]); samples++;
            }
        }
        int representative = samples == 0 ? FALLBACK_COLOR : Color.rgb(
                Math.round(red / (float) samples), Math.round(green / (float) samples),
                Math.round(blue / (float) samples));
        fillMissingRows(left, representative);
        fillMissingRows(right, representative);
        suppressCornerBadges(left, right);
        red = 0; green = 0; blue = 0;
        for (int y = 0; y < height; y++) {
            red += Color.red(left[y]) + Color.red(right[y]);
            green += Color.green(left[y]) + Color.green(right[y]);
            blue += Color.blue(left[y]) + Color.blue(right[y]);
        }
        representative = Color.rgb(Math.round(red / (height * 2f)),
                Math.round(green / (height * 2f)), Math.round(blue / (height * 2f)));
        return new EdgeProfile(left, right, representative);
    }

    private static void fillMissingRows(int[] colors, int fallback) {
        int previous = -1;
        for (int y = 0; y < colors.length; y++) {
            if (colors[y] == NO_COLOR) continue;
            if (previous < 0) Arrays.fill(colors, 0, y, colors[y]);
            else {
                int before = colors[previous], after = colors[y];
                for (int gap = previous + 1; gap < y; gap++)
                    colors[gap] = gap - previous <= y - gap ? before : after;
            }
            previous = y;
        }
        if (previous < 0) Arrays.fill(colors, fallback);
        else Arrays.fill(colors, previous + 1, colors.length, colors[previous]);
    }

    /**
     * A small colored corner badge on a predominantly neutral plate is foreground,
     * not background. Borrow that row's opposite neutral edge for the outside fill.
     * Require a neutral majority and mostly agreeing edges, so blue weather/purple
     * album gradients are not mistaken for isolated badges. Original pixels stay intact.
     */
    private static void suppressCornerBadges(int[] left, int[] right) {
        int height = left.length, agreeing = 0, neutral = 0;
        for (int y = 0; y < height; y++) {
            if (colorDistance(left[y], right[y]) <= 32) agreeing++;
            if (saturation(left[y]) <= .22f) neutral++;
            if (saturation(right[y]) <= .22f) neutral++;
        }
        if (agreeing < height * .55f || neutral < height * 2 * .60f) return;
        boolean[] replaceLeft = new boolean[height], replaceRight = new boolean[height];
        int leftCount = 0, rightCount = 0;
        for (int y = 0; y < height; y++) {
            if (colorDistance(left[y], right[y]) <= 45) continue;
            if (saturation(left[y]) >= .38f && saturation(right[y]) <= .22f) {
                replaceLeft[y] = true; leftCount++;
            }
            if (saturation(right[y]) >= .38f && saturation(left[y]) <= .22f) {
                replaceRight[y] = true; rightCount++;
            }
        }
        for (int y = 0; y < height; y++) {
            if (leftCount <= height * .45f && replaceLeft[y]) left[y] = right[y];
            if (rightCount <= height * .45f && replaceRight[y]) right[y] = left[y];
        }
    }

    private static int colorDistance(int a, int b) {
        return Math.max(Math.abs(Color.red(a) - Color.red(b)),
                Math.max(Math.abs(Color.green(a) - Color.green(b)),
                        Math.abs(Color.blue(a) - Color.blue(b))));
    }

    private static float saturation(int color) {
        int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
        int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
        return max == 0 ? 0f : (max - min) / (float) max;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        float fit = Math.min(bounds.width() / (float) state.bitmap.getWidth(),
                bounds.height() / (float) state.bitmap.getHeight());
        float left = bounds.exactCenterX() - state.bitmap.getWidth() * fit / 2f;
        float top = bounds.exactCenterY() - state.bitmap.getHeight() * fit / 2f;
        destination.set(left, top, left + state.bitmap.getWidth() * fit,
                top + state.bitmap.getHeight() * fit);
        // Map the two texel centers to the first/last original pixel centers. CLAMP
        // extends exactly those row colors outside the contained artwork rectangle.
        float horizontal = Math.max(1, state.bitmap.getWidth() - 1) * fit;
        matrix.setScale(horizontal, fit);
        matrix.postTranslate(left + fit / 2f - horizontal / 2f, top);
        shader.setLocalMatrix(matrix);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || alpha == 0) return;
        int save = alpha == 255 ? -1 : canvas.saveLayerAlpha(bounds.left, bounds.top,
                bounds.right, bounds.bottom, alpha);
        canvas.drawRect(bounds, backgroundPaint);
        canvas.drawBitmap(state.bitmap, null, destination, artworkPaint);
        if (save != -1) canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) {
        backgroundPaint.setColorFilter(filter); artworkPaint.setColorFilter(filter); invalidateSelf();
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return state.width; }
    @Override public int getIntrinsicHeight() { return state.height; }
    @Override public ConstantState getConstantState() { return state; }
    @Override public Drawable mutate() { return this; }

    private static final class EdgeProfile {
        final int[] left, right;
        final int representativeColor;
        EdgeProfile(int[] left, int[] right, int color) {
            this.left = left; this.right = right; this.representativeColor = color;
        }
    }

    /** Only the prepared bitmap is shared; drawing state remains local to each copy. */
    private static final class IconState extends ConstantState {
        final Bitmap bitmap, background;
        final int edgeColor, width, height, configurations;
        IconState(Bitmap bitmap, Bitmap background, int edgeColor, int width, int height, int configurations) {
            this.bitmap = bitmap; this.background = background; this.edgeColor = edgeColor;
            this.width = width; this.height = height; this.configurations = configurations;
        }
        @Override public Drawable newDrawable() { return new AppIconDrawable(this); }
        @Override public Drawable newDrawable(Resources resources) { return new AppIconDrawable(this); }
        @Override public int getChangingConfigurations() { return configurations; }
    }
}
