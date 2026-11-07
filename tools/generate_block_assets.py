"""Deterministic original pixel textures, cuboid art, and matching collision geometry.

Run from any directory with Python + Pillow. Generated assets are committed; Pillow
is not a runtime or build dependency. No existing Minecraft texture is edited.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import itertools, json, math, random, zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/cuupsidedown'
JAVA = ROOT / 'src/main/java/dev/unyielding/upsidedown/dungeon/block'
MOUNTS = ('up', 'down', 'north', 'south', 'east', 'west')
FACES = ('down', 'up', 'north', 'south', 'west', 'east')
NORMALS = {'up': (0,1,0), 'down': (0,-1,0), 'north': (0,0,-1),
           'south': (0,0,1), 'east': (1,0,0), 'west': (-1,0,0)}

def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

def new_texture(color, seed=5, noise=3):
    image = Image.new('RGB', (32,32), color)
    rng = random.Random(seed)
    for x,y in itertools.product(range(32), repeat=2):
        n = rng.randint(-noise,noise)
        image.putpixel((x,y), tuple(max(0,min(255,c+n)) for c in color))
    return image, ImageDraw.Draw(image)

def flower(d,x,y,colors,scale=1):
    outer, inner, center = colors
    for dx,dy in [(0,-2),(-2,-1),(2,-1),(-1,2),(1,2)]:
        d.rectangle((x+dx*scale-scale,y+dy*scale-scale,x+dx*scale+scale,y+dy*scale+scale), fill=outer)
        d.point((x+dx*scale,y+dy*scale),fill=inner)
    d.rectangle((x,y,x+scale-1,y+scale-1),fill=center)

def texture(name,image):
    path=ASSETS/'textures/block'/f'{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

def panel(name, base, timber, gold, kind):
    im,d=new_texture(base)
    d.rectangle((0,0,31,31),outline=timber,width=2)
    d.line((1,7,30,7),fill=timber,width=2)
    for x in [8,23]: d.line((x,2,x,29),fill=timber)
    for y in [14,23]:d.line((2,y,29,y),fill=tuple(max(0,c-15) for c in base))
    for x in [2,27]:
        for y in [2,27]:d.rectangle((x,y,x+2,y+2),fill=gold)
    if kind=='sakura':
        d.line([(7,29),(12,23),(14,18),(21,12)],fill=(96,54,62),width=2)
        d.line([(13,21),(20,22)],fill=(96,54,62))
        for x,y in [(13,18),(21,12),(20,22)]:flower(d,x,y,('#cc6092','#ffe5ed','#d1a550'))
    elif kind=='bamboo':
        for x in [12,20]:
            d.line((x,10,x,29),fill=(81,117,87),width=2)
            for y in [14,20,26]:
                d.line((x-1,y,x+1,y),fill=gold)
                d.line((x,y-1,x+4,y-4),fill=(112,148,100),width=2)
    elif kind=='dark':
        for x in [12,20]:
            d.line((x,10,x,29),fill=gold)
            for y in [14,22]:
                d.line((x,y,x+4,y-3),fill=(116,98,139))
        d.rectangle((11,26,21,28),outline=gold)
    else:
        d.ellipse((11,10,23,22),fill=gold)
        d.ellipse((15,8,25,20),fill=base)
        for x,y in [(12,26),(19,28),(6,20)]:
            d.line((x-3,y,x+3,y),fill=(100,126,177))
            d.line((x-1,y-1,x+2,y-1),fill=(100,126,177))
    texture(name,im)

panel('sakura_wall',(240,207,212),(90,44,70),(214,172,89),'sakura')
panel('dark_wall',(43,39,61),(22,23,35),(173,139,78),'dark')
panel('zen_wall',(222,233,209),(55,86,78),(197,166,101),'bamboo')
panel('moon_wall',(35,48,79),(24,27,48),(231,199,117),'moon')

im,d=new_texture((196,123,145),7)
for y in [0,8,16,24]:
    d.line((0,y,31,y),fill=(127,73,97));d.line((0,y+1,31,y+1),fill=(225,157,172))
    d.line((7+(y//8%2)*12,y,7+(y//8%2)*12,y+7),fill=(127,73,97))
for x,y in [(5,5),(26,21)]:flower(d,x,y,('#e8afbf','#f9d8de','#ba944d'))
texture('blossom_floor',im)
im,d=new_texture((41,43,56),8,4)
d.rectangle((0,0,31,31),outline=(22,24,34));d.rectangle((2,2,29,29),outline=(103,87,67))
d.line([(16,5),(27,16),(16,27),(5,16),(16,5)],fill=(169,134,77))
d.rectangle((14,14,18,18),outline=(121,101,119))
texture('dark_floor',im)
im,d=new_texture((86,42,62),17,5)
for y in [3,9,15,21,27]:
    d.line((0,y,31,y),fill=(113,57,74));d.line((3,y+1,24,y+1),fill=(56,29,45))
texture('lacquer',im)
im,d=new_texture((203,164,82),19,5)
for y in range(3,32,7):d.line((0,y,31,y),fill=(231,198,118))
texture('brass',im)
im,d=new_texture((231,234,215),20,2)
d.rectangle((1,1,30,30),outline=(90,145,151),width=2)
for x,y in [(8,8),(23,23)]:flower(d,x,y,('#d695b0','#efd4da','#c6aa6d'))
texture('porcelain',im)
im,d=new_texture((78,49,32),21,7);texture('soil',im)
im,d=new_texture((75,92,41),22,4)
for x in range(2,32,7):d.line((x,0,x+4,31),fill=(110,129,65))
texture('leaf',im)
im,d=new_texture((232,134,179),23,5)
for x,y in [(7,7),(24,23)]:flower(d,x,y,('#ce639b','#ffd0e6','#f7cb77'),2)
texture('petal',im)
im,d=new_texture((255,225,161),24,2)
for x in [0,8,23,31]:d.line((x,0,x,31),fill=(114,65,58),width=2)
for y in [0,8,23,31]:d.line((0,y,31,y),fill=(114,65,58),width=2)
flower(d,16,16,('#eaa28c','#ffeac2','#bd805f'))
texture('shoji_light',im)
im,d=new_texture((77,54,37),25,3);d.ellipse((5,5,26,26),fill=(117,77,38));texture('tea',im)

def cuboid(a,b,material): return {'from':list(a),'to':list(b),'material':material}

TABLE=[cuboid((1,6,1),(15,8,15),'lacquer'),
       cuboid((.75,7,.75),(15.25,7.5,1),'brass'),
       cuboid((.75,7,15),(15.25,7.5,15.25),'brass'),
       cuboid((.75,7,1),(1,7.5,15),'brass'),
       cuboid((15,7,1),(15.25,7.5,15),'brass')]
for x,z in [(2,2),(11.5,2),(2,11.5),(11.5,11.5)]:
    TABLE.append(cuboid((x,0,z),(x+2.5,6,z+2.5),'lacquer'))
TABLE += [cuboid((2,8,2),(14,8.25,14),'porcelain'),
          cuboid((7,8.25,6),(11,11.25,10),'porcelain'),
          cuboid((7.5,11.25,6.5),(10.5,12,9.5),'brass'),
          cuboid((8.5,12,7.5),(9.5,12.75,8.5),'lacquer'),
          cuboid((11,9,7),(13,10,9),'porcelain'),
          cuboid((5.5,9,6.5),(7,10.5,7.5),'brass'),
          cuboid((5.5,9,8.5),(7,10.5,9.5),'brass'),
          cuboid((5,9,7.5),(6,10.5,8.5),'brass')]
for x,z in [(3,3),(3,10),(10,11)]:
    TABLE.extend([cuboid((x-.5,8.25,z-.5),(x+2.5,8.5,z+2.5),'brass'),
                  cuboid((x,8.5,z),(x+2,10,z+2),'porcelain'),
                  cuboid((x+.25,10,z+.25),(x+1.75,10.125,z+1.75),'tea')])
PLANTER=[cuboid((4,0,4),(12,1,12),'brass'),cuboid((3,1,3),(13,6,13),'porcelain'),
         cuboid((2.5,5.5,2.5),(13.5,6.5,13.5),'brass'),cuboid((3.5,6.5,3.5),(12.5,6.75,12.5),'soil'),
         cuboid((7.5,6.75,7.5),(8.5,13,8.5),'lacquer'),
         cuboid((4.5,10,7.5),(8,11,8.5),'lacquer'),
         cuboid((8,11,7.5),(11.5,12,8.5),'lacquer'),
         cuboid((4,10.5,6.5),(6,11,9.5),'leaf'),
         cuboid((10,11.5,6.5),(13,12,9.5),'leaf')]
for x,y,z in [(4.5,12,8),(8,14,8),(11,13,8)]:
    PLANTER += [cuboid((x-2,y-.5,z-1),(x+2,y+.5,z+1),'petal'),
                cuboid((x-1,y-1.5,z-1),(x+1,y+1.5,z+1),'petal'),
                cuboid((x-.75,y-.75,z-1.25),(x+.75,y+.75,z+1.25),'brass')]
LANTERN=[cuboid((3,0,3),(13,2,13),'lacquer'),cuboid((4,2,4),(12,14,12),'shoji_light'),
         cuboid((2,14,2),(14,15,14),'brass'),cuboid((3,15,3),(13,16,13),'lacquer')]
for x,z in [(3.5,3.5),(11.5,3.5),(3.5,11.5),(11.5,11.5)]:
    LANTERN.append(cuboid((x,2,z),(x+1,14,z+1),'lacquer'))

def point(p,mount):
    x,y,z=p
    return {'up':(x,y,z),'down':(x,16-y,16-z),'north':(x,z,16-y),
            'south':(x,16-z,y),'east':(y,16-x,z),'west':(16-y,x,z)}[mount]

def transformed(box,mount):
    a,b=point(box['from'],mount),point(box['to'],mount)
    return [min(a[i],b[i]) for i in range(3)],[max(a[i],b[i]) for i in range(3)]

def face_map(face,mount):
    a=point((8,8,8),mount)
    n=NORMALS[face]
    b=point(tuple(8+k for k in n),mount)
    normal=tuple(b[i]-a[i] for i in range(3))
    return next(k for k,v in NORMALS.items() if v==normal)

def geometry_model(geometry,mount='up'):
    textures={x['material']:f'cuupsidedown:block/{x["material"]}' for x in geometry}
    textures['particle']=textures[geometry[0]['material']]
    elements=[]
    for box in geometry:
        low,high=transformed(box,mount)
        elements.append({'from':low,'to':high,'faces':{
            face_map(face,mount):{'uv':[0,0,16,16],'texture':'#'+box['material']} for face in FACES}})
    return {'parent':'minecraft:block/block','ambientocclusion':False,
            'textures':textures,'elements':elements}

fulls=['sakura_wall','dark_wall','zen_wall','moon_wall','blossom_floor','dark_floor']
for name in fulls:
    tex=f'cuupsidedown:block/{name}'
    model={'parent':'minecraft:block/cube','textures':{'particle':tex,
           'north':tex,'south':tex,'east':tex,'west':tex,'up':tex,'down':'cuupsidedown:block/lacquer'}}
    if name.endswith('_wall'):model['textures']['up']='cuupsidedown:block/lacquer'
    else:
        for side in ('north','south','east','west'):model['textures'][side]='cuupsidedown:block/lacquer'
    save_json(ASSETS/'models/block'/f'{name}.json',model)
    save_json(ASSETS/'blockstates'/f'{name}.json',{'variants':{'':{'model':f'cuupsidedown:block/{name}'}}})

for name,geometry in [('tea_table',TABLE),('flower_planter',PLANTER)]:
    for mount in MOUNTS:save_json(ASSETS/'models/block'/f'{name}_{mount}.json',geometry_model(geometry,mount))
    save_json(ASSETS/'blockstates'/f'{name}.json',{'variants':{f'mount={m}':{'model':f'cuupsidedown:block/{name}_{m}'} for m in MOUNTS}})
save_json(ASSETS/'models/block/lantern.json',geometry_model(LANTERN))
save_json(ASSETS/'blockstates/lantern.json',{'variants':{'':{'model':'cuupsidedown:block/lantern'}}})

# Vanilla's complete straight/inner/outer stair model mapping, with original textures.
extra=ROOT/'build/moddev/artifacts/neoforge-21.1.249-client-extra-aka-minecraft-resources.jar'
with zipfile.ZipFile(extra) as archive:
    state=json.loads(archive.read('assets/minecraft/blockstates/polished_deepslate_stairs.json'))
for variant in state['variants'].values():
    variant['model']=variant['model'].replace('minecraft:block/polished_deepslate_stairs','cuupsidedown:block/temple_stairs')
save_json(ASSETS/'blockstates/temple_stairs.json',state)
for suffix,parent in [('', 'stairs'),('_inner','inner_stairs'),('_outer','outer_stairs')]:
    save_json(ASSETS/'models/block'/f'temple_stairs{suffix}.json',{'parent':f'minecraft:block/{parent}',
       'textures':{'bottom':'cuupsidedown:block/dark_floor','top':'cuupsidedown:block/dark_floor','side':'cuupsidedown:block/lacquer'}})

ids=fulls+['lantern','temple_stairs','tea_table','flower_planter']
for name in ids:
    model=name+'_up' if name in ('tea_table','flower_planter') else name
    save_json(ASSETS/'models/item'/f'{name}.json',{'parent':f'cuupsidedown:block/{model}'})
    save_json(ROOT/'src/main/resources/data/cuupsidedown/loot_table/blocks'/f'{name}.json',{'type':'minecraft:block','pools':[]})
save_json(ROOT/'src/main/resources/data/cuupsidedown/tags/block/dungeon_blocks.json',{'replace':False,'values':['cuupsidedown:'+x for x in ids]})
names={'sakura_wall':'Sakura Shoji Wall','dark_wall':'Midnight Bamboo Wall','zen_wall':'Jade Garden Wall',
       'moon_wall':'Moonlit Cloud Wall','blossom_floor':'Cherry Blossom Floor','dark_floor':'Gilded Midnight Floor',
       'lantern':'Blossom Shoji Lantern','temple_stairs':'Midnight Temple Stairs','tea_table':'Lacquer Tea Ceremony Table',
       'flower_planter':'Porcelain Sakura Bonsai'}
lang=ASSETS/'lang/en_us.json'
old=json.loads(lang.read_text()) if lang.exists() else {}
old.update({f'block.cuupsidedown.{k}':v for k,v in names.items()})
save_json(lang,old)

# Collision arrays are generated from the exact same cuboids as the model art.
java=['package dev.unyielding.upsidedown.dungeon.block;',
      '/** Generated by tools/generate_block_assets.py; collision cuboids match model art. */',
      'public final class DungeonGeometry {','    private DungeonGeometry() {}']
for name,geometry in [('TEA_TABLE',TABLE),('FLOWER_PLANTER',PLANTER)]:
    java.append(f'    public static final double[][] {name} = {{')
    java.extend('        {'+','.join(str(v) for v in x['from']+x['to'])+'},' for x in geometry)
    java.append('    };')
java.append('}')
(JAVA/'DungeonGeometry.java').write_text('\n'.join(java)+'\n',encoding='utf-8')
save_json(ROOT/'tools/furniture_geometry.json',{'tea_table':TABLE,'flower_planter':PLANTER,'lantern':LANTERN})

# Compact, nearest-neighbour texture sheet for visual review.
files=sorted((ASSETS/'textures/block').glob('*.png'))
sheet=Image.new('RGB',(640,math.ceil(len(files)/5)*158),(25,27,36));d=ImageDraw.Draw(sheet)
for i,path in enumerate(files):
    x=(i%5)*128+16;y=(i//5)*158+10
    sheet.paste(Image.open(path).resize((96,96),Image.Resampling.NEAREST),(x,y))
    d.text((x,y+103),path.stem,fill=(234,226,230))
dest=ROOT/'art-review';dest.mkdir(exist_ok=True)
sheet.save(dest/'texture-sheet.png')
print(f'Generated {len(ids)} blocks, {len(files)} original textures, and 12 oriented furniture models.')
