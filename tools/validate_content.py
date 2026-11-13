#!/usr/bin/env python3
"""Read-only content checks; no third-party Python dependencies or game launch.

Run: python tools/validate_content.py --templates ../upside-dungeons-design/templates
Optional --mods PATH checks actual installed reward assets/unique tags as independent evidence.
Runtime registry/codec/loot-modifier verification is /dungeon verifyloot in the running game.
"""
from __future__ import annotations
import argparse,bisect,collections,gzip,json,math,struct,sys,zipfile
from pathlib import Path

TOP_REWARDS={"cobblemon:master_ball","cobblemon:ancient_origin_ball","simplyswords:stars_edge",
 "simplyswords:whisperwind","simplyswords:ribboncleaver","simplyswords:mjolnir",
 "simplyswords:hearthflame","simplyswords:arcanethyst"}
TABLES={"maze_common","maze_rare","pagoda_rare","pagoda_final"}
FAST={"Delay":20,"MinSpawnDelay":40,"MaxSpawnDelay":100,"SpawnCount":3,
 "MaxNearbyEntities":12,"RequiredPlayerRange":16,"SpawnRange":4}
AIR={"minecraft:air","minecraft:cave_air","minecraft:void_air"}
class NbtList(list):
 def __init__(self,kind,values):super().__init__(values);self.element_type=kind
class Reader:
 def __init__(self,b):self.b=b;self.p=0
 def take(self,n):
  if n<0 or self.p+n>len(self.b):raise ValueError("Invalid NBT length")
  r=self.b[self.p:self.p+n];self.p+=n;return r
 def num(self,f):return struct.unpack('>'+f,self.take(struct.calcsize('>'+f)))[0]
 def string(self):return self.take(self.num('H')).decode('utf-8')
 def val(self,t):
  if 1<=t<=6:return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return self.take(self.num('i'))
  if t==8:return self.string()
  if t==9:
   k=self.num('B');n=self.num('i');return NbtList(k,(self.val(k) for _ in range(n)))
  if t==10:
   d={}
   while (k:=self.num('B'))!=0:
    name=self.string();d[name]=self.val(k)
   return d
  if t in (11,12):
   n=self.num('i');f='i' if t==11 else 'q';return list(struct.unpack('>'+f*n,self.take(n*(4 if t==11 else 8))))
  raise ValueError(f'Unknown tag {t}')
def read_nbt(path):
 b=path.read_bytes();b=gzip.decompress(b) if b[:2]==b'\x1f\x8b' else b
 r=Reader(b);t=r.num('B');r.string();d=r.val(t)
 assert r.p==len(b),f'Trailing data in {path}'
 return d

def assert_true(value,message):
 if not value:raise AssertionError(message)

def loot_checks(resources,mods):
 root=resources/'data/cuupsidedown/loot_table/chests';items=set();result={}
 for name in sorted(TABLES):
  table=json.loads((root/(name+'.json')).read_text(encoding='utf-8-sig'))
  assert_true(table['type']=='minecraft:chest',name+' must use chest context')
  assert_true(table.get('pools'),name+' is empty')
  maxrolls=0
  for pool in table['pools']:
   assert_true(pool.get('bonus_rolls',0)==0,name+' unexpected luck bonus')
   rolls=pool['rolls'];maxrolls+=rolls['max'] if isinstance(rolls,dict) else rolls
   for e in pool['entries']:
    assert_true(e['type']=='minecraft:item',name+' uses indirect/empty reward entry')
    assert_true(e.get('weight',1)>0,name+' has nonpositive weight')
    items.add(e['name'])
    for f in e.get('functions',[]):
     assert_true(f['function']=='minecraft:set_count',name+' has unreviewed loot function')
     count=f['count'];minimum=count['min'] if isinstance(count,dict) else count
     assert_true(minimum>=1,name+' permits zero reward stack')
  assert_true(maxrolls<=27,name+' exceeds native chest capacity before stack splitting')
  result[name]={'pools':len(table['pools']),'maximum_entry_rolls':maxrolls}
  if name=='pagoda_final':
   guarantee=table['pools'][0]
   assert_true(guarantee['rolls']==1 and not guarantee.get('conditions'),'Final pool is not unconditional exactly-one')
   assert_true(not table.get('functions'),'Table-wide functions could remove final reward')
   assert_true({e['name'] for e in guarantee['entries']}==TOP_REWARDS,'Final curated pool changed')
   assert_true(all(not e.get('conditions') and not e.get('functions') for e in guarantee['entries']),'Final entries can fail or alter count')
 if mods:
  jars={}
  for ns,prefix in [('cobblemon','Cobblemon-neoforge-'),('simplyswords','simplyswords-neoforge-')]:
   matches=list(mods.glob(prefix+'*.jar'));assert_true(len(matches)==1,'Expected one '+prefix+' jar')
   jars[ns]=zipfile.ZipFile(matches[0])
  for full in items:
   ns,key=full.split(':',1)
   if ns in jars:
    assert_true('assets/'+ns+'/models/item/'+key+'.json' in jars[ns].namelist(),'No installed item model: '+full)
  swordtag=json.loads(jars['simplyswords'].read('data/simplyswords/tags/item/lootable_uniques.json'))['values']
  for full in TOP_REWARDS:
   if full.startswith('simplyswords:'):assert_true(full in swordtag,'Final sword absent from native lootable_uniques: '+full)
  # Models/tags support exact names; the server command independently checks registry existence.
  for z in jars.values():z.close()
 return {'tables':result,'distinct_items':len(items),'installed_assets_checked':bool(mods)}

