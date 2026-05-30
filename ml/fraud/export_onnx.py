import json
import numpy as np
from pathlib import Path
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
from xgboost import XGBClassifier
import onnxruntime as rt

MODELS_DIR = Path(__file__).parent / "models"


def main():
    print("=== VANGUARD ONNX Export ===")

    with open(MODELS_DIR / 'model_metadata.json', 'r') as f:
        metadata = json.load(f)

    feature_count = metadata['feature_count']
    print(f"Feature count: {feature_count}")

    model = XGBClassifier()
    model.load_model(str(MODELS_DIR / 'fraud_model.json'))

    initial_type = [('float_input', FloatTensorType([None, feature_count]))]
    onnx_model = convert_sklearn(
        model,
        initial_types=initial_type,
        target_opset=15,
        options={id(model): {'raw_scores': True}}
    )

    onnx_path = MODELS_DIR / 'fraud_model.onnx'
    with open(onnx_path, 'wb') as f:
        f.write(onnx_model.SerializeToString())
    print(f"Saved: {onnx_path}")

    print("\n=== ONNX Verification ===")
    sess = rt.InferenceSession(str(onnx_path))

    input_name = sess.get_inputs()[0].name
    input_shape = sess.get_inputs()[0].shape
    input_type = sess.get_inputs()[0].type
    print(f"Input name:  {input_name}")
    print(f"Input shape: {input_shape}")
    print(f"Input type:  {input_type}")

    output_names = [o.name for o in sess.get_outputs()]
    print(f"Output names: {output_names}")
    for o in sess.get_outputs():
        print(f"  {o.name}: shape={o.shape}, type={o.type}")

    dummy_input = np.zeros((1, feature_count), dtype=np.float32)
    results = sess.run(output_names, {input_name: dummy_input})
    print(f"\nDummy inference output: {results}")
    for i, name in enumerate(output_names):
        print(f"  {name}: {results[i]}")

    print("\n=== ONNX VERIFICATION PASSED ===")
    print(f"INPUT NAME: {input_name}")
    print(f"OUTPUT NAMES: {output_names}")
    print("\nRecord these in ModelConstants.java:")
    print(f'  FRAUD_MODEL_INPUT_NAME = "{input_name}"')
    for name in output_names:
        clean = name.replace(':', '_').replace('-', '_')
        print(f'  FRAUD_MODEL_OUTPUT_{clean.upper()} = "{name}"')


if __name__ == '__main__':
    main()
