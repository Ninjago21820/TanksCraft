#!/usr/bin/env python3
"""Normalise les images GUI générées :
- hangar.png : conservé en RGB (dimensions réelles), ré-encodé proprement
- logo.png   : recadré sur le contenu (fond noir), mis à l'échelle sur un canvas 1024x256
"""
import struct
import zlib
import os

GUI = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tankscraft", "textures", "gui")


# ---------------------------------------------------------------- décodage PNG
def read_png(path):
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    i = 8
    w = h = bd = ct = None
    idat = bytearray()
    while i < len(data):
        ln = struct.unpack(">I", data[i:i + 4])[0]
        tag = data[i + 4:i + 8]
        payload = data[i + 8:i + 8 + ln]
        if tag == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", payload[:10])
        elif tag == b"IDAT":
            idat.extend(payload)
        i += 12 + ln
    assert bd == 8, "profondeur non supportée"
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    raw = zlib.decompress(bytes(idat))
    stride = w * channels
    rows = []
    prev = bytearray(stride)
    pos = 0
    for y in range(h):
        f = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if f == 0:
            pass
        elif f == 1:  # sub
            for x in range(channels, stride):
                line[x] = (line[x] + line[x - channels]) & 0xFF
        elif f == 2:  # up
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 0xFF
        elif f == 3:  # average
            for x in range(stride):
                a = line[x - channels] if x >= channels else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 0xFF
        elif f == 4:  # paeth
            for x in range(stride):
                a = line[x - channels] if x >= channels else 0
                b = prev[x]
                c = prev[x - channels] if x >= channels else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pred) & 0xFF
        else:
            raise ValueError("filtre inconnu %d" % f)
        rows.append(bytes(line))
        prev = line
    return w, h, channels, rows


def write_png(path, w, h, channels, rows):
    raw = bytearray()
    for line in rows:
        raw.append(0)
        raw.extend(line)
    def chunk(tag, payload):
        c = struct.pack(">I", len(payload)) + tag + payload
        return c + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF)
    ct = {1: 0, 3: 2, 4: 6}[channels]
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, ct, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    print("écrit", path, w, "x", h, len(png), "octets")


# ---------------------------------------------------------------- hangar
def process_hangar():
    path = os.path.join(GUI, "hangar.png")
    w, h, ch, rows = read_png(path)
    write_png(path, w, h, ch, rows)


# ---------------------------------------------------------------- logo
def process_logo():
    path = os.path.join(GUI, "logo.png")
    w, h, ch, rows = read_png(path)
    assert ch == 3

    def px(x, y):
        o = x * 3
        return rows[y][o], rows[y][o + 1], rows[y][o + 2]

    # bbox du contenu non noir
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        line = rows[y]
        for x in range(w):
            o = x * 3
            if line[o] > 24 or line[o + 1] > 24 or line[o + 2] > 24:
                if x < minx: minx = x
                if x > maxx: maxx = x
                if y < miny: miny = y
                if y > maxy: maxy = y
    if maxx < 0:
        raise SystemExit("logo vide !")
    cw, chh = maxx - minx + 1, maxy - miny + 1
    print("contenu du logo : %dx%d à (%d,%d)" % (cw, chh, minx, miny))

    # échelle nearest vers hauteur 224 (marge noire) et largeur max 980
    target_h = 224
    scale = target_h / chh
    target_w = int(cw * scale)
    if target_w > 980:
        scale = 980 / cw
        target_w, target_h = 980, int(chh * scale)
    out = []
    for y in range(256):
        sy = (y - (256 - target_h) // 2) / scale
        row = bytearray()
        for x in range(1024):
            sx = (x - (1024 - target_w) // 2) / scale
            if 0 <= sy < chh and 0 <= sx < cw:
                r, g2, b = px(minx + int(sx), miny + int(sy))
                row.extend((r, g2, b))
            else:
                row.extend((0, 0, 0))
        out.append(bytes(row))
    write_png(path, 1024, 256, 3, out)


if __name__ == "__main__":
    process_hangar()
    process_logo()
