#!/bin/bash

# Setup Git Hooks Script
# Installs pre-commit hooks to automate formatting checks

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "Setting up Git hooks..."

# Create .git/hooks directory if it doesn't exist
mkdir -p .git/hooks

# Install pre-commit hook
PRE_COMMIT_HOOK=".git/hooks/pre-commit"

cat > "$PRE_COMMIT_HOOK" << 'HOOK_EOF'
#!/bin/sh

# Ephyra pre-commit hook
# Runs formatting checks before allowing commit

set -e

echo "Running pre-commit checks..."

# Run pre-commit script
./scripts/pre-commit.sh

echo "✓ Pre-commit checks passed"
HOOK_EOF

chmod +x "$PRE_COMMIT_HOOK"

echo "✓ Pre-commit hook installed"
echo ""
echo "Git hooks are now set up. The pre-commit hook will:"
echo "  - Apply spotless formatting automatically"
echo "  - Add formatted files to staging"
echo "  - Verify formatting passes"
echo ""
echo "You can skip the hook with: git commit --no-verify"
echo "You can remove the hook with: rm .git/hooks/pre-commit"
