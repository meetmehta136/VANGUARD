import json
meta = json.load(open('ml/fraud/models/model_metadata.json'))
with open('ml/fraud/data/feature_columns.txt', 'w') as f:
    for name in meta['feature_names']:
        f.write(name + '\n')
print(f"Updated feature_columns.txt with {len(meta['feature_names'])} features")
