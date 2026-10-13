"""Regenerates every texture, model and sound into src/main/resources. Run from anywhere:
    python3 tools/artgen/build_all.py
Requires Pillow, numpy and ffmpeg (with libvorbis)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources'))

import blocks
import font
import gui_clamps
import gui_panel
import gui_passport
import gui_power
import gui_pump
import gui_wheel
import lang
import models
import sounds
import structures
from palette import DOOR_GREEN, GATE_BLUE


def out(*parts):
    path = os.path.join(RES, 'assets', 'hermodoors', *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def write_font_and_sounds_json():
    import json
    rows = [row.replace('\u0000', '\u0000') for row in font.FONT_ROWS]
    font_def = {'providers': [
        {'type': 'space', 'advances': {' ': 3}},
        {'type': 'bitmap', 'file': 'hermodoors:font/stencil.png', 'ascent': 7, 'height': 8, 'chars': rows},
    ]}
    with open(out('font', 'stencil.json'), 'w', encoding='utf-8') as f:
        json.dump(font_def, f, indent=2)
    sound_defs = {
        'door.unseal': ['door/unseal'],
        'door.rumble': ['door/rumble1', 'door/rumble2'],
        'panel.hiss': ['panel/hiss'],
        'panel.arc': ['panel/arc1', 'panel/arc2'],
        'panel.hum': ['panel/hum'],
        'passport.type': ['passport/type1', 'passport/type2', 'passport/type3'],
    }
    data = {name: {'subtitle': f'subtitles.hermodoors.{name}', 'sounds': [f'hermodoors:{s}' for s in files]}
            for name, files in sound_defs.items()}
    with open(out('sounds.json'), 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2)


def main():
    gui_panel.build().save(out('textures', 'gui', 'panel.png'))
    gui_power.build().save(out('textures', 'gui', 'stage_power.png'))
    gui_pump.build().save(out('textures', 'gui', 'stage_pump.png'))
    gui_clamps.build().save(out('textures', 'gui', 'stage_clamps.png'))
    gui_wheel.build().save(out('textures', 'gui', 'stage_wheel.png'))
    gui_passport.build().save(out('textures', 'gui', 'passport.png'))
    atlas, missing = font.build_font_atlas()
    if missing:
        print('font: no glyph for', missing)
    atlas.save(out('textures', 'font', 'stencil.png'))
    blocks.frame_steel().save(out('textures', 'block', 'frame_steel.png'))
    blocks.hazard_tex().save(out('textures', 'block', 'hazard.png'))
    blocks.painted_frame(DOOR_GREEN, 681).save(out('textures', 'block', 'dzg_frame.png'))
    blocks.painted_frame(GATE_BLUE, 682).save(out('textures', 'block', 'gate_frame.png'))
    blocks.dzg_leaf().save(out('textures', 'entity', 'dzg_leaf.png'))
    blocks.gate_leaf().save(out('textures', 'entity', 'germozatvor_leaf.png'))
    blocks.dzg_icon().save(out('textures', 'item', 'dzg_door.png'))
    blocks.gate_icon().save(out('textures', 'item', 'germozatvor.png'))
    logo = blocks.logo()
    logo.img.resize((logo.w * 2, logo.h * 2), 0).save(os.path.join(RES, 'hermodoors_logo.png'))
    models.write(RES)
    lang.write(RES)
    structures.write(RES)
    write_font_and_sounds_json()
    if '--no-sounds' not in sys.argv:
        sounds.write(RES)
    print('resources written to', RES)


if __name__ == '__main__':
    main()
