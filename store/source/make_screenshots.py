"""Frames the raw 1080x2400 captures onto 1080x1920 Play Store screenshots with a caption.
Run: python3 store/source/make_screenshots.py  (needs Google Chrome; writes store/screenshots/)."""
import os, subprocess, tempfile, shutil
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FONTS = os.path.join(ROOT, "app/src/main/res/font")
OUT = os.path.join(ROOT, "store/screenshots")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
SHOTS = [  # raw file, headline, line under it, dark frame
    ("01-home.png", "Write first.", "Organise later.", False),
    ("02-note.png", "Notes that do more", "Checklists, headings, tags and links", False),
    ("03-math.png", "Math as you type", "End a line with = to get the answer", False),
    ("04-reminders.png", "Never miss a thing", "Reminders, repeats and your calendar", False),
    ("07-lisbon-dark.png", "Easy on the eyes", "Light and dark, made for each other", True),
    ("06-features.png", "42 features, one app", "Private by design. No ads, no tracking.", False),
]
CSS = """
@font-face{font-family:Inter;font-weight:400;src:url(inter_regular.ttf)}
@font-face{font-family:Inter;font-weight:600;src:url(inter_semibold.ttf)}
@font-face{font-family:Inter;font-weight:700;src:url(inter_bold.ttf)}
html,body{margin:0;width:1080px;height:1920px;overflow:hidden}
body{font-family:Inter;background:BG;color:INK;display:flex;flex-direction:column;align-items:center}
h1{font-size:84px;font-weight:700;letter-spacing:-2px;margin:118px 0 0;line-height:1.05}
p{font-size:42px;font-weight:400;color:MUTED;margin:22px 0 0}
.phone{margin-top:76px;width:720px;height:1518px;border-radius:72px;background:FRAME;padding:18px;box-sizing:border-box;
 box-shadow:0 30px 80px rgba(0,0,0,.18)}
.screen{width:100%;height:100%;border-radius:56px;overflow:hidden}
.screen img{display:block;width:100%;margin-top:-38px}
"""
tmp = tempfile.mkdtemp()
for f in os.listdir(FONTS):
    shutil.copy(os.path.join(FONTS, f), tmp)
os.makedirs(OUT, exist_ok=True)
for i, (raw, h, sub, dark) in enumerate(SHOTS, 1):
    shutil.copy(os.path.join(HERE, "raw", raw), os.path.join(tmp, raw))
    css = CSS.replace("BG", "#151413" if dark else "#f6efe4").replace("INK", "#f2efe9" if dark else "#17150f") \
        .replace("MUTED", "#ffb340" if dark else "#b25e00").replace("FRAME", "#2c2a27" if dark else "#17150f")
    html = f"<html><head><style>{css}</style></head><body><h1>{h}</h1><p>{sub}</p>" \
           f"<div class=phone><div class=screen><img src={raw}></div></div></body></html>"
    page = os.path.join(tmp, f"s{i}.html")
    open(page, "w").write(html)
    out = os.path.join(OUT, f"phone-{i}.png")
    subprocess.run([CHROME, "--headless", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--screenshot={out}", "--window-size=1080,1920", "file://" + page], check=True, capture_output=True)
    print(out)

# Feature graphic, 1024x500.
shutil.copy(os.path.join(ROOT, "docs/icon-512.png"), os.path.join(tmp, "icon.png"))
for raw in ("01-home.png", "07-lisbon-dark.png"):
    shutil.copy(os.path.join(HERE, "raw", raw), os.path.join(tmp, raw))
fg = """<html><head><style>""" + CSS.split("html,body")[0] + """
html,body{margin:0;width:1024px;height:500px;overflow:hidden;background:#f6efe4;font-family:Inter;color:#17150f}
.l{position:absolute;left:64px;top:118px;width:470px}
.l img{width:96px;height:96px;border-radius:22px;box-shadow:0 8px 24px rgba(0,0,0,.12)}
h1{font-size:76px;font-weight:700;letter-spacing:-2px;margin:22px 0 0;line-height:1}
p{font-size:30px;color:#b25e00;margin:14px 0 0;font-weight:600}
.ph{position:absolute;width:300px;border-radius:40px;background:#17150f;padding:10px;box-shadow:0 20px 50px rgba(0,0,0,.22)}
.ph div{border-radius:31px;overflow:hidden}.ph img{display:block;width:100%;margin-top:-16px}
</style></head><body>
<div class=l><img src=icon.png><h1>Margin</h1><p>Write first. Organise later.</p></div>
<div class=ph style="left:560px;top:120px;transform:rotate(-6deg);background:#2c2a27"><div><img src=07-lisbon-dark.png></div></div>
<div class=ph style="left:700px;top:60px;transform:rotate(4deg)"><div><img src=01-home.png></div></div>
</body></html>"""
page = os.path.join(tmp, "fg.html"); open(page, "w").write(fg)
out = os.path.join(ROOT, "store/feature-graphic-1024x500.png")
subprocess.run([CHROME, "--headless", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                f"--screenshot={out}", "--window-size=1024,500", "file://" + page], check=True, capture_output=True)
print(out)
