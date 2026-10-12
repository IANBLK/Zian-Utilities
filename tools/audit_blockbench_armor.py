"""Compare every exported face with Blockbench's signed geometry and UV convention."""
import json,math,base64,hashlib,sys
from pathlib import Path
source=Path(sys.argv[1]);assets=Path(__file__).resolve().parents[1]/'neoforge/src/main/resources/assets/zianutilities'
def rotation(v,p,r):
 # Three.js XYZ Euler matrix (R_x R_y R_z), independently expanded.
 x,y,z=[v[i]-p[i] for i in range(3)];a,b,c=[math.radians(n) for n in r];A,B,C=math.cos(a),math.cos(b),math.cos(c);D,E,F=math.sin(a),math.sin(b),math.sin(c)
 return [B*C*x-B*F*y+E*z+p[0],(A*F+D*E*C)*x+(A*C-D*E*F)*y-D*B*z+p[1],(D*F-A*E*C)*x+(D*C+A*E*F)*y+A*B*z+p[2]]
counts={'faces':0,'invertedCubes':0,'rotatedUVs':0}
for file in source.glob('reforged_*_armor.bbmodel'):
 model=json.loads(file.read_text());name=file.stem.removesuffix('_armor');mesh=json.loads((assets/f'equipment/{name}.json').read_text());groups={g['uuid']:g for g in model['groups']};cubes={e['uuid']:e for e in model['elements']};bones=iter(mesh['bones']);texture=base64.b64decode(model['textures'][0]['source'].split(',',1)[1]);assert hashlib.sha256(texture).digest()==hashlib.sha256((assets/f'textures/models/armor/{name}_layer_1.png').read_bytes()).digest();w=model['resolution']['width'];h=model['resolution']['height']
 def walk(nodes,chain,result):
  for node in nodes:
   if isinstance(node,dict):
    g=groups[node['uuid']]
    if g.get('visibility',True) and g.get('export',True):walk(node.get('children',[]),chain+[(g['origin'],g.get('rotation',[0,0,0]))],result)
   else:
    e=cubes[node]
    if not e.get('visibility',True) or not e.get('export',True):continue
    if any(e['from'][i]>e['to'][i] for i in range(3)):counts['invertedCubes']+=1
    f=[e['from'][i]-e.get('inflate',0) for i in range(3)];t=[e['to'][i]+e.get('inflate',0) for i in range(3)]
    for i in range(3):
     if f[i]==t[i]:t[i]+=.001
    # Official setShape order: top-left, top-right, bottom-left, bottom-right.
    pts={'north':[[t[0],t[1],f[2]],[f[0],t[1],f[2]],[t[0],f[1],f[2]],[f[0],f[1],f[2]]],'south':[[f[0],t[1],t[2]],[t[0],t[1],t[2]],[f[0],f[1],t[2]],[t[0],f[1],t[2]]],'east':[[t[0],t[1],t[2]],[t[0],t[1],f[2]],[t[0],f[1],t[2]],[t[0],f[1],f[2]]],'west':[[f[0],t[1],f[2]],[f[0],t[1],t[2]],[f[0],f[1],f[2]],[f[0],f[1],t[2]]],'up':[[f[0],t[1],f[2]],[t[0],t[1],f[2]],[f[0],t[1],t[2]],[t[0],t[1],t[2]]],'down':[[f[0],f[1],t[2]],[t[0],f[1],t[2]],[f[0],f[1],f[2]],[t[0],f[1],f[2]]]}
    for direction,face in e['faces'].items():
     if face.get('texture') is None:continue
     u0,v0,u1,v1=face['uv'];uv=[[u0/w,v0/h],[u1/w,v0/h],[u0/w,v1/h],[u1/w,v1/h]]
     if face.get('rotation',0):counts['rotatedUVs']+=1
     for _ in range(int(face.get('rotation',0))//90):uv=[uv[2],uv[0],uv[3],uv[1]]
     expected=[]
     for index in [0,1,3,2]:
      p=pts[direction][index]
      for pivot,angles in reversed(chain+[(e['origin'],e.get('rotation',[0,0,0]))]):p=rotation(p,pivot,angles)
      expected.append([-p[0],24-p[1],p[2]]+uv[index])
     result.append(expected)
 for root in model['outliner']:
  g=groups[root['uuid']]
  if not g.get('visibility',True):continue
  expected=[];walk(root.get('children',[]),[(g['origin'],g.get('rotation',[0,0,0]))],expected)
  if not expected:continue
  actual=next(bones);assert len(expected)==len(actual['faces'])
  for ref,face in zip(expected,actual['faces']):
   assert all(abs(a-b)<1e-6 for v,q in zip(ref,face['world']) for a,b in zip(v,q)),(name,g['name'],'face mismatch')
   for local,world in zip(face['vertices'],face['world']):
    pivot=[-g['origin'][0],24-g['origin'][1],g['origin'][2]]
    assert all(abs(local[i]+pivot[i]-world[i])<1e-6 for i in range(3))
   counts['faces']+=1
 print('PASS',name,'original PNG + signed cubes + face UVs + nested rotations + joint-local vertices')
assert len(list(source.glob('reforged_*_armor.bbmodel')))==7
print(counts)
