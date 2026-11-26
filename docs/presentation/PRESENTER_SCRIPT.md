# Presenter Script — JSM Final Defense

A spoken script for the 16-slide deck (`JSM_Final_Defense.pptx`). Each section names the slide, gives the words to say and a timing. Total ≈ 14–16 minutes, leaving time for questions.

**Before you start:** open the deck, the live site (`https://enuvex.onrender.com`) and the live-demo guide. If the rendering machine lacks Poppins and Inter, PowerPoint substitutes a similar sans-serif — the layout still holds.

---

## Slide 1 — Title (30 s)

> Good morning/afternoon, members of the panel. We are ENUVEX, and our project is the **Job and Skills Matching system — JSM**.
>
> JSM is a working web platform that connects job seekers and employers in four municipalities of Camarines Norte: Basud, Daet, Vinzons and Labo. It is deployed right now at enuvex.onrender.com, and it is developed to the end of our eighth and final phase.
>
> In the next fifteen minutes I will explain the problem, show the system, walk through the matching logic, and be straightforward about what it cannot do.

## Slide 2 — Background (1 min 15 s)

> These four municipalities are not a corporate job market. Most employers are small businesses — retail shops, eateries, offices, construction crews, transport operators, farms and fishing enterprises. They do not have a human-resources department.
>
> So hiring happens informally: a tarpaulin on a post, a Facebook post, a friend's referral, a walk-in applicant.
>
> That works for one vacancy and one applicant. It does not work at scale. A job seeker only sees what they happen to hear about. An employer receives applications from people whose skills are wrong, or who live two towns away. And nobody — not the seeker, not the employer, not the local government — can say afterwards what happened.
>
> On the right are the three consequences we designed against: no shared channel, no way to compare fit, and no accountability.

## Slide 3 — Problem statement (1 min)

> We broke that down into six specific problems.
>
> Vacancies are fragmented, so posts disappear and employers re-advertise. Skills are never compared before an application is sent, so both sides waste time. Location is ambiguous — in a rural municipality, the barangay decides whether a job is actually reachable. There is no tracking, so an application has no status, no timeline and no interview record. There is no accountability, so fake or duplicated posts cannot be challenged. And employers get no insight into where their applicants come from or where they drop off.
>
> Each of those six became a module. That is why the system has the shape it has.

## Slide 4 — Objectives (1 min)

> Our general objective was to build a web-based job and skills matching system for these four municipalities that connects local seekers with local employers through structured profiles, geographic search and a transparent match score.
>
> From that we derived eight specific objectives, and every one is delivered: role-based accounts, structured profiles, geographic search, a deterministic and explained match, application tracking with interviews, skills-gap guidance, employer analytics with export, and moderation with an audit trail.
>
> I want to be precise about the word "transparent". The score does not just appear — the system shows the five criteria and what each one contributed.

## Slide 5 — Scope (1 min 15 s)

> Scope is geographic and functional.
>
> Geographically the system is limited — technically limited, by an enumeration in the code — to four municipalities. Basud has 29 barangays in the reference data, Daet 25, Labo 52, and Vinzons 19: 125 in total, taken from the PSA PSGC list with boundary polygons.
>
> Here is the honest qualification. Being listed means the barangay exists in the coverage area. It does **not** mean it contains a vacancy. A barangay shows jobs only when an employer has pinned a workplace there. And we do not claim that our deployment contains every employer or every vacancy in these towns — it contains the accounts and posts created through the system.
>
> Functionally, on the right, is what we deliberately excluded: other municipalities, payroll, chat, payments, AI, resume parsing, native apps and email delivery. Excluding them is a design decision, not an omission.

## Slide 6 — Proposed solution (1 min 15 s)

