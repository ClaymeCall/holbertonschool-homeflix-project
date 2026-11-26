#!/usr/bin/env python3
"""Render the HomeFlix video animations (scenes.html) to 1080p30 MP4 clips.

Usage:
  python3 render.py all out/               render every scene into out/
  python3 render.py arch out/arch.mp4      render one scene
  python3 render.py arch prev 6,15,40      save JPEG previews at those seconds into out/

Needs: python3, playwright (pip), Google Chrome or Chromium, ffmpeg, the Inter font.
Set CHROME_PATH if Chrome is not at /usr/bin/google-chrome.
"""
import os, subprocess, sys
from pathlib import Path
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
HTML = (HERE / "scenes.html").as_uri()
SCENES = ["intro", "value", "divider", "design", "stack", "arch", "db", "stream", "agent", "outro"]
FPS = 30

def open_scene(p, scene):
    exe = os.environ.get("CHROME_PATH", "/usr/bin/google-chrome")
    kw = {"executable_path": exe} if os.path.exists(exe) else {}
    b = p.chromium.launch(args=["--no-sandbox", "--force-device-scale-factor=1"], **kw)
    pg = b.new_page(viewport={"width": 1920, "height": 1080})
    pg.goto(HTML); pg.evaluate("document.fonts.ready"); pg.wait_for_timeout(300)
    dur = pg.evaluate(f"load('{scene}')"); pg.wait_for_timeout(150)
    return b, pg, dur

def preview(scene, times, outdir):
    outdir.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        b, pg, _ = open_scene(p, scene)
        for t in times:
            pg.evaluate(f"renderAt({t})"); pg.wait_for_timeout(30)
            pg.screenshot(path=str(outdir / f"{scene}_{t:05.1f}.jpg"), type="jpeg", quality=90)
        b.close()

def render(scene, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp.mp4")
    with sync_playwright() as p:
        b, pg, dur = open_scene(p, scene)
        ff = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(FPS),
            "-c:v", "mjpeg", "-i", "-", "-c:v", "libx264", "-crf", "18", "-preset", "veryfast",
            "-pix_fmt", "yuv420p", "-r", str(FPS), "-movflags", "+faststart", str(tmp)], stdin=subprocess.PIPE)
        prev = None; data = None
        for i in range(int(round(dur * FPS))):
            key = pg.evaluate(f"renderAt({i / FPS})")
            if key != prev:                      # only screenshot when the picture changed
                data = pg.screenshot(type="jpeg", quality=92); prev = key
            ff.stdin.write(data)
        ff.stdin.close(); rc = ff.wait(); b.close()
    if rc != 0:
        sys.exit(f"{scene}: ffmpeg failed ({rc})")
    tmp.rename(out); print(scene, "ok", f"{dur}s")

if __name__ == "__main__":
    if len(sys.argv) < 3: sys.exit(__doc__)
    scene, arg = sys.argv[1], sys.argv[2]
    if scene == "all":
        for s in SCENES: render(s, Path(arg) / f"{s}.mp4")
    elif arg == "prev":
        preview(scene, [float(x) for x in sys.argv[3].split(",")], Path("out"))
    else:
        render(scene, Path(arg))
