"""stage_pump.png: manometer, hand pump, relief valve, time relay, bleed valve."""
import math
import random

import font
from common import Canvas, rgb, shade, mix, screw, rivet, ValueNoise
from gui_panel import hammertone, chips, scratches, grime
from gui_power import paint_label
from palette import *

SW, SH = 166, 152
GX, GY = 50, 56
FACE = [rgb(h) for h in ('#cfc6ab', '#ddd5bc', '#e9e2cb', '#f1ebd8')]


def gauge(cv):
    # bezel: black-painted steel with a chrome lip and three screws
    def bezel(X, Y, d, px, py):
        s = px + py
        if d > 41.3:
            return OUTLINE
        if d > 39.5:
            return rgb('#3a3c3d') if s < 0 else rgb('#1c1d1e')
        if d > 38.2:
            return STEEL[7] if s < -10 else STEEL[5] if s < 20 else STEEL[3]
        return STEEL[2]

    cv.ring(GX, GY, 37, 42, bezel)

    def face(X, Y, d, px, py):
        i = 3 if d < 20 else 2 if d < 31 else 1 if d < 35.5 else 0
        return FACE[i]

    cv.ring(GX, GY, 0, 37, face)
    ink = rgb('#1e1d1a')
    red = rgb('#b0281c')
    # red zone 14.5..16 on the same track as the operating band
    a0 = 135 + 270 * 14.5 / 16
    cv.ring(GX, GY, 27.5, 31, red, a0, 135 + 270)
    # ticks
    for k in range(0, 33):
        val = k / 2
        a = math.radians(135 + 270 * val / 16)
        major = k % 4 == 0
        r0 = 31.5 if major else 33.5
        for rr in [r0 + i * 0.5 for i in range(int((36 - r0) * 2) + 1)]:
            cv.set(int(GX + math.cos(a) * rr), int(GY + math.sin(a) * rr), ink)
        if major:
            label = str(int(val))
            tx = GX + math.cos(a) * 22
            ty = GY + math.sin(a) * 22
            w = font.text_width(label)
            font.draw_text(cv, int(round(tx - w / 2)), int(round(ty - 2.5)), label, ink)
    font.draw_text_centered(cv, GX, GY + 5, 'КГС/СМ²', ink)
    font.draw_text_centered(cv, GX, GY - 12, 'МТП-100', ink)
    font.draw_text_centered(cv, GX + 1, GY + 14, '1,5', mix(ink, FACE[2], 0.35))
    # bezel screws
    for ang in (90, 210, 330):
        a = math.radians(ang)
        screw(cv, int(GX + math.cos(a) * 39.8) - 1, int(GY + math.sin(a) * 39.8) - 1, ang, 3)
    # stem
    cv.rect(GX - 3, GY + 41, GX + 3, GY + 47, BRASS[2])
    cv.rect(GX - 4, GY + 44, GX + 4, GY + 46, BRASS[3])
    cv.rect(GX - 3, GY + 41, GX - 2, GY + 47, BRASS[4])


def pipe_h(cv, x0, x1, y):
    for x in range(x0, x1):
        cv.set(x, y, STEEL[6])
        cv.set(x, y + 1, STEEL[4])
        cv.set(x, y + 2, STEEL[2])
        cv.set(x, y + 3, OUTLINE)


def pipe_v(cv, x, y0, y1):
    for y in range(y0, y1):
        cv.set(x, y, STEEL[6])
        cv.set(x + 1, y, STEEL[4])
        cv.set(x + 2, y, STEEL[2])
        cv.set(x + 3, y, OUTLINE)


def flange(cv, x, y, vertical=False):
    if vertical:
        cv.rect(x - 1, y, x + 5, y + 3, STEEL[4])
        cv.rect(x - 1, y, x + 5, y + 1, STEEL[6])
        cv.set(x - 1, y + 2, STEEL[1])
        cv.set(x + 4, y + 2, STEEL[1])
    else:
        cv.rect(x, y - 1, x + 3, y + 5, STEEL[4])
        cv.rect(x, y - 1, x + 1, y + 5, STEEL[6])
        cv.set(x + 2, y + 4, STEEL[1])


def relay(cv, cx, cy):
    def housing(X, Y, d, px, py):
        if d > 16.2:
            return OUTLINE
        if d > 15:
            return BAKELITE[4] if px + py < 0 else BAKELITE[1]
        return BAKELITE[2]

    cv.ring(cx, cy, 0, 17, housing)
    white = rgb('#d9d4c3')
    for k in range(11):
        a = math.radians(135 + 27 * k)
        for rr in (12.5, 13.5, 14.2):
            if k % 5 == 0 or rr > 13:
                cv.set(int(cx + math.cos(a) * rr), int(cy + math.sin(a) * rr), white)
    cv.ring(cx, cy, 12.5, 14.5, rgb('#b0281c'), 135 + 270 - 12, 135 + 270)
    cv.disc(cx, cy, 2.2, BAKELITE[4])


