package com.alert.beacon.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class MapUtils {

    public static Bitmap createMarkerWithPointer(Context context, Bitmap profileBitmap) {
        if (profileBitmap == null) return null;

        float density = context.getResources().getDisplayMetrics().density;
        int imageSize = (int) (40 * density);
        int borderWidth = (int) (2 * density);
        int pointerHeight = (int) (12 * density);

        // Calculate total dimensions
        int width = imageSize + (borderWidth * 2);
        int height = width + pointerHeight;

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 1. Draw the Pointed Pin Shape (Circular Top, Sharp Bottom Corner)
        paint.setColor(Color.WHITE);
        
        android.graphics.Path markerPath = new android.graphics.Path();
        // Circular top part (3/4 of a circle roughly)
        RectF oval = new RectF(0, 0, width, width);
        markerPath.addArc(oval, 135, 270); // Starts from bottom-leftish, goes around the top to bottom-rightish
        
        // Pointed bottom part (The exact location corner)
        markerPath.lineTo(width / 2f, height); // To the sharp point
        markerPath.close();
        
        canvas.drawPath(markerPath, paint);

        // 2. Draw the Profile Image (Circular)
        Bitmap circularBitmap = getCircularBitmap(profileBitmap);
        if (circularBitmap != null) {
            // Center the circular image inside the circular top of the marker
            float offset = borderWidth;
            RectF dstRect = new RectF(offset, offset, width - offset, width - offset);
            canvas.drawBitmap(circularBitmap, null, dstRect, paint);
        }

        return output;
    }

    private static Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);
        
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        
        android.graphics.Rect srcRect = new android.graphics.Rect(
                (width - size) / 2, 
                (height - size) / 2, 
                (width + size) / 2, 
                (height + size) / 2
        );
        android.graphics.Rect dstRect = new android.graphics.Rect(0, 0, size, size);
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
        
        return output;
    }

    public static Bitmap getSquareBitmapWithBorder(Bitmap bitmap, int borderWidth, int borderColor) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);
        Bitmap squareBitmap = Bitmap.createBitmap(bitmap, (width - size) / 2, (height - size) / 2, size, size);
        Bitmap output = Bitmap.createBitmap(size + borderWidth * 2, size + borderWidth * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(borderColor);
        canvas.drawRect(0, 0, output.getWidth(), output.getHeight(), paint);
        canvas.drawBitmap(squareBitmap, borderWidth, borderWidth, null);
        return output;
    }
}
