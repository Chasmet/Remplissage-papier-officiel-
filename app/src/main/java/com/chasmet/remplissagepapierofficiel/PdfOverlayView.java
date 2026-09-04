package com.chasmet.remplissagepapierofficiel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.List;

public class PdfOverlayView extends View {
    public interface OnPositionSelectedListener {
        void onPositionSelected(float x, float y);
    }

    public interface OnFieldSelectedListener {
        void onFieldSelected(int index, FormField field);
    }

    public interface OnZoomChangedListener {
        void onZoomChanged(float zoom);
    }

    private Bitmap bitmap;
    private int pageUnitWidth = 1;
    private int pageUnitHeight = 1;
    private final List<TextOverlay> overlays = new ArrayList<>();
    private final List<FormField> detectedFields = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private OnPositionSelectedListener positionListener;
    private OnFieldSelectedListener fieldListener;
    private OnZoomChangedListener zoomListener;

    private ScaleGestureDetector scaleDetector;
    private int touchSlop;

    private float selectedX = -1f;
    private float selectedY = -1f;
    private int selectedFieldIndex = -1;

    private float baseScale = 1f;
    private float zoomScale = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private float drawW = 1f;
    private float drawH = 1f;

    private float lastTouchX;
    private float lastTouchY;
    private float downTouchX;
    private float downTouchY;
    private boolean moved;
    private long suppressTapUntil;

    public PdfOverlayView(Context context) {
        super(context);
        init(context);
    }

    public PdfOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PdfOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                moved = true;
                suppressTapUntil = SystemClock.uptimeMillis() + 220L;
                return bitmap != null;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (bitmap == null || bitmap.isRecycled()) return false;
                updateTransform();

                float oldZoom = zoomScale;
                float newZoom = clamp(oldZoom * detector.getScaleFactor(), 1f, 5f);
                if (Math.abs(newZoom - oldZoom) < 0.001f) return true;

                float oldW = bitmap.getWidth() * baseScale * oldZoom;
                float oldH = bitmap.getHeight() * baseScale * oldZoom;
                float oldLeft = (getWidth() - oldW) * 0.5f + panX;
                float oldTop = (getHeight() - oldH) * 0.5f + panY;

                float docX = oldW > 0f ? (detector.getFocusX() - oldLeft) / oldW : 0.5f;
                float docY = oldH > 0f ? (detector.getFocusY() - oldTop) / oldH : 0.5f;

