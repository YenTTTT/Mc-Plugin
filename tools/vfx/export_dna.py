"""Export the forty-three Java DNA constants for designers; this is not a generic Java parser."""
from pathlib import Path
import csv
import json
import re

root = Path(__file__).resolve().parents[2]
source = (root / 'src/main/java/com/customrpg/vfx/VfxEffects.java').read_text(encoding='utf-8')
ids = dict(re.findall(r'(SLASH|THUNDER|VOID)="([^"]+)"', source))
rows = []
for symbol, body, budget in re.findall(r'designs\.put\((SLASH|THUNDER|VOID),new Dna\(\1,(.*?),(\d+)\)\);', source, re.S):
    values = [json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', body)]
    assert len(values) == 11, (symbol, values)
    rows.append([ids[symbol], *values, int(budget), 'NEEDS_IN_GAME_VISUAL_REVIEW'])
for name in ['FrostVfx.java','EarthVfx.java','DarkVfx.java','NecroVfx.java','ChickenVfx.java']:
    source = (root / 'src/main/java/com/customrpg/vfx' / name).read_text(encoding='utf-8')
    for skill, body, budget in re.findall(r'new VfxEffects\.Dna\("([^"\n]+)",(.*?),(\d+)\)', source, re.S):
        values = [json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', body)]
        assert len(values) == 11, (skill, values)
        rows.append([skill, *values, int(budget), 'NEEDS_IN_GAME_VISUAL_REVIEW'])
assert len(rows) == 43
with (root / 'docs/data/VFX_DNA.csv').open('w', encoding='utf-8', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['skill_id', 'name', 'combat_role', 'theme', 'signature_moment', 'primary_silhouette', 'motion_language',
                     'temporal_rhythm', 'impact_feedback', 'decoration', 'audio', 'difference', 'max_samples_per_tick', 'status'])
    writer.writerows(rows)
print('Exported 43 independent VFX DNA definitions with enforceable sample budgets.')
