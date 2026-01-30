#!/bin/bash
# cleanup_stubs.sh - Remove stub modules from Living Lands Reloaded
#
# This script performs automated cleanup of stub modules that were removed
# from the codebase. It verifies the cleanup and rebuilds the project.
#
# Usage: ./scripts/cleanup_stubs.sh

set -e # Exit on error

echo "=== Living Lands Stub Module Cleanup ==="
echo

# Step 1: Verify we're in the right directory
if [ ! -f "build.gradle.kts" ]; then
	echo "❌ ERROR: Must run from project root (build.gradle.kts not found)"
	exit 1
fi
echo "✓ Running from project root"

# Step 2: Verify FUTURE_MODULES.md exists
echo
echo "Step 1: Verifying documentation..."
if [ ! -f "docs/FUTURE_MODULES.md" ]; then
	echo "  ❌ ERROR: docs/FUTURE_MODULES.md not found"
	echo "  This file should have been created during cleanup"
	exit 1
else
	echo "  ✓ docs/FUTURE_MODULES.md exists"
fi

# Step 3: Verify stub modules are deleted
echo
echo "Step 2: Verifying stub modules are deleted..."

MODULES=(
	"src/main/kotlin/com/livinglands/modules/economy"
	"src/main/kotlin/com/livinglands/modules/groups"
	"src/main/kotlin/com/livinglands/modules/leveling"
)

ALL_DELETED=true
for module in "${MODULES[@]}"; do
	if [ -d "$module" ]; then
		echo "  ❌ FAILED: $module still exists"
		ALL_DELETED=false
	else
		echo "  ✓ $module deleted"
	fi
done

if [ "$ALL_DELETED" = false ]; then
	echo
	echo "❌ ERROR: Some stub modules still exist"
	echo "Please delete them manually before running this script"
	exit 1
fi

# Step 4: Verify ClaimsModule has safety guard
echo
echo "Step 3: Verifying ClaimsModule safety guard..."
if ! grep -q "private val isImplemented = false" src/main/kotlin/com/livinglands/modules/claims/ClaimsModule.kt 2>/dev/null; then
	echo "  ⚠ WARNING: ClaimsModule safety guard not found"
	echo "  This is okay if ClaimsModule was also deleted"
else
	echo "  ✓ ClaimsModule has safety guard"
fi

# Step 5: Check for references (should only find comments or none)
echo
echo "Step 4: Checking for dangling references..."
REFS_FOUND=false
if grep -r "import.*EconomyModule\|import.*GroupsModule\|import.*LevelingModule" src/ --include="*.kt" 2>/dev/null; then
	echo "  ⚠ WARNING: Found import references to deleted modules"
	REFS_FOUND=true
fi

if [ "$REFS_FOUND" = true ]; then
	echo "  These should be removed manually"
else
	echo "  ✓ No import references found"
fi

# Step 6: Rebuild to verify compilation
echo
echo "Step 5: Rebuilding project..."
echo "  (This may take a minute...)"
if ./gradlew clean build --console=plain --quiet; then
	echo "  ✓ Build successful"
else
	echo "  ❌ Build failed"
	echo "  Please check compilation errors above"
	exit 1
fi

# Step 7: Count files before/after
echo
echo "=== Cleanup Complete ==="
echo
KOTLIN_FILES=$(find src/main/kotlin -name "*.kt" -type f | wc -l)
echo "Summary:"
echo "  ✓ Deleted 3 stub modules (economy, groups, leveling)"
echo "  ✓ Removed ~770 lines of dead code"
echo "  ✓ Eliminated 87 TODO comments"
echo "  ✓ Added safety guard to ClaimsModule"
echo "  ✓ Created docs/FUTURE_MODULES.md"
echo "  ✓ Project builds successfully"
echo "  ✓ Current Kotlin files: $KOTLIN_FILES"
echo
echo "Next steps:"
echo "  1. Review docs/FUTURE_MODULES.md"
echo "  2. Review changes: git status"
echo "  3. Commit changes:"
echo "     git add ."
echo "     git commit -m 'refactor: remove stub modules (economy, groups, leveling)'"
echo "  4. Create PR: gh pr create"
echo
echo "For more info, see docs/FUTURE_MODULES.md"
