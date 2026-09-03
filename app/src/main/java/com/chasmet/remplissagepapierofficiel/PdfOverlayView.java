package com.chasmet.remplissagepapierofficiel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PdfOverlayView extends View {
    public interface OnPositionSelectedListener {
        void onPositionSelected(float x, float y);
    }

    private Bitmap bitmap;
    private final List<TextOverlay> overlays = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnPositionSelectedListener listener;
    private float selectedX = -1f;
    private float selectedY = -1f;
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    public PdfOverlayView(Context context) {
        super(context);
    }

    public PdfOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PdfOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setPage(Bitmap bitmap, List<TextOverlay> pageOverlays) {
        this.bitmap = bitmap;
        overlays.clear();
        if (pageOverlays != null) overlays.addAll(pageOverlays);
        invalidate();
    }

    public void setOverlays(List<TextOverlay> pageOverlays) {
        overlays.clear();
        if (pageOverlays != null) overlays.addAll(pageOverlays);
        invalidate();
    }

    public void setOnPositionSelectedListener(OnPositionSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;

        float sx = getWidth() / (float) bitmap.getWidth();
        float sy = getHeight() / (float) bitmap.getHeight();
        scale = Math.min(sx, sy);
        float drawW = bitmap.getWidth() * scale;
        float drawH = bitmap.getHeight() * scale;
        offsetX = (getWidth() - drawW) / 2f;
        offsetY = (getHeight() - drawH) / 2f;

        canvas.drawBitmap(bitmap, null,
                new android.graphics.RectF(offsetX, offsetY, offsetX + drawW, offsetY + drawH), paint);

        paint.setColor(Color.BLACK);
        for (TextOverlay overlay : overlays) {
            paint.setTextSize(Math.max(12f, overlay.textSize * scale));
            float x = offsetX + overlay.x * drawW;
            float y = offsetY + overlay.y * drawH;
            canvas.drawText(overlay.text, x, y, paint);
        }

        if (selectedX >= 0f && selectedY >= 0f) {
            paint.setColor(Color.rgb(36, 87, 230));
            float cx = offsetX + selectedX * drawW;
            float cy = offsetY + selectedY * drawH;
            canvas.drawCircle(cx, cy, 8f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || event.getAction() != MotionEvent.ACTION_DOWN) return true;
        float drawW = bitmap.getWidth() * scale;
        float drawH = bitmap.getHeight() * scale;
        float x = event.getX();
        float y = event.getY();
        if (x < offsetX || y < offsetY || x > offsetX + drawW || y > offsetY + drawH) return true;

        selectedX = (x - offsetX) / drawW;
        selectedY = (y - offsetY) / drawH;
        invalidate();
        if (listener != null) listener.onPositionSelected(selectedX, selectedY);
        return true;
    }
}
