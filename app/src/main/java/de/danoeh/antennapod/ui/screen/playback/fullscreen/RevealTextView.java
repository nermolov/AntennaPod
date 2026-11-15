package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;

public class RevealTextView extends View {
    private static final float LINE_SPACING_MULTIPLIER = 1.2f;
    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private LiveTranscriptModel.Turn turn;
    private long revealTime = Long.MAX_VALUE;
    private StaticLayout layout;
    private long[] lineStartTimes = new long[0];
    private int layoutWidth = -1;
    private int revealedLines = 0;

    public RevealTextView(Context context) {
        this(context, null);
    }

    public RevealTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(48f);
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
            requestLayout();
        }
        invalidate();
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
        int height = 0;
        if (revealedLines > 0 && layout != null) {
            height = Math.max(layout.getLineBottom(revealedLines - 1) + getPaddingTop() + getPaddingBottom(),
                    getMinimumHeight());
        }
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
        int textHeight = layout.getLineBottom(revealedLines - 1);
        int free = getHeight() - getPaddingTop() - getPaddingBottom() - textHeight;
        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop() + Math.max(0, free / 2f));
        canvas.clipRect(0, 0, layoutWidth, layout.getLineBottom(revealedLines - 1));
        layout.draw(canvas);
        canvas.restore();
    }
}
