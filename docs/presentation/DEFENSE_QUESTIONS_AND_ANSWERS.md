# Defense Questions and Suggested Answers

Thirty-two realistic panel questions with answers grounded in the implementation. Where the honest answer is "we did not measure that", the answer says so — do not replace it with an invented figure.

---

## A. Motivation and problem

**1. Why did you develop this project?**
Because hiring in the four municipalities happens informally and nothing records it. Vacancies circulate by tarpaulin, social post and word of mouth; seekers miss posts, employers receive unsuitable applications, and no one can review what happened afterwards. We built one structured channel for the whole cycle — profile, search, match, apply, track, moderate — restricted to the local area.

**2. Why does this problem matter?**
It wastes time on both sides and hides opportunity. A qualified applicant a barangay away never sees the vacancy; an employer re-advertises the same post because the right people never saw it. And because nothing is recorded, fake or duplicated postings cannot be challenged. A local, low-bandwidth web platform is a realistic intervention for a small-business job market.

**3. Why these four municipalities?**
They form a contiguous area in the first district of Camarines Norte, they share a labour market, and they include both coastal and inland barangays. Our coverage area is exactly these four, enforced by an enumeration in the code — a post in another municipality cannot be created.

**4. How is JSM different from Facebook groups or a traditional job board?**
Three differences. First, structure: every post has municipality, barangay, category, employment type, salary, skills and requirements as separate fields, so it can be filtered and scored. Second, transparency: the match percentage comes with a per-criterion explanation. Third, accountability: employer verification, approval before publication, reports, rule-based flagging and an audit trail — none of which a group post or a generic board gives a local user.

**5. Did you conduct a survey or needs assessment?**
We did not run a formal survey, and we do not claim statistics about local unemployment or user satisfaction. The problem statement comes from the documented situation of informal hiring in these municipalities; the design decisions come from the requirements of the project phases. Any figure about adoption or satisfaction would need a real study, which is future work.

## B. Objectives and scope

**6. What is your general objective, in one sentence?**
To build a web-based job and skills matching system for Basud, Daet, Vinzons and Labo that connects local seekers and employers through structured profiles, geographic search and a transparent match score, with moderation to keep listings credible.

**7. What did you deliberately not build?**
Other municipalities, payroll and attendance, chat between users, online payments, AI or resume parsing, native apps, and email/SMS delivery. Each is listed on the scope slide; excluding them kept the system deliverable and verifiable.

**8. Is every barangay covered?**
Covered as *reference data*, yes: 125 barangays of the four municipalities, from the PSA PSGC list with boundary polygons. Covered with *jobs*, no — a barangay shows vacancies only when an employer pinned a workplace there. We are explicit about that distinction in the documentation and on the map itself.

**9. Does your production database contain every employer and job in these towns?**
No, and we do not claim it does. The deployment contains the accounts and postings created through the system, including training data. Full coverage would require adoption by local employers, which is a deployment programme, not a software feature.

## C. Matching

**10. Why deterministic matching instead of AI?**
Three reasons. Explainability: an applicant can be shown exactly why they got 77 %, and can act on it. Consistency: one engine is used by the seeker list, the employer ranking and the admin statistics, so the numbers cannot disagree. Feasibility: training a model needs labelled hiring outcomes we do not have, and a small local dataset would produce a model that looks sophisticated but is not trustworthy. Deterministic scoring is honest about what it knows.

**11. How are the percentages calculated?**
Five weighted criteria: skills 40, location 25, employment type 15, experience 10, salary 10. Each criterion produces a 0–100 score and earns its weight proportionally. The total is earned points divided by applicable weight, multiplied by 100. If a criterion cannot be judged — no salary published, no employment preference set — it is removed from the denominator, so missing data neither penalises nor rewards.

**12. How are missing skills handled?**
Required skills are split from the employer's text, normalised and compared with the seeker's skill set. The score is matched divided by required. If the employer listed no skills, the criterion is unavailable and leaves the score. If the seeker listed none, they score zero on skills and the interface says "Your profile lists no skills yet — N required skills are missing". The matched and missing lists are also returned separately, and they drive the skills-gap module.

