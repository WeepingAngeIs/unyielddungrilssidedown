#!/usr/bin/env python3
"""Rebuild the authored loot tables, or check them without writes with --check.
Independent installed-item checks: tools/validate_content.py --mods PATH.
"""
from pathlib import Path
import argparse,json,sys
parser=argparse.ArgumentParser()
parser.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'src/main/resources/data/cuupsidedown/loot_table/chests')
parser.add_argument('--check',action='store_true')
args=parser.parse_args();out=args.output
if not args.check:out.mkdir(parents=True,exist_ok=True)
def item(name, weight=1, lo=1, hi=None):
 e={'type':'minecraft:item','name':name,'weight':weight}
 if lo!=1 or hi is not None:
  e['functions']=[{'function':'minecraft:set_count','count':({'type':'minecraft:uniform','min':lo,'max':hi} if hi is not None and hi!=lo else lo),'add':False}]
 return e
def pool(entries,lo=1,hi=None,chance=None):
 p={'rolls':({'type':'minecraft:uniform','min':lo,'max':hi} if hi is not None else lo),'bonus_rolls':0,'entries':entries}
 if chance is not None:p['conditions']=[{'condition':'minecraft:random_chance','chance':chance}]
 return p
def cob(name,*a):return item('cobblemon:'+name,*a)
def mc(name,*a):return item('minecraft:'+name,*a)
def sword(name,*a):return item('simplyswords:'+name,*a)
balls=[cob('love_ball',4,3,7),cob('heal_ball',4,3,7),cob('dream_ball',2,2,5),cob('moon_ball',2,2,5),cob('luxury_ball',2,2,5)]
themes=[mc('cherry_sapling',3,1,3),mc('pink_petals',3,4,12),mc('pink_candle',2,2,5),mc('amethyst_shard',3,4,10),mc('glow_berries',2,5,12),mc('honey_bottle',2,2,4),mc('cookie',3,6,16)]
rare=[cob('rare_candy',6,1,3),cob('exp_candy_l',6,2,4),cob('max_revive',4,1,3),cob('full_restore',4,2,4),cob('shiny_stone',2),cob('dawn_stone',2),cob('moon_stone',2),cob('ability_capsule',1),mc('diamond',4,2,5),mc('golden_apple',3,1,2)]
uniques=[sword(n) for n in ['stars_edge','whisperwind','ribboncleaver','mjolnir','hearthflame','arcanethyst']]
tables={
'maze_common':{'type':'minecraft:chest','pools':[pool(balls,2,3),pool([cob('exp_candy_s',5,3,6),cob('exp_candy_m',3,1,3),cob('revive',3,1,2),cob('max_potion',2,1,2),mc('golden_carrot',3,3,7)],2,3),pool(themes,2,3)]},
'maze_rare':{'type':'minecraft:chest','pools':[pool(balls,2,3),pool(rare,3,4),pool(themes,2,3),pool(uniques,chance=0.01)]},
'pagoda_rare':{'type':'minecraft:chest','pools':[pool(rare,4,6),pool([cob('ultra_ball',5,4,9),cob('dusk_ball',3,3,7),cob('dream_ball',2,3,6),cob('luxury_ball',2,3,6)],2,3),pool([mc('amethyst_shard',2,8,16),mc('gold_ingot',3,4,9),mc('emerald',3,4,9),cob('exp_candy_xl',2,1,2)],1,2),pool(uniques,chance=0.005)]},
'pagoda_final':{'type':'minecraft:chest','pools':[pool([cob('master_ball',4),cob('ancient_origin_ball',4)]+uniques),pool([cob('rare_candy',4,4,8),cob('exp_candy_xl',4,3,6),cob('ability_patch',2),cob('ability_capsule',3),mc('netherite_ingot',2,1,2),mc('enchanted_golden_apple',2,1,2),mc('diamond',3,6,12)],4,5),pool([cob('max_revive',2,3,6),cob('full_restore',2,4,8),cob('ultra_ball',3,12,24),cob('dream_ball',2,6,12),cob('luxury_ball',2,6,12)],3,4),pool([mc('cherry_sapling',1,2,4),mc('pink_petals',1,8,16),mc('amethyst_shard',1,12,24)],1)]}}

g=tables['pagoda_final']['pools'][0]
assert g['rolls']==1 and len(g['entries'])==8 and not g.get('conditions')
assert all(e['type']=='minecraft:item' and not e.get('conditions') and not e.get('functions') for e in g['entries'])
differences=[]
for name,table in tables.items():
 path=out/(name+'.json');content=json.dumps(table,indent=2)+'\n'
 if args.check:
  if not path.exists() or json.loads(path.read_text(encoding='utf-8-sig'))!=table:differences.append(name)
 else:path.write_text(content,encoding='utf-8')
if differences:print('DIFFERENT OR MISSING: '+', '.join(differences),file=sys.stderr);raise SystemExit(1)
print(('PASS: all four authored loot tables match.' if args.check else 'Wrote four loot tables. Final pool: one unconditional high-end reward.'))
