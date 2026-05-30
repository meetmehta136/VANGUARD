import pandas as pd
import numpy as np
import xgboost as xgb
import json
from sklearn.metrics import (
    classification_report, confusion_matrix, roc_auc_score,
    precision_recall_curve, auc, average_precision_score
)

print("=== VANGUARD Fraud Model Evaluation (v2) ===\n")

meta = json.load(open('ml/fraud/models/model_metadata.json'))
feature_cols = meta['feature_names']
print(f"Using {len(feature_cols)} features, model threshold={meta['threshold']:.4f}")

df = pd.read_parquet('ml/fraud/data/processed_features.parquet')
fraud_df = df[df['isFraud'] == 1]
legit_df = df[df['isFraud'] == 0]
test_fraud = fraud_df.sample(frac=0.3, random_state=42)
test_legit = legit_df.sample(n=100000, random_state=42)
test_df = pd.concat([test_fraud, test_legit], ignore_index=True)

X_test = test_df[feature_cols].values.astype(np.float32)
y_test = test_df['isFraud'].values

model = xgb.XGBClassifier()
model.load_model('ml/fraud/models/fraud_model.json')
y_pred_proba = model.predict_proba(X_test)[:, 1]

roc_auc = roc_auc_score(y_test, y_pred_proba)
print(f"ROC-AUC:              {roc_auc:.4f}   (random=0.50, perfect=1.00)")

precision, recall, thresholds = precision_recall_curve(y_test, y_pred_proba)
auprc = auc(recall, precision)
baseline = y_test.mean()
lift = auprc / baseline
print(f"AUPRC:                {auprc:.4f}   (baseline={baseline:.4f}, lift={lift:.1f}x)")

ap = average_precision_score(y_test, y_pred_proba)
print(f"Average Precision:    {ap:.4f}")

f1_scores = 2 * precision * recall / (precision + recall + 1e-8)
f2_scores = (5 * precision[:-1] * recall[:-1]) / (4 * precision[:-1] + recall[:-1] + 1e-8)
best_f1_thresh = thresholds[np.argmax(f1_scores[:-1])]
best_f2_thresh = thresholds[np.argmax(f2_scores)]
print(f"\nBest F1 threshold:    {best_f1_thresh:.4f}")
print(f"Best F2 threshold:    {best_f2_thresh:.4f}")

print(f"\n--- Classification Report (threshold={best_f2_thresh:.3f}) ---")
y_pred = (y_pred_proba >= best_f2_thresh).astype(int)
print(classification_report(y_test, y_pred, target_names=['Legit', 'Fraud'], digits=4))

cm = confusion_matrix(y_test, y_pred)
tn, fp, fn, tp = cm.ravel()
print(f"--- Confusion Matrix ---")
print(f"True Negatives  (legit caught):   {tn:>7,}")
print(f"False Positives (legit flagged):  {fp:>7,}")
print(f"False Negatives (fraud missed):   {fn:>7,}")
print(f"True Positives  (fraud caught):   {tp:>7,}")
print(f"\nFraud Detection Rate:  {tp/(tp+fn)*100:.1f}%  ({tp} of {tp+fn} frauds caught)")
print(f"False Alarm Rate:      {fp/(fp+tn)*100:.2f}%  ({fp:,} legit transactions flagged)")

fraud_scores = y_pred_proba[y_test == 1]
legit_scores = y_pred_proba[y_test == 0]
print(f"\n--- Score Distribution ---")
print(f"Fraud transactions:  mean={fraud_scores.mean():.4f}  median={np.median(fraud_scores):.4f}  max={fraud_scores.max():.4f}")
print(f"Legit transactions:  mean={legit_scores.mean():.4f}  median={np.median(legit_scores):.4f}  max={legit_scores.max():.4f}")
print(f"Score separation:    {fraud_scores.mean() - legit_scores.mean():.4f}  (higher = better)")

print(f"\n=== VERDICT ===")
passed = 0
total = 4
if roc_auc > 0.90:
    print(f"  [OK]   ROC-AUC {roc_auc:.3f} -- STRONG model")
    passed += 1
else:
    print(f"  [FAIL] ROC-AUC {roc_auc:.3f} -- consider retraining")

if lift > 50:
    print(f"  [OK]   AUPRC lift {lift:.1f}x over baseline -- model adds real value")
    passed += 1
elif lift > 10:
    print(f"  [WARN] AUPRC lift {lift:.1f}x -- some signal detected")
    passed += 0.5
else:
    print(f"  [FAIL] AUPRC lift {lift:.1f}x -- model barely better than random")

dr = tp / (tp + fn) * 100
if dr > 70:
    print(f"  [OK]   Catches {dr:.1f}% of fraud -- usable in production")
    passed += 1
elif dr > 40:
    print(f"  [WARN] Catches {dr:.1f}% of fraud -- marginal")
    passed += 0.5
else:
    print(f"  [FAIL] Catches {dr:.1f}% of fraud -- too many misses")

far = fp / (fp + tn) * 100
if far < 5:
    print(f"  [OK]   False alarm rate {far:.2f}% -- acceptable")
    passed += 1
elif far < 15:
    print(f"  [WARN] False alarm rate {far:.2f}% -- tolerable")
    passed += 0.5
else:
    print(f"  [FAIL] False alarm rate {far:.2f}% -- too many false positives")

print(f"\n  Score: {passed:.0f}/{total} checks passed")
print(f"  Verdict: {'PROCEED TO ONNX EXPORT' if passed >= 3 else 'NEEDS RETRAINING'}")
