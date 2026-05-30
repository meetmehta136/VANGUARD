import torch
import numpy as np
import json
from pathlib import Path
from train_traffic_lstm import TrafficLSTM
import onnxruntime as rt

MODELS_DIR = Path(__file__).parent / "models"


def main():
    print("=== VANGUARD LSTM ONNX Export ===")

    checkpoint = torch.load(MODELS_DIR / 'traffic_lstm.pt', map_location='cpu', weights_only=True)
    mean = checkpoint['mean']
    std = checkpoint['std']

    model = TrafficLSTM()
    model.load_state_dict(checkpoint['model_state_dict'])
    model.eval()

    dummy_input = torch.zeros((1, 60, 1), dtype=torch.float32)

    torch.onnx.export(
        model,
        dummy_input,
        MODELS_DIR / 'traffic_lstm.onnx',
        input_names=['traffic_sequence'],
        output_names=['predicted_traffic'],
        dynamic_axes={
            'traffic_sequence': {0: 'batch_size'},
            'predicted_traffic': {0: 'batch_size'},
        },
        opset_version=16,
    )

    metadata = {
        'mean': mean,
        'std': std,
        'lookback': 60,
    }
    with open(MODELS_DIR / 'traffic_metadata.json', 'w') as f:
        json.dump(metadata, f, indent=2)

    print(f"Saved: {MODELS_DIR / 'traffic_lstm.onnx'}")
    print(f"Saved: {MODELS_DIR / 'traffic_metadata.json'}")

    print("\n=== LSTM ONNX Verification ===")
    sess = rt.InferenceSession(str(MODELS_DIR / 'traffic_lstm.onnx'))

    input_name = sess.get_inputs()[0].name
    output_names = [o.name for o in sess.get_outputs()]
    print(f"Input name:  {input_name}")
    print(f"Input shape: {sess.get_inputs()[0].shape}")
    print(f"Output names: {output_names}")
    for o in sess.get_outputs():
        print(f"  {o.name}: shape={o.shape}")

    dummy_np = np.zeros((1, 60, 1), dtype=np.float32)
    result = sess.run(output_names, {input_name: dummy_np})
    print(f"\nDummy inference output: {result[0][0][0]:.4f}")
    print(f"Output shape: {np.array(result[0]).shape}")

    print("\n=== LSTM ONNX VERIFICATION PASSED ===")
    print(f"LSTM_INPUT_NAME = \"{input_name}\"")
    print(f"LSTM_OUTPUT_NAME = \"{output_names[0]}\"")


if __name__ == '__main__':
    main()
