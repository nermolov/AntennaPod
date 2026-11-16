package de.danoeh.antennapod.actionbutton;

import android.content.Context;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.playback.service.PlaybackServiceInterface;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.ui.screen.playback.fullscreen.FullscreenPlayerActivity;

final class PlayStarter {
    private PlayStarter() {
    }

    static void playAndOpenPlayer(Context context, FeedMedia media) {
        new PlaybackServiceStarter(context, media)
                .callEvenIfRunning(true)
                .start();

        if (media.getMediaType() == MediaType.VIDEO) {
            context.startActivity(PlaybackServiceInterface.getPlayerActivityIntent(context, media));
        } else {
            context.startActivity(FullscreenPlayerActivity.getIntent(context, media.getId()));
        }
    }
}
