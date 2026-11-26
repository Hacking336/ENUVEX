# Matching Engine Documentation

**Job & Skills Matching (JSM) — final release**
Implementation: `backend/src/main/java/com/jobskills/service/MatchingEngine.java` (518 lines, single class, no external service, no model, no randomness).

---

## 1. What it is

The engine is a **deterministic weighted scoring algorithm**. Given one job-seeker profile and one job post it returns:

- an overall percentage (0–100, one decimal),
- a band label,
- the five criterion results, each with its own score, points, status and a human-readable sentence,
- the matched and missing required skills.

Determinism matters in this project: the same profile and the same post always produce the same percentage, in every interface and at every run. That is what allows the employer's applicant list, the seeker's job list and the administrator's statistics to agree, and it is what makes every score explainable to the user who receives it.

**This is not AI and not machine learning.** There is no training data, no model file, no inference service, no embedding, no natural-language understanding and no popularity weighting. The result is arithmetic over values the users themselves entered.

## 2. Weights (verified in source)

```java
public static final double WEIGHT_SKILLS          = 40.0;
public static final double WEIGHT_LOCATION        = 25.0;
public static final double WEIGHT_EMPLOYMENT_TYPE = 15.0;
public static final double WEIGHT_EXPERIENCE      = 10.0;
public static final double WEIGHT_SALARY          = 10.0;
```

| Criterion | Weight | Why this weight |
| --- | --- | --- |
| Skills | 40 % | The main reason a local employer rejects an applicant; the only criterion that can be judged objectively from both sides |
| Location | 25 % | In a rural municipality, commute feasibility decides whether an offer is usable |
| Employment type | 15 % | A part-time student cannot take a full-time shift; this is a hard practical constraint |
| Experience | 10 % | Most listed posts ask for 0–2 years; it filters rather than decides |
| Salary | 10 % | Many local posts are "negotiable", so salary must not dominate the score |
| **Total** | **100 %** | |

## 3. The final formula

```
applicable weight = Σ weights of criteria that can be judged
earned points     = Σ points earned in those criteria

overall % = earned points / applicable weight × 100      (clamped to 0–100, 1 decimal)
```

Each criterion earns `weight × criterion score / 100`, where the criterion score is itself 0–100. A criterion is **unavailable** when the stored data cannot support a judgement; it is removed from *both* the numerator and the denominator. Consequences:

- Missing data never counts as a mismatch (no unfair zero).
- Missing data never hands out free points (no unfair 100).
- A profile with only skills and location filled in is scored out of 65, not 100.
- The response carries `applicableWeight`, `earnedPoints` and `complete`, so the interface can say the score is based on a subset.

Worked example — a job with no salary published, a seeker with no employment-type preference:

```
skills 30/40 + location 20/25 + experience 8/10 = 58 earned
applicable = 40 + 25 + 10 = 75
overall = 58 / 75 × 100 = 77.3 %  → "Strong Match" (incomplete: type and salary left out)
```

## 4. How each criterion is scored

### 4.1 Skills — 40 %

- Required skills are split from the employer's `required_skills` text on `,` `;` newline `•` `|`, cleaned of extra spaces, de-duplicated by a normalised key (lower-case, punctuation-tolerant), and the employer's original wording is kept for display.
- The seeker's `skills` text is normalised the same way into a set.
- `score = matched / required × 100`; the criterion earns `40 × score / 100`.
- If the post lists **no** required skills → unavailable (left out of the denominator).
- If the seeker lists **no** skills → scored 0 of 40, with the explanation "Your profile lists no skills yet — N required skills are missing".
- Status: all matched → `MATCH`, some → `PARTIAL`, none → `NONE`.
- The same matched/missing lists are returned at the top level (`matchedSkills`, `missingSkills`, `totalRequiredSkills`) and drive the "Required skills" panel and the skills-gap analysis.

Matching is **exact on the normalised string**, not fuzzy: "Customer Service" and "customer service " match; "Customer Support" does not. This is deliberate — fuzzy matching would invent equivalences the engine cannot justify.

### 4.2 Location — 25 %

Municipality and barangay are compared; the coverage area's land borders are encoded in `NEARBY`:

```
BASUD   ↔ DAET
DAET    ↔ BASUD, VINZONS, LABO
VINZONS ↔ DAET, LABO
LABO    ↔ DAET, VINZONS, BASUD
```

| Situation | Criterion score | Points | Explanation shown |
| --- | --- | --- | --- |
| Same municipality **and** same barangay | 100 | 25.0 | "Same barangay (Barangay, Municipality)" |
| Same municipality, different barangay | 80 | 20.0 | "Same municipality (Daet), different barangay" |
| Neighbouring municipality (land border) | 50 | 12.5 | "Nearby municipality: Labo borders your Daet" |
| Outside the area | 10 | 2.5 | "Outside your preferred area (Labo is not Daet or next to it)" |
| Either side missing a municipality | unavailable | — | "One side has no municipality recorded…" |

Distance in kilometres is **not** part of the score; it is a separate search filter (`radiusKm`, `nearLatitude`, `nearLongitude`) and map concern. The score uses administrative geography because that is the data both sides actually provide.

### 4.3 Employment type — 15 %

The seeker's `employmentTypePreference` is compared with the post's `employmentType`, with a table of types a person would realistically accept:

| Preference | Also acceptable |
| --- | --- |
| FULL_TIME | CONTRACT |
| PART_TIME | CONTRACT, TEMPORARY |
| CONTRACT | FULL_TIME, PART_TIME, TEMPORARY |
| TEMPORARY | CONTRACT, PART_TIME |

