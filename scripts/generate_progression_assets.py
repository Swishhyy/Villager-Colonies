"""Generate WWMC's original pixel textures, models and data for tin, bronze and research.

Run from any directory with Python and Pillow. Minecraft assets are not copied or recolored.
"""
from pathlib import Path
import json
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/wwmc'
DATA = ROOT / 'data'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def tag(namespace, kind, name, values):
    write(DATA / namespace / 'tags' / kind / (name + '.json'), {'replace': False, 'values': values})

def recipe(name, value):
    write(DATA / 'wwmc/recipe' / (name + '.json'), value)

def shaped(name, pattern, key, result=None, count=1):
    recipe(name, {'type':'minecraft:crafting_shaped', 'category':'misc', 'pattern':pattern, 'key':key,
                  'result':{'id':result or 'wwmc:' + name, 'count':count}})

def shapeless(name, ingredients, result, count=1):
    recipe(name, {'type':'minecraft:crafting_shapeless', 'category':'misc', 'ingredients':ingredients,
                  'result':{'id':result, 'count':count}})

def item_model(name, texture=None, parent=None):
    model = {'parent':parent} if parent else {'parent':'minecraft:item/handheld' if name.startswith('bronze_') and name.split('_')[-1] in ('sword','pickaxe','axe','shovel','hoe') else 'minecraft:item/generated',
                                             'textures':{'layer0':texture or 'wwmc:item/' + name}}
    write(ASSETS / 'models/item' / (name + '.json'), model)
    write(ASSETS / 'items' / (name + '.json'), {'model':{'type':'minecraft:model','model':'wwmc:item/' + name}})

def save_texture(name, image, folder='item'):
    path = ASSETS / 'textures' / folder / (name + '.png')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

def pixel_item(name, metal):
    im = Image.new('RGBA',(16,16)); d=ImageDraw.Draw(im)
    dark=tuple(int(c*.48) for c in metal); bright=tuple(min(255,int(c*1.2)+10) for c in metal)
    wood=(104,70,40); edge=(48,35,25)
    if name.endswith('ingot'):
        d.polygon([(2,8),(5,4),(13,4),(14,8),(11,12),(3,12)],fill=dark)
        d.polygon([(3,8),(6,5),(12,5),(12,8),(10,10),(4,10)],fill=metal); d.line([(4,7),(6,5),(11,5)],fill=bright)
    elif name=='raw_tin':
        d.polygon([(2,8),(4,4),(9,2),(13,5),(14,10),(10,14),(4,12)],fill=dark)
        for box,col in [((4,5,8,10),metal),((8,4,11,7),bright),((8,9,12,12),metal)]:d.rectangle(box,fill=col)
    elif name=='bronze_blend':
        for x,y in [(4,6),(8,4),(10,8),(5,11)]:
            d.rectangle((x-2,y-1,x+2,y+1),fill=dark); d.rectangle((x-1,y-2,x+1,y),fill=metal)
        d.rectangle((8,8,10,10),fill=(204,214,218))
    elif name.endswith(('sword','pickaxe','axe','shovel','hoe')):
        d.line((3,13,10,6), fill=edge, width=3); d.line((3,13,10,6), fill=wood, width=1)
        tool=name.split('_')[-1]
        points={'sword':[(6,8),(11,3),(13,2),(13,5),(8,10)],'pickaxe':[(3,5),(6,2),(10,2),(14,5),(14,8),(12,6),(9,5),(6,4)],
                'axe':[(7,2),(11,2),(14,5),(13,8),(9,7),(7,5)],'shovel':[(9,2),(13,2),(14,5),(11,8),(8,5)],
                'hoe':[(3,3),(10,3),(13,6),(11,8),(9,6),(4,6)]}[tool]
        d.polygon(points,fill=dark); d.line(points[:-1], fill=metal, width=2)
        d.line((points[0],points[1]), fill=bright, width=1)
        if tool=='sword':d.line((5,7,9,11),fill=dark,width=2)
    else:
        armor=name.split('_')[-1]
        points={'helmet':[(3,3),(12,3),(14,6),(14,11),(11,12),(11,8),(5,8),(5,12),(2,11),(2,6)],
                'chestplate':[(2,3),(5,2),(6,5),(9,5),(10,2),(13,3),(15,7),(12,8),(12,14),(3,14),(3,8),(0,7)],
                'leggings':[(3,2),(12,2),(12,14),(8,14),(8,7),(7,7),(7,14),(3,14)],
                'boots':[(3,3),(6,3),(6,10),(7,11),(7,14),(1,14),(1,10),(3,10)]}[armor]
        d.polygon(points, fill=dark); d.line(points,fill=metal,width=2)
        d.line((points[0],points[1]),fill=bright)
        if armor=='boots':im.alpha_composite(im.copy(),(8,0))
    return im

