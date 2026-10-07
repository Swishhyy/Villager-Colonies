"""Generate the station and banner block models: small dioramas made from opaque vanilla block textures.

Usage: python3 tools/station_models.py src/main/resources/assets/wwmc
"""
import json, os, sys

OUT = sys.argv[1]
FACES = ["down", "up", "north", "south", "west", "east"]
SIDES = ["north", "south", "west", "east"]


def box(frm, to, tex, skip=(), uv=None, rot=None, rotation=None):
    """tex: a texture key for every face, or a dict with face names, 'side' (horizontal faces) and 'all' defaults."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    assert x1 < x2 and y1 < y2 and z1 < z2, (frm, to)
    for v in frm + to:
        assert -16 <= v <= 32
    faces = {}
    for face in FACES:
        if face in skip:
            continue
        if isinstance(tex, dict):
            key = tex.get(face) or (tex.get("side") if face in SIDES else None) or tex.get("all")
        else:
            key = tex
        if key is None:
            continue
        f = {"texture": "#" + key}
        if uv and face in uv:
            f["uv"] = uv[face]
        if rot and face in rot:
            f["rotation"] = rot[face]
        bound = {"down": y1 == 0, "up": y2 == 16, "north": z1 == 0, "south": z2 == 16, "west": x1 == 0, "east": x2 == 16}
        if bound[face]:
            f["cullface"] = face
        faces[face] = f
    element = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        element["rotation"] = rotation
    return element


def log_x(frm, to, bark, ends):
    """A log lying along X: bark grain runs along the log, rings on both ends."""
    length = to[0] - frm[0]
    thick_y = to[1] - frm[1]
    thick_z = to[2] - frm[2]
    return box(frm, to, {"north": bark, "south": bark, "up": bark, "down": bark, "west": ends, "east": ends},
               uv={"north": [0, 0, thick_y, length], "south": [0, 0, thick_y, length], "up": [0, 0, thick_z, length],
                   "down": [0, 0, thick_z, length], "west": [0, 0, 16, 16], "east": [0, 0, 16, 16]},
               rot={"north": 90, "south": 90, "up": 90, "down": 90})


def wheel(frm, to, face, rim):
    return box(frm, to, {"west": face, "east": face, "side": rim, "up": rim, "down": rim},
               uv={"west": [0, 0, 16, 16], "east": [0, 0, 16, 16]})


def model(textures, elements):
    textures = {k: (v if ":" in v else "minecraft:block/" + v) for k, v in textures.items()}
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


M = {}

M["farm_station"] = model(
    {"particle": "composter_side", "side": "composter_side", "bottom": "composter_bottom", "soil": "farmland_moist",
     "rim": "stripped_oak_log", "pumpkin": "pumpkin_side", "pumpkin_top": "pumpkin_top", "melon": "melon_side",
     "melon_top": "melon_top", "hay": "hay_block_side", "hay_top": "hay_block_top"},
    [
        box([0, 0, 0], [16, 10, 16], {"side": "side", "down": "bottom", "up": "soil"}),
        box([0, 10, 0], [16, 11, 1.5], "rim"),
        box([0, 10, 14.5], [16, 11, 16], "rim"),
        box([0, 10, 1.5], [1.5, 11, 14.5], "rim"),
        box([14.5, 10, 1.5], [16, 11, 14.5], "rim"),
        box([2.5, 10, 2.5], [7.5, 15, 7.5], {"side": "pumpkin", "up": "pumpkin_top", "down": "pumpkin_top"}),
        box([8.5, 10, 8.5], [13, 14, 13], {"side": "melon", "up": "melon_top", "down": "melon_top"}),
        box([9, 10, 2.5], [14, 13, 6.5], {"side": "hay", "up": "hay_top", "down": "hay_top"}),
    ])

M["lumber_station"] = model(
    {"particle": "oak_log", "bark": "oak_log", "rings": "oak_log_top", "spruce": "spruce_log", "spruce_top": "spruce_log_top",
     "stripped": "stripped_oak_log", "stripped_top": "stripped_oak_log_top", "handle": "stripped_birch_log", "iron": "iron_block"},
    [
        box([1, 0, 1], [15, 10, 15], {"side": "bark", "up": "rings", "down": "rings"}),
        log_x([2, 10, 2], [14, 13.5, 5.5], "bark", "rings"),
        log_x([2, 10, 6], [14, 13.5, 9.5], "spruce", "spruce_top"),
        log_x([3, 13.5, 4], [13, 16, 7.5], "stripped", "stripped_top"),
        box([11.5, 10, 11], [12.5, 15.3, 12], "handle"),
        box([10, 13.5, 10.75], [12, 15.5, 12.25], "iron"),
    ])

# The mine digs toward its north face, where the tunnel entrance is.
M["mine_station"] = model(
    {"particle": "cobblestone", "stone": "cobblestone", "beam": "dark_oak_log", "plank": "dark_oak_planks", "dark": "coal_block",
     "iron": "iron_ore", "coal": "coal_ore", "gold": "gold_ore", "copper": "copper_ore"},
    [
        box([0, 0, 0], [16, 1, 16], "stone"),
        box([0, 1, 2], [16, 11, 16], "stone"),
        box([0, 1, 0], [3, 11, 2], "stone"),
        box([13, 1, 0], [16, 11, 2], "stone"),
        box([3, 9, 0], [13, 11, 2], "stone"),
        box([3, 1, 0], [4, 9, 2], "beam"),
        box([12, 1, 0], [13, 9, 2], "beam"),
        box([4, 8, 0], [12, 9, 2], "plank"),
        box([4, 1, 1.9], [12, 8, 2], {"north": "dark"}),
        box([2, 11, 3], [6, 14, 7], "iron"),
        box([6.5, 11, 8], [10.5, 13.5, 12], "coal"),
        box([10, 11, 3], [13, 13, 6], "gold"),
        box([2.5, 11, 9], [5.5, 13, 12], "copper"),
    ])

# The quarry digs the chunk past its north side, marked by a red flag on the rim.
ring = lambda a, b, h, tex: [
    box([a, 0, a], [b, h, a + 2], tex), box([a, 0, b - 2], [b, h, b], tex),
    box([a, 0, a + 2], [a + 2, h, b - 2], tex), box([b - 2, 0, a + 2], [b, h, b - 2], tex)]
M["quarry_station"] = model(
    {"particle": "cobblestone", "cobble": "cobblestone", "stone": "stone", "andesite": "andesite", "gravel": "gravel",
     "pole": "oak_log", "flag": "red_wool"},
    ring(0, 16, 13, "cobble") + ring(2, 14, 10, "stone") + ring(4, 12, 7, "andesite") + [
        box([6, 0, 6], [10, 4, 10], "gravel"),
        box([7.5, 13, 0.5], [8.5, 16, 1.5], "pole"),
        box([8.5, 14, 0.75], [11.5, 15.75, 1.25], "flag"),
    ])

M["guard_station"] = model(
    {"particle": "stone_bricks", "brick": "stone_bricks", "chiseled": "chiseled_stone_bricks", "base": "polished_andesite", "slit": "coal_block"},
    [
        box([0, 0, 0], [16, 2, 16], "base"),
        box([1, 2, 1], [15, 12, 15], "brick"),
        box([4.5, 3.5, 15], [11.5, 10.5, 15.4], "chiseled"),
        box([7.25, 5, 15.4], [8.75, 9, 15.5], {"south": "slit"}),
        box([0, 12, 0], [16, 13, 16], "brick"),
        box([0, 13, 0], [4, 16, 4], "brick"), box([12, 13, 0], [16, 16, 4], "brick"),
        box([0, 13, 12], [4, 16, 16], "brick"), box([12, 13, 12], [16, 16, 16], "brick"),
        box([6, 13, 0], [10, 16, 2], "brick"), box([6, 13, 14], [10, 16, 16], "brick"),
        box([0, 13, 6], [2, 16, 10], "brick"), box([14, 13, 6], [16, 16, 10], "brick"),
    ])

M["craftsman_station"] = model(
    {"particle": "crafting_table_front", "top": "crafting_table_top", "front": "crafting_table_front", "side": "crafting_table_side",
     "leg": "stripped_oak_log", "plank": "oak_planks", "barrel": "barrel_side", "barrel_top": "barrel_top", "crate": "spruce_planks"},
    [
        box([0, 12, 0], [16, 16, 16], {"up": "top", "down": "plank", "north": "front", "south": "front", "west": "side", "east": "side"}),
        box([1, 0, 1], [3, 12, 3], "leg"), box([13, 0, 1], [15, 12, 3], "leg"),
        box([1, 0, 13], [3, 12, 15], "leg"), box([13, 0, 13], [15, 12, 15], "leg"),
        box([1.5, 3, 1.5], [14.5, 4.5, 14.5], "plank"),
        box([3, 4.5, 4], [7.5, 10, 8.5], {"side": "barrel", "up": "barrel_top", "down": "barrel_top"}),
        box([9, 4.5, 7], [13, 8.5, 11], "crate"),
    ])

M["smeltery_station"] = model(
    {"particle": "blast_furnace_side", "side": "blast_furnace_side", "front": "blast_furnace_front", "top": "blast_furnace_top",
     "brick": "bricks", "iron": "raw_iron_block", "copper": "raw_copper_block", "gold": "raw_gold_block"},
    [
        box([0, 0, 0], [16, 12, 16], {"side": "side", "south": "front", "up": "top", "down": "top"}),
        box([9, 12, 9], [14, 16, 14], "brick"),
        box([2, 12, 2], [6, 14.5, 6], "iron"),
        box([2.5, 12, 7], [6, 14, 10.5], "copper"),
        box([6.5, 12, 2.5], [9, 14, 5], "gold"),
    ])

M["cook_station"] = model(
    {"particle": "smoker_side", "side": "smoker_side", "front": "smoker_front", "top": "smoker_top", "bottom": "smoker_bottom",
     "board": "stripped_birch_log", "loaf": "stripped_jungle_log", "pot": "polished_blackstone", "soup": "orange_terracotta"},
    [
        box([0, 0, 0], [16, 12, 16], {"side": "side", "south": "front", "up": "top", "down": "bottom"}),
        box([2, 12, 2.5], [8.5, 12.75, 8], "board"),
        box([3, 12.75, 3.5], [7.5, 14.5, 6.5], "loaf"),
        box([9.5, 12, 8.5], [14, 15.5, 13], {"side": "pot", "down": "pot", "up": "soup"}),
    ])

M["blacksmith_station"] = model(
    {"particle": "polished_blackstone_bricks", "base": "polished_blackstone_bricks", "front": "smithing_table_front",
     "top": "polished_blackstone", "anvil": "anvil", "anvil_top": "anvil_top", "coal": "coal_block"},
    [
        box([0, 0, 0], [16, 9, 16], {"side": "base", "south": "front", "up": "top", "down": "top"}),
        box([4, 9, 4.5], [12, 10, 11.5], "anvil"),
        box([6, 10, 6], [10, 12, 10], "anvil"),
        box([3, 12, 5], [13, 15, 11], {"all": "anvil", "up": "anvil_top"}),
        box([12, 9, 12], [15, 10.5, 15], "coal"),
    ])

M["warehouse_station"] = model(
    {"particle": "barrel_side", "pallet": "spruce_planks", "barrel": "barrel_side", "barrel_top": "barrel_top", "barrel_bottom": "barrel_bottom",
     "crate": "oak_planks", "boards": "stripped_spruce_log", "sack": "brown_wool"},
    [
        box([0, 0, 0], [16, 2, 16], "pallet"),
        box([1.5, 2, 1.5], [9.5, 14, 9.5], {"side": "barrel", "up": "barrel_top", "down": "barrel_bottom"}),
        box([10, 2, 1.5], [15, 7, 6.5], "crate"),
        box([10.5, 7, 2], [14.5, 9, 6], "sack"),
        box([1.5, 2, 10], [7.5, 8, 15], "boards"),
        box([2.5, 8, 10.5], [6.5, 12, 14.5], "crate"),
        box([9.5, 2, 9.5], [14.5, 9, 14.5], {"side": "barrel", "up": "barrel_top", "down": "barrel_bottom"}),
    ])

M["housing_station"] = model(
    {"particle": "oak_planks", "base": "cobblestone", "wall": "oak_planks", "post": "oak_log", "roof": "terracotta",
     "door": "dark_oak_planks", "window": "light_blue_terracotta", "brick": "bricks"},
    [
        box([0, 0, 0], [16, 1.5, 16], "base"),
        box([2, 1.5, 2], [14, 9, 14], "wall"),
        box([1.5, 1.5, 1.5], [2.5, 9, 2.5], "post"), box([13.5, 1.5, 1.5], [14.5, 9, 2.5], "post"),
        box([1.5, 1.5, 13.5], [2.5, 9, 14.5], "post"), box([13.5, 1.5, 13.5], [14.5, 9, 14.5], "post"),
        box([6.5, 1.5, 14], [9.5, 6.5, 14.4], "door"),
        box([3.5, 4.5, 14], [5.5, 6.5, 14.2], "window"), box([10.5, 4.5, 14], [12.5, 6.5, 14.2], "window"),
        box([1.8, 4.5, 6], [2, 6.5, 10], "window"), box([14, 4.5, 6], [14.2, 6.5, 10], "window"),
        box([1, 9, 1], [15, 10.5, 15], "roof"),
        box([3, 10.5, 1], [13, 12, 15], "roof"),
        box([5, 12, 1], [11, 13.5, 15], "roof"),
        box([7, 13.5, 1], [9, 15, 15], "roof"),
        box([10.5, 12, 9], [12.5, 16, 11], "brick"),
    ])

M["barracks_station"] = model(
    {"particle": "green_terracotta", "ground": "coarse_dirt", "tent": "green_terracotta", "flap": "black_wool", "pole": "spruce_log", "flag": "red_wool"},
    [
        box([0, 0, 0], [16, 1, 16], "ground"),
        box([1, 1, 1], [15, 4, 15], "tent"),
        box([2.5, 4, 1], [13.5, 7, 15], "tent"),
        box([4, 7, 1], [12, 10, 15], "tent"),
        box([5.5, 10, 1], [10.5, 12.5, 15], "tent"),
        box([7, 12.5, 1], [9, 14, 15], "tent"),
        box([6, 1, 15], [10, 6, 15.3], "flap"),
        box([7.5, 14, 0.5], [8.5, 15, 15.5], "pole"),
        box([7.6, 15, 13.6], [8.4, 16, 14.4], "pole"),
        box([8.4, 15, 13.75], [11, 16, 14.25], "flag"),
    ])


def cross_ns(z1, z2):
    return [box([7, 4, z1], [9, 11, z2], "cross"), box([4.5, 6.5, z1], [11.5, 8.5, z2], "cross")]


def cross_we(x1, x2):
    return [box([x1, 4, 7], [x2, 11, 9], "cross"), box([x1, 6.5, 4.5], [x2, 8.5, 11.5], "cross")]


M["hospital_station"] = model(
    {"particle": "white_concrete", "wall": "white_concrete", "base": "smooth_stone_slab_side", "base_top": "smooth_stone",
     "trim": "light_gray_concrete", "cross": "red_concrete"},
    [
        box([0, 0, 0], [16, 2, 16], {"side": "base", "up": "base_top", "down": "base_top"}),
        box([1, 2, 1], [15, 13, 15], "wall"),
        box([0.5, 13, 0.5], [15.5, 14, 15.5], "trim"),
        box([3, 14, 3], [13, 15, 13], "wall"),
        box([7, 15, 4.5], [9, 15.5, 11.5], "cross"),
        box([4.5, 15, 7], [11.5, 15.5, 9], "cross"),
    ] + cross_ns(15, 15.4) + cross_ns(0.6, 1) + cross_we(15, 15.4) + cross_we(0.6, 1))

M["courier_station"] = model(
    {"particle": "spruce_planks", "bed": "spruce_planks", "wheel": "dark_oak_log_top", "rim": "dark_oak_log", "handle": "stripped_oak_log",
     "parcel": "brown_wool", "string": "white_wool", "box": "light_gray_wool", "barrel": "barrel_side", "barrel_top": "barrel_top",
     "hay": "hay_block_side", "hay_top": "hay_block_top"},
    [
        box([1, 5, 2], [15, 9, 14], "bed"),
        wheel([0, 0, 5], [1, 7, 12], "wheel", "rim"),
        wheel([15, 0, 5], [16, 7, 12], "wheel", "rim"),
        box([1, 3, 8], [15, 4, 9], "rim"),
        box([3, 7, 14], [4, 8, 16], "handle"), box([12, 7, 14], [13, 8, 16], "handle"),
        box([4, 7, 15], [12, 8, 16], "handle"),
        box([7.5, 0, 12.5], [8.5, 5, 13.5], "handle"),
        box([2.5, 9, 3.5], [8, 13, 8.5], "parcel"),
        box([5, 9, 3.4], [5.5, 13.1, 8.6], "string"),
        box([2.4, 9, 5.75], [8.1, 13.1, 6.25], "string"),
        box([3.5, 9, 9], [7.5, 11.5, 12.5], "box"),
        box([9.5, 9, 3.5], [13.5, 15, 7.5], {"side": "barrel", "up": "barrel_top", "down": "barrel_top"}),
        box([9.5, 9, 9], [13.5, 12, 12.5], {"side": "hay", "up": "hay_top", "down": "hay_top"}),
    ])

M["enchanter_station"] = model(
    {"particle": "bookshelf", "base": "obsidian", "shelf": "bookshelf", "wood": "oak_planks", "cloth": "enchanting_table_top",
     "cloth_side": "red_wool", "gem": "lapis_block", "page": "white_wool", "cover": "purple_wool"},
    [
        box([0, 0, 0], [16, 3, 16], "base"),
        box([1, 3, 1], [15, 11, 15], {"side": "shelf", "up": "wood", "down": "wood"}),
        box([0.5, 11, 0.5], [15.5, 13, 15.5], {"side": "cloth_side", "up": "cloth", "down": "cloth_side"}),
        box([1, 13, 1], [3, 15, 3], "gem"), box([13, 13, 1], [15, 15, 3], "gem"),
        box([1, 13, 13], [3, 15, 15], "gem"), box([13, 13, 13], [15, 15, 15], "gem"),
        box([5, 13, 6.5], [11, 13.5, 10.5], "cover"),
        box([4.5, 13.5, 7], [8, 14, 10], "page", rotation={"origin": [8, 13.5, 8.5], "axis": "z", "angle": -22.5}),
        box([8, 13.5, 7], [11.5, 14, 10], "page", rotation={"origin": [8, 13.5, 8.5], "axis": "z", "angle": 22.5}),
    ])

M["settlement_banner"] = model(
    {"particle": "blue_wool", "plinth": "stone_bricks", "step": "polished_andesite", "pole": "dark_oak_log", "pole_top": "dark_oak_log_top",
     "finial": "gold_block", "flag": "blue_wool", "stripe": "yellow_wool"},
    [
        box([1, 0, 1], [15, 2, 15], "plinth"),
        box([3, 2, 3], [13, 3, 13], "step"),
        box([7, 3, 7], [9, 15, 9], {"side": "pole", "up": "pole_top", "down": "pole_top"}),
        box([6.5, 15, 6.5], [9.5, 16, 9.5], "finial"),
        box([9, 7.5, 7.6], [15.5, 14.5, 8.4], "flag"),
        box([9.05, 10.25, 7.5], [15.45, 11.75, 8.5], "stripe"),
    ])

os.makedirs(os.path.join(OUT, "models/block"), exist_ok=True)
os.makedirs(os.path.join(OUT, "blockstates"), exist_ok=True)
for name, data in M.items():
    with open(os.path.join(OUT, "models/block", name + ".json"), "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        variant = {"model": "wwmc:block/" + name}
        if y:
            variant["y"] = y
        variants["facing=" + facing] = variant
    with open(os.path.join(OUT, "blockstates", name + ".json"), "w") as f:
        json.dump({"variants": variants}, f, indent=2)
        f.write("\n")
print(len(M), "models")
