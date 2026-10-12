"""Generate original voxel trap models and their recipes, saved-state loot and labels.

Uses existing vanilla materials and VC's bronze material; creates no copied textures.
Run with Python from any directory. Generation is deterministic and idempotent.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/vc'
DATA = ROOT / 'data'
TRAPS = {
    'wooden_spikes': ('Wooden Spikes', 12, 'wood'),
    'tangle_net': ('Tangle Net', 4, 'wood'),
    'bronze_snare': ('Bronze Snare', 6, 'bronze'),
    'bronze_caltrops': ('Bronze Caltrops', 12, 'bronze'),
    'iron_spring_trap': ('Iron Spring Trap', 8, 'iron'),
}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def cube(a, b, texture='wood', rotation=None):
    element = {'from': a, 'to': b,
               'faces': {face: {'texture': '#' + texture, 'uv': [0, 0, 16, 16]}
                         for face in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rotation:
        element['rotation'] = rotation
    return element

def model(name, status, material):
    elements = []
    broken = status == 'broken'
    armed = status == 'armed'
    textures = {'particle': '#wood', 'wood': 'minecraft:block/oak_planks', 'stake': 'minecraft:block/stripped_oak_log',
                'bronze': 'vc:block/bronze_block', 'iron': 'minecraft:block/iron_block',
                'rope': 'minecraft:block/white_wool', 'marker': 'minecraft:block/' + ('green' if armed else 'red') + '_wool'}
    if name == 'wooden_spikes':
        for z in (2, 7, 12):
            elements.append(cube([1, 0, z], [15, 1.5, z + 2]))
        for x in (3, 8, 13):
            for z in (3, 8, 13):
                h = 2 if broken else 3 if not armed else 5
                elements.append(cube([x - 1, 1.5, z - 1], [x + 1, h, z + 1], 'stake'))
                if armed:
                    elements.append(cube([x - .5, h, z - .5], [x + .5, h + 1, z + .5], 'stake'))
    elif name == 'tangle_net':
        for x in (1, 14):
            elements.append(cube([x, 0, 1], [x + 1, 1.5, 15]))
        for n in (2, 5, 8, 11, 14):
            if armed:
                elements.extend([cube([n, 1.5, 1], [n + .4, 1.9, 15], 'rope'),
                                 cube([1, 1.5, n], [15, 1.9, n + .4], 'rope')])
            elif not broken or n < 8:
                elements.append(cube([3, 1, n / 2 + 3], [12, 2.5, n / 2 + 3.4], 'rope'))
    elif name == 'bronze_caltrops':
        for x, z in ((4, 4), (11, 5), (7, 11)):
            elements.extend([cube([x - 2, 0, z - .5], [x + 2, 1, z + .5], 'bronze'),
                             cube([x - .5, 0, z - 2], [x + .5, 1, z + 2], 'bronze')])
            if armed:
                elements.append(cube([x - .5, 1, z - .5], [x + .5, 4, z + .5], 'bronze'))
    else:
        elements.extend([cube([2, 0, 2], [14, 1, 14], 'wood'),
                         cube([6, 1, 6], [10, 1.8, 10], material),
                         cube([7, 1, 2], [9, 2, 5], material)])
        for x in (2, 13):
            h = 2 if broken else 6 if not armed else 3
            elements.append(cube([x, 1, 2], [x + 1, h, 14], material))
            if not broken:
                for z in (3, 6, 9, 12):
                    elements.append(cube([x - .5, h, z], [x + 1.5, h + 1, z + 1], material))
        if name == 'iron_spring_trap':
            for z in (5, 7, 9, 11):
                elements.append(cube([5, 1, z], [11, 1.5, z + .5], 'iron'))
    elements.append(cube([13, 0, 13], [15, 1.1, 15], 'marker'))
    if broken:
        textures['marker'] = 'minecraft:block/gray_wool'
    return {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements,
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 1, 0], 'scale': [.85, .85, .85]},
                        'ground': {'translation': [0, 3, 0], 'scale': [.5, .5, .5]},
                        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 2, 0], 'scale': [.8, .8, .8]}}}

for name, (title, uses, material) in TRAPS.items():
    for status in ('armed', 'spent', 'broken'):
        write(ASSETS / 'models/block' / f'{name}_{status}.json', model(name, status, material))
    variants = {}
    for wear in range(13):
        for armed in (True, False):
            status = 'broken' if wear >= uses else 'armed' if armed else 'spent'
            variants[f'armed={str(armed).lower()},wear={wear}'] = {'model': f'vc:block/{name}_{status}'}
    write(ASSETS / 'blockstates' / f'{name}.json', {'variants': variants})
    write(ASSETS / 'models/item' / f'{name}.json', {'parent': f'vc:block/{name}_armed'})
    write(ASSETS / 'items' / f'{name}.json', {'model': {'type': 'minecraft:model', 'model': f'vc:item/{name}'}})
    write(DATA / 'vc/loot_table/blocks' / f'{name}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'vc:' + name,
            'functions': [{'function': 'minecraft:copy_state', 'block': 'vc:' + name, 'properties': ['wear', 'armed'],
                'conditions': [{'condition': 'minecraft:inverted', 'term': {'condition': 'minecraft:block_state_property',
                    'block': 'vc:' + name, 'properties': {'wear': '0', 'armed': 'true'}}}]}]}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})

recipes = {
    'wooden_spikes': (['S S', 'SSS', 'PPP'], {'S': 'minecraft:stick', 'P': '#minecraft:planks'}, 2),
    'tangle_net': (['STS', 'T T', 'STS'], {'S': 'minecraft:string', 'T': 'minecraft:stick'}, 1),
    'bronze_snare': (['B B', ' S ', 'PPP'], {'B': 'vc:bronze_ingot', 'S': 'minecraft:string', 'P': '#minecraft:planks'}, 1),
    'bronze_caltrops': ([' B ', 'BBB'], {'B': 'vc:bronze_ingot'}, 4),
    'iron_spring_trap': (['I I', 'IPI', 'PPP'], {'I': 'minecraft:iron_ingot', 'P': '#minecraft:planks'}, 1),
}
for name, (pattern, key, count) in recipes.items():
    write(DATA / 'vc/recipe' / f'{name}.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment',
        'pattern': pattern, 'key': key, 'result': {'id': 'vc:' + name, 'count': count}})

for namespace, name, values in (
    ('vc', 'requires_bronze', ['bronze_snare', 'bronze_caltrops']),
    ('vc', 'requires_iron', ['iron_spring_trap']),
    ('minecraft', 'mineable/axe', ['wooden_spikes', 'tangle_net']),
    ('minecraft', 'mineable/pickaxe', ['bronze_snare', 'bronze_caltrops', 'iron_spring_trap']),
):
    path = DATA / namespace / 'tags/item' / f'{name}.json' if namespace == 'vc' else DATA / namespace / 'tags/block' / f'{name}.json'
    value = json.loads(path.read_text()) if path.exists() else {'replace': False, 'values': []}
    for trap in values:
        if 'vc:' + trap not in value['values']:
            value['values'].append('vc:' + trap)
    write(path, value)

path = ASSETS / 'lang/en_us.json'
language = json.loads(path.read_text())
for name, (title, _, _) in TRAPS.items():
    language['block.vc.' + name] = title
language['vc.configuration.maxSettlementTraps'] = 'Traps per settlement'
language['vc.configuration.maxSettlementTraps.tooltip'] = 'Maximum placed traps per town. Only traps in ticking chunks activate; each activation spends durability.'
write(path, language)
