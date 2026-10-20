from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[3]
source=yaml.safe_load((root/'plugins/BedWars/arenas/world5.yml').read_text())
out=Path(__file__).with_name('bwxemtori.yml')
def loc(x,y,z): return f'{x};{y};{z};0.0;0.0'
names=['Red','Blue','Green','Yellow']
beds=[(-108,-26,118),(-57,-26,65),(-110,-26,14),(-161,-26,67)]
# Red was marked directly. The other three forge/shop positions were rotated
# around (-109, 66), then snapped to the matching slab/cobblestone floor blocks.
spawns=[(-107.5,-26,121.5),(-52.5,-26,65.5),(-109.5,-26,10.5),(-164.5,-26,67.5)]
forges=[(-107.5,-26,125.5),(-49.5,-26,64.5),(-110.5,-26,7.5),(-167.5,-26,67.5)]
shops=[(-104.5,-26,124.5),(-50.5,-26,62.5),(-112.5,-26,7.5),(-167.5,-26,70.5)]
source['name']='bwxemtori';source['world']='bwxemtori'
source['pos1']=loc(-200,-70,-20);source['pos2']=loc(-20,100,160)
source['specSpawn']=loc(-108.5,54,66.5);source['lobbySpawn']=loc(-108.5,53,66.5)
source['lobbySpawnWorld']='bwxemtori';source['minPlayers']=2
source['teams']={}
for i,name in enumerate(names):
 source['teams'][name.lower()]=dict(isNewColor=True,color=name.upper(),maxPlayers=2,bed=loc(*beds[i]),spawn=loc(*spawns[i]),actualName=name)
def spawner(x,y,z,kind,team=None):
 return dict(maxSpawnedResources=32,hologramEnabled=True,location=loc(x,y,z),customName=None,startLevel=1.0,team=team,type=kind)
source['spawners']=[];source['stores']=[]
for i,name in enumerate(names):
 x,y,z=forges[i]
 source['spawners'].append(spawner(x,y,z,'iron',name))
 source['spawners'].append(spawner(x,y,z,'gold',name))
 sx,sy,sz=shops[i]
 source['stores'].append(dict(loc=loc(sx,sy,sz),parent='true',shop='shop.yml',name='Item Shop',type='VILLAGER'))
for x,z in [(-150,26),(-69,25),(-149,107),(-68,106)]:source['spawners'].append(spawner(x+.5,-26,z+.5,'diamond'))
for x,z in [(-91,47),(-122,49),(-127,82),(-94,81)]:source['spawners'].append(spawner(x+.5,-25,z+.5,'emerald'))
out.write_text(yaml.safe_dump(source,sort_keys=False,allow_unicode=True))
target=root/'plugins/BedWars/arenas/bwxemtori.yml'
target.write_text(out.read_text())
print(f'Wrote {out} and {target}')
