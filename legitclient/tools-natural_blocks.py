"""Where Minecraft 1.21.11 generates each block by itself, from its own data:
  - structure templates (data/minecraft/structure/<structure>/*.nbt): natural only in that structure,
  - worldgen JSON (features, surface rules, carvers): natural everywhere ("terrain"),
  - processor lists (block swaps inside structures), assigned to their structure by name,
  - structures and terrain built in code (mineshaft, stronghold, fortress, monument, temples, dungeon...), listed by hand.
Writes block_groups.txt for the mod: one line per block that is NOT natural everywhere, "<id> <structures>" ("-" = never
generated, player-only). Blocks natural everywhere are left out."""
import collections
import glob
import gzip
import io
import re
import struct
import zipfile

jar = [j for j in glob.glob(r'C:/Users/PC/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common/**/*.jar', recursive=True) if 'sources' not in j][0]
client = [j for j in glob.glob(r'C:/Users/PC/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly/**/*.jar', recursive=True) if 'sources' not in j][0]
z = zipfile.ZipFile(jar)
BLOCKS = set(n[len('assets/minecraft/blockstates/'):-5] for n in zipfile.ZipFile(client).namelist()
             if n.startswith('assets/minecraft/blockstates/') and n.endswith('.json'))


def nbt_strings(data):
    out = []
    b = io.BytesIO(data)

    def rd(n): return b.read(n)

    def string():
        (ln,) = struct.unpack('>H', rd(2))
        return rd(ln).decode('utf-8', 'replace')

    def payload(t):
        if t == 1: rd(1)
        elif t == 2: rd(2)
        elif t == 3: rd(4)
        elif t == 4: rd(8)
        elif t == 5: rd(4)
        elif t == 6: rd(8)
        elif t == 7: (n,) = struct.unpack('>i', rd(4)); rd(n)
        elif t == 8: out.append(string())
        elif t == 9:
            et = rd(1)[0]; (n,) = struct.unpack('>i', rd(4))
            for _ in range(n): payload(et)
        elif t == 10:
            while True:
                tt = rd(1)[0]
                if tt == 0: break
                string(); payload(tt)
        elif t == 11: (n,) = struct.unpack('>i', rd(4)); rd(4 * n)
        elif t == 12: (n,) = struct.unpack('>i', rd(4)); rd(8 * n)

    t = rd(1)[0]
    string()
    payload(t)
    return out


groups = collections.defaultdict(set)   # block -> structures it generates in ("terrain" = anywhere)


def ids(text):
    return [s for s in re.findall(r'minecraft:([a-z0-9_]+)', text) if s in BLOCKS]


PROCESSOR_OWNERS = [('trail_ruins', 'trail_ruins'), ('ancient_city', 'ancient_city'), ('trial_chambers', 'trial_chambers'),
                    ('bastion', 'bastion'), ('rampart', 'bastion'), ('bridge', 'bastion'), ('stable', 'bastion'), ('treasure', 'bastion'),
                    ('housing', 'bastion'), ('side_wall', 'bastion'), ('entrance', 'bastion'), ('bottom_rampart', 'bastion'),
                    ('high_wall', 'bastion'), ('high_rampart', 'bastion'), ('roof', 'bastion'), ('fossil', 'fossil'),
                    ('outpost', 'pillager_outpost'), ('zombie', 'village'), ('street', 'village'), ('farm', 'village'),
                    ('mossify', 'village'), ('ruined_portal', 'ruined_portal')]

for n in z.namelist():
    if n.startswith('data/minecraft/structure/') and n.endswith('.nbt'):
        parts = n.split('/')
        if len(parts) < 5: continue
        owner = parts[3]
        for s in nbt_strings(gzip.decompress(z.read(n))):
            m = re.match(r'minecraft:([a-z0-9_]+)', s)
            if m and m.group(1) in BLOCKS: groups[m.group(1)].add(owner)
    elif n.startswith('data/minecraft/worldgen/processor_list/'):
        name = n.rsplit('/', 1)[1][:-5]
        owner = next((o for k, o in PROCESSOR_OWNERS if k in name), 'unknown_structure')
        for s in ids(z.read(n).decode('utf-8')): groups[s].add(owner)
    elif n.startswith(('data/minecraft/worldgen/configured_feature/', 'data/minecraft/worldgen/placed_feature/',
                       'data/minecraft/worldgen/noise_settings/', 'data/minecraft/worldgen/configured_carver/')):
        for s in ids(z.read(n).decode('utf-8')): groups[s].add('terrain')

