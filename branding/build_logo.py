"""Rebuild B1 Aurora SVG and Android vector/adaptive icon resources. No third-party Python packages."""
from pathlib import Path
import math
import subprocess

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'branding'/'aurora'
RES=ROOT/'app'/'src'/'main'/'res'
OUT.mkdir(parents=True,exist_ok=True)
COLORS=['#00AFC8','#3767E8','#7544D8']
BG='#EDF5FF'

def bar(left,y,right=77.5,height=8.4,shear=4.7,radius=1.0):
    points=[(left+shear,y),(right,y),(right-shear,y+height),(left,y+height)]
    corners=[]
    for i,p in enumerate(points):
        def toward(q):
            length=math.dist(p,q)
            return tuple(p[j]+(q[j]-p[j])*min(radius,length/3)/length for j in range(2))
        corners.append((toward(points[i-1]),p,toward(points[(i+1)%4])))
    def xy(p):return f'{p[0]:.3f},{p[1]:.3f}'
    parts=['M'+xy(corners[0][0])]
    for i,(entry,p,exit) in enumerate(corners):
        if i:parts.append('L'+xy(entry))
        parts.append('Q'+xy(p)+' '+xy(exit))
    return ' '.join(parts)+' Z'

PATHS=[bar(53.7,36.5),bar(41.5,49.1),bar(29.5,61.9)]
def paths(colors):return ''.join(f'<path fill="{c}" d="{p}"/>' for p,c in zip(PATHS,colors))
def svg(content,size=108):return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108">{content}</svg>\n'
def save_svg(name,content):
    (OUT/name).write_text(svg(content))

save_svg('logo-color.svg',paths(COLORS))
save_svg('logo-monochrome.svg',paths(['#000000']*3))
save_svg('icon-square.svg',f'<rect width="108" height="108" fill="{BG}"/>'+paths(COLORS))
save_svg('icon-circle.svg',f'<circle cx="54" cy="54" r="54" fill="{BG}"/>'+paths(COLORS))

def vector(colors,group=''):
    body='\n'.join(f'    <path android:fillColor="{c}" android:pathData="{p}" />' for p,c in zip(PATHS,colors))
    if group:body=f'    <group {group}>\n{body}\n    </group>'
    return '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'+body+'\n</vector>\n'

(RES/'drawable'/'ic_launcher_foreground.xml').write_text(vector(COLORS))
(RES/'drawable'/'ic_launcher_monochrome.xml').write_text(vector(['#FFFFFF']*3))
notification=vector(['#FFFFFF']*3,'android:scaleX="1.8" android:scaleY="1.8" android:pivotX="54" android:pivotY="54"').replace('android:width="108dp" android:height="108dp"','android:width="24dp" android:height="24dp"')
(RES/'drawable'/'ic_rootless.xml').write_text(notification)
(RES/'values'/'icon_colors.xml').write_text(f'<resources><color name="launcher_background">{BG}</color></resources>\n')
for folder,mono in [('mipmap-anydpi-v26',False),('mipmap-anydpi-v33',True)]:
    target=RES/folder;target.mkdir(exist_ok=True)
    body='<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/launcher_background" />\n    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
    if mono:body+='    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
    (target/'ic_launcher.xml').write_text(body+'</adaptive-icon>\n')

# Editable presentation with multiple launcher masks and themed previews.
parts=['<rect width="1200" height="900" fill="#F8FAFD"/>', '<text x="70" y="82" font-family="sans-serif" font-size="34" fill="#18263D">Xperia Clear All</text>', '<text x="70" y="120" font-family="sans-serif" font-size="18" fill="#66758B">B1 Aurora · Final vector artwork</text>']
def icon(x,y,size,shape,inks,bg):
    parts.append(f'<g transform="translate({x},{y}) scale({size/108})">')
    parts.append(f'<circle cx="54" cy="54" r="54" fill="{bg}"/>' if shape=='circle' else f'<rect width="108" height="108" rx="24" fill="{bg}"/>')
    parts.append(paths(inks));parts.append('</g>')
icon(80,190,320,'circle',COLORS,BG)
icon(450,230,230,'square',COLORS,BG)
icon(760,230,230,'circle',['#18263D']*3,BG)
icon(760,560,230,'circle',['#DCEBFF']*3,'#1D2636')
parts += ['<text x="90" y="555" font-family="sans-serif" font-size="20" fill="#34445E">Color · circular mask</text>','<text x="450" y="500" font-family="sans-serif" font-size="20" fill="#34445E">Color · rounded mask</text>','<text x="760" y="500" font-family="sans-serif" font-size="20" fill="#34445E">Monochrome · light</text>','<text x="760" y="830" font-family="sans-serif" font-size="20" fill="#34445E">Monochrome · dark</text>','<text x="80" y="650" font-family="sans-serif" font-size="20" fill="#34445E">Small-size silhouette</text>']
for i,size in enumerate([72,48,32]):icon(80+i*130,690,size,'circle',COLORS,BG)
(OUT/'preview.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="900" viewBox="0 0 1200 900">'+''.join(parts)+'</svg>\n')

if __name__=='__main__':
    for source,width in [('logo-color.svg',1024),('logo-monochrome.svg',1024),('icon-square.svg',1024),('icon-circle.svg',1024),('preview.svg',1600)]:
        subprocess.run(['inkscape',str(OUT/source),'--export-type=png',f'--export-width={width}',f'--export-filename={OUT/source.replace(".svg",".png")}'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run(['inkscape',str(OUT/'icon-square.svg'),'--export-type=png','--export-width=512',f'--export-filename={OUT/"store-icon-512.png"}'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    print('B1 Aurora vector resources and PNG exports generated.')
