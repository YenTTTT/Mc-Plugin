"""Inventory real bundled IDs, cross-check existing design data, and classify renderer reuse. Stdlib only."""
from pathlib import Path
import csv
import re

root = Path(__file__).resolve().parents[2]
resources = root / 'src/main/resources/config'
with (root / 'docs/data/TALENT_REDESIGN_MAPPING.csv').open(encoding='utf-8-sig', newline='') as f:
    legacy = list(csv.DictReader(f))
actual = set()
for name in ['talents.yml', 'talents_additional.yml', 'talents_hunter.yml', 'talents_frost.yml', 'talents_earth.yml', 'talents_dark.yml', 'talents_necro.yml', 'talents_chicken.yml']:
    actual.update(re.findall(r'^  ([a-z][a-z0-9_]*):\s*$', (resources / name).read_text(encoding='utf-8'), re.M))
with (root / 'docs/data/VFX_DNA.csv').open(encoding='utf-8-sig', newline='') as f:
    redesigned = {n['skill_id']: n for n in csv.DictReader(f) if n['skill_id'].startswith(('frost_','earth_','dark_','necro_','chicken_'))}
dark_source = (root/'src/main/java/com/customrpg/talents/DarkSkill.java').read_text(encoding='utf-8')
dark_ids = dict(re.findall(r'^    ([A-Z]+)\("([^"\n]+)"', dark_source, re.M))
aliases = {old: dark_ids[current] for old,current in re.findall(r'Map\.entry\("([a-z_]+)",([A-Z]+)\.id\)',dark_source)}
assert len(aliases)==12
actual.difference_update(aliases)
assert actual == ({n['legacy_id'] for n in legacy} - set(aliases)) | set(redesigned) and len(actual)==220
rows = []
for n in legacy:
    if n['legacy_id'] in aliases: continue
    branch = n['legacy_branch']
    if n['legacy_id'] in redesigned:
        dna = redesigned[n['legacy_id']]
        rows.append([n['legacy_id'], dna['name'], {'necro':'necromancer'}.get(dna['skill_id'].split('_')[0],dna['skill_id'].split('_')[0]), 'ACTIVE', {'earth':'EarthTalentSkills','frost':'FrostTalentSkills','dark':'DarkTalentSkills','necro':'NecroTalentSkills','chicken':'ChickenTalentSkills'}[dna['skill_id'].split('_')[0]], dna['primary_silhouette'], 'INDEPENDENT_BRANCH_ANIMATION', 'NEEDS_IN_GAME_VISUAL_REVIEW'])
        continue
    engine = 'HunterTalentSkills' if branch == 'hunter' else 'ExpandedTalentSkills' if branch in ['frost', 'storm', 'earth', 'light'] else 'TalentSkillManager'
    active = n['legacy_type'] == 'ACTIVE'
    family = 'character_style_pulse' if branch == 'hunter' else 'ring_or_beam' if engine == 'ExpandedTalentSkills' else 'individual_execute_method'
    rows.append([n['legacy_id'], n['legacy_name'], branch, n['legacy_type'], engine, family if active else 'passive_or_training',
                 'HIGH_SHARED_RENDERER' if active and engine != 'TalentSkillManager' else 'REVIEW_REQUIRED' if active else 'NOT_MANUAL_CAST', 'NOT_MIGRATED'])
old_ids = {n['legacy_id'] for n in legacy}
for skill, dna in redesigned.items():
    if skill not in old_ids:
        rows.append([skill, dna['name'], {'necro':'necromancer'}.get(dna['skill_id'].split('_')[0],dna['skill_id'].split('_')[0]), 'ACTIVE', {'earth':'EarthTalentSkills','frost':'FrostTalentSkills','dark':'DarkTalentSkills','necro':'NecroTalentSkills','chicken':'ChickenTalentSkills'}[dna['skill_id'].split('_')[0]], dna['primary_silhouette'], 'INDEPENDENT_BRANCH_ANIMATION', 'NEEDS_IN_GAME_VISUAL_REVIEW'])
for directory, engine in [('weapons/skills', 'weaponSkills.ConfigDrivenWeaponSkill'), ('skills', 'legacy.SkillManager')]:
    for path in sorted((resources / directory).glob('*.yml')):
        for skill in re.findall(r'^([a-z][a-z0-9_]*):\s*$', path.read_text(encoding='utf-8'), re.M):
            rows.append([skill, skill, directory, 'ACTIVE', engine, 'configured_animation_style', 'REVIEW_REQUIRED', 'NOT_MIGRATED'])
race_source = (root / 'src/main/java/com/customrpg/races/RacialSkill.java').read_text(encoding='utf-8')
for race, name in re.findall(r'^    [A-Z_]+\("([^"]+)", "([^"]+)"', race_source, re.M):
    rows.append([race, name, 'racial', 'ACTIVE', 'RacialSkillManager', 'racial_specific', 'REVIEW_REQUIRED', 'NOT_MIGRATED'])
path = root / 'docs/data/VFX_SKILL_INVENTORY.csv'
with path.open('w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['skill_id', 'name', 'family', 'type', 'execution', 'visual_family', 'reuse_risk', 'status'])
    writer.writerows(rows)
print(f'Validated 220 active-catalog talent IDs; exported {len(rows)} definitions across talent, weapon, legacy item and racial systems.')

by_old={n['legacy_id']:n for n in legacy}
with (root/'docs/data/DARK_LEGACY_MAPPING.csv').open('w',encoding='utf-8',newline='') as f:
    writer=csv.writer(f);writer.writerow(['legacy_id','legacy_name','current_id','current_name','rank_policy','data_policy'])
    for old,current in sorted(aliases.items()):writer.writerow([old,by_old[old]['legacy_name'],current,redesigned[current]['name'],'MAX_MERGED_RANK_1_TO_3','PRESERVE_RAW_RANK_POINTS_AND_BINDINGS'])
print('Validated all twelve Dark legacy aliases; raw progression remains preserved.')
