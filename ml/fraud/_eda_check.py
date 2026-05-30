import pandas as pd
import numpy as np

import pathlib
df = pd.read_csv(pathlib.Path(__file__).parent / 'data' / 'train_transaction.csv')

df['orig_balance_error'] = abs(df['oldbalanceOrg'] + df['amount'] - df['newbalanceOrig'])
df['dest_balance_error'] = abs(df['oldbalanceDest'] + df['amount'] - df['newbalanceDest'])
df['orig_balance_error_flag'] = (df['orig_balance_error'] > 0.01).astype(int)
df['dest_balance_error_flag'] = (df['dest_balance_error'] > 0.01).astype(int)

print('=== Balance Error Signals ===')
fraud = df[df['isFraud'] == 1]
legit = df[df['isFraud'] == 0].sample(n=10000, random_state=42)

print(f"orig_balance_error_flag  fraud={fraud['orig_balance_error_flag'].mean():.4f}  legit={legit['orig_balance_error_flag'].mean():.4f}")
print(f"dest_balance_error_flag  fraud={fraud['dest_balance_error_flag'].mean():.4f}  legit={legit['dest_balance_error_flag'].mean():.4f}")

print(f"\norig_balance_error_mean  fraud={fraud['orig_balance_error'].mean():.2f}  legit={legit['orig_balance_error'].mean():.2f}")
print(f"dest_balance_error_mean  fraud={fraud['dest_balance_error'].mean():.2f}  legit={legit['dest_balance_error'].mean():.2f}")

fraud['pct_of_balance'] = fraud['amount'] / (fraud['oldbalanceOrg'] + 1)
legit2 = legit.copy()
legit2['pct_of_balance'] = legit2['amount'] / (legit2['oldbalanceOrg'] + 1)
print(f"\npct_of_balance fraud mean={fraud['pct_of_balance'].mean():.2f} median={fraud['pct_of_balance'].median():.2f}")
print(f"pct_of_balance legit mean={legit2['pct_of_balance'].mean():.2f} median={legit2['pct_of_balance'].median():.2f}")

print(f"\n=== Profit-like feature ===")
# In PaySim, fraudsters manipulate initial balance
# profit_orig = newbalanceOrig - oldbalanceOrg + amount (should be 0 for legit)
df['profit_orig'] = df['newbalanceOrig'] - df['oldbalanceOrg'] + df['amount']
df['profit_dest'] = df['newbalanceDest'] - df['oldbalanceDest'] - df['amount']
fraud2 = df[df['isFraud'] == 1]
print(f"profit_orig  fraud={fraud2['profit_orig'].mean():.2f}  legit={df[df['isFraud']==0]['profit_orig'].mean():.2f}")
print(f"profit_dest  fraud={fraud2['profit_dest'].mean():.2f}  legit={df[df['isFraud']==0]['profit_dest'].mean():.2f}")
print(f"abs profit_orig > 0.01:  fraud={(abs(fraud2['profit_orig']) > 0.01).mean():.4f}  legit={(abs(df[df['isFraud']==0]['profit_orig']) > 0.01).mean():.4f}")