def build():
    cv = Canvas(256, 256)
    hammertone(cv, 0, 0, SW, SH, 201, lo=4, hi=6)
    grime(cv, 0, 0, SW, SH, 202, 0.25)
    chips(cv, 0, 0, SW, SH, 203, 9)
    scratches(cv, 0, 0, SW, SH, 204, 9, PAINT[7])

    # pipework (drawn first, the instruments sit on top)
    pipe_h(cv, GX - 1, 136, 104)
    pipe_v(cv, GX - 2, 100, 106)
    flange(cv, GX - 3, 102, vertical=True)
    flange(cv, 67, 103)
    pipe_v(cv, 68, 107, 116)
    flange(cv, 128, 103)
    # relief branch: up the right-hand side to the valve
    pipe_v(cv, 152, 28, 112)
    pipe_h(cv, 146, 156, 109)
    flange(cv, 151, 60, vertical=True)

    gauge(cv)
    # paint the flow arrow on the main pipe
    for i, x in enumerate(range(84, 92)):
        cv.set(x, 101, WHITE_PAINT)
    for k in range(3):
        cv.set(91 - k, 100 - k, WHITE_PAINT)
        cv.set(91 - k, 102 + k, WHITE_PAINT)

    # relief valve body (cap is a sprite)
    cv.rect(149, 15, 159, 29, BRASS[2])
    cv.rect(149, 15, 151, 29, BRASS[4])
    cv.rect(157, 15, 159, 29, BRASS[1])
    for y in range(17, 27, 3):
        cv.rect(150, y, 158, y + 1, BRASS[0])
    paint_label(cv, 154, 32, 'КП', 205, wear=0.0)

    # pump: bracket, cylinder, base
    cv.rect(137, 52, 144, 70, STEEL[3])
    cv.rect(137, 52, 138, 70, STEEL[5])
    cv.rect(143, 52, 144, 70, STEEL[1])
    green = DOOR_GREEN
    cv.rect(132, 68, 149, 72, STEEL[4])
    cv.rect(132, 68, 149, 69, STEEL[6])
    for x in range(134, 147):
        for y in range(72, 137):
            t = (x - 134) / 12
            i = 5 if t < 0.15 else 4 if t < 0.4 else 3 if t < 0.75 else 2
            cv.set(x, y, green[i])
    cv.rect(131, 137, 150, 141, STEEL[3])
    cv.rect(131, 137, 150, 138, STEEL[5])
    for bx in (133, 146):
        cv.set(bx, 139, STEEL[6])
        cv.set(bx, 69, STEEL[7])
    font.draw_text(cv, 136, 96, 'НР', WHITE_PAINT)
    font.draw_text(cv, 137, 103, '2', WHITE_PAINT)
    chips(cv, 134, 72, 147, 137, 206, 4, edge_bias=False)

    # time relay and its label
    relay(cv, 26, 122)
    paint_label(cv, 26, 142, 'РЕЛЕ ВР.', 207, wear=0.0)
    # bleed valve body and label
    cv.rect(64, 116, 74, 128, BRASS[1])
    paint_label(cv, 69, 136, 'СБРОС', 208, wear=0.0)
    # instruction plate under the relief valve
    cv.rect(104, 4, 141, 18, PAINT[2])
    from gui_panel import bakelite_plate
    bakelite_plate(cv, 103, 3, 140, 17, 209)
    font.draw_text_centered(cv, 121, 5, 'НОРМА', CREAM)
    font.draw_text_centered(cv, 121, 11, 'ПО ЗЕЛЁН.', CREAM)

    # Sprites
    def cap(x, y, open_):
        cv.rect(x + 1, y + 2, x + 11, y + 10, BRASS[3])
        cv.rect(x + 1, y + 2, x + 11, y + 3, BRASS[5])
        cv.rect(x + 10, y + 2, x + 11, y + 10, BRASS[1])
        cv.rect(x + 4, y, x + 8, y + 2, BRASS[2] if not open_ else BRASS[4])
        cv.rect(x + 2, y + 9, x + 10, y + 10, BRASS[0])
        if open_:
            cv.set(x + 3, y + 1, rgb('#f2f0e8'))
    cap(168, 0, False)
    cap(180, 0, True)
    # pump knob: black ball
    cv.ring(172, 28, 0, 3.6, lambda X, Y, d, px, py: BAKELITE[5] if px + py < -2.5 else (BAKELITE[1] if px + py > 1.5 else BAKELITE[3]))
    # bleed valve star wheels, three rotations
    red = RED_PAINT
    for f in range(3):
        ox, oy = 168 + f * 18, 34
        c = (ox + 9, oy + 9)
        for k in range(5):
            a = math.radians(-90 + 72 * k + f * 24)
            for rr in [x * 0.5 for x in range(4, 16)]:
                px, py = c[0] + math.cos(a) * rr, c[1] + math.sin(a) * rr
                cv.disc(px, py, 1.1 if rr < 6 else 1.7, red[3])
            tip = (c[0] + math.cos(a) * 7.5, c[1] + math.sin(a) * 7.5)
            cv.set(int(tip[0]), int(tip[1]) - 1, red[5])
        cv.ring(c[0], c[1], 0, 3.2, lambda X, Y, d, px, py: red[5] if px + py < -1 else red[2])
        cv.disc(c[0], c[1], 1.2, STEEL[5])
    # gauge needle cap
    cv.ring(225.5, 3.5, 0, 3.4, lambda X, Y, d, px, py: rgb('#4a4846') if px + py < -1.5 else (rgb('#0e0e0d') if px + py > 1 else rgb('#232221')))
    return cv


if __name__ == '__main__':
    import sys
    build().save(sys.argv[1])
