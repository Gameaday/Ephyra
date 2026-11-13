#!/bin/bash

# Pre-commit Hook Script
# Runs quick checks before allowing a commit
# Full CI should still be run before pushing

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "Running pre-commit checks..."

# Apply and check formatting
./gradlew spotlessApply --build-cache --parallel --no-daemon > /dev/null 2>&1

if ! git diff --cached --quiet; then
    echo "✓ Format fixed by spotless"
    git add -A
fi

./gradlew spotlessCheck --build-cache --parallel --no-daemon > /dev/null 2>&1

echo "✓ Pre-commit checks passed"