> The solution is one platform with three workspaces and one database.
>
> The job seeker records a structured profile, searches by place, category, salary, distance and match band, uses a real map, reads the match and its explanation, applies, and follows the application with its timeline and interviews. The skills module then tells them which local skills they are missing.
>
> The employer records a business profile, requests verification, posts a vacancy that is approved before publication, reviews applicants ranked by match, moves them through the pipeline, schedules interviews, and reads analytics on their own postings.
>
> The administrator keeps the listing credible: verification, approvals, reports, suspicious-listing review, suspension and an audit trail.
>
> All three workspaces are one deployed application. There is no separate frontend server and no build step for the interface.

## Slide 7 — Architecture (1 min 30 s)

> Technically it is one Spring Boot 3.4 application on Java 21.
>
> The browser receives static HTML and vanilla JavaScript. Requests carry a JSON Web Token and pass, in this order, a rate limiter and a JWT filter before reaching controllers, services and repositories.
>
> The database is PostgreSQL, holding seventeen tables in their own schema, created by nine Flyway migrations. Hibernate runs in validate mode, so if an entity and the schema disagree, the application refuses to start instead of silently altering a table.
>
> Hosting is Render, which builds the Dockerfile — a Maven stage that produces the jar, then a JRE runtime — and Supabase provides PostgreSQL. The health endpoint performs a real database query, not a constant "UP".
>
> Four numbers worth remembering: Java 21, seventeen tables, thirty-three pages, and one-hour JWT with BCrypt hashing. And to be clear about what is *not* in the stack: no Node, no React, no Redis, no queue, no AI service and no paid third-party API.

## Slide 8 — Modules (45 s)

> Eight phases, eight module groups, all implemented. Phases one to six are the user-facing features you have seen. Phase seven was a full security and quality audit — it found real defects, and I will show what was fixed. Phase eight is this release: the documentation set, this presentation, and one real security addition, persistent rate limiting.

## Slide 9 — Geographic mapping (1 min)

> This is the actual job map, captured from the running system. On the left are the four municipalities with their PSGC codes, barangay counts and the number of active jobs. The base map draws real boundaries from the PSGC-aligned GeoJSON.
>
> Markers appear only for posts whose employer confirmed a workplace location. In this training database many posts have no pin — and notice what the system does: it says "no job has a confirmed location for these filters" and offers the list view. It does not invent a location to make the map look full.
>
> "Near me" asks the browser for a position once, uses it to compute distance for that search, and never stores or shares it.

## Slide 10 — Matching engine (1 min 45 s)

> This is the part panels usually question, so let me be exact.
>
> The engine is a deterministic weighted score: skills forty, location twenty-five, employment type fifteen, experience ten, salary ten. Each criterion produces a score from zero to one hundred and earns its weight proportionally.
>
> The overall percentage is earned points divided by the **applicable** weight. That detail matters: when a criterion cannot be judged — the employer published no salary, or the seeker set no employment preference — it leaves the denominator. So missing data never counts as a mismatch, and it never hands out free points.
>
> Location is tiered by administrative geography, because that is the data both sides actually provide: same barangay one hundred, same municipality eighty, bordering municipality fifty, outside the area ten.
>
> Bands are: excellent at ninety and above, strong from seventy-five, good from sixty, partial from forty, low below that.
>
> And I will say plainly: this is **not** artificial intelligence. There is no model, no training data and no inference. It is arithmetic over what users entered — which is exactly why it can be explained to the person who receives the score, and why the seeker's list, the employer's ranking and the administrator's statistics always agree.

## Slide 11 — Tracking and skills guidance (1 min)

> Applications move through seven statuses with a full history. Every change records who did it, when, and an optional message, and notifies the other party. Interviews have their own record — date, type, location or meeting link, instructions — with reschedule and cancel.
>
> On the right is the skills module: which skills the local jobs demand, which ones the applicant is missing, what a what-if simulation would do to their scores, and curated learning resources maintained by the administrator.

## Slide 12 — Employer analytics (1 min)

