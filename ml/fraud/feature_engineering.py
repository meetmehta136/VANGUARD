import pandas as pd
import numpy as np
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data"


def load_data() -> pd.DataFrame:
    expected_cols = {'step', 'type', 'amount', 'nameOrig', 'oldbalanceOrg',
                     'newbalanceOrig', 'nameDest', 'oldbalanceDest',
                     'newbalanceDest', 'isFraud', 'isFlaggedFraud'}
    df = pd.read_csv(DATA_DIR / 'train_transaction.csv')
    print(f"Shape: {df.shape}")
    print(f"isFraud:\n{df['isFraud'].value_counts()}")
    fraud_rate = df['isFraud'].mean() * 100
    print(f"Fraud rate: {fraud_rate:.4f}%")
    actual_cols = set(df.columns)
    if not expected_cols.issubset(actual_cols):
        raise ValueError(f"Missing columns. Expected {expected_cols - actual_cols}")
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()

    type_dummies = pd.get_dummies(df['type'], prefix='type')
    df = pd.concat([df, type_dummies], axis=1)

    df['balance_delta_orig'] = df['newbalanceOrig'] - df['oldbalanceOrg']
    df['balance_delta_dest'] = df['newbalanceDest'] - df['oldbalanceDest']

    df['orig_balance_zero_after'] = (df['newbalanceOrig'] == 0).astype(int)
    df['dest_no_increase'] = ((df['oldbalanceDest'] == df['newbalanceDest']) &
                              (df['amount'] > 0)).astype(int)
    df['is_high_risk_type'] = df['type'].isin(['TRANSFER', 'CASH_OUT']).astype(int)

    df['log_amount'] = np.log1p(df['amount'])
    df['amount_to_orig_balance_ratio'] = df['amount'] / (df['oldbalanceOrg'] + 1)

    df['hour_of_day'] = df['step'] % 24
    df['hour_sin'] = np.sin(2 * np.pi * df['hour_of_day'] / 24)
    df['hour_cos'] = np.cos(2 * np.pi * df['hour_of_day'] / 24)
    df['day_of_month'] = (df['step'] // 24) % 30
    df['day_sin'] = np.sin(2 * np.pi * df['day_of_month'] / 30)
    df['day_cos'] = np.cos(2 * np.pi * df['day_of_month'] / 30)

    df['orig_had_zero_balance'] = (df['oldbalanceOrg'] == 0).astype(int)
    df['dest_had_zero_balance'] = (df['oldbalanceDest'] == 0).astype(int)
    df['log_orig_balance'] = np.log1p(df['oldbalanceOrg'])
    df['log_dest_balance'] = np.log1p(df['oldbalanceDest'])

    drop_cols = ['nameOrig', 'nameDest', 'type', 'isFlaggedFraud',
                 'hour_of_day', 'day_of_month']
    df = df.drop(columns=drop_cols)

    return df


def get_feature_columns(df: pd.DataFrame) -> list:
    return [c for c in df.columns if c not in {'isFraud', 'step'}]


def main():
    print("=== VANGUARD Feature Engineering (CiferAI Dataset) ===")
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    df = load_data()
    df = engineer_features(df)
    feature_cols = get_feature_columns(df)

    print(f"\nFeature count: {len(feature_cols)}")
    print("Features:")
    for col in feature_cols:
        print(f"  {col}")

    df.to_parquet(DATA_DIR / 'processed_features.parquet', index=False)
    with open(DATA_DIR / 'feature_columns.txt', 'w') as f:
        for col in feature_cols:
            f.write(col + '\n')

    print("\nSaved processed_features.parquet and feature_columns.txt")


if __name__ == '__main__':
    main()
