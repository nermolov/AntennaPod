package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class TranscriptListView extends RecyclerView {
    public TranscriptListView(Context context) {
        super(context);
    }

    public TranscriptListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private int getSlideOffset() {
        int count = getAdapter() == null ? 0 : getAdapter().getItemCount();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof TranscriptRowLayout && getChildAdapterPosition(child) == count - 1) {
                return ((TranscriptRowLayout) child).getHiddenHeight();
            }
        }
        return 0;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int offset = getSlideOffset();
        if (offset <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        canvas.save();
        canvas.translate(0, offset);
        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