> Employers get analytics from their own data only: overview, applicant funnel, response metrics, match distribution, per-post performance, applicant geography and plain-language insights, with a seven, thirty or ninety day filter and a CSV export.
>
> The CSV is built in the browser from the same data, and cells that begin with an equals sign are neutralised so a spreadsheet cannot execute them as a formula.
>
> The numbers in this screenshot come from our training database, so they are small — twenty-one posts, one applicant. They are real computations, not mock-ups, and I am not presenting them as performance results.

## Slide 13 — Administrator moderation (1 min 15 s)

> Phase six is the control layer.
>
> Employers request verification; the administrator verifies, rejects or asks for information, with a required reason that reaches the employer. Every post is approved before publication, and — this is deliberate — any employer edit sends an approved post back to review, so the public version is always one an administrator saw.
>
> Users can report posts, employers or users; reports move from open to under review, then resolved or dismissed, and the reporter's identity is visible only to administrators. Six rules flag suspicious listings — duplicates, missing information, repeated rejections, payment requests — and a human always decides.
>
> Suspension deserves a specific mention: it does not just block the next login. The account state is re-read on every request, so a token issued before the suspension stops working immediately. That behaviour is covered by a test.
>
> And every decision writes an append-only audit entry with the previous state, the new state and the reason. There is no route that edits or deletes an audit entry.

## Slide 14 — Security and testing (1 min 30 s)

> Phase seven was an audit, not a rewrite, and it found a critical defect: administrator sign-in accepted a known name with an empty password. Anyone who had read the demo instructions could become administrator. That is closed — the shortcut now requires a deployment access code, compared in constant time, and it is disabled entirely when that code is not configured.
>
> The same audit found that a suspended user's existing session kept working, that page size was unbounded, that several request errors returned five hundred instead of four hundred or four-oh-three, and that the employer analytics console had been broken since its endpoints changed. All fixed.
>
> Phase eight adds persistent rate limiting: counters in the database, so a restart does not reset them, applied to sign-in, registration, report submission and applications. Limits are configurable, and the limiter fails open, so a database problem can never lock a legitimate user out.
>
> Fifty-eight automated tests pass, plus a fifty-check security suite and one hundred twenty-eight moderation checks.
>
> At the bottom I list what is still imperfect: tokens in localStorage, no Content-Security-Policy, no refresh token, three admin lists that paginate in the browser, public Swagger, and rate limiting per address rather than per account. We also claim no penetration test, no user-acceptance study and no performance benchmark.

## Slide 15 — Demonstration sequence (45 s)

> If the panel allows, the demonstration follows one story: a seeker finds a job that fits and applies; the employer reviews the ranked applicants and moves the application forward; the administrator keeps the listing credible. Every step has a documented fallback in case the free hosting tier is waking up or a demo record is missing.

## Slide 16 — Conclusion (45 s)

> To conclude: all eight planned phases are implemented and deployed. The matching is deterministic and explained rather than impressive-sounding. The geographic scope is honest — four municipalities, 125 barangays of reference data, and only what users have actually posted.
>
> The next engineering steps are security hardening — a content security policy, HttpOnly cookies and per-account throttling — plus server-side pagination and full-text search before the data grows. The first *operational* step is rotating the seeded administrator password and keeping the access code in the deployment environment.
>
> Thank you. We welcome your questions.

---

## Short version (8 minutes)

If time is cut, present slides 1, 3, 6, 10, 12, 13, 14, 16 and use the demo for the rest. Skip the architecture detail; say one sentence: "one Spring Boot application, PostgreSQL, JWT, deployed on Render".

## Delivery notes

- Say "deterministic weighted score", never "AI".
- When you mention a number, say where it comes from: "from the training database", "from the automated test suite", "measured locally on this machine".
- If the live site is asleep, say so: "the free hosting tier sleeps when idle; the first request takes a few seconds" — do not pretend it is instant.
- If asked about data you do not have (user counts, satisfaction, hiring outcomes), answer: "we did not conduct that study, so we do not claim a figure".
- Keep the limitations slide visible while answering security questions; it helps the panel trust the rest.