                zoomScale = newZoom;
                float newW = bitmap.getWidth() * baseScale * zoomScale;
                float newH = bitmap.getHeight() * baseScale * zoomScale;
                panX = detector.getFocusX() - (getWidth() - newW) * 0.5f - docX * newW;
                panY = detector.getFocusY() - (getHeight() - newH) * 0.5f - docY * newH;
                clampPan();
                invalidate();
                notifyZoom();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                suppressTapUntil = SystemClock.uptimeMillis() + 220L;
                super.onScaleEnd(detector);
            }
        });
    }

    public void setPage(Bitmap bitmap, List<TextOverlay> pageOverlays) {
        int width = bitmap == null ? 1 : Math.max(1, bitmap.getWidth());
        int height = bitmap == null ? 1 : Math.max(1, bitmap.getHeight());
        setPage(bitmap, width, height, pageOverlays);
    }

    public void setPage(Bitmap bitmap, int pageWidth, int pageHeight,
                        List<TextOverlay> pageOverlays) {
        if (this.bitmap != null && this.bitmap != bitmap && !this.bitmap.isRecycled()) {
            this.bitmap.recycle();
        }
        this.bitmap = bitmap;
        this.pageUnitWidth = Math.max(1, pageWidth);
        this.pageUnitHeight = Math.max(1, pageHeight);
        overlays.clear();
        if (pageOverlays != null) overlays.addAll(pageOverlays);
        detectedFields.clear();
        selectedFieldIndex = -1;
        selectedX = -1f;
        selectedY = -1f;
        zoomScale = 1f;
        panX = 0f;
        panY = 0f;
        moved = false;
        invalidate();
        notifyZoom();
    }

    public void setOverlays(List<TextOverlay> pageOverlays) {
        overlays.clear();
        if (pageOverlays != null) overlays.addAll(pageOverlays);
        invalidate();
    }

    public void setDetectedFields(List<FormField> fields) {
        detectedFields.clear();
        if (fields != null) detectedFields.addAll(fields);
        if (selectedFieldIndex >= detectedFields.size()) selectedFieldIndex = -1;
        invalidate();
    }

    public int getDetectedFieldCount() {
        return detectedFields.size();
    }

    public int getSelectedFieldIndex() {
        return selectedFieldIndex;
    }

    public float getZoomScale() {
        return zoomScale;
    }

    public void resetZoom() {
        zoomScale = 1f;
        panX = 0f;
        panY = 0f;
        invalidate();
        notifyZoom();
    }

    public void selectNextField() {
        if (detectedFields.isEmpty()) return;
        int next = selectedFieldIndex < 0 ? 0 : (selectedFieldIndex + 1) % detectedFields.size();
        selectField(next);
    }

    public void selectPreviousField() {
        if (detectedFields.isEmpty()) return;
        int next = selectedFieldIndex < 0
                ? detectedFields.size() - 1
                : (selectedFieldIndex - 1 + detectedFields.size()) % detectedFields.size();
        selectField(next);
    }

    public void setOnPositionSelectedListener(OnPositionSelectedListener listener) {
        this.positionListener = listener;
    }

    public void setOnFieldSelectedListener(OnFieldSelectedListener listener) {
        this.fieldListener = listener;
    }

    public void setOnZoomChangedListener(OnZoomChangedListener listener) {
        this.zoomListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled()) return;
        updateTransform();

        RectF pageRect = new RectF(offsetX, offsetY, offsetX + drawW, offsetY + drawH);
        canvas.drawBitmap(bitmap, null, pageRect, paint);

        drawDetectedFields(canvas);
        drawTextOverlays(canvas);
        drawSelection(canvas);
    }

    private void drawDetectedFields(Canvas canvas) {
        if (detectedFields.isEmpty()) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < detectedFields.size(); i++) {
            FormField field = detectedFields.get(i);
            RectF rect = fieldToScreen(field);
            boolean selected = i == selectedFieldIndex;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.argb(52, 68, 210, 120) : Color.argb(30, 255, 101, 0));
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((selected ? 2.2f : 1.2f) * density);
            paint.setColor(selected ? Color.rgb(72, 225, 136) : Color.rgb(255, 112, 30));
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTextOverlays(Canvas canvas) {
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);

        if (bitmap == null || bitmap.isRecycled()) return;
        float pageScale = Math.min(
                drawW / Math.max(1f, pageUnitWidth),
                drawH / Math.max(1f, pageUnitHeight)
        );

        for (TextOverlay overlay : overlays) {
            if (overlay == null || overlay.text == null || overlay.text.isEmpty()) continue;

            float screenTextSize = Math.max(1f, overlay.textSize * pageScale);
            paint.setTextSize(screenTextSize);
            paint.setTextAlign(toPaintAlign(overlay.align));

            float x = offsetX + overlay.x * drawW;
            float y = offsetY + overlay.y * drawH;

            if (overlay.isCheckbox()) {
                Paint.FontMetrics fm = paint.getFontMetrics();
                y = y - (fm.ascent + fm.descent) * 0.5f;
                paint.setTextAlign(Paint.Align.CENTER);
            }

            canvas.drawText(overlay.text, x, y, paint);
        }

        paint.setTextAlign(Paint.Align.LEFT);
    }

    private static Paint.Align toPaintAlign(String align) {
        if (TextOverlay.ALIGN_CENTER.equals(align)) return Paint.Align.CENTER;
        if (TextOverlay.ALIGN_RIGHT.equals(align)) return Paint.Align.RIGHT;
        return Paint.Align.LEFT;
    }

    private void drawSelection(Canvas canvas) {
        if (selectedX < 0f || selectedY < 0f) return;
        float density = getResources().getDisplayMetrics().density;
        float cx = offsetX + selectedX * drawW;
        float cy = offsetY + selectedY * drawH;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        paint.setColor(Color.rgb(35, 102, 245));
        canvas.drawCircle(cx, cy, 7f * density, paint);
        canvas.drawLine(cx - 11f * density, cy, cx + 11f * density, cy, paint);
        canvas.drawLine(cx, cy - 11f * density, cx, cy + 11f * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null || bitmap.isRecycled()) return true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                downTouchX = lastTouchX;
                downTouchY = lastTouchY;
                moved = false;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                moved = true;
                suppressTapUntil = SystemClock.uptimeMillis() + 220L;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress() || event.getPointerCount() > 1) {
                    moved = true;
                    return true;
                }
                float x = event.getX();
                float y = event.getY();
                float distance = (float) Math.hypot(x - downTouchX, y - downTouchY);
                if (distance > touchSlop) moved = true;
                if (zoomScale > 1.001f && moved) {
                    panX += x - lastTouchX;
                    panY += y - lastTouchY;
                    clampPan();
                    invalidate();
                }
                lastTouchX = x;
                lastTouchY = y;
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                moved = true;
                suppressTapUntil = SystemClock.uptimeMillis() + 220L;
                return true;

            case MotionEvent.ACTION_UP:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (!moved
                        && !scaleDetector.isInProgress()
                        && SystemClock.uptimeMillis() >= suppressTapUntil) {
                    performClick();
                    selectAtScreen(event.getX(), event.getY());
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                moved = true;
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void selectAtScreen(float screenX, float screenY) {
        updateTransform();
        if (drawW <= 0f || drawH <= 0f) return;
        if (screenX < offsetX || screenY < offsetY || screenX > offsetX + drawW || screenY > offsetY + drawH) {
            return;
        }

        float nx = clamp((screenX - offsetX) / drawW, 0f, 1f);
        float ny = clamp((screenY - offsetY) / drawH, 0f, 1f);
        int fieldIndex = findNearestField(nx, ny);
        if (fieldIndex >= 0) {
            selectField(fieldIndex);
            return;
        }

        selectedFieldIndex = -1;
        selectedX = nx;
        selectedY = ny;
        invalidate();
        if (positionListener != null) positionListener.onPositionSelected(selectedX, selectedY);
    }

    private int findNearestField(float nx, float ny) {
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < detectedFields.size(); i++) {
            FormField field = detectedFields.get(i);
            if (!field.containsExpanded(nx, ny, 0.012f, 0.014f)) continue;
            float dx = nx - field.centerX();
            float dy = ny - field.centerY();
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void selectField(int index) {
        if (index < 0 || index >= detectedFields.size()) return;
        selectedFieldIndex = index;
        FormField field = detectedFields.get(index);
        selectedX = field.textX();
        selectedY = field.textBaselineY();
        invalidate();
        if (positionListener != null) positionListener.onPositionSelected(selectedX, selectedY);
        if (fieldListener != null) fieldListener.onFieldSelected(index, field);
    }

    private RectF fieldToScreen(FormField field) {
        return new RectF(
                offsetX + field.x * drawW,
                offsetY + field.y * drawH,
                offsetX + (field.x + field.width) * drawW,
                offsetY + (field.y + field.height) * drawH
        );
    }

    private void updateTransform() {
        if (bitmap == null || bitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return;
        float sx = getWidth() / (float) bitmap.getWidth();
        float sy = getHeight() / (float) bitmap.getHeight();
        baseScale = Math.min(sx, sy);
        clampPan();
        drawW = bitmap.getWidth() * baseScale * zoomScale;
        drawH = bitmap.getHeight() * baseScale * zoomScale;
        offsetX = (getWidth() - drawW) * 0.5f + panX;
        offsetY = (getHeight() - drawH) * 0.5f + panY;
    }

    private void clampPan() {
        if (bitmap == null || bitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0) return;
        float sx = getWidth() / (float) bitmap.getWidth();
        float sy = getHeight() / (float) bitmap.getHeight();
        baseScale = Math.min(sx, sy);
        float w = bitmap.getWidth() * baseScale * zoomScale;
        float h = bitmap.getHeight() * baseScale * zoomScale;

        float maxPanX = Math.max(0f, (w - getWidth()) * 0.5f);
        float maxPanY = Math.max(0f, (h - getHeight()) * 0.5f);
        panX = clamp(panX, -maxPanX, maxPanX);
        panY = clamp(panY, -maxPanY, maxPanY);
    }

    private void notifyZoom() {
        if (zoomListener != null) zoomListener.onZoomChanged(zoomScale);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
        overlays.clear();
        detectedFields.clear();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
