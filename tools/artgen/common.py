"""Small pixel-art toolkit used by the texture generators. Everything is drawn at 1:1 pixel scale."""
import math
import random

from PIL import Image


def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, k):
    """Darken (k < 0) or lighten (k > 0) with a slight hue shift: shadows go cool, lights go warm."""
    r, g, b, a = c
    if k >= 0:
        r = r + (255 - r) * k * 1.05
        g = g + (255 - g) * k
        b = b + (255 - b) * k * 0.8
    else:
        k = -k
        r = r * (1 - k * 1.08)
        g = g * (1 - k)
        b = b * (1 - k * 0.85)
    return (max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))), a)


class ValueNoise:
    """Smooth value noise with octaves, deterministic per seed."""

    def __init__(self, seed, cell=8):
        self.seed = seed
        self.cell = cell
        self.cache = {}

    def _rnd(self, ix, iy, octave):
        key = (ix, iy, octave)
        if key not in self.cache:
            r = random.Random((ix * 73856093) ^ (iy * 19349663) ^ (octave * 83492791) ^ self.seed)
            self.cache[key] = r.random()
        return self.cache[key]

    def _octave(self, x, y, cell, octave):
        fx, fy = x / cell, y / cell
        ix, iy = math.floor(fx), math.floor(fy)
        tx, ty = fx - ix, fy - iy
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        a = self._rnd(ix, iy, octave)
        b = self._rnd(ix + 1, iy, octave)
        c = self._rnd(ix, iy + 1, octave)
        d = self._rnd(ix + 1, iy + 1, octave)
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty

    def __call__(self, x, y, octaves=3):
        total, amp, norm, cell = 0.0, 1.0, 0.0, self.cell
        for o in range(octaves):
            total += self._octave(x, y, cell, o) * amp
            norm += amp
            amp *= 0.5
            cell = max(1, cell / 2)
        return total / norm


BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.img = Image.new('RGBA', (w, h), fill)
        self.px = self.img.load()

    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, c):
        x, y = int(x), int(y)
        if self.inb(x, y):
            if c[3] == 255:
                self.px[x, y] = c
            elif c[3] > 0:
                base = self.px[x, y]
                t = c[3] / 255
                if base[3] == 0:
                    self.px[x, y] = c
                else:
                    self.px[x, y] = (int(base[0] + (c[0] - base[0]) * t), int(base[1] + (c[1] - base[1]) * t),
                                     int(base[2] + (c[2] - base[2]) * t), max(base[3], c[3]))

    def get(self, x, y):
        if self.inb(x, y):
            return self.px[int(x), int(y)]
        return (0, 0, 0, 0)

    def rect(self, x0, y0, x1, y1, c):
        """Filled rectangle, x1/y1 exclusive."""
        for y in range(int(y0), int(y1)):
            for x in range(int(x0), int(x1)):
                self.set(x, y, c)

    def outline(self, x0, y0, x1, y1, c):
        for x in range(x0, x1):
            self.set(x, y0, c)
            self.set(x, y1 - 1, c)
        for y in range(y0, y1):
            self.set(x0, y, c)
            self.set(x1 - 1, y, c)

    def bevel(self, x0, y0, x1, y1, light, dark, width=1):
        for i in range(width):
            for x in range(x0 + i, x1 - i):
                self.set(x, y0 + i, light)
                self.set(x, y1 - 1 - i, dark)
            for y in range(y0 + i, y1 - i):
                self.set(x0 + i, y, light)
                self.set(x1 - 1 - i, y, dark)

    def line(self, x0, y0, x1, y1, c):
        x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                return
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r, c):
        self.ring(cx, cy, 0, r, c)

    def ring(self, cx, cy, r0, r1, c, a0=None, a1=None):
        for y in range(int(math.floor(cy - r1)) - 1, int(math.ceil(cy + r1)) + 1):
            for x in range(int(math.floor(cx - r1)) - 1, int(math.ceil(cx + r1)) + 1):
                px, py = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(px, py)
                if r0 <= d <= r1:
                    if a0 is not None:
                        a = math.degrees(math.atan2(py, px)) % 360
                        lo, hi = a0 % 360, a1 % 360
                        inside = lo <= a <= hi if lo <= hi else (a >= lo or a <= hi)
                        if not inside:
                            continue
                    self.set(x, y, c(x, y, d, px, py) if callable(c) else c)

    def capsule(self, x0, y0, x1, y1, r, base, light=None, dark=None):
        light = light or base
        dark = dark or base
        dx, dy = x1 - x0, y1 - y0
        l2 = dx * dx + dy * dy
        ln = math.sqrt(l2) or 1
        nx, ny = -dy / ln, dx / ln
        if nx + ny > 0:
            nx, ny = -nx, -ny
        for y in range(int(min(y0, y1) - r - 2), int(max(y0, y1) + r + 2)):
            for x in range(int(min(x0, x1) - r - 2), int(max(x0, x1) + r + 2)):
                px, py = x + 0.5, y + 0.5
                t = 0 if l2 == 0 else max(0, min(1, ((px - x0) * dx + (py - y0) * dy) / l2))
                cx, cy = x0 + dx * t, y0 + dy * t
                ox, oy = px - cx, py - cy
                d = math.hypot(ox, oy)
                if d <= r:
                    col = base
                    if d > r - 1.1:
                        col = light if ox * nx + oy * ny > 0 else dark
                    self.set(x, y, col)

    def fill_noise(self, x0, y0, x1, y1, ramp, seed, cell=6, octaves=3, bias=0.0, contrast=1.0, dither=True):
        """Fill with value noise quantised onto a colour ramp (dark to light), ordered-dithered between steps."""
        n = ValueNoise(seed, cell)
        steps = len(ramp) - 1
        for y in range(y0, y1):
            for x in range(x0, x1):
                v = (n(x, y, octaves) - 0.5) * contrast + 0.5 + bias
                v = max(0.0, min(0.999, v)) * steps
                i = int(v)
                f = v - i
                if dither and f * 16 > BAYER4[y % 4][x % 4] + 0.5:
                    i = min(steps, i + 1)
                elif not dither and f > 0.5:
                    i = min(steps, i + 1)
                self.set(x, y, ramp[i])

    def paste(self, other, x, y):
        for yy in range(other.h):
            for xx in range(other.w):
                c = other.px[xx, yy]
                if c[3]:
                    self.set(x + xx, y + yy, c)

    def save(self, path):
        self.img.save(path)


