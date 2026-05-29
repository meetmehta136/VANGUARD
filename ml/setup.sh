#!/usr/bin/env bash
set -e
echo "Creating VANGUARD ML virtual environment..."
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install xgboost scikit-learn pandas numpy imbalanced-learn skl2onnx onnxruntime torch matplotlib seaborn jupyter
pip freeze > requirements.txt
echo "Done! Run: source ml/venv/bin/activate"
