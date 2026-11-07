"""Validate actual generated block assets. Only Python's standard library required."""
import itertools,json,math,re,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ASSETS=RES/'assets/cuupsidedown'
MOUNTS=('up','down','north','south','east','west')
IDS=('sakura_wall','dark_wall','zen_wall','moon_wall','blossom_floor','dark_floor','lantern','temple_stairs','tea_table','flower_planter')
geometry=json.loads((ROOT/'tools/furniture_geometry.json').read_text())
vanilla=zipfile.ZipFile(ROOT/'build/moddev/artifacts/neoforge-21.1.249-client-extra-aka-minecraft-resources.jar')

def resource(location,kind,suffix):
    namespace,path=location.split(':',1)
    rel=f'assets/{namespace}/{kind}/{path}.{suffix}'
    assert (RES/rel).exists() or rel in vanilla.namelist(), f'Missing asset: {rel}'

def point(p,m):
    x,y,z=p
    return {'up':(x,y,z),'down':(x,16-y,16-z),'north':(x,z,16-y),'south':(x,16-z,y),
            'east':(y,16-x,z),'west':(16-y,x,z)}[m]

count=0
for path in (ASSETS/'models').rglob('*.json'):
    model=json.loads(path.read_text())
    if 'parent' in model:resource(model['parent'],'models','json')
    for texture in model.get('textures',{}).values():
        if not texture.startswith('#'):resource(texture,'textures','png')
    for element in model.get('elements',[]):
        for a,b in zip(element['from'],element['to']):assert 0<=a<b<=16,(path,a,b)
        assert set(element['faces'])==set(MOUNTS),path
    count+=1

for name in IDS:
    state=json.loads((ASSETS/'blockstates'/f'{name}.json').read_text())
    for value in state['variants'].values():
        for variant in value if isinstance(value,list) else [value]:resource(variant['model'],'models','json')
    resource(f'cuupsidedown:item/{name}','models','json')
    loot=json.loads((RES/'data/cuupsidedown/loot_table/blocks'/f'{name}.json').read_text())
    assert loot['pools']==[],name

for name in ('tea_table','flower_planter'):
    state=json.loads((ASSETS/'blockstates'/f'{name}.json').read_text())
    assert set(state['variants'])=={f'mount={m}' for m in MOUNTS}
    for mount in MOUNTS:
        model=json.loads((ASSETS/'models/block'/f'{name}_{mount}.json').read_text())
        assert len(model['elements'])==len(geometry[name])
        for base,element in zip(geometry[name],model['elements']):
            # Check all 8 corners, not merely the two used by the generator.
            corners=[point(p,mount) for p in itertools.product(*zip(base['from'],base['to']))]
            low=[min(c[a] for c in corners) for a in range(3)]
            high=[max(c[a] for c in corners) for a in range(3)]
            assert element['from']==low and element['to']==high,(name,mount)

stairs=json.loads((ASSETS/'blockstates/temple_stairs.json').read_text())['variants']
assert len(stairs)==40
for facing,half,shape in itertools.product(('east','north','south','west'),('bottom','top'),('straight','inner_left','inner_right','outer_left','outer_right')):
    assert f'facing={facing},half={half},shape={shape}' in stairs
tag=json.loads((RES/'data/cuupsidedown/tags/block/dungeon_blocks.json').read_text())
assert set(tag['values'])=={f'cuupsidedown:{name}' for name in IDS}
print(f'PASS: {count} model references/textures; 10 block states/items/empty loot tables; six furniture orientations; 40 stair variants; dungeon block tag.')
