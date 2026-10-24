"""Historical design export only. Core talents were retired; never write runtime resources."""
from pathlib import Path
import csv
import json

root=Path(__file__).resolve().parents[2]
with (root/'docs/data/TALENT_REDESIGN_NODES.csv').open(encoding='utf-8-sig',newline='') as f:
    nodes=list(csv.DictReader(f))
assert len(nodes)==len({n['node_id'] for n in nodes})==80
slots={1:37,2:39,3:19,4:21,5:23,6:25,7:43,8:28,9:13,10:4}
lines=['# Eight core trees. Legacy talents and player ranks are never overwritten.','core:']
for n in nodes:
    index=int(n['node_id'][1:])
    data=dict(code=n['node_id'],theme=n['theme'],name=n['name'],description=n['effect'],kind=n['kind'],
        cost=int(n['point_cost']),level=int(n['min_player_level']),**{'theme-points':int(n['min_theme_points'])},
        cooldown=int(n['cooldown_s']),mana=int(n['mana_cost']),hp=int(n['hp_cost_percent'])/100,
        **{'current-hp-cost':n['hp_cost_basis']=='CURRENT_HP'},slot=41 if n['node_id']=='W05' else slots[index],
        prerequisites=list(filter(None,n['prerequisites'].split(';'))),**{'prerequisite-mode':n['prerequisite_mode']})
    lines.append('  '+n['proposed_runtime_id']+':')
    for key,value in data.items():
        lines.append('    '+key+': '+json.dumps(value,ensure_ascii=False))
(root/'docs/data/TALENT_CORE_RETIRED.yml').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('Exported 80 retired design definitions to docs only; not loaded by the plugin.')
