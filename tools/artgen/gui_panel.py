"""panel.png: the control cabinet shared by every stage, plus lamps, tags and the blanking plate."""
import math
import random

import font
from common import Canvas, rgb, shade, mix, screw, rivet, speckle, ValueNoise
from palette import *


def hammertone(cv, x0, y0, x1, y1, seed, ramp=PAINT, lo=3, hi=5):
    """Hammered enamel: soft blotches with darker rims, the way the paint pools in the dimples."""
    n1 = ValueNoise(seed, 5)
    n2 = ValueNoise(seed + 7, 2)
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = n1(x, y, 2) * 0.75 + n2(x, y, 1) * 0.25
            if v < 0.36:
                i = lo
            elif v < 0.62:
                i = lo + 1
            else:
                i = hi
            # dimple rims
            e = abs(n1(x + 1, y, 2) - n1(x, y, 2)) + abs(n1(x, y + 1, 2) - n1(x, y, 2))
            if e > 0.075:
                i = max(0, i - 1)
            cv.set(x, y, ramp[i])


def chips(cv, x0, y0, x1, y1, seed, count, edge_bias=True):
    """Chipped paint: small irregular patches of primer or bare steel, with a light lip on the paint edge."""
    r = random.Random(seed)
    for _ in range(count):
        if edge_bias and r.random() < 0.7:
            side = r.randrange(4)
            if side == 0:
                cx, cy = r.randrange(x0, x1), y0 + r.randrange(0, 4)
            elif side == 1:
                cx, cy = r.randrange(x0, x1), y1 - 1 - r.randrange(0, 4)
            elif side == 2:
                cx, cy = x0 + r.randrange(0, 4), r.randrange(y0, y1)
            else:
                cx, cy = x1 - 1 - r.randrange(0, 4), r.randrange(y0, y1)
        else:
            cx, cy = r.randrange(x0, x1), r.randrange(y0, y1)
        size = r.choice([2, 2, 3, 3, 4])
        bare = r.random() < 0.3
        for i in range(size * 3):
            px = cx + r.randrange(-size + 1, size)
            py = cy + r.randrange(-1, 2)
            if x0 <= px < x1 and y0 <= py < y1:
                cv.set(px, py, STEEL[5] if bare else PRIMER[r.randrange(2)])
                if y0 <= py - 1 and cv.get(px, py - 1) in PAINT:
                    cv.set(px, py - 1, PAINT[min(len(PAINT) - 1, PAINT.index(cv.get(px, py - 1)) + 2)])


def scratches(cv, x0, y0, x1, y1, seed, count, color):
    r = random.Random(seed)
    for _ in range(count):
        x, y = r.randrange(x0, x1), r.randrange(y0, y1)
        a = r.uniform(-0.6, 0.6) + (math.pi if r.random() < 0.5 else 0)
        ln = r.randrange(3, 9)
        for i in range(ln):
            px, py = int(x + math.cos(a) * i), int(y + math.sin(a) * i * 0.5)
            if x0 <= px < x1 and y0 <= py < y1 and r.random() < 0.85:
                cv.set(px, py, color)


def grime(cv, x0, y0, x1, y1, seed, strength=0.35):
    """Darkening that collects toward the bottom."""
    n = ValueNoise(seed, 6)
    for y in range(y0, y1):
        t = (y - y0) / max(1, (y1 - y0))
        for x in range(x0, x1):
            v = n(x, y, 2)
            k = strength * t * t * (0.6 + v)
            if k > 0.02:
                c = cv.get(x, y)
                if c[3]:
                    cv.set(x, y, mix(c, rgb('#1d1a14'), min(0.6, k)))


