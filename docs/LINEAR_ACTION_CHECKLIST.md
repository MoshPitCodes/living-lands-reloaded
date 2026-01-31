# Linear Audit - Action Checklist

**Date Created:** 2026-01-31  
**Target Completion:** 2026-02-07 (This week + next week)

---

## ✅ Immediate Actions (Completed)

- [x] **Audit Linear board** - All 56 issues analyzed
- [x] **Identify missing work** - 3 critical issues found
- [x] **Create MPC-84** - Professions Tier 3 Abilities (HIGH, v1.3.0)
- [x] **Create MPC-85** - JMH Benchmark Suite (MEDIUM, v1.3.0)
- [x] **Create MPC-86** - Unit Test Infrastructure (MEDIUM, ongoing)
- [x] **Update MPC-29** - Mark as Done (Metabolism testing)
- [x] **Update MPC-76-83** - Raise priority from Low(4) to Medium(3)

**Status:** ✅ COMPLETE (All in-system actions done)

---

## 🎯 This Week (Priority 1)

### 1. Create Linear Projects
- [ ] Create "v1.3.0 Release" project (Feb 2026)
- [ ] Create "v1.4.0 Release" project (Mar 2026)
- [ ] Create "v1.5.0 Release" project (Apr 2026)
- [ ] Create "v1.6.0 Release" project (May 2026)

**Time:** 5 minutes

### 2. Create Retrospective Issues (8 total)
These document completed phases and restore deleted MPC-1-27:

- [ ] Create: "[Phase 0] Project Setup & Plugin Bootstrap" → Mark DONE
- [ ] Create: "[Phase 1] Core Infrastructure Implementation" → Mark DONE
- [ ] Create: "[Phase 2] Persistence Layer & Async Operations" → Mark DONE
- [ ] Create: "[Phase 3] Configuration System with Hot-Reload" → Mark DONE
- [ ] Create: "[Phase 3.5] Config Migration & Versioning" → Mark DONE
- [ ] Create: "[Phase 4] Module System & Dependency Management" → Mark DONE
- [ ] Create: "[Phase 6] Professions System - Tier 1 & 2" → Mark DONE
- [ ] Create: "[Phase 7] HUD System Implementation" → Mark DONE
- [ ] Create: "[Phase 8] Buffs/Debuffs System" → Mark DONE

**Time:** 30 minutes

### 3. Assign Issues to Projects
- [ ] Assign Phase 9 testing (MPC-37-42) → v1.3.0 project
- [ ] Assign Announcer (MPC-43-50) → v1.3.0 project
- [ ] Assign Tier 3 Professions (MPC-84) → v1.3.0 project
- [ ] Assign Benchmarks (MPC-85) → v1.3.0 project
- [ ] Assign all Economy/Moderation → v1.4.0 project
- [ ] Assign all Claims/Encounters → v1.5.0 project
- [ ] Assign all Groups → v1.6.0 project

**Time:** 15 minutes

---

## 🔧 Next 2 Weeks (Priority 2)

### 4. Add Time Estimates to Issues

**v1.3.0 Issues (6-8 hours):**
- [ ] MPC-37: 3-4 days
- [ ] MPC-38: 3-4 days
- [ ] MPC-39: 2-3 days
- [ ] MPC-40: 2-3 days
- [ ] MPC-41: 2-3 days
- [ ] MPC-42: 2-3 days
- [ ] MPC-43: 1-2 days
- [ ] MPC-44: 1-2 days
- [ ] MPC-45: 1 day
- [ ] MPC-46: 1-2 days
- [ ] MPC-47: 1 day
- [ ] MPC-48: 1-2 days
- [ ] MPC-49: 1 day
- [ ] MPC-50: 1-2 days
- [ ] MPC-84: 3-5 days
- [ ] MPC-85: 2-3 days
- [ ] MPC-86: 2-3 days

**v1.4.0 Issues (1 hour):** See estimates in full report

**v1.5.0+ Issues (1 hour):** See estimates in full report

**Time:** 2 hours total

### 5. Update Phase 9 Testing Issues
- [ ] MPC-30: Add specific test scenarios (join, food, XP, world switch, cycles)
- [ ] MPC-37/38: Add success criteria and expected results
- [ ] MPC-39-42: Add performance baselines (target: <1ms metabolism, <5ms HUD)

**Time:** 1 hour