def template_checks(path):
 d=read_nbt(path);size=d['Size'];palette=d['Palette'];runs=d['Runs'];tiles=d['BlockEntities'];zones=d['Zones']
 assert_true(len(size)==3 and all(x>0 for x in size),'Invalid template dimensions')
 assert_true(len(runs)%2==0,'Invalid RLE pair count')
 assert_true(all(p.split('[',1)[0]!='minecraft:bedrock' for p in palette),'Bedrock still in palette')
 endpoints=[];stateindices=[];count=0;nonair=0
 for length,index in zip(runs[::2],runs[1::2]):
  assert_true(length>0 and 0<=index<len(palette),'Invalid RLE state/length')
  count+=length;endpoints.append(count);stateindices.append(index)
  if palette[index].split('[',1)[0] not in AIR:nonair+=length
 assert_true(count==math.prod(size),'RLE total does not match template volume')
 sx,sy,sz=size
 def state_at(pos):
  x,y,z=pos;assert_true(0<=x<sx and 0<=y<sy and 0<=z<sz,'Block entity/marker outside template')
  idx=(y*sz+z)*sx+x
  return palette[stateindices[bisect.bisect_right(endpoints,idx)]].split('[',1)[0]
 metadata=json.loads(path.with_suffix('.json').read_text()) if path.with_suffix('.json').exists() else None
 posseen=set();chests=[];spawners=[]
 for tile in tiles:
  pos=tuple(tile[k] for k in ('x','y','z'));assert_true(pos not in posseen,'Duplicate block entity position');posseen.add(pos)
  state=state_at(pos);tid=tile.get('id','')
  if 'chest' in tid or 'chest' in state:
   chests.append(tile)
   assert_true(state=='lootr:lootr_chest','Dungeon chest is not native Lootr')
   assert_true(tile.get('LootTable','').startswith('cuupsidedown:chests/'),'Chest loot table is not ours')
   assert_true(tile['LootTable'].split('/')[-1] in TABLES,'Unknown dungeon loot tier')
   assert_true(not any(k in tile for k in ['Items','LootrId','LootrOpeners','LootrHasBeenOpened','customInventory','customSize']),'Copied inventory/identity defeats fresh personal loot')
  if 'spawner' in tid or 'spawner' in state:
   spawners.append(tile)
   x,y,z=pos
   for adjacent in [(x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)]:
    assert_true(state_at(adjacent) not in AIR,'Spawner is not enclosed on six faces: '+str(pos))
   for key,value in FAST.items():assert_true(tile.get(key)==value,f'Spawner {pos} {key} expected {value}, got {tile.get(key)}')
   spawns=[tile.get('SpawnData',{})]+[x['data'] for x in tile.get('SpawnPotentials',[])]
   for data in spawns:
    rules=data.get('custom_spawn_rules',{})
    for light in ['block_light_limit','sky_light_limit']:
     assert_true(rules.get(light)=={'min_inclusive':0,'max_inclusive':15},'Spawner still light-limited or wrong inclusive codec')
    assert_true(data.get('entity',{}).get('id')=='minecraft:zombie','Mimic base mob changed')
    assert_true('cu_mimic' in data['entity'].get('Tags',[]),'Spawner missing mimic tag')
    for field in ['ArmorDropChances','HandDropChances']:
     if field in data['entity']:
      assert_true(getattr(data['entity'][field],'element_type',None)==5,'Spawner equipment drop chances must be FLOAT-list: '+field)
 for zone in zones:
  assert_true(zone['Gravity'] in ['down','up','north','south','east','west'],'Invalid gravity direction')
  assert_true(len(zone['Min'])==len(zone['Max'])==3,'Invalid zone corners')
  assert_true(all(a<=b for a,b in zip(zone['Min'],zone['Max'])),'Reversed gravity zone')
 assert_true(len(zones)>0,'Template has no gravity gameplay')
 lootcounts=collections.Counter(t['LootTable'] for t in chests)
 final=[t for t in chests if t['LootTable']=='cuupsidedown:chests/pagoda_final']
 geometry={}
 if 'labyrinth' in path.stem:
  assert_true(all(state_at((x,5,z)) not in AIR for x in range(2,203) for z in (219,220)), 'Maze outer floor strip has a gap')
  assert_true(all(state_at((x,y,220)) not in AIR for x in range(2,203) for y in range(6,13)), 'Maze south perimeter wall has a gap')
  assert_true(len(chests)>=400,'100-room maze needs at least four chests per room')
  assert_true(not final,'Maze must not contain final pagoda reward')
  assert_true(len(zones)>=100,'100-room maze needs at least 100 room gravity zones')
  assert_true(len({z['Gravity'] for z in zones})>=4,'Maze does not exercise enough gravity directions')
  if metadata:
   rooms=metadata['rooms'];byid={r['number']:r for r in rooms}
   assert_true(len(rooms)==len(byid)==100,'Maze room graph does not have100 unique rooms')
   seen={1};todo=[1]
   for current in todo:
    for other in byid[current]['neighbors']:
     assert_true(other in byid and current in byid[other]['neighbors'],'Maze graph asymmetric/dangling edge')
     if other not in seen:seen.add(other);todo.append(other)
   assert_true(len(seen)==100,'Maze room graph disconnected')
   edges=sum(len(r['neighbors']) for r in rooms)//2
   assert_true(edges-100+1>=1,'Maze has no loop')
   # Conservative independent voxel check: only literal air counts as unobstructed.
   y=d['Spawn'][1];walkable=set()
   for z in range(sz):
    for x in range(sx):
     if state_at((x,y,z)) in AIR and state_at((x,y+1,z)) in AIR and state_at((x,y-1,z)) not in AIR:
      walkable.add((x,z))
   start=(d['Spawn'][0],d['Spawn'][2]);assert_true(start in walkable,'Maze spawn lacks safe2-high clearance/support')
   visited={start};queue=collections.deque([start])
   while queue:
    x,z=queue.popleft()
    for q in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]:
     if q in walkable and q not in visited:visited.add(q);queue.append(q)
   for room in rooms:
    x,ry,z=room['safe_waypoint'];assert_true(ry==y and (x,z) in visited,'Room waypoint unreachable from actual maze spawn: '+str(room['number']))
   challenge={tuple(r['challenge_chest']) for r in rooms}
   assert_true(len(challenge)==100,'Maze must have100 distinct chamber challenge chests')
   chestbypos={(c['x'],c['y'],c['z']):c for c in chests}
   assert_true(challenge<=set(chestbypos),'Room challenge chest does not exist in NBT')
   promenade=[c for c in chests if (c['x'],c['y'],c['z']) not in challenge]
   assert_true(len(promenade)==300,'Maze must have300 promenade restock chests')
   for chest in promenade:
    x,z=chest['x'],chest['z']
    assert_true(chest['y']==y and any(q in visited for q in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]),'Promenade chest has no reachable adjacency')
   pocketcount=0
   for room in rooms:
    chestpos=tuple(room['challenge_chest']);cx,cy,cz=chestpos
    assert_true(state_at((cx,cy+1,cz)) in AIR,'Challenge chest lid obstructed')
    pocket=room['reward_pocket']
    if room['gravity']=='down':
     assert_true(pocket is None,'Down room should not require an elevated gravity pocket')
     assert_true(cy==y and any(q in visited for q in [(cx-1,cz),(cx+1,cz),(cx,cz-1),(cx,cz+1)]),'Down chamber chest inaccessible')
     continue
    pocketcount+=1
    assert_true(pocket is not None and pocket['gravity']=='down','Non-down room reward needs safe normal-gravity pocket')
    lo,hi=pocket['min'],pocket['max']
    assert_true(cy==y+5 and all(a<=v<=b for a,v,b in zip(lo,chestpos,hi)),'Reward chest is not on elevated pocket')
    assert_true(tuple(pocket['chest'])==chestpos,'Pocket/challenge chest metadata disagree')
    for zone in zones:
     overlap=all(a<=d and c<=b for a,b,c,d in zip(lo,hi,zone['Min'],zone['Max']))
     assert_true(not overlap,'Reward pocket intersects an active gravity zone')
    approaches=[]
    for x,z in [(cx-1,cz),(cx+1,cz),(cx,cz-1),(cx,cz+1)]:
     if lo[0]<=x<=hi[0] and lo[2]<=z<=hi[2] and state_at((x,cy,z)) in AIR and state_at((x,cy+1,z)) in AIR and state_at((x,cy-1,z)) not in AIR:approaches.append((x,z))
    assert_true(approaches,'Gravity reward pocket lacks safe2-high chest approach')
   assert_true(pocketcount==80,'Expected80 elevated gravity reward pockets')
   geometry={'connected_room_graph':len(seen),'edges':edges,'independent_cycles':edges-99,'literal_air_walkable_cells_reached':len(visited),'promenade_chests_reachable':300,'challenge_chests':100,'elevated_reward_pockets':pocketcount,'reward_pockets_excluded_from_gravity_zones':True}

 elif 'pagoda' in path.stem:
  assert_true(len(chests)>=200,'Paired 25-floor pagodas need at least 200 chests')
  assert_true(len(final)==1,'Pagoda must have exactly one guaranteed final chest')
  assert_true(final[0]['y']==min(t['y'] for t in chests),'Final chest is not at the deepest chest level')
  assert_true(any(z['Gravity']=='up' for z in zones),'Pagoda has no inverted gravity zone')
  for c in chests:
   x,y,z=c['x'],c['y'],c['z']
   above=state_at((x,y+1,z))
   assert_true(above in AIR or above=='minecraft:chain','Pagoda native chest lid blocked by full block: '+str((x,y,z)))
  if metadata:
   for wing in ['upright','inverted']:
    floors=[r for r in metadata['rooms'] if r['wing']==wing]
    assert_true(sorted(r['floor'] for r in floors)==list(range(1,26)),'Pagoda missing one of25 floors in '+wing)
   geometry={'floors_per_wing':25,'final_at_lowest_chest_y':True}
 return {'size':size,'volume':count,'nonair':nonair,'chests':len(chests),'spawners':len(spawners),
  'gravity_zones':len(zones),'geometry':geometry,'loot_tiers':dict(lootcounts),'final_chest_positions':[[t[k] for k in ('x','y','z')] for t in final]}