def bakelite_plate(cv, x0, y0, x1, y1, seed):
    """Moulded bakelite: nearly flat black with a soft sheen band and a few scuffs."""
    r = random.Random(seed)
    for y in range(y0, y1):
        for x in range(x0, x1):
            c = BAKELITE[2]
            if y == y0 + 2 and (x + y) % 2 == 0 and y1 - y0 > 6:
                c = BAKELITE[3]
            cv.set(x, y, c)
    for _ in range((x1 - x0) * (y1 - y0) // 60):
        sx, sy = r.randrange(x0 + 1, x1 - 1), r.randrange(y0 + 1, y1 - 1)
        cv.set(sx, sy, BAKELITE[3])
        if r.random() < 0.4:
            cv.set(sx + 1, sy, BAKELITE[3])
    for x in range(x0, x1):
        cv.set(x, y0, BAKELITE[4])
        cv.set(x, y1 - 1, BAKELITE[0])
    for y in range(y0, y1):
        cv.set(x0, y, BAKELITE[4] if y < y1 - 1 else BAKELITE[1])
        cv.set(x1 - 1, y, BAKELITE[1])
    cv.set(x0, y0, BAKELITE[5])


def aluminium_plate(cv, x0, y0, x1, y1, seed):
    r = random.Random(seed)
    n = ValueNoise(seed, 9)
    row_off = {y: r.uniform(-0.12, 0.12) for y in range(y0, y1)}
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = 0.55 + row_off[y] + (n(x, y, 2) - 0.5) * 0.25 + r.uniform(-0.05, 0.05)
            i = max(1, min(len(ALU) - 2, int(v * (len(ALU) - 1))))
            cv.set(x, y, ALU[i])
    cv.bevel(x0, y0, x1, y1, ALU[6], ALU[0])


def lamp(cv, x, y, kind, size=10):
    cx, cy = x + size / 2, y + size / 2
    outer = size / 2
    lens_r = 2.9 if size >= 10 else 1.75
    colors = {
        'off': (rgb('#2d2522'), rgb('#3e3330'), rgb('#4d423d'), rgb('#857c76')),
        'red': (rgb('#a51e16'), rgb('#e23a2b'), rgb('#ff7a5c'), rgb('#ffe0d4')),
        'green': (rgb('#1c7a2a'), rgb('#3fc252'), rgb('#9ef59a'), rgb('#efffea')),
        'amber': (rgb('#a85d16'), rgb('#ef9f33'), rgb('#ffd37a'), rgb('#fff6dc')),
    }[kind]
    edge, mid, core, hi = colors
    lit = kind != 'off'

    def bezel(X, Y, d, px, py):
        if d > outer - 0.7:
            return STEEL[0]
        if d < lens_r + 0.45:
            return STEEL[1]
        s = px + py
        c = STEEL[7] if s < -2.2 else STEEL[6] if s < -0.6 else STEEL[5] if s < 1.2 else STEEL[3]
        return mix(c, mid, 0.22) if lit else c

    cv.ring(cx, cy, lens_r, outer, bezel)

    def lens(X, Y, d, px, py):
        t = d / lens_r
        c = mix(core, mid, min(1, t * 1.3)) if t < 0.75 else mix(mid, edge, (t - 0.75) / 0.25)
        if 0.45 < t < 0.62:
            c = shade(c, -0.12)  # fresnel ring
        return c

    cv.ring(cx, cy, 0, lens_r, lens)
    hx, hy = int(cx - lens_r * 0.45), int(cy - lens_r * 0.5)
    cv.set(hx, hy, hi)
    if size >= 10:
        cv.set(hx + 1, hy, mix(hi, mid, 0.5))


def build():
    cv = Canvas(256, 256)
    W, H = 256, 184
    # Body
    hammertone(cv, 0, 0, W, H, 11)
    grime(cv, 0, 0, W, H, 12, 0.3)
    chips(cv, 3, 3, W - 3, H - 3, 13, 28)
    scratches(cv, 3, 3, W - 3, H - 3, 14, 18, PAINT[7])
    # Frame bevel and rounded outline
    cv.bevel(1, 1, W - 1, H - 1, PAINT[7], PAINT[1])
    cv.bevel(2, 2, W - 2, H - 2, PAINT[6], PAINT[2])
    cv.outline(0, 0, W, H, OUTLINE)
    for (x, y) in [(0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)]:
        cv.px[x, y] = (0, 0, 0, 0)
    for (x, y) in [(1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)]:
        cv.px[x, y] = OUTLINE

    # Header nameplate (text is engraved at runtime)
    cv.rect(11, 5, 247, 20, PAINT[1])  # shadow
    bakelite_plate(cv, 10, 4, 246, 19, 21)
    screw(cv, 12, 9 - 2, 22)
    screw(cv, 240, 9 - 2, 23)

    # Procedure plate
    cv.rect(7, 24, 79, 179, PAINT[1])
    aluminium_plate(cv, 6, 23, 78, 178, 31)
    for (rx, ry) in [(8, 25), (73, 25), (8, 173), (73, 173)]:
        rivet(cv, rx, ry, ALU[4])
    for i in range(4):
        y = 50 + i * 31 - 3
        for x in range(10, 75):
            if x % 2 == 0 or i == 0:
                cv.set(x, y, mix(ALU[2], PRINT_INK, 0.55))
    font.draw_text_centered(cv, 42, 171, 'П/Я Г-4417', mix(ALU[2], PRINT_INK, 0.75))

    # Stage recess
    cv.rect(80, 22, 250, 178, GASKET)
    for x in range(80, 250):
        cv.set(x, 22, OUTLINE)
        cv.set(x, 177, PAINT[6])
    for y in range(22, 178):
        cv.set(80, y, OUTLINE)
        cv.set(249, y, PAINT[6])

    # Sprites
    lamp(cv, 0, 184, 'off')
    lamp(cv, 10, 184, 'red')
    lamp(cv, 20, 184, 'green')
    lamp(cv, 30, 184, 'amber')
    lamp(cv, 50, 184, 'off', 6)
    lamp(cv, 56, 184, 'green', 6)
    lamp(cv, 62, 184, 'red', 6)
    bakelite_plate(cv, 100, 184, 116, 191, 41)
    # Blanking plate: a cover from a different paint batch, two screws, faded stencil.
    blank = [rgb(h) for h in ('#3c4549', '#4b555a', '#58636a', '#66727a', '#76838b', '#8a979e')]
    hammertone(cv, 0, 196, 64, 222, 51, blank, 1, 3)
    cv.bevel(0, 196, 64, 222, blank[5], blank[0])
    screw(cv, 3, 206, 52)
    screw(cv, 56, 206, 53)
    font.draw_text_centered(cv, 32, 207, 'ЗАГЛУШКА', mix(blank[3], WHITE_PAINT, 0.55))
    chips(cv, 1, 197, 63, 221, 54, 8)
    return cv


if __name__ == '__main__':
    import sys
    out = sys.argv[1]
    build().save(out)
