"""Prism Client's GUI icons, drawn here (original line art, white on transparent, tinted in game).
Each icon is drawn at 256 px and scaled down to 32 px; a .mcmeta turns on smooth filtering so they stay clean at any
GUI scale.  Run from the project root:  python tools-icons.py"""
import json
import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src', 'client', 'resources', 'assets', 'prismclient', 'textures', 'gui', 'icons')
S = 256          # drawing size
W = 18           # stroke width at drawing size (about 2.3 px at 32 px)
WHITE = (255, 255, 255, 255)


def canvas():
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def line(d, pts, w=W):
    d.line(pts, fill=WHITE, width=w, joint='curve')
    for x, y in (pts[0], pts[-1]):
        d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=WHITE)


def ring(d, cx, cy, r, w=W):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=WHITE, width=w)


def rrect(d, box, r, w=W, fill=False):
    if fill:
        d.rounded_rectangle(box, r, fill=WHITE)
    else:
        d.rounded_rectangle(box, r, outline=WHITE, width=w)


def arc_pts(cx, cy, rx, ry, a0, a1, n=40):
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


def eye():
    im, d = canvas()
    line(d, arc_pts(128, 128, 104, 70, 180, 360), W)
    line(d, arc_pts(128, 128, 104, 70, 0, 180), W)
    ring(d, 128, 128, 32)
    d.ellipse((116, 116, 140, 140), fill=WHITE)
    return im


def globe():
    im, d = canvas()
    ring(d, 128, 128, 100)
    d.ellipse((128 - 44, 28, 128 + 44, 228), outline=WHITE, width=W)
    line(d, [(32, 128), (224, 128)])
    for y in (82, 174):
        half = math.sqrt(100 ** 2 - (y - 128) ** 2) - 10
        line(d, [(128 - half, y), (128 + half, y)])
    return im

def apps():
    im, d = canvas()
    for x, y in ((36, 36), (140, 36), (36, 140), (140, 140)):
        rrect(d, (x, y, x + 80, y + 80), 20)
    return im


def swords():
    """Combat: a crosshair (reads better than swords at 32 px)."""
    im, d = canvas()
    ring(d, 128, 128, 74)
    for a, b in (((128, 20), (128, 80)), ((128, 176), (128, 236)), ((20, 128), (80, 128)), ((176, 128), (236, 128))):
        line(d, [a, b])
    d.ellipse((116, 116, 140, 140), fill=WHITE)
    return im

def monitor():
    im, d = canvas()
    rrect(d, (28, 40, 228, 176), 22)
    line(d, [(128, 176), (128, 214)])
    line(d, [(84, 218), (172, 218)])
    return im


def search():
    im, d = canvas()
    ring(d, 108, 108, 68)
    line(d, [(160, 160), (218, 218)], W + 4)
    return im


def gear():
    im, d = canvas()
    cx = cy = 128
    for i in range(8):
        a = math.radians(i * 45)
        line(d, [(cx + 74 * math.cos(a), cy + 74 * math.sin(a)), (cx + 100 * math.cos(a), cy + 100 * math.sin(a))], 34)
    ring(d, cx, cy, 70, 26)
    ring(d, cx, cy, 26, 20)
    return im

def sliders():
    im, d = canvas()
    for y, kx in ((60, 90), (128, 166), (196, 110)):
        line(d, [(36, y), (220, y)])
        d.ellipse((kx - 22, y - 22, kx + 22, y + 22), fill=WHITE)
    return im


def user():
    im, d = canvas()
    ring(d, 128, 86, 46)
    line(d, arc_pts(128, 226, 86, 80, 180, 360, 40))
    return im


def layout():
    im, d = canvas()
    rrect(d, (28, 36, 228, 220), 22)
    line(d, [(28, 92), (228, 92)])
    line(d, [(100, 92), (100, 220)])
    return im


