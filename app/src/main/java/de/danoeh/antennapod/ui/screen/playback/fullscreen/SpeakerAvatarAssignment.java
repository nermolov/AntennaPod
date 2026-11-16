package de.danoeh.antennapod.ui.screen.playback.fullscreen;

import java.nio.charset.StandardCharsets;

public class SpeakerAvatarAssignment {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long SPLIT_MIX_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long SPLIT_MIX_MUL_1 = 0xBF58476D1CE4E5B9L;
    private static final long SPLIT_MIX_MUL_2 = 0x94D049BB133111EBL;

    private final String key;
    private final int[] permutation;

    public SpeakerAvatarAssignment(String key) {
        this.key = key;
        this.permutation = permutation(seedFor(key), SpeakerAvatarDrawable.variantCount());
    }

    public String getKey() {
        return key;
    }

    public int avatarFor(int speakerIndex) {
        return permutation[Math.floorMod(speakerIndex, permutation.length)];
    }

    public static long seedFor(String key) {
        long hash = FNV_OFFSET;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFFL);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    public static int[] permutation(long seed, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = i;
        }
        long state = seed;
        for (int i = size - 1; i > 0; i--) {
            state += SPLIT_MIX_GAMMA;
            long z = state;
            z = (z ^ (z >>> 30)) * SPLIT_MIX_MUL_1;
            z = (z ^ (z >>> 27)) * SPLIT_MIX_MUL_2;
            z ^= z >>> 31;
            int j = (int) Long.remainderUnsigned(z, i + 1);
            int tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return result;
    }
}