**13. How does location matching work?**
Administrative geography, not road distance. Same barangay scores 100, same municipality 80, a municipality that shares a land border 50, anywhere else 10. The adjacency table is in the code: Basud borders Daet; Daet borders Basud, Vinzons and Labo; Vinzons borders Daet and Labo; Labo borders Daet, Vinzons and Basud. Distance in kilometres is used as a search filter and on the map, not as a score component.

**14. Why those weights?**
They reflect what decides a local hire. Skills are the main rejection reason and the only criterion both sides describe objectively, so 40. Commute feasibility decides whether an offer is usable in a rural municipality, so 25. Employment type is a hard practical constraint, 15. Experience filters but rarely decides at entry level, 10. Many local posts are "negotiable", so salary cannot dominate, 10.

**15. Can the weights be changed?**
Yes — they are constants in `MatchingEngine`, in one file. Changing them changes every surface at once, which is the point of having one engine. We did not expose them as settings because a per-deployment weight would make two users' percentages incomparable.

**16. What are the weaknesses of your matching?**
Four, stated openly. Free-text inputs are parsed, not verified: "three years" in words counts as one year. Synonyms are not expanded: "Cashiering" and "Cashier" are different strings. Distance is coarse — same municipality gets 25 % even for a 20 km barangay. And it cannot judge soft skills, portfolio quality or interview performance. It is a compatibility indicator, not a hiring decision.

## D. Employer verification and moderation

**17. How are employers verified?**
An employer submits a verification request from the company profile. The administrator reviews the profile, then verifies, rejects with a reason, or requests information with a message. The status is one of: unverified, pending, verified, rejected, needs information. Only the administrator can change it — the employer side has no route that sets a verification status, which is covered by a test. We should be clear that this is a review of the submitted information, not a background check of business permits.

**18. How are job postings approved?**
A new post is created as `PENDING_REVIEW` and is invisible to seekers. The administrator approves, rejects with a reason, or requests changes with a reason. Any employer edit to an approved post returns it to review, so what the public sees is always a version an administrator approved. Every decision notifies the employer and writes an audit entry.

**19. How are suspicious job listings handled?**
Six rules: duplicate title, near-duplicate description, same title and location, missing required information, repeated rejected content, and wording asking applicants for money. A rule creates a flag with a human-readable reason; one open flag per rule per post is allowed, and re-running detection cannot duplicate an open flag. The administrator dismisses legitimate ones with a reason. Detection never removes a post automatically — moderation stays a human decision.

**20. What happens when someone reports a post?**
The report records the target, a category (scam, misleading, fake employer, duplicate, wrong location, inappropriate content, other) and a description. The reporter's identity is taken from the token, never from the request body, and is visible only to administrators. Reports move open → under review → resolved or dismissed, with notes, and the reporter is notified of the outcome.

## E. Data and privacy

**21. How is personal data protected?**
Passwords are BCrypt hashes and are never returned. Access is role-based at URL and method level, and ownership is checked in the services: an employer can only see applicants, analytics and skill analyses for its own posts, and a seeker only their own applications — a foreign record answers 403, which does not confirm it exists. The reporter's identity is never shown to the employer. Uploads are stored in the database and served by filename, capped at 5 MB. Responses carry no-store caching, and the browser gets frame, sniffing and referrer restrictions.

**22. Where is personal information stored, and who can see it?**
In PostgreSQL, in the application's own schema, hosted on Supabase. Within the system: the account owner, plus administrators where moderation requires it (reports, verification, audit). Applicants' contact details go only to the employer that owns the post. We do not sell, export or share data with third parties, and there is no analytics or advertising service.

**23. How do you prevent duplicate applications?**
Two layers: the service checks for an existing application before inserting, and the database has a unique constraint on (job seeker, job post). A second attempt is refused, and the existing application is untouched.

**24. What happens to a suspended user's data?**
Suspension sets an active flag to false. Nothing is deleted: applications, history, posts and audit entries remain, and restoring the account brings everything back. It is reversible by design, because deleting a user would destroy the other party's hiring record.

