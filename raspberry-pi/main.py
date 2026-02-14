#!/usr/bin/env python3
"""
SmartAmbient Raspberry Pi Hub
Main entry point for the IoT hub application
"""

import os
import sys
import signal
import logging
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=getattr(logging, os.getenv('LOG_LEVEL', 'INFO')),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('SmartAmbient')

from hub import SmartAmbientHub

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
        hub = SmartAmbientHub()
        hub.start()
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