CODE = {
    'terrain': """stone granite diorite andesite deepslate tuff dirt grass_block coarse_dirt podzol mycelium sand red_sand gravel clay
        water lava bedrock obsidian ice packed_ice blue_ice snow snow_block powder_snow netherrack soul_sand soul_soil basalt
        blackstone end_stone fire soul_fire air cave_air void_air magma_block
        brain_coral brain_coral_block brain_coral_fan brain_coral_wall_fan bubble_coral bubble_coral_block bubble_coral_fan
        bubble_coral_wall_fan fire_coral fire_coral_block fire_coral_fan fire_coral_wall_fan horn_coral horn_coral_block
        horn_coral_fan horn_coral_wall_fan tube_coral tube_coral_block tube_coral_fan tube_coral_wall_fan
        dead_brain_coral dead_brain_coral_block dead_brain_coral_fan dead_brain_coral_wall_fan dead_bubble_coral
        dead_bubble_coral_block dead_bubble_coral_fan dead_bubble_coral_wall_fan dead_fire_coral dead_fire_coral_block
        dead_fire_coral_fan dead_fire_coral_wall_fan dead_horn_coral dead_horn_coral_block dead_horn_coral_fan
        dead_horn_coral_wall_fan dead_tube_coral dead_tube_coral_block dead_tube_coral_fan dead_tube_coral_wall_fan
        vine kelp kelp_plant seagrass tall_seagrass bubble_column bamboo bamboo_sapling bee_nest sculk sculk_vein sculk_sensor
        sculk_catalyst sculk_shrieker pale_hanging_moss open_eyeblossom closed_eyeblossom resin_clump creaking_heart
        dried_ghast frogspawn turtle_egg sniffer_egg lava_cauldron powder_snow_cauldron water_cauldron frosted_ice
        moving_piston nether_portal end_portal end_gateway light structure_void command_block chain_command_block
        repeating_command_block structure_block jigsaw test_block test_instance_block barrier""",
    'mineshaft': 'oak_planks oak_fence rail cobweb chest spawner dark_oak_planks dark_oak_fence',
    'stronghold': """stone_bricks mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks infested_stone infested_stone_bricks
        infested_mossy_stone_bricks infested_cracked_stone_bricks infested_chiseled_stone_bricks iron_bars oak_door iron_door
        stone_button ladder bookshelf end_portal_frame stone_slab smooth_stone_slab stone_brick_stairs stone_brick_slab
        cobblestone cobblestone_stairs torch wall_torch chest spawner oak_planks oak_fence cobweb""",
    'fortress': 'nether_bricks nether_brick_fence nether_brick_stairs nether_wart soul_sand chest spawner',
    'monument': 'prismarine prismarine_bricks dark_prismarine sea_lantern wet_sponge gold_block',
    'desert_pyramid': """sandstone chiseled_sandstone cut_sandstone sandstone_stairs sandstone_slab orange_terracotta blue_terracotta
        tnt stone_pressure_plate suspicious_sand chest""",
    'jungle_pyramid': """cobblestone mossy_cobblestone chiseled_stone_bricks stone_bricks cobblestone_stairs lever sticky_piston
        tripwire_hook tripwire redstone_wire repeater dispenser vine chest""",
    'swamp_hut': 'spruce_planks spruce_log spruce_stairs oak_fence flower_pot potted_red_mushroom crafting_table cauldron',
    'dungeon': 'cobblestone mossy_cobblestone spawner chest',
    'ruined_portal': 'crying_obsidian obsidian netherrack magma_block gold_block',
    'end_city': 'dragon_head dragon_wall_head',
}
for owner, text in CODE.items():
    for b in text.split():
        if b in BLOCKS: groups[b].add(owner)

lines = []
for b in sorted(BLOCKS):
    g = groups.get(b, set())
    if 'terrain' in g: continue
    lines.append(b + ' ' + (','.join(sorted(g)) if g else '-'))
out = r'C:/Users/PC/verity-dev/prismclient/src/client/resources/assets/prismclient/block_groups.txt'
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write('# Minecraft 1.21.11: blocks that do not generate everywhere, and the structures that generate them\n')
    f.write('# ("-" = never generated, player-only). Made from the game\'s own structure and worldgen files by natural_blocks.py.\n')
    f.write('\n'.join(lines) + '\n')
po = [l for l in lines if l.endswith(' -')]
print(len(BLOCKS), 'blocks;', len(lines), 'not natural everywhere;', len(po), 'player-only')
for probe in ['hopper', 'chest', 'torch', 'oak_planks', 'shulker_box', 'piston', 'observer', 'comparator', 'beacon', 'white_concrete',
              'barrel', 'furnace', 'crafting_table', 'red_bed', 'bell', 'rail', 'cobweb', 'enchanting_table', 'anvil', 'oak_sign',
              'glass', 'redstone_lamp', 'bookshelf', 'iron_block', 'lantern', 'gold_block']:
    print(probe, '->', 'terrain' if 'terrain' in groups.get(probe, set()) else (','.join(sorted(groups.get(probe, set()))) or 'PLAYER-ONLY'))
