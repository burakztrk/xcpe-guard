import sys
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
BG = (11, 14, 19, 255)
CARD = (21, 26, 34, 255)
GREEN = (129, 201, 149, 255)
WHITE = (241, 243, 244, 255)
FONT = "/System/Library/Fonts/Avenir Next.ttc"

def bez(p0, p1, p2, p3, n=40):
    pts = []
    for i in range(1, n + 1):
        t = i / n
        u = 1 - t
        x = u**3*p0[0] + 3*u*u*t*p1[0] + 3*u*t*t*p2[0] + t**3*p3[0]
        y = u**3*p0[1] + 3*u*u*t*p1[1] + 3*u*t*t*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

OUTER = [(12, 1), (3, 5), (3, 11)] + bez((3, 11), (3, 16.55), (6.84, 21.74), (12, 23)) \
    + bez((12, 23), (17.16, 21.74), (21, 16.55), (21, 11)) + [(21, 5)]
CHECK = [(10, 17), (6, 13), (7.41, 11.59), (10, 14.17), (16.59, 7.58), (18, 9)]

def shield(d, x, y, h, fill, hole):
    s = h / 22.0
    tr = lambda pts: [(x + (px - 3) * s, y + (py - 1) * s) for px, py in pts]
    d.polygon(tr(OUTER), fill=fill)
    d.polygon(tr(CHECK), fill=hole)

def font(size, idx):
    return ImageFont.truetype(FONT, size, index=idx)

def render(w, h, scale, draw_fn, name):
    big = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(big), w * scale, h * scale, scale)
    big.resize((w, h), Image.LANCZOS).save(f"{OUT}/{name}")

def layout(d, W, H, shield_h, text_h, max_w, hole):
    sw = shield_h * 18 / 22
    gap = shield_h * 0.16
    size = int(text_h * 0.62)
    while True:
        f = font(size, 2)
        tw = max(d.textlength("XCPE", font=f), d.textlength("GUARD", font=f))
        if sw + gap + tw <= max_w or size < 8:
            break
        size -= 1
    asc, desc = f.getmetrics()
    total = sw + gap + tw
    x0 = (W - total) / 2
    shield(d, x0, (H - shield_h) / 2, shield_h, GREEN, hole)
    tx = x0 + sw + gap
    line = asc
    top = H / 2 - line
    d.text((tx, top), "XCPE", font=f, fill=WHITE)
    d.text((tx, top + line * 0.98), "GUARD", font=f, fill=GREEN)

def banner(d, W, H, s):
    d.rectangle([0, 0, W, H], fill=BG)
    layout(d, W, H, H * 0.5, H * 0.5, W * 0.72, BG)

def square(d, W, H, s):
    d.rounded_rectangle([0, 0, W, H], radius=int(W * 0.18), fill=CARD)
    layout(d, W, H, H * 0.40, H * 0.40, W * 0.86, CARD)

render(640, 360, 4, banner, "banner_xhdpi.png")
render(1280, 720, 2, banner, "banner_readme.png")
for px, name in [(48, "mdpi"), (72, "hdpi"), (96, "xhdpi"), (144, "xxhdpi"), (192, "xxxhdpi")]:
    render(px, px, 8, square, f"ic_launcher_{name}.png")
render(512, 512, 4, square, "icon_512.png")
print("ok")
