from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[3]
current=root/'plugins/BedWars/arenas/world5.yml'
out=Path(__file__).with_name('world5-tentacle.yml')
arena=yaml.safe_load(current.read_text())
names=['red','blue','green','yellow','pink','aqua','white','gray']
beds=[(-32,-71),(32,-71),(71,-32),(71,32),(-71,32),(32,71),(-32,71),(-71,-32)]
forges=[(-32,-82),(32,-82),(82,-32),(82,32),(-82,32),(32,82),(-32,82),(-82,-32)]
shops=[(-38,-82),(26,-82),(82,-38),(82,26),(-82,26),(26,82),(-38,82),(-82,-38)]
def loc(x,y,z):return f'{x};{y};{z};0.0;0.0'
arena['pos1']=loc(-100,40,-100);arena['pos2']=loc(100,120,100)
arena['specSpawn']=loc(.5,101,.5);arena['lobbySpawn']=loc(.5,100,.5)
size=next(iter(arena['teams'].values()))['maxPlayers']
for i,key in enumerate(list(arena['teams'])):
 bx,bz=beds[i];fx,fz=forges[i]
 team=arena['teams'][key]
 team['bed']=loc(bx,66,bz)
 team['spawn']=loc(bx+(1 if fx>bx else -1 if fx<bx else 0)*5+.5,66,bz+(1 if fz>bz else -1 if fz<bz else 0)*5+.5)
 team['maxPlayers']=size
spawners=[];stores=[]
def spawner(x,y,z,type,team=None):
 spawners.append(dict(maxSpawnedResources=32,hologramEnabled=True,location=loc(x,y,z),customName=None,startLevel=1.0,team=team,type=type))
for i,key in enumerate(arena['teams']):
 x,z=forges[i];name=key.capitalize()
 spawner(x-.5,66,z+.5,'iron',name);spawner(x+1.5,66,z+.5,'gold',name)
 sx,sz=shops[i]
 stores.append(dict(loc=loc(sx+.5,66,sz+.5),parent='true',shop='shop.yml',name='Item Shop',type='VILLAGER'))
for x in (-32,32):
 for z in (-32,32):spawner(x+.5,65,z+.5,'diamond')
spawner(12.5,66,-14.5,'emerald');spawner(-11.5,66,15.5,'emerald')
arena['spawners']=spawners;arena['stores']=stores
out.write_text(yaml.safe_dump(arena,sort_keys=False,allow_unicode=True))
print(out,'teams',len(arena['teams']),'spawners',len(spawners),'stores',len(stores))
