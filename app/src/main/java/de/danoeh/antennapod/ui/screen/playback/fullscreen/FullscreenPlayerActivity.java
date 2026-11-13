package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.PlayerErrorEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter;
import de.danoeh.antennapod.ui.screen.playback.MediaPlayerErrorDialog;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class FullscreenPlayerActivity extends AppCompatActivity {
    private static final String TAG = "FullscreenPlayer";
    private static final long SEEK_THROTTLE_MS = 300;
    private static final float VISIBLE_LINES = 5.6f;
    private static final float LINE_HEIGHT_RATIO = 1.17f;

    private final PlaybackClock clock = new PlaybackClock();
    private final TranscriptRowAdapter adapter = new TranscriptRowAdapter();
    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> onFrame();

    private RecyclerView transcriptList;
    private LinearLayoutManager layoutManager;
    private ImageView coverImage;
    private TextView episodeTitle;
    private TextView feedTitle;
    private ProgressBar progress;
    private TextView elapsedText;
    private TextView durationText;

    private FeedMedia media;
    private Disposable disposable;
    private float speed = 1.0f;
    private boolean jumpPending = true;
    private boolean alignPending = false;
    private boolean modelReady = false;
    private boolean started = false;
    private int lastListHeight = -1;
    private long lastTime = -1;
    private long lastDisplayedSecond = -1;
    private long lastDisplayedDuration = -1;
    private long lastSeekKeyTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF0A0A0A));
        setContentView(R.layout.fullscreen_player_activity);
        hideSystemBars();

        transcriptList = findViewById(R.id.transcriptList);
        coverImage = findViewById(R.id.coverImage);
        episodeTitle = findViewById(R.id.episodeTitle);
        feedTitle = findViewById(R.id.feedTitle);
        progress = findViewById(R.id.progress);
        elapsedText = findViewById(R.id.elapsedText);
        durationText = findViewById(R.id.durationText);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        transcriptList.setLayoutManager(layoutManager);
        transcriptList.setAdapter(adapter);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        transcriptList.setItemAnimator(animator);
        transcriptList.getViewTreeObserver().addOnPreDrawListener(this::updateStickyAvatars);
        transcriptList.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                                  oldBottom) -> onListLayout(bottom - top));
    }

    private boolean updateStickyAvatars() {
        int listTop = transcriptList.getPaddingTop();
        for (int i = 0; i < transcriptList.getChildCount(); i++) {
            View row = transcriptList.getChildAt(i);
            View avatar = row.findViewById(R.id.avatar);
            if (avatar == null) {
                continue;
            }
            float translation = 0;
            int avatarTop = row.getTop() + avatar.getTop();
            if (avatarTop < listTop) {
                float maxTranslation = row.getHeight() - row.getPaddingBottom()
                        - avatar.getTop() - avatar.getHeight();
                translation = Math.max(0, Math.min(listTop - avatarTop, maxTranslation));
            }
            if (avatar.getTranslationY() != translation) {
                avatar.setTranslationY(translation);
            }
        }
        return true;
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void onListLayout(int height) {
        if (height <= 0 || height == lastListHeight) {
            return;
        }
        lastListHeight = height;
        float lineHeight = height / VISIBLE_LINES;
        adapter.setTextSizePx(lineHeight / LINE_HEIGHT_RATIO);
        jumpPending = true;
        lastTime = -1;
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        EventBus.getDefault().register(this);
        loadMedia();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private static class Loaded {
        final FeedMedia media;
        final LiveTranscriptModel model;

        Loaded(FeedMedia media, LiveTranscriptModel model) {
            this.media = media;
            this.model = model;
        }
    }

    private void loadMedia() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.fromCallable(() -> {
            FeedMedia loaded = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            if (loaded == null) {
                return null;
            }
            return new Loaded(loaded, new LiveTranscriptModel(
                    FakeTranscriptGenerator.generate(loaded.getId(), loaded.getDuration())));
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onLoaded,
                        error -> Log.e(TAG, Log.getStackTraceString(error)),
                        this::finish);
    }

    private void onLoaded(Loaded loaded) {
        final boolean sameMedia = media != null && media.getId() == loaded.media.getId();
        media = loaded.media;
        episodeTitle.setText(media.getEpisodeTitle());
        feedTitle.setText(media.getFeedTitle());
        loadCover();
        long now = SystemClock.elapsedRealtime();
        speed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(media);
        clock.setDuration(media.getDuration());
        clock.setSpeed(speed, now);
        clock.setPlaying(isPlaybackActive(), now);
        if (!sameMedia || !modelReady) {
            clock.update(media.getPosition(), now);
            adapter.setModel(loaded.model);
            modelReady = true;
            jumpPending = true;
            lastTime = -1;
            lastDisplayedSecond = -1;
            lastDisplayedDuration = -1;
        }
    }

    private void loadCover() {
        RequestOptions options = new RequestOptions()
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .centerCrop()
                .dontAnimate();
        Glide.with(this)
                .load(ImageResourceUtils.getEpisodeListImageLocation(media))
                .error(Glide.with(this)
                        .load(ImageResourceUtils.getFallbackImageLocation(media))
                        .apply(options))
                .apply(options)
                .into(coverImage);
    }

    private boolean isPlaybackActive() {
        return PlaybackServiceInterface.isPlaying
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPositionUpdate(PlaybackPositionEvent event) {
        if (event.getPosition() == Playable.INVALID_TIME || event.getDuration() == Playable.INVALID_TIME) {
            return;
        }
        clock.setDuration(event.getDuration());
        if (clock.update(event.getPosition(), SystemClock.elapsedRealtime())) {
            jumpPending = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatus(PlayerStatusEvent event) {
        clock.setPlaying(isPlaybackActive(), SystemClock.elapsedRealtime());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSpeedChanged(SpeedChangedEvent event) {
        speed = event.getNewSpeed();
        clock.setSpeed(speed, SystemClock.elapsedRealtime());
        lastDisplayedSecond = -1;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServiceChanged(PlaybackServiceEvent event) {
        if (event.action == PlaybackServiceEvent.Action.SERVICE_SHUT_DOWN) {
            finish();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerError(PlayerErrorEvent event) {
        MediaPlayerErrorDialog.show(this, event);
    }

    private void onFrame() {
        if (!started) {
            return;
        }
        Choreographer.getInstance().postFrameCallback(frameCallback);
        if (!modelReady) {
            return;
        }
        alignToEnd();
        long now = SystemClock.elapsedRealtime();
        long time = clock.positionAt(now);
        boolean jump = jumpPending;
        if (time == lastTime && !jump) {
            return;
        }
        lastTime = time;
        boolean changed = adapter.setTime(time);
        updateProgress(time);
        int count = adapter.getItemCount();
        if (count == 0) {
            return;
        }
        if (jump) {
            jumpPending = false;
            alignPending = true;
            transcriptList.stopScroll();
            layoutManager.scrollToPositionWithOffset(count - 1, 0);
        } else if (changed) {
            alignPending = true;
            smoothScrollToEnd(count - 1);
        }
    }

    private void smoothScrollToEnd(int position) {
        LinearSmoothScroller scroller = new LinearSmoothScroller(this) {
            @Override
            protected int getVerticalSnapPreference() {
                return SNAP_TO_END;
            }
        };
        scroller.setTargetPosition(position);
        layoutManager.startSmoothScroll(scroller);
    }

    private void alignToEnd() {
        if (!alignPending || jumpPending || transcriptList.isLayoutRequested()
                || transcriptList.getScrollState() != RecyclerView.SCROLL_STATE_IDLE
                || transcriptList.isAnimating()) {
            return;
        }
        int remaining = transcriptList.computeVerticalScrollRange()
                - transcriptList.computeVerticalScrollExtent()
                - transcriptList.computeVerticalScrollOffset();
        if (remaining > 0) {
            transcriptList.smoothScrollBy(0, remaining);
        } else {
            alignPending = false;
        }
    }

    private void updateProgress(long time) {
        long duration = media != null ? media.getDuration() : 0;
        TimeSpeedConverter converter = new TimeSpeedConverter(speed);
        int shownPosition = converter.convert((int) time);
        long second = shownPosition / 1000;
        if (second != lastDisplayedSecond) {
            lastDisplayedSecond = second;
            elapsedText.setText(Converter.getDurationStringLong(shownPosition));
            if (duration > 0) {
                progress.setProgress((int) (time * progress.getMax() / duration));
            }
        }
        if (duration != lastDisplayedDuration) {
            lastDisplayedDuration = duration;
            durationText.setText(Converter.getDurationStringLong(converter.convert((int) duration)));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (event.getRepeatCount() == 0) {
                    setPlaying(!clock.isPlaying());
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (event.getRepeatCount() == 0) {
                    setPlaying(true);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (event.getRepeatCount() == 0) {
                    setPlaying(false);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void setPlaying(boolean play) {
        if (media == null) {
            return;
        }
        clock.setPlaying(play, SystemClock.elapsedRealtime());
        if (play) {
            new PlaybackServiceStarter(this, media).callEvenIfRunning(true).start();
        } else {
            PlaybackController.bindToMedia3Service(this, MediaController::pause);
        }
    }

    private void seek(boolean forward) {
        if (media == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastSeekKeyTime < SEEK_THROTTLE_MS) {
            return;
        }
        lastSeekKeyTime = now;
        final FeedMedia current = media;
        int skipMs = (forward ? UserPreferences.getFastForwardSecs() : UserPreferences.getRewindSecs()) * 1000;
        clock.seekBy(forward ? skipMs : -skipMs, now);
        jumpPending = true;
        PlaybackController.bindToMedia3Service(this, controller -> {
            if (controller.getCurrentMediaItem() != null) {
                if (forward) {
                    controller.seekForward();
                } else {
                    controller.seekBack();
                }
            } else {
                seekWithoutPlaybackService(current, forward
                        ? Math.min(current.getDuration(), current.getPosition() + skipMs)
                        : Math.max(0, current.getPosition() - skipMs));
            }
        });
    }

    private void seekWithoutPlaybackService(FeedMedia target, int position) {
        target.setPosition(position);
        DBWriter.setFeedMediaPlaybackInformation(target);
        EventBus.getDefault().post(new PlaybackPositionEvent(target.getPosition(), target.getDuration()));
    }
}