| Situation | Score | Points |
| --- | --- | --- |
| Exact match | 100 | 15.0 |
| Compatible type | 60 | 9.0 |
| Incompatible type | 0 | 0.0 |
| Preference or post type missing | unavailable | — |

### 4.4 Experience — 10 %

The post's free-text requirement is parsed:

- "no experience", "entry level", "fresh graduate", "any", "0" → requirement of **0 years** → score 100 and the note "No experience required — suitable for fresh graduates".
- A number (optionally a range such as "2-3 years") → minimum years; the range is normalised if written backwards.
- Text with no number and no entry-level wording → unavailable.

The seeker's years come from the free-text `work_experience`: an explicit "N years" wins; a non-empty history with no number counts as **1 year** (the seeker recorded at least one job).

| Situation | Score | Points |
| --- | --- | --- |
| Requirement 0 years | 100 | 10.0 |
| Seeker years ≥ required | 100 | 10.0 |
| Seeker years < required, > 0 | `years / required × 100` (capped at 100) | proportional |
| Seeker has no work experience recorded | 0 | 0.0 |

### 4.5 Salary — 10 %

Only positive values are considered; blank or zero means "not stated".

| Situation | Score | Points |
| --- | --- | --- |
| Seeker expectation missing **or** job salary missing | unavailable | — |
| Job range's lower bound ≥ seeker's minimum | 100 | 10.0 |
| Ranges overlap | `50 + 50 × (overlap / job range)` | 5–10 |
| Job starts below the seeker's minimum but has no upper bound | 60 | 6.0 |
| Job maximum below the seeker's minimum | 0 | 0.0 |
| Job range entirely above the seeker's maximum | 0 | 0.0 |

The overlap percentage is stated in the explanation ("about 60 % of the offered range is inside it"), so a 7.5/10 is not a mystery.

## 5. Match bands

| Percentage | Classification | Where it appears |
| --- | --- | --- |
| ≥ 90 | Excellent Match | Job cards, job detail, applicant list, admin analytics |
| 75 – 89.9 | Strong Match | ditto |
| 60 – 74.9 | Good Match | ditto |
| 40 – 59.9 | Partial Match | ditto |
| < 40 | Low Match | ditto |

Bands are also searchable: `matchBand=excellent|strong|good|partial` and `minMatchScore=<n>` filter the job list. `MatchingEngine.minimumScoreForBand` is the single mapping used by both.

## 6. How explanations are produced

Every criterion returns a `MatchCriterionDto`:

| Field | Example |
| --- | --- |
| `key` | `location` |
| `label` | `Location` |
| `weight` | `25.0` |
| `applicable` | `true` |
| `earnedPoints` | `20.0` |
| `score` | `80.0` |
| `status` | `MATCH` / `PARTIAL` / `NONE` |
| `detail` | "Same municipality (Daet), different barangay" |

Unexplained cases do not exist: an unavailable criterion carries the reason it was left out ("This employer did not disclose a salary, so salary is left out"). The frontend's `assets/js/jsm-match.js` renders the badge, the band, the per-criterion bars and the detail sentence; the job detail page adds a "Why this job matches you" list and the "Before you apply" checklist derived from the missing skills.

## 7. Where the engine is used

| Surface | Endpoint | Ordering |
| --- | --- | --- |
| Seeker job search with scores | `GET /api/jobs/with-match` | Any supported sort, score included |
| Seeker recommended jobs | `GET /api/job-seekers/recommended` | Highest score first |
| Job detail | `GET /api/job-seekers/jobs/{id}/match` | Single result with criteria |
| Employer applicant list | `GET /api/applications/job/{jobId}/applicants` | Ranked by score |
| Employer matching insights | `GET /api/employers/analytics/matching` | Distribution, averages |
| Admin matching statistics | `GET /api/admin/matching/stats` | Aggregates |
| Application record | `POST /api/job-seekers/apply` | Score snapshot stored in `job_applications.match_percentage` |

One engine, one formula, no duplicated scoring code — verified by inspection of the callers.

## 8. Recommendations

`/api/job-seekers/recommended` returns active, approved posts ordered by computed score, using the same criteria. The dashboard shows the top few with the band and the single most useful next step (missing skills), and the profile completeness panel (`profileCompleteness`) lists the six fields that influence a score, their weights (skills 25, work experience 20, education 15, location 20, employment type 10, salary 10) and a direct link to the exact section of the profile that needs the data.

## 9. Limitations, stated honestly

1. **Free-text inputs are parsed, not verified.** "5 years" in a work-history sentence is taken at face value; "three years" written in words is scored as 1 year. Skill strings must match exactly after normalisation.
2. **Synonym blindness.** "Cashiering" and "Cashier", "BSIT" and "Information Technology" are different strings. The skill catalogue and skills-gap analysis help, but the engine itself does not expand synonyms.
3. **Binary education is not scored.** Education level is displayed and filterable, but it is not one of the five weighted criteria; only the README of an earlier phase claimed otherwise. This document reflects the code.
4. **Distance is coarse.** Location uses municipality/barangay adjacency, not road distance or travel time; 25 % is awarded for "same municipality", even for a 20 km barangay.
5. **Salary negotiation is over-simplified.** A range that overlaps slightly scores almost like a perfect overlap; hiring bonuses and allowances are not modelled.
6. **No learning from outcomes.** The weights are fixed by design. If employers consistently hire at 65 %, the engine does not adapt — that would require outcome data and an explicitly validated model, which this project does not claim.
7. **Score is not a hiring decision.** It is a compatibility indicator; the employer decides, and the interface says so on the applicant and job pages.
8. **Cold-start penalty is visible, not hidden.** A new seeker with an empty profile gets low scores until the profile is completed — the completeness panel exists precisely to explain that.
