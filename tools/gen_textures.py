#!/usr/bin/env python3
"""Générateur de textures TanksCraft (PNG RGBA écrit à la main, sans dépendances).

Le layout UV utilisé ici doit rester synchronisé avec TankModels.java :
  - hull    : texOffs (0, 0)     (max 26 x 9 x 48)
  - tracks  : texOffs (0, 60)    (max  5 x 9 x 44)
  - turret  : texOffs (140, 0)   (max 16 x 8 x 18)
  - gun     : texOffs (150, 26)  (max  4 x 4 x 34)
  - muzzle  : texOffs (230, 0)   (max  6 x 6 x 5)
  - cupola  : texOffs (230, 30)  (max  6 x 5 x 6)

Projection "box UV" Minecraft pour un cube (u,v) de dimensions w(x) h(y) d(z):
  top    : (u+d,       v)   w x d
  bottom : (u+d+w,     v)   w x d
  side1  : (u,         v+d) d x h
  front  : (u+d,       v+d) w x h   (face -Z = avant du char)
  side2  : (u+d+w,     v+d) d x h
  back   : (u+2d+w,    v+d) w x h
"""
import math
import random
import struct
import zlib
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tankscraft", "textures")
TW, TH = 256, 128


# ---------------------------------------------------------------- PNG helpers
def write_png(path, w, h, get_px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)  # filter none
        for x in range(w):
            raw.extend(get_px(x, y))
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("écrit", path)


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [None] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y * self.w + x]
        return None

    def rect(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, c)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, m):
    return (clamp(c[0] * m), clamp(c[1] * m), clamp(c[2] * m), 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t),
            clamp(a[2] + (b[2] - a[2]) * t), 255)


