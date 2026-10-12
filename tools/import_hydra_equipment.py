"""Convert source geometry into self-contained 1.21.1 assets; never copy plugin shaders."""
from pathlib import Path
import zipfile,json,base64,math,shutil,hashlib
ROOT=Path(__file__).resolve().parents[1]
SOURCES=ROOT.parent/'imported-packs-2026-10-12'
ASSETS=ROOT/'neoforge/src/main/resources/assets/zianutilities'
def put(rel,raw):
 p=ASSETS/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
def js(rel,value):put(rel,json.dumps(value,ensure_ascii=False,separators=(',',':')).encode('utf-8'))
def rotate(v,p,r):
 x,y,z=[v[i]-p[i] for i in range(3)]
 a,b,c=[math.radians(float(t)) for t in r]
 x,y=x*math.cos(c)-y*math.sin(c),x*math.sin(c)+y*math.cos(c)
 x,z=x*math.cos(b)+z*math.sin(b),-x*math.sin(b)+z*math.cos(b)
 y,z=y*math.cos(a)-z*math.sin(a),y*math.sin(a)+z*math.cos(a)
 return [x+p[0],y+p[1],z+p[2]]
def transform(v,chain,normal=False):
 for p,r in reversed(chain):v=rotate(v,[0,0,0] if normal else p,r)
 return [-v[0],-v[1] if normal else 24-v[1],v[2]]
