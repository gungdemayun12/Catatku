package com.example.pencatatan.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Loads and renders the round profile photo.
 *
 * <p>Photos are downscaled and re-encoded as a JPEG before being stored, so the Firestore
 * document stays far below the 1 MiB document limit. A data-string photo is used instead of a
 * file path because a picked gallery URI is not readable after the app restarts.
 */
public final class ProfilePhoto {

    /** Hard cap for the encoded payload; Firestore documents must stay well under 1 MiB. */
    private static final int MAX_BYTES = 220 * 1024;
    private static final int MAX_EDGE = 320;
    private static final String PREFIX = "data:image/jpeg;base64,";

    private ProfilePhoto() {
    }

    static boolean isPhoto(String photoUrl) {
        return photoUrl != null && photoUrl.startsWith(PREFIX);
    }

    /**
     * Reads, downscales, and compresses the picked image.
     *
     * @return a {@code data:image/jpeg;base64,...} string, or {@code null} if the image is unreadable.
     */
    @Nullable
    static String encode(Context context, Uri uri) {
        Bitmap bitmap = decodeScaled(context, uri);
        if (bitmap == null) return null;

        byte[] bytes = compress(bitmap, 82);
        // Step the quality down until the payload fits the document budget.
        int quality = 82;
        while (bytes != null && bytes.length > MAX_BYTES && quality > 35) {
            quality -= 12;
            bytes = compress(bitmap, quality);
        }
        bitmap.recycle();
        if (bytes == null) return null;
        return PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /** Renders the stored data-string photo as a circle inside the given ImageView. */
    public static boolean apply(ImageView view, @Nullable String photoUrl) {
        if (view == null) return false;
        if (!isPhoto(photoUrl)) {
            view.setVisibility(View.GONE);
            return false;
        }
        try {
            byte[] bytes = Base64.decode(photoUrl.substring(PREFIX.length()), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                view.setVisibility(View.GONE);
                return false;
            }
            view.setImageDrawable(new BitmapDrawableCircle(bitmap));
            view.setVisibility(View.VISIBLE);
            return true;
        } catch (IllegalArgumentException error) {
            view.setVisibility(View.GONE);
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private static Bitmap decodeScaled(Context context, Uri uri) {
        try (InputStream probe = context.getContentResolver().openInputStream(uri)) {
            if (probe == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(probe, null, bounds);

            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > MAX_EDGE * 2) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
                if (stream == null) return null;
                Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
                if (decoded == null) return null;
                return scaleToSquare(decoded);
            }
        } catch (Exception error) {
            return null;
        }
    }

    /** Centre-crops into a square no larger than {@link #MAX_EDGE} pixels. */
    private static Bitmap scaleToSquare(Bitmap source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        if (side <= 0) return source;
        int left = (source.getWidth() - side) / 2;
        int top = (source.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(source, left, top, side, side);
        if (square == source) {
            square = Bitmap.createScaledBitmap(source, MAX_EDGE, MAX_EDGE, true);
        } else {
            Bitmap scaled = Bitmap.createScaledBitmap(square, MAX_EDGE, MAX_EDGE, true);
            if (scaled != square) square.recycle();
            square = scaled;
        }
        if (square != source) source.recycle();
        return square;
    }

    @Nullable
    private static byte[] compress(Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null;
        return out.toByteArray();
    }

    /** Drawable that paints the bitmap clipped to a circle. */
    private static final class BitmapDrawableCircle extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap bitmap;

        BitmapDrawableCircle(Bitmap bitmap) {
            this.bitmap = bitmap;
            paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            float radius = Math.min(getBounds().width(), getBounds().height()) / 2f;
            canvas.drawCircle(getBounds().exactCenterX(), getBounds().exactCenterY(), radius, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
