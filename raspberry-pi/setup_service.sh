#!/bin/bash
# SmartAmbient Raspberry Pi Service Setup Script

echo "======================================"
echo "SmartAmbient Service Setup"
echo "======================================"

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Install system dependencies
echo "Installing system dependencies..."
sudo apt-get update -qq
sudo apt-get install -y -qq libportaudio2 > /dev/null 2>&1
echo "System dependencies installed."

# Set up Python virtual environment and install requirements
echo "Setting up Python environment..."
if [ ! -d "$SCRIPT_DIR/venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv "$SCRIPT_DIR/venv"
fi

echo "Installing Python dependencies..."
"$SCRIPT_DIR/venv/bin/pip" install --upgrade pip -q
"$SCRIPT_DIR/venv/bin/pip" install -r "$SCRIPT_DIR/requirements.txt" -q
echo "Python dependencies installed."

# Create systemd service file
echo "Creating systemd service..."
sudo tee /etc/systemd/system/smartambient.service > /dev/null << EOF
[Unit]
Description=SmartAmbient IoT Hub
After=network.target mosquitto.service
Wants=mosquitto.service

[Service]
Type=simple
User=$USER
WorkingDirectory=$SCRIPT_DIR
ExecStart=$SCRIPT_DIR/venv/bin/python $SCRIPT_DIR/main.py
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd
echo "Reloading systemd daemon..."
sudo systemctl daemon-reload

# Enable the service to start on boot
echo "Enabling SmartAmbient service..."
sudo systemctl enable smartambient.service

# Start the service
echo "Starting SmartAmbient service..."
sudo systemctl start smartambient.service

# Show status
echo ""
echo "======================================"
echo "Service Status:"
sudo systemctl status smartambient.service --no-pager
echo ""
echo "======================================"
echo "Setup complete!"
echo ""
echo "Commands:"
echo "  Start:   sudo systemctl start smartambient"
echo "  Stop:    sudo systemctl stop smartambient"
echo "  Restart: sudo systemctl restart smartambient"
echo "  Status:  sudo systemctl status smartambient"
echo "  Logs:    journalctl -u smartambient -f"
echo "======================================"
