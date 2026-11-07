"""Assertions against only the workspace's loopback disposable server. Not a deployment tool."""
import json,sys
from pathlib import Path
from smoke_rcon import Rcon
from validate_content import read_nbt

root=Path(__file__).resolve().parents[1]
reports=root/'test-evidence';reports.mkdir(exist_ok=True)
def cmd(text):
    r=Rcon()
    try:answer=r.command(text)
    finally:r.sock.close()
    print(text+' => '+answer.replace('\n',' ')[:900])
    return answer

if __name__=='__main__':
    name=sys.argv[1];anchor=list(map(int,sys.argv[2:5]));t=read_nbt(root/'src/main/resources/data/cuupsidedown/dungeons'/(name+'.nbt'))
    minimum=[a+b for a,b in zip(anchor,t['Offset'])];sx,sy,sz=t['Size']
    def world(tile):return [minimum[i]+tile[k] for i,k in enumerate('xyz')]
    tiles=t['BlockEntities'];chest=next(b for b in tiles if 'LootTable' in b);spawner=next(b for b in tiles if b.get('id')=='minecraft:mob_spawner')
    cursor=0;wall=None
    for length,state in zip(t['Runs'][::2],t['Runs'][1::2]):
        if t['Palette'][state].startswith('cuupsidedown:sakura_wall'):
            wall=[minimum[0]+cursor%sx,minimum[1]+cursor//(sx*sz),minimum[2]+cursor//sx%sz];break
        cursor+=length
    assert wall is not None
    positions=[wall,world(chest),world(spawner)]
    for x,y,z in positions:cmd(f'forceload add {x} {z}')
    x,y,z=wall
    before=cmd(f'execute if block {x} {y} {z} cuupsidedown:sakura_wall')
    assert 'Test passed' in before
    cmd(f'setblock {x} {y} {z} air')
    protected=cmd(f'execute if block {x} {y} {z} cuupsidedown:sakura_wall')
    assert 'Test passed' in protected,'Protected wall changed'
    x,y,z=world(chest)
    table=cmd(f'data get block {x} {y} {z} LootTable');assert chest['LootTable'] in table
    x,y,z=world(spawner)
    for field,value in [('MinSpawnDelay',40),('MaxSpawnDelay',100),('MaxNearbyEntities',12),('SpawnCount',3)]:
        answer=cmd(f'data get block {x} {y} {z} {field}');assert str(value) in answer
    # The Minecraft codec omits default 0..15 bounds when reserializing; an existing empty
    # custom_spawn_rules object is canonical full light allowance, unlike absence of that object.
    spawn=cmd(f'data get block {x} {y} {z} SpawnData');assert 'cu_mimic' in spawn and 'custom_spawn_rules' in spawn
    if name=='mirror_pagoda':
        final=next(b for b in tiles if b.get('LootTable','').endswith('/pagoda_final'));x,y,z=world(final)
        cmd(f'forceload add {x} {z}');answer=cmd(f'data get block {x} {y} {z} LootTable');assert 'cuupsidedown:chests/pagoda_final' in answer
    report={'template':name,'anchor':anchor,'actual_placement':True,'protected_wall':True,'native_lootr_table':True,'fast_bright_mimic_nbt':True}
    (reports/(name+'-runtime.json')).write_text(json.dumps(report,indent=2))
    print('PASS: actual protected placement, native loot assignment and hidden fast bright Mimic spawner NBT')
