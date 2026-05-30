import pandas as pd
import numpy as np
import xgboost as xgb
import json
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    roc_auc_score, precision_recall_curve,
    auc, classification_report, confusion_matrix
)

print("=== VANGUARD XGBoost v2 — Balanced Undersampling ===\n")

# Load correct 22 features from metadata
meta = json.load(open('ml/fraud/models/model_metadata.json'))
feature_cols = meta['feature_names']
print(f"Using {len(feature_cols)} features")

df = pd.read_parquet('ml/fraud/data/processed_features.parquet')

fraud_df = df[df['isFraud'] == 1]
legit_df = df[df['isFraud'] == 0]

print(f"Fraud cases:     {len(fraud_df)}")
print(f"Legit cases:     {len(legit_df)}")

# Undersample legit — 10x fraud count
RATIO = 10
legit_sample = legit_df.sample(
    n=len(fraud_df) * RATIO,
    random_state=42
)

balanced_df = pd.concat([fraud_df, legit_sample]).sample(
    frac=1,
    random_state=42
)

print(f"Balanced dataset: {len(balanced_df)} rows")
print(f"Fraud rate after balance: {balanced_df['isFraud'].mean():.2%}\n")

test_fraud = fraud_df.sample(frac=0.3, random_state=42)
test_legit = legit_df.sample(n=100000, random_state=42)
test_df = pd.concat([test_fraud, test_legit], ignore_index=True)
print(f"Test set: {len(test_df)} rows (fraud={test_df['isFraud'].sum()}, {test_df['isFraud'].mean():.4%})")

train_legit = legit_df.drop(test_legit.index)
train_legit_sample = train_legit.sample(n=len(fraud_df) * 10, random_state=42)
train_df = pd.concat([fraud_df, train_legit_sample], ignore_index=True)
train_df = train_df.sample(frac=1, random_state=42).reset_index(drop=True)
print(f"Train set: {len(train_df)} rows (fraud={train_df['isFraud'].sum()}, {train_df['isFraud'].mean():.2%})")

X_train = train_df[feature_cols].values.astype(np.float32)
y_train = train_df['isFraud'].values
X_test = test_df[feature_cols].values.astype(np.float32)
y_test = test_df['isFraud'].values

model = xgb.XGBClassifier(
    n_estimators=500,
    max_depth=6,
    learning_rate=0.05,
    subsample=0.8,
    colsample_bytree=0.8,
    eval_metric='aucpr',
    early_stopping_rounds=30,
    random_state=42,
    tree_method='hist'
)

model.fit(
    X_train, y_train,
    eval_set=[(X_test, y_test)],
    verbose=50
)

# Evaluate
y_pred_proba = model.predict_proba(X_test)[:, 1]

roc_auc = roc_auc_score(y_test, y_pred_proba)
precision, recall, thresholds = precision_recall_curve(y_test, y_pred_proba)
auprc = auc(recall, precision)

# Best F2 threshold (recall-weighted — catching fraud > avoiding false alarms)
f2_scores = (5 * precision[:-1] * recall[:-1]) / (4 * precision[:-1] + recall[:-1] + 1e-8)
best_thresh = thresholds[np.argmax(f2_scores)]

y_pred = (y_pred_proba >= best_thresh).astype(int)
cm = confusion_matrix(y_test, y_pred)
tn, fp, fn, tp = cm.ravel()

print(f"\n=== RESULTS ===")
print(f"ROC-AUC:          {roc_auc:.4f}")
print(f"AUPRC:            {auprc:.4f}")
print(f"Best threshold:   {best_thresh:.4f}")
print(f"\nFraud caught:     {tp/(tp+fn)*100:.1f}% ({tp} of {tp+fn})")
print(f"False alarm rate: {fp/(fp+tn)*100:.2f}%")
print(f"\n{classification_report(y_test, y_pred, target_names=['Legit','Fraud'], digits=4)}")

fraud_scores = y_pred_proba[y_test == 1]
legit_scores = y_pred_proba[y_test == 0]
print(f"Score separation: {fraud_scores.mean() - legit_scores.mean():.4f}")

# Save model
model.save_model('ml/fraud/models/fraud_model.json')

# Update metadata with correct values
metadata = {
    'feature_count': len(feature_cols),
    'feature_names': feature_cols,
    'threshold': float(best_thresh),
    'auprc': float(auprc),
    'roc_auc': float(roc_auc),
    'training_approach': 'balanced_undersampling_10x'
}
with open('ml/fraud/models/model_metadata.json', 'w') as f:
    json.dump(metadata, f, indent=2)

print("\nModel saved to ml/fraud/models/fraud_model.json")
print("Metadata updated")

# Verdict
print(f"\n=== VERDICT ===")
if roc_auc > 0.90:
    print(f"[OK] ROC-AUC {roc_auc:.3f} - STRONG, proceed to ONNX export")
elif roc_auc > 0.80:
    print(f"[OK] ROC-AUC {roc_auc:.3f} - GOOD, proceed to ONNX export")
else:
    print(f"[FAIL] ROC-AUC {roc_auc:.3f} - still weak, do not export")
