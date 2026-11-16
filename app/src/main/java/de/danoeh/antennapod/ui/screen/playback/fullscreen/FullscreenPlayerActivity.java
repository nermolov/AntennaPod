package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.session.MediaController;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.palette.graphics.Palette;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.RequestOptions;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.EpisodeDownloadEvent;
import de.danoeh.antennapod.event.PlayerErrorEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.event.playback.PlaybackServiceEvent;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.model.feed.DiarizedTranscript;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.transcription.JobStatus;
import de.danoeh.antennapod.net.transcription.Lookup;
import de.danoeh.antennapod.net.transcription.Partial;
import de.danoeh.antennapod.net.transcription.SubmitResult;
import de.danoeh.antennapod.net.transcription.TranscriptionClient;
import de.danoeh.antennapod.net.transcription.TranscriptionException;
import de.danoeh.antennapod.parser.transcript.ServiceTranscriptParser;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.GeneratedTranscript;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter;
import de.danoeh.antennapod.ui.screen.playback.MediaPlayerErrorDialog;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class FullscreenPlayerActivity extends AppCompatActivity {
    private static final String TAG = "FullscreenPlayer";
    private static final long SEEK_THROTTLE_MS = 300;
    private static final long TRANSCRIPT_LEAD_MS = 450;
    private static final long POLL_INTERVAL_SECONDS = 3;
    private static final int PROGRESS_MAX = 1000;
    private static final int DESIGN_WIDTH_DP = 1280;
    private static final float DESIGN_SCALE = 0.95f;
    private static final int MIN_DENSITY_DPI = 120;
    private static final int MAX_DENSITY_DPI = 640;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TITLE_DIM = 0x80FFFFFF;

    private enum State { LOADING, PROMPT_BUSY, PROMPT_DOWNLOAD, DOWNLOADING, PROGRESS, TRANSCRIPT, NONE, FAILED }

    private enum Retry { LOOKUP, SUBMIT, DOWNLOAD, BUSY }

    private static class Outcome {
        final State state;
        final String jobId;
        final DiarizedTranscript transcript;
        final String message;
        final Retry retry;

        Outcome(State state, String jobId, DiarizedTranscript transcript, String message, Retry retry) {
            this.state = state;
            this.jobId = jobId;
            this.transcript = transcript;
            this.message = message;
            this.retry = retry;
        }

        static Outcome of(State state) {
            return new Outcome(state, null, null, null, null);
        }

        static Outcome progress(String jobId) {
            return new Outcome(State.PROGRESS, jobId, null, null, null);
        }

        static Outcome transcript(DiarizedTranscript transcript) {
            return new Outcome(State.TRANSCRIPT, null, transcript, null, null);
        }

        static Outcome failed(String message, Retry retry) {
            return new Outcome(State.FAILED, null, null, message, retry);
        }
    }

    private enum PollKind { UPDATE, UNREACHABLE, FINISHED, LOST }

    private static class Batch {
        final int from;
        final int nextSince;
        final List<DiarizedTranscript.Turn> turns;
        final long readyUntilMs;
        final boolean complete;

        Batch(int from, Partial partial, List<DiarizedTranscript.Turn> turns) {
            this.from = from;
            this.nextSince = partial.turnCount;
            this.turns = turns;
            this.readyUntilMs = partial.readyUntilMs;
            this.complete = partial.complete;
        }
    }

    private static class PollResult {
        final PollKind kind;
        final JobStatus status;
        final Outcome outcome;
        final Batch batch;

        PollResult(PollKind kind, JobStatus status, Outcome outcome) {
            this(kind, status, outcome, null);
        }

        PollResult(PollKind kind, JobStatus status, Outcome outcome, Batch batch) {
            this.kind = kind;
            this.status = status;
            this.outcome = outcome;
            this.batch = batch;
        }
    }

    private final PlaybackClock clock = new PlaybackClock();
    private final TranscriptRowAdapter adapter = new TranscriptRowAdapter();
    private final TranscriptPlaybackGate gate = new TranscriptPlaybackGate(TRANSCRIPT_LEAD_MS);
    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> onFrame();

    private TranscriptListView transcriptList;
    private LinearLayoutManager layoutManager;
    private ImageView coverImage;
    private TextView episodeTitle;
    private TextView feedTitle;
    private ProgressBar progress;
    private TextView elapsedText;
    private TextView durationText;
    private View statusOverlay;
    private TextView statusTitle;
    private ProgressBar statusProgress;
    private TextView statusDetail;
    private LinearLayout statusButtons;
    private Button primaryButton;
    private Button secondaryButton;

    private FeedMedia media;
    private Disposable disposable;
    private Disposable workDisposable;
    private Disposable pollDisposable;
    private State state = State.LOADING;
    private Retry retry = Retry.LOOKUP;
    private String jobId;
    private LiveTranscriptModel liveModel;
    private ServiceTranscriptParser.Accumulator accumulator = new ServiceTranscriptParser.Accumulator();
    private volatile int turnsSince = 0;
    private JobStatus lastStatus;
    private boolean resumeAfter = false;
    private boolean downloadSeen = false;
    private long hashedMediaId = -1;
    private String hashedValue;
    private float speed = 1.0f;
    private boolean alignPending = false;
    private boolean smoothScrollPending = false;
    private final List<View> fadeInRows = new ArrayList<>();
    private boolean jumpPending = true;
    private boolean modelReady = false;
    private boolean started = false;
    private int lastListHeight = -1;
    private long lastTime = -1;
    private long lastDisplayedSecond = -1;
    private long lastDisplayedDuration = -1;
    private long lastSeekKeyTime = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        DisplayMetrics metrics = newBase.getResources().getDisplayMetrics();
        int longEdgePx = Math.max(metrics.widthPixels, metrics.heightPixels);
        int designDpi = Math.round(longEdgePx * (float) DisplayMetrics.DENSITY_DEFAULT / DESIGN_WIDTH_DP
                * DESIGN_SCALE);
        Configuration configuration = new Configuration(newBase.getResources().getConfiguration());
        configuration.densityDpi = Math.max(MIN_DENSITY_DPI, Math.min(MAX_DENSITY_DPI, designDpi));
        super.attachBaseContext(newBase.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyBackground(PlayerColorScheme.DEFAULT_BACKGROUND);
        setContentView(R.layout.fullscreen_player_activity);
        hideSystemBars();

        transcriptList = findViewById(R.id.transcriptList);
        coverImage = findViewById(R.id.coverImage);
        final float coverRadius = getResources().getDimension(R.dimen.fullscreen_cover_corner_radius);
        coverImage.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), coverRadius);
            }
        });
        coverImage.setClipToOutline(true);
        episodeTitle = findViewById(R.id.episodeTitle);
        feedTitle = findViewById(R.id.feedTitle);
        progress = findViewById(R.id.progress);
        elapsedText = findViewById(R.id.elapsedText);
        durationText = findViewById(R.id.durationText);
        statusOverlay = findViewById(R.id.statusOverlay);
        statusTitle = findViewById(R.id.statusTitle);
        statusProgress = findViewById(R.id.statusProgress);
        statusDetail = findViewById(R.id.statusDetail);
        statusButtons = findViewById(R.id.statusButtons);
        primaryButton = findViewById(R.id.statusPrimaryButton);
        secondaryButton = findViewById(R.id.statusSecondaryButton);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        transcriptList.setLayoutManager(layoutManager);
        transcriptList.setAdapter(adapter);
        DefaultItemAnimator animator = new DefaultItemAnimator() {
            @Override
            public boolean animateAdd(RecyclerView.ViewHolder holder) {
                holder.itemView.setAlpha(0f);
                fadeInRows.add(holder.itemView);
                dispatchAddFinished(holder);
                return false;
            }

            @Override
            public boolean animatePersistence(@NonNull RecyclerView.ViewHolder viewHolder,
                                              @NonNull RecyclerView.ItemAnimator.ItemHolderInfo preInfo,
                                              @NonNull RecyclerView.ItemAnimator.ItemHolderInfo postInfo) {
                dispatchMoveFinished(viewHolder);
                return false;
            }

            private final Map<RecyclerView.ViewHolder, ValueAnimator> holds = new HashMap<>();

            @Override
            public boolean animateDisappearance(@NonNull RecyclerView.ViewHolder viewHolder,
                                                @NonNull RecyclerView.ItemAnimator.ItemHolderInfo preLayoutInfo,
                                                @Nullable RecyclerView.ItemAnimator.ItemHolderInfo postLayoutInfo) {
                if (postLayoutInfo == null) {
                    return super.animateDisappearance(viewHolder, preLayoutInfo, postLayoutInfo);
                }
                ValueAnimator hold = ValueAnimator.ofFloat(0f, 1f);
                hold.setDuration(RevealTextView.SLIDE_DURATION_MS);
                hold.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        holds.remove(viewHolder);
                        dispatchMoveFinished(viewHolder);
                    }
                });
                holds.put(viewHolder, hold);
                hold.start();
                return false;
            }

            @Override
            public boolean isRunning() {
                return !holds.isEmpty() || super.isRunning();
            }

            @Override
            public void endAnimation(@NonNull RecyclerView.ViewHolder item) {
                ValueAnimator hold = holds.get(item);
                if (hold != null) {
                    hold.end();
                }
                super.endAnimation(item);
            }

            @Override
            public void endAnimations() {
                for (ValueAnimator hold : new ArrayList<>(holds.values())) {
                    hold.end();
                }
                super.endAnimations();
            }

            @Override
            public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder,
                                         int fromX, int fromY, int toX, int toY) {
                if (oldHolder == newHolder) {
                    dispatchChangeFinished(oldHolder, true);
                    return false;
                }
                return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY);
            }
        };
        animator.setSupportsChangeAnimations(false);
        transcriptList.setItemAnimator(animator);
        transcriptList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    fadeInPendingRow();
                }
            }
        });
        transcriptList.getViewTreeObserver().addOnPreDrawListener(this::updateStickyAvatars);
        transcriptList.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                                  oldBottom) -> onListLayout(bottom - top));
    }

    private boolean updateStickyAvatars() {
        pinToEnd();
        float listTop = transcriptList.getPaddingTop()
                + getResources().getDimension(R.dimen.fullscreen_transcript_edge_gap)
                - transcriptList.getSlideOffset();
        for (int i = 0; i < transcriptList.getChildCount(); i++) {
            View row = transcriptList.getChildAt(i);
            View avatar = row.findViewById(R.id.avatar);
            if (avatar == null) {
                continue;
            }
            float translation = 0;
            float avatarTop = row.getY() + avatar.getTop();
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

    private void fadeInPendingRow() {
        long duration = transcriptList.getItemAnimator().getAddDuration();
        for (View row : fadeInRows) {
            row.animate().alpha(1f).setDuration(duration).start();
        }
        fadeInRows.clear();
    }

    private void pinToEnd() {
        if (!modelReady || jumpPending || transcriptList.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }
        int last = adapter.getItemCount() - 1;
        View lastRow = last >= 0 ? layoutManager.findViewByPosition(last) : null;
        if (lastRow == null) {
            return;
        }
        int remaining = lastRow.getBottom() - (transcriptList.getHeight() - transcriptList.getPaddingBottom());
        if (smoothScrollPending) {
            smoothScrollPending = false;
            if (remaining > 0) {
                transcriptList.smoothScrollBy(0, remaining, new DecelerateInterpolator(),
                        (int) RevealTextView.SLIDE_DURATION_MS);
            } else {
                fadeInPendingRow();
            }
            return;
        }
        if (remaining != 0) {
            transcriptList.scrollBy(0, remaining);
        }
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
        stopWork();
        stopPolling();
    }

    private void loadMedia() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.<FeedMedia>fromCallable(() -> {
            return DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onLoaded,
                        error -> Log.e(TAG, Log.getStackTraceString(error)),
                        this::finish);
    }

    private void onLoaded(FeedMedia loaded) {
        final boolean sameMedia = media != null && media.getId() == loaded.getId();
        if (!sameMedia) {
            stopWork();
            stopPolling();
            state = State.LOADING;
            jobId = null;
            resumeAfter = false;
            downloadSeen = false;
        }
        media = loaded;
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
            liveModel = new LiveTranscriptModel(new DiarizedTranscript(Collections.emptyList()));
            adapter.setModel(liveModel);
            modelReady = true;
            jumpPending = true;
            lastTime = -1;
            lastDisplayedSecond = -1;
            lastDisplayedDuration = -1;
        }
        resumeState();
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
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target,
                                                boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        if (resource instanceof BitmapDrawable) {
                            extractBackground(((BitmapDrawable) resource).getBitmap());
                        }
                        return false;
                    }
                })
                .into(coverImage);
    }

    private void extractBackground(Bitmap bitmap) {
        Palette.from(bitmap).generate(palette -> {
            if (palette != null) {
                int primary = PlayerColorScheme.primary(palette);
                applyBackground(PlayerColorScheme.background(primary));
            }
        });
    }

    private void applyBackground(int background) {
        getWindow().setBackgroundDrawable(
                new NoiseBackgroundDrawable(background, PlayerColorScheme.noise(background),
                        getResources().getDisplayMetrics().density));
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
        if (media == null) {
            return;
        }
        long playingId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        if (playingId > 0 && playingId != media.getId()) {
            loadMedia();
        } else if (isPlaybackBlocked() && isPlaybackActive()) {
            setPlaying(false);
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEpisodeDownload(EpisodeDownloadEvent event) {
        checkDownload();
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
        if (state == State.PROGRESS) {
            updateGate(time);
        }
        boolean jump = jumpPending;
        if (time == lastTime && !jump) {
            return;
        }
        lastTime = time;
        final int previousCount = adapter.getItemCount();
        adapter.setTime(time + TRANSCRIPT_LEAD_MS);
        updateProgress(time);
        int count = adapter.getItemCount();
        if (count == 0) {
            return;
        }
        if (jump || count - previousCount > 1) {
            jumpPending = false;
            alignPending = true;
            smoothScrollPending = true;
            transcriptList.stopScroll();
            layoutManager.scrollToPositionWithOffset(count - 1, 0);
        } else if (count != previousCount) {
            alignPending = true;
            smoothScrollPending = true;
        }
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
            elapsedText.setText(formatClock(shownPosition));
            if (duration > 0) {
                progress.setProgress((int) (time * progress.getMax() / duration));
            }
        }
        if (duration != lastDisplayedDuration) {
            lastDisplayedDuration = duration;
            durationText.setText(formatClock(converter.convert((int) duration)));
        }
    }

    private static String formatClock(int millis) {
        if (millis < 3600 * 1000) {
            return Converter.getDurationStringShort(Math.max(millis, 0), false);
        }
        return Converter.getDurationStringLong(millis);
    }

    private boolean buttonsVisible() {
        return statusOverlay.getVisibility() == View.VISIBLE && statusButtons.getVisibility() == View.VISIBLE;
    }

    private boolean isPlaybackBlocked() {
        return (state == State.PROGRESS && gate.isBlocked()) || state == State.DOWNLOADING;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (buttonsVisible()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (getCurrentFocus() == null || !isButton(getCurrentFocus())) {
                        primaryButton.requestFocus();
                        return true;
                    }
                    return super.onKeyDown(keyCode, event);
                default:
                    break;
            }
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (event.getRepeatCount() == 0 && !isPlaybackBlocked()) {
                    setPlaying(!clock.isPlaying());
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (event.getRepeatCount() == 0 && !isPlaybackBlocked()) {
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
                if (!isPlaybackBlocked()) {
                    seek(false);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!isPlaybackBlocked()) {
                    seek(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private boolean isButton(View view) {
        return view == primaryButton || view == secondaryButton;
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

    private boolean isRunning(@Nullable Disposable d) {
        return d != null && !d.isDisposed();
    }

    private void stopWork() {
        if (workDisposable != null) {
            workDisposable.dispose();
            workDisposable = null;
        }
    }

    private void stopPolling() {
        if (pollDisposable != null) {
            pollDisposable.dispose();
            pollDisposable = null;
        }
    }

    private void resumeState() {
        if (!started || media == null) {
            return;
        }
        switch (state) {
            case LOADING:
                startLookup();
                break;
            case PROGRESS:
                if (jobId != null) {
                    if (gate.isBlocked()) {
                        showProgress(lastStatus);
                    }
                    startPolling();
                } else {
                    startSubmit();
                }
                break;
            case DOWNLOADING:
                showDownloading(DownloadServiceInterface.get().getProgress(media.getDownloadUrl()));
                checkDownload();
                break;
            default:
                renderCurrentState();
                break;
        }
    }

    private void renderCurrentState() {
        switch (state) {
            case PROMPT_BUSY:
                showBusyPrompt();
                break;
            case PROMPT_DOWNLOAD:
                showDownloadPrompt();
                break;
            case TRANSCRIPT:
                statusOverlay.setVisibility(View.GONE);
                break;
            case NONE:
                showStatus(getString(R.string.no_transcript_label), COLOR_TITLE_DIM, null, -2, 0, null, 0, null);
                break;
            default:
                break;
        }
    }

    private void showStatus(CharSequence title, int titleColor, @Nullable CharSequence detail, int progressPercent,
                            int primaryLabel, @Nullable Runnable primaryAction,
                            int secondaryLabel, @Nullable Runnable secondaryAction) {
        statusOverlay.setVisibility(View.VISIBLE);
        statusTitle.setText(title);
        statusTitle.setTextColor(titleColor);
        if (detail == null) {
            statusDetail.setVisibility(View.GONE);
        } else {
            statusDetail.setText(detail);
            statusDetail.setVisibility(View.VISIBLE);
        }
        if (progressPercent == -2) {
            statusProgress.setVisibility(View.GONE);
        } else {
            statusProgress.setVisibility(View.VISIBLE);
            statusProgress.setIndeterminate(progressPercent < 0);
            if (progressPercent >= 0) {
                statusProgress.setProgress(progressPercent * PROGRESS_MAX / 100);
            }
        }
        bindButton(primaryButton, primaryLabel, primaryAction);
        bindButton(secondaryButton, secondaryLabel, secondaryAction);
        boolean any = primaryLabel != 0;
        statusButtons.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any) {
            View focused = getCurrentFocus();
            if (focused == null || !isButton(focused) || focused.getVisibility() != View.VISIBLE) {
                primaryButton.requestFocus();
            }
        }
    }

    private void bindButton(Button button, int label, @Nullable Runnable action) {
        if (label == 0 || action == null) {
            button.setVisibility(View.GONE);
            button.setOnClickListener(null);
            return;
        }
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        button.setVisibility(View.VISIBLE);
    }

    private void showLoading() {
        showStatus(getString(R.string.transcript_checking), COLOR_TITLE, null, -1, 0, null, 0, null);
    }

    private void showBusyPrompt() {
        showStatus(getString(R.string.transcript_busy_title), COLOR_TITLE,
                getString(R.string.transcript_busy_detail), -2,
                R.string.transcript_cancel_and_transcribe_label, this::cancelOthersAndSubmit,
                R.string.transcript_not_now_label, this::declineTranscript);
    }

    private void showDownloadPrompt() {
        showStatus(getString(R.string.transcript_download_prompt_title), COLOR_TITLE, null, -2,
                R.string.transcript_download_and_transcribe_label, this::startDownload,
                R.string.transcript_not_now_label, this::declineTranscript);
    }

    private void showDownloading(int percent) {
        showStatus(getString(R.string.transcript_downloading_title), COLOR_TITLE,
                percent >= 0 ? getString(R.string.percentage_format, percent) : null, Math.max(percent, -1), 0, null,
                0, null);
    }

    private void showProgress(@Nullable JobStatus status) {
        boolean catchingUp = liveModel != null && liveModel.getTurnCount() > 0;
        String title = getString(catchingUp
                ? R.string.transcript_catching_up_title : R.string.transcript_progress_title);
        if (status == null) {
            showStatus(title, COLOR_TITLE, null, -1, 0, null, 0, null);
            return;
        }
        String stage = getString(TranscriptionProgressFormatter.stageLabel(status.status, status.stage));
        if (catchingUp && liveModel.getReadyUntilMs() > 0) {
            stage = getString(R.string.transcript_ready_until, formatClock((int) liveModel.getReadyUntilMs()));
        }
        int percent = TranscriptionProgressFormatter.percent(status.overallProgress);
        boolean queued = TranscriptionProgressFormatter.isQueued(status.status);
        String detail = stage;
        if (queued && status.queuePosition != null && status.queuePosition > 0) {
            detail = getString(R.string.transcript_detail_format, stage,
                    getString(R.string.transcript_queue_ahead, status.queuePosition));
        } else if (!queued && percent >= 0) {
            detail = getString(R.string.transcript_detail_format, stage,
                    getString(R.string.percentage_format, percent));
        }
        showStatus(title, COLOR_TITLE, detail, queued ? -1 : percent,
                queued ? R.string.transcript_cancel_label : 0, queued ? this::cancelJob : null, 0, null);
    }

    private void showFailed(String title, @Nullable String message, Retry retryMode) {
        retry = retryMode;
        showStatus(title, COLOR_TITLE, message, -2,
                R.string.transcript_retry_label, this::retryAfterFailure,
                R.string.transcript_not_now_label, this::declineTranscript);
    }

    private void declineTranscript() {
        stopWork();
        stopPolling();
        enterNone();
    }

    private void enterNone() {
        state = State.NONE;
        renderCurrentState();
    }

    private void retryAfterFailure() {
        switch (retry) {
            case SUBMIT:
                startSubmit();
                break;
            case DOWNLOAD:
                startDownload();
                break;
            case BUSY:
                cancelOthersAndSubmit();
                break;
            default:
                startLookup();
                break;
        }
    }

    private void pauseForWork() {
        resumeAfter = resumeAfter || isPlaybackActive();
        setPlaying(false);
    }

    private void applyOutcome(Outcome outcome) {
        switch (outcome.state) {
            case TRANSCRIPT:
                showTranscript(outcome.transcript);
                break;
            case PROGRESS:
                if (outcome.jobId == null) {
                    stopWork();
                    startSubmit();
                } else {
                    enterProgress(outcome.jobId);
                }
                break;
            case PROMPT_BUSY:
                state = State.PROMPT_BUSY;
                showBusyPrompt();
                break;
            case PROMPT_DOWNLOAD:
                state = State.PROMPT_DOWNLOAD;
                showDownloadPrompt();
                break;
            case FAILED:
                state = State.FAILED;
                showFailed(getString(outcome.retry == Retry.LOOKUP
                        ? R.string.transcript_lookup_failed_title : R.string.transcript_failed_title),
                        outcome.message, outcome.retry);
                break;
            case NONE:
                state = State.NONE;
                if (outcome.message != null) {
                    showStatus(outcome.message, COLOR_TITLE_DIM, null, -2, 0, null, 0, null);
                } else {
                    renderCurrentState();
                }
                break;
            default:
                break;
        }
    }

    private void enterProgress(String newJobId) {
        stopPolling();
        jobId = newJobId;
        state = State.PROGRESS;
        turnsSince = 0;
        accumulator = new ServiceTranscriptParser.Accumulator();
        gate.reset();
        lastStatus = null;
        liveModel = new LiveTranscriptModel(new DiarizedTranscript(Collections.emptyList()));
        adapter.setModel(liveModel);
        modelReady = true;
        jumpPending = true;
        lastTime = -1;
        pauseForWork();
        showProgress(null);
        startPolling();
    }

    private void updateGate(long position) {
        if (liveModel == null) {
            return;
        }
        boolean wasBlocked = gate.isBlocked();
        boolean blocked = gate.update(position, liveModel.getReadyUntilMs(), liveModel.isComplete());
        if (blocked == wasBlocked) {
            return;
        }
        if (blocked) {
            resumeAfter = resumeAfter || clock.isPlaying() || isPlaybackActive();
            setPlaying(false);
            showProgress(lastStatus);
        } else {
            statusOverlay.setVisibility(View.GONE);
            if (resumeAfter) {
                resumeAfter = false;
                setPlaying(true);
            }
        }
    }

    private void applyBatch(@Nullable Batch batch) {
        if (batch == null || liveModel == null || batch.from != turnsSince) {
            return;
        }
        turnsSince = batch.nextSince;
        liveModel.appendTurns(batch.turns);
        liveModel.setReadyUntilMs(batch.readyUntilMs);
        liveModel.setComplete(batch.complete);
        adapter.onModelGrown();
        lastTime = -1;
    }

    private void finishStreaming() {
        stopPolling();
        state = State.TRANSCRIPT;
        jobId = null;
        lastStatus = null;
        statusOverlay.setVisibility(View.GONE);
        gate.update(0, 0, true);
        if (resumeAfter) {
            resumeAfter = false;
            setPlaying(true);
        }
    }

    private void showTranscript(DiarizedTranscript transcript) {
        stopPolling();
        state = State.TRANSCRIPT;
        jobId = null;
        statusOverlay.setVisibility(View.GONE);
        liveModel = new LiveTranscriptModel(transcript);
        adapter.setModel(liveModel);
        modelReady = true;
        jumpPending = true;
        lastTime = -1;
        if (resumeAfter) {
            resumeAfter = false;
            setPlaying(true);
        }
    }

    private void runWork(Callable<Outcome> callable) {
        stopWork();
        workDisposable = Single.fromCallable(callable)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::applyOutcome,
                        error -> applyOutcome(Outcome.failed(describe(error), Retry.LOOKUP)));
    }

    private static String describe(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private String hashOf(FeedMedia target, File file) throws IOException {
        if (hashedMediaId == target.getId() && hashedValue != null) {
            return hashedValue;
        }
        String value = TranscriptionClient.sha256Hex(file);
        hashedMediaId = target.getId();
        hashedValue = value;
        return value;
    }

    @Nullable
    private static File localFile(FeedMedia target) {
        if (!target.isDownloaded() || target.getLocalFileUrl() == null) {
            return null;
        }
        File file = new File(target.getLocalFileUrl());
        return file.exists() ? file : null;
    }

    private void startLookup() {
        if (isRunning(workDisposable)) {
            return;
        }
        state = State.LOADING;
        showLoading();
        final FeedMedia target = media;
        runWork(() -> lookup(target, true));
    }

    private Outcome lookup(FeedMedia target, boolean autoSubmit) {
        GeneratedTranscript stored = DBReader.getGeneratedTranscript(target.getItemId());
        if (stored != null && stored.getTranscript() != null) {
            return Outcome.transcript(stored.getTranscript());
        }
        if (stored != null && stored.getJobId() != null
                && GeneratedTranscript.STATUS_RUNNING.equals(stored.getStatus())) {
            return Outcome.progress(stored.getJobId());
        }
        if (!UserPreferences.isTranscriptionConfigured()) {
            return new Outcome(State.NONE, null, null, getString(R.string.transcript_not_configured), null);
        }
        File file = localFile(target);
        if (file == null) {
            return Outcome.of(State.PROMPT_DOWNLOAD);
        }
        try {
            TranscriptionClient client = TranscriptionClient.fromPreferences();
            Lookup found = client.lookupBySha256(hashOf(target, file));
            if (found == null) {
                if (!client.listActiveJobs().isEmpty()) {
                    return Outcome.of(State.PROMPT_BUSY);
                }
                if (autoSubmit) {
                    return Outcome.of(State.PROGRESS);
                }
                return Outcome.failed(getString(R.string.transcript_job_lost), Retry.SUBMIT);
            }
            if (found.isDone()) {
                return finish(target, client.getTranscriptResult(found.transcriptId));
            }
            DBWriter.setTranscriptionJob(target.getItemId(), found.jobId).get();
            return Outcome.progress(found.jobId);
        } catch (IOException | JSONException | InterruptedException | ExecutionException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return Outcome.failed(describe(e), Retry.LOOKUP);
        }
    }

    private Outcome finish(FeedMedia target, String json) throws JSONException, InterruptedException,
            ExecutionException {
        DiarizedTranscript transcript = ServiceTranscriptParser.parse(json);
        DBWriter.saveGeneratedTranscript(target.getItemId(), transcript).get();
        return Outcome.transcript(transcript);
    }

    private void startSubmit() {
        if (media == null || isRunning(workDisposable)) {
            return;
        }
        stopPolling();
        jobId = null;
        state = State.PROGRESS;
        pauseForWork();
        showProgress(null);
        final FeedMedia target = media;
        runWork(() -> submit(target));
    }

    private Outcome submit(FeedMedia target) {
        File file = localFile(target);
        if (file == null) {
            return Outcome.of(State.PROMPT_DOWNLOAD);
        }
        try {
            FeedItem item = target.getItem();
            TranscriptionClient client = TranscriptionClient.fromPreferences();
            String context = target.getFeedTitle() + ", " + target.getEpisodeTitle();
            SubmitResult result = client.submit(file, hashOf(target, file), TranscriptionClient.episodeKey(item),
                    context);
            if (result.isDone()) {
                String json = result.transcriptId != null
                        ? client.getTranscriptResult(result.transcriptId) : client.getJobResult(result.id);
                return finish(target, json);
            }
            DBWriter.setTranscriptionJob(target.getItemId(), result.id).get();
            return Outcome.progress(result.id);
        } catch (IOException | JSONException | InterruptedException | ExecutionException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return Outcome.failed(describe(e), Retry.SUBMIT);
        }
    }

    private void startPolling() {
        if (isRunning(pollDisposable) || jobId == null || media == null) {
            return;
        }
        final String id = jobId;
        final FeedMedia target = media;
        pollDisposable = Observable.interval(0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS, Schedulers.io())
                .concatMapSingle(tick -> Single.fromCallable(() -> pollOnce(target, id)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPoll, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private PollResult pollOnce(FeedMedia target, String id) {
        TranscriptionClient client = TranscriptionClient.fromPreferences();
        try {
            JobStatus status = client.getJob(id);
            if (status.isFailed()) {
                String message = status.error != null ? status.error : status.message;
                DBWriter.setTranscriptionError(target.getItemId(), message).get();
                return new PollResult(PollKind.FINISHED, status, Outcome.failed(message, Retry.SUBMIT));
            }
            Batch batch = null;
            if (!TranscriptionProgressFormatter.isQueued(status.status)) {
                int from = turnsSince;
                Partial partial = client.getPartial(id, from);
                batch = new Batch(from, partial, accumulator.add(partial.turnsJson));
            }
            if (status.isDone()) {
                Outcome outcome = finish(target, client.getJobResult(id));
                try {
                    client.deleteJob(id);
                } catch (IOException ignored) {
                    Log.d(TAG, "Could not delete finished job");
                }
                return new PollResult(PollKind.FINISHED, status, outcome, batch);
            }
            return new PollResult(PollKind.UPDATE, status, null, batch);
        } catch (TranscriptionException e) {
            if (e.isNotFound()) {
                return new PollResult(PollKind.LOST, null, null);
            }
            return new PollResult(PollKind.UNREACHABLE, null, null);
        } catch (JSONException | InterruptedException | ExecutionException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            return new PollResult(PollKind.FINISHED, null, Outcome.failed(describe(e), Retry.SUBMIT));
        }
    }

    private void onPoll(PollResult result) {
        if (state != State.PROGRESS) {
            return;
        }
        switch (result.kind) {
            case UPDATE:
                lastStatus = result.status;
                applyBatch(result.batch);
                updateGate(clock.positionAt(SystemClock.elapsedRealtime()));
                if (gate.isBlocked()) {
                    showProgress(result.status);
                }
                break;
            case UNREACHABLE:
                if (gate.isBlocked()) {
                    statusDetail.setText(R.string.transcript_server_unreachable);
                    statusDetail.setVisibility(View.VISIBLE);
                }
                break;
            case LOST:
                stopPolling();
                jobId = null;
                final FeedMedia target = media;
                state = State.LOADING;
                stopWork();
                workDisposable = Single.fromCallable(() -> {
                    DBWriter.clearTranscriptionJob(target.getItemId()).get();
                    return lookup(target, false);
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::applyOutcome,
                                error -> applyOutcome(Outcome.failed(describe(error), Retry.LOOKUP)));
                break;
            default:
                if (result.outcome.state == State.TRANSCRIPT && result.batch != null
                        && result.batch.from == turnsSince && liveModel != null) {
                    applyBatch(result.batch);
                    finishStreaming();
                } else {
                    stopPolling();
                    applyOutcome(result.outcome);
                }
                break;
        }
    }

    private void cancelJob() {
        if (jobId == null || media == null) {
            return;
        }
        final String id = jobId;
        final FeedMedia target = media;
        stopPolling();
        stopWork();
        workDisposable = Single.fromCallable(() -> {
            try {
                TranscriptionClient.fromPreferences().deleteJob(id);
            } catch (TranscriptionException e) {
                if (!e.isNotFound()) {
                    return Outcome.progress(id);
                }
            }
            DBWriter.clearTranscriptionJob(target.getItemId()).get();
            return Outcome.of(State.NONE);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(outcome -> {
                    jobId = null;
                    if (outcome.state == State.NONE) {
                        resumeAfter = false;
                    }
                    applyOutcome(outcome);
                }, error -> applyOutcome(Outcome.progress(id)));
    }

    private void cancelOthersAndSubmit() {
        if (media == null || isRunning(workDisposable)) {
            return;
        }
        stopPolling();
        jobId = null;
        state = State.PROGRESS;
        pauseForWork();
        showProgress(null);
        final FeedMedia target = media;
        runWork(() -> {
            try {
                TranscriptionClient.fromPreferences().cancelAll();
            } catch (TranscriptionException e) {
                Log.e(TAG, Log.getStackTraceString(e));
                return Outcome.failed(describe(e), Retry.BUSY);
            }
            return submit(target);
        });
    }

    private void startDownload() {
        if (media == null || media.getItem() == null) {
            return;
        }
        state = State.DOWNLOADING;
        downloadSeen = false;
        pauseForWork();
        showDownloading(-1);
        DownloadServiceInterface.get().downloadNow(this, media.getItem(), true);
        stopPolling();
        pollDisposable = Observable.interval(1, 1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
                .subscribe(tick -> checkDownload(), error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private void checkDownload() {
        if (state != State.DOWNLOADING || media == null || !started) {
            return;
        }
        String url = media.getDownloadUrl();
        if (DownloadServiceInterface.get().isDownloadingEpisode(url)) {
            downloadSeen = true;
            showDownloading(DownloadServiceInterface.get().getProgress(url));
            return;
        }
        final long mediaId = media.getId();
        if (downloadSeen || media.isDownloaded()) {
            downloadSeen = false;
            runDownloadFinished(mediaId);
            return;
        }
        if (isRunning(workDisposable)) {
            return;
        }
        workDisposable = Maybe.fromCallable(() -> DBReader.getFeedMedia(mediaId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(fresh -> {
                    workDisposable = null;
                    if (state == State.DOWNLOADING && media != null && media.getId() == fresh.getId()
                            && fresh.isDownloaded()) {
                        downloadSeen = false;
                        runDownloadFinished(mediaId);
                    }
                }, error -> {
                    workDisposable = null;
                    Log.e(TAG, Log.getStackTraceString(error));
                }, () -> workDisposable = null);
    }

    private void runDownloadFinished(long mediaId) {
        stopWork();
        workDisposable = Maybe.fromCallable(() -> DBReader.getFeedMedia(mediaId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(fresh -> {
                    if (media == null || media.getId() != fresh.getId()) {
                        return;
                    }
                    media = fresh;
                    if (localFile(fresh) != null) {
                        workDisposable = null;
                        startSubmit();
                    } else {
                        state = State.FAILED;
                        showFailed(getString(R.string.transcript_failed_title),
                                getString(R.string.transcript_download_failed), Retry.DOWNLOAD);
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
