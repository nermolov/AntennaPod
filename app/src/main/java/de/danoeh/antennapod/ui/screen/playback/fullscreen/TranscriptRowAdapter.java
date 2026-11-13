package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;

import java.util.List;

public class TranscriptRowAdapter extends RecyclerView.Adapter<TranscriptRowAdapter.ViewHolder> {
    private static final Object PAYLOAD = new Object();
    private static final int CURRENT_COLOR = 0xFFFFFFFF;
    private static final int PAST_COLOR = 0x8CFFFFFF;

    private LiveTranscriptModel model;
    private float textSizePx = 48f;
    private int avatarSizePx = 70;
    private int visibleCount = 0;
    private long currentTime = 0;
    private int trackedIndex = -1;
    private int trackedWords = -1;

    public TranscriptRowAdapter() {
        setHasStableIds(true);
    }

    public void setModel(LiveTranscriptModel model) {
        this.model = model;
        visibleCount = 0;
        trackedIndex = -1;
        trackedWords = -1;
        currentTime = 0;
        notifyDataSetChanged();
    }

    public void setTextSizePx(float px) {
        textSizePx = px;
        Paint paint = new Paint();
        paint.setTextSize(px);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        avatarSizePx = Math.round((metrics.descent - metrics.ascent) * 1.2f);
        notifyDataSetChanged();
    }

    public boolean setTime(long timeMs) {
        currentTime = timeMs;
        if (model == null) {
            return false;
        }
        int oldCount = visibleCount;
        int newCount = model.visibleTurnCountAt(timeMs);
        boolean changed = false;
        if (newCount > oldCount) {
            visibleCount = newCount;
            notifyItemRangeInserted(oldCount, newCount - oldCount);
            changed = true;
        } else if (newCount < oldCount) {
            visibleCount = newCount;
            notifyItemRangeRemoved(newCount, oldCount - newCount);
            changed = true;
        }
        int last = newCount - 1;
        if (trackedIndex >= 0 && trackedIndex != last && trackedIndex < newCount) {
            notifyItemChanged(trackedIndex, PAYLOAD);
        }
        if (last >= 0) {
            int words = model.spokenWordCountAt(last, timeMs);
            if (last != trackedIndex || words != trackedWords) {
                if (!(newCount > oldCount && last >= oldCount)) {
                    notifyItemChanged(last, PAYLOAD);
                }
                changed = true;
            }
            trackedWords = words;
        } else {
            trackedWords = -1;
        }
        trackedIndex = last;
        return changed;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fullscreen_transcript_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiveTranscriptModel.Turn turn = model.getTurn(position);
        ViewGroup.LayoutParams params = holder.avatar.getLayoutParams();
        if (params.width != avatarSizePx || params.height != avatarSizePx) {
            params.width = avatarSizePx;
            params.height = avatarSizePx;
            holder.avatar.setLayoutParams(params);
        }
        holder.avatar.setImageDrawable(new SpeakerAvatarDrawable(turn.getSpeakerIndex()));
        holder.avatar.setContentDescription(holder.itemView.getContext()
                .getString(R.string.transcript_speaker_number, turn.getSpeakerIndex() + 1));
        holder.text.setTextSizePx(textSizePx);
        holder.text.setTurn(turn);
        applyReveal(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            applyReveal(holder, position);
        }
    }

    private void applyReveal(ViewHolder holder, int position) {
        boolean current = position == visibleCount - 1;
        holder.text.setTextColor(current ? CURRENT_COLOR : PAST_COLOR);
        holder.text.setRevealTime(current ? currentTime : Long.MAX_VALUE);
        holder.avatar.setAlpha(current ? 1f : 0.55f);
    }

    @Override
    public int getItemCount() {
        return visibleCount;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final RevealTextView text;

        ViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            text = itemView.findViewById(R.id.text);
        }
    }
}
