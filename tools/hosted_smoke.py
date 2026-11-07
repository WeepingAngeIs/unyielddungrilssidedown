#!/usr/bin/env python3
"""Hosted, disposable NeoForge integration smoke. Refuses execution outside GitHub Linux.

Prerequisites: Java 21, repository Gradle wrapper, the six pinned dependencies in
run/mods. Run `python tools/hosted_smoke.py --timeout-seconds 2400` on a fresh
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
STATE_RE=re.compile(r'^(SCAN|READY|VERIFY|PLACE|RESTORE) ([a-f0-9]{12}) (\d+)%;.*preview token ([a-f0-9]{12})',re.S)

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
        self.proc=None;self.log_handle=None;self.boots=0;self.instances={};self.loaded={};self.events=[]
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
                phase,i,percent,token=match.groups();last=f'{phase} {percent}%'
                if last!=last_logged:self.event('phase',instance=i,phase=phase,percent=int(percent));last_logged=last
                if instance:require(i==instance,'Active job unexpectedly changed identity')
                if phase==wanted:return i,token
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
            require(i in indexed and indexed[i]['Status']=='PLACED' and indexed[i]['Template']==name,f'{stage}: missing placed entry for {name}')
            actual=[z for z in zones if z['name'].startswith('dungeon/'+i+'/')];t=self.loaded[name]
            require(len(actual)==len(t['Zones']),f'{stage}: gravity zone count differs for {name}')
            az={z['name'].split('/')[-1]:z for z in actual}
            for z in t['Zones']:
                got=az[z['Name']];require(got['gravity']==z['Gravity'],f'{stage}: gravity changed')
                for axis,n in zip('xyz',range(3)):
                    require(got[axis+'0']==t['Minimum'][n]+z['Min'][n] and got[axis+'1']==t['Minimum'][n]+z['Max'][n]+1,f'{stage}: gravity bounds changed')
        require(len(zones)==sum(len(self.loaded[n]['Zones']) for n in expected),f'{stage}: leftover or unexpected gravity zones')
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
                self.command('kill @e[type=minecraft:marker,tag=cu_ci_zone_probe]',record=False)
                try:
                    result=self.command('summon minecraft:marker '+xyz+' {Tags:["cu_ci_zone_probe"]}')
                    require('Summoned' in result or 'summoned' in result,'Gravity probe marker could not spawn')
                    result=self.command('execute as @e[type=minecraft:marker,tag=cu_ci_zone_probe,limit=1] at @s run gravity room')
                    require('dungeon/'+self.instances[name]+'/'+z['Name'] in result and '(gravity '+gravity+')' in result,'Live gravity index did not return the expected zone/direction')
                    self.event('live_gravity_verified',stage=stage,template=name,gravity=gravity)
                finally:self.command('kill @e[type=minecraft:marker,tag=cu_ci_zone_probe]',record=False)
    def audit_disk(self,stage,air=False):
        require(self.proc is None,'Offline audit requires cleanly stopped server')
        region=RegionReader(WORLD/'region');summary={}
        try:
            for name,t in self.loaded.items():
                self.check();mx,my,mz=t['Minimum'];sx,sy,sz=t['Size'];maxx,maxy,maxz=mx+sx-1,my+sy-1,mz+sz-1
                states=t['CanonicalPalette'];expected=t['Expected'];cells=chunks=0;found_be={};mismatches=[]
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
                                        got=names[pi];want='minecraft:air' if air else states[expected[(y-my)*sx*sz+(z-mz)*sx+x-mx]]
                                        if got!=want and not(got in AIR and want in AIR):
                                            if len(mismatches)<8:mismatches.append({'position':[x,y,z],'expected':want,'actual':got})
                                        cells+=1
                require(not mismatches,f'{stage}: block mismatches in {name}: {mismatches}')
                if air:require(not found_be,f'{stage}: block entities survived undo in {name}')
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
                summary[name]={'cells_verified':cells,'saved_chunks':chunks,'block_entities_verified':len(found_be),'all_original_air':air}
                self.event('full_saved_world_verified',stage=stage,template=name,**summary[name])
        finally:region.close()
        (EVIDENCE/(stage+'-full-world-audit.json')).write_text(json.dumps(summary,indent=2),encoding='utf-8');self.report['steps'].append({'stage':stage,'full_world_audit':summary})
    def undo(self,name):
        i=self.instances[name];out=self.command('dungeon undo '+i);require('UNDO PREVIEW:' in out,'Undo preview refused: '+out)
        _,token=self.wait_phase('READY',i);out=self.command('dungeon confirm '+token);require('Restoring the original terrain' in out,'Undo confirmation refused')
        self.wait_phase('IDLE',i,max_seconds=420)
        expected={n:j for n,j in self.instances.items() if n!=name};self.assert_state('undone-'+name,expected)
        # Region protection must actually be removed, not just its journal entry.
        p=self.probes(name)[0];xyz=' '.join(map(str,p));self.command('forceload add '+str(p[0])+' '+str(p[2]))
        require('Test passed' in self.command('execute if block '+xyz+' minecraft:air'),'Restored probe is not air')
        self.command('setblock '+xyz+' minecraft:stone');require('Test passed' in self.command('execute if block '+xyz+' minecraft:stone'),'Protection persisted after undo')
        self.command('setblock '+xyz+' minecraft:air');require('Test passed' in self.command('execute if block '+xyz+' minecraft:air'),'Air reset failed')
        del self.instances[name];self.event('undo_verified',template=name,instance=i)
    def run(self):
        try:
            self.prepare();self.boot()
            result=self.command('dungeon verifyloot',timeout=90);require('Dungeon loot verified:' in result and 'all 512 final rolls' in result,'Runtime loot verification failed')
            for name in ANCHORS:self.place(name)
            self.runtime('before-restart');self.command('save-all flush');self.stop()
            self.assert_state('before-restart',self.instances);self.audit_disk('placed')
            self.boot();self.assert_state('after-restart',self.instances);self.runtime('after-restart')
            result=self.command('dungeon verifyloot',timeout=90);require('Dungeon loot verified:' in result,'Loot verification failed after restart')
            for name in list(self.instances):self.undo(name)
            require('No gravity zones.' in self.command('flipzone list'),'Live gravity index retained zones after undo')
            self.command('forceload remove all');self.command('save-all flush');self.stop()
            self.assert_state('final-empty',{});self.audit_disk('restored',air=True)
            self.report['passed']=True;self.event('PASS',meaning='both real placements, restart persistence, full saved geometry, all loot/spawner records, full air undo, zones/protection removed')
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
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--timeout-seconds',type=int,default=2400);args=parser.parse_args()
    require(300<=args.timeout_seconds<=2400,'Total timeout must be between 300 and 2400 seconds')
    # Guard before creating evidence or touching configuration on any local machine.
    require(os.environ.get('GITHUB_ACTIONS')=='true' and sys.platform=='linux','GitHub Linux runner required; no local server/build will be started')
    Controller(args.timeout_seconds).run()

if __name__=='__main__':main()
