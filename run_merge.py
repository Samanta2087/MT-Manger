import sys, json
from pathlib import Path

ast = json.loads(Path('graphify-out/.graphify_ast.json').read_text())
sem = {'nodes':[],'edges':[],'hyperedges':[], 'input_tokens':0, 'output_tokens':0}
Path('graphify-out/.graphify_semantic.json').write_text(json.dumps(sem, indent=2))

merged_nodes = list(ast['nodes'])
merged_edges = ast['edges']
merged = {
    'nodes': merged_nodes,
    'edges': merged_edges,
    'hyperedges': [],
    'input_tokens': 0,
    'output_tokens': 0,
}
Path('graphify-out/.graphify_extract.json').write_text(json.dumps(merged, indent=2))
print(f'Merged: {len(merged_nodes)} nodes, {len(merged_edges)} edges')
