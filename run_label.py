import sys, json
from graphify.build import build_from_json
from graphify.cluster import score_all
from graphify.analyze import god_nodes, surprising_connections, suggest_questions
from graphify.report import generate
from pathlib import Path
import re
from collections import Counter

extraction = json.loads(Path('graphify-out/.graphify_extract.json').read_text())
detection  = json.loads(Path('graphify-out/.graphify_detect.json').read_text())
analysis   = json.loads(Path('graphify-out/.graphify_analysis.json').read_text())

G = build_from_json(extraction)
communities = {int(k): v for k, v in analysis['communities'].items()}
cohesion = {int(k): v for k, v in analysis['cohesion'].items()}
tokens = {'input': 0, 'output': 0}

labels = {}
for cid, nodes in communities.items():
    words = []
    for node in nodes:
        node_data = G.nodes[node]
        label = node_data.get('label', '')
        parts = re.split(r'[_A-Z\.\-\\\/]', label)
        words.extend([p.lower() for p in parts if len(p) > 2])
    if not words:
        labels[cid] = f'Core System {cid}'
    else:
        most_common = Counter(words).most_common(2)
        labels[cid] = ' '.join(w[0].title() for w in most_common) + ' Module'

questions = suggest_questions(G, communities, labels)

report = generate(G, communities, cohesion, labels, analysis['gods'], analysis['surprises'], detection, tokens, str(Path('.').absolute()), suggested_questions=questions)
Path('graphify-out/GRAPH_REPORT.md').write_text(report, encoding='utf-8')
Path('graphify-out/.graphify_labels.json').write_text(json.dumps({str(k): v for k, v in labels.items()}))
print('Report updated with community labels')
