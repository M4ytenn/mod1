"""stage_clamps.png: enamel clamp diagram, warning plate, close-up of the clamp with its chalk scale, slate."""
import math
import random

import font
from common import Canvas, rgb, shade, mix, screw, rivet, ValueNoise
from gui_panel import hammertone, chips, scratches, grime
from gui_power import paint_label
from palette import *

SW, SH = 166, 152
HUB = (108, 94)
ENAMEL = [rgb(h) for h in ('#b9b49f', '#d3ceba', '#e6e1cf', '#f0ecdd')]
ENAMEL_INK = rgb('#22304d')


def enamel_plate(cv, x0, y0, x1, y1, seed):
    n = ValueNoise(seed, 8)
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = n(x, y, 2)
            cv.set(x, y, ENAMEL[3] if v > 0.62 else ENAMEL[2])
    cv.outline(x0, y0, x1, y1, ENAMEL[0])
    # chipped enamel shows black steel, mostly at the edges and round the screw holes
    r = random.Random(seed)
    for _ in range(14):
        side = r.randrange(4)
        if side == 0:
            cx, cy = r.randrange(x0, x1), y0
        elif side == 1:
            cx, cy = r.randrange(x0, x1), y1 - 1
        elif side == 2:
            cx, cy = x0, r.randrange(y0, y1)
        else:
            cx, cy = x1 - 1, r.randrange(y0, y1)
        for _ in range(r.randrange(2, 6)):
            px, py = cx + r.randrange(-2, 3), cy + r.randrange(-2, 3)
            if x0 <= px < x1 and y0 <= py < y1:
                cv.set(px, py, rgb('#1f2122'))
                if r.random() < 0.3:
                    cv.set(px, py, RUST[2])


