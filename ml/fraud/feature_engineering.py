import pandas as pd
import numpy as np
from sklearn.preprocessing import LabelEncoder
from pathlib import Path


DATA_DIR = Path(__file__).parent / "data"
MODELS_DIR = Path(__file__).parent / "models"


def load_and_merge() -> pd.DataFrame:
    txn_path = DATA_DIR / "train_transaction.csv"
    identity_path = DATA_DIR / "train_identity.csv"

    txn = pd.read_csv(txn_path)
    print(f"Loaded transactions: {txn.shape}")

    if identity_path.exists():
        identity = pd.read_csv(identity_path)
        print(f"Loaded identity: {identity.shape}")
        df = txn.merge(identity, on="TransactionID", how="left")
    else:
        print("No identity file found — using transactions only")
        df = txn

    print(f"Merged shape: {df.shape}")
    return df


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()

    # Drop columns with >80% null rate
    null_rates = df.isnull().mean()
    high_null_cols = null_rates[null_rates > 0.80].index.tolist()
    df.drop(columns=high_null_cols, inplace=True)
    print(f"Dropped {len(high_null_cols)} columns with >80% nulls")

    # Create datetime features from TransactionDT (seconds since epoch reference)
    if "TransactionDT" in df.columns:
        # IEEE-CIS dataset: TransactionDT is seconds from a reference date
        # Rough conversion: reference date ~ 2017-11-30 (for IEEE fraud)
        df["hour"] = (df["TransactionDT"] // 3600) % 24
        df["day_of_week"] = (df["TransactionDT"] // 86400) % 7
    else:
        df["hour"] = 0
        df["day_of_week"] = 0

    # Cyclic encoding
    df["hour_sin"] = np.sin(2 * np.pi * df["hour"] / 24)
    df["hour_cos"] = np.cos(2 * np.pi * df["hour"] / 24)
    df["dow_sin"] = np.sin(2 * np.pi * df["day_of_week"] / 7)
    df["dow_cos"] = np.cos(2 * np.pi * df["day_of_week"] / 7)

    # Amount features
    if "TransactionAmt" in df.columns:
        df["log_amount"] = np.log1p(df["TransactionAmt"])
        df["amount_cents"] = (df["TransactionAmt"] * 100).astype(int) % 100
    else:
        df["log_amount"] = 0.0
        df["amount_cents"] = 0

    # Frequency encoding for email domains
    for col in ["P_emaildomain", "R_emaildomain"]:
        if col in df.columns:
            freq = df[col].fillna("MISSING").value_counts()
            df[f"{col}_freq"] = df[col].fillna("MISSING").map(freq)
        else:
            df[f"{col}_freq"] = 0

    # Label encode object columns
    for col in df.select_dtypes(include=["object"]).columns:
        if col not in ["TransactionID", "isFraud"]:
            le = LabelEncoder()
            df[col] = df[col].astype(str)
            df[col] = le.fit_transform(df[col])

    # Fill remaining NaN with median
    for col in df.columns:
        if df[col].dtype in [np.float64, np.float32, np.int64, np.int32]:
            df[col] = df[col].fillna(df[col].median())

    return df


def get_feature_columns(df: pd.DataFrame) -> list:
    exclude = {"TransactionID", "isFraud", "TransactionDT"}
    return [c for c in df.columns if c not in exclude]


def main():
    print("=== VANGUARD Feature Engineering ===")

    # Ensure directories exist
    DATA_DIR.mkdir(parents=True, exist_ok=True)

    df = load_and_merge()
    df = engineer_features(df)

    feature_cols = get_feature_columns(df)
    print(f"Feature count: {len(feature_cols)}")

    # Save processed data
    df.to_parquet(DATA_DIR / "processed_features.parquet", index=False)
    print(f"Saved: {DATA_DIR / 'processed_features.parquet'}")

    # Save feature columns
    with open(DATA_DIR / "feature_columns.txt", "w") as f:
        for col in feature_cols:
            f.write(col + "\n")
    print(f"Saved: {DATA_DIR / 'feature_columns.txt'}")

    print(f"\nSaved to data/")
    print(f"Feature count: {len(feature_cols)}")


if __name__ == "__main__":
    main()
