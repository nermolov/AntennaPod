#!/usr/bin/env python3
import argparse
import gzip
import json
import os
import random
import re
import sqlite3
import struct
import subprocess
import sys
import tempfile
import time
import zlib

FEED_URL = "seed://fake-feed"
FEED_TITLE = "Seeded Test Feed"
EPISODE_TITLE = "Episode 1: The Seeded Episode"
ITEM_ID = "seed-episode-1"
DURATION_S = 240
SAMPLE_RATE = 8000
PREF_FEED_ID = "de.danoeh.antennapod.preferences.lastPlayedFeedId"
PREF_MEDIA_ID = "de.danoeh.antennapod.preferences.lastPlayedFeedMediaId"
PREF_MEDIA_TYPE = "de.danoeh.antennapod.preferences.currentlyPlayingMedia"
PREF_PLAYER_STATUS = "de.danoeh.antennapod.preferences.currentPlayerStatus"
PLAYER_STATUS_PAUSED = 0
PLAYABLE_TYPE_FEEDMEDIA = 1
ACTIVITY = "de.danoeh.antennapod.activity.MainActivity"

SCRIPT = [
    (0, "So welcome back to the show everybody, today we have a really special episode lined up for you."),
    (1, "Yeah, thanks for having me. I have been looking forward to this conversation for weeks now."),
    (0, "Let's start at the beginning. Tell us how you first got interested in building tiny weather stations "
        "out of spare parts, because I think that is a story people will want to hear."),
    (2, "Okay, so this is going to sound ridiculous, but it started with a broken toaster. I took it apart "
        "to see what was inside and found a tiny thermostat, and I became obsessed with how it worked. "
        "A month later my whole garage was covered in sensors."),
    (1, "Yeah, 100%."),
    (0, "And your neighbors were fine with that?"),
    (2, "Mostly. One of them complained about the anemometer on the roof, since it squeaks whenever the wind "
        "picks up, but honestly I think the squeak is how I knew it was working."),
    (1, "That is the best kind of debugging. If it makes noise, it is alive."),
    (0, "Let's talk about the numbers. How accurate do you think these homemade stations really are compared "
        "to the official ones?"),
    (2, "Surprisingly close. Within a degree most days, and the humidity readings drift a little in summer, "
        "but you can calibrate that out if you are patient."),
    (1, "And patience is the one thing you cannot buy at the hardware store."),
    (0, "That is a perfect place to take a short break. We will be right back."),
]


def run(cmd, serial, check=True, capture=True, stdin=None):
    full = ["adb"] + (["-s", serial] if serial else []) + cmd
    result = subprocess.run(full, stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None, input=stdin)
    if check and result.returncode != 0:
        sys.exit("adb failed: %s\n%s" % (" ".join(full), (result.stderr or b"").decode()))
    return result.stdout if capture else b""


def build_wav(path):
    frames = DURATION_S * SAMPLE_RATE
    data = bytes([128]) * frames
    header = b"RIFF" + struct.pack("<I", 36 + frames) + b"WAVEfmt " + struct.pack(
        "<IHHIIHH", 16, 1, 1, SAMPLE_RATE, SAMPLE_RATE, 1, 8) + b"data" + struct.pack("<I", frames)
    with open(path, "wb") as f:
        f.write(header + data)


def build_cover(path, size=400):
    rng = random.Random(7)
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            r = int(200 - 90 * y / size) + rng.randint(-8, 8)
            g = int(70 + 60 * x / size) + rng.randint(-8, 8)
            b = int(90 + 120 * (x + y) / (2 * size)) + rng.randint(-8, 8)
            row += bytes([max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b))])
        rows.append(bytes(row))

    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def build_transcript():
    turns = []
    cursor = 1500
    for speaker, text in SCRIPT:
        words, starts, ends = [], [], []
        for word in text.split():
            duration = 180 + 45 * len(word)
            words.append(word)
            starts.append(cursor)
            ends.append(cursor + duration - 40)
            cursor += duration
        turns.append({"s": "SPEAKER_%02d" % speaker, "i": speaker, "w": words, "a": starts, "b": ends})
        cursor += 600
    return gzip.compress(json.dumps(turns, separators=(",", ":")).encode("utf-8"))