def speckle(cv, x0, y0, x1, y1, color, density, seed, mask=None):
    r = random.Random(seed)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if r.random() < density and (mask is None or mask(x, y)):
                cv.set(x, y, color)


SCREW_SLOTS = [((1, 2), (2, 2), (3, 2)), ((2, 1), (2, 2), (2, 3)), ((1, 1), (2, 2), (3, 3)), ((3, 1), (2, 2), (1, 3))]


def screw(cv, x, y, seed, size=5, metal=None):
    """Slotted screw head (5 x 5, or 3 x 3), slot in one of four orientations."""
    metal = metal or rgb('#8d9193')
    r = random.Random(seed)
    light, dark, slot = shade(metal, 0.35), shade(metal, -0.4), shade(metal, -0.72)
    if size >= 5:
        shape = [".###.", "#####", "#####", "#####", ".###."]
        for yy, row in enumerate(shape):
            for xx, ch in enumerate(row):
                if ch == '#':
                    d = (xx - 2) + (yy - 2)
                    cv.set(x + xx, y + yy, light if d <= -2 else (dark if d >= 2 else metal))
        for (sx, sy) in r.choice(SCREW_SLOTS):
            cv.set(x + sx, y + sy, slot)
    else:
        for yy in range(3):
            for xx in range(3):
                if (xx, yy) not in ((0, 0), (2, 0), (0, 2), (2, 2)):
                    cv.set(x + xx, y + yy, light if xx + yy < 2 else (dark if xx + yy > 2 else metal))
        cv.set(x + 1, y + 1, slot)


def rivet(cv, x, y, metal):
    """3x3 domed rivet with a highlight."""
    cv.set(x + 1, y, shade(metal, 0.2))
    cv.set(x, y + 1, shade(metal, 0.2))
    cv.set(x + 1, y + 1, shade(metal, 0.45))
    cv.set(x + 2, y + 1, shade(metal, -0.3))
    cv.set(x + 1, y + 2, shade(metal, -0.35))
    cv.set(x, y + 2, shade(metal, -0.15))
    cv.set(x + 2, y + 2, shade(metal, -0.45))
    cv.set(x + 2, y, metal)
    cv.set(x, y, metal)
