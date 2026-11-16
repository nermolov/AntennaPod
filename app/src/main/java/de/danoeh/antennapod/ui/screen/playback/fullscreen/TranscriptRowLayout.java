package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import de.danoeh.antennapod.R;

public class TranscriptRowLayout extends LinearLayout {
    private static final float UNBOUNDED = 10000f;

    public TranscriptRowLayout(Context context) {
        super(context);
    }

    public TranscriptRowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int getHiddenHeight() {
        RevealTextView text = findViewById(R.id.text);
        return text == null ? 0 : text.getHiddenHeight();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int hidden = getHiddenHeight();
        if (hidden <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        canvas.save();
        canvas.clipRect(-UNBOUNDED, -UNBOUNDED, UNBOUNDED, getHeight() - hidden);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int hidden = getHiddenHeight();
        if (hidden <= 0 || child.getId() != R.id.avatar) {
            return super.drawChild(canvas, child, drawingTime);
        }
        canvas.save();
        canvas.translate(0, -hidden);
        boolean more = super.drawChild(canvas, child, drawingTime);
        canvas.restore();
        return more;
    }
}
