"""Writes the empty test arena used by the GameTests (data/hermodoors/structure/empty.nbt)."""
import gzip
import os
import struct


def _name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag_int(name, v):
    return b'\x03' + _name(name) + struct.pack('>i', v)


def tag_string(name, v):
    return b'\x08' + _name(name) + _name(v)


def tag_list(name, elem_type, payloads):
    return b'\x09' + _name(name) + bytes([elem_type]) + struct.pack('>i', len(payloads)) + b''.join(payloads)


def compound_payload(*tags):
    return b''.join(tags) + b'\x00'


def int_payload(v):
    return struct.pack('>i', v)


def write(res_root, size=(8, 8, 8), data_version=3955):
    sx, sy, sz = size
    palette = [compound_payload(tag_string('Name', 'minecraft:smooth_stone')),
               compound_payload(tag_string('Name', 'minecraft:air'))]
    blocks = []
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                pos = tag_list('pos', 3, [int_payload(x), int_payload(y), int_payload(z)])
                blocks.append(compound_payload(pos, tag_int('state', 0 if y == 0 else 1)))
    root = b'\x0a' + _name('') + compound_payload(
        tag_int('DataVersion', data_version),
        tag_list('size', 3, [int_payload(sx), int_payload(sy), int_payload(sz)]),
        tag_list('palette', 10, palette),
        tag_list('blocks', 10, blocks),
        tag_list('entities', 10, []),
    )
    path = os.path.join(res_root, 'data', 'hermodoors', 'structure', 'empty.nbt')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # Fixed header (no file name, mtime 0) so regenerating does not touch the file.
    with open(path, 'wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as f:
        f.write(root)
