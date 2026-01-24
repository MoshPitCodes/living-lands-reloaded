# Git Flow Workflow Guide - Living Lands

## Overview

This document outlines the Git Flow branching strategy for the **Living Lands** Hytale server mod project. All team members should follow this workflow to ensure smooth collaboration and release management.

## Branch Structure

```
main                    (Protected) - Production releases only
    ↑
release/vX.Y.Z          (Temporary) - Release preparation
    ↑ ↑
    | develop           (Protected) - Development integration branch
    |   ↑
    |   feature/*       (Temporary) - Feature development branches
    |
hotfix/*                (Temporary) - Emergency production fixes
```

### Branch Types

| Branch Type | Base Branch | Purpose | Merge Target |
|------------|-------------|---------|--------------|
| **main** | N/A | Production-ready code | Protected (no direct commits) |
| **develop** | N/A | Integration branch for all features | Protected (no direct commits) |
| **feature/\*** | develop | Individual feature development | develop |
| **release/vX.Y.Z** | develop | Code preparation for release | main + develop |
| **hotfix/\*** | main | Emergency production fixes | main + develop |

## Feature Workflow

Use feature branches for new features, enhancements, or non-breaking changes.

### Starting a Feature

```bash
# 1. Ensure develop is up to date
git checkout develop
git pull origin develop

# 2. Create a new feature branch
git checkout -b feature/your-feature-name

# 3. Push to remote and set tracking
git push -u origin feature/your-feature-name
```

**Feature Branch Naming Convention:**
- `feature/metabolism-system` - Main survival mechanics
- `feature/player-leveling` - XP and profession system
- `feature/hud-display` - HUD overlay components
- `feature/land-claims` - Claim protection system
- `feature/command-system` - Command implementations

### Working on a Feature

```bash
# Commit changes with conventional commit format
git add .
git commit -m "feat(metabolism): add hunger stat tracking"

# Push regularly for backup
git push
```

**Commit Message Format:**
```
<type>(<scope>): <description>

[optional body]

🤖 Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation changes
- `style` - Code style changes (formatting)
- `refactor` - Code refactoring
- `test` - Test additions or changes
- `chore` - Maintenance tasks

### Completing a Feature

```bash
# 1. Ensure all tests pass
./gradlew build

# 2. Update develop and merge
git checkout develop
git pull origin develop
git merge --no-ff feature/your-feature-name

# 3. Push develop
git push origin develop

# 4. Delete feature branches
git branch -d feature/your-feature-name
git push origin --delete feature/your-feature-name
```

## Release Workflow

Use release branches when preparing to release a new version.

### Starting a Release

```bash
# 1. Ensure develop is up to date
git checkout develop
git pull origin develop

# 2. Create release branch (version format: vX.Y.Z)
git checkout -b release/v2.8.0

# 3. Update version in relevant files
# Edit build.gradle.kts, version constants, etc.

# 4. Commit version bump
git commit -am "chore(release): bump version to 2.8.0"

# 5. Push to remote
git push -u origin release/v2.8.0
```

### Completing a Release

```bash
# 1. Merge to main
git checkout main
git pull origin main
git merge --no-ff release/v2.8.0

# 2. Tag the release
git tag -a v2.8.0 -m "Release v2.8.0 - Metabolism system with HUD"
git push origin main --tags

# 3. Merge back to develop
git checkout develop
git merge --no-ff release/v2.8.0
git push origin develop

# 4. Prepare for next version (optional bump to X.Y.Z-SNAPSHOT)
# Edit version files and commit
git commit -am "chore(release): bump version to 2.9.0-SNAPSHOT"
git push

# 5. Delete release branch
git branch -d release/v2.8.0
git push origin --delete release/v2.8.0
```

## Hotfix Workflow

Use hotfix branches for emergency fixes to production code.

### Starting a Hotfix

```bash
# 1. Ensure main is up to date
git checkout main
git pull origin main

# 2. Create hotfix branch
git checkout -b hotfix/critical-bug-fix

# 3. Push to remote
git push -u origin hotfix/critical-bug-fix
```

### Completing a Hotfix

```bash
# 1. Merge to main
git checkout main
git pull origin main
git merge --no-ff hotfix/critical-bug-fix

# 2. Tag the hotfix
git tag -a v2.8.1 -m "Hotfix v2.8.1 - Critical bug fix"
git push origin main --tags

# 3. Merge to develop
git checkout develop
git merge --no-ff hotfix/critical-bug-fix
git push origin develop

# 4. Delete hotfix branch
git branch -d hotfix/critical-bug-fix
git push origin --delete hotfix/critical-bug-fix
```

## Pull Request Process

All merges to `develop` and `main` should go through Pull Requests.

### Creating a Pull Request

```bash
# Ensure branch is pushed
git push

