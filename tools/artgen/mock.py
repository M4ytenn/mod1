"""Rough composites of the in-game screens, for reviewing the art outside the game (not shipped)."""
import math
import sys

import font
import gui_panel
import gui_power
from common import Canvas, rgb, mix
from palette import *

OUT = sys.argv[1]


def blit(dst, src, x, y, u, v, w, h):
    for yy in range(h):
        for xx in range(w):
            c = src.px[u + xx, v + yy]
            if c[3]:
                dst.set(x + xx, y + yy, c)


def cabinet(stage_names, lamps):
    panel = gui_panel.build()
    cv = Canvas(256, 184)
    blit(cv, panel, 0, 0, 0, 0, 256, 184)
    font.draw_text(cv, 20, 9, 'ДВЕРЬ ДЗГ-2', CREAM)
    s = 'ЗАВ. № 0417'
    font.draw_text(cv, 236 - font.text_width(s), 9, s, CREAM)
    ink = rgb('#23211d')
    font.draw_text_centered(cv, 42, 30, 'ПОРЯДОК', ink)
    font.draw_text_centered(cv, 42, 37, 'ВСКРЫТИЯ', ink)
    for i, (l1, l2) in enumerate(stage_names):
        rx, ry = 10, 50 + i * 31
        if l1 is None:
            blit(cv, panel, rx, ry, 0, 196, 64, 26)
            continue
        blit(cv, panel, rx + 1, ry + 4, lamps[i], 184, 10, 10)
        font.draw_text(cv, rx + 14, ry + 3, f'{i + 1}.', ink)
        font.draw_text(cv, rx + 22, ry + 3, l1, ink)
        font.draw_text(cv, rx + 22, ry + 10, l2, ink)
    return cv, panel