MAPPING={'helmet':('HEAD','head'),'head':('HEAD','head'),'torso':('CHEST','body'),'right_arm':('CHEST','right_arm'),'left_arm':('CHEST','left_arm'),'hips':('LEGS','body'),'right_leg':('LEGS','right_leg'),'left_leg':('LEGS','left_leg'),'right_boot':('FEET','right_leg'),'left_boot':('FEET','left_leg')}
NORMALS={'north':[0,0,-1],'south':[0,0,1],'east':[1,0,0],'west':[-1,0,0],'up':[0,1,0],'down':[0,-1,0]}
def convert(model,name):
 groups={g['uuid']:g for g in model['groups']};cubes={c['uuid']:c for c in model['elements']};bones=[]
 texture=model['textures'][0];raw=base64.b64decode(texture['source'].split(',',1)[1],validate=True)
 assert raw[:8]==b'\x89PNG\r\n\x1a\n'
 tw=int.from_bytes(raw[16:20],'big');th=int.from_bytes(raw[20:24],'big')
 for layer in [1,2]:put(f'textures/models/armor/{name}_layer_{layer}.png',raw)
 def walk(nodes,chain,faces):
  for node in nodes:
   if isinstance(node,dict):
    g=groups[node['uuid']]
    if g.get('visibility',True) and g.get('export',True):walk(node.get('children',[]),chain+[(g.get('origin',[0,0,0]),g.get('rotation',[0,0,0]))],faces)
   else:
    e=cubes[node]
    if not e.get('visibility',True) or not e.get('export',True):continue
    if e.get('type','cube')!='cube':raise ValueError('Unsupported armor element')
    inflate=e.get('inflate',0);stretch=e.get('stretch',[1,1,1]);center=[(e['from'][i]+e['to'][i])/2 for i in range(3)];half=[((e['to'][i]-e['from'][i])/2+inflate)*stretch[i] for i in range(3)];lo=[center[i]-half[i] for i in range(3)];hi=[center[i]+half[i] for i in range(3)]
    for i in range(3):
     if lo[i]==hi[i]:hi[i]+=.001
    x0,y0,z0=lo;x1,y1,z1=hi
    # Source shells are thinner than extruded player skin layers. Keep the
    # original mesh for inventory previews, enlarge only worn base shells.
    clearance=max(0,.75-inflate) if e.get('box_uv',False) and inflate>0 and all(e['to'][i]>e['from'][i] for i in range(3)) else 0
    points={'north':[(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],'south':[(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],'east':[(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],'west':[(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],'up':[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],'down':[(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)]}
    ec=chain+[(e.get('origin',[0,0,0]),e.get('rotation',[0,0,0]))]
    for direction,f in e.get('faces',{}).items():
     if f.get('texture') is None:continue
     assert f['texture']==0
     u0,v0,u1,v1=f['uv'];uv=[[u0/tw,v0/th],[u1/tw,v0/th],[u1/tw,v1/th],[u0/tw,v1/th]]
     turns=int(f.get('rotation',0))//90;uv=uv[-turns:]+uv[:-turns] if turns else uv
     world=[transform(list(v),ec) for v in points[direction]]
     a=[world[1][i]-world[0][i] for i in range(3)];b=[world[2][i]-world[0][i] for i in range(3)];normal=[a[2]*b[1]-a[1]*b[2],a[0]*b[2]-a[2]*b[0],a[1]*b[0]-a[0]*b[1]];length=math.sqrt(sum(v*v for v in normal));normal=[v/length for v in normal]
     vertices=[[round(world[i][j]-pivot[j],7) for j in range(3)]+uv[i] for i in range(4)]
     face={'vertices':vertices,'world':[world[i]+uv[i] for i in range(4)],'normal':normal}
     if clearance:
      worn=[transform([v[i]+(clearance if v[i]>center[i] else -clearance) for i in range(3)],ec) for v in points[direction]]
      face['wornVertices']=[[round(worn[i][j]-pivot[j],7) for j in range(3)]+uv[i] for i in range(4)]
     faces.append(face)
 for root in model['outliner']:
  g=groups[root['uuid']];key=g['name']
  if key not in MAPPING:raise ValueError('Unmapped armor root '+key)
  if not g.get('visibility',True):continue
  slot,part=MAPPING[key];pivot=transform(g['origin'],[]);faces=[];walk(root.get('children',[]),[(g.get('origin',[0,0,0]),g.get('rotation',[0,0,0]))],faces)
  if faces:bones.append({'slot':slot,'part':part,'faces':faces})
 assert all(any(b['slot']==slot for b in bones) for slot in ['HEAD','CHEST','LEGS','FEET'])
 for b in bones:
  for f in b['faces']:
   assert all(math.isfinite(v) for row in f['vertices'] for v in row)
   assert all(-.001<=uv<=1.001 for row in f['vertices'] for uv in row[3:])
 js(f'equipment/{name}.json',{'schema':1,'texture':f'zianutilities:textures/models/armor/{name}_layer_1.png','bones':bones})
 for kind in ['helmet','chestplate','leggings','boots']:
  js(f'models/item/{name}_{kind}.json',{'parent':'minecraft:builtin/entity','textures':{'particle':f'zianutilities:models/armor/{name}_layer_1'},'display':{'gui':{'rotation':[15,-35,0],'scale':[.9,.9,.9]},'ground':{'scale':[.5,.5,.5]}}})
 return {'model':name,'bones':len(bones),'faces':sum(len(b['faces']) for b in bones),'textureSHA256':hashlib.sha256(raw).hexdigest()}
report=[]
def fit_weapon_gui(model):
 points=[]
 for element in model['elements']:
  for x in [element['from'][0],element['to'][0]]:
   for y in [element['from'][1],element['to'][1]]:
    for z in [element['from'][2],element['to'][2]]:
     point=[x,y,z];rotation=element.get('rotation')
     if rotation:
      angles=[0,0,0];angles['xyz'.index(rotation['axis'])]=rotation['angle']
      point=rotate(point,rotation['origin'],angles)
     points.append(rotate([point[i]-8 for i in range(3)],[0,0,0],[-180,0,135]))
 low=[min(p[i] for p in points) for i in range(3)];high=[max(p[i] for p in points) for i in range(3)]
 scale=12/max(high[0]-low[0],high[1]-low[1])
 model.setdefault('display',{})['gui']={'rotation':[-180,0,135],'translation':[-(low[i]+high[i])*scale/2 if i<2 else 0 for i in range(3)],'scale':[scale]*3}
 return model
# Reforged models retired. Registry aliases remain for lossless save migration.
for material in ['leather','chainmail','copper','golden','iron','diamond','netherite']:
 for kind in ['helmet','chestplate','leggings','boots']:
  name='reforged_'+material+'_'+kind;vanilla='netherite' if material=='netherite' else 'diamond'
  js(f'models/item/{name}.json',{'parent':f'minecraft:item/{vanilla}_{kind}'})
with zipfile.ZipFile(SOURCES/'Dark lightning free.zip') as z:
 prefix='ItemsAdder/contents/dark_lightning/resourcepack/assets/dark_lightning/'
 for entry in z.namelist():
  if entry.startswith(prefix+'textures/') and entry.endswith('.png'):put(entry[len(prefix):].replace('textures/','textures/imported/dark_lightning/',1),z.read(entry))
 for kind in ['axe','pickaxe','sword']:
  folder='weapons' if kind=='sword' else 'tools';model=json.loads(z.read(prefix+f'models/{folder}/dark_lightning_{kind}.json'))
  text=json.dumps(model).replace('dark_lightning:','zianutilities:imported/dark_lightning/')
  js(f'models/item/dark_lightning_{kind}.json',fit_weapon_gui(json.loads(text)))
 for kind in ['helmet','chestplate','leggings','boots','book']:
  js(f'models/item/dark_lightning_{kind}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'zianutilities:imported/dark_lightning/items/dark_lightning_{kind}'}})
 for layer in [1,2]:put(f'textures/models/armor/dark_lightning_layer_{layer}.png',z.read(prefix+f'textures/armor/dark_lightning_armor_layer_{layer}.png'))
with zipfile.ZipFile(SOURCES/'Weapons Pack - Free Edition.zip') as z:
 for name in ['cosmic_sword','frost_axe','molten_mace','poison_dagger','reapers_scythe','storm_katana']:
  put(f'textures/imported/weapons/{name}.png',z.read(f'Files/{name}.png'))
  js(f'models/item/{name}.json',{'parent':'minecraft:item/handheld','textures':{'layer0':f'zianutilities:imported/weapons/{name}'}})
(ROOT/'artifacts').mkdir(exist_ok=True)
(ROOT/'artifacts/imported-equipment-validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