# Use GitHub CLI to create PR
gh pr create --title "feat(metabolism): add hunger stat tracking" \
  --body "$(cat <<'EOF'
## Summary
- Implemented hunger stat tracking system
- Added hunger decay based on activity
- Integrated with HUD display system

## Type of Change
- [x] Feature

## Test Plan
- Start server with test mod
- Join game and observe hunger stat
- Verify hunger decreases over time
- Check HUD displays correct values

## Checklist
- [x] Code follows project style guidelines
- [x] Self-reviewed code changes
- [x] Commented complex logic
- [x] Updated documentation if needed
- [x] Tests passing locally

🤖 Generated with Claude Code
EOF
)"
```

### PR Labels

Use appropriate labels based on branch type:
- `feature` - For feature branches
- `bug` - For bug fixes
- `hotfix` - For hotfix branches
- `release` - For release branches
- documentation - For doc changes

## Branch Protection Rules

The repository has the following protection rules configured:

### Protected Branches
- **main**
- **develop**

### Protection Rules
- **Require Pull Request**: Direct pushes are blocked
- **Block Force Pushes**: Cannot force push to protected branches
- **Require Linear History**: No merge commits
- **Require Resolved Comments**: All conversations must be resolved
- **Require Signed Commits**: All commits must be GPG signed (optional)

These are enforced via GitHub Rulesets in `.github/rulesets/`.

## Versioning

Living Lands uses Semantic Versioning:

```
vMAJOR.MINOR.PATCH
```

- **MAJOR**: Incompatible API changes (e.g., v2.8.0 → v3.0.0)
- **MINOR**: New backwards-compatible features (e.g., v2.8.0 → v2.9.0)
- **PATCH**: Backwards-compatible bug fixes (e.g., v2.8.0 → v2.8.1)

### Current Version
- **Main Branch**: v2.8.0-beta
- **Next Version**: v2.8.0 (targeted release)

## Development Lifecycle

### Phases (from IMPLEMENTATION_PLAN.md)

1. **Phase 0**: Core infrastructure ✅
2. **Phase 1**: Metabolism system (current focus)
3. **Phase 2**: Leveling system
4. **Phase 3**: Claims system
5. **Phase 4**: HUD enhancements

Each phase should have its own feature branches.

## Team Member Guidelines

### Do ✅

- Always pull before creating new branches
- Use descriptive branch names following conventions
- Write meaningful commit messages with conventional format
- Run tests before completing branches (`./gradlew build`)
- Keep feature branches small and focused
- Delete branches after merging
- Create PRs for all features and hotfixes
- Review code before approval
- Update documentation when adding features

### Don't ❌

- Never push directly to main or develop
- Never force push to shared branches
- Never merge without running tests
- Never create branches with unclear names (e.g., `fix-bug`, `my-feature`)
- Never leave stale branches undeleted
- Never ignore merge conflicts - resolve them carefully
- Never commit secrets or sensitive data

## Conflict Resolution

When encountering merge conflicts:

```bash
# 1. Identify conflicting files
git status

# 2. Open files in your IDE
# Look for conflict markers: <<<<<<<, =======, >>>>>>>

# 3. Resolve conflicts manually
# - Understand what each side represents
# - Keep the changes that make sense
# - Remove conflict markers

# 4. Mark files as resolved
git add <resolved-files>

# 5. Verify resolution
git diff --check

# 6. Complete merge
git commit
```

## Quick Reference

### Common Commands

```bash
# Start feature
git checkout develop && git pull
git checkout -b feature/your-feature
git push -u origin feature/your-feature

# Finish feature
git checkout develop && git pull
git merge --no-ff feature/your-feature
git push
git branch -d feature/your-feature
git push origin --delete feature/your-feature

# Check status
git status
git log --oneline --graph --all

# Sync with remote
git fetch --all
git pull --rebase
```

### Status Report

```bash
# View current branch info
git branch -vv

# See commits ahead/behind
git log @{u}..
```

## Automation Support

This repository supports Git Flow workflow automation via Claude Code's **git-flow-manager** agent. Use it to:

- Create feature/release/hotfix branches
- Complete branches with proper merging
- Generate pull requests
- Validate branch names
- Generate changelogs for releases

Example: "Create feature branch for metabolism system"

## Additional Resources

- [Git Flow Original](https://nvie.com/posts/a-successful-git-branching-model/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- Project Docs: `docs/IMPLEMENTATION_PLAN.md`
- Tech Design: `docs/TECHNICAL_DESIGN.md`

## Questions?

For questions about Git Flow workflow for the Living Lands project, contact the team or consult:
- `AGENTS.md` - Agent usage guidelines
- `docs/TECHNICAL_DESIGN.md` - Architecture details
- `docs/IMPLEMENTATION_PLAN.md` - Task breakdown