def jar_checks(path,resources):
 import tomllib
 with zipfile.ZipFile(path) as z:
  names=set(z.namelist());metadata=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
  ids={m['modId'] for m in metadata['mods']}
  assert_true({'cuupsidedown','cumimic'}<=ids,'Merged jar missing legacy mod IDs')
  for cls in ['dev/unyielding/upsidedown/UpsideDown.class','dev/unyielding/mimic/MimicMod.class','dev/unyielding/upsidedown/dungeon/DungeonLootChecks.class']:
   assert_true(cls in names,'Merged jar missing class '+cls)
  assert_true(not any(n.endswith('.jar') for n in names),'Legacy jars must not be nested into merged jar')
  for name in TABLES:
   entry='data/cuupsidedown/loot_table/chests/'+name+'.json'
   assert_true(entry in names,'Jar missing loot table '+name)
   assert_true(json.loads(z.read(entry))==json.loads((resources/entry).read_text(encoding='utf-8-sig')),'Jar has stale loot table '+name)
  template_hashes={}
  import hashlib
  for name in ['sakura_labyrinth','mirror_pagoda']:
   entry='data/cuupsidedown/dungeons/'+name+'.nbt'
   assert_true(entry in names,'Jar missing dungeon template '+name)
   b=z.read(entry)
   assert_true(b==(resources/entry).read_bytes(),'Jar contains stale dungeon template '+name)
   template_hashes[name]=hashlib.sha256(b).hexdigest()
  return {'file':str(path),'mod_ids':sorted(ids),'loot_resources_match_source':True,'templates_match_source':True,'template_sha256':template_hashes}

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--project',type=Path,default=Path(__file__).resolve().parents[1]);ap.add_argument('--templates',type=Path);ap.add_argument('--mods',type=Path);ap.add_argument('--loot-only',action='store_true');ap.add_argument('--output',type=Path);ap.add_argument('--jar',type=Path)
 args=ap.parse_args();resources=args.project/'src/main/resources';result={'loot':loot_checks(resources,args.mods)}
 if not args.loot_only:
  templates=args.templates or resources/'data/cuupsidedown/dungeons'
  result['templates']={name:template_checks(templates/(name+'.nbt')) for name in ['sakura_labyrinth','mirror_pagoda']}
 if args.jar:result['jar']=jar_checks(args.jar,resources)
 result['status']='PASS';text=json.dumps(result,indent=2);print(text)
 if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text+'\n')
if __name__=='__main__':
 try:main()
 except (AssertionError,KeyError,ValueError,OSError) as exc:print('FAIL:',exc,file=sys.stderr);raise SystemExit(1)