def tile(name, base, ore=False):
    rng=random.Random(name); im=Image.new('RGB',(16,16),base)
    for y in range(16):
        for x in range(16):
            shade=rng.randint(-13,13)
            im.putpixel((x,y), tuple(max(0,min(255,c+shade)) for c in base))
    d=ImageDraw.Draw(im)
    if ore:
        for x,y in [(2,3),(10,2),(6,7),(12,10),(2,12)]:
            d.rectangle((x,y,x+2,y+2),fill=(120,139,146)); d.point((x,y),fill=(220,228,225)); d.point((x+1,y+1),fill=(175,193,196))
    else:
        d.rectangle((0,0,15,15),outline=tuple(max(0,c-35) for c in base)); d.line((1,1,14,1),fill=tuple(min(255,c+30) for c in base))
    save_texture(name, im, 'block')

def cube(lo, hi, texture):
    return {'from':lo,'to':hi,'faces':{face:{'texture':'#'+texture} for face in ('down','up','north','south','west','east')}}

def block(name):
    write(ASSETS / 'models/block' / (name+'.json'), {'parent':'minecraft:block/cube_all','textures':{'all':'wwmc:block/'+name}})
    write(ASSETS / 'blockstates' / (name+'.json'), {'variants':{'':{'model':'wwmc:block/'+name}}})
    item_model(name,parent='wwmc:block/'+name)

def carcass(kind, skin):
    textures={'particle':skin,'skin':skin,'hoof':'minecraft:block/black_wool','flesh':'minecraft:block/white_terracotta'}
    if kind in ('cod','salmon'):
        parts=[cube([3,5,6],[12,8,10],'skin'),cube([1,5,6],[4,8,10],'flesh'),cube([12,5,5],[15,8,11],'skin'),cube([6,7.5,7],[9,9,9],'skin')]
    else:
        small=kind in ('chicken','rabbit')
        parts=[cube([3,3,5],[10 if small else 12,8,11],'skin'),cube([10,3.5,5.5],[14,7.5,10.5],'skin')]
        for x in (4,8 if small else 10):
            for z in (3.5,10):parts.extend([cube([x,2,z],[x+1.5,4,z+2.5],'skin'),cube([x,2,z],[x+1.5,3,z+1],'hoof')])
        if kind in ('cow','sheep'):parts.extend([cube([12,7,5],[13,9,6],'flesh'),cube([12,7,10],[13,9,11],'flesh')])
        if kind=='rabbit':parts.extend([cube([12,7,6],[13,11,7],'skin'),cube([12,7,9],[13,11,10],'skin')])
        if kind=='chicken':parts.extend([cube([13,4,5],[15,6,6],'flesh'),cube([11,7.5,6],[13,9,7],'flesh')])
    model={'parent':'minecraft:block/block','textures':textures,'elements':parts,
           'display':{'gui':{'rotation':[25,-35,0],'translation':[0,1,0],'scale':[.95,.95,.95]},
                      'ground':{'translation':[0,1,0],'scale':[.6,.6,.6]},'fixed':{'rotation':[0,90,0],'scale':[.8,.8,.8]},
                      'thirdperson_righthand':{'rotation':[70,0,0],'translation':[0,2,0],'scale':[.55,.55,.55]},
                      'firstperson_righthand':{'rotation':[0,-30,0],'translation':[0,2,0],'scale':[.75,.75,.75]}}}
    write(ASSETS / 'models/item' / (kind+'_carcass.json'),model)