# ---------------------------------------------------------------- box UV math
def faces(u, v, w, h, d):
    """Retourne les rects (x, y, rw, rh) de chaque face pour un cube Minecraft."""
    return {
        "top":    (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "side1":  (u, v + d, d, h),
        "front":  (u + d, v + d, w, h),
        "side2":  (u + d + w, v + d, d, h),
        "back":   (u + 2 * d + w, v + d, w, h),
    }


# camo ------------------------------------------------------------ ------------
NATIONS = {
    "france":   {"base": (107, 114, 128), "spots": [(75, 82, 96), (156, 163, 175), (60, 66, 78)], "seed": 11},
    "germany":  {"base": (113, 113, 122), "spots": [(82, 82, 91), (161, 161, 170), (52, 52, 60)], "seed": 22},
    "ussr":     {"base": (90, 106, 67),   "spots": [(63, 74, 42), (107, 122, 74), (107, 91, 62)], "seed": 33},
    "usa":      {"base": (138, 143, 106), "spots": [(110, 115, 85), (168, 173, 140), (86, 90, 66)], "seed": 44},
    "uk":       {"base": (140, 132, 116), "spots": [(110, 103, 89), (166, 158, 139), (88, 82, 70)], "seed": 55},
}

GREY = (72, 72, 78)
DARK = (38, 38, 42)
WHEEL = (94, 94, 100)
METAL = (88, 92, 98)
COPPER = (176, 122, 66)

HULL = (0, 0, 26, 9, 48)
TRACK = (0, 60, 5, 9, 44)
TURRET = (140, 0, 16, 8, 18)
GUN = (150, 26, 4, 4, 34)
MUZZLE = (230, 0, 6, 6, 5)
CUPOLA = (230, 30, 6, 5, 6)


def paint_camo_region(img, rect, pal, rng):
    """Remplit un rect avec le motif camouflage de la nation."""
    x0, y0, rw, rh = rect
    blobs = []
    for _ in range(14):
        cx = rng.uniform(x0, x0 + rw)
        cy = rng.uniform(y0, y0 + rh)
        rx, ry = rng.uniform(5, 16), rng.uniform(3, 9)
        rot = rng.uniform(0, math.pi)
        col = rng.choice(pal["spots"])
        blobs.append((cx, cy, rx, ry, rot, col))
    for y in range(y0, y0 + rh):
        for x in range(x0, x0 + rw):
            c = pal["base"]
            for (cx, cy, rx, ry, rot, col) in blobs:
                dx, dy = x - cx, y - cy
                ux = (dx * math.cos(rot) + dy * math.sin(rot)) / rx
                uy = (-dx * math.sin(rot) + dy * math.cos(rot)) / ry
                if ux * ux + uy * uy < 1.0:
                    c = col
                    break
            n = rng.uniform(0.93, 1.07)
            img.set(x, y, shade(c, n))


def paint_face_shading(img, f, mult):
    """Assombrit/éclaircit une face + liseré de tôle."""
    x, y, rw, rh = f
    for yy in range(y, y + rh):
        for xx in range(x, x + rw):
            c = img.get(xx, yy)
            if c is not None:
                img.set(xx, yy, shade(c, mult))
    # liseré (bord de plaque)
    edge = 0.72
    for xx in range(x, x + rw):
        for (xx2, yy2) in ((xx, y), (xx, y + rh - 1)):
            c = img.get(xx2, yy2)
            if c is not None:
                img.set(xx2, yy2, shade(c, edge))
    for yy in range(y, y + rh):
        for (xx2, yy2) in ((x, yy), (x + rw - 1, yy)):
            c = img.get(xx2, yy2)
            if c is not None:
                img.set(xx2, yy2, shade(c, edge))


def fill_flat(img, rect, col, jitter=0.05, rng=None):
    x0, y0, rw, rh = rect
    for y in range(y0, y0 + rh):
        for x in range(x0, x0 + rw):
            n = rng.uniform(1 - jitter, 1 + jitter) if rng else 1.0
            img.set(x, y, shade(col, n))


def draw_disc(img, cx, cy, r, col, hole_r=0):
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                if hole_r and d <= hole_r:
                    continue
                img.set(x, y, col)


def gen_tank_texture(nation):
    pal = NATIONS[nation]
    rng = random.Random(pal["seed"])
    img = Img(TW, TH)
    # fond transparent (parties non utilisées de la planche)
    for y in range(TH):
        for x in range(TW):
            img.set(x, y, (0, 0, 0, 0))

    # ---- coque
    f = faces(*HULL)
    paint_camo_region(img, (0, 0, (HULL[2] + HULL[4]) * 2, HULL[4] + HULL[3] + 2), pal, rng)
    paint_face_shading(img, f["top"], 1.18)
    paint_face_shading(img, f["front"], 0.95)
    paint_face_shading(img, f["back"], 0.82)
    paint_face_shading(img, f["side1"], 0.88)
    paint_face_shading(img, f["side2"], 0.88)
    paint_face_shading(img, f["bottom"], 0.45)
    # deck moteur : grilles arrière du toit
    tx, ty, tw_, th_ = f["top"]
    for i in range(6):
        gx = tx + tw_ - 14 + (i % 2) * 7
        gy = ty + 4 + (i // 2) * 6
        img.rect(gx, gy, 5, 4, shade(DARK, 1.0))
    # trappe conducteur à l'avant du toit
    draw_disc(img, tx + tw_ // 2, ty + 3, 2, shade(GREY, 1.1))
    # phare avant
    img.rect(f["front"][0] + f["front"][2] // 2, f["front"][1] + 1, 3, 3, (230, 220, 160, 255))
    # bas de caisse boueux
    for xx in range(f["front"][0], f["front"][0] + f["front"][2]):
        for yy in range(f["front"][1] + f["front"][3] - 2, f["front"][1] + f["front"][3]):
            c = img.get(xx, yy)
            if c is not None:
                img.set(xx, yy, mix(c, (74, 58, 40), 0.6))

    # ---- chenilles (région partagée par les deux chenilles)
    f = faces(*TRACK)
    region = (TRACK[0], TRACK[1], (TRACK[2] + TRACK[4]) * 2, TRACK[4] + TRACK[3] + 2)
    fill_flat(img, region, DARK, 0.18, rng)
    # patins de chenille sur les flancs
    for face in ("side1", "side2"):
        sx, sy, sw, sh = f[face]
        for i in range(sw // 2):
            col = shade((58, 58, 64), 1.0 if i % 2 == 0 else 0.78)
            for yy in range(sy, sy + sh):
                img.set(sx + 2 * i, yy, col)
                if 2 * i + 1 < sw:
                    img.set(sx + 2 * i + 1, yy, shade(col, 0.9))
    # roues
    for face in ("side1", "side2"):
        sx, sy, sw, sh = f[face]
        cy = sy + sh // 2 + 1
        n = max(4, sw // 9)
        for i in range(n):
            cx = sx + 3 + i * (sw - 6) / max(1, n - 1)
            draw_disc(img, cx, cy, 3.4, WHEEL, hole_r=1)
            img.set(int(cx), int(cy), DARK)
            img.set(int(cx) + 1, int(cy), shade(DARK, 1.4))
    # barbotins extrémités
    for face in ("side1", "side2"):
        sx, sy, sw, sh = f[face]
        draw_disc(img, sx + 2, sy + sh // 2 + 1, 3.6, shade(GREY, 1.05), hole_r=1)
        draw_disc(img, sx + sw - 3, sy + sh // 2 + 1, 3.6, shade(GREY, 1.05), hole_r=1)

    # ---- tourelle
    f = faces(*TURRET)
    paint_camo_region(img, (TURRET[0], TURRET[1], (TURRET[2] + TURRET[4]) * 2, TURRET[4] + TURRET[3] + 2), pal, rng)
    paint_face_shading(img, f["top"], 1.15)
    paint_face_shading(img, f["front"], 0.92)
    paint_face_shading(img, f["back"], 0.8)
    paint_face_shading(img, f["side1"], 0.85)
    paint_face_shading(img, f["side2"], 0.85)
    # trappes sur le toit
    tx, ty, tw_, th_ = f["top"]
    draw_disc(img, tx + 4, ty + th_ // 2, 2, shade(GREY, 1.15))
    img.rect(tx + tw_ - 6, ty + th_ // 2 - 1, 3, 2, DARK)

    # ---- canon
    f = faces(*GUN)
    fill_flat(img, (GUN[0], GUN[1], (GUN[2] + GUN[4]) * 2, GUN[4] + GUN[3] + 2), METAL, 0.06, rng)
    paint_face_shading(img, f["top"], 1.2)
    paint_face_shading(img, f["side1"], 0.85)
    paint_face_shading(img, f["side2"], 0.85)
    paint_face_shading(img, f["front"], 0.95)
    paint_face_shading(img, f["back"], 0.7)
    # manchon thermique
    sx, sy, sw, sh = f["side1"]
    img.rect(sx + sw - 10, sy, 8, sh, shade((60, 62, 66), 1.0))
    sx, sy, sw, sh = f["side2"]
    img.rect(sx + sw - 10, sy, 8, sh, shade((60, 62, 66), 1.0))

    # ---- frein de bouche
    f = faces(*MUZZLE)
    fill_flat(img, (MUZZLE[0], MUZZLE[1], (MUZZLE[2] + MUZZLE[4]) * 2, MUZZLE[4] + MUZZLE[3] + 2), shade(GREY, 0.9), 0.06, rng)
    paint_face_shading(img, f["side1"], 0.8)
    paint_face_shading(img, f["side2"], 0.8)

    # ---- cupule
    f = faces(*CUPOLA)
    fill_flat(img, (CUPOLA[0], CUPOLA[1], (CUPOLA[2] + CUPOLA[4]) * 2, CUPOLA[4] + CUPOLA[3] + 2), shade(pal["base"], 0.95), 0.06, rng)
    paint_face_shading(img, f["top"], 1.2)
    paint_face_shading(img, f["side1"], 0.8)
    paint_face_shading(img, f["side2"], 0.8)

    write_png(os.path.join(OUT, "entity", "tank_%s.png" % nation), TW, TH,
              lambda x, y: img.get(x, y) or (0, 0, 0, 0))


def gen_item_icon():
    W = H = 16
    img = Img(W, H)
    green = (86, 102, 64)
    green_d = (64, 78, 48)
    track_c = (44, 44, 50)
    wheel = (92, 92, 98)
    gun_c = (96, 100, 106)
    for y in range(H):
        for x in range(W):
            img.set(x, y, (0, 0, 0, 0))
    # chenilles
    img.rect(1, 11, 14, 3, track_c)
    for wx in (3, 6, 9, 12):
        img.set(wx, 12, wheel)
    # caisse
    img.rect(2, 8, 12, 3, green)
    img.rect(2, 10, 12, 1, green_d)
    # tourelle
    img.rect(4, 5, 6, 3, green)
    img.rect(4, 7, 6, 1, green_d)
    # canon
    img.rect(8, 6, 6, 1, gun_c)
    img.rect(13, 5, 1, 3, shade(gun_c, 1.2))
    # pare-boue
    img.rect(1, 8, 2, 1, green_d)
    write_png(os.path.join(OUT, "item", "tank.png"), W, H, lambda x, y: img.get(x, y) or (0, 0, 0, 0))


def gen_shell_texture():
    W, H = 32, 32
    img = Img(W, H)
    rng = random.Random(7)
    body = (168, 118, 72)
    dark = (60, 52, 40)
    tip = (210, 210, 216)
    f = faces(0, 0, 4, 4, 14)
    for name, r in f.items():
        x, y, rw, rh = r
        for yy in range(rh):
            for xx in range(rw):
                px, py = x + xx, y + yy
                c = body
                if name == "tip" or xx > rw - 3:
                    c = tip
                if yy > rh - 2:
                    c = mix(c, dark, 0.5)
                img.set(px, py, shade(c, rng.uniform(0.92, 1.08)))
    # bande cuivrée
    for name in ("side1", "side2", "front", "back"):
        x, y, rw, rh = f[name]
        xx = rw // 2
        for yy in range(rh):
            if name in ("front", "back"):
                img.set(x + xx, y + yy, COPPER)
            else:
                img.set(x + xx, y + yy, COPPER)
    write_png(os.path.join(OUT, "entity", "shell.png"), W, H, lambda x, y: img.get(x, y) or (0, 0, 0, 0))


if __name__ == "__main__":
    for nation in NATIONS:
        gen_tank_texture(nation)
    gen_item_icon()
    gen_shell_texture()
    print("Terminé.")
