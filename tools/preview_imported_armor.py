from pathlib import Path
import json,math
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1]/'neoforge/src/main/resources/assets/zianutilities'
names=['leather','chainmail','copper','golden','iron','diamond','netherite'];canvas=Image.new('RGB',(1050,260),(19,23,29));draw=ImageDraw.Draw(canvas)
for index,name in enumerate(names):
 data=json.loads((root/f'equipment/reforged_{name}.json').read_text());texture=Image.open(root/f'textures/models/armor/reforged_{name}_layer_1.png').convert('RGBA');pix=texture.load();tw,th=texture.size
 faces=[f for b in data['bones'] for f in b['faces']];angle=math.radians(-22);elev=math.radians(9)
 def project(v):
  x,y,z=v[:3];side=x*math.cos(angle)+z*math.sin(angle);depth=-x*math.sin(angle)+z*math.cos(angle);return(side,-y*math.cos(elev)-depth*math.sin(elev),depth*math.cos(elev)-y*math.sin(elev))
 pts=[project(v) for f in faces for v in f['world']];lo=[min(v[i] for v in pts)for i in range(2)];hi=[max(v[i] for v in pts)for i in range(2)];scale=min(128/(hi[0]-lo[0]),215/(hi[1]-lo[1]));cx=index*150+75;cy=133;zb={}
 for f in faces:
  verts=[]
  for v in f['world']:
   x,y,z=project(v);verts.append((cx+(x-(lo[0]+hi[0])/2)*scale,cy-(y-(lo[1]+hi[1])/2)*scale,z,v[3],v[4]))
  for ids in [(0,1,2),(0,2,3)]:
   a,b,c=[verts[i]for i in ids];den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
   if abs(den)<1e-6:continue
   for y in range(max(20,int(min(a[1],b[1],c[1]))),min(248,int(max(a[1],b[1],c[1]))+1)):
    for x in range(max(index*150,int(min(a[0],b[0],c[0]))),min((index+1)*150-1,int(max(a[0],b[0],c[0]))+1)):
     w0=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/den;w1=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/den;w2=1-w0-w1
     if min(w0,w1,w2)<-.00001:continue
     z=w0*a[2]+w1*b[2]+w2*c[2]
     if (x,y)in zb and z>=zb[x,y]:continue
     u=w0*a[3]+w1*b[3]+w2*c[3];v=w0*a[4]+w1*b[4]+w2*c[4];rgba=pix[max(0,min(tw-1,int(u*tw))),max(0,min(th-1,int(v*th)))]
     if rgba[3]<25:continue
     shade=.65+.35*max(0,-.3*f['normal'][0]-.65*f['normal'][1]-.6*f['normal'][2]);canvas.putpixel((x,y),tuple(int(q*shade)for q in rgba[:3]));zb[x,y]=z
 draw.text((index*150+8,5),name,fill=(242,203,104))
out=Path(__file__).resolve().parents[1]/'artifacts/reforged-conversion-preview.png';canvas.save(out);print(out)