def build():
    cv = Canvas(256, 256)
    hammertone(cv, 0, 0, SW, SH, 301, lo=4, hi=6)
    grime(cv, 0, 0, SW, SH, 302, 0.25)
    scratches(cv, 0, 0, SW, SH, 303, 8, PAINT[7])

    # --- clamp diagram (enamel), plate-local coords match ClampsView.SLOTS
    px0, py0 = 4, 4
    cv.rect(px0 + 1, py0 + 1, px0 + 57, py0 + 87, PAINT[2])
    enamel_plate(cv, px0, py0, px0 + 56, py0 + 86, 304)
    ink = ENAMEL_INK
    font.draw_text_centered(cv, px0 + 28, py0 + 1, 'ЗАТВОРЫ', ink)
    lx0, ly0, lx1, ly1 = px0 + 10, py0 + 11, px0 + 46, py0 + 79
    cv.outline(lx0, ly0, lx1 + 1, ly1 + 1, ink)
    cv.outline(lx0 + 2, ly0 + 2, lx1 - 1, ly1 - 1, mix(ink, ENAMEL[2], 0.6))
    for hy in (ly0 + 8, ly0 + 34, ly0 + 60):
        cv.rect(lx0 - 3, hy, lx0, hy + 5, ink)
    cv.ring(px0 + 28, py0 + 45, 5.2, 6.2, ink)
    cv.line(px0 + 28, py0 + 39, px0 + 28, py0 + 51, ink)
    cv.line(px0 + 22, py0 + 45, px0 + 34, py0 + 45, ink)
    screw(cv, px0 + 2, py0 + 80, 305, 3)
    screw(cv, px0 + 51, py0 + 80, 306, 3)

    # --- warning plate
    wx0, wy0, wx1, wy1 = 4, 95, 60, 148
    yellow = HAZARD_YELLOW
    cv.rect(wx0 + 1, wy0 + 1, wx1 + 1, wy1 + 1, PAINT[2])
    n = ValueNoise(307, 6)
    for y in range(wy0, wy1):
        for x in range(wx0, wx1):
            cv.set(x, y, yellow[2] if n(x, y, 2) < 0.6 else yellow[3])
    cv.outline(wx0, wy0, wx1, wy1, HAZARD_BLACK[1])
    cv.outline(wx0 + 2, wy0 + 2, wx1 - 2, wy1 - 2, HAZARD_BLACK[1])
    black = HAZARD_BLACK[1]
    # warning triangle
    tx, ty = 32, 100
    for row in range(10):
        for x in range(tx - row, tx + row + 1):
            edge = x in (tx - row, tx + row) or row == 9
            if edge or row < 2:
                cv.set(x, ty + row, black)
    cv.set(tx, ty + 4, black)
    cv.set(tx, ty + 5, black)
    cv.set(tx, ty + 6, black)
    cv.set(tx, ty + 8, black)
    font.draw_text_centered(cv, 32, 112, 'ОСТОРОЖНО', black)
    font.draw_text_centered(cv, 32, 120, 'ЗАТВОРЫ', black)
    font.draw_text_centered(cv, 32, 127, 'ПОД', black)
    font.draw_text_centered(cv, 32, 134, 'НАТЯГОМ', black)
    for (sx, sy) in ((wx0 + 3, wy0 + 3), (wx1 - 6, wy0 + 3), (wx0 + 3, wy1 - 6), (wx1 - 6, wy1 - 6)):
        screw(cv, sx, sy, sx * 3 + sy, 3)
    r = random.Random(308)
    for _ in range(40):
        x, y = r.randrange(wx0, wx1), r.randrange(wy0, wy1)
        if r.random() < 0.5:
            cv.set(x, y, mix(cv.get(x, y), RUST[3], 0.6))

    # --- close-up of the clamp on the door edge
    cx0, cy0, cx1, cy1 = 64, 4, 162, 108
    door = DOOR_GREEN
    nz = ValueNoise(309, 5)
    for y in range(cy0, cy1):
        for x in range(cx0, cx1):
            v = nz(x, y, 3)
            cv.set(x, y, door[3] if v < 0.45 else door[4] if v < 0.7 else door[5])
    cv.bevel(cx0, cy0, cx1, cy1, door[6], door[0])
    # weld seam across the top and a row of rivets
    for x in range(cx0 + 2, cx1 - 2):
        cv.set(x, cy0 + 9, door[1] if x % 3 else door[2])
        cv.set(x, cy0 + 10, door[5])
    for x in range(cx0 + 5, cx1 - 5, 9):
        rivet(cv, x, cy0 + 4, door[4])
        # rust streak running down from each rivet
        rr = random.Random(x)
        length = rr.randrange(4, 18)
        for k in range(length):
            yy = cy0 + 7 + k
            col = mix(cv.get(x + 1, yy), RUST[2 if k < length // 2 else 3], 0.55 - k / length * 0.4)
            cv.set(x + 1, yy, col)
            if rr.random() < 0.3:
                cv.set(x + 2, yy, mix(cv.get(x + 2, yy), RUST[3], 0.25))
    # paint chips with rust
    r = random.Random(310)
    for _ in range(22):
        x, y = r.randrange(cx0 + 2, cx1 - 3), r.randrange(cy0 + 12, cy1 - 3)
        for _ in range(r.randrange(2, 7)):
            px, py = x + r.randrange(-2, 3), y + r.randrange(-1, 2)
            cv.set(px, py, RUST[r.randrange(1, 4)])
    # painted scale round the hub (white ticks) - target zone drawn at runtime in chalk
    paint = WHITE_PAINT
    for k in range(11):
        ang = -120 + 6 * k
        a = math.radians(ang)
        long = k in (0, 5, 10)
        for rr in [70 + i * 0.5 for i in range(int((8 if long else 5) * 2))]:
            x, y = HUB[0] + math.cos(a) * rr, HUB[1] + math.sin(a) * rr
            cv.set(int(x), int(y), paint)
    # boss behind the hub
    cv.ring(HUB[0], HUB[1], 0, 10, lambda X, Y, d, px, py: door[1] if d > 8.6 else (door[5] if px + py < -4 else door[2]))

    # --- slate for the chalk tally
    sx0, sy0, sx1, sy1 = 64, 112, 162, 148
    cv.rect(sx0 - 1, sy0 - 1, sx1 + 1, sy1 + 1, rgb('#5a3f27'))
    cv.bevel(sx0 - 1, sy0 - 1, sx1 + 1, sy1 + 1, rgb('#7a5a3a'), rgb('#3a2818'))
    ns = ValueNoise(311, 5)
    for y in range(sy0, sy1):
        for x in range(sx0, sx1):
            v = ns(x, y, 3)
            cv.set(x, y, SLATE[1] if v < 0.45 else SLATE[2] if v < 0.7 else SLATE[3])
    # old chalk smudges
    r = random.Random(312)
    for _ in range(5):
        x, y = r.randrange(sx0 + 4, sx1 - 10), r.randrange(sy0 + 3, sy1 - 3)
        for k in range(r.randrange(5, 14)):
            cv.set(x + k, y + r.randrange(-1, 2), mix(SLATE[2], CHALK, 0.18))
    # the tally of blows still needed is chalked on in game, right after this label (ClampsView.TALLY_X)
    font.draw_text(cv, 69, 119, 'УДАРЫ:', mix(CHALK, SLATE[2], 0.25))

    # --- sprites
    # sledgehammer raised (head top right) and striking (head bottom left)
    def hammer(ox, oy, strike):
        wood = [rgb('#4a2f1a'), rgb('#6b4526'), rgb('#8a5c33'), rgb('#a8763f')]
        if not strike:
            hx0, hy0, hx1, hy1 = ox + 4, oy + 24, ox + 21, oy + 7
            head = (ox + 15, oy + 0, ox + 29, oy + 9)
        else:
            hx0, hy0, hx1, hy1 = ox + 28, oy + 22, ox + 10, oy + 14
            head = (ox + 0, oy + 10, ox + 13, oy + 19)
        cv.capsule(hx0, hy0, hx1, hy1, 2.1, wood[2], wood[3], wood[0])
        x0, y0, x1, y1 = head
        cv.rect(x0, y0, x1, y1, STEEL[3])
        cv.bevel(x0, y0, x1, y1, STEEL[6], STEEL[1])
        cv.rect(x0 + 1, y0 + 1, x0 + 3, y1 - 1, STEEL[5])
        cv.rect(x1 - 3, y0 + 1, x1 - 1, y1 - 1, STEEL[2])
    hammer(168, 0, False)
    hammer(200, 0, True)
    # lever knob (rusty ball), hub
    cv.ring(172, 52, 0, 3.6, lambda X, Y, d, px, py: RUST[5] if px + py < -2.4 else (RUST[1] if px + py > 1.4 else RUST[3]))
    cv.ring(183, 55, 0, 6.8, lambda X, Y, d, px, py: STEEL[1] if d > 5.9 else (STEEL[6] if px + py < -3 else STEEL[4] if px + py < 2 else STEEL[2]))
    for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        cv.set(182 + dx, 54 + dy, STEEL[1])
    # chalk marks, two variants (6 x 8)
    for i, shape in enumerate((["..##..", ".####.", "######", ".####.", "..##..", "..##..", "...#..", "......"],
                               ["...#..", "..###.", ".####.", "######", "..##..", "..#...", "..#...", "......"])):
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch == '#':
                    cv.set(190 + i * 6 + x, 48 + y, CHALK if (x + y) % 3 else mix(CHALK, SLATE[3], 0.3))
    # diagram icons 7 x 7: seized (rust), loosening (steel), free (steel + green dot)
    def icon(ox, oy, body, dot=None):
        shape = ["..###..", ".#####.", "#######", "#######", "#######", ".#####.", "..###.."]
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch == '#':
                    cv.set(ox + x, oy + y, body[0] if (x + y) > 8 else body[1] if (x + y) < 4 else body[2])
        cv.rect(ox + 3, oy + 1, ox + 4, oy + 6, body[3])
        if dot:
            cv.set(ox + 3, oy + 3, dot)
    icon(168, 64, (RUST[1], RUST[4], RUST[3], RUST[0]))
    icon(175, 64, (STEEL[2], STEEL[6], STEEL[4], STEEL[1]))
    icon(182, 64, (STEEL[3], STEEL[7], STEEL[5], STEEL[2]), rgb('#3fae4c'))
    # red pencil ring round the clamp being worked (chalk would vanish on the cream enamel), 13 x 13
    pencil = rgb('#b02a1e')
    for k in range(46):
        a = k / 40 * math.tau
        rr = 5.6 + math.sin(a * 3) * 0.4
        cv.set(int(196.5 + math.cos(a) * rr), int(70.5 + math.sin(a) * rr), pencil)
    return cv


if __name__ == '__main__':
    import sys
    build().save(sys.argv[1])
