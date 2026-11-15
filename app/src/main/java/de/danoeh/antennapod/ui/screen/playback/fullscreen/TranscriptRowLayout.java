package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import de.danoeh.antennapod.R;

public class TranscriptRowLayout extends LinearLayout {
    public TranscriptRowLayout(Context context) {
        super(context);
    }

    public TranscriptRowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        RevealTextView text = findViewById(R.id.text);
        View avatar = findViewById(R.id.avatar);
        if (text == null || avatar == null) {
            return;
        }
        if (text.isSliding()) {
            int height = getPaddingTop() + getPaddingBottom() + text.getMeasuredHeight();
            setMeasuredDimension(getMeasuredWidth(), Math.min(getMeasuredHeight(), height));
            int target = text.getTargetHeight();
            avatar.setAlpha(target > 0 ? Math.min(1f, text.getMeasuredHeight() / (float) target) : 1f);
        } else if (avatar.getAlpha() != 1f) {
            avatar.setAlpha(1f);
        }
    }
}
