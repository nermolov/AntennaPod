#!/usr/bin/env python3
import curses
import os
import shutil
import subprocess
import sys
import time

FLASH_SECONDS = 0.25
HISTORY_SIZE = 6

KEYS = {
    curses.KEY_UP: ("UP", "KEYCODE_DPAD_UP"),
    curses.KEY_DOWN: ("DOWN", "KEYCODE_DPAD_DOWN"),
    curses.KEY_LEFT: ("LEFT", "KEYCODE_DPAD_LEFT"),
    curses.KEY_RIGHT: ("RIGHT", "KEYCODE_DPAD_RIGHT"),
    10: ("OK", "KEYCODE_DPAD_CENTER"),
    13: ("OK", "KEYCODE_DPAD_CENTER"),
    curses.KEY_ENTER: ("OK", "KEYCODE_DPAD_CENTER"),
    27: ("BACK", "KEYCODE_BACK"),
    curses.KEY_BACKSPACE: ("BACK", "KEYCODE_BACK"),
    127: ("BACK", "KEYCODE_BACK"),
    8: ("BACK", "KEYCODE_BACK"),
    ord("h"): ("HOME", "KEYCODE_HOME"),
    ord("m"): ("MENU", "KEYCODE_MENU"),
    ord(" "): ("PLAY", "KEYCODE_MEDIA_PLAY_PAUSE"),
    ord("s"): ("STOP", "KEYCODE_MEDIA_STOP"),
    ord("n"): ("NEXT", "KEYCODE_MEDIA_NEXT"),
    ord("p"): ("PREV", "KEYCODE_MEDIA_PREVIOUS"),
    ord("f"): ("FFWD", "KEYCODE_MEDIA_FAST_FORWARD"),
    ord("r"): ("REW", "KEYCODE_MEDIA_REWIND"),
    ord("+"): ("VOL+", "KEYCODE_VOLUME_UP"),
    ord("="): ("VOL+", "KEYCODE_VOLUME_UP"),
    ord("-"): ("VOL-", "KEYCODE_VOLUME_DOWN"),
    ord("_"): ("VOL-", "KEYCODE_VOLUME_DOWN"),
    ord("0"): ("MUTE", "KEYCODE_VOLUME_MUTE"),
    ord("i"): ("INFO", "KEYCODE_INFO"),
    ord("g"): ("GUIDE", "KEYCODE_GUIDE"),
    ord("t"): ("SEARCH", "KEYCODE_SEARCH"),
    ord("w"): ("POWER", "KEYCODE_POWER"),
}

LAYOUT = [
    (1, 8, "UP", "  ↑  ", "↑"),
    (3, 0, "LEFT", "  ←  ", "←"),
    (3, 16, "RIGHT", "  →  ", "→"),
    (5, 8, "DOWN", "  ↓  ", "↓"),
    (3, 8, "OK", " OK  ", "Enter"),
    (7, 0, "BACK", " BACK ", "Esc/Bksp"),
    (7, 10, "HOME", " HOME ", "h"),
    (9, 0, "MENU", " MENU ", "m"),
    (9, 10, "SEARCH", " FIND ", "t"),
    (11, 0, "REW", " ◀◀ ", "r"),
    (11, 8, "PLAY", " ▶/❚❚ ", "Space"),
    (11, 17, "FFWD", " ▶▶ ", "f"),
    (13, 0, "PREV", " |◀ ", "p"),
    (13, 8, "STOP", " ■ ", "s"),
    (13, 17, "NEXT", " ▶| ", "n"),
    (15, 0, "VOL-", " VOL- ", "-"),
    (15, 10, "MUTE", " MUTE ", "0"),
    (15, 20, "VOL+", " VOL+ ", "+"),
]

EXTRA_HELP = "i: info   g: guide   w: power   q / Ctrl-C: quit"


def find_adb_command():
    if shutil.which("adb"):
        return ["adb"]
    if shutil.which("nix"):
        return ["nix", "develop", "--command", "adb"]
    return None


