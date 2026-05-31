import json, warnings
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from pathlib import Path

warnings.filterwarnings("ignore")

MODELS_DIR = Path(__file__).parent / "models"
DATA_DIR = Path(__file__).parent / "data"
OUTPUT = MODELS_DIR / "shap_importance.png"

with open(MODELS_DIR / "model_metadata.json") as f:
    meta = json.load(f)
feature_names = meta["feature_names"]
feature_count = meta["feature_count"]

import pandas as pd
df = pd.read_parquet(DATA_DIR / "processed_features.parquet")
X = df[feature_names].values.astype(np.float32)

import shap
import xgboost as xgb

model = xgb.XGBClassifier()
model.load_model(str(MODELS_DIR / "fraud_model.json"))

explainer = shap.TreeExplainer(model)
shap_values = explainer.shap_values(X)

if isinstance(shap_values, list):
    shap_values = shap_values[1]

plt.figure(figsize=(12, 10))
shap.summary_plot(shap_values, X, feature_names=feature_names, show=False)
plt.tight_layout()
plt.savefig(OUTPUT, dpi=150, bbox_inches="tight")
plt.close()
print(f"SHAP summary plot saved to {OUTPUT}")

plt.figure(figsize=(10, 8))
shap.summary_plot(shap_values, X, feature_names=feature_names, plot_type="bar", show=False)
plt.tight_layout()
plt.savefig(MODELS_DIR / "shap_importance_bar.png", dpi=150, bbox_inches="tight")
plt.close()
print("Bar plot saved to", MODELS_DIR / "shap_importance_bar.png")

mean_abs_shap = np.abs(shap_values).mean(axis=0)
ranking = sorted(zip(feature_names, mean_abs_shap), key=lambda x: -x[1])
print("\nTop 10 features by mean |SHAP|:")
for name, val in ranking[:10]:
    print(f"  {name:30s}  {val:.6f}")