def main():
    bronze=(189,124,57); tin=(171,188,193)
    names=['raw_tin','tin_ingot','bronze_blend','bronze_ingot']+['bronze_'+part for part in ['sword','pickaxe','axe','shovel','hoe','helmet','chestplate','leggings','boots']]
    for name in names:
        save_texture(name,pixel_item(name,tin if 'tin' in name else bronze)); item_model(name)
    for name,base,ore in [('tin_ore',(113,114,111),True),('deepslate_tin_ore',(70,71,73),True),('raw_tin_block',(137,155,156),True),('tin_block',tin,False),('bronze_block',bronze,False)]:
        tile(name,base,ore); block(name)
        entry={'type':'minecraft:item','name':'wwmc:'+name}
        if name.endswith('ore'):
            entry={'type':'minecraft:alternatives','children':[
                {'type':'minecraft:item','name':'wwmc:'+name,'conditions':[{'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}}]},
                {'type':'minecraft:item','name':'wwmc:raw_tin','functions':[{'function':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},{'function':'minecraft:explosion_decay'}]}]}
        write(DATA / 'wwmc/loot_table/blocks' / (name+'.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[entry],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
    for base in ('tin','bronze'):
        shaped(base+'_block',['III','III','III'],{'I':'wwmc:'+base+'_ingot'})
        shapeless(base+'_ingot_from_block',['wwmc:'+base+'_block'],'wwmc:'+base+'_ingot',9)
    shaped('raw_tin_block',['III','III','III'],{'I':'wwmc:raw_tin'})
    shapeless('raw_tin_from_block',['wwmc:raw_tin_block'],'wwmc:raw_tin',9)
    shapeless('bronze_blend',['minecraft:copper_ingot']*3+['wwmc:tin_ingot'],'wwmc:bronze_blend',4)
    for name,ingredient,result in [('tin_from_raw','wwmc:raw_tin','wwmc:tin_ingot'),('tin_from_ore','wwmc:tin_ore','wwmc:tin_ingot'),
                                    ('tin_from_deepslate','wwmc:deepslate_tin_ore','wwmc:tin_ingot'),('bronze_from_blend','wwmc:bronze_blend','wwmc:bronze_ingot')]:
        for cooking,duration in [('smelting',200),('blasting',100)]:
            recipe(name+'_'+cooking,{'type':'minecraft:'+cooking,'category':'misc','ingredient':ingredient,'result':{'id':result},'experience':.7,'cookingtime':duration})
    patterns={'sword':[' I ',' I ',' S '],'pickaxe':['III',' S ',' S '],'axe':['II ','IS ',' S '],
              'shovel':[' I ',' S ',' S '],'hoe':['II ',' S ',' S '],'helmet':['III','I I'],'chestplate':['I I','III','III'],
              'leggings':['III','I I','I I'],'boots':['I I','I I']}
    for part,pattern in patterns.items():
        key={'I':'wwmc:bronze_ingot'}
        if any('S' in row for row in pattern):key['S']='minecraft:stick'
        shaped('bronze_'+part,pattern,key)
    shaped('researcher_station',['PPP','PIP','PPP'],{'P':'#minecraft:planks','I':'minecraft:book'})
    for role,center in [('guard','minecraft:stone_sword'),('barracks','minecraft:stone_sword'),('butcher','minecraft:stone_axe'),('quarry','wwmc:bronze_pickaxe')]:
        shaped(role+'_station',['PPP','PIP','PPP'],{'P':'#minecraft:planks','I':center})
    elements=[cube([1,0,1],[3,12,3],'wood'),cube([13,0,1],[15,12,3],'wood'),cube([1,0,13],[3,12,15],'wood'),cube([13,0,13],[15,12,15],'wood'),
              cube([0,11,0],[16,13,16],'wood'),cube([2,2,4],[14,10,13],'shelf'),cube([4,13,5],[12,13.5,11],'cover'),
              cube([4.5,13.5,5.5],[7.75,14.3,10.5],'page'),cube([8.25,13.5,5.5],[11.5,14.3,10.5],'page'),cube([13,13,3],[14.5,15,4.5],'ink')]
    write(ASSETS/'models/block/researcher_station.json',{'parent':'minecraft:block/block','textures':{'particle':'minecraft:block/bookshelf','wood':'minecraft:block/oak_planks','shelf':'minecraft:block/bookshelf','cover':'minecraft:block/blue_wool','page':'minecraft:block/white_wool','ink':'minecraft:block/black_wool'},'elements':elements})
    write(ASSETS/'blockstates/researcher_station.json',{'variants':{'facing='+direction:{'model':'wwmc:block/researcher_station','y':angle} for direction,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    item_model('researcher_station',parent='wwmc:block/researcher_station')
    write(DATA/'wwmc/loot_table/blocks/researcher_station.json',json.loads((DATA/'wwmc/loot_table/blocks/enchanter_station.json').read_text().replace('enchanter_station','researcher_station')))
    for kind,skin in [('cow','brown_wool'),('pig','pink_wool'),('sheep','white_wool'),('chicken','white_wool'),('rabbit','brown_wool'),('cod','brown_terracotta'),('salmon','pink_terracotta')]:carcass(kind,'minecraft:block/'+skin)
    for name in ('humanoid','humanoid_leggings'):
        armor=Image.new('RGBA',(64,32)); d=ImageDraw.Draw(armor)
        for y in range(32):
            for x in range(64):
                shade=(-24 if x%8==0 or y%8==0 else 18 if x%8==1 or y%8==1 else 0)
                d.point((x,y),fill=tuple(max(0,min(255,c+shade)) for c in bronze)+(255,))
        save_texture('bronze',armor,'entity/equipment/'+name)
    write(ASSETS/'equipment/bronze.json',{'layers':{'humanoid':[{'texture':'wwmc:bronze'}],'humanoid_leggings':[{'texture':'wwmc:bronze'}]}})
    for namespace,kind,name,values in [('c','item','ingots/tin',['wwmc:tin_ingot']),('c','item','ingots/bronze',['wwmc:bronze_ingot']),
         ('c','item','ingots',['#c:ingots/tin','#c:ingots/bronze']),('c','block','ores/tin',['wwmc:tin_ore','wwmc:deepslate_tin_ore']),
         ('c','block','ores',['#c:ores/tin']),('c','item','ores/tin',['wwmc:tin_ore','wwmc:deepslate_tin_ore']),('c','item','ores',['#c:ores/tin']),
         ('c','item','raw_materials/tin',['wwmc:raw_tin']),('c','item','raw_materials',['#c:raw_materials/tin'])]:tag(namespace,kind,name,values)
    tag('minecraft','block','mineable/pickaxe',['wwmc:'+n for n in ['tin_ore','deepslate_tin_ore','raw_tin_block','tin_block','bronze_block']])
    tag('minecraft','block','needs_stone_tool',['wwmc:tin_ore','wwmc:deepslate_tin_ore','wwmc:tin_block','wwmc:raw_tin_block','wwmc:bronze_block'])
    for part,group in [('sword','swords'),('pickaxe','pickaxes'),('axe','axes'),('shovel','shovels'),('hoe','hoes')]:tag('minecraft','item',group,['wwmc:bronze_'+part])
    for enchant,parts in [('sword',['sword']),('sharp_weapon',['sword','axe']),('mining',['pickaxe','axe','shovel','hoe']),('mining_loot',['pickaxe','axe','shovel','hoe']),('durability',list(patterns)),
                         ('armor',['helmet','chestplate','leggings','boots']),('head_armor',['helmet']),('chest_armor',['chestplate']),('leg_armor',['leggings']),('foot_armor',['boots'])]:
        tag('minecraft','item','enchantable/'+enchant,['wwmc:bronze_'+p for p in parts])
    gear=['sword','pickaxe','axe','shovel','hoe','helmet','chestplate','leggings','boots','spear']
    def optional(id):return {'id':id,'required':False}
    tag('wwmc','item','requires_bronze',['wwmc:bronze_'+p for p in list(patterns)]+['wwmc:bronze_blend','wwmc:bronze_ingot','wwmc:bronze_block','wwmc:quarry_station']+[optional('minecraft:copper_'+p) for p in gear])
    tag('wwmc','item','requires_iron',[optional('minecraft:'+m+'_'+p) for m in ('iron','golden') for p in gear]+['minecraft:chainmail_'+p for p in ['helmet','chestplate','leggings','boots']]
        +['minecraft:bucket','minecraft:water_bucket','minecraft:lava_bucket','minecraft:milk_bucket','minecraft:powder_snow_bucket','minecraft:cod_bucket','minecraft:salmon_bucket','minecraft:pufferfish_bucket','minecraft:tropical_fish_bucket','minecraft:axolotl_bucket','minecraft:tadpole_bucket',
          'minecraft:shears','minecraft:flint_and_steel','minecraft:shield','minecraft:crossbow','minecraft:trident','minecraft:anvil','minecraft:chipped_anvil','minecraft:damaged_anvil','minecraft:blast_furnace','minecraft:smithing_table','wwmc:blacksmith_station'])
    tag('wwmc','item','requires_gemcraft',[optional('minecraft:diamond_'+p) for p in gear]+['minecraft:enchanting_table','minecraft:mace','wwmc:enchanter_station'])
    tag('wwmc','item','requires_netherite',[optional('minecraft:netherite_'+p) for p in gear])
    configured={'type':'minecraft:ore','config':{'size':7,'discard_chance_on_air_exposure':0.0,'targets':[
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'},'state':{'Name':'wwmc:tin_ore'}},
        {'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'},'state':{'Name':'wwmc:deepslate_tin_ore'}}]}}
    write(DATA/'wwmc/worldgen/configured_feature/tin_ore.json',configured)
    write(DATA/'wwmc/worldgen/placed_feature/tin_ore.json',{'feature':'wwmc:tin_ore','placement':[{'type':'minecraft:count','count':8},{'type':'minecraft:in_square'},
        {'type':'minecraft:height_range','height':{'type':'minecraft:uniform','min_inclusive':{'absolute':-32},'max_inclusive':{'absolute':64}}},{'type':'minecraft:biome'}]})
    write(DATA/'wwmc/neoforge/biome_modifier/tin_ore.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':'wwmc:tin_ore','step':'underground_ores'})
    lang_path=ASSETS/'lang/en_us.json'; lang=json.loads(lang_path.read_text())
    for name in names:lang['item.wwmc.'+name]=name.replace('_',' ').title()
    for name in ['tin_ore','deepslate_tin_ore','raw_tin_block','tin_block','bronze_block','researcher_station']:lang['block.wwmc.'+name]=name.replace('_',' ').title()
    write(lang_path,lang)

if __name__=='__main__':main()