## F. Security

**25. How is administrator access secured?**
Two ways in: the administrator's email and password, or the `admin` / `admin++` shortcut, which now requires a deployment access code compared in constant time. If that variable is not set, the shortcut is refused entirely — there is no default code in the repository. During our Phase 7 audit we found this shortcut accepted an empty password, which was a critical takeover hole; that is the fix. The seeded password in the first migration is a public placeholder and must be rotated by the operator.

**26. How do you prevent someone from calling the admin API directly?**
Every administrator route requires the ADMIN role at two levels: the URL rules in the security configuration and the method-level annotation on the controller class. Anonymous calls get 401, a seeker or employer token gets 403. We test it from the outside: the security suite confirms 401 for anonymous and 403 for the wrong role on every admin route it covers.

**27. What attacks did your audit find, and what did you fix?**
The critical one was the password-free administrator login. High: a suspended user's token kept working, because the filter only checked the signature, not the account state. High: cross-tenant analytics reads returned 500 instead of 403, and several client errors returned 500. Medium: unbounded page size, missing hardening headers, a health endpoint that always said "UP", and CSV formula injection in exports. All fixed and re-tested. Known remaining risks are listed on the security slide — tokens in localStorage, no CSP, no refresh token, public Swagger, rate limiting per address.

**28. Is the system protected against brute force?**
Yes, at the network level: a persistent database-backed limiter allows a configurable number of sign-in attempts per client address per minute — twenty by default — then answers 429 with a Retry-After. Registration, report submission and applications are limited too. The limitation is that it counts per address, not per account, so a distributed attack could spread across addresses; per-account throttling is our next step. The limiter fails open on a database error so nobody is locked out by an outage.

## G. Technical and operational

**29. How do notifications work?**
Each event writes a notification row for the recipient with a type, title, message, link and a dedupe key; a unique index on (user, dedupe key) makes it impossible to notify the same user twice for the same event. The bell polls the unread count, and the inbox supports read, read-all and delete. There is no email or SMS — notifications are in-app, which we state as a limitation.

**30. What happens if the internet connection is lost?**
The pages are ordinary HTML, so they stay open, but no action completes: the client shows an error and nothing is saved. Nothing is queued for later, because a queue without a server acknowledgement could silently drop an application — we prefer an explicit failure. Reconnecting and repeating the action works; the duplicate-application constraint prevents a double submission.

**31. How would you expand the system?**
Horizontally: more municipalities, which is one enum plus reference GeoJSON, then a proper administrative scope per account. Vertically: SMS or email notifications, a password-reset flow, an employer messaging thread, and outcome data that could justify a learned ranking model. Operationally: server-side pagination, full-text search indexing, automated backups with a rehearsed restore, and continuous dependency scanning. None of these changes the architecture; the layered design and single matching engine are there to make them additive.

**32. How do you know the system actually works?**
Three kinds of evidence, and I will distinguish them. Fifty-eight automated tests in the Maven suite, including a full-context test that applies every migration and validates the schema — those run without a browser. Scripted HTTP suites against a running instance: fifty security and API checks, and one hundred twenty-eight moderation checks. And browser checks with real sessions: eight role pages with real data, five widths with no horizontal overflow. What we did **not** do is a penetration test, a load test or a user-acceptance study, and the production-only workflows — signing in as administrator with the real access code, and performing one moderation cycle — remain for the operator because they need production credentials. The release checklist marks them as pending rather than passing.

---

## Handling questions you cannot answer

Say: "That was not measured in this project", then state what *would* be needed. Examples:

- "How many users do you have?" → "The deployment contains training accounts and the accounts created during testing; we have not run an adoption study, so we do not quote a user count."
- "How much faster is it than a job board?" → "We measured local response times on a small dataset for our own sanity checks, but we did not benchmark against another platform, so we make no comparison claim."
- "Does it reduce unemployment?" → "That would require a longitudinal study with local employers. What the system provides is the mechanism: structured vacancies, explained matches, tracked applications and moderated listings."
