@echo off
echo Creating VANGUARD ML virtual environment...
python -m venv venv
echo Activating venv...
call venv\Scripts\activate.bat
echo Installing ML dependencies...
pip install --upgrade pip
pip install xgboost scikit-learn pandas numpy imbalanced-learn skl2onnx onnxruntime torch matplotlib seaborn jupyter
pip freeze > requirements.txt
echo Done! Virtual environment ready.
echo Run: call ml\venv\Scripts\activate.bat
