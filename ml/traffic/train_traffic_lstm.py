import numpy as np
import torch
import torch.nn as nn
from pathlib import Path
import json

MODELS_DIR = Path(__file__).parent / "models"


class TrafficLSTM(nn.Module):
    def __init__(self, input_size=1, hidden_size=64, num_layers=2, dropout=0.2):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size, hidden_size, num_layers,
            batch_first=True, dropout=dropout
        )
        self.fc1 = nn.Linear(hidden_size, 32)
        self.relu = nn.ReLU()
        self.fc2 = nn.Linear(32, 1)

    def forward(self, x):
        lstm_out, _ = self.lstm(x)
        last_out = lstm_out[:, -1, :]
        out = self.fc1(last_out)
        out = self.relu(out)
        out = self.fc2(out)
        return out


def generate_synthetic_traffic(n_samples=50000):
    t = np.arange(n_samples)
    daily = 100 + 40 * np.sin(2 * np.pi * t / 1440)
    weekly = 20 * np.sin(2 * np.pi * t / 10080)
    noise = np.random.normal(0, 10, n_samples)
    base = daily + weekly + noise
    base = np.maximum(base, 0)

    attack_indices = np.random.choice(n_samples, size=int(n_samples * 0.02), replace=False)
    base[attack_indices] += np.random.uniform(300, 800, size=len(attack_indices))

    return base


def create_sequences(data, lookback=60):
    X, y = [], []
    for i in range(lookback, len(data)):
        X.append(data[i - lookback:i])
        y.append(data[i])
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.float32)


def main():
    print("=== VANGUARD LSTM Traffic Model Training ===")
    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    data = generate_synthetic_traffic(50000)
    mean, std = float(data.mean()), float(data.std())
    data_norm = (data - mean) / std

    X, y = create_sequences(data_norm, lookback=60)
    X = X.reshape(-1, 60, 1)

    split = int(len(X) * 0.8)
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]
    print(f"Train: {X_train.shape[0]}, Val: {X_val.shape[0]}")

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    model = TrafficLSTM().to(device)
    criterion = nn.MSELoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=0.001)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer, mode='min', factor=0.5, patience=5
    )

    X_train_t = torch.FloatTensor(X_train).to(device)
    y_train_t = torch.FloatTensor(y_train).to(device)
    X_val_t = torch.FloatTensor(X_val).to(device)
    y_val_t = torch.FloatTensor(y_val).to(device)

    batch_size = 256
    n_batches = (len(X_train_t) + batch_size - 1) // batch_size

    for epoch in range(60):
        model.train()
        perm = torch.randperm(len(X_train_t))
        epoch_loss = 0
        for i in range(0, len(X_train_t), batch_size):
            idx = perm[i:i + batch_size]
            X_batch, y_batch = X_train_t[idx], y_train_t[idx]
            optimizer.zero_grad()
            y_pred = model(X_batch)
            loss = criterion(y_pred.squeeze(), y_batch)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            optimizer.step()
            epoch_loss += loss.item()

        model.eval()
        with torch.no_grad():
            val_pred = model(X_val_t)
            val_loss = criterion(val_pred.squeeze(), y_val_t).item()

        scheduler.step(val_loss)

        if (epoch + 1) % 10 == 0:
            print(f"Epoch {epoch + 1:2d}/60, Train Loss: {epoch_loss / n_batches:.6f}, Val Loss: {val_loss:.6f}")

    checkpoint = {
        'model_state_dict': model.state_dict(),
        'mean': mean,
        'std': std,
    }
    torch.save(checkpoint, MODELS_DIR / 'traffic_lstm.pt')
    print(f"\nSaved: {MODELS_DIR / 'traffic_lstm.pt'}")
    print(f"Normalization params: mean={mean:.4f}, std={std:.4f}")


if __name__ == '__main__':
    main()
