## Summary

<!-- Provide a clear overview of what this PR accomplishes -->

### [Feature/Fix/Performance/Refactor] Category 1

**Key Changes:**
- Change 1
- Change 2

**Impact/Benefits:**
<!-- For performance improvements, include metrics table -->
| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
|        |        |       |             |

### [Optional] Additional Categories

**Features/Changes:**
- Item 1
- Item 2

## Type of Change

<!-- Mark relevant options with 'x' -->

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] 📚 Documentation update
- [ ] ⚙️ Configuration change
- [ ] ♻️ Refactoring (no functional changes)
- [ ] ⚡ Performance improvement
- [ ] 🧪 Test updates

## Files Changed

**New Files:**
- `path/to/file.kt` - Description of what this file does

**Modified Files:**
- `path/to/file.kt` - What was changed and why

**Deleted Files:**
- `path/to/file.kt` - Reason for deletion

## Testing

<!-- Describe the testing you've performed -->

### Build & Compilation

- [ ] ✅ Build successful: `./gradlew build`
- [ ] ✅ No new warnings
- [ ] ✅ ShadowJar generated successfully

### Manual Testing

- [ ] ✅ Deployed JAR to test server
- [ ] ✅ Server starts without errors
- [ ] ✅ Plugin loads correctly
- [ ] ✅ Config files generate/migrate properly

### Specific Feature Testing

<!-- List specific tests performed for this PR's features -->

1. Test case 1
2. Test case 2
3. Test case 3

### Performance Testing (if applicable)

- [ ] Tested with X concurrent players
- [ ] No performance regressions
- [ ] Performance improvements verified with metrics

## Documentation

<!-- Mark completed items with 'x' -->

- [ ] README.md updated (if user-facing changes)
- [ ] CHANGELOG.md updated with version entry
- [ ] TECHNICAL_DESIGN.md updated (if architecture changes)
- [ ] IMPLEMENTATION_PLAN.md phase marked complete
- [ ] KDoc comments added for public APIs
- [ ] Migration guide provided (if breaking changes)

## Code Quality Checklist

### General

- [ ] Code follows Kotlin coding conventions
- [ ] Self-review completed
- [ ] Proper error handling with try/catch
- [ ] No hardcoded values (use config)
- [ ] Thread-safe operations (synchronized, ConcurrentHashMap)

### Hytale API Integration

- [ ] ECS access wrapped in `world.execute { }`
- [ ] Event handlers have error handling
- [ ] Commands extend `CommandBase`
- [ ] API verified against `docs/HYTALE_API_REFERENCE.md`

### Performance

- [ ] No unnecessary allocations in hot paths
- [ ] Database operations are async (Dispatchers.IO)
- [ ] Proper resource cleanup (connections, caches)
- [ ] Logging uses appropriate levels (FINE/INFO/SEVERE)

### Architecture

- [ ] Per-world data isolation maintained
- [ ] Service registry used for cross-module communication
- [ ] Config stored in YAML, not database
- [ ] Module lifecycle properly implemented

## Related Issues

<!-- Link to related issues -->

Closes #
Relates to #
Phase: <!-- e.g., Phase 7: Buffs & Debuffs -->

## Breaking Changes

- [ ] No breaking changes

<!-- If there are breaking changes, describe them: -->

**Migration Path:**
<!-- Describe how users should migrate from old version -->

1. Step 1
2. Step 2

## Additional Notes

<!-- Add any additional context, concerns, or implementation notes -->

### Known Issues/Limitations

<!-- List any known issues or limitations -->

### Future Work

<!-- List any follow-up work or improvements planned -->

## Reviewer Notes

<!-- Any specific areas you'd like reviewers to focus on? -->

---

**Version:** 1.0.0-beta  
**Branch:** <!-- feature/fix/perf/refactor name -->
