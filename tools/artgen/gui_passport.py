"""passport.png: the door's technical passport form, ink circles and the two rubber stamps."""
import math
import random

from PIL import Image, ImageDraw, ImageFont

import font
from common import Canvas, rgb, shade, mix, ValueNoise
from palette import *

W, H = 232, 184
INK_VIOLET = rgb('#4e3483')
INK_BLUE = rgb('#2b2d66')
PRINT = rgb('#3a352b')
DEJAVU_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'


def paper_sheet(cv):
    n = ValueNoise(501, 9)
    n2 = ValueNoise(502, 3)
    r = random.Random(503)
    for y in range(H):
        for x in range(W):
            edge = min(x, W - 1 - x, y, H - 1 - y)
            v = n(x, y, 3) * 0.55 + n2(x, y, 1) * 0.15 + 0.38
            v -= 0.32 if edge < 1 else 0.18 if edge < 3 else 0.08 if edge < 7 else 0
            i = max(0, min(len(PAPER) - 1, int(v * len(PAPER))))
            cv.set(x, y, PAPER[i])
    for _ in range(W * H // 22):
        fx, fy = r.randrange(1, W - 3), r.randrange(1, H - 1)
        base = cv.get(fx, fy)
        c = PAPER[max(0, PAPER.index(base) - 1)] if base in PAPER else PAPER[2]
        if r.random() < 0.3:
            c = PAPER[min(len(PAPER) - 1, PAPER.index(base) + 1)] if base in PAPER else PAPER[4]
        cv.set(fx, fy, c)
        if r.random() < 0.6:
            cv.set(fx + 1, fy + (1 if r.random() < 0.2 else 0), c)
    # worn corners
    for (cx, cy, dx, dy) in ((0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)):
        for i in range(3):
            for j in range(3 - i):
                cv.px[cx + dx * j, cy + dy * i] = (0, 0, 0, 0)
    # horizontal fold
    fy = 96
    for x in range(1, W - 1):
        cv.set(x, fy, mix(cv.get(x, fy), rgb('#fffaf0'), 0.45))
        cv.set(x, fy + 1, mix(cv.get(x, fy + 1), PAPER[0], 0.35))
    # coffee ring, bottom left, and a thumb smudge
    cv.ring(36, 150, 11.2, 12.6, (122, 84, 42, 60), 160, 40)
    cv.ring(36, 150, 11.0, 11.5, (100, 66, 30, 70), 200, 330)
    for y in range(150, 178):
        for x in range(120, 150):
            if math.hypot((x - 134) * 0.8, y - 165) < 9 and n2(x, y, 1) > 0.5:
                cv.set(x, y, mix(cv.get(x, y), PAPER[0], 0.18))


def punch_holes(cv):
    for hy in (48, 136):
        cv.ring(8, hy, 3.0, 4.2, PAPER[1])
        for y in range(hy - 3, hy + 4):
            for x in range(5, 12):
                if math.hypot(x + 0.5 - 8, y + 0.5 - hy) < 3.0:
                    cv.px[x, y] = (0, 0, 0, 0)


def printed(cv):
    ink = mix(PRINT, PAPER[3], 0.15)
    # form frame
    for x in range(14, W - 5):
        cv.set(x, 5, ink)
        cv.set(x, H - 6, ink)
    for y in range(5, H - 5):
        cv.set(14, y, ink)
        cv.set(W - 6, y, ink)
    font.draw_text(cv, 16, 7, 'ФОРМА ПС-3', ink)
    font.draw_text(cv, 16, 13, 'ЭКЗ. № 1', ink)
    # dotted baselines for the typed particulars
    faint = mix(PRINT, PAPER[3], 0.6)
    for i in range(4):
        y = 35 + i * 9 + 8
        for x in range(94, 214, 2):
            cv.set(x, y, faint)
    # rule under the particulars and between sections
    for x in range(18, W - 10):
        cv.set(x, 72, faint)
    # place for the stamp
    cx, cy = 192, 150
    for k in range(64):
        if k % 4 < 2:
            a = k / 64 * math.tau
            cv.set(int(cx + math.cos(a) * 24.5), int(cy + math.sin(a) * 24.5), faint)
    font.draw_text_centered(cv, cx, cy - 2, 'М.П.', faint)


def ink_ellipse(cv, ox, oy, w, h, seed):
    r = random.Random(seed)
    cx, cy = ox + w / 2, oy + h / 2
    rx, ry = w / 2 - 1, h / 2 - 1
    start = r.uniform(0, math.tau)
    steps = int((w + h) * 3)
    for i in range(int(steps * 1.12)):
        a = start + i / steps * math.tau
        wob = 1 + math.sin(a * 2 + seed) * 0.04
        x, y = cx + math.cos(a) * rx * wob, cy + math.sin(a) * ry * wob - (i / steps) * 0.8
        cv.set(int(x), int(y), INK_BLUE)


def stamp_round(cv, ox, oy, size=52):
    S = size * 4
    img = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    d.ellipse([4, 4, S - 5, S - 5], outline=255, width=9)
    d.ellipse([28, 28, S - 29, S - 29], outline=255, width=5)
    f_ring = ImageFont.truetype(DEJAVU_BOLD, 19)
    text = 'ЗАВОД П/Я Г-4417 * ПРИНЯТО * '
    for i, ch in enumerate(text):
        a = -math.pi / 2 + i / len(text) * math.tau
        ch_img = Image.new('L', (30, 30), 0)
        ImageDraw.Draw(ch_img).text((15, 15), ch, font=f_ring, fill=255, anchor='mm')
        ch_img = ch_img.rotate(-math.degrees(a) - 90, resample=Image.BICUBIC)
        px, py = c + math.cos(a) * (c - 30), c + math.sin(a) * (c - 30)
        img.paste(255, (int(px - 15), int(py - 15)), ch_img)
    f_big = ImageFont.truetype(DEJAVU_BOLD, 50)
    d.text((c, c - 6), 'ОТК', font=f_big, fill=255, anchor='mm')
    f_small = ImageFont.truetype(DEJAVU_BOLD, 24)
    d.text((c, c + 32), '№ 7', font=f_small, fill=255, anchor='mm')
    img = img.rotate(-9, resample=Image.BICUBIC)
    small = img.resize((size, size), Image.BOX)
    r = random.Random(511)
    n = ValueNoise(512, 5)
    for y in range(size):
        for x in range(size):
            v = small.getpixel((x, y)) / 255
            ink = n(x, y, 2)
            if v > 0.42 and ink > 0.22 and r.random() > 0.06:
                cv.set(ox + x, oy + y, INK_VIOLET if ink > 0.4 else mix(INK_VIOLET, PAPER[4], 0.35))


def stamp_writeoff(cv, ox, oy, w=64, h=22):
    S = 4
    img = Image.new('L', (w * S, h * S), 0)
    d = ImageDraw.Draw(img)
    d.rectangle([3, 3, w * S - 4, h * S - 4], outline=255, width=7)
    d.rectangle([15, 15, w * S - 16, h * S - 16], outline=255, width=3)
    f = ImageFont.truetype(DEJAVU_BOLD, 44)
    d.text((w * S / 2, h * S / 2 + 2), 'СПИСАНО', font=f, fill=255, anchor='mm')
    img = img.rotate(4, resample=Image.BICUBIC)
    small = img.resize((w, h), Image.BOX)
    red = rgb('#a8291f')
    n = ValueNoise(521, 4)
    for y in range(h):
        for x in range(w):
            if small.getpixel((x, y)) / 255 > 0.4 and n(x, y, 2) > 0.25:
                cv.set(ox + x, oy + y, red)


def build():
    cv = Canvas(256, 256)
    paper_sheet(cv)
    punch_holes(cv)
    printed(cv)
    ink_ellipse(cv, 0, 184, 20, 13, 3)
    ink_ellipse(cv, 0, 197, 44, 13, 5)
    stamp_round(cv, 48, 184)
    stamp_writeoff(cv, 104, 184)
    return cv


if __name__ == '__main__':
    import sys
    build().save(sys.argv[1])
