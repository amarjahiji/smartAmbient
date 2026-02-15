#!/bin/bash
set -e

echo "============================================"
echo "  SmartAmbient Backend - GCP VM Setup"
echo "  e2-highmem-2 (2 vCPUs, 16 GB Memory)"
echo "============================================"

# ---- System Update ----
echo "[1/7] Updating system packages..."
sudo apt update && sudo apt upgrade -y

# ---- Install Dependencies ----
echo "[2/7] Installing required packages..."
sudo apt install -y git curl wget unzip zip lsb-release gnupg

# ---- Install MySQL 8 ----
echo "[3/7] Installing MySQL..."
sudo apt install -y mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql

# Set root password and create database (handles both fresh and re-run)
if sudo mysql -e "SELECT 1" 2>/dev/null; then
    sudo mysql <<MYSQL_SCRIPT
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY 'flacko1237';
FLUSH PRIVILEGES;
CREATE DATABASE IF NOT EXISTS smart_ambient;
MYSQL_SCRIPT
else
    sudo mysql -u root -pflacko1237 -e "CREATE DATABASE IF NOT EXISTS smart_ambient;"
fi

echo "MySQL configured: root/flacko1237, database: smart_ambient"

# ---- Install Java 25 via SDKMAN ----
echo "[4/7] Installing Java 25 (Amazon Corretto) via SDKMAN..."
if [ ! -d "$HOME/.sdkman" ]; then
    curl -s "https://get.sdkman.io" | bash
fi
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install Java 25 - try corretto first, fallback to open
sdk install java 25-amzn 2>/dev/null || sdk install java 25-open 2>/dev/null || {
    echo "Java 25 not found in SDKMAN, listing available Java 25 versions:"
    sdk list java | grep "25\."
    echo ""
    echo "Install manually with: sdk install java <identifier>"
    echo "Then re-run this script."
    exit 1
}

java -version
echo "Java installed successfully."

# ---- Clone Repository ----
echo "[5/7] Cloning SmartAmbient repository..."
cd "$HOME"
if [ -d "smartAmbient" ]; then
    echo "Repository already exists, pulling latest changes..."
    cd smartAmbient
    git pull origin main
else
    git clone https://github.com/amarjahiji/smartAmbient.git
    cd smartAmbient
fi

# ---- Build Backend ----
echo "[6/7] Building backend..."
cd "$HOME/smartAmbient/backend"
chmod +x mvnw
./mvnw clean package -DskipTests

echo "Backend built: target/smartAmbient-0.0.1-SNAPSHOT.jar"

# ---- Systemd Service ----
echo "[7/7] Setting up systemd service..."

JAVA_PATH=$(which java)
JAVA_HOME_DIR=$(dirname $(dirname "$JAVA_PATH"))

sudo tee /etc/systemd/system/smartambient.service > /dev/null <<EOF
[Unit]
Description=SmartAmbient Spring Boot Backend
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=$USER
Environment="JAVA_HOME=$JAVA_HOME_DIR"
Environment="PATH=$JAVA_HOME_DIR/bin:/usr/local/bin:/usr/bin:/bin"
WorkingDirectory=$HOME/smartAmbient/backend
ExecStart=$JAVA_PATH -jar $HOME/smartAmbient/backend/target/smartAmbient-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable smartambient
sudo systemctl start smartambient

echo ""
echo "============================================"
echo "  Setup Complete!"
echo "============================================"
echo ""
echo "Backend running on port 8080"
echo ""
echo "Useful commands:"
echo "  sudo systemctl status smartambient    - Check status"
echo "  sudo systemctl restart smartambient   - Restart"
echo "  sudo systemctl stop smartambient      - Stop"
echo "  sudo journalctl -u smartambient -f    - View logs"
echo ""
echo "IMPORTANT: Make sure GCP firewall allows TCP port 8080 inbound."
echo "  gcloud compute firewall-rules create allow-8080 \\"
echo "    --allow tcp:8080 --direction INGRESS --priority 1000 \\"
echo "    --target-tags=http-server --source-ranges=0.0.0.0/0"
echo ""
