package de.danoeh.antennapod.ui.screen.drawer;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.common.ImagePlaceholder;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import de.danoeh.antennapod.ui.screen.playback.fullscreen.FullscreenPlayerActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class NavPlayerStatusView {
    private static final String TAG = "NavPlayerStatusView";

    private final View root;
    private final ImageView cover;
    private final View pausedIndicator;
    private Disposable disposable;
    private Playable currentMedia;

    public NavPlayerStatusView(View root) {
        this.root = root;
        this.cover = root.findViewById(R.id.imgvCover);
        this.pausedIndicator = root.findViewById(R.id.pausedIndicator);
        root.setOnClickListener(v -> open());
    }

    public void start() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        load();
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    public void setSelected(boolean selected) {
        root.setSelected(selected);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusEvent(PlayerStatusEvent event) {
        load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlaybackServiceChanged(PlaybackServiceEvent event) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            currentMedia = null;
            root.setVisibility(View.GONE);
        }
    }

    private void open() {
        if (currentMedia == null) {
            return;
        }
        Context context = root.getContext();
        if (currentMedia.getMediaType() == MediaType.VIDEO) {
            context.startActivity(PlaybackServiceInterface.getPlayerActivityIntent(context, currentMedia));
        } else {
            context.startActivity(new Intent(context, FullscreenPlayerActivity.class));
        }
    }

    private void load() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.fromCallable(
                        () -> DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId()))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::update,
                        error -> Log.e(TAG, Log.getStackTraceString(error)),
                        () -> {
                            currentMedia = null;
                            root.setVisibility(View.GONE);
                        });
    }

    private void update(Playable media) {
        Context context = root.getContext();
        currentMedia = media;
        root.setVisibility(View.VISIBLE);
        root.setContentDescription(context.getString(R.string.now_playing_content_description,
                media.getEpisodeTitle()));
        boolean isPlaying = PlaybackServiceInterface.isPlaying
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
        pausedIndicator.setVisibility(isPlaying ? View.GONE : View.VISIBLE);

        float radius = 12 * context.getResources().getDisplayMetrics().density;
        RequestOptions options = new RequestOptions()
                .placeholder(ImagePlaceholder.getDrawable(context, radius))
                .error(ImagePlaceholder.getDrawable(context, radius))
                .transform(new CenterCrop(), new RoundedCorners((int) radius))
                .dontAnimate();
        Glide.with(context)
                .load(ImageResourceUtils.getEpisodeListImageLocation(media))
                .error(Glide.with(context)
                        .load(ImageResourceUtils.getFallbackImageLocation(media))
                        .apply(options))
                .apply(options)
                .into(cover);
    }
}
