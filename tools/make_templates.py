from pathlib import Path
import sys, json, gzip, struct, random, collections, copy, math, hashlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont

BASE=Path(__file__).resolve().parents[1]
OUT=BASE/'src/main/resources/data/cuupsidedown/dungeons'; OUT.mkdir(parents=True,exist_ok=True)
PRE=OUT/'previews';PRE.mkdir(exist_ok=True)
AIR='minecraft:air'
CUSTOM='cuupsidedown:'
THEMES=['sakura','emo','dark','zen','flower','moon']
WALLS={'sakura':'sakura_wall','emo':'dark_wall','dark':'dark_wall','zen':'zen_wall','flower':'sakura_wall','moon':'moon_wall'}
ACCENTS={'sakura':'minecraft:pink_concrete','emo':'minecraft:purple_concrete','dark':'minecraft:polished_blackstone_bricks','zen':'minecraft:smooth_sandstone','flower':'minecraft:moss_block','moon':'minecraft:amethyst_block'}
COLORS={'sakura':(241,153,187),'emo':(128,80,147),'dark':(51,56,72),'zen':(198,189,156),'flower':(118,165,116),'moon':(145,145,200)}
REQ=['lootr','cobblemon','simplyswords']
class F(float):pass

def nbtstr(s):
 b=s.encode('utf-8');return struct.pack('>H',len(b))+b
def tagtype(v):
 if isinstance(v,str):return 8
 if isinstance(v,dict):return 10
 if isinstance(v,np.ndarray):return 11
 if isinstance(v,list):return 9
 if isinstance(v,F):return 5
 if isinstance(v,float):return 6
 if isinstance(v,int):return 3
 raise TypeError(type(v))
def payload(v,t=None):
 t=t or tagtype(v)
 if t==8:return nbtstr(v)
 if t==3:return struct.pack('>i',v)
 if t==6:return struct.pack('>d',v)
 if t==5:return struct.pack('>f',v)
 if t==11:return struct.pack('>i',v.size)+v.astype('>i4').tobytes()
 if t==10:return b''.join(bytes([tagtype(x)])+nbtstr(k)+payload(x) for k,x in v.items())+b'\0'
 if t==9:
  lt=tagtype(v[0]) if v else 10
  assert all(tagtype(x)==lt for x in v)
  return bytes([lt])+struct.pack('>i',len(v))+b''.join(payload(x,lt) for x in v)
 raise TypeError(t)
def arr(v):return np.array(v,dtype=np.int32)

