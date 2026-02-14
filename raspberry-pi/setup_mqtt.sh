#!/bin/bash
# Mosquitto MQTT Broker Installation and Configuration Script for Raspberry Pi

echo "======================================"
echo "SmartAmbient MQTT Broker Setup"
echo "======================================"

# Update package list
echo "Updating package list..."
sudo apt update

# Install Mosquitto MQTT broker and clients
echo "Installing Mosquitto MQTT broker..."
sudo apt install -y mosquitto mosquitto-clients

# Enable Mosquitto to start on boot
echo "Enabling Mosquitto service..."
sudo systemctl enable mosquitto

# Create Mosquitto configuration
echo "Configuring Mosquitto..."
sudo tee /etc/mosquitto/conf.d/smartambient.conf > /dev/null << 'EOF'
# SmartAmbient MQTT Broker Configuration

# Listen on all interfaces
listener 1883

# Allow anonymous connections (for local network use)
allow_anonymous true

# Persistence settings
persistence true
persistence_location /var/lib/mosquitto/

# Logging
log_dest syslog
log_type error
log_type warning
log_type notice
log_type information

# Connection settings
max_connections -1
max_inflight_messages 20
max_queued_messages 100
EOF

# Restart Mosquitto to apply configuration
echo "Restarting Mosquitto service..."
sudo systemctl restart mosquitto

# Check status
echo "Checking Mosquitto status..."
sudo systemctl status mosquitto --no-pager

echo ""
echo "======================================"
echo "MQTT Broker setup complete!"
echo "Broker is running on port 1883"
echo ""
echo "Test with:"
echo "  mosquitto_sub -t 'test' -v &"
echo "  mosquitto_pub -t 'test' -m 'Hello MQTT'"
echo "======================================"
