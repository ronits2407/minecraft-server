"""Build ScreamingBedWars configs for Gardens, Aquarium, and Lighthouse.

Coordinates are inferred from the map's original bed/chest/diamond blocks and
symmetry. Use the BedWars marker and preview to fine-tune these in-game.
"""
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[3]
TEMPLATE = yaml.safe_load((ROOT / 'plugins/BedWars/arenas/world5.yml').read_text())

def loc(x, y, z, yaw=0):
    return f'{x};{y};{z};{yaw};0.0'

def rotate(x, z, turns=1, cx=0, cz=0):
    x -= cx; z -= cz
    for _ in range(turns % 4): x, z = -z, x
    return x + cx, z + cz

def spawner(x, y, z, kind, team=None):
    return dict(maxSpawnedResources=32, hologramEnabled=True,
                location=loc(x,y,z), customName=None, startLevel=1.0,
                team=team, type=kind)

def make(name, spawn, bounds, teams, diamonds, emeralds):
    cfg = dict(TEMPLATE)
    cfg['name'] = cfg['world'] = cfg['lobbySpawnWorld'] = name
    cfg['pos1'] = loc(*bounds[0]); cfg['pos2'] = loc(*bounds[1])
    cfg['lobbySpawn'] = loc(*spawn); cfg['specSpawn'] = loc(spawn[0], spawn[1]+1, spawn[2])
    cfg['minPlayers'] = 2; cfg['pauseCountdown'] = 20; cfg['gameTime'] = 3600
    cfg['constant.join-random-team-on-join'] = 'false'
    cfg['teams'] = {}; cfg['spawners'] = []; cfg['stores'] = []
    for team in teams:
        name_, color, max_players, bed, player_spawn, forge, shop = team
        cfg['teams'][name_.lower()] = dict(isNewColor=True, color=color, maxPlayers=max_players,
                                            bed=loc(*bed), spawn=loc(*player_spawn), actualName=name_)
        for kind in ('iron','gold'): cfg['spawners'].append(spawner(*forge,kind,name_))
        cfg['stores'].append(dict(loc=loc(*shop), parent='true', shop='shop.yml',
                                  name='Item Shop', type='VILLAGER'))
    for point in diamonds: cfg['spawners'].append(spawner(*point,'diamond'))
    for point in emeralds: cfg['spawners'].append(spawner(*point,'emerald'))
    content = yaml.safe_dump(cfg,sort_keys=False,allow_unicode=True)
    (Path(__file__).parent / (name+'.yml')).write_text(content)
    (ROOT / 'plugins/BedWars/arenas' / (name+'.yml')).write_text(content)
    print(name, len(teams), 'teams', len(diamonds), 'diamond', len(emeralds), 'emerald')

# Gardens: Two 4-player bases. Calibrated from in-game survey marks.
# Blue is at negative X; Red is at positive X.
gardens = [
    ('Blue', 'BLUE', 4, (-80, 77, 0), (-93.5, 79, 0.5, -90.0), (-97.5, 79, 0.5), (-93.5, 79, 8.5, 0.0)),
    ('Red', 'RED', 4, (80, 77, 0), (94.5, 79, 0.5, 90.0), (98.5, 79, 0.5), (94.5, 79, -7.5, 180.0)),
]
make('bwgardens', (-1.5, 149, 0.5), ((-115, 50, -80), (115, 160, 80)), gardens,
     [(0.5, 78, -51.5), (0.5, 78, 52.5)], [(-20.5, 77, -20.5), (21.5, 77, 21.5)])

# Aquarium's four bases: Red (+X), Blue (+Z), Green (-X), Yellow (-Z).
# Calibrated from in-game survey marks on Red and verified on all four quadrants.
aquarium = [
    ('Red', 'RED', 4, (48, 87, 0), (59.5, 87, 0.5, 90.0), (64.5, 87, 0.5), (59.5, 87, 5.5, 0.0)),
    ('Blue', 'BLUE', 4, (0, 87, 48), (-0.5, 87, 59.5, 180.0), (-0.5, 87, 64.5), (-4.5, 87, 59.5, 90.0)),
    ('Green', 'GREEN', 4, (-48, 87, 0), (-59.5, 87, -0.5, -90.0), (-64.5, 87, -0.5), (-59.5, 87, -4.5, 180.0)),
    ('Yellow', 'YELLOW', 4, (0, 87, -48), (0.5, 87, -59.5, 0.0), (0.5, 87, -64.5), (5.5, 87, -59.5, -90.0)),
]
make('bwaquarium', (0.5, 118, 0.5), ((-115, 50, -115), (115, 160, 115)), aquarium,
     [(-40.5, 83, -38.5), (39.5, 82, -40.5), (41.5, 82, 39.5), (-38.5, 82, 41.5)],
     [(-9.5, 95, -10.5), (8.5, 95, 11.5)])

# Lighthouse: Eight 2-player bases. Calibrated from in-game survey marks.
lighthouse = [
    ('Red', 'RED', 2, (-23, 66, -76), (-22.5, 65, -86.5, 0.0), (-22.5, 65, -90.5), (-27.5, 65, -88.5, 90.0)),
    ('Blue', 'BLUE', 2, (29, 66, -76), (29.5, 65, -86.5, 0.0), (29.5, 65, -90.5), (24.5, 65, -88.5, 90.0)),
    ('Green', 'GREEN', 2, (79, 66, -26), (89.5, 65, -25.5, -90.0), (93.5, 65, -25.5), (91.5, 65, -30.5, 0.0)),
    ('Yellow', 'YELLOW', 2, (79, 66, 26), (89.5, 65, 26.5, -90.0), (93.5, 65, 26.5), (91.5, 65, 21.5, 0.0)),
    ('Aqua', 'CYAN', 2, (29, 66, 76), (29.5, 65, 86.5, 180.0), (29.5, 65, 90.5), (23.5, 65, 88.5, 90.0)),
    ('White', 'WHITE', 2, (-23, 66, 76), (-22.5, 65, 86.5, 180.0), (-22.5, 65, 90.5), (-28.5, 65, 88.5, 90.0)),
    ('Pink', 'PINK', 2, (-73, 66, 26), (-83.5, 65, 26.5, 90.0), (-87.5, 65, 26.5), (-85.5, 65, 31.5, 180.0)),
    ('Gray', 'GRAY', 2, (-73, 66, -26), (-83.5, 65, -25.5, 90.0), (-87.5, 65, -25.5), (-85.5, 65, -20.5, 0.0)),
]
make('bwlighthouse', (3.5, 118, 0.5), ((-120, 40, -120), (125, 150, 120)), lighthouse,
     [(-48.5, 67, -51.5), (55.5, 67, -51.5), (55.5, 67, 52.5), (-48.5, 67, 52.5)],
     [(-6.5, 65, 14.5), (13.5, 65, -13.5), (3.5, 87, 7.5), (3.5, 87, -6.5)])
