#!/usr/bin/env python3
"""Hosted, disposable NeoForge integration smoke. Refuses execution outside GitHub Linux.

Prerequisites: Java 21, repository Gradle wrapper, the six pinned dependencies in
run/mods. Run `python tools/hosted_smoke.py --scenario legacy` or select
`--scenario direct` for the native placement check on a fresh
GitHub-hosted Ubuntu runner. No real profile, server account, or credentials used.
The fixture is deliberately playerless: client physics and two-player Lootr
opening remain separate tests, never claimed by this controller.
"""
from __future__ import annotations
import argparse,array,collections,gzip,hashlib,json,os,re,secrets,signal,struct,subprocess,sys,time,traceback,zlib
from pathlib import Path
from smoke_rcon import Rcon
from validate_content import Reader,read_nbt

ROOT=Path(__file__).resolve().parents[1]
RUN=ROOT/'run';WORLD=RUN/'hosted-smoke-world';EVIDENCE=ROOT/'test-evidence'
TEMPLATES=ROOT/'src/main/resources/data/cuupsidedown/dungeons'
ANCHORS={'sakura_labyrinth':(1024,64,1024),'mirror_pagoda':(1536,64,1024)}
AIR={'minecraft:air','minecraft:cave_air','minecraft:void_air'}
STATE_RE=re.compile(r'^([A-Z_]+) ([a-f0-9]{12}) (\d+)%;')

def require(ok,message):
    if not ok:raise AssertionError(message)

def canonical(state):
    if '[' not in state:return state
    name,props=state[:-1].split('[',1)
    return name+'['+','.join(sorted(props.split(',')))+']'

def nbt_from_bytes(raw):
    r=Reader(raw);kind=r.num('B');r.string();result=r.val(kind)
    require(r.p==len(raw),'Trailing chunk NBT data')
    return result

def replace_nbt_string(raw,path,expected,replacement):
    """Change one known string payload; preserve every other original NBT byte/type."""
    r=Reader(raw);kind=r.num('B');r.string();found=[]
    def visit(kind,here):
        if kind==8:
            start=r.p;value=r.string()
            if here==path:found.append((start,r.p,value))
        elif kind==10:
            while (child:=r.num('B'))!=0:visit(child,here+(r.string(),))
        elif kind==9:
            child=r.num('B');length=r.num('i')
            for index in range(length):visit(child,here+(index,))
        elif kind in (7,11,12):r.take(r.num('i')*{7:1,11:4,12:8}[kind])
        elif kind in (1,2,3,4,5,6):r.take({1:1,2:2,3:4,4:8,5:4,6:8}[kind])
        else:raise ValueError('Unsupported NBT tag in fixture: '+str(kind))
    visit(kind,());require(r.p==len(raw),'Trailing data in journal fixture')
    require(len(found)==1 and found[0][2]==expected,'Journal fixture string/path differs from expected')
    start,end,_=found[0];value=replacement.encode('utf-8')
    return raw[:start]+struct.pack('>H',len(value))+value+raw[end:]

class RegionReader:
    def __init__(self,folder):self.folder=folder;self.handles={}
    def chunk(self,x,z):
        path=self.folder/f'r.{x//32}.{z//32}.mca'
        if not path.exists():return None
        if path not in self.handles:self.handles[path]=path.open('rb')
        f=self.handles[path];f.seek(4*((x%32)+(z%32)*32));loc=int.from_bytes(f.read(4),'big');sector=loc>>8
        if not sector:return None
        f.seek(sector*4096);length=int.from_bytes(f.read(4),'big');require(1<=length<=256*1024*1024,'Invalid region length')
        kind=f.read(1)[0]
        if kind&128:raw=(self.folder/f'c.{x}.{z}.mcc').read_bytes();kind&=127
        else:raw=f.read(length-1)
        require(kind in (1,2,3),f'Unsupported chunk compression {kind}')
        raw=gzip.decompress(raw) if kind==1 else zlib.decompress(raw) if kind==2 else raw
        d=nbt_from_bytes(raw);return d.get('Level',d)
    def close(self):
        for f in self.handles.values():f.close()

