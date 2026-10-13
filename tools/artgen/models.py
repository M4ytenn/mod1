"""Blockstates and part models. Frame boxes mirror DoorGeometry.frameBoxes() in the Java code."""
import json
import os

TYPES = {
    'dzg_door': dict(width=2, height=3, sliding=False, jamb=3, head=3, sill=2, front='hermodoors:block/dzg_frame'),
    'germozatvor': dict(width=4, height=4, sliding=True, jamb=4, head=6, sill=1, front='hermodoors:block/gate_frame'),
}
FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def frame_boxes(t):
    w, h = t['width'] * 16, t['height'] * 16
    j, hd, s = t['jamb'], t['head'], t['sill']
    if t['sliding']:
        lz0, lz1 = 5, 11
        return [
            ((0, 0, 0, j, h - hd, lz0), 'hazard'), ((0, 0, lz1, j, h - hd, 16), 'side'),
            ((w - j, 0, 0, w, h - hd, lz0), 'hazard'), ((w - j, 0, lz1, w, h - hd, 16), 'side'),
            ((0, h - hd, 0, w, h, 16), 'front'),
            ((j, 0, 0, w - j, s, 16), 'side'),
        ]
    return [
        ((0, 0, 0, j, h, 16), 'front'), ((w - j, 0, 0, w, h, 16), 'front'),
        ((j, h - hd, 0, w - j, h, 16), 'front'),
        ((j, 0, 0, w - j, s, 16), 'side'),
    ]


def part_model(t, px, py):
    elements = []
    cx, cy = px * 16, py * 16
    for (x0, y0, z0, x1, y1, z1), front_tex in frame_boxes(t):
        ax0, ax1 = max(x0, cx) - cx, min(x1, cx + 16) - cx
        ay0, ay1 = max(y0, cy) - cy, min(y1, cy + 16) - cy
        if ax1 <= ax0 or ay1 <= ay0:
            continue
        # authored facing north: door-local x runs west (x' = 16 - x), depth runs south
        frm = [16 - ax1, ay0, z0]
        to = [16 - ax0, ay1, z1]
        faces = {}
        for face in ('north', 'south', 'east', 'west', 'up', 'down'):
            tex = '#side'
            if face == 'north':
                tex = '#' + front_tex
            faces[face] = {'texture': tex}
        elements.append({'from': frm, 'to': to, 'faces': faces})
    textures = {'particle': 'hermodoors:block/frame_steel', 'side': 'hermodoors:block/frame_steel',
                'front': t['front']}
    if t['sliding']:
        textures['hazard'] = 'hermodoors:block/hazard'
    return {'textures': textures, 'elements': elements}


def write(res_root):
    for name, t in TYPES.items():
        variants = {}
        for facing, rot in FACING_Y.items():
            for opened in ('false', 'true'):
                for px in range(t['width']):
                    for py in range(t['height']):
                        key = f'facing={facing},open={opened},px={px},py={py}'
                        v = {'model': f'hermodoors:block/{name}_{px}_{py}'}
                        if rot:
                            v['y'] = rot
                        variants[key] = v
        path = os.path.join(res_root, 'assets/hermodoors/blockstates', f'{name}.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            json.dump({'variants': variants}, f, indent=2)
        for px in range(t['width']):
            for py in range(t['height']):
                path = os.path.join(res_root, 'assets/hermodoors/models/block', f'{name}_{px}_{py}.json')
                os.makedirs(os.path.dirname(path), exist_ok=True)
                with open(path, 'w') as f:
                    json.dump(part_model(t, px, py), f, indent=2)
        path = os.path.join(res_root, 'assets/hermodoors/models/item', f'{name}.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'hermodoors:item/{name}'}}, f, indent=2)
