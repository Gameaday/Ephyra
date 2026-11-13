#!/bin/bash

# Local CI Script
# Runs the exact same checks as GitHub Actions CI
# This ensures "if it passes locally, it passes remotely"

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "=========================================="
echo "  Running Local CI Checks"
echo "=========================================="
echo ""

# Step 1: Apply formatting (same as build.yml quality job)
echo "[1/4] Applying code format fixes..."
./gradlew spotlessApply --build-cache --parallel --no-daemon

# Check if formatting was applied
if ! git diff --cached --quiet; then
    echo ""
    echo "⚠️  Format was applied. Please review and commit:"
    echo "   git add -A && git commit -m 'style: apply spotless formatting'"
    echo ""
    echo "Local CI: FORMAT FIXED (needs commit)"
    exit 0
fi

# Step 2: Check formatting
echo "[2/4] Checking code format..."
./gradlew spotlessCheck --build-cache --parallel --no-daemon

# Step 3: Run architecture checks (same as build.yml)
echo "[3/4] Running architecture checks..."

# No android.* imports in domain modules
VIOLATIONS=$(grep -rn --include="*.kt" "^\s*import android\\." \
  core/domain/src domain/src 2>/dev/null || true)
if [ -n "$VIOLATIONS" ]; then
    echo "::error::android.* imports found in domain modules"
    echo "$VIOLATIONS"
    exit 1
fi

# No Injekt.get()
VIOLATIONS=$(grep -rHn --include="*.kt" --exclude-dir="uy" "Injekt\\.get(" . || true)
if [ -n "$VIOLATIONS" ]; then
    echo "::error::Injekt.get() found in codebase"
    echo "$VIOLATIONS"
    exit 1
fi

# Step 4: Run unit tests (same as build.yml build job)
echo "[4/4] Running unit tests..."
./gradlew testDebugUnitTest --build-cache --parallel --no-daemon

echo ""
echo "=========================================="
echo "  ✅ All Local CI Checks Passed!"
echo "=========================================="
echo ""
echo "Your changes should pass remote CI."
echo "Push with confidence: git push origin <branch>"