def plus():
    im, d = canvas()
    d.ellipse((24, 24, 232, 232), fill=WHITE)
    d.rounded_rectangle((118, 70, 138, 186), 8, fill=(0, 0, 0, 0))
    d.rounded_rectangle((70, 118, 186, 138), 8, fill=(0, 0, 0, 0))
    return im


def folder():
    im, d = canvas()
    d.line([(40, 70), (40, 200), (216, 200), (216, 92), (128, 92), (106, 62), (52, 62)], fill=WHITE, width=W, joint='curve')
    d.line([(52, 62), (40, 70)], fill=WHITE, width=W)
    return im


def arrow():
    """Pointer: a navigation arrow pointing up (rotated in game)."""
    im, d = canvas()
    d.polygon([(128, 24), (216, 220), (128, 176), (40, 220)], fill=WHITE)
    return im


def pin():
    im, d = canvas()
    d.ellipse((60, 20, 196, 156), fill=WHITE)
    d.polygon([(70, 120), (186, 120), (128, 238)], fill=WHITE)
    d.ellipse((104, 64, 152, 112), fill=(0, 0, 0, 0))
    return im


def check():
    im, d = canvas()
    ring(d, 128, 128, 100)
    line(d, [(80, 132), (114, 166), (180, 96)], W + 2)
    return im


def warning():
    im, d = canvas()
    d.line([(128, 30), (230, 214), (26, 214), (128, 30)], fill=WHITE, width=W, joint='curve')
    line(d, [(128, 96), (128, 150)])
    d.ellipse((116, 172, 140, 196), fill=WHITE)
    return im


def cube():
    im, d = canvas()
    top, left, right, bottom, mid = (128, 28), (36, 80), (220, 80), (128, 232), (128, 132)
    d.line([top, right, (220, 184), bottom, (36, 184), left, top], fill=WHITE, width=W, joint='curve')
    line(d, [left, mid, right])
    line(d, [mid, bottom])
    return im


def gauge():
    """FPS: a speedometer dial with a needle."""
    im, d = canvas()
    line(d, arc_pts(128, 150, 100, 100, 180, 360, 40))
    line(d, [(128, 150), (186, 96)], W + 2)
    d.ellipse((112, 134, 144, 166), fill=WHITE)
    return im


def signal():
    """Ping: four rising bars."""
    im, d = canvas()
    for i, hgt in enumerate((50, 95, 140, 185)):
        x = 34 + i * 52
        d.rounded_rectangle((x, 220 - hgt, x + 34, 220), 8, fill=WHITE)
    return im


def speed():
    """Speed: two chevrons pointing right."""
    im, d = canvas()
    for ox in (40, 120):
        line(d, [(ox, 56), (ox + 80, 128), (ox, 200)], W + 6)
    return im


ICONS = {'render': eye, 'world': globe, 'misc': apps, 'combat': swords, 'client': monitor, 'search': search, 'gear': gear,
         'profiles': sliders, 'friends': user, 'hud': layout, 'plus': plus, 'folder': folder,
         'arrow': arrow, 'pin': pin, 'check': check, 'warning': warning, 'cube': cube, 'gauge': gauge, 'signal': signal, 'speed': speed}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in ICONS.items():
        big = fn()
        small = big.resize((32, 32), Image.LANCZOS)
        small.save(os.path.join(OUT, name + '.png'))
        with open(os.path.join(OUT, name + '.png.mcmeta'), 'w') as f:
            json.dump({'texture': {'blur': True, 'clamp': True}}, f)
    # a preview sheet for checking by eye
    sheet = Image.new('RGBA', (len(ICONS) * 40 + 8, 48), (20, 20, 20, 255))
    for i, name in enumerate(ICONS):
        sheet.alpha_composite(Image.open(os.path.join(OUT, name + '.png')), (8 + i * 40, 8))
    print(len(ICONS), 'icons ->', OUT)


if __name__ == '__main__':
    main()
