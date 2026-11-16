package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.core.graphics.ColorUtils;

import java.util.Arrays;

public class RevealTextView extends View {
    private static final float LINE_SPACING_MULTIPLIER = 1.2f;
    public static final long SLIDE_DURATION_MS = 350;
    static final long UNSPOKEN_LEAD_MS = 300;
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private LiveTranscriptModel.Turn turn;
    private long revealTime = Long.MAX_VALUE;
    private StaticLayout layout;
    private long[] lineStartTimes = new long[0];
    private final SparseArray<Built> builtLayouts = new SparseArray<>();
    private int layoutWidth = -1;
    private int revealedLines = 0;
    private int shownLines = 0;
    private float slideProgress = 1f;
    private boolean turnFresh = true;
    private ValueAnimator slideAnimator;
    private Drawable bubble;
    private Spannable text;
    private ForegroundColorSpan dimSpan;
    private int unspokenStart = -1;
    private float unspokenAlpha = 0.4f;

    public RevealTextView(Context context) {
        this(context, null);
    }

    public RevealTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(48f);
        setPadding(getPaddingLeft(), Math.max(0, getPaddingTop() - 1), getPaddingRight(), getPaddingBottom());
    }

    public int getHiddenHeight() {
        if (slideProgress >= 1f || shownLines >= revealedLines) {
            return 0;
        }
        return Math.max(0, paddedHeight(revealedLines) - animatedHeight());
    }

    public static int revealedLineCount(long[] lineStartTimes, long timeMs) {
        int count = 0;
        while (count < lineStartTimes.length && lineStartTimes[count] <= timeMs) {
            count++;
        }
        return count;
    }

    static int unspokenStartOffset(long[] wordStarts, int[] charOffsets, long timeMs, int textLength) {
        int low = 0;
        int high = wordStarts.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (wordStarts[mid] <= timeMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low >= charOffsets.length ? textLength : charOffsets[low];
    }

    public void setTurn(LiveTranscriptModel.Turn newTurn) {
        if (newTurn == turn) {
            return;
        }
        turn = newTurn;
        text = new SpannableString(newTurn.getText());
        clearUnspoken();
        applyUnspoken();
        turnFresh = true;
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        shownLines = 0;
        slideProgress = 1f;
        invalidateLayout();
    }

    public void setTextSizePx(float px) {
        if (paint.getTextSize() == px) {
            return;
        }
        paint.setTextSize(px);
        invalidateLayout();
    }

    public void setTypeface(Typeface typeface) {
        if (paint.getTypeface() == typeface) {
            return;
        }
        paint.setTypeface(typeface);
        invalidateLayout();
    }

    public void setTextColor(int color) {
        paint.setColor(color);
        clearUnspoken();
        applyUnspoken();
        invalidate();
    }

    public void setUnspokenAlpha(float alpha) {
        if (unspokenAlpha == alpha) {
            return;
        }
        unspokenAlpha = alpha;
        clearUnspoken();
        applyUnspoken();
        invalidate();
    }

    private void clearUnspoken() {
        if (dimSpan != null && text != null) {
            text.removeSpan(dimSpan);
        }
        dimSpan = null;
        unspokenStart = -1;
    }

    private long dimTime() {
        return revealTime > Long.MAX_VALUE - UNSPOKEN_LEAD_MS ? Long.MAX_VALUE : revealTime + UNSPOKEN_LEAD_MS;
    }

    private void applyUnspoken() {
        if (turn == null || text == null) {
            return;
        }
        int start = unspokenStartOffset(turn.getWordStartTimes(), turn.getWordCharOffsets(), dimTime(),
                text.length());
        if (start == unspokenStart) {
            return;
        }
        unspokenStart = start;
        if (dimSpan != null) {
            text.removeSpan(dimSpan);
        }
        if (start >= text.length()) {
            dimSpan = null;
        } else {
            int color = ColorUtils.setAlphaComponent(paint.getColor(), Math.round(255 * unspokenAlpha));
            dimSpan = new ForegroundColorSpan(color);
            text.setSpan(dimSpan, start, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        invalidate();
    }

    public void setRevealTime(long timeMs) {
        revealTime = timeMs;
        applyUnspoken();
        if (layout == null) {
            return;
        }
        int count = revealedLineCount(lineStartTimes, timeMs);
        if (count != revealedLines) {
            revealedLines = count;
            if (timeMs == Long.MAX_VALUE) {
                if (slideAnimator != null) {
                    slideAnimator.cancel();
                }
                shownLines = revealedLines;
                slideProgress = 1f;
                requestLayout();
            } else {
                startSlide();
            }
        }
        invalidate();
    }

    public void setRevealTimeInstant(long timeMs) {
        revealTime = timeMs;
        applyUnspoken();
        if (layout == null) {
            return;
        }
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        revealedLines = revealedLineCount(lineStartTimes, timeMs);
        shownLines = revealedLines;
        slideProgress = 1f;
        requestLayout();
        invalidate();
    }

    private void startSlide() {
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        if (revealedLines < shownLines || shownLines == 0 || !isAttachedToWindow()) {
            shownLines = revealedLines;
            slideProgress = 1f;
            requestLayout();
            return;
        }
        slideProgress = 0f;
        slideAnimator = ValueAnimator.ofFloat(0f, 1f);
        slideAnimator.setDuration(SLIDE_DURATION_MS);
        slideAnimator.setInterpolator(new DecelerateInterpolator());
        slideAnimator.addUpdateListener(a -> {
            slideProgress = (float) a.getAnimatedValue();
            if (slideProgress >= 1f) {
                shownLines = revealedLines;
            }
            invalidateRow();
        });
        slideAnimator.start();
        requestLayout();
    }

    private void invalidateRow() {
        invalidate();
        ViewParent row = getParent();
        if (row instanceof View) {
            ((View) row).invalidate();
            ViewParent list = row.getParent();
            if (list instanceof View) {
                ((View) list).invalidate();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (slideAnimator != null) {
            slideAnimator.cancel();
            slideAnimator = null;
        }
        shownLines = revealedLines;
        slideProgress = 1f;
    }

    private int paddedHeight(int lines) {
        if (lines == 0 || layout == null) {
            return 0;
        }
        return Math.max(textBottom(lines) + getPaddingTop() + getPaddingBottom(), getMinimumHeight());
    }

    private int textBottom(int lines) {
        int index = lines - 1;
        int lastIndex = layout.getLineCount() - 1;
        if (index >= lastIndex) {
            return layout.getLineBottom(lastIndex);
        }
        int lastDescent = layout.getLineBottom(lastIndex) - layout.getLineBaseline(lastIndex);
        return layout.getLineBaseline(index) + lastDescent;
    }

    private int animatedHeight() {
        int target = paddedHeight(revealedLines);
        if (slideProgress >= 1f || shownLines >= revealedLines) {
            return target;
        }
        int from = paddedHeight(shownLines);
        return from + Math.round((target - from) * slideProgress);
    }

    private void invalidateLayout() {
        builtLayouts.clear();
        layout = null;
        layoutWidth = -1;
        requestLayout();
        invalidate();
    }

    private static class Built {
        final StaticLayout layout;
        final long[] lineStartTimes;

        Built(StaticLayout layout, long[] lineStartTimes) {
            this.layout = layout;
            this.lineStartTimes = lineStartTimes;
        }
    }

    private Built measureTurn(int width) {
        StaticLayout built = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
                .build();
        int lines = built.getLineCount();
        long[] times = new long[lines];
        Arrays.fill(times, Long.MAX_VALUE);
        for (int w = 0; w < turn.getWordCount(); w++) {
            int line = built.getLineForOffset(turn.getWordCharOffset(w));
            if (times[line] == Long.MAX_VALUE) {
                times[line] = turn.getWordStartTime(w);
            }
        }
        for (int i = 1; i < lines; i++) {
            if (times[i] == Long.MAX_VALUE) {
                times[i] = times[i - 1];
            }
        }
        return new Built(built, times);
    }

    private void buildLayout(int width) {
        layoutWidth = width;
        if (turn == null || width <= 0) {
            layout = null;
            lineStartTimes = new long[0];
            revealedLines = 0;
            return;
        }
        Built built = builtLayouts.get(width);
        if (built == null) {
            built = measureTurn(width);
            builtLayouts.put(width, built);
        }
        layout = built.layout;
        lineStartTimes = built.lineStartTimes;
        revealedLines = revealedLineCount(lineStartTimes, revealTime);
        if (turnFresh) {
            turnFresh = false;
            shownLines = revealedLines;
            slideProgress = 1f;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int padding = getPaddingLeft() + getPaddingRight();
        int width = MeasureSpec.getSize(widthMeasureSpec) - padding;
        if (layout == null || width != layoutWidth) {
            buildLayout(width);
        }
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY && layout != null) {
            measuredWidth = Math.min(measuredWidth, widestLine() + padding);
        }
        int height = layout != null ? paddedHeight(revealedLines) : 0;
        setMeasuredDimension(measuredWidth, resolveSize(height, heightMeasureSpec));
    }

    private int widestLine() {
        float widest = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            widest = Math.max(widest, layout.getLineWidth(i));
        }
        return (int) Math.ceil(widest);
    }

    public void setBubble(Drawable bubble) {
        this.bubble = bubble;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bubble != null) {
            bubble.setBounds(0, 0, getWidth(), getHeight() - getHiddenHeight());
            bubble.draw(canvas);
        }
        if (layout == null || revealedLines == 0) {
            return;
        }
        int textHeight = textBottom(revealedLines);
        int free = getHeight() - getPaddingTop() - getPaddingBottom() - textHeight;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop() + Math.max(0, free / 2f));
        canvas.clipRect(0, 0, layoutWidth, textHeight - getHiddenHeight());
        layout.draw(canvas);
        canvas.restore();
    }
}