### 6. Make v1.3.0 Scope Decision
**Required:** Discussion/decision from project lead

Options:
- [ ] **Option A:** Keep full scope (34 days) - High risk
- [ ] **Option B:** Reduce scope (20-25 days) - Defer Tier 3 Professions

**Questions to resolve:**
- Can v1.3.0 realistically ship in 29 days?
- Should we reduce scope or extend deadline?
- What's the minimum viable feature set for v1.3.0?

**Time:** 30 minutes decision + 1 hour to implement changes

---

## 📋 This Month (Priority 3)

### 7. Create Release Cycles

- [ ] Create Cycle: "v1.3.0" (Feb 1-28, 2026)
  - [ ] Assign Phase 9 testing issues
  - [ ] Assign Announcer issues
  - [ ] Assign Tier 3 Professions (if included)
  - [ ] Assign Benchmarking

- [ ] Create Cycle: "v1.4.0" (Mar 1-31, 2026)
  - [ ] Assign Economy issues
  - [ ] Assign Moderation issues

**Time:** 1 hour

### 8. Link Cross-Module Dependencies

Use Linear's "relates to" or "blocks" fields:

- [ ] MPC-81 (Group Banks) → depends on MPC-51 (Economy)
- [ ] MPC-82 (Group Territories) → depends on MPC-62 (Claims)
- [ ] MPC-73 (World Bosses) → depends on MPC-43+ (Announcer)
- [ ] MPC-84 (Tier 3 Professions) → depends on v1.2.3 complete
- [ ] MPC-75 (Encounters Config) → depends on all Encounters modules
- [ ] MPC-56 (Economy Config) → depends on all Economy modules

**Time:** 2 hours

### 9. Add Performance Targets

Update these issues with performance goals:

- [ ] MPC-68 (Claims Testing): Add "Spatial queries <1ms with 1000+ claims"
- [ ] MPC-29 (Metabolism): Add "Tick rate <1ms per 50 players"
- [ ] MPC-41 (HUD Performance): Add "Render <5ms per player"
- [ ] MPC-85 (Benchmarks): Add specific baseline targets

**Time:** 30 minutes

---

## 🔄 Ongoing (Every Sprint)

### 10. Daily Standup Discipline
- [ ] Move issues to "In Progress" when work starts
- [ ] Add progress comments (not just status updates)
- [ ] Move to "Done" immediately when complete
- [ ] Update estimates if work differs from plan

**Time:** 5 minutes per day

### 11. Weekly Board Review
- [ ] Check if Backlog % is decreasing
- [ ] Verify cycle progress
- [ ] Identify any blockers
- [ ] Update next week's plan

**Time:** 15 minutes per week

---

## 📊 Success Metrics

Track these to verify improvements:

| Metric | Current | Target | Timeline |
|--------|---------|--------|----------|
| Issues in Backlog | 98.3% | <50% | By v1.3.0 |
| Issues w/ Estimates | 10% | 80%+ | This week |
| Issues w/ Project | 0% | 100% | This week |
| In Progress Issues | 0% | 10-20% | Daily |
| Done Issues | 1.7% | Increasing | Daily |

---

## 📄 Documentation

**Full Report:**
- `/home/moshpitcodes/Development/living-lands-reloaded/LINEAR_AUDIT_REPORT.md`

**Summary:**
- `/tmp/LINEAR_AUDIT_SUMMARY.txt`

**This Checklist:**
- `/home/moshpitcodes/Development/living-lands-reloaded/LINEAR_ACTION_CHECKLIST.md`

---

## 🎯 Timeline

- **This Week (Feb 1-7):** Complete actions 1-5 (projects, retrospectives, estimates)
- **Week of Feb 7-14:** Complete action 6 (scope decision) and 7 (cycles)
- **By v1.3.0 Release:** Complete actions 8-9 (dependencies, performance targets)
- **Ongoing:** Implement action 10-11 (daily discipline)

---

## Notes

- See full LINEAR_AUDIT_REPORT.md for detailed analysis and recommendations
- All Linear updates from original audit already completed (9 issues updated, 3 created)
- Main work now is organizational: projects, estimates, cycles, discipline
- v1.3.0 scope decision is critical - recommend Option B (reduced scope, higher confidence)

**Started:** 2026-01-31  
**Last Updated:** 2026-01-31  
**Status:** In Progress ⏳

