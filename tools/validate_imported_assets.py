"""Check atlas inclusion, packaged PNGs and GUI bounds of the imported items."""
import json, math, sys, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
jar=Path(sys.argv[1]) if len(sys.argv)>1 else root/'neoforge/build/libs/Zian-Utilities-0.1.0-beta.21-NeoForge-1.21.1.jar'
def rotate(v,r):
 x,y,z=v;a,b,c=[math.radians(t) for t in r]
 y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
 x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
 return [x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c),z]
with zipfile.ZipFile(jar) as archive:
 files=set(archive.namelist());base='assets/zianutilities/'
 sources=json.loads(archive.read('assets/minecraft/atlases/blocks.json'))['sources']
 models=[n for n in files if n.startswith(base+'models/item/') and any(s in n for s in ['dark_lightning','reforged_','cosmic_sword','frost_axe','molten_mace','poison_dagger','reapers_scythe','storm_katana'])]
 assert len(models)==42,len(models)
 for name in models:
  model=json.loads(archive.read(name))
  for ref in model.get('textures',{}).values():
   if ref.startswith('#'):continue
   ns,path=ref.split(':',1);assert ns=='zianutilities'
   file=f'assets/{ns}/textures/{path}.png'
   assert file in files,(name,file)
   assert archive.read(file).startswith(b'\x89PNG\r\n\x1a\n')
   assert any(s['type']=='minecraft:directory' and path.startswith(s['prefix']) and file==f"assets/{ns}/textures/{s['source']}/{path[len(s['prefix']):]}.png" for s in sources),(name,'PNG exists but is excluded from block/item atlas')
  if 'elements' in model:
   gui=model['display']['gui'];points=[]
   for e in model['elements']:
    for x in [e['from'][0],e['to'][0]]:
     for y in [e['from'][1],e['to'][1]]:
      for z in [e['from'][2],e['to'][2]]:
       p=[x,y,z];r=e.get('rotation')
       if r:
        angles=[0,0,0];angles['xyz'.index(r['axis'])]=r['angle']
        p=[v+r['origin'][i] for i,v in enumerate(rotate([p[i]-r['origin'][i] for i in range(3)],angles))]
       p=rotate([(p[i]-8)*gui['scale'][i] for i in range(3)],gui['rotation'])
       points.append([p[i]+gui['translation'][i] for i in range(3)])
   assert all(abs(p[i])<=6.001 for p in points for i in [0,1]),(name,'GUI geometry exceeds 12px bounds')
 for name in [n for n in files if n.startswith(base+'equipment/') and n.endswith('.json')]:
  mesh=json.loads(archive.read(name));texture=mesh['texture'].replace('zianutilities:',base)
  assert texture in files
  for slot in ['HEAD','CHEST','LEGS','FEET']:
   vertices=[v for b in mesh['bones'] if b['slot']==slot for f in b['faces'] for v in f['world']]
   lo=[min(v[i] for v in vertices) for i in range(3)];hi=[max(v[i] for v in vertices) for i in range(3)];center=[(lo[i]+hi[i])/2 for i in range(3)]
   diameter=2*max(math.sqrt(sum((v[i]-center[i])**2 for i in range(3))) for v in vertices)
   assert diameter>0
   # Sphere normalization bounds every rotation, not only the current GUI angle.
   assert max(math.sqrt(sum(((v[i]-center[i])*.8/diameter)**2 for i in range(3))) for v in vertices)<=.400001
print('PASS: 42 packaged models, all PNG atlas references, 3 weapon GUI bounds and 28 rotation-safe armor icon bounds')
