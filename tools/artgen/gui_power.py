"""stage_power.png: breaker panel interior, knife switch, breakers, sparks, and the wiring diagram card."""
import math
import random

import font
from common import Canvas, rgb, shade, mix, screw, rivet, ValueNoise
from gui_panel import hammertone, chips, scratches, grime, bakelite_plate
from palette import *

SW, SH = 166, 152


def paint_label(cv, cx, y, text, seed, color=None, wear=0.12):
    """Hand-stencilled white paint, a few pixels worn off."""
    color = color or WHITE_PAINT
    tmp = Canvas(cv.w, cv.h)
    font.draw_text_centered(tmp, cx, y, text, color)
    r = random.Random(seed)
    for yy in range(max(0, y - 1), min(cv.h, y + 7)):
        for xx in range(cv.w):
            c = tmp.px[xx, yy]
            if c[3] and r.random() > wear:
                cv.set(xx, yy, c)


def paper(cv, x0, y0, x1, y1, seed, torn_corner=True):
    n = ValueNoise(seed, 7)
    r = random.Random(seed)
    for y in range(y0, y1):
        for x in range(x0, x1):
            edge = min(x - x0, x1 - 1 - x, y - y0, y1 - 1 - y)
            v = n(x, y, 3) * 0.6 + 0.4 - (0.25 if edge < 2 else 0.12 if edge < 5 else 0)
            i = max(0, min(len(PAPER) - 1, int(v * len(PAPER))))
            cv.set(x, y, PAPER[i])
    # fibres
    for _ in range((x1 - x0) * (y1 - y0) // 30):
        fx, fy = r.randrange(x0 + 1, x1 - 2), r.randrange(y0 + 1, y1 - 1)
        c = PAPER[max(0, PAPER.index(cv.get(fx, fy)) - 1)] if cv.get(fx, fy) in PAPER else PAPER[2]
        cv.set(fx, fy, c)
        cv.set(fx + 1, fy, c)
    if torn_corner:
        for i in range(5):
            for j in range(5 - i):
                cv.px[x1 - 1 - j, y0 + i] = (0, 0, 0, 0)
        cv.set(x1 - 6, y0, PAPER[1])
        cv.set(x1 - 1, y0 + 5, PAPER[1])


def tape(cv, x, y, w, h, seed):
    r = random.Random(seed)
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            jag = (xx == x and r.random() < 0.5) or (xx == x + w - 1 and r.random() < 0.5)
            if not jag:
                cv.set(xx, yy, (233, 214, 150, 110))
    for xx in range(x, x + w):
        cv.set(xx, y, (245, 232, 180, 120))


def stain_ring(cv, cx, cy, r0, r1, color, a0, a1):
    cv.ring(cx, cy, r0, r1, color, a0, a1)


def knife_switch(cv, ox, oy, state):
    """Single-throw double-pole knife switch on a slate base, 28 x 52. state: 0 off, 1 mid, 2 on."""
    W, H = 28, 52
    slate = SLATE
    # base board
    n = ValueNoise(91, 4)
    for y in range(H):
        for x in range(W):
            v = n(x, y, 2)
            cv.set(ox + x, oy + y, slate[2] if v < 0.5 else slate[3])
    cv.bevel(ox, oy, ox + W, oy + H, slate[4], slate[0])
    for (sx, sy) in [(1, 1), (W - 4, 1), (1, H - 4), (W - 4, H - 4)]:
        screw(cv, ox + sx, oy + sy, 90 + sx + sy, 3)
    # jaws at the top (fixed contacts) and hinge clips at the bottom
    for px in (8, 18):
        for (y0, y1) in ((7, 13), (40, 46)):
            for y in range(y0, y1):
                cv.set(ox + px - 1, oy + y, COPPER[1])
                cv.set(ox + px + 2, oy + y, COPPER[1])
            cv.rect(ox + px - 1, oy + y0, ox + px + 3, oy + y0 + 1, COPPER[3])
            cv.set(ox + px, oy + y1 - 1, COPPER[2])
            cv.set(ox + px + 1, oy + y1 - 1, COPPER[2])
        # terminal screws
        cv.set(ox + px, oy + 5, BRASS[4])
        cv.set(ox + px + 1, oy + 5, BRASS[2])
        cv.set(ox + px, oy + 48, BRASS[4])
        cv.set(ox + px + 1, oy + 48, BRASS[2])
    # blades: pivot at y=43, tip height depends on state (foreshortened when thrown out)
    tip = {0: 30, 1: 21, 2: 9}[state]
    for px in (8, 18):
        for y in range(tip, 44):
            cv.set(ox + px, oy + y, COPPER[4] if state == 2 else COPPER[3])
            cv.set(ox + px + 1, oy + y, COPPER[2])
    # insulating crossbar and handle
    bar_y = tip + 2
    cv.rect(ox + 6, oy + bar_y, ox + 22, oy + bar_y + 3, BAKELITE[2])
    cv.rect(ox + 6, oy + bar_y, ox + 22, oy + bar_y + 1, BAKELITE[4])
    hl = {0: 3, 1: 5, 2: 8}[state]
    cv.rect(ox + 12, oy + bar_y - hl, ox + 16, oy + bar_y, BAKELITE[3])
    cv.rect(ox + 11, oy + bar_y - hl - 3, ox + 17, oy + bar_y - hl, BAKELITE[2])
    cv.rect(ox + 11, oy + bar_y - hl - 3, ox + 17, oy + bar_y - hl - 2, BAKELITE[5])
    # ВКЛ / ОТКЛ markings
    font.draw_text_centered(cv, ox + 14, oy + 1 if state != 2 else oy + 1, '', WHITE_PAINT)


def breaker(cv, ox, oy, state, seed):
    """Moulded-case breaker in brown carbolite, 16 x 28. state: 'open', 'closed', 'mid'."""
    W, H = 16, 28
    body = [rgb(h) for h in ('#170f0b', '#22170f', '#2e2016', '#3b2a1e', '#4c3727', '#634a36')]
    cv.rect(ox, oy, ox + W, oy + H, body[2])
    cv.bevel(ox, oy, ox + W, oy + H, body[4], body[0])
    # terminal covers top and bottom
    for ty in (oy + 1, oy + H - 5):
        cv.rect(ox + 1, ty, ox + W - 1, ty + 4, body[3])
        cv.set(ox + 4, ty + 2, BRASS[3])
        cv.set(ox + W - 5, ty + 2, BRASS[3])
    # label strip
    cv.rect(ox + 2, oy + 6, ox + 14, oy + 8, rgb('#bfb8a4'))
    for x in range(ox + 3, ox + 13):
        if (x * 7 + seed) % 3 == 0:
            cv.set(x, oy + 6, rgb('#5d574a'))
    # lever slot
    cv.rect(ox + 5, oy + 10, ox + 11, oy + 21, body[0])
    lever = rgb('#4a4744')
    tip = rgb('#dcd6c6')
    if state == 'closed':
        cv.rect(ox + 6, oy + 9, ox + 10, oy + 16, lever)
        cv.rect(ox + 6, oy + 9, ox + 10, oy + 11, tip)
        cv.rect(ox + 6, oy + 11, ox + 7, oy + 16, shade(lever, 0.3))
    elif state == 'open':
        cv.rect(ox + 6, oy + 15, ox + 10, oy + 22, lever)
        cv.rect(ox + 6, oy + 20, ox + 10, oy + 22, tip)
        cv.rect(ox + 6, oy + 15, ox + 7, oy + 20, shade(lever, 0.3))
    else:
        cv.rect(ox + 5, oy + 13, ox + 11, oy + 18, lever)
        cv.rect(ox + 5, oy + 13, ox + 11, oy + 14, tip)
    # trip flag window
    flag = rgb('#3fae4c') if state == 'closed' else rgb('#c43a2a') if state == 'mid' else rgb('#d8d2c2')
    cv.rect(ox + 12, oy + 14, ox + 14, oy + 17, flag)


def spark(cv, ox, oy, frame):
    cx, cy = ox + 6, oy + 6
    r = random.Random(frame * 7 + 3)
    core = [rgb('#fffbe8'), rgb('#ffe680'), rgb('#ffb037'), rgb('#e0601c')]
    length = [4, 6, 5][frame]
    for k in range(9):
        a = r.uniform(0, math.tau)
        ln = r.uniform(length * 0.6, length)
        for i in range(int(ln) + 1):
            c = core[min(3, int(i / ln * 3.2))]
            cv.set(int(cx + math.cos(a) * i), int(cy + math.sin(a) * i), c)
    cv.set(cx, cy, core[0])
    if frame < 2:
        cv.set(cx + 1, cy, core[0])
        cv.set(cx, cy + 1, core[1])


def build():
    cv = Canvas(256, 256)
    hammertone(cv, 0, 0, SW, SH, 101, lo=4, hi=6)
    grime(cv, 0, 0, SW, SH, 102, 0.25)
    chips(cv, 0, 0, SW, SH, 103, 10)
    scratches(cv, 0, 0, SW, SH, 104, 10, PAINT[7])
    # knife switch footprint shadow
    cv.rect(7, 4, 35, 56, PAINT[2])
    # lamp sockets and labels
    for (x, label) in ((6, 'СЕТЬ'), (24, 'АВАР')):
        cv.ring(x + 5, 62, 4.6, 6.2, PAINT[2])
        paint_label(cv, x + 5, 69, label, 105 + x, wear=0.0)
    # DIN rail behind the breakers
    cv.rect(37, 22, 165, 34, PAINT[2])
    for y, c in ((23, STEEL[6]), (24, STEEL[5]), (25, STEEL[4]), (26, STEEL[3]), (27, STEEL[3]), (28, STEEL[3]), (29, STEEL[3]), (30, STEEL[4]), (31, STEEL[5]), (32, STEEL[2])):
        for x in range(38, 164):
            cv.set(x, y, c)
    for x in range(42, 162, 9):
        cv.rect(x, 26, x + 4, 29, STEEL[1])
    screw(cv, 39, 25, 107, 3)
    screw(cv, 160, 25, 108, 3)
    # terminal block with wires dropping behind the card
    wire_colors = [rgb('#2b2b2b'), rgb('#a13527'), rgb('#2f4e8c'), rgb('#c9b23a'), rgb('#2b2b2b'), rgb('#a13527')]
    for i, x in enumerate(range(42, 162, 7)):
        cv.rect(x, 57, x + 6, 66, rgb('#7d776a'))
        cv.rect(x, 57, x + 6, 58, rgb('#9a9484'))
        cv.rect(x + 5, 57, x + 6, 66, rgb('#5d584e'))
        cv.set(x + 2, 59, BRASS[4])
        cv.set(x + 3, 59, BRASS[3])
        cv.set(x + 2, 60, BRASS[2])
        cv.set(x + 3, 60, BRASS[1])
        wc = wire_colors[i % len(wire_colors)]
        for y in range(66, 75):
            cv.set(x + 2, y, wc)
            cv.set(x + 3, y, shade(wc, -0.35))
        cv.set(x + 2, 66, shade(wc, 0.3))
    paint_label(cv, 101, 51 - 4, '', 0)
    # Wiring diagram card
    cx0, cy0, cw, ch = 4, 75, 158, 74
    cv.rect(cx0 + 1, cy0 + 1, cx0 + cw + 1, cy0 + ch + 1, PAINT[2])
    paper(cv, cx0, cy0, cx0 + cw, cy0 + ch, 111)
    # fold crease
    for y in range(cy0 + 1, cy0 + ch - 1):
        cv.set(cx0 + 79, y, mix(cv.get(cx0 + 79, y), rgb('#ffffff'), 0.35))
        cv.set(cx0 + 80, y, mix(cv.get(cx0 + 80, y), PAPER[0], 0.5))
    # coffee ring and a water mark
    stain_ring(cv, cx0 + 18, cy0 + 62, 8.2, 9.6, (120, 82, 40, 70), 200, 70)
    stain_ring(cv, cx0 + 18, cy0 + 62, 8.0, 8.6, (100, 66, 30, 60), 230, 300)
    n = ValueNoise(113, 6)
    for y in range(cy0 + 30, cy0 + 60):
        for x in range(cx0 + 96, cx0 + 140):
            if math.hypot((x - cx0 - 118) / 1.4, y - cy0 - 45) < 14 and n(x, y, 2) > 0.55:
                cv.set(x, y, mix(cv.get(x, y), PAPER[0], 0.35))
    # printed heading and title block
    ink = mix(PRINT_INK, PAPER[3], 0.25)
    font.draw_text(cv, cx0 + 92, cy0 + 3, 'СХЕМА ПИТАНИЯ', ink)
    bx0, by0, bx1, by1 = cx0 + 106, cy0 + 65, cx0 + cw - 2, cy0 + ch - 1
    cv.outline(bx0, by0, bx1, by1 + 1, ink)
    cv.line(bx0 + 18, by0, bx0 + 18, by1, ink)
    font.draw_text(cv, bx0 + 2, by0 + 1, 'Щ-1', ink)
    font.draw_text(cv, bx0 + 21, by0 + 1, 'ЛИСТ 1', ink)
    # sticky tape
    tape(cv, cx0 - 2, cy0 - 2, 12, 5, 114)
    tape(cv, cx0 + cw - 12, cy0 + 2, 11, 5, 115)

    # Sprites: knife switch states, breakers, sparks
    for i, st in enumerate((0, 1, 2)):
        knife_switch(cv, 168 + i * 28, 0, st)
    breaker(cv, 168, 60, 'open', 1)
    breaker(cv, 184, 60, 'closed', 2)
    breaker(cv, 200, 60, 'mid', 3)
    for f in range(3):
        spark(cv, 168 + f * 12, 90, f)
    return cv


if __name__ == '__main__':
    import sys
    build().save(sys.argv[1])
