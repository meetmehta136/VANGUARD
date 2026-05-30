import pandas as pd
import numpy as np
import json
from pathlib import Path
from sklearn.model_selection import train_test_split
from sklearn.metrics import precision_recall_curve, auc, roc_auc_score, classification_report
from imblearn.over_sampling import SMOTE
import xgboost as xgb

DATA_DIR = Path(__file__).parent / "data"
MODELS_DIR = Path(__file__).parent / "models"


def main():
    print("=== VANGUARD XGBoost Fraud Model Training ===")
    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    df = pd.read_parquet(DATA_DIR / 'processed_features.parquet')
    print(f"Full dataset shape: {df.shape}")
    print(f"Fraud rate: {df['isFraud'].mean():.4f}")

    assert len(df) > 500_000, f"Dataset too small: {len(df)} rows. Check parquet file."

    with open(DATA_DIR / 'feature_columns.txt') as f:
        feature_cols = [line.strip() for line in f if line.strip()]

    constant_cols = [c for c in feature_cols if df[c].nunique() <= 1]
    if constant_cols:
        print(f"Dropping constant features: {constant_cols}")
        feature_cols = [c for c in feature_cols if c not in constant_cols]

    X = df[feature_cols].values.astype(np.float32)
    y = df['isFraud'].values

    print(f"X shape: {X.shape}")
    print(f"Fraud count: {y.sum()}")
    print(f"Legit count: {(y == 0).sum()}")

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, stratify=y, random_state=42
    )
    print(f"Train size: {len(X_train)}, Test size: {len(X_test)}")

    fraud_train_count = int(y_train.sum())
    print(f"Fraud in train set: {fraud_train_count}")

    X_train_final, y_train_final = X_train, y_train

    neg = (y_train_final == 0).sum()
    pos = (y_train_final == 1).sum()
    spw = neg / pos
    print(f"scale_pos_weight = {spw:.2f}")

    model = xgb.XGBClassifier(
        n_estimators=1000,
        max_depth=8,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        scale_pos_weight=spw,
        eval_metric='aucpr',
        early_stopping_rounds=50,
        tree_method='hist',
        random_state=42,
        min_child_weight=1,
    )

    model.fit(
        X_train_final, y_train_final,
        eval_set=[(X_test, y_test)],
        verbose=100,
    )

    y_pred_proba = model.predict_proba(X_test)[:, 1]
    precision, recall, thresholds = precision_recall_curve(y_test, y_pred_proba)
    auprc = auc(recall, precision)
    roc = roc_auc_score(y_test, y_pred_proba)

    print(f"\n{'=' * 40}")
    print(f"AUPRC : {auprc:.4f}  (target: >0.80)")
    print(f"ROC-AUC: {roc:.4f}  (target: >0.95)")
    print(f"{'=' * 40}")

    f2 = (5 * precision[:-1] * recall[:-1]) / (4 * precision[:-1] + recall[:-1] + 1e-8)
    best_idx = np.argmax(f2)
    best_threshold = float(thresholds[best_idx])
    print(f"Best F2 threshold: {best_threshold:.4f}")

    y_pred = (y_pred_proba >= best_threshold).astype(int)
    print(classification_report(y_test, y_pred, target_names=['Legit', 'Fraud']))

    importances = model.feature_importances_
    feat_imp = sorted(zip(feature_cols, importances), key=lambda x: x[1], reverse=True)

    print("\nTop 10 Feature Importances:")
    for feat, imp in feat_imp[:10]:
        print(f"  {feat}: {imp:.4f}")

    top5_names = [f for f, _ in feat_imp[:5]]
    fraud_types_in_top5 = any(
        'TRANSFER' in n or 'CASH_OUT' in n or
        'balance_delta_orig' in n or
        'orig_balance_zero' in n for n in top5_names
    )
    if not fraud_types_in_top5:
        print("\nWARNING: Expected fraud-signal features not in top 5.")
        print("Top 5 are:", top5_names)
        print("This may indicate the full dataset did not load correctly.")
    else:
        print("\n[v] Feature importance sanity check PASSED")

    model.save_model(str(MODELS_DIR / 'fraud_model.json'))

    metadata = {
        'feature_count': len(feature_cols),
        'feature_names': feature_cols,
        'threshold': best_threshold,
        'auprc': float(auprc),
        'roc_auc': float(roc),
        'train_rows': int(len(X_train_final)),
        'dataset': 'CiferAI-Fraud-Detection-Dataset-AF-part-1',
    }
    with open(MODELS_DIR / 'model_metadata.json', 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"\nFeature count: {len(feature_cols)}")
    print("Saved fraud_model.json and model_metadata.json")


if __name__ == '__main__':
    main()
