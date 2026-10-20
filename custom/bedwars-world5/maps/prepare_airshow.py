from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[3]
source = yaml.safe_load((root / 'plugins/BedWars/arenas/world5.yml').read_text())
out = Path(__file__).with_name('bwairshow.yml')

def loc(x, y, z, yaw=0.0, pitch=0.0):
    return f'{x};{y};{z};{yaw};{pitch}'

names = [
    # Beds stay at their original map positions. Red and Blue placements were
    # measured; the other teams rotate around the map's block center (0.5, 0.5).
    ('Red', 'RED', (-40, 63, -77), (-39.5, 63, -82.5, 0.0), (-39.5, 63, -86.5), (-44.5, 63, -82.5, -90.0)),
    ('Blue', 'BLUE', (40, 67, -77), (40.5, 67, -82.5, 0.0), (40.5, 67, -86.5), (35.5, 67, -82.5, -90.0)),
    ('Green', 'GREEN', (77, 67, -40), (83.5, 67, -39.5, 90.0), (87.5, 67, -39.5), (83.5, 67, -44.5, 0.0)),
    ('Yellow', 'YELLOW', (77, 63, 40), (83.5, 63, 40.5, 90.0), (87.5, 63, 40.5), (83.5, 63, 35.5, 0.0)),
    ('Cyan', 'CYAN', (40, 63, 77), (40.5, 63, 83.5, 180.0), (40.5, 63, 87.5), (45.5, 63, 83.5, 90.0)),
    ('White', 'WHITE', (-40, 67, 77), (-39.5, 67, 83.5, 180.0), (-39.5, 67, 87.5), (-34.5, 67, 83.5, 90.0)),
    ('Pink', 'PINK', (-77, 67, 40), (-82.5, 67, 40.5, 270.0), (-86.5, 67, 40.5), (-82.5, 67, 45.5, 180.0)),
    ('Gray', 'GRAY', (-77, 63, -40), (-82.5, 63, -39.5, 270.0), (-86.5, 63, -39.5), (-82.5, 63, -34.5, 180.0)),
]

source['name'] = 'bwairshow'
source['world'] = 'bwairshow'
source['pauseCountdown'] = 20
source['gameTime'] = 3600
source['pos1'] = loc(-130, 30, -130)
source['pos2'] = loc(130, 145, 130)
source['specSpawn'] = loc(0.5, 100, 0.5)
source['lobbySpawn'] = loc(0.5, 118, 0.5)
source['lobbySpawnWorld'] = 'bwairshow'
source['minPlayers'] = 2
source['constant.join-random-team-on-join'] = 'false'

source['teams'] = {}
for name, color, bed, spawn, forge, shop in names:
    actual = 'Aqua' if name == 'Cyan' else name
    source['teams'][name.lower()] = dict(
        isNewColor=True,
        color=color,
        maxPlayers=2,
        bed=loc(*bed),
        spawn=loc(*spawn),
        actualName=actual
    )

def spawner(x, y, z, kind, team=None):
    return dict(
        maxSpawnedResources=32,
        hologramEnabled=True,
        location=loc(x, y, z),
        customName=None,
        startLevel=1.0,
        team=team,
        type=kind
    )

source['spawners'] = []
source['stores'] = []

for name, color, bed, spawn, forge, shop in names:
    fx, fy, fz = forge
    # 2 resource spawners per base: iron and gold
    team_name = 'Aqua' if name == 'Cyan' else name
    source['spawners'].append(spawner(fx, fy, fz, 'iron', team_name))
    source['spawners'].append(spawner(fx, fy, fz, 'gold', team_name))
    sx, sy, sz, syaw = shop
    source['stores'].append(dict(
        loc=loc(sx, sy, sz, syaw),
        parent='true',
        shop='shop.yml',
        name='Item Shop',
        type='VILLAGER'
    ))

# All neutral generators were measured on the map. Each location is one block
# above its marked diamond or emerald floor block.
source['spawners'].append(spawner(40.5, 65, -39.5, 'diamond'))
source['spawners'].append(spawner(40.5, 61, 40.5, 'diamond'))
source['spawners'].append(spawner(-39.5, 65, 40.5, 'diamond'))
source['spawners'].append(spawner(-39.5, 61, -39.5, 'diamond'))

source['spawners'].append(spawner(-11.5, 61, -11.5, 'emerald'))
source['spawners'].append(spawner(-11.5, 65, 12.5, 'emerald'))
source['spawners'].append(spawner(12.5, 61, 12.5, 'emerald'))
source['spawners'].append(spawner(12.5, 65, -11.5, 'emerald'))

content = yaml.safe_dump(source, sort_keys=False, allow_unicode=True)
out.write_text(content)
target = root / 'plugins/BedWars/arenas/bwairshow.yml'
target.write_text(content)
print(f'Wrote {out} and {target}')