class Controller:
    def __init__(self,seconds):
        self.start=time.monotonic();self.hard_deadline=self.start+seconds
        self.deadline=self.hard_deadline-35 # Reserve clean-stop time within the same 40-minute limit.
        self.proc=None;self.log_handle=None;self.boots=0;self.instances={};self.loaded={};self.events=[];self.interrupted=set()
        self.report={'passed':False,'scope':'playerless GitHub-hosted integration; not client physics or two-player Lootr opening','steps':[],'anchors':ANCHORS}
        EVIDENCE.mkdir(exist_ok=True)
    def remaining(self,cleanup=False):
        return max(0,(self.hard_deadline if cleanup else self.deadline)-time.monotonic())
    def check(self):
        if not self.remaining():raise TimeoutError('Hosted smoke reached its total time budget')
        if self.proc and self.proc.poll() is not None:raise RuntimeError(f'Server process exited unexpectedly with {self.proc.returncode}')
    def event(self,name,**data):
        record={'elapsed_s':round(time.monotonic()-self.start,2),'event':name,**data};self.events.append(record)
        with (EVIDENCE/'hosted-smoke-events.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(record)+'\n')
        print(json.dumps(record),flush=True)
    def command(self,text,timeout=30,record=True):
        self.check();r=Rcon(RUN/'server.properties',timeout=max(.2,min(timeout,self.remaining())))
        try:answer=r.command(text)
        finally:r.sock.close()
        if record:self.event('rcon',command=text,result=answer[:1600])
        return answer
    def wait_success(self,text,label,seconds=10):
        """Observe command readiness across ticks, never treat elapsed time as success."""
        start=time.monotonic();end=min(self.deadline,start+seconds);attempts=0;last=''
        while time.monotonic()<end:
            attempts+=1;last=self.command(text,timeout=max(.2,min(2,end-time.monotonic())),record=False)
            if 'Test passed' in last:
                self.event('readiness_verified',check=label,attempts=attempts,wait_s=round(time.monotonic()-start,3))
                return last
            time.sleep(min(.25,max(0,end-time.monotonic())))
        raise TimeoutError(f'{label} not ready within {seconds}s; last response {last!r}')
    def prepare(self):
        require(os.environ.get('GITHUB_ACTIONS')=='true' and sys.platform=='linux','This integration controller runs only on GitHub Actions Linux; local game/build execution is forbidden')
        require(not WORLD.exists(),'Refusing to reuse or erase an existing smoke world')
        mods=sorted((RUN/'mods').glob('*.jar'));require(len(mods)==6,f'Expected exactly six pinned dependency mods in run/mods, found {len(mods)}')
        self.report['dependencies']=[{'name':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in mods]
        for name,anchor in ANCHORS.items():
            t=read_nbt(TEMPLATES/(name+'.nbt'));t['Minimum']=[a+b for a,b in zip(anchor,t['Offset'])]
            t['CanonicalPalette']=[canonical(s) for s in t['Palette']]
            expected=array.array('H')
            for n,p in zip(t['Runs'][::2],t['Runs'][1::2]):expected.extend(array.array('H',[p])*n)
            require(len(expected)==t['Size'][0]*t['Size'][1]*t['Size'][2],'Invalid template run length')
            t['Expected']=expected;self.loaded[name]=t
        # A test-only datapack overrides Overworld height; it is not shipped in the mod.
        pack=WORLD/'datapacks/hosted-height';(pack/'data/minecraft/dimension_type').mkdir(parents=True)
        (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'CI fixture only: expanded-height empty world'}}),encoding='utf-8')
        dtype={'ambient_light':0.0,'bed_works':True,'coordinate_scale':1.0,'effects':'minecraft:overworld','has_ceiling':False,'has_raids':True,'has_skylight':True,'height':1024,'infiniburn':'#minecraft:infiniburn_overworld','logical_height':1024,'min_y':-512,'monster_spawn_block_light_limit':0,'monster_spawn_light_level':{'type':'minecraft:uniform','min_inclusive':0,'max_inclusive':7},'natural':True,'piglin_safe':False,'respawn_anchor_works':False,'ultrawarm':False}
        (pack/'data/minecraft/dimension_type/overworld.json').write_text(json.dumps(dtype),encoding='utf-8')
        props={'server-ip':'127.0.0.1','server-port':'25595','enable-rcon':'true','rcon.port':'25596','rcon.password':secrets.token_hex(24),'broadcast-rcon-to-ops':'false','online-mode':'false','enforce-secure-profile':'false','white-list':'true','max-players':'1','motd':'Disposable hosted dungeon smoke','level-name':WORLD.name,'level-seed':'20261107','level-type':'minecraft:flat','generator-settings':json.dumps({'layers':[{'block':'minecraft:air','height':1}],'biome':'minecraft:the_void','lakes':False,'features':False,'structure_overrides':[]},separators=(',',':')),'initial-enabled-packs':'vanilla,file/hosted-height','generate-structures':'false','spawn-protection':'0','difficulty':'normal','gamemode':'creative','view-distance':'2','simulation-distance':'2','allow-flight':'true','enable-command-block':'false','max-tick-time':'120000','sync-chunk-writes':'true'}
        RUN.mkdir(exist_ok=True);(RUN/'server.properties').write_text(''.join(f'{k}={v}\n' for k,v in props.items()),encoding='utf-8')
        (RUN/'server.properties').chmod(0o600)
        (RUN/'eula.txt').write_text('eula=true\n',encoding='utf-8')
        self.event('fixture_created',world=WORLD.name,height=[-512,511],dependency_count=6)
    def boot(self):
        self.boots+=1;path=EVIDENCE/f'hosted-server-{self.boots}.log';self.log_handle=path.open('w',encoding='utf-8')
        args=['bash','./gradlew','--no-daemon','--console=plain','-Dorg.gradle.jvmargs=-Xmx1536m','-I','tools/hosted-smoke.init.gradle','runServer']
        self.proc=subprocess.Popen(args,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=self.log_handle,stderr=subprocess.STDOUT,start_new_session=True)
        self.event('server_start',boot=self.boots)
        end=min(self.deadline,time.monotonic()+420);last_error='not started'
        while time.monotonic()<end:
            self.check()
            try:
                answer=self.command('dungeon list',timeout=3,record=False)
                if 'Templates: sakura_labyrinth' in answer:break
                last_error=answer
            except (OSError,EOFError,RuntimeError) as e:last_error=type(e).__name__+': '+str(e)
            time.sleep(2)
        else:raise TimeoutError('Server/RCON not ready within startup bound: '+last_error)
        self.event('server_ready',boot=self.boots)
        for cmd in ['gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule randomTickSpeed 0','gamerule spawnChunkRadius 0','time set noon','weather clear']:
            self.command(cmd)
    def stop(self,cleanup=False):
        if not self.proc:return
        if self.proc.poll() is None:
            try:
                r=Rcon(RUN/'server.properties',timeout=min(10,max(.2,self.remaining(True))))
                try:r.command('stop')
                finally:r.sock.close()
            except (OSError,EOFError,RuntimeError):pass
            try:self.proc.wait(timeout=max(.1,min(30 if cleanup else 75,self.remaining(True))))
            except subprocess.TimeoutExpired:
                os.killpg(self.proc.pid,signal.SIGTERM)
                try:self.proc.wait(timeout=min(3,max(.1,self.remaining(True))))
                except subprocess.TimeoutExpired:os.killpg(self.proc.pid,signal.SIGKILL);self.proc.wait(timeout=3)
                if not cleanup:raise RuntimeError('Server did not stop cleanly; forcibly terminated')
        code=self.proc.returncode;self.proc=None
        if self.log_handle:self.log_handle.close();self.log_handle=None
        self.event('server_stopped',exit_code=code,cleanup=cleanup)
        if not cleanup:require(code==0,f'Gradle/server exited nonzero on clean stop: {code}')
    def wait_phase(self,wanted,instance=None,max_seconds=420):
        end=min(self.deadline,time.monotonic()+max_seconds);last='';last_logged=''
        while time.monotonic()<end:
            text=self.command('dungeon status',record=False);match=STATE_RE.search(text)
            if match:
                phase,i,percent=match.groups();last=f'{phase} {percent}%'
                if last!=last_logged:self.event('phase',instance=i,phase=phase,percent=int(percent));last_logged=last
                if instance:require(i==instance,'Active job unexpectedly changed identity')
                if phase==wanted:
                    token = re.search(r'preview token ([a-f0-9]{12})', text)
                    require(phase!='READY' or token is not None,'Ready preview is missing its confirmation token')
                    return i,token[1] if token else None
                if phase=='READY' and wanted=='IDLE':
                    raise AssertionError('Placement stalled waiting for an unexpected confirmation')
            else:
                if wanted=='IDLE' and 'No placement job.' in text:return None,None
                if 'No placement job.' in text:raise RuntimeError('Job vanished before expected '+wanted+'; inspect server log')
                last=text
            time.sleep(2)
        raise TimeoutError('Timed out waiting for '+wanted+'; last status '+last)
    def journal(self):
        p=WORLD/'data/cu_dungeons/placements.nbt'
        return read_nbt(p).get('Entries',[]) if p.exists() else []
    def zones(self):
        p=WORLD/'data/cuupsidedown_zones.dat'
        if not p.exists():return []
        d=read_nbt(p);return d.get('data',d).get('zones',[])
    def assert_state(self,stage,expected):
        entries=self.journal();require(len(entries)==len(expected),f'{stage}: journal count differs')
        indexed={e['Id']:e for e in entries};zones=self.zones()
        for name,i in expected.items():
            status='BUILDING' if name in self.interrupted else 'PLACED'
            require(i in indexed and indexed[i]['Status']==status and indexed[i]['Template']==name,f'{stage}: missing {status} entry for {name}')
            actual=[z for z in zones if z['name'].startswith('dungeon/'+i+'/')];t=self.loaded[name]
            if name in self.interrupted:
                require(not actual,f'{stage}: unfinished dungeon retained stale gravity zones')
                continue
            require(len(actual)==len(t['Zones']),f'{stage}: gravity zone count differs for {name}')
            az={z['name'].split('/')[-1]:z for z in actual}
            for z in t['Zones']:
                got=az[z['Name']];require(got['gravity']==z['Gravity'],f'{stage}: gravity changed')
                for axis,n in zip('xyz',range(3)):
                    require(got[axis+'0']==t['Minimum'][n]+z['Min'][n] and got[axis+'1']==t['Minimum'][n]+z['Max'][n]+1,f'{stage}: gravity bounds changed')
        require(len(zones)==sum(len(self.loaded[n]['Zones']) for n in expected if n not in self.interrupted),f'{stage}: leftover or unexpected gravity zones')
        result={'stage':stage,'placements':len(entries),'zones':len(zones),'statuses':[e['Status'] for e in entries]}
        (EVIDENCE/(stage+'-persistence.json')).write_text(json.dumps(result,indent=2),encoding='utf-8');self.event('persistent_state_verified',**result)
    def place(self,name):
        anchor=ANCHORS[name];out=self.command('dungeon preview '+name+' '+' '.join(map(str,anchor)))
        require('PREVIEW ONLY:' in out,'Preview refused: '+out)
        i,token=self.wait_phase('READY');self.instances[name]=i
        require(i not in {e['Id'] for e in self.journal()},'Preview unexpectedly journaled a placement')
        # No block write is permitted before confirmation. Probe future structural cells.
        for p in self.probes(name):
            ans=self.command('execute if block '+' '.join(map(str,p))+' minecraft:air')
            require('Test passed' in ans,'Preview changed an original air sample')
        out=self.command('dungeon confirm '+token);require('Confirmation accepted.' in out,'Confirmation rejected: '+out)
        self.wait_phase('IDLE',i,max_seconds=600)
        self.assert_state('placed-'+name,self.instances)
        self.assert_backup(name,i)
        self.event('placement_verified',template=name,instance=i)
    def probes(self,name):
        t=self.loaded[name];sx,sy,sz=t['Size'];m=t['Minimum'];result=[];cursor=0
        for length,p in zip(t['Runs'][::2],t['Runs'][1::2]):
            if t['Palette'][p].startswith('cuupsidedown:sakura_wall'):
                result.append([m[0]+cursor%sx,m[1]+cursor//(sx*sz),m[2]+cursor//sx%sz]);break
            cursor+=length
        for entity_id in ['lootr:lootr_chest','minecraft:mob_spawner']:
            tile=next(v for v in t['BlockEntities'] if v['id']==entity_id);result.append([m[n]+tile[k] for n,k in enumerate('xyz')])
        return result
    def assert_backup(self,name,i):
        p=WORLD/f'data/cu_dungeons/{i}.before.nbt';b=read_nbt(p);t=self.loaded[name]
        require(b['Size']==t['Size'] and b['Min']==t['Minimum'],'Backup bounds changed')
        require(sum(b['Runs'][::2])==len(t['Expected']),'Backup incomplete')
        require(all(b['Palette'][v] in AIR for v in b['Runs'][1::2]),'Original world was not completely air inside tested bounds')
        self.event('backup_verified',template=name,cells=len(t['Expected']),sha256=hashlib.sha256(p.read_bytes()).hexdigest())
    def runtime(self,stage):
        for name,anchor in ANCHORS.items():
            self.check();log=EVIDENCE/f'{stage}-{name}-runtime.log'
            with log.open('w',encoding='utf-8') as f:
                subprocess.run([sys.executable,'tools/run_smoke_checks.py',name,*map(str,anchor)],cwd=ROOT,stdout=f,stderr=subprocess.STDOUT,check=True,timeout=min(120,self.remaining()))
            report=EVIDENCE/(name+'-runtime.json');require(report.exists(),'Runtime report missing')
            (EVIDENCE/f'{stage}-{name}-runtime.json').write_bytes(report.read_bytes())
            self.event('runtime_checks_passed',stage=stage,template=name)
        # Probe the live in-memory gravity index, including after reload. Markers
        # have no collision/physics; remove them before any placement or undo.
        for name,t in self.loaded.items():
            representatives={}
            for zone in t['Zones']:representatives.setdefault(zone['Gravity'],zone)
            for gravity,z in representatives.items():
                pos=[t['Minimum'][n]+(z['Min'][n]+z['Max'][n]+1)/2 for n in range(3)]
                xyz=' '.join(map(str,pos));x,y,zz=map(int,pos)
                self.command(f'forceload add {x} {zz}')
                self.wait_success(f'execute if loaded {x} {y} {zz}','probe chunk '+name+'/'+gravity)
                # Summon succeeds before entity sections are necessarily selector-
                # visible. A unique marker plus observed registration avoids both
                # that race and accidentally reading a prior probe's entity.
                tag='cu_ci_probe_'+secrets.token_hex(6)
                selector='@e[type=minecraft:marker,tag='+tag+',limit=1]'
                try:
                    result=self.command('summon minecraft:marker '+xyz+' {Tags:["cu_ci_zone_probe","'+tag+'"]}')
                    require('Summoned' in result or 'summoned' in result,'Gravity probe marker could not spawn')
                    self.wait_success('execute if entity '+selector,'probe marker '+name+'/'+gravity)
                    result=self.command('execute as '+selector+' at @s run gravity room')
                    if name in self.interrupted:
                        require('Not in a gravity zone (normal gravity).' in result,'Interrupted dungeon still has live gravity')
                    else:
                        require('dungeon/'+self.instances[name]+'/'+z['Name'] in result and '(gravity '+gravity+')' in result,'Live gravity index did not return the expected zone/direction')
                    if name not in self.interrupted:
                        for mob in ('bat', 'pig'):
                            mobtag='cu_ci_mob_'+secrets.token_hex(6)
                            mobsel='@e[tag='+mobtag+',limit=1]'
                            try:
                                self.command('summon minecraft:'+mob+' '+xyz+' {Tags:["'+mobtag+'"],NoAI:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b}')
                                self.wait_success('execute if entity '+mobsel,'mob regression spawn')
                                time.sleep(3)
                                self.check()
                                self.wait_success('execute if entity '+mobsel,'rotated living entity survives ticking')
                                self.event('living_gravity_regression',stage=stage,gravity=gravity,mob=mob)
                            finally:self.command('kill '+mobsel,record=False)
                    self.event('live_gravity_verified',stage=stage,template=name,gravity='normal-after-recovery' if name in self.interrupted else gravity)
                finally:self.command('kill '+selector,record=False)
    def simulate_interrupted_state(self,name):
        """A journal-state regression fixture, explicitly not a real crash simulation."""
        require(self.proc is None,'Interrupted-state fixture requires a cleanly stopped server')
        require(not self.interrupted,'Only one interrupted-state fixture is permitted')
        entries=self.journal();i=self.instances[name]
        indices=[n for n,e in enumerate(entries) if e['Id']==i];require(len(indices)==1,'Selected instance missing from journal')
        require(entries[indices[0]]['Status']=='PLACED','Fixture must start from a complete placement')
        path=WORLD/'data/cu_dungeons/placements.nbt';before=path.read_bytes();raw=gzip.decompress(before)
        replacement=replace_nbt_string(raw,('Entries',indices[0],'Status'),'PLACED','BUILDING')
        original=nbt_from_bytes(raw);changed=nbt_from_bytes(replacement)
        changed['Entries'][indices[0]]['Status']='PLACED';require(changed==original,'Fixture changed more than selected status')
        backup=WORLD/f'data/cu_dungeons/{i}.before.nbt';backup_hash=hashlib.sha256(backup.read_bytes()).hexdigest()
        tmp=path.with_suffix('.ci-fixture.tmp');tmp.write_bytes(gzip.compress(replacement,mtime=0));os.replace(tmp,path)
        self.interrupted.add(name)
        report={'fixture':'simulated interrupted journal state, not process crash','template':name,'instance':i,'status_change':['PLACED','BUILDING'],'all_other_nbt_bytes_preserved':True,'backup_sha256':backup_hash,'retained_stale_zones_before_boot':len([z for z in self.zones() if z['name'].startswith('dungeon/'+i+'/')])}
        require(report['retained_stale_zones_before_boot']==len(self.loaded[name]['Zones']),'Recovery fixture lacks stale zones')
        (EVIDENCE/'interrupted-state-fixture.json').write_text(json.dumps(report,indent=2),encoding='utf-8');self.event('interrupted_state_fixture_created',**report)
        return backup_hash
    def audit_disk(self,stage,air=False,originals=None):
        require(self.proc is None,'Offline audit requires cleanly stopped server')
        region=RegionReader(WORLD/'region');summary={}
        try:
            for name,t in self.loaded.items():
                self.check();mx,my,mz=t['Minimum'];sx,sy,sz=t['Size'];maxx,maxy,maxz=mx+sx-1,my+sy-1,mz+sz-1
                states=t['CanonicalPalette'];expected=t['Expected'];cells=chunks=0;found_be={};mismatches=[]
                original_states,original_tiles = originals[name] if originals else ({}, {})
                for cx in range(mx//16,maxx//16+1):
                    for cz in range(mz//16,maxz//16+1):
                        self.check();chunk=region.chunk(cx,cz);require(chunk is not None,f'Missing saved chunk {cx},{cz} after completed placement/undo')
                        chunks+=1;sections={s['Y']:s for s in chunk.get('sections',[])}
                        for be in chunk.get('block_entities',[]):
                            x,y,z=(be.get(k) for k in 'xyz')
                            if x is not None and mx<=x<=maxx and my<=y<=maxy and mz<=z<=maxz:found_be[(x,y,z)]=be
                        for sec_y in range(my//16,maxy//16+1):
                            section=sections.get(sec_y,{});bs=section.get('block_states',{});palette=bs.get('palette',[{'Name':'minecraft:air'}])
                            names=[p['Name']+('['+','.join(f'{k}={v}' for k,v in sorted(p.get('Properties',{}).items()))+']' if p.get('Properties') else '') for p in palette]
                            packed=bs.get('data',[]);bits=max(4,(len(names)-1).bit_length());per=64//bits;mask=(1<<bits)-1
                            require(len(names)==1 or len(packed)>0,'Missing packed section data')
                            for y in range(max(my,sec_y*16),min(maxy,sec_y*16+15)+1):
                                for z in range(max(mz,cz*16),min(maxz,cz*16+15)+1):
                                    for x in range(max(mx,cx*16),min(maxx,cx*16+15)+1):
                                        idx=(y%16)*256+(z%16)*16+(x%16)
                                        pi=0 if len(names)==1 else ((packed[idx//per]&((1<<64)-1))>>((idx%per)*bits))&mask
                                        got=names[pi];want=original_states.get((x,y,z),'minecraft:air') if air else states[expected[(y-my)*sx*sz+(z-mz)*sx+x-mx]]
                                        if got!=want and not(got in AIR and want in AIR):
                                            if len(mismatches)<8:mismatches.append({'position':[x,y,z],'expected':want,'actual':got})
                                        cells+=1
                require(not mismatches,f'{stage}: block mismatches in {name}: {mismatches}')
                if air:require(found_be==original_tiles,f'{stage}: original block entities, contents or components differ in {name}')
                else:
                    require(len(found_be)==len(t['BlockEntities']),f'{stage}: block entity count differs in {name}')
                    for expected_be in t['BlockEntities']:
                        pos=tuple(t['Minimum'][n]+expected_be[k] for n,k in enumerate('xyz'));be=found_be[pos]
                        require(be['id']==expected_be['id'],f'{stage}: BE type changed at {pos}')
                        if 'LootTable' in expected_be:require(be.get('LootTable')==expected_be['LootTable'],f'{stage}: loot table changed at {pos}')
                        if expected_be['id']=='minecraft:mob_spawner':
                            for key in ['MinSpawnDelay','MaxSpawnDelay','SpawnCount','MaxNearbyEntities','RequiredPlayerRange','SpawnRange']:
                                require(be.get(key)==expected_be[key],f'{stage}: spawner {key} changed at {pos}')
                            require('cu_mimic' in be['SpawnData']['entity']['Tags'] and 'custom_spawn_rules' in be['SpawnData'],f'{stage}: Mimic/light rules missing')
                summary[name]={'cells_verified':cells,'saved_chunks':chunks,'block_entities_verified':len(found_be),'all_original_air':air and not original_states,'original_nonair_verified':len(original_states) if air else 0}
                self.event('full_saved_world_verified',stage=stage,template=name,**summary[name])
        finally:region.close()
        (EVIDENCE/(stage+'-full-world-audit.json')).write_text(json.dumps(summary,indent=2),encoding='utf-8');self.report['steps'].append({'stage':stage,'full_world_audit':summary})
    def undo(self,name,stage_prefix=''):
        i=self.instances[name];out=self.command('dungeon undo '+i);require('UNDO PREVIEW:' in out,'Undo preview refused: '+out)
        _,token=self.wait_phase('READY',i);out=self.command('dungeon confirm '+token);require('Restoring the original terrain' in out,'Undo confirmation refused')
        self.wait_phase('IDLE',i,max_seconds=420)
        expected={n:j for n,j in self.instances.items() if n!=name};self.assert_state(stage_prefix+'undone-'+name,expected)
        # Region protection must actually be removed, not just its journal entry.
        p=self.probes(name)[0];xyz=' '.join(map(str,p));self.command('forceload add '+str(p[0])+' '+str(p[2]))
        require('Test passed' in self.command('execute if block '+xyz+' minecraft:air'),'Restored probe is not air')
        self.command('setblock '+xyz+' minecraft:stone');require('Test passed' in self.command('execute if block '+xyz+' minecraft:stone'),'Protection persisted after undo')
        self.command('setblock '+xyz+' minecraft:air');require('Test passed' in self.command('execute if block '+xyz+' minecraft:air'),'Air reset failed')
        del self.instances[name];self.event('undo_verified',template=name,instance=i,placement='native' if stage_prefix else 'preview')
    def seed_obstructions(self):
        originals = {}
        for name, template in self.loaded.items():
            mx, my, mz = template['Minimum']
            x, y, z = mx + 6, my + 4, mz + 6
            chest = (x + 3, y, z)
            chest_at = ' '.join(map(str, chest))
            self.command(f'forceload add {x-1} {z-1} {x+3} {z+1}')
            for px, pz in ((x-1, z-1), (x+3, z+1)):
                self.wait_success(f'execute if loaded {px} {y} {pz}', 'fixture chunk '+name)
            # The obsidian shell keeps lava stable before placement and after undo.
            self.command(f'fill {x-1} {y-1} {z-1} {x+1} {y+1} {z+1} minecraft:obsidian')
            self.command(f'setblock {x} {y} {z} minecraft:lava[level=0]')
            self.command(f'setblock {x+2} {y} {z} cuupsidedown:zen_wall')
            self.command('setblock '+chest_at+' minecraft:chest[facing=east]{Items:[{Slot:4b,id:"minecraft:diamond",count:7,components:{"minecraft:custom_data":{proof:"direct placement"}}}]}')
            self.wait_success(f'execute if block {x} {y} {z} minecraft:lava[level=0]', 'source lava '+name)
            self.wait_success(f'execute if block {x+2} {y} {z} cuupsidedown:zen_wall', 'mod block '+name)
            self.wait_success('execute if data block '+chest_at+' Items[{Slot:4b,id:"minecraft:diamond",count:7}]', 'original chest inventory '+name)
            states = {(px, py, pz): 'minecraft:obsidian'
                      for px in range(x-1, x+2) for py in range(y-1, y+2) for pz in range(z-1, z+2)}
            states[x, y, z] = 'minecraft:lava[level=0]'
            states[x+2, y, z] = 'cuupsidedown:zen_wall'
            states[chest] = 'minecraft:chest[facing=east,type=single,waterlogged=false]'
            originals[name] = (states, {chest: None})
        self.command('save-all flush')
        region = RegionReader(WORLD/'region')
        try:
            for name, (_, tiles) in originals.items():
                (position,) = tiles
                x, _, z = position
                chunk = region.chunk(x//16, z//16)
                chest = next(tile for tile in chunk['block_entities'] if tuple(tile[k] for k in 'xyz') == position)
                item, = chest['Items']
                require(chest['id'] == 'minecraft:chest' and item['Slot'] == 4 and item['count'] == 7
                        and item['id'] == 'minecraft:diamond'
                        and item['components']['minecraft:custom_data']['proof'] == 'direct placement',
                        'Original inventory fixture is incomplete for '+name)
                tiles[position] = chest
                self.event('original_inventory_recorded', template=name, position=position)
        finally:
            region.close()
        return originals

    def verify_no_item_drops(self, name, originals):
        (chest,) = originals[name][1]
        xyz = ' '.join(map(str, chest))
        self.wait_success('execute positioned '+xyz+' unless entity @e[type=minecraft:item,distance=..12]',
                          'original inventory was not dropped '+name)

    def place_direct(self, name):
        x, y, z = ANCHORS[name]
        self.command(f'forceload add {x} {z}')
        self.wait_success(f'execute if loaded {x} {y} {z}', 'direct placement anchor '+name)
        structure = 'place structure cuupsidedown:'+name
        marker = '@e[type=minecraft:marker,tag=direct_dungeon_builder,limit=1]'
        default_position = name == 'sakura_labyrinth'
        if default_position:
            self.command(f'summon minecraft:marker {x}.5 {y} {z}.5 {{Tags:["direct_dungeon_builder"]}}')
            self.wait_success('execute if entity '+marker, 'builder at the requested origin')
            native_command = 'execute as '+marker+' at @s run '+structure
        else:
            native_command = f'{structure} {x} {y} {z}'
        try:
            queued = self.command(native_command)
            match = re.search(r'Direct placement started: ([a-f0-9]{12})\.', queued)
            require(match is not None, 'Native placement did not start from one command: '+queued)
            instance = match[1]
            self.wait_phase('IDLE', instance, max_seconds=600)
            self.instances[name] = instance
            self.assert_state('direct-placed-'+name, self.instances)
            require(any(e['Id'] == instance and e.get('Direct') for e in self.journal()),
                    'Direct placement missing from durable journal')
            self.event('direct_placement_verified', template=name, instance=instance,
                       default_position=default_position, command=native_command)
        finally:
            if default_position:
                self.command('kill '+marker)

    def direct_scenario(self):
        self.boot()
        result = self.command('dungeon verifyloot', timeout=90)
        require('Dungeon loot verified:' in result and 'all 512 final rolls' in result, 'Runtime loot verification failed')
        originals = self.seed_obstructions()
        for name in ANCHORS:
            self.place_direct(name)
            self.verify_no_item_drops(name, originals)
        self.command('save-all flush')
        self.stop()
        self.audit_disk('direct-placed')
        self.boot()
        self.assert_state('direct-after-restart', self.instances)
        self.runtime('direct-after-restart')
        for name in list(self.instances):
            self.undo(name, stage_prefix='direct-')
            self.verify_no_item_drops(name, originals)
        require('No gravity zones.' in self.command('flipzone list'), 'Direct undo retained zones')
        self.command('forceload remove all')
        self.command('save-all flush')
        self.stop()
        self.assert_state('direct-final-empty', {})
        self.audit_disk('direct-restored', air=True, originals=originals)

    def legacy_scenario(self):
        self.boot()
        result=self.command('dungeon verifyloot',timeout=90);require('Dungeon loot verified:' in result and 'all 512 final rolls' in result,'Runtime loot verification failed')
        for name in ANCHORS:self.place(name)
        self.runtime('before-restart');self.command('save-all flush');self.stop()
        self.assert_state('before-restart',self.instances);self.audit_disk('placed')
        self.boot();self.assert_state('after-restart',self.instances);self.runtime('after-restart')
        result=self.command('dungeon verifyloot',timeout=90);require('Dungeon loot verified:' in result,'Loot verification failed after restart')
        # Startup reconciles stale zones without deleting the unfinished backup or the placed sibling.
        self.command('save-all flush');self.stop()
        interrupted_name='sakura_labyrinth';backup_hash=self.simulate_interrupted_state(interrupted_name)
        self.boot();self.assert_state('interrupted-recovery',self.instances);self.runtime('interrupted-recovery')
        backup=WORLD/f'data/cu_dungeons/{self.instances[interrupted_name]}.before.nbt'
        require(hashlib.sha256(backup.read_bytes()).hexdigest()==backup_hash,'Recovery modified the original backup')
        self.assert_backup(interrupted_name,self.instances[interrupted_name])
        self.event('interrupted_state_recovery_verified',template=interrupted_name,backup_unchanged=True,protection_retained=True,stale_zones_removed=True,placed_sibling_zones_retained=True)
        for name in list(self.instances):self.undo(name)
        require('No gravity zones.' in self.command('flipzone list'),'Live gravity index retained zones after undo')
        self.command('forceload remove all');self.command('save-all flush');self.stop()
        self.assert_state('final-empty',{});self.audit_disk('restored',air=True)

    def run(self, scenario):
        self.report['scenario'] = scenario
        try:
            self.prepare()
            if scenario == 'direct':
                self.direct_scenario()
            else:
                self.legacy_scenario()
            self.report['passed'] = True
            self.event('PASS', scenario=scenario)
        except Exception as e:
            self.report['error']=type(e).__name__+': '+str(e);self.event('FAIL',error=self.report['error'])
            (EVIDENCE/'hosted-smoke-traceback.txt').write_text(traceback.format_exc(),encoding='utf-8')
            raise
        finally:
            try:self.stop(cleanup=True)
            finally:
                self.report['elapsed_s']=round(time.monotonic()-self.start,2);self.report['boots']=self.boots
                (EVIDENCE/'hosted-smoke-report.json').write_text(json.dumps(self.report,indent=2),encoding='utf-8')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--timeout-seconds',type=int,default=2400)
    parser.add_argument('--scenario', choices=('legacy','direct'), default='legacy')
    args=parser.parse_args()
    require(300<=args.timeout_seconds<=2400,'Total timeout must be between 300 and 2400 seconds')
    # Guard before creating evidence or touching configuration on any local machine.
    require(os.environ.get('GITHUB_ACTIONS')=='true' and sys.platform=='linux','GitHub Linux runner required; no local server/build will be started')
    Controller(args.timeout_seconds).run(args.scenario)

if __name__=='__main__':main()
