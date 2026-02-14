#!/usr/bin/env python3
"""
SmartAmbient Raspberry Pi Hub
Main entry point for the IoT hub application
"""

import os
import sys
import signal
import logging
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=getattr(logging, os.getenv('LOG_LEVEL', 'INFO')),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('SmartAmbient')

from hub import app, init_mqtt, config

def signal_handler(sig, frame):
    """Handle shutdown signals gracefully"""
    logger.info("Shutdown signal received, cleaning up...")
    sys.exit(0)

def main():
    """Main entry point"""
    # Register signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    logger.info("=" * 50)
    logger.info("SmartAmbient Raspberry Pi Hub Starting...")
    logger.info("=" * 50)

    try:
        # Initialize MQTT
        if not init_mqtt():
            logger.error("Failed to initialize MQTT, exiting")
            sys.exit(1)

        # Give MQTT time to connect
        time.sleep(1)

        # Start Flask
        flask_config = config.get("flask", {})
        app.run(
            host=flask_config.get("host", "0.0.0.0"),
            port=flask_config.get("port", 5000),
            debug=flask_config.get("debug", False)
        )
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
