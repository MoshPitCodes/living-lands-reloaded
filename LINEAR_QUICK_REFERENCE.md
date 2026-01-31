# Linear Quick Reference - Living Lands Reloaded

**Team:** moshpitcodes (MPC)  
**Board:** https://linear.app/moshpitcodes/team/MPC/active

---

## 📊 Custom Views (Quick Access)

### v1.3.0 Release Tracker ✨
**Description:** All issues for v1.3.0 - Testing & Performance release  
**Color:** Green (#0E8A16)  
**View ID:** 060029a4-0afc-4610-8c26-0740b4891f41

**Shows all 8 v1.3.0 issues:**
- MPC-87 - Multi-Player Stress Testing (URGENT)
- MPC-84 - Professions Tier 3 Abilities (HIGH)
- MPC-85 - JMH Benchmark Suite (MEDIUM)
- MPC-86 - Unit Test Infrastructure (MEDIUM)
- MPC-47 - Announcer: Placeholder System (HIGH)
- MPC-48 - Announcer: Recurring Announcements (HIGH)
- MPC-49 - Announcer: Admin Commands (HIGH)
- MPC-50 - Announcer: Testing & Polish (HIGH)

### Announcer Module ✨
**Description:** All Announcer module issues  
**Color:** Orange (#F2994A)  
**View ID:** 3fe58901-3f38-420f-be57-31a66448df41

**Shows:** MPC-47, MPC-48, MPC-49, MPC-50

### Critical & Urgent ✨
**Description:** All urgent and high priority issues (Priority 1-2)  
**Color:** Red (#EB5757)  
**View ID:** 97255d18-4164-4ab9-89ed-8b361fffeef9

**Shows:** MPC-87 (Urgent) + all HIGH priority issues (9 total)

### Testing & Performance ✨
**Description:** All testing and performance-related issues  
**Color:** Yellow (#F2C94C)  
**View ID:** 0f3aa0b7-1bce-4978-94bb-dc54a8304206

**Shows:** MPC-87, MPC-85, MPC-86, MPC-50 (4 issues)

---

## 🏷️ Filtering by Labels

### v1.3.0 Release Issues (8 issues)
**Filter:** `label:v1.3.0`  
**URL:** https://linear.app/moshpitcodes/team/MPC/active?filter=label%3Av1.3.0

**Shows:**
- MPC-87 - Multi-Player Stress Testing (URGENT)
- MPC-84 - Professions Tier 3 Abilities (HIGH)
- MPC-85 - JMH Benchmark Suite (MEDIUM)
- MPC-86 - Unit Test Infrastructure (MEDIUM)
- MPC-47 - Announcer: Placeholder System (HIGH)
- MPC-48 - Announcer: Recurring Announcements (HIGH)
- MPC-49 - Announcer: Admin Commands (HIGH)
- MPC-50 - Announcer: Testing & Polish (HIGH)

### Announcer Module Issues
**Filter:** `label:announcer`  
**Shows:** MPC-47, MPC-48, MPC-49, MPC-50

### Professions Module Issues
**Filter:** `label:professions`  
**Shows:** MPC-84 (+ future professions work)

### Testing Issues
**Filter:** `label:testing`  
**Shows:** MPC-87, MPC-85, MPC-86, MPC-50

### Performance Issues
**Filter:** `label:performance`  
**Shows:** MPC-85 (JMH Benchmarks)

---

## 🔄 Cycles

### v1.3.0 - Testing & Performance
**Dates:** Feb 1 - Mar 15, 2026  
**Cycle Number:** #1  
**ID:** adc18745-3b7a-4fea-ab1a-7173334de746

**Status:** 0% complete (all issues in Backlog)  
**Effort:** 18 story points (~11-17 days of work)  
**Buffer:** ~26-32 days for polish and contingency

**⚠️ Manual Action Required:**
Issues have `v1.3.0` label but need manual cycle assignment in Linear web UI.

### v1.4.0 - Economy & Moderation
**Dates:** Mar 16 - Apr 15, 2026  
**Cycle Number:** #2

### v1.5.0 - Territory & Encounters
**Dates:** Apr 16 - May 15, 2026  
**Cycle Number:** #3

### v1.6.0 - Social Features
**Dates:** May 16 - Jun 15, 2026  
**Cycle Number:** #4

---

## 📊 Priority Levels

### URGENT (Priority 1)
- **MPC-87** - Multi-Player Stress Testing (BLOCKS v1.3.0)

### HIGH (Priority 2)
- **MPC-84** - Professions Tier 3 Abilities
- **MPC-47-50** - Announcer Module (4 issues)

### MEDIUM (Priority 3)
- **MPC-85** - JMH Benchmark Suite
- **MPC-86** - Unit Test Infrastructure
- Most Economy, Claims, Encounters issues

### LOW (Priority 4)
- Groups issues (require other modules first)
- Retrospective documentation issues

---

## 🎯 Quick Actions

### Access Custom Views (FASTEST METHOD)
1. Go to Linear: https://linear.app/moshpitcodes/team/MPC/active
2. Click **"Views"** in left sidebar
3. Select view:
   - **v1.3.0 Release Tracker** - All v1.3.0 issues (8 issues)
   - **Announcer Module** - Announcer work only (4 issues)
   - **Critical & Urgent** - High-priority work (9 issues)
   - **Testing & Performance** - Testing/perf issues (4 issues)

### Filter v1.3.0 Issues for Cycle Assignment
**Option 1 (Recommended):** Use "v1.3.0 Release Tracker" custom view
1. Open "v1.3.0 Release Tracker" view from sidebar
2. Select all 8 issues (Cmd/Ctrl+Click)
3. Right-click → Assign to Cycle → "v1.3.0 - Testing & Performance"

**Option 2:** Use label filter
1. Go to: https://linear.app/moshpitcodes/team/MPC/active
2. Click filter icon → Select label: `v1.3.0`
3. Follow steps 2-3 from Option 1

### View Completed Work (Retrospective)
**Filter:** `status:done`  
**Shows:** 9 completed issues (8 retrospective + 1 previous)

---

## 🏗️ Issue Naming Conventions

**Format:** `[Module] Feature/Task Description`

**Examples:**
- `[Testing] Multi-Player Stress Testing (50+ Players)`
- `[Professions] Tier 3 Abilities Implementation`
- `[Announcer] Build Placeholder System`
- `[Economy] Currency System & Wallet`
- `[Claims] Land Protection Core`

---

## 📋 Labels Reference

| Label | Color | Description | Usage |
|-------|-------|-------------|-------|
| `v1.3.0` | Green #0E8A16 | v1.3.0 release issues | Version tracking |
| `announcer` | Orange #F2994A | Announcer module | Feature area |
| `professions` | Purple #BB6BD9 | Professions module | Feature area |
| `enhancement` | Blue #5E6AD2 | New feature/enhancement | Issue type |
| `testing` | Yellow #F2C94C | Testing related | Issue type |
| `documentation` | Gray #95A2B3 | Documentation | Issue type |
| `performance` | Red #EB5757 | Performance work | Issue type |
| `integration` | Purple #9B51E0 | Third-party integration | Issue type |

---

## 🔍 Search Tips

### Find Issues by Module
- `label:announcer`
- `label:professions`
- Future: `label:economy`, `label:claims`, `label:groups`

### Find Issues by Release
- `label:v1.3.0` (current release)
- Future: `label:v1.4.0`, `label:v1.5.0`, `label:v1.6.0`

### Combine Filters
- `label:v1.3.0 priority:urgent` → Critical blockers for v1.3.0
- `label:announcer status:backlog` → Announcer work not started
- `label:testing estimate:>0` → Testing issues with estimates

---

## 📅 Weekly Planning

### Week 3 (Current)
1. Manually assign v1.3.0 issues to cycle (5 min)
2. Link v1.3.0 dependencies (30 min)
3. Start multi-player testing planning (MPC-87)

### Week 4-6
1. Execute multi-player stress testing (MPC-87)
2. Begin parallel work (MPC-84-86, Announcer)
3. Add estimates to v1.4.0+ issues

---

## 🔗 External Links

- **Linear Board:** https://linear.app/moshpitcodes/team/MPC/active
- **GitHub Repo:** https://github.com/MoshPitCodes/hytale-livinglands
- **v2.6.0-beta Repo:** https://github.com/MoshPitCodes/hytale-livinglands (reference)

---

---

## ✨ Custom Views Summary

| View Name | Color | Purpose | Issues |
|-----------|-------|---------|--------|
| **v1.3.0 Release Tracker** | Green #0E8A16 | Current release scope | 8 |
| **Announcer Module** | Orange #F2994A | Announcer feature work | 4 |
| **Critical & Urgent** | Red #EB5757 | Priority 1-2 issues | ~10 |
| **Testing & Performance** | Yellow #F2C94C | Testing/perf work | 4 |

All views are **shared** with the team for collaborative planning.

**How to Access:** Linear UI → Left Sidebar → "Views" → Select view name

---

**Last Updated:** 2026-01-31 (✨ Custom views added!)  
**Current Release:** v1.2.3 (Production Ready)  
**Next Release:** v1.3.0 - Testing & Performance (Feb 1 - Mar 15, 2026)
