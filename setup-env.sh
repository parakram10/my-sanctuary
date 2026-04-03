#!/bin/bash

# Sanctuary Development Environment Setup
# This script loads API keys from .env file into the current shell session
#
# Usage:
#   source setup-env.sh
#   ./gradlew :composeApp:installDebug
#
# Or run commands directly:
#   ./gradlew-with-env :composeApp:installDebug

set -a  # Export all variables

# Load .env file
if [ -f .env ]; then
    echo "✓ Loading environment variables from .env"
    source .env
else
    echo "✗ .env file not found!"
    echo "  Please copy .env.example to .env and fill in your API keys:"
    echo "  cp .env.example .env"
    exit 1
fi

set +a  # Stop exporting variables

# Verify required keys are set
if [ -z "$GROQ_API_KEY" ] && [ -z "$CLAUDE_API_KEY" ]; then
    echo "✗ ERROR: No API keys configured!"
    echo "  Please set GROQ_API_KEY or CLAUDE_API_KEY in .env"
    exit 1
fi

# Show what's loaded (mask actual keys for security)
if [ ! -z "$GROQ_API_KEY" ]; then
    GROQ_DISPLAY="${GROQ_API_KEY:0:10}...${GROQ_API_KEY: -4}"
    echo "✓ GROQ_API_KEY: $GROQ_DISPLAY"
fi

if [ ! -z "$CLAUDE_API_KEY" ]; then
    CLAUDE_DISPLAY="${CLAUDE_API_KEY:0:10}...${CLAUDE_API_KEY: -4}"
    echo "✓ CLAUDE_API_KEY: $CLAUDE_DISPLAY"
fi

echo "✓ AI_PROVIDER: $AI_PROVIDER"
echo ""
echo "Environment ready! You can now:"
echo "  ./gradlew :composeApp:installDebug    # Build and install Android"
echo "  open iosApp/iosApp.xcodeproj           # Open iOS project"
