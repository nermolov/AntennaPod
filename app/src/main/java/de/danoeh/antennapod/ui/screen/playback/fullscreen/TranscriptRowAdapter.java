package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;

import java.util.List;

public class TranscriptRowAdapter extends RecyclerView.Adapter<TranscriptRowAdapter.ViewHolder> {
    private static final Object PAYLOAD = new Object();
    private static final float BUBBLE_CORNER_DP = 21f;
    private static final float BUBBLE_SHADOW_DY_DP = 4f;
    private static final float BUBBLE_SHADOW_BLUR_DP = 6f;
    private static final int BUBBLE_SHADOW_COLOR = 0x401D1818;

    private LiveTranscriptModel model;
    private SpeakerAvatarAssignment assignment = new SpeakerAvatarAssignment("");
    private Typeface avatarTypeface;
    private Typeface textTypeface;
    private int visibleCount = 0;
    private long currentTime = 0;
    private int trackedIndex = -1;
    private int trackedWords = -1;
    private boolean instantReveal = false;

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

    public void setAssignment(SpeakerAvatarAssignment newAssignment) {
        if (assignment.getKey().equals(newAssignment.getKey())) {
            return;
        }
        assignment = newAssignment;
        if (visibleCount > 0) {
            notifyDataSetChanged();
        }
    }

    public void onModelGrown() {
        trackedWords = -1;
    }

    public int visibleCountAt(long timeMs) {
        return model == null ? 0 : model.visibleTurnCountAt(timeMs);
    }

    public void jumpTo(long timeMs) {
        currentTime = timeMs;
        if (model == null) {
            return;
        }
        instantReveal = true;
        visibleCount = model.visibleTurnCountAt(timeMs);
        int last = visibleCount - 1;
        trackedIndex = last;
        trackedWords = last >= 0 ? model.spokenWordCountAt(last, timeMs) : -1;
        notifyDataSetChanged();
    }

    public boolean setTime(long timeMs) {
        instantReveal = false;
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
        Context context = parent.getContext();
        if (avatarTypeface == null) {
            avatarTypeface = ResourcesCompat.getFont(context, R.font.margarine_regular);
            textTypeface = ResourcesCompat.getFont(context, R.font.haffer_regular);
        }
        View view = LayoutInflater.from(context).inflate(R.layout.fullscreen_transcript_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LiveTranscriptModel.Turn turn = model.getTurn(position);
        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        Context context = holder.itemView.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int speaker = turn.getSpeakerIndex();
        int avatar = assignment.avatarFor(speaker);
        holder.avatar.setImageDrawable(new SpeakerAvatarDrawable(avatar, avatarTypeface, density));
        holder.avatar.setContentDescription(context.getString(R.string.transcript_speaker_number, speaker + 1));
        holder.text.setBubble(new ShadowedRoundRectDrawable(
                PlayerColorScheme.bubble(SpeakerAvatarDrawable.colorFor(avatar)),
                BUBBLE_CORNER_DP * density, density, BUBBLE_SHADOW_DY_DP * density,
                BUBBLE_SHADOW_BLUR_DP * density, BUBBLE_SHADOW_COLOR));
        holder.text.setTypeface(textTypeface);
        holder.text.setTextColor(ResourcesCompat.getColor(context.getResources(),
                R.color.fullscreen_player_bubble_text, context.getTheme()));
        holder.text.setTextSizePx(context.getResources().getDimension(R.dimen.fullscreen_transcript_text_size));
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
        long time = current ? currentTime : Long.MAX_VALUE;
        if (instantReveal) {
            holder.text.setRevealTimeInstant(time);
        } else {
            holder.text.setRevealTime(time);
        }
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
