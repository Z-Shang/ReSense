#!/usr/bin/env python3
"""Recover the stock Weather app's full-scene movies, posters and Fusion scene."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


def extract(rom, output, previews=False):
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    report={"source":str(rom),"assets":[]}
    def save(name,data,source):
        target=output/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        entry={"file":name,"source":source,"bytes":len(data),"sha256":hashlib.sha256(data).hexdigest()}
        report["assets"].append(entry)
        return target,entry
    with zipfile.ZipFile(rom) as archive:
        for name in sorted(archive.namelist()):
            if name.startswith("system/media/weather/") and name.endswith(".mp4"):
                target,entry=save("videos/"+Path(name).name,archive.read(name),name)
                if shutil.which("ffprobe"):
                    entry["media"]=json.loads(subprocess.check_output([
                        "ffprobe","-v","error","-show_streams","-show_format","-of","json",str(target)],text=True))
                if previews:
                    preview=output/"previews"/(target.stem+".jpg");preview.parent.mkdir(exist_ok=True)
                    subprocess.run(["ffmpeg","-v","error","-y","-ss","2","-i",str(target),
                        "-frames:v","1","-vf","scale=180:-1",str(preview)],check=True)
        for package in ("WeatherVideo","Weather"):
            source=f"system/app/{package}.apk"
            with zipfile.ZipFile(io.BytesIO(archive.read(source))) as apk:
                for name in apk.namelist():
                    if (name.startswith("assets/") and name.endswith((".m10",".r10"))) or (
                            package=="WeatherVideo" and name.startswith("res/drawable/") and name.endswith(".jpg")):
                        save(package+"/"+name,apk.read(name),source+"!/"+name)
    (output/"provenance.json").write_text(json.dumps(report,indent=2)+"\n")
    if previews:
        from PIL import Image,ImageDraw
        frames=sorted((output/"previews").glob("*.jpg"));sheet=Image.new("RGB",(6*200,((len(frames)+5)//6)*220),"#161b20")
        draw=ImageDraw.Draw(sheet)
        for i,path in enumerate(frames):
            with Image.open(path) as im:
                im.thumbnail((190,190));x=(i%6)*200;y=(i//6)*220
                sheet.paste(im,(x+(200-im.width)//2,y));draw.text((x+4,y+195),path.stem.removeprefix("weather_"),fill="white")
        sheet.save(output/"contact-sheet.jpg")
    print(f"Recovered {len(report['assets'])} original assets to {output}")


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rom",default="res/3.33.401.153.zip")
    parser.add_argument("--output",default="references/3.33.401.153/weather-app-animation")
    parser.add_argument("--previews",action="store_true",help="Requires ffmpeg and Pillow")
    args=parser.parse_args();extract(args.rom,args.output,args.previews)
