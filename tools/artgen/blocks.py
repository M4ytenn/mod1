"""Block, block-entity and item textures plus the mod logo."""
import math
import random

import font
from common import Canvas, rgb, shade, mix, rivet, ValueNoise
from gui_panel import chips, scratches, grime
from palette import *


def noise_paint(cv, x0, y0, w, h, ramp, seed, lo, hi, cell=4):
    n = ValueNoise(seed, cell)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = n(x, y, 3)
            i = lo if v < 0.42 else (lo + hi) // 2 if v < 0.66 else hi
            cv.set(x, y, ramp[i])


def rust_streaks(cv, x0, y0, w, h, seed, count, starts=None):
    r = random.Random(seed)
    pts = starts or [(r.randrange(x0, x0 + w), r.randrange(y0, y0 + h // 2)) for _ in range(count)]
    for (sx, sy) in pts:
        ln = r.randrange(3, max(4, h // 2))
        for k in range(ln):
            y = sy + k
            if y >= y0 + h:
                break
            t = 1 - k / ln
            cv.set(sx, y, mix(cv.get(sx, y), RUST[2 if t > 0.5 else 3], 0.25 + 0.4 * t))
            if r.random() < 0.25 and sx + 1 < x0 + w:
                cv.set(sx + 1, y, mix(cv.get(sx + 1, y), RUST[3], 0.2))


def hazard(cv, x0, y0, w, h, seed, period=8, worn=True):
    r = random.Random(seed)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            band = ((x + y) // (period // 2)) % 2
            c = HAZARD_YELLOW[2] if band == 0 else HAZARD_BLACK[1]
            if band == 0 and (x * 7 + y * 3) % 11 == 0:
                c = HAZARD_YELLOW[1]
            if worn and r.random() < 0.05:
                c = STEEL[4] if r.random() < 0.6 else RUST[3]
            cv.set(x, y, c)


# ------------------------------------------------------------------ block textures (16 x 16)

def frame_steel():
    cv = Canvas(16, 16)
    noise_paint(cv, 0, 0, 16, 16, STEEL, 601, 2, 4)
    for x in range(16):
        cv.set(x, 0, STEEL[5])
        cv.set(x, 15, STEEL[1])
    r = random.Random(602)
    for _ in range(5):
        cv.set(r.randrange(16), r.randrange(16), RUST[r.randrange(2, 4)])
    for x in range(2, 14, 3):
        cv.set(x, 8, STEEL[1])
        cv.set(x + 1, 8, STEEL[5])
    return cv


def painted_frame(ramp, seed):
    cv = Canvas(16, 16)
    noise_paint(cv, 0, 0, 16, 16, ramp, seed, 3, 5)
    chips(cv, 0, 0, 16, 16, seed + 1, 3)
    rust_streaks(cv, 0, 0, 16, 16, seed + 2, 2)
    return cv


def hazard_tex():
    cv = Canvas(16, 16)
    hazard(cv, 0, 0, 16, 16, 611)
    return cv


# ------------------------------------------------------------------ ДЗГ leaf (128 x 64)

def dzg_leaf():
    cv = Canvas(128, 64)
    g = DOOR_GREEN
    # FRONT 26 x 43
    noise_paint(cv, 0, 0, 26, 43, g, 621, 3, 5)
    cv.bevel(0, 0, 26, 43, g[1], g[0])
    cv.bevel(1, 1, 25, 42, g[6], g[2])
    # rubbed ring round the handwheel (centre at x 13, y 19)
    cv.ring(13, 19.5, 7.5, 9.5, lambda X, Y, d, px, py: g[4] if (X + Y) % 3 else STEEL[4])
    font.draw_text_centered(cv, 13, 4, 'ДЗГ', mix(WHITE_PAINT, g[4], 0.2))
    # manufacturer's plate
    cv.rect(9, 33, 17, 37, ALU[4])
    cv.outline(9, 33, 17, 37, ALU[1])
    for x in range(10, 16, 2):
        cv.set(x, 35, ALU[1])
    chips(cv, 0, 0, 26, 43, 622, 8)
    rust_streaks(cv, 0, 0, 26, 43, 623, 0, starts=[(22, 9), (22, 33), (21, 21), (4, 40), (13, 2)])
    grime(cv, 0, 0, 26, 43, 624, 0.45)
    # BACK 26 x 43: welded stiffener frame with an X brace
    noise_paint(cv, 26, 0, 26, 43, g, 625, 2, 4)
    cv.bevel(26, 0, 52, 43, g[1], g[0])
    for (x0, y0, x1, y1) in ((30, 4, 48, 6), (30, 37, 48, 39), (30, 4, 32, 39), (46, 4, 48, 39)):
        cv.rect(x0, y0, x1, y1, g[5])
        cv.rect(x0, y1 - 1, x1, y1, g[1])
    cv.line(32, 6, 46, 37, g[5])
    cv.line(33, 6, 47, 37, g[2])
    cv.line(46, 6, 32, 37, g[5])
    cv.line(47, 6, 33, 37, g[2])
    rust_streaks(cv, 26, 0, 26, 43, 626, 5)
    grime(cv, 26, 0, 26, 43, 627, 0.5)
    # EDGES
    noise_paint(cv, 52, 0, 4, 43, STEEL, 628, 2, 4)
    for y in range(43):
        cv.set(52, y, g[3])
        cv.set(55, y, g[2])
    noise_paint(cv, 0, 44, 26, 4, STEEL, 629, 2, 4)
    for x in range(26):
        cv.set(x, 44, g[3])
        cv.set(x, 47, g[2])
    # material swatches 8 x 12
    noise_paint(cv, 56, 0, 8, 12, RED_PAINT, 630, 2, 4, cell=2)
    noise_paint(cv, 64, 0, 8, 12, STEEL, 631, 3, 5, cell=2)
    noise_paint(cv, 72, 0, 8, 12, RUST, 632, 1, 3, cell=2)
    noise_paint(cv, 80, 0, 8, 12, BAKELITE, 633, 1, 2, cell=2)
    lamp_swatch(cv, 88, 0, 'off')
    lamp_swatch(cv, 92, 0, 'amber')
    lamp_swatch(cv, 96, 0, 'green')
    return cv


def lamp_swatch(cv, x, y, kind):
    cols = {'off': ('#2d2522', '#4a3d38'), 'amber': ('#ef9f33', '#ffe3a0'), 'green': ('#3fc252', '#c9ffc4'),
            'red': ('#e23a2b', '#ffb6a6')}[kind]
    base, hi = rgb(cols[0]), rgb(cols[1])
    cv.rect(x, y, x + 4, y + 4, base)
    cv.set(x + 1, y + 1, hi)


# ------------------------------------------------------------------ гермозатвор leaf (256 x 128)

def gate_leaf():
    cv = Canvas(256, 128)
    b = GATE_BLUE
    W, H = 62, 57
    noise_paint(cv, 0, 0, W, H, b, 641, 3, 5)
    cv.bevel(0, 0, W, H, b[6], b[0])
    hazard(cv, 1, 1, 6, H - 2, 642)
    hazard(cv, W - 7, 1, 6, H - 2, 643)
    for sy in (18, 38):
        for x in range(7, W - 7):
            cv.set(x, sy, b[1])
            cv.set(x, sy + 1, b[5])
    for x in range(31, 32):
        for y in range(1, H - 1):
            cv.set(x, y, b[1])
            cv.set(x + 1, y, b[5])
    for x in range(9, W - 9, 6):
        for ry in (3, 15, 22, 35, 42, 52):
            rivet(cv, x, ry, b[4])
    font.draw_text_centered(cv, W // 2, 5, 'ЗГ-4', mix(WHITE_PAINT, b[4], 0.15), scale=2)
    font.draw_text_centered(cv, W // 2, 25, '№ 07', rgb('#a8331f'), scale=2)
    chips(cv, 0, 0, W, H, 644, 14)
    rust_streaks(cv, 0, 0, W, H, 645, 0, starts=[(x + 1, ry + 2) for x in range(9, W - 9, 12) for ry in (15, 35)])
    grime(cv, 0, 0, W, H, 646, 0.5)
    # BACK
    noise_paint(cv, W, 0, W, H, b, 647, 2, 4)
    cv.bevel(W, 0, 2 * W, H, b[5], b[0])
    for (x0, y0, x1, y1) in ((W + 4, 4, 2 * W - 4, 7), (W + 4, H - 7, 2 * W - 4, H - 4), (W + 29, 4, W + 33, H - 4)):
        cv.rect(x0, y0, x1, y1, b[5])
        cv.rect(x0, y1 - 1, x1, y1, b[1])
    cv.line(W + 6, 7, W + 29, H - 7, b[5])
    cv.line(2 * W - 6, 7, W + 33, H - 7, b[5])
    rust_streaks(cv, W, 0, W, H, 648, 7)
    grime(cv, W, 0, W, H, 649, 0.55)
    # EDGES: the leading edges carry stripes too
    hazard(cv, 124, 0, 6, H, 650)
    noise_paint(cv, 0, 58, W, 6, STEEL, 651, 2, 4)
    # swatches 8 x 8
    noise_paint(cv, 132, 0, 8, 8, STEEL, 652, 3, 5, cell=2)
    noise_paint(cv, 140, 0, 8, 8, SLATE, 653, 1, 3, cell=2)
    for x in range(148, 156):
        for y in range(8):
            cv.set(x, y, STEEL[7] if y < 2 else STEEL[6] if y < 5 else STEEL[5])
    lamp_swatch(cv, 156, 0, 'off')
    lamp_swatch(cv, 160, 0, 'amber')
    lamp_swatch(cv, 164, 0, 'green')
    return cv


# ------------------------------------------------------------------ item icons

def dzg_icon():
    cv = Canvas(16, 16)
    g = DOOR_GREEN
    cv.rect(2, 0, 14, 16, STEEL[1])
    cv.rect(3, 1, 13, 15, g[4])
    cv.bevel(3, 1, 13, 15, g[6], g[2])
    for y in (3, 12):
        cv.set(2, y, STEEL[5])
    cv.ring(8, 8, 2.2, 3.6, RED_PAINT[3])
    cv.set(6, 6, RED_PAINT[5])
    cv.set(8, 8, STEEL[5])
    cv.set(7, 8, RED_PAINT[2])
    cv.set(9, 8, RED_PAINT[2])
    cv.set(8, 7, RED_PAINT[2])
    cv.set(8, 9, RED_PAINT[2])
    cv.set(12, 4, STEEL[6])
    cv.set(12, 11, STEEL[6])
    cv.set(7, 0, rgb('#ef9f33'))
    cv.set(8, 0, rgb('#ef9f33'))
    cv.set(5, 13, RUST[3])
    cv.set(10, 14, RUST[2])
    return cv


def gate_icon():
    cv = Canvas(16, 16)
    b = GATE_BLUE
    cv.rect(0, 1, 16, 16, STEEL[1])
    cv.rect(1, 2, 15, 15, b[4])
    hazard(cv, 1, 2, 3, 13, 661, period=4, worn=False)
    hazard(cv, 12, 2, 3, 13, 662, period=4, worn=False)
    cv.rect(4, 6, 12, 7, b[2])
    cv.rect(4, 11, 12, 12, b[2])
    for x in range(5, 12, 2):
        cv.set(x, 4, b[6])
    cv.rect(2, 0, 9, 2, SLATE[2])
    cv.rect(9, 0, 13, 1, STEEL[6])
    font.draw_text(cv, 6, 7, '7', rgb('#a8331f'))
    return cv


def logo():
    cv = Canvas(160, 48)
    # small ДЗГ door with frame
    g = DOOR_GREEN
    cv.rect(4, 4, 30, 44, STEEL[1])
    cv.rect(6, 6, 28, 43, g[4])
    noise_paint(cv, 6, 6, 22, 37, g, 671, 3, 5)
    cv.bevel(6, 6, 28, 43, g[6], g[1])
    cv.ring(17, 24, 4.5, 6.5, RED_PAINT[3])
    for a in (45, 135, 225, 315):
        cv.line(17, 24, 17 + math.cos(math.radians(a)) * 5, 24 + math.sin(math.radians(a)) * 5, RED_PAINT[2])
    cv.disc(17, 24, 1.5, STEEL[5])
    for y in (12, 34):
        cv.rect(24, y, 28, y + 2, STEEL[5])
    cv.rect(15, 2, 19, 4, rgb('#ef9f33'))
    rust_streaks(cv, 6, 6, 22, 37, 672, 4)
    # title
    font.draw_text(cv, 38, 9, 'ГЕРМОДВЕРИ', rgb('#1a1c1b'), scale=2)
    font.draw_text(cv, 37, 8, 'ГЕРМОДВЕРИ', CREAM, scale=2)
    font.draw_text(cv, 38, 25, 'ДЗГ-2  ·  ЗГ-4', rgb('#9aa69b'))
    font.draw_text(cv, 38, 33, 'HERMODOORS', rgb('#6f7f72'))
    return cv


if __name__ == '__main__':
    import sys
    out = sys.argv[1]
    for name, fn in (('frame_steel', frame_steel), ('hazard', hazard_tex)):
        fn().save(f'{out}/{name}.png')
    painted_frame(DOOR_GREEN, 681).save(f'{out}/dzg_frame.png')
    painted_frame(GATE_BLUE, 682).save(f'{out}/gate_frame.png')
    dzg_leaf().save(f'{out}/dzg_leaf.png')
    gate_leaf().save(f'{out}/germozatvor_leaf.png')
    dzg_icon().save(f'{out}/icon_dzg.png')
    gate_icon().save(f'{out}/icon_gate.png')
    logo().save(f'{out}/logo.png')
