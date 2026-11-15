package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Arrays;

public class RevealTextView extends View {
    private static final float LINE_SPACING_MULTIPLIER = 1.2f;
    public static final long SLIDE_DURATION_MS = 350;
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private LiveTranscriptModel.Turn turn;
    private long revealTime = Long.MAX_VALUE;
    private StaticLayout layout;
    private long[] lineStartTimes = new long[0];
    private int layoutWidth = -1;
    private int revealedLines = 0;
    private int shownLines = 0;
    private float slideProgress = 1f;
    private boolean turnFresh = true;
    private boolean animateIn = false;
    private ValueAnimator slideAnimator;

    public RevealTextView(Context context) {
        this(context, null);
    }

    public RevealTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(48f);
        setPadding(getPaddingLeft(), Math.max(0, getPaddingTop() - 1), getPaddingRight(), getPaddingBottom());
    }

    public void setAnimateIn(boolean animateIn) {
        this.animateIn = animateIn;
    }

    public boolean isSliding() {
        return slideProgress < 1f && shownLines < revealedLines;
    }

    public int getTargetHeight() {
        return paddedHeight(revealedLines);
    }

    public static int revealedLineCount(long[] lineStartTimes, long timeMs) {
        int count = 0;
        while (count < lineStartTimes.length && lineStartTimes[count] <= timeMs) {
            count++;
        }
        return count;
    }

    public void setTurn(LiveTranscriptModel.Turn newTurn) {
        if (newTurn == turn) {
            return;
        }
        turn = newTurn;
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
        invalidate();
    }

    public void setRevealTime(long timeMs) {
        revealTime = timeMs;
        if (layout == null) {
            return;
        }
        int count = revealedLineCount(lineStartTimes, timeMs);
        if (count != revealedLines) {
            revealedLines = count;
            startSlide();
        }
        invalidate();
    }

    private void startSlide() {
        if (slideAnimator != null) {
            slideAnimator.cancel();
        }
        if (revealedLines < shownLines || !isAttachedToWindow()) {
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
            requestLayout();
            invalidate();
        });
        slideAnimator.start();
        requestLayout();
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
        return Math.max(layout.getLineBottom(lines - 1) + getPaddingTop() + getPaddingBottom(), getMinimumHeight());
    }

    private int animatedHeight() {
        int target = paddedHeight(revealedLines);
        if (slideProgress >= 1f || shownLines >= revealedLines) {
            return target;
        }
        int from = paddedHeight(shownLines);
        return from + Math.round((target - from) * slideProgress);
    }

    private int animatedTextHeight() {
        if (layout == null || revealedLines == 0) {
            return 0;
        }
        int target = layout.getLineBottom(revealedLines - 1);
        if (slideProgress >= 1f || shownLines >= revealedLines) {
            return target;
        }
        int from = shownLines == 0 ? 0 : layout.getLineBottom(shownLines - 1);
        return from + Math.round((target - from) * slideProgress);
    }

    private void invalidateLayout() {
        layout = null;
        layoutWidth = -1;
        requestLayout();
        invalidate();
    }

    private void buildLayout(int width) {
        layoutWidth = width;
        if (turn == null || width <= 0) {
            layout = null;
            lineStartTimes = new long[0];
            revealedLines = 0;
            return;
        }
        String text = turn.getText();
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
                .build();
        int lines = layout.getLineCount();
        lineStartTimes = new long[lines];
        Arrays.fill(lineStartTimes, Long.MAX_VALUE);
        for (int w = 0; w < turn.getWordCount(); w++) {
            int line = layout.getLineForOffset(turn.getWordCharOffset(w));
            if (lineStartTimes[line] == Long.MAX_VALUE) {
                lineStartTimes[line] = turn.getWordStartTime(w);
            }
        }
        for (int i = 1; i < lines; i++) {
            if (lineStartTimes[i] == Long.MAX_VALUE) {
                lineStartTimes[i] = lineStartTimes[i - 1];
            }
        }
        revealedLines = revealedLineCount(lineStartTimes, revealTime);
        if (turnFresh) {
            turnFresh = false;
            shownLines = revealedLines;
            slideProgress = 1f;
            if (animateIn && revealedLines > 0) {
                shownLines = 0;
                slideProgress = 0f;
                post(this::startSlide);
            }
            animateIn = false;
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
        int height = layout != null ? animatedHeight() : 0;
        setMeasuredDimension(measuredWidth, resolveSize(height, heightMeasureSpec));
    }

    private int widestLine() {
        float widest = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            widest = Math.max(widest, layout.getLineWidth(i));
        }
        return (int) Math.ceil(widest);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (layout == null || revealedLines == 0) {
            return;
        }
        int textHeight = animatedTextHeight();
        int free = getHeight() - getPaddingTop() - getPaddingBottom() - textHeight;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop() + Math.max(0, free / 2f));
        canvas.clipRect(0, 0, layoutWidth, textHeight);
        int target = layout.getLineBottom(revealedLines - 1);
        if (textHeight < target && shownLines < revealedLines) {
            int split = shownLines == 0 ? 0 : layout.getLineBottom(shownLines - 1);
            canvas.save();
            canvas.clipRect(0, 0, layoutWidth, split);
            layout.draw(canvas);
            canvas.restore();
            canvas.translate(0, target - textHeight);
            canvas.clipRect(0, split, layoutWidth, target);
            layout.draw(canvas);
        } else {
            layout.draw(canvas);
        }
        canvas.restore();
    }
}
