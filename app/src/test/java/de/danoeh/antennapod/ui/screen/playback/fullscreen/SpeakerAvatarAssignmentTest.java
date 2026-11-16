package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class SpeakerAvatarAssignmentTest {
    @Test
    public void permutationIsBijection() {
        for (int size : new int[] {1, 5, 17}) {
            for (long seed = -50; seed < 50; seed++) {
                int[] sorted = SpeakerAvatarAssignment.permutation(seed * 7919L, size).clone();
                Arrays.sort(sorted);
                for (int i = 0; i < size; i++) {
                    assertEquals(i, sorted[i]);
                }
            }
        }
    }

    @Test
    public void permutationIsDeterministic() {
        assertArrayEquals(SpeakerAvatarAssignment.permutation(42, 5), SpeakerAvatarAssignment.permutation(42, 5));
        assertArrayEquals(new int[] {1, 2, 0, 4, 3}, SpeakerAvatarAssignment.permutation(42, 5));
    }

    @Test
    public void differentKeysGiveDifferentPermutations() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(Arrays.toString(SpeakerAvatarAssignment.permutation(
                    SpeakerAvatarAssignment.seedFor("episode-" + i), 5)));
        }
        assertTrue(seen.size() >= 2);
    }

    @Test
    public void avatarForIsDistinctAndWraps() {
        SpeakerAvatarAssignment assignment = new SpeakerAvatarAssignment("episode-guid");
        Set<Integer> avatars = new HashSet<>();
        for (int i = 0; i < SpeakerAvatarDrawable.variantCount(); i++) {
            avatars.add(assignment.avatarFor(i));
        }
        assertEquals(SpeakerAvatarDrawable.variantCount(), avatars.size());
        assertEquals(assignment.avatarFor(0), assignment.avatarFor(SpeakerAvatarDrawable.variantCount()));
        int negative = assignment.avatarFor(-1);
        assertTrue(negative >= 0 && negative < SpeakerAvatarDrawable.variantCount());
    }

    @Test
    public void seedIsStable() {
        assertEquals(-8010313599078757212L, SpeakerAvatarAssignment.seedFor("episode-guid"));
        assertNotEquals(SpeakerAvatarAssignment.seedFor("a"), SpeakerAvatarAssignment.seedFor("b"));
        assertEquals(0xcbf29ce484222325L, SpeakerAvatarAssignment.seedFor(""));
        new SpeakerAvatarAssignment("");
    }

    @Test
    public void emptyAndNegativeSizes() {
        assertEquals(0, SpeakerAvatarAssignment.permutation(1, 0).length);
        try {
            SpeakerAvatarAssignment.permutation(1, -1);
            throw new AssertionError("expected exception");
        } catch (IllegalArgumentException expected) {
            assertTrue(true);
        }
    }
}