def power_mock():
    cv, panel = cabinet([('ПОДАЧА', 'ПИТАНИЯ'), ('ДАВЛЕНИЕ', 'ГИДРАВЛИКИ'), ('КЛИНОВЫЕ', 'ЗАТВОРЫ'), (None, None)], [30, 0, 0, 0])
    sheet = gui_power.build()
    ox, oy = 82, 24
    blit(cv, sheet, ox, oy, 0, 0, 166, 152)
    blit(cv, sheet, ox + 6, oy + 3, 224, 0, 28, 52)  # knife ON
    blit(cv, panel, ox + 6, oy + 57, 10, 184, 10, 10)
    blit(cv, panel, ox + 24, oy + 57, 0, 184, 10, 10)
    n = 5
    total = n * 20 - 4
    for i in range(n):
        bx = ox + 40 + (122 - total) // 2 + i * 20
        closed = i in (0, 2)
        blit(cv, sheet, bx, oy + 16, 184 if closed else 168, 60, 16, 28)
        blit(cv, panel, bx + 5, oy + 7, 56 if closed else 50, 184, 6, 6)
        blit(cv, panel, bx, oy + 46, 100, 184, 16, 7)
        font.draw_text_centered(cv, bx + 8, oy + 47, f'А{i + 1}', CREAM)
    # pencil diagram: ~380 -> А3 -> (А1 -> А5), А2 ; А4 from bus
    cx, cy = ox + 4, oy + 75
    font.draw_text(cv, cx + 4, cy + 4, '~380', PENCIL)
    nodes = {2: (0, 0), 0: (1, 0), 4: (2, 0), 1: (1, 1), 3: (0, 2)}
    parent = {2: -1, 0: 2, 4: 0, 1: 2, 3: -1}
    colw, rowh = 28, 13
    top = cy + 20
    pos = {k: (cx + 34 + d * colw, top + r * rowh + 6) for k, (d, r) in nodes.items()}
    busx = cx + 24
    cv.line(cx + 6, cy + 11, cx + 6, (pos[2][1] + pos[3][1]) // 2, PENCIL)
    cv.line(cx + 6, (pos[2][1] + pos[3][1]) // 2, busx, (pos[2][1] + pos[3][1]) // 2, PENCIL)
    cv.line(busx, pos[2][1], busx, pos[3][1], PENCIL)
    for k, (x, y) in pos.items():
        p = parent[k]
        fx, fy = (busx, y) if p < 0 else (pos[p][0] + 12, pos[p][1])
        ex = x - 5
        cv.line(fx, fy, ex, fy, PENCIL)
        cv.line(ex, fy, ex, y, PENCIL)
        cv.line(ex, y, x, y, PENCIL)
        cv.line(x, y, x + 3, y, PENCIL)
        cv.line(x + 3, y, x + 8, y - 3, PENCIL)
        cv.line(x + 8, y, x + 12, y, PENCIL)
        font.draw_text(cv, x + 1, y - 8, f'А{k + 1}', PENCIL)
    return cv


if __name__ == '__main__':
    cv = power_mock()
    bg = Canvas(276, 204, (24, 24, 28, 255))
    bg.paste(cv, 10, 10)
    bg.img.resize((276 * 3, 204 * 3), 0).save(OUT)


import gui_pump, gui_clamps, gui_wheel, gui_passport


def capsule(cv, x0, y0, x1, y1, r, base, light, dark):
    cv.capsule(x0, y0, x1, y1, r, base, light, dark)


def pump_mock():
    cv, panel = cabinet([('ПОДАЧА', 'ПИТАНИЯ'), ('ДАВЛЕНИЕ', 'ГИДРАВЛИКИ'), ('КЛИНОВЫЕ', 'ЗАТВОРЫ'), ('ШТУРВАЛ', 'РИГЕЛИ')], [20, 30, 0, 0])
    sheet = gui_pump.build()
    ox, oy = 82, 24
    blit(cv, sheet, ox, oy, 0, 0, 166, 152)
    gx, gy = ox + 50, oy + 56
    ang = lambda p: 135 + 270 * p / 16
    cv.ring(gx, gy, 27.5, 31, rgb('#2f7a3c'), ang(8.8), ang(11.3))
    a = math.radians(ang(10.1))
    cv.capsule(gx - math.cos(a) * 5, gy - math.sin(a) * 5, gx + math.cos(a) * 31, gy + math.sin(a) * 31, 0.75, rgb('#151515'), rgb('#151515'), rgb('#151515'))
    cv.line(gx + math.cos(a) * 24, gy + math.sin(a) * 24, gx + math.cos(a) * 31, gy + math.sin(a) * 31, rgb('#b0281c'))
    blit(cv, sheet, gx - 3, gy - 3, 222, 0, 7, 7)
    blit(cv, sheet, ox + 148, oy + 6, 168, 0, 12, 10)
    ra = math.radians(135 + 270 * 0.55)
    cv.line(ox + 26, oy + 122, ox + 26 + math.cos(ra) * 11, oy + 122 + math.sin(ra) * 11, rgb('#b0281c'))
    blit(cv, sheet, ox + 60, oy + 114, 168, 34, 18, 18)
    la = math.radians(180)
    px, py = ox + 140, oy + 60
    kx, ky = px + math.cos(la) * 44, py + math.sin(la) * 44
    cv.capsule(px + math.cos(la) * 9, py + math.sin(la) * 9, ox + 139, oy + 68, 1.0, STEEL[6], rgb('#d9dcdc'), STEEL[4])
    cv.capsule(px, py, kx, ky, 2.2, rgb('#6e7376'), rgb('#a4a9aa'), rgb('#3a3e40'))
    cv.disc(px, py, 3.5, rgb('#3a3e40'))
    cv.disc(px, py, 1.5, rgb('#a4a9aa'))
    blit(cv, sheet, int(kx) - 4, int(ky) - 4, 168, 24, 8, 8)
    return cv


def clamps_mock():
    cv, panel = cabinet([('ПОДАЧА', 'ПИТАНИЯ'), ('ДАВЛЕНИЕ', 'ГИДРАВЛИКИ'), ('КЛИНОВЫЕ', 'ЗАТВОРЫ'), ('ШТУРВАЛ', 'РИГЕЛИ')], [20, 20, 30, 0])
    sheet = gui_clamps.build()
    ox, oy = 82, 24
    blit(cv, sheet, ox, oy, 0, 0, 166, 152)
    slots = [(46, 22), (46, 66), (28, 11), (28, 79), (46, 44)]
    for i, (sx, sy) in enumerate(slots):
        u = 182 if i < 2 else 175 if i == 2 else 168
        blit(cv, sheet, ox + 4 + sx - 3, oy + 4 + sy - 3, u, 64, 7, 7)
        if i == 2:
            blit(cv, sheet, ox + 4 + sx - 6, oy + 4 + sy - 6, 190, 64, 13, 13)
    hub = (ox + 108, oy + 94)
    ma = math.radians(-90 + 8)
    mx, my = hub[0] + math.cos(ma) * 74, hub[1] + math.sin(ma) * 74
    blit(cv, sheet, int(mx) - 3, int(my) - 4, 190, 48, 6, 8)
    cv.ring(hub[0], hub[1], 78, 79.5, (233, 231, 223, 150), math.degrees(ma) - 360 - 3.3 + 360, math.degrees(ma) + 3.3 + 360)
    la = math.radians(-90 - 14)
    tx, ty = hub[0] + math.cos(la) * 66, hub[1] + math.sin(la) * 66
    cv.capsule(hub[0], hub[1], tx, ty, 3.0, rgb('#7e4122'), rgb('#a4602f'), rgb('#4e2814'))
    blit(cv, sheet, int(tx) - 4, int(ty) - 4, 168, 48, 8, 8)
    blit(cv, sheet, hub[0] - 7, hub[1] - 7, 176, 48, 14, 14)
    blit(cv, sheet, ox + 130, oy + 64, 168, 0, 30, 26)
    for i in range(3):
        tx = ox + 70 + i * 6
        cv.line(tx + (i % 2), oy + 118, tx, oy + 130, CHALK)
    font.draw_text(cv, ox + 70, oy + 137, 'ЗАТВОР 3/5', CHALK)
    return cv


def wheel_mock():
    cv, panel = cabinet([('ПОДАЧА', 'ПИТАНИЯ'), (None, None), ('КЛИНОВЫЕ', 'ЗАТВОРЫ'), ('ШТУРВАЛ', 'РИГЕЛИ')], [20, 0, 20, 30])
    sheet = gui_wheel.build()
    ox, oy = 82, 24
    blit(cv, sheet, ox, oy, 0, 0, 166, 152)
    cx, cy = ox + 62, oy + 80
    blit(cv, sheet, cx - 56, cy - 56, 170, 0, 112, 112)
    vis = -137
    for k in range(4):
        a = math.radians(vis + k * 90)
        cv.capsule(cx + math.cos(a) * 11, cy + math.sin(a) * 11, cx + math.cos(a) * 47, cy + math.sin(a) * 47, 3.2, rgb('#8e2a1e'), rgb('#b8452f'), rgb('#55170f'))
    a = math.radians(vis)
    cv.capsule(cx + math.cos(a) * 30, cy + math.sin(a) * 30, cx + math.cos(a) * 38, cy + math.sin(a) * 38, 1.6, rgb('#6e7376'), rgb('#a4a9aa'), rgb('#6e7376'))
    a2 = math.radians(vis + 45)
    cv.disc(cx + math.cos(a2) * 51, cy + math.sin(a2) * 51, 2.2, WHITE_PAINT)
    blit(cv, sheet, cx - 13, cy - 13, 284, 0, 26, 26)
    blit(cv, sheet, ox + 130, oy + 16, 312, 0, 7, 11)
    blit(cv, sheet, ox + 138, oy + 16, 312, 2 * 11 + 4, 7, 11)
    font.draw_text(cv, ox + 148, oy + 20, '/4', CREAM)
    sa = math.radians(135 + 270 * 0.35)
    cv.capsule(ox + 144, oy + 70, ox + 144 + math.cos(sa) * 12, oy + 70 + math.sin(sa) * 12, 0.6, rgb('#151515'), rgb('#151515'), rgb('#151515'))
    return cv


def passport_mock():
    sheet = gui_passport.build()
    cv = Canvas(232, 184)
    blit(cv, sheet, 0, 0, 0, 0, 232, 184)
    from PIL import ImageFont, ImageDraw
    ink = (38, 35, 30, 255)
    d = ImageDraw.Draw(cv.img)
    f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 8)
    fb = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 11)
    d.text((116, 9), 'ПАСПОРТ', font=fb, fill=ink, anchor='mt')
    d.text((116, 24), 'дверь защитно-герметическая', font=f, fill=ink, anchor='mt')
    for i, (l, v) in enumerate([('Изделие:', 'ДЗГ-2'), ('Заводской №:', '0417'), ('Год выпуска:', '1974'), ('Ответственный:', 'm4ytenn')]):
        d.text((20, 35 + i * 9), l, font=f, fill=ink)
        d.text((96, 35 + i * 9), v, font=f, fill=(42, 39, 64, 255))
    d.text((18, 76), '1. Порядок вскрытия', font=f, fill=ink)
    for i, (s, on) in enumerate([('Подача питания (щит)', True), ('Давление в гидросистеме', True), ('Клиновые затворы', False), ('Отвод ригелей штурвалом', True)]):
        y = 87 + i * 10
        d.rectangle([21, y - 1, 29, y + 7], outline=ink)
        if on:
            d.text((23, y - 1), 'X', font=f, fill=(42, 39, 64, 255))
        d.text((34, y - 1), s, font=f, fill=ink)
    d.text((18, 129), '2. Категория сложности:', font=f, fill=ink)
    for x, s in zip((32, 52, 76), ('I', 'II', 'III')):
        d.text((x, 139), s, font=f, fill=ink, anchor='mt')
    blit(cv, sheet, 52 - 10, 136, 0, 184, 20, 13)
    d.text((18, 150), '3. Навеска:', font=f, fill=ink)
    d.text((44, 160), 'левая', font=f, fill=ink, anchor='mt')
    d.text((104, 160), 'правая', font=f, fill=ink, anchor='mt')
    blit(cv, sheet, 44 - 22, 157, 0, 197, 44, 13)
    d.text((18, 169), 'Списать изделие', font=f, fill=(163, 41, 31, 255))
    blit(cv, sheet, 166, 124, 48, 184, 52, 52)
    d.text((222, 2), 'Заполнить при установке!', font=f, fill=(163, 41, 31, 255), anchor='rt')
    cv.px = cv.img.load()
    return cv


def save_all(prefix):
    for name, fn in (('power', power_mock), ('pump', pump_mock), ('clamps', clamps_mock), ('wheel', wheel_mock), ('passport', passport_mock)):
        cv = fn()
        bg = Canvas(cv.w + 20, cv.h + 20, (24, 24, 28, 255))
        bg.img.alpha_composite(cv.img, (10, 10))
        bg.img.resize((bg.w * 3, bg.h * 3), 0).save(f'{prefix}_{name}.png')
