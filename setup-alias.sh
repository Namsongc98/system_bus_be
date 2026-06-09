#!/bin/bash

################################################################################
# 🚀 Code Review CLI - Quick Setup
# This script sets up aliases for easy access
################################################################################

SCRIPT_DIR="/Users/ADMIN/Desktop/Wordspace/Programing/Java/ticket-system"
SCRIPT_PATH="$SCRIPT_DIR/code-review.sh"

echo "🔧 Setting up Code Review CLI..."

# Detect shell
if [ -n "$ZSH_VERSION" ]; then
    SHELL_RC="$HOME/.zshrc"
    SHELL_NAME="zsh"
elif [ -n "$BASH_VERSION" ]; then
    SHELL_RC="$HOME/.bashrc"
    SHELL_NAME="bash"
else
    echo "❌ Unsupported shell"
    exit 1
fi

echo "✅ Detected shell: $SHELL_NAME"
echo "✅ Config file: $SHELL_RC"

# Check if alias already exists
if grep -q "alias review=" "$SHELL_RC" 2>/dev/null; then
    echo "⚠️  Alias 'review' already exists in $SHELL_RC"
    echo ""
    read -p "Do you want to replace it? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "❌ Setup cancelled"
        exit 1
    fi
    # Remove old alias
    sed -i.bak '/alias review=/d' "$SHELL_RC"
fi

# Add new alias
echo "" >> "$SHELL_RC"
echo "# Code Review CLI Tool" >> "$SHELL_RC"
echo "alias review='cd $SCRIPT_DIR && ./code-review.sh'" >> "$SHELL_RC"

echo "✅ Alias added to $SHELL_RC"

# Reload shell config
if [ -n "$ZSH_VERSION" ]; then
    source "$SHELL_RC"
elif [ -n "$BASH_VERSION" ]; then
    source "$SHELL_RC"
fi

echo ""
echo "🎉 Setup complete!"
echo ""
echo "📚 You can now use:"
echo "   review /scan"
echo "   review /security"
echo "   review /fix"
echo "   review /help"
echo ""
echo "⚠️  If commands don't work, run:"
echo "   source $SHELL_RC"
echo ""