class Build:
 def __init__(self,id,size,offset):
  self.id=id;self.size=size;self.offset=offset;self.a=np.zeros((size[1],size[2],size[0]),dtype=np.int16);self.pal=[AIR];self.index={AIR:0};self.tiles=[];self.zones=[];self.rooms=[];self.meta={};self.spawn=[0,0,0]
 def pi(self,s):
  if s not in self.index:self.index[s]=len(self.pal);self.pal.append(s)
  return self.index[s]
 def put(self,x,y,z,s):
  assert 0<=x<self.size[0] and 0<=y<self.size[1] and 0<=z<self.size[2],(self.id,x,y,z)
  self.a[y,z,x]=self.pi(s)
 def box(self,x0,y0,z0,x1,y1,z1,s):
  if x1<x0 or y1<y0 or z1<z0:return
  assert min(x0,y0,z0)>=0 and x1<self.size[0] and y1<self.size[1] and z1<self.size[2],(x0,y0,z0,x1,y1,z1)
  self.a[y0:y1+1,z0:z1+1,x0:x1+1]=self.pi(s)
 def chest(self,x,y,z,loot,facing='north'):
  self.put(x,y,z,f'lootr:lootr_chest[facing={facing},type=single,waterlogged=false]')
  self.tiles.append({'id':'lootr:lootr_chest','x':x,'y':y,'z':z,'LootTable':f'cuupsidedown:chests/{loot}'})
 def mimic(self,x,y,z,tags):
  self.put(x,y,z,'minecraft:spawner')
  ent={'id':'minecraft:zombie','Tags':['cu_mimic']+tags,'CustomName':'{"text":"Mimic"}','Silent':1,'IsBaby':0,'CanBreakDoors':0,'CanPickUpLoot':0,'DeathLootTable':'minecraft:entities/cow','HandDropChances':[F(0.0),F(0.0)],'ArmorDropChances':[F(0.0)]*4,'ArmorItems':[{}, {}, {}, {'id':'minecraft:player_head','count':1}],'active_effects':[{'id':'minecraft:invisibility','amplifier':0,'duration':-1,'show_particles':0}],'attributes':[{'id':'minecraft:zombie.spawn_reinforcements','base':0.0}]}
  rules={'block_light_limit':{'min_inclusive':0,'max_inclusive':15},'sky_light_limit':{'min_inclusive':0,'max_inclusive':15}}
  spawn={'entity':ent,'custom_spawn_rules':rules}
  self.tiles.append({'id':'minecraft:mob_spawner','x':x,'y':y,'z':z,'Delay':20,'MinSpawnDelay':40,'MaxSpawnDelay':100,'SpawnCount':3,'MaxNearbyEntities':12,'RequiredPlayerRange':16,'SpawnRange':4,'SpawnData':spawn,'SpawnPotentials':[{'weight':1,'data':copy.deepcopy(spawn)}]})
 def zone(self,name,lo,hi,g):self.zones.append({'Name':name,'Min':arr(lo),'Max':arr(hi),'Gravity':g})
 def save(self,title):
  flat=self.a.ravel(); starts=np.r_[0,np.flatnonzero(flat[1:]!=flat[:-1])+1]; lens=np.diff(np.r_[starts,flat.size]);runs=np.column_stack([lens,flat[starts]]).astype(np.int32).ravel()
  assert int(runs[::2].sum())==flat.size
  root={'Id':self.id,'Name':title,'Size':arr(self.size),'Offset':arr(self.offset),'Palette':self.pal,'Runs':runs,'BlockEntities':self.tiles,'Zones':self.zones,'Spawn':arr(self.spawn),'Requirements':REQ}
  raw=b'\x0a'+nbtstr('')+payload(root)
  with (OUT/(self.id+'.nbt')).open('wb') as f:
   with gzip.GzipFile(filename='',mode='wb',fileobj=f,mtime=0) as g:g.write(raw)
  counts=collections.Counter(t['id'] for t in self.tiles)
  side={'id':self.id,'name':title,'size':self.size,'offset':self.offset,'spawn':self.spawn,'volume':int(flat.size),'non_air':int(np.count_nonzero(flat)),'palette':self.pal,'runs':len(runs)//2,'block_entities':dict(counts),'loot_counts':dict(collections.Counter(t['LootTable'] for t in self.tiles if 'LootTable' in t)),'zones':[{'name':z['Name'],'min':z['Min'].tolist(),'max':z['Max'].tolist(),'gravity':z['Gravity']} for z in self.zones],'rooms':self.rooms,**self.meta}
  (OUT/(self.id+'.json')).write_text(json.dumps(side,indent=2),encoding='utf-8')
  np.savez_compressed(OUT/(self.id+'-geometry.npz'),blocks=self.a,palette=np.array(self.pal))
  return side

def stairs(face,half='bottom'):return f'cuupsidedown:temple_stairs[facing={face},half={half},shape=straight,waterlogged=false]'
def furniture(name,mount):return f'cuupsidedown:{name}[mount={mount}]'

def build_maze():
 b=Build('sakura_labyrinth',[205,36,222],[-102,-5,-202]);rng=random.Random(20261107)
 # Ten by ten pavilions. Every room has a three-wide, normal-gravity promenade.
 ox=12;oz=12;stride=18;fy=5;cy=14
 b.box(3,0,3,201,3,218,'minecraft:stone');b.box(3,4,3,201,4,218,'minecraft:dirt');b.box(3,fy,3,201,fy,218,CUSTOM+'blossom_floor')
 # Deterministic DFS spanning tree + 17 loops, retaining true dead ends.
 graph={i:set() for i in range(100)};seen={90};stack=[90]
 while stack:
  u=stack[-1];x,z=u%10,u//10
  ns=[(z+dz)*10+x+dx for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)] if 0<=x+dx<10 and 0<=z+dz<10 and (z+dz)*10+x+dx not in seen]
  if not ns:stack.pop();continue
  v=rng.choice(ns);seen.add(v);graph[u].add(v);graph[v].add(u);stack.append(v)
 candidates=[(u,v) for u in range(100) for v in [u+1,u+10] if v<100 and (v!=u+1 or u//10==v//10) and v not in graph[u]]
 rng.shuffle(candidates)
 for u,v in candidates[:17]:graph[u].add(v);graph[v].add(u)
 gravity_seq=['down','up','east','up','north','west','down','south','up','east']
 for rid in range(100):
  gx,gz=rid%10,rid//10;x=ox+gx*stride;z=oz+gz*stride
  theme=THEMES[(gx+gz*3+(gz//2))%6];wall=CUSTOM+WALLS[theme];g=gravity_seq[(gx+gz*3)%10]
  dark=theme in ['dark','emo'];floor=CUSTOM+('dark_floor' if dark else 'blossom_floor')
  b.box(x,fy,z,x+18,fy,z+18,floor)
  # Perimeter and low recessed windows: an exterior colonnade of Japanese pavilions.
  for xx in [x,x+18]:b.box(xx,fy+1,z,xx,cy,z+18,wall)
  for zz in [z,z+18]:b.box(x,fy+1,zz,x+18,cy,zz,wall)
  for dx,dz in [(0,0),(18,0),(0,18),(18,18)]:b.box(x+dx,fy+1,z+dz,x+dx,cy+2,z+dz,'minecraft:stripped_cherry_log[axis=y]')
  # Internal gravity chamber, bordered by normal safe corridors.
  b.box(x+4,fy+1,z+4,x+14,cy,z+14,wall);b.box(x+5,fy+1,z+5,x+13,cy-1,z+13,AIR)
  b.box(x+4,fy,z+4,x+14,fy,z+14,ACCENTS[theme]);b.box(x+4,cy,z+4,x+14,cy,z+14,floor)
  # Full height portals permit transition without a narrow-floor trap.
  for zz in [z+4,z+14]:b.box(x+8,fy+1,zz,x+10,cy-1,zz,AIR)
  for xx in [x+4,x+14]:b.box(xx,fy+1,z+8,xx,cy-1,z+10,AIR)
  mount={'down':'up','up':'down','east':'west','west':'east','north':'south','south':'north'}[g]
  # Furniture touches the active floor/ceiling/wall and rotates with it.
  if g=='down':fpos=[(x+6,fy+1,z+6),(x+12,fy+1,z+12)]
  elif g=='up':fpos=[(x+6,cy-1,z+6),(x+12,cy-1,z+12)]
  elif g=='east':fpos=[(x+13,fy+2,z+6),(x+13,fy+5,z+12)]
  elif g=='west':fpos=[(x+5,fy+2,z+6),(x+5,fy+5,z+12)]
  elif g=='north':fpos=[(x+6,fy+2,z+5),(x+12,fy+5,z+5)]
  else:fpos=[(x+6,fy+2,z+13),(x+12,fy+5,z+13)]
  b.put(*fpos[0],furniture('tea_table',mount));b.put(*fpos[1],furniture('flower_planter',mount))
  # Three restock alcoves on the safe promenade. The fourth requires entering
  # the themed room; non-DOWN rooms reward walking on its ceiling or wall.
  for dx,dz in [(2,6),(2,12),(16,6)]:b.chest(x+dx,fy+1,z+dz,'maze_common','east' if dx==2 else 'west')
  loot='maze_rare' if rid%5==4 else 'maze_common'
  if g=='down':
   b.zone(f'room_{rid+1:03}_{theme}',[x+5,fy+1,z+5],[x+13,cy-1,z+13],g)
   b.chest(x+12,fy+1,z+11,loot,'north');reward=[x+12,fy+1,z+11];pocket=None
  else:
   # Normal-gravity reward pocket high against the active support surface.
   # It is excluded from the surrounding chamber zones, not an ambiguous overlap.
   px=x+(5 if g=='west' else 10);pz=z+(5 if g=='north' else 10);px1=px+3;pz1=pz+3;sy=fy+5
   xb=[x+5,px-1] if px>x+5 else [px1+1,x+13]
   zb=[z+5,pz-1] if pz>z+5 else [pz1+1,z+13]
   b.zone(f'room_{rid+1:03}_{theme}_a',[xb[0],fy+1,z+5],[xb[1],cy-1,z+13],g)
   b.zone(f'room_{rid+1:03}_{theme}_b',[px,fy+1,zb[0]],[px1,cy-1,zb[1]],g)
   b.zone(f'room_{rid+1:03}_{theme}_c',[px,fy+1,pz],[px1,sy,pz1],g)
   b.box(px,sy,pz,px1,sy,pz1,CUSTOM+'moon_wall');b.box(px,sy+1,pz,px1,cy-1,pz1,AIR)
   reward=[px+2,sy+1,pz+2];b.chest(*reward,loot,'north')
   b.put(px,sy+1,pz,furniture('flower_planter','up'))
   pocket={'min':[px,sy+1,pz],'max':[px1,cy-1,pz1],'gravity':'down','support_y':sy,'entry_from':g,'chest':reward}
  # Lanterns on corner posts, unobstructed corridor crossing and room-specific accents.
  for dx,dz in [(2,2),(16,16)]:b.put(x+dx,cy-1,z+dz,CUSTOM+'lantern')
  b.put(x+6,cy-1,z+12,CUSTOM+'lantern' if not dark else 'minecraft:amethyst_block')
  if rid%6==1:
   # Hidden behind a one-block panel, spawn interior within two horizontal blocks.
   b.box(x+3,fy+1,z+5,x+5,fy+4,z+7,wall)
   b.mimic(x+4,fy+2,z+6,[f'cu_maze_{rid+1}'])
  # Layered hipped pavilion roofs with upturned eaves and ridges.
  for layer in range(6):
   lo=layer;hi=18-layer;y=cy+1+layer
   b.box(x+lo,y,z+lo,x+hi,y,z+lo,'minecraft:deepslate_tiles')
   b.box(x+lo,y,z+hi,x+hi,y,z+hi,'minecraft:deepslate_tiles')
   b.box(x+lo,y,z+lo,x+lo,y,z+hi,'minecraft:deepslate_tiles')
   b.box(x+hi,y,z+lo,x+hi,y,z+hi,'minecraft:deepslate_tiles')
  b.box(x+6,cy+7,z+6,x+12,cy+7,z+12,wall)
  b.box(x+8,cy+8,z+6,x+10,cy+8,z+12,'minecraft:cherry_slab[type=bottom,waterlogged=false]')
  for dx,dz in [(0,0),(18,0),(0,18),(18,18)]:b.put(x+dx,cy+2,z+dz,stairs('north' if dz==0 else 'south','top'))
  b.rooms.append({'number':rid+1,'theme':theme,'gravity':g,'cell':[gx,gz],'bounds':[x,z,x+18,z+18],'safe_waypoint':[x+9,fy+1,z+2],'neighbors':[v+1 for v in sorted(graph[rid])],'loot':4,'challenge_chest':reward,'reward_pocket':pocket})
 # Cut maze graph doors after all shared walls have been built.
 for u in range(100):
  x=ox+(u%10)*18;z=oz+(u//10)*18
  for v in graph[u]:
   if v==u+1:b.box(x+18,fy+1,z+8,x+18,fy+4,z+10,AIR)
   if v==u+10:b.box(x+8,fy+1,z+18,x+10,fy+4,z+18,AIR)
 # Entrance south-west cell and exit north-east; a garden torii and clear plaza.
 entx=ox+9;entz=oz+180;b.box(entx-1,fy+1,entz,entx+1,fy+5,entz,AIR)
 ex=ox+9*18+9;b.box(ex-1,fy+1,oz,ex+1,fy+5,oz,AIR)
 # Enclose the garden and isolate the north exit court from entrance-side paths.
 for xx in [2,202]:b.box(xx,fy+1,2,xx,fy+7,220,CUSTOM+'zen_wall')
 for zz in [2,220]:b.box(2,fy+1,zz,202,fy+7,zz,CUSTOM+'zen_wall')
 b.box(2,fy+1,oz,ox-1,fy+7,oz,CUSTOM+'zen_wall')
 b.box(ox+181,fy+1,oz,202,fy+7,oz,CUSTOM+'zen_wall')
 # Small moon shrine in the separated exit court.
 b.box(ex-4,fy+1,4,ex-4,fy+5,9,CUSTOM+'moon_wall');b.box(ex+4,fy+1,4,ex+4,fy+5,9,CUSTOM+'moon_wall')
 b.box(ex-5,fy+6,4,ex+5,fy+6,9,'minecraft:deepslate_tiles');b.put(ex,fy+5,4,CUSTOM+'lantern')
 for xx in [entx-5,entx+5]:b.box(xx,fy+1,210,xx,fy+9,210,CUSTOM+'sakura_wall')
 b.box(entx-7,fy+9,210,entx+7,fy+9,210,CUSTOM+'dark_wall');b.box(entx-8,fy+11,210,entx+8,fy+11,210,'minecraft:cherry_planks')
 for xx in [6,198]:
  for zz in range(10,211,20):
   b.box(xx,fy+1,zz,xx,fy+5,zz,'minecraft:cherry_log[axis=y]')
   b.box(max(1,xx-3),fy+5,zz-3,min(203,xx+3),fy+7,zz+3,'minecraft:cherry_leaves[distance=7,persistent=true,waterlogged=false]')
 b.spawn=[entx,fy+1,216]
 # Exact 2-high normal-gravity traversal validation of every promenade.
 blocked_zone=np.zeros((b.size[2],b.size[0]),bool)
 for zone in b.zones:
  lo,hi=zone['Min'],zone['Max'];blocked_zone[lo[2]:hi[2]+1,lo[0]:hi[0]+1]=True
 walk=(b.a[fy]!=0)&(b.a[fy+1]==0)&(b.a[fy+2]==0)&~blocked_zone
 q=collections.deque([(b.spawn[0],b.spawn[2])]);visited=set(q)
 while q:
  x,z=q.popleft()
  for xx,zz in [(x+1,z),(x-1,z),(x,z+1),(x,z-1)]:
   if 0<=xx<b.size[0] and 0<=zz<b.size[2] and walk[zz,xx] and (xx,zz) not in visited:visited.add((xx,zz));q.append((xx,zz))
 assert all((r['safe_waypoint'][0],r['safe_waypoint'][2]) in visited for r in b.rooms)
 # Exactly three chests per room have reachable safe promenade cells.
 promenade_chests=0
 for t in b.tiles:
  if t['id']=='lootr:lootr_chest' and t['y']==fy+1 and any((t['x']+dx,t['z']+dz) in visited for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]):promenade_chests+=1
 assert promenade_chests==300,promenade_chests
 for r in b.rooms:
  if r['reward_pocket']:
   p=r['reward_pocket'];px,py,pz=p['min'];xx,yy,zz=p['max'];assert py-(fy+1)==5
   assert all(not (z['Min'][0]<=p['chest'][0]<=z['Max'][0] and z['Min'][1]<=p['chest'][1]<=z['Max'][1] and z['Min'][2]<=p['chest'][2]<=z['Max'][2]) for z in b.zones)
   # Upright native chest opens into clear two-block air inside the high shrine.
   x1,y1,z1=p['chest'];assert b.a[y1+1,z1,x1]==0 and b.a[y1+2,z1,x1]==0
 edges=sum(map(len,graph.values()))//2;dead=[u+1 for u,v in graph.items() if len(v)==1]
 b.meta.update({'room_count':100,'graph_edges':edges,'independent_cycles':edges-99,'dead_end_rooms':dead,'safe_promenade_bfs_reachable_cells':len(visited),'all_room_promenades_reachable_without_entering_gravity_zones':True,'promenade_chests':300,'challenge_chests':100,'elevated_gravity_reward_pockets':sum(r['reward_pocket'] is not None for r in b.rooms),'gravity_chambers':100,'spawner_policy':'20 initial, 40-100 tick interval; 3 per burst; cap12; bright rooms allowed','geometry_checks':'Normal-gravity 2-high promenade BFS, all 100 room waypoints and 300 restock chests; 100 challenge chests inside chambers, 80 on high normal-gravity reward ledges connected to active gravity support surface; deterministic graph plus17loops. Actual gravity movement requires game playtest.'})
 return b

def flip_state(s):
 if s.startswith('cuupsidedown:temple_stairs') or ('stairs[' in s):s=s.replace('half=bottom','half=TEMP').replace('half=top','half=bottom').replace('half=TEMP','half=top')
 if 'slab[' in s:s=s.replace('type=bottom','type=TEMP').replace('type=top','type=bottom').replace('type=TEMP','type=top')
 if 'mount=' in s:s=s.replace('mount=up','mount=TEMP').replace('mount=down','mount=up').replace('mount=TEMP','mount=down')
 if 'hanging=' in s:s=s.replace('hanging=false','hanging=TEMP').replace('hanging=true','hanging=false').replace('hanging=TEMP','hanging=true')
 return s

def build_pagoda():
 # Ground is local y320. Two 25-storey towers mirrored about world y=-5.5.
 b=Build('mirror_pagoda',[97,636,107],[-48,-323,-48]);Y=323;cx=48;cz=48
 # Separate upright construction, then exact state-aware mirrored copy.
 stair_cells=[];landings=[];floor_records=[]
 for f in range(25):
  base=Y+f*12;r=27-f//3;theme=THEMES[(f//4)%6];wall=CUSTOM+WALLS[theme]
  b.box(cx-r,base,cz-r,cx+r,base,cz+r,CUSTOM+'blossom_floor')
  # Open stairwell in every level, with the north landing left intact.
  b.box(cx-11,base,cz-6,cx-3,base,cz+3,AIR)
  # Thick corner pillars and patterned walls; high windows are protected glass.
  for xx in [cx-r,cx+r]:b.box(xx,base+1,cz-r,xx,base+10,cz+r,wall)
  for zz in [cz-r,cz+r]:b.box(cx-r,base+1,zz,cx+r,base+10,zz,wall)
  for sx in [-1,1]:
   for sz in [-1,1]:b.box(cx+sx*(r-1),base+1,cz+sz*(r-1),cx+sx*(r-1),base+11,cz+sz*(r-1),'minecraft:stripped_cherry_log[axis=y]')
  for delta in range(-r+5,r-3,7):
   for xx in [cx-r,cx+r]:b.box(xx,base+4,cz+delta,xx,base+7,cz+delta+2,'minecraft:pink_stained_glass')
   for zz in [cz-r,cz+r]:b.box(cx+delta,base+4,zz,cx+delta+2,base+7,zz,'minecraft:pink_stained_glass')
  # Broad dark upturned eaves. Each tier is a recognizable Japanese roof edge.
  for spread,h in [(4,8),(3,9),(2,10),(1,11)]:
   rr=r+spread
   for zz in [cz-rr,cz+rr]:b.box(cx-rr,base+h,zz,cx+rr,base+h,zz,'minecraft:deepslate_tiles')
   for xx in [cx-rr,cx+rr]:b.box(xx,base+h,cz-rr,xx,base+h,cz+rr,'minecraft:deepslate_tiles')
  for sx in [-1,1]:
   for sz in [-1,1]:b.put(cx+sx*(r+4),base+9,cz+sz*(r+4),stairs('north' if sz<0 else 'south','top'))
  # Inlaid pattern uses full blocks so its inverted twin is truly mirrored.
  for xx in range(cx-2,cx+3):b.box(xx,base,cz-r+2,xx,base,cz+r-2,ACCENTS[theme])
  # Four loot alcoves per floor, all outside the stairwell.
  chestpos=[(cx-r+5,base+1,cz-12),(cx+r-5,base+1,cz-12),(cx-r+5,base+1,cz+12),(cx+r-5,base+1,cz+12)]
  if f==24:
   # Four-step shrine dais gives the mirrored final chest unique lowest elevation.
   for j in range(4):b.box(cx+6+j,base+1+j,cz+6,cx+14,base+1+j,cz+12,CUSTOM+'moon_wall')
   for j in range(4):b.box(cx+6+j,base+1+j,cz+6,cx+6+j,base+1+j,cz+12,stairs('east'))
   chestpos[3]=(cx+12,base+5,cz+9)
  for x,y,z in chestpos:b.chest(x,y,z,'pagoda_rare','north')
  # Tea-room furnishings, floor and ceiling lamps, flowers and themed interior accent.
  for dx,dz in [(8,-7),(8,2),(-14,9)]:
   b.put(cx+dx,base+1,cz+dz,furniture('tea_table','up'));b.put(cx+dx+2,base+1,cz+dz,furniture('flower_planter','up'))
  for dx,dz in [(12,12),(-12,12),(12,-12),(-12,-12)]:b.put(cx+dx,base+9,cz+dz,CUSTOM+'lantern')
  # 1+1+2*6+3*17 = 65, hidden one block behind accent walls.
  n=1 if f<2 else 2 if f<8 else 3
  positions=[(cx+r-1,base+2,cz-7),(cx+r-1,base+2,cz+7),(cx+7,base+2,cz+r-1)]
  for i,(x,y,z) in enumerate(positions[:n]):
   # Fully sealed small plinth: all six adjacent faces hide the spawner.
   # Valid floor space remains two horizontal blocks away, within SpawnRange=4.
   b.box(x-1,base+1,z-1,x+1,base+3,z+1,wall)
   tags=[f'cu_floor_{f+1}']+(['cu_top'] if f==24 else []);b.mimic(x,y,z,tags)
  if f<24:
   # 3-wide switchback staircase: 6 up, 3x7 landing, 6 back up.
   for step in range(1,7):
    z=cz-7+step;y=base+step
    b.box(cx-10,y,z,cx-8,y,z,stairs('south'));stair_cells.extend((x,y,z) for x in range(cx-10,cx-7))
   b.box(cx-10,base+6,cz,cx-4,base+6,cz+2,CUSTOM+'dark_floor')
   for step in range(1,7):
    z=cz-step;y=base+6+step
    b.box(cx-6,y,z,cx-4,y,z,stairs('north'));stair_cells.extend((x,y,z) for x in range(cx-6,cx-3))
   landings.append([cx-5,base+12,cz-7])
  floor_records.append({'floor':f+1,'base_y':base,'radius':r,'theme':theme,'spawners':n,'loot':4,'landing':[cx-5,base+1,cz-8]})
 # Later floor cutouts touch the final step of each prior flight; restore those
 # intentionally after every floor is cut so each upper landing has a real bridge.
 for x,y,z in stair_cells:b.put(x,y,z,stairs('south' if x<=cx-8 else 'north'))
 # Top pagoda cap: nested roof converges to a blossom crown.
 top=Y+24*12
 for level in range(11):
  r=19-level;yy=top+11+level
  b.box(cx-r,yy,cz-r,cx+r,yy,cz+r,'minecraft:deepslate_tiles')
 b.box(cx-2,top+22,cz-2,cx+2,top+23,cz+2,CUSTOM+'sakura_wall');b.put(cx,top+24,cz,CUSTOM+'lantern')
 # Headroom proof on stairs before reflection. Stairs have at least two empty blocks above high edge.
 for x,y,z in stair_cells:
  assert b.a[y+1,z,x]==0 and b.a[y+2,z,x]==0,('stair headroom',x,y,z,b.pal[b.a[y+1,z,x]],b.pal[b.a[y+2,z,x]])
 # Mirror every tower cell through yworld=-5.5. y' = 2*Y - 11 - y.
 original=b.a[Y:,:,:].copy();pal_before=list(b.pal);flipids=np.array([b.pi(flip_state(s)) for s in pal_before],np.int16)
 for y in range(Y,b.size[1]):
  my=2*Y-11-y
  if my>=0:b.a[my]=flipids[original[y-Y]]
 originals=copy.deepcopy(b.tiles)
 for t in originals:
  t['y']=2*Y-11-t['y']
  if t['id']=='minecraft:mob_spawner':
   t['SpawnData']['entity']['Tags'].append('cu_upside');t['SpawnPotentials'][0]['data']['entity']['Tags'].append('cu_upside')
  if t['id']=='lootr:lootr_chest' and t['y']==2*Y-11-(Y+24*12+5):t['LootTable']='cuupsidedown:chests/pagoda_final'
  if t['id']=='lootr:lootr_chest':
   # Native Lootr chests do not have an upside-down model. Hang them upright
   # from a chain: unlike a solid ceiling, the chain does not obstruct the lid.
   b.put(t['x'],t['y']+1,t['z'],'minecraft:chain[axis=y,waterlogged=false]')
   b.put(t['x'],t['y']+2,t['z'],CUSTOM+'dark_floor')
  b.tiles.append(t)
 # Base courtyard: upright entrance at world y0; inverted entrance via protected external ladder well.
 b.box(4,Y-2,4,92,Y-1,102,'minecraft:stone');b.box(4,Y,4,92,Y,102,CUSTOM+'blossom_floor')
 # Restore main staircase hole after plaza floor. Main front door.
 b.box(cx-11,Y,cz-6,cx-3,Y,cz+3,AIR)
 b.box(cx-2,Y+1,cz+27,cx+2,Y+5,cz+27,AIR)
 # Downward access shaft outside all gravity zones, with an open connection into inverted tower.
 sx=cx+8;sz=cz+38
 b.box(sx-2,Y-18,sz-2,sx+2,Y+2,sz+2,CUSTOM+'zen_wall');b.box(sx-1,Y-17,sz-1,sx+1,Y+3,sz+1,AIR)
 for y in range(Y-17,Y+3):b.put(sx,y,sz+1,'minecraft:ladder[facing=north,waterlogged=false]')
 b.box(sx-1,Y-14,cz+26,sx+1,Y-12,sz,AIR)
 b.box(sx-2,Y-15,cz+26,sx+2,Y-15,sz,CUSTOM+'dark_floor')
 b.box(sx-2,Y-11,cz+26,sx+2,Y-11,sz,CUSTOM+'blossom_floor')
 # Shaft outside zone; tunnel reaches the upside chamber through full-height door.
 b.zone('mirror_pagoda_up',[cx-32,0,cz-32],[cx+32,Y-6,cz+32],'up')
 # Wooden entry arch, cherry trees, lantern-lined front path.
 for xx in [cx-8,cx+8]:b.box(xx,Y+1,97,xx,Y+10,97,CUSTOM+'sakura_wall')
 b.box(cx-11,Y+10,97,cx+11,Y+10,97,CUSTOM+'dark_wall');b.box(cx-12,Y+12,97,cx+12,Y+12,97,'minecraft:cherry_planks')
 for x,z in [(10,12),(86,12),(10,90),(86,90)]:
  b.box(x,Y+1,z,x,Y+7,z,'minecraft:cherry_log[axis=y]');b.box(x-5,Y+6,z-5,x+5,Y+10,z+5,'minecraft:cherry_leaves[distance=7,persistent=true,waterlogged=false]')
 b.spawn=[cx,Y+1,103]
 # Discrete conservative walk graph across upright floor surfaces and stair high edges.
 solid=b.a!=0
 for i,s in enumerate(b.pal):
  if 'carpet' in s:solid[b.a==i]=False
 support=solid[:-2]&~solid[1:-1]&~solid[2:]
 # Restrict to tower interior; low outside garden routes don't establish upper-floor connectivity.
 start=(cx,Y,cz+24);q=collections.deque([start]);vis={start}
 while q:
  x,y,z=q.popleft()
  for xx,zz in [(x+1,z),(x-1,z),(x,z+1),(x,z-1)]:
   if not(cx-28<=xx<=cx+28 and cz-28<=zz<=cz+28):continue
   for yy in [y,y+1,y-1]:
    if Y<=yy<b.size[1]-2 and support[yy,zz,xx] and (xx,yy,zz) not in vis:vis.add((xx,yy,zz));q.append((xx,yy,zz))
 for f in floor_records:
  x,y,z=f['landing'];assert (x,y-1,z) in vis,('unreachable floor',f)
 for t in b.tiles:
  if t['id']=='lootr:lootr_chest' and t['y']>=Y:
   assert any((t['x']+dx,t['y']-1,t['z']+dz) in vis for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)]),('unreachable chest',t)
 final=[t for t in b.tiles if t.get('LootTable')=='cuupsidedown:chests/pagoda_final'];assert len(final)==1
 assert final[0]['y']<min(t['y'] for t in b.tiles if t['id']=='lootr:lootr_chest' and t not in final)
 # State-aware symmetry verified for every stair before the ground entrance modifications.
 for x,y,z in stair_cells:
  my=2*Y-11-y;assert b.pal[b.a[my,z,x]]==flip_state(b.pal[b.a[y,z,x]])
  assert b.a[my-1,z,x]==0 and b.a[my-2,z,x]==0
 b.rooms=[{'wing':'upright',**f} for f in floor_records]+[{'wing':'inverted',**{**f,'base_y':2*Y-11-f['base_y']}} for f in floor_records]
 b.meta.update({'floors_per_wing':25,'floor_spacing':12,'ground_local_y':Y,'world_y_range_relative_anchor':[-323,312],'stairs':{'width':3,'flights_per_storey':2,'rise_per_flight':6,'normal_and_mirrored_headroom_verified':True,'upper_floor_access_bfs':True},'final_chest_local':[final[0][k] for k in 'xyz'],'normal_stair_bfs_reachable_nodes':len(vis),'spawner_policy':'20 initial, 40-100 tick interval; 3 per burst; cap12; bright rooms allowed','geometry_checks':'All 25 upright floors and all upright chests reached by 2-block headroom/support graph. All 864 staircase blocks and reflected counterpart states/headroom verified. Mirror mapping proves same geometry in UP gravity; in-game collision playtest remains required.'})
 return b

def palette_color(s):
 if s==AIR:return(20,23,31)
 if 'lootr' in s:return(255,205,65)
 if 'spawner' in s:return(229,51,62)
 if 'lantern' in s:return(255,226,155)
 if 'sakura_wall' in s or 'cherry' in s or 'blossom' in s:return(227,154,177)
 if 'moon' in s or 'amethyst' in s:return(142,132,197)
 if 'zen' in s or 'sandstone' in s:return(200,187,155)
 if 'dark' in s or 'deepslate' in s:return(62,65,79)
 if 'flower' in s or 'moss' in s:return(113,172,123)
 if 'tea_table' in s:return(155,101,105)
 if 'glass' in s:return(185,135,157)
 if 'stairs' in s:return(155,113,148)
 if 'air' in s:return(20,23,31)
 if 'stone' in s:return(126,130,139)
 return(161,148,137)

def previews(b):
 colors=np.array([palette_color(s) for s in b.pal],np.uint8)
 if b.id=='sakura_labyrinth':
  # Real horizontal cut at chest height, with maze paths/room interiors visible.
  img=Image.fromarray(colors[b.a[6]]).resize((1025,1110),Image.Resampling.NEAREST);d=ImageDraw.Draw(img)
  for r in b.rooms:
   x,z,x1,z1=r['bounds'];d.text(((x+7)*5,(z+7)*5),str(r['number']),fill=(250,245,242))
  img.save(PRE/(b.id+'-floorplan.png'))
  # Actual top-most occupied voxel projection.
  mask=b.a!=0;ys=b.a.shape[0]-1-np.argmax(mask[::-1],axis=0);zz,xx=np.indices(ys.shape);view=colors[b.a[ys,zz,xx]]
  Image.fromarray(view).resize((820,888),Image.Resampling.NEAREST).save(PRE/(b.id+'-roofs.png'))
 else:
  # Front orthographic view and true cut through switchback staircase.
  mask=b.a!=0;zi=b.a.shape[1]-1-np.argmax(mask[:,::-1,:],axis=1);yy,xx=np.indices(zi.shape);view=colors[b.a[yy,zi,xx]][::-1]
  Image.fromarray(view).resize((388,1266),Image.Resampling.NEAREST).save(PRE/(b.id+'-front.png'))
  sl=colors[b.a[:,:,40]][::-1];Image.fromarray(sl).resize((321,1266),Image.Resampling.NEAREST).save(PRE/(b.id+'-stair-section.png'))

def main():
 import argparse
 global OUT,PRE
 parser=argparse.ArgumentParser(description='Rebuild authored dungeons and run geometry assertions. Heavy checks belong on GitHub Actions.')
 parser.add_argument('--output',type=Path)
 args=parser.parse_args()
 if args.output:OUT=args.output;OUT.mkdir(parents=True,exist_ok=True);PRE=OUT/'previews';PRE.mkdir(exist_ok=True)
 for maker,title in [(build_maze,'Sakura Labyrinth — 100 Rooms'),(build_pagoda,'Mirror Sakura Pagoda — 25 + 25 Floors')]:
  b=maker();stats=b.save(title);previews(b);print(b.id,stats['non_air'],stats['block_entities'],stats['loot_counts'],flush=True)
if __name__=='__main__':main()
