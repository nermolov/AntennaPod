package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import de.danoeh.antennapod.model.feed.Transcript;
import de.danoeh.antennapod.model.feed.TranscriptSegment;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public final class FakeTranscriptGenerator {
    private static final long DEFAULT_DURATION_MS = 60 * 60 * 1000L;
    private static final String[] SPEAKERS = {"Speaker 1", "Speaker 2", "Speaker 3"};
    private static final String[] SENTENCES = {
        "Welcome back to the show.",
        "So, thanks for having me.",
        "Yeah, that makes a lot of sense.",
        "I think the interesting part is how it all started.",
        "Honestly, I did not expect it to go that well.",
        "We spent about three months just figuring out what the problem actually was.",
        "That is a really good question.",
        "Right.",
        "And then, out of nowhere, everything changed.",
        "I remember sitting in the kitchen at two in the morning, staring at the screen.",
        "It sounds simple, but it took us years to get there.",
        "Can you walk us through what that looked like day to day?",
        "Well, it depends on who you ask.",
        "Most people assume it is all about the technology, but it really is not.",
        "Exactly, and that is what surprised me the most.",
        "Let me give you an example.",
        "A friend of mine tried the same thing and failed spectacularly.",
        "I would say the biggest lesson was to listen more than you talk.",
        "That is fair.",
        "Looking back, I would do a few things differently.",
        "There was this one moment where the whole team just went quiet.",
        "But we kept going, because we believed in it.",
        "Okay, so what happens next?",
        "I am not sure anyone really knows yet, to be honest.",
        "The community around it has been incredible, though.",
        "It is funny how small decisions end up mattering so much later on.",
        "I could talk about this for hours.",
        "Anyway, that is a story for another episode.",
        "Do you think people are ready for that kind of change?",
        "Probably not, but that has never stopped anybody before."
    };

    private FakeTranscriptGenerator() {
    }

    public static Transcript generate(long seed, long durationMs) {
        long total = durationMs <= 0 ? DEFAULT_DURATION_MS : durationMs;
        Random random = new Random(seed);
        Transcript transcript = new Transcript();
        Set<String> speakers = new LinkedHashSet<>();
        for (String speaker : SPEAKERS) {
            speakers.add(speaker);
        }
        transcript.setSpeakers(speakers);

        long time = 300;
        int speakerIndex = random.nextInt(SPEAKERS.length);
        boolean first = true;
        while (time < total) {
            if (!first) {
                speakerIndex = (speakerIndex + 1 + random.nextInt(SPEAKERS.length - 1)) % SPEAKERS.length;
                time += 600 + random.nextInt(901);
            }
            first = false;
            String speaker = SPEAKERS[speakerIndex];
            long turnLimit = 2000 + (long) (random.nextDouble() * random.nextDouble() * 23000);
            long turnStart = time;
            int sentences = 1 + random.nextInt(4);
            for (int s = 0; s < sentences && time < total; s++) {
                if (s > 0) {
                    time += 300 + random.nextInt(401);
                }
                String[] words = SENTENCES[random.nextInt(SENTENCES.length)].split(" ");
                for (String word : words) {
                    if (time >= total) {
                        break;
                    }
                    long end = time + 250 + random.nextInt(301);
                    transcript.addSegment(new TranscriptSegment(time, end, word, speaker));
                    time = end + 80;
                }
                if (time - turnStart >= turnLimit) {
                    break;
                }
            }
        }
        return transcript;
    }
}
