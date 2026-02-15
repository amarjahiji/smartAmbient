#!/bin/bash
# Install dependencies before starting SmartAmbient hub
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Create venv if missing
if [ ! -d "$SCRIPT_DIR/venv" ]; then
    python3 -m venv "$SCRIPT_DIR/venv"
fi

# Install/update Python packages
"$SCRIPT_DIR/venv/bin/pip" install -q -r "$SCRIPT_DIR/requirements.txt"

# Ensure system audio library is present
dpkg -s libportaudio2 > /dev/null 2>&1 || sudo apt-get install -y -qq libportaudio2