class AdbShell:
    def __init__(self, adb_command):
        self.adb_command = adb_command
        self.process = None
        self.error = None

    def connect(self):
        if self.process is not None and self.process.poll() is None:
            return
        self.process = subprocess.Popen(
            self.adb_command + ["shell"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            text=True,
        )

    def send(self, keycode):
        try:
            self.connect()
            self.process.stdin.write("input keyevent %s\n" % keycode)
            self.process.stdin.flush()
            self.error = None
        except (BrokenPipeError, OSError) as exc:
            self.process = None
            self.error = str(exc)

    def connected(self):
        return self.process is not None and self.process.poll() is None

    def close(self):
        if self.process is not None:
            try:
                self.process.stdin.close()
                self.process.wait(timeout=2)
            except (OSError, subprocess.TimeoutExpired):
                self.process.kill()


def init_colors():
    curses.start_color()
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_BLACK, curses.COLOR_GREEN)
    curses.init_pair(2, curses.COLOR_CYAN, -1)
    curses.init_pair(3, curses.COLOR_RED, -1)
    curses.init_pair(4, curses.COLOR_GREEN, -1)
    curses.init_pair(5, curses.COLOR_YELLOW, -1)


def safe_addstr(win, y, x, text, attr=0):
    height, width = win.getmaxyx()
    if y < 0 or y >= height or x >= width:
        return
    try:
        win.addstr(y, x, text[: max(0, width - x - 1)], attr)
    except curses.error:
        pass


def draw(win, shell, flashes, history, now):
    win.erase()
    safe_addstr(win, 0, 0, "Android TV remote", curses.A_BOLD)
    status = "adb: connected" if shell.connected() else "adb: idle (connects on first key)"
    status_color = curses.color_pair(4) if shell.connected() else curses.color_pair(5)
    if shell.error:
        status = "adb error: " + shell.error
        status_color = curses.color_pair(3)
    safe_addstr(win, 0, 20, status, status_color)

    for row, col, name, label, shortcut in LAYOUT:
        active = now - flashes.get(name, 0) < FLASH_SECONDS
        button_attr = curses.color_pair(1) | curses.A_BOLD if active else curses.A_REVERSE
        top = 1 + row
        safe_addstr(win, top, col + 2, "[" + label + "]", button_attr)
        safe_addstr(win, top + 1, col + 2, shortcut.center(len(label) + 2), curses.color_pair(2))

    base = 18
    safe_addstr(win, base, 0, EXTRA_HELP, curses.color_pair(2))
    safe_addstr(win, base + 2, 0, "Sent:", curses.A_BOLD)
    for index, entry in enumerate(reversed(history)):
        attr = curses.color_pair(4) | curses.A_BOLD if index == 0 else curses.A_DIM
        safe_addstr(win, base + 3 + index, 2, entry, attr)
    win.refresh()


def run(stdscr, adb_command):
    curses.curs_set(0)
    init_colors()
    stdscr.keypad(True)
    stdscr.timeout(50)
    os.environ.setdefault("ESCDELAY", "25")

    shell = AdbShell(adb_command)
    flashes = {}
    history = []
    try:
        while True:
            draw(stdscr, shell, flashes, history, time.monotonic())
            ch = stdscr.getch()
            if ch in (ord("q"), 3):
                break
            if ch == -1 or ch == curses.KEY_RESIZE:
                continue
            entry = KEYS.get(ch)
            if entry is None:
                continue
            name, keycode = entry
            shell.send(keycode)
            flashes[name] = time.monotonic()
            history.append("%s  %s  %s" % (time.strftime("%H:%M:%S"), name.ljust(7), keycode))
            del history[:-HISTORY_SIZE]
    finally:
        shell.close()


def main():
    adb_command = find_adb_command()
    if adb_command is None:
        sys.exit("adb not found and nix is unavailable")
    os.environ.setdefault("ESCDELAY", "25")
    curses.wrapper(run, adb_command)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