def seed_database(db_path, media_path, cover_path, position_ms):
    db = sqlite3.connect(db_path)
    db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    old = db.execute("SELECT id FROM Feeds WHERE download_url=?", (FEED_URL,)).fetchall()
    for (feed_id,) in old:
        items = [r[0] for r in db.execute("SELECT id FROM FeedItems WHERE feed=?", (feed_id,))]
        for item in items:
            db.execute("DELETE FROM FeedMedia WHERE feeditem=?", (item,))
            db.execute("DELETE FROM Queue WHERE feeditem=?", (item,))
            db.execute("DELETE FROM GeneratedTranscripts WHERE feeditem=?", (item,))
        db.execute("DELETE FROM FeedItems WHERE feed=?", (feed_id,))
        db.execute("DELETE FROM Feeds WHERE id=?", (feed_id,))
    now_ms = int(time.time() * 1000)
    cover_url = "file://" + cover_path
    feed_id = db.execute(
        "INSERT INTO Feeds (title, download_url, link, description, author, image_url, type, feed_identifier,"
        " auto_download, keep_updated, last_update) VALUES (?,?,?,?,?,?,?,?,0,0,?)",
        (FEED_TITLE, FEED_URL, "https://example.invalid", "A fake feed used for UI testing.", "Seed Script",
         cover_url, "RSS 2.0", FEED_URL, "Sun, 15 Nov 2026 00:00:00 GMT")).lastrowid
    item_id = db.execute(
        "INSERT INTO FeedItems (title, pubDate, read, link, description, feed, item_identifier, image_url,"
        " auto_download, has_simple_chapters) VALUES (?,?,0,?,?,?,?,?,0,0)",
        (EPISODE_TITLE, now_ms, "https://example.invalid/1", "A fake episode with a fake transcript.", feed_id,
         ITEM_ID, cover_url)).lastrowid
    media_id = db.execute(
        "INSERT INTO FeedMedia (duration, file_url, download_url, downloaded, position, filesize, mime_type,"
        " feeditem, played_duration, has_embedded_picture) VALUES (?,?,?,?,?,?,?,?,0,0)",
        (DURATION_S * 1000, media_path, "https://example.invalid/seed.wav", now_ms, position_ms,
         os.path.getsize(media_path) if os.path.exists(media_path) else DURATION_S * SAMPLE_RATE + 44,
         "audio/x-wav", item_id)).lastrowid
    db.execute("UPDATE FeedItems SET media=? WHERE id=?", (media_id, item_id))
    queue_pos = db.execute("SELECT COALESCE(MAX(id), 0) + 1 FROM Queue").fetchone()[0]
    db.execute("INSERT INTO Queue (id, feeditem, feed) VALUES (?,?,?)", (queue_pos, item_id, feed_id))
    db.execute("INSERT INTO GeneratedTranscripts (feeditem, status, transcript, updated_at) VALUES (?,?,?,?)",
               (item_id, "done", build_transcript(), now_ms))
    db.commit()
    db.execute("PRAGMA journal_mode=DELETE")
    db.close()
    return feed_id, media_id


def patch_prefs(xml, feed_id, media_id):
    entries = {
        PREF_FEED_ID: ("long", feed_id),
        PREF_MEDIA_ID: ("long", media_id),
        PREF_MEDIA_TYPE: ("long", PLAYABLE_TYPE_FEEDMEDIA),
        PREF_PLAYER_STATUS: ("int", PLAYER_STATUS_PAUSED),
    }
    for name in entries:
        xml = re.sub(r'\s*<(long|int) name="%s" value="[^"]*"\s*/>' % re.escape(name), "", xml)
    block = "".join('    <%s name="%s" value="%d" />\n' % (kind, name, value)
                    for name, (kind, value) in entries.items())
    if "</map>" in xml:
        return xml.replace("</map>", block + "</map>")
    if re.search(r"<map\s*/>", xml):
        return re.sub(r"<map\s*/>", "<map>\n" + block + "</map>", xml)
    return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" + block + "</map>\n"


def run_as(package, serial, command, check=True):
    return run(["shell", "run-as", package, "sh", "-c", "'%s'" % command], serial, check)


def main():
    parser = argparse.ArgumentParser(description="Seed a fake episode with a transcript into the app")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--package", default="de.danoeh.antennapod.debug")
    parser.add_argument("--position", type=int, default=62000, help="Playback position in ms")
    parser.add_argument("--no-launch", action="store_true")
    args = parser.parse_args()
    package, serial = args.package, args.serial

    media_dir = "/sdcard/Android/data/%s/files" % package
    media_path = "%s/seed-episode.wav" % media_dir
    cover_path = "%s/seed-cover.png" % media_dir
    stage = "/data/local/tmp"

    with tempfile.TemporaryDirectory() as tmp:
        wav, cover = os.path.join(tmp, "seed-episode.wav"), os.path.join(tmp, "seed-cover.png")
        build_wav(wav)
        build_cover(cover)
        run(["shell", "mkdir", "-p", media_dir], serial)
        run(["push", wav, media_path], serial)
        run(["push", cover, cover_path], serial)

        run(["shell", "am", "force-stop", package], serial)
        db_local = os.path.join(tmp, "Antennapod.db")
        for suffix in ("", "-wal", "-shm"):
            data = run(["exec-out", "run-as", package, "cat", "databases/Antennapod.db" + suffix], serial,
                       check=False)
            if data and not data.startswith(b"run-as:"):
                with open(db_local + suffix, "wb") as f:
                    f.write(data)
        if not os.path.exists(db_local):
            sys.exit("Could not read the app database. Launch the app once and make sure it is a debug build.")
        feed_id, media_id = seed_database(db_local, media_path, cover_path, args.position)
        run(["push", db_local, stage + "/seed.db"], serial)
        run_as(package, serial, "cp %s/seed.db databases/Antennapod.db && rm -f databases/Antennapod.db-wal"
               " databases/Antennapod.db-shm" % stage)

        prefs_file = "shared_prefs/%s_preferences.xml" % package
        xml = run(["exec-out", "run-as", package, "cat", prefs_file], serial, check=False).decode("utf-8")
        if xml.startswith("run-as:") or "No such file" in xml:
            xml = ""
        prefs_local = os.path.join(tmp, "prefs.xml")
        with open(prefs_local, "w", encoding="utf-8") as f:
            f.write(patch_prefs(xml, feed_id, media_id))
        run(["push", prefs_local, stage + "/seed-prefs.xml"], serial)
        run_as(package, serial, "mkdir -p shared_prefs && cp %s/seed-prefs.xml %s" % (stage, prefs_file))
        run(["shell", "rm", "-f", stage + "/seed.db", stage + "/seed-prefs.xml"], serial)

    print("Seeded feed %d, media %d" % (feed_id, media_id))
    if not args.no_launch:
        run(["shell", "am", "start", "-n", "%s/%s" % (package, ACTIVITY), "--ez", "open_player", "true"], serial)


if __name__ == "__main__":
    main()
