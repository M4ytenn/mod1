"""stage_wheel.png (512 x 256): door face round the handwheel, the wheel rim and hub, counter drums, strain gauge."""
import math
import random

import font
from common import Canvas, rgb, shade, mix, screw, rivet, ValueNoise
from gui_panel import hammertone, chips, scratches, grime, bakelite_plate
from gui_power import paint_label
from palette import *

SW, SH = 166, 152
CX, CY = 62, 80

DIGITS = {
    '0': [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
    '1': ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    '2': [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    '3': ["#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."],
    '4': ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    '5': ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    '6': ["..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."],
    '7': ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
    '8': [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    '9': [".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."],
}


def rim(cv, ox, oy):
    """Red handwheel rim, 112 x 112, shaded as a torus lit from the top left."""
    c = 56
    light = (-0.45, -0.55, 0.70)
    ramp = RED_PAINT

    def px(X, Y, d, dx, dy):
        t = (d - 51.2) / 4.9
        t = max(-1, min(1, t))
        nz = math.sqrt(max(0, 1 - t * t))
        ux, uy = dx / (d or 1), dy / (d or 1)
        nx, ny = ux * t, uy * t
        b = nx * light[0] + ny * light[1] + nz * light[2]
        if abs(t) > 0.93:
            return ramp[0]
        i = 1 if b < 0.15 else 2 if b < 0.45 else 3 if b < 0.7 else 4 if b < 0.88 else 5
        return ramp[i]

    cv.ring(ox + c, oy + c, 46, 56.4, px)
    # hands have worn the paint off the outer top edge in places
    r = random.Random(401)
    for k in range(9):
        a0 = r.uniform(math.pi * 0.9, math.pi * 1.6)
        for j in range(r.randrange(3, 7)):
            a = a0 + j * 0.018
            rr = 54.2 + r.uniform(-0.4, 0.6)
            cv.set(int(ox + c + math.cos(a) * rr), int(oy + c + math.sin(a) * rr), STEEL[5] if j % 2 else STEEL[6])


def hub(cv, ox, oy):
    c = 13

    def body(X, Y, d, dx, dy):
        if d > 12.2:
            return OUTLINE
        if d > 9.5:
            s = dx + dy
            return RED_PAINT[4] if s < -6 else RED_PAINT[3] if s < 4 else RED_PAINT[1]
        s = dx + dy
        return STEEL[6] if s < -5 else STEEL[4] if s < 4 else STEEL[2]

    cv.ring(ox + c, oy + c, 0, 13, body)
    # hex nut
    pts = [(ox + c + math.cos(math.radians(30 + 60 * k)) * 5.2, oy + c + math.sin(math.radians(30 + 60 * k)) * 5.2) for k in range(6)]
    for y in range(oy + c - 6, oy + c + 7):
        for x in range(ox + c - 6, ox + c + 7):
            inside = True
            for k in range(6):
                x0, y0 = pts[k]
                x1, y1 = pts[(k + 1) % 6]
                if (x1 - x0) * (y + 0.5 - y0) - (y1 - y0) * (x + 0.5 - x0) < 0:
                    inside = False
                    break
            if inside:
                s = (x - ox - c) + (y - oy - c)
                cv.set(x, y, STEEL[7] if s < -3 else STEEL[5] if s < 2 else STEEL[3])
    cv.disc(ox + c, oy + c, 1.6, STEEL[1])


def drum_strip(cv, ox, oy):
    """0..9 then 0 again, 7 x 11 per digit, white figures on a black drum."""
    for i, ch in enumerate('01234567890'):
        y0 = oy + i * 11
        for y in range(11):
            shade_k = abs(y - 5) / 5
            base = mix(BAKELITE[3], BAKELITE[0], shade_k * 0.8)
            for x in range(7):
                cv.set(ox + x, y0 + y, base)
        for y, row in enumerate(DIGITS[ch]):
            for x, bit in enumerate(row):
                if bit == '#':
                    k = abs(y + 2 - 5) / 5
                    cv.set(ox + 1 + x, y0 + 2 + y, mix(rgb('#ece6d2'), rgb('#8f8a7c'), k * 0.6))


def arrow_arc(cv, cx, cy, r, a_from, a_to, color, seed, wear=0.0):
    """Painted arc arrow; head at a_to."""
    rnd = random.Random(seed)
    step = 1 if a_to > a_from else -1
    a = a_from
    while (a - a_to) * step < 0:
        rad = math.radians(a)
        for rr in (r, r + 1):
            if rnd.random() >= wear:
                cv.set(int(cx + math.cos(rad) * rr), int(cy + math.sin(rad) * rr), color)
        a += step * 0.8
    rad = math.radians(a_to)
    tx, ty = cx + math.cos(rad) * (r + 0.5), cy + math.sin(rad) * (r + 0.5)
    # tangent direction of travel
    tdx, tdy = -math.sin(rad) * step, math.cos(rad) * step
    nx, ny = math.cos(rad), math.sin(rad)
    for k in range(5):
        for side in (-1, 1):
            px = tx - tdx * k + nx * side * k * 0.8
            py = ty - tdy * k + ny * side * k * 0.8
            if rnd.random() >= wear:
                cv.set(int(px), int(py), color)


def build():
    cv = Canvas(512, 256)
    door = DOOR_GREEN
    hammertone(cv, 0, 0, SW, SH, 402, door, 3, 5)
    grime(cv, 0, 0, SW, SH, 403, 0.3)
    chips(cv, 0, 0, SW, SH, 404, 14)
    scratches(cv, 0, 0, SW, SH, 405, 12, door[6])
    # rivet rows top and bottom, weld seam on the right
    for x in range(5, SW - 4, 10):
        rivet(cv, x, 2, door[4])
        rivet(cv, x, SH - 5, door[4])
    for y in range(8, SH - 8):
        cv.set(120, y, door[1] if y % 3 else door[2])
        cv.set(121, y, door[5])
    # wear ring where gloves brush the door round the wheel
    n = ValueNoise(406, 4)
    cv.ring(CX, CY, 56, 60, lambda X, Y, d, dx, dy: door[2] if n(X, Y, 2) < 0.5 else door[3])
    # painted direction arrows
    arrow_arc(cv, CX, CY, 63, 300, 214, WHITE_PAINT, 407, 0.04)
    font.draw_text(cv, 6, 9, 'ОТКР.', WHITE_PAINT)
    arrow_arc(cv, CX, CY, 63, 60, 140, mix(WHITE_PAINT, door[4], 0.55), 408, 0.45)
    paint_label(cv, 106, 140, 'ЗАКР.', 409, mix(WHITE_PAINT, door[4], 0.55), 0.45)

    # counter housing
    cv.rect(125, 9, 162, 37, door[1])
    bakelite_plate(cv, 124, 8, 161, 36, 410)
    cv.rect(128, 14, 154, 29, BAKELITE[0])
    cv.rect(129, 15, 153, 28, rgb('#0f0f0e'))
    paint_label(cv, 143, 40, 'ОБОРОТЫ', 411, wear=0.0)
    # strain gauge
    sgx, sgy = 144, 70

    def sg_face(X, Y, d, dx, dy):
        if d > 16.2:
            return OUTLINE
        if d > 15:
            return STEEL[6] if dx + dy < 0 else STEEL[2]
        return rgb('#e6dfca') if d < 13.5 else rgb('#d6cfb8')

    cv.ring(sgx, sgy, 0, 17, sg_face)
    cv.ring(sgx, sgy, 9.6, 12.2, rgb('#3f8a45'), 135, 135 + 270 * 0.6)
    cv.ring(sgx, sgy, 9.6, 12.2, rgb('#d4a62a'), 135 + 270 * 0.6, 135 + 270 * 0.85)
    cv.ring(sgx, sgy, 9.6, 12.2, rgb('#b0281c'), 135 + 270 * 0.85, 135 + 270)
    font.draw_text_centered(cv, sgx, sgy + 5, 'КН', rgb('#1e1d1a'))
    paint_label(cv, 144, 90, 'НАГРУЗКА', 412, wear=0.0)
    # instruction plate
    ix0, iy0, ix1, iy1 = 126, 102, 162, 132
    cv.rect(ix0 + 1, iy0 + 1, ix1 + 1, iy1 + 1, door[1])
    from gui_panel import aluminium_plate
    aluminium_plate(cv, ix0, iy0, ix1, iy1, 413)
    ink = PRINT_INK
    font.draw_text_centered(cv, (ix0 + ix1) // 2, iy0 + 3, 'ВРАЩАТЬ', ink)
    font.draw_text_centered(cv, (ix0 + ix1) // 2, iy0 + 10, 'ПЛАВНО', ink)
    font.draw_text_centered(cv, (ix0 + ix1) // 2, iy0 + 17, 'БЕЗ', ink)
    font.draw_text_centered(cv, (ix0 + ix1) // 2, iy0 + 23, 'РЫВКОВ', ink)

    # sprites
    rim(cv, 170, 0)
    hub(cv, 284, 0)
    drum_strip(cv, 312, 0)
    return cv


if __name__ == '__main__':
    import sys
    build().save(sys.argv[1])
