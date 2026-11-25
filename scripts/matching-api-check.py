"""
End-to-end checks for the matching engine against a running JSM backend.

Usage:  python3 scripts/matching-api-check.py [base-url]
Default base url: http://127.0.0.1:3000/api

It logs in as the seeded accounts, verifies recommendation ordering, band
filters, pagination, score normalization, employer applicant matching, admin
analytics and role-based access, then removes the temporary job it created.
"""
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:3000/api").rstrip("/")
results = []


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, data) as response:
            raw = response.read().decode()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            return error.code, json.loads(raw)
        except Exception:
            return error.code, raw


def check(name, condition, detail=""):
    results.append((name, bool(condition), detail))
    print(("PASS " if condition else "FAIL ") + name + (" | " + str(detail) if detail else ""))


def login(email, password):
    status, data = call("POST", "/auth/login", body={"email": email, "password": password})
    assert status == 200, (status, data)
    return data["token"]


seeker_token = login("seeker@test.com", "jobseeker123")
employer_token = login("employer@test.com", "employer123")
admin_status, admin_data = call("POST", "/auth/login", body={"email": "admin", "password": ""})
admin_token = admin_data["token"] if admin_status == 200 else None
check("admin login", admin_status == 200, admin_status)

status, profile = call("GET", "/job-seekers/profile", seeker_token)
print("SEEKER PROFILE:", json.dumps({k: profile.get(k) for k in (
    "skills", "barangay", "municipality", "workExperience", "employmentTypePreference",
    "expectedSalaryMin", "expectedSalaryMax", "educationLevel")}))

# --- recommendations -------------------------------------------------------
status, rec = call("GET", "/job-seekers/recommended?page=0&size=5", seeker_token)
check("recommended 200", status == 200, status)
items = rec["content"]
print("RECOMMENDED:", [(j["id"], j["jobTitle"], j["matchPercentage"], j["matchClassification"],
                        len(j["matchedSkills"]), len(j["missingSkills"])) for j in items])
scores = [j["matchPercentage"] for j in items]
check("recommended sorted high to low", all(scores[i] >= scores[i + 1] for i in range(len(scores) - 1)), scores)
check("classification present", all(j["matchClassification"] for j in items))
check("all five criteria returned", all(len(j["matchCriteria"]) == 5 for j in items))

if items:
    job = items[0]
    applicable = sum(c["weight"] for c in job["matchCriteria"] if c["applicable"])
    earned = sum(c["earnedPoints"] for c in job["matchCriteria"] if c["applicable"])
    expected = round(earned / applicable * 100, 1)
    check("percentage equals earned/applicable", abs(expected - job["matchPercentage"]) <= 0.15,
          "%s/%s=%s vs %s" % (earned, applicable, expected, job["matchPercentage"]))

# --- match band filter -----------------------------------------------------
status, strong = call("GET", "/job-seekers/recommended?page=0&size=50&matchBand=strong", seeker_token)
check("strong band 200", status == 200, status)
check("strong band only >= 75", all(x["matchPercentage"] >= 75 for x in strong["content"]),
      [x["matchPercentage"] for x in strong["content"]])
status, excellent = call("GET", "/job-seekers/recommended?page=0&size=50&matchBand=excellent", seeker_token)
check("excellent band only >= 90", all(x["matchPercentage"] >= 90 for x in excellent["content"]),
      [x["matchPercentage"] for x in excellent["content"]])

# --- pagination ------------------------------------------------------------
status, page_one = call("GET", "/job-seekers/recommended?page=0&size=2", seeker_token)
status, page_two = call("GET", "/job-seekers/recommended?page=1&size=2", seeker_token)
check("pagination size respected", len(page_one["content"]) <= 2 and len(page_two["content"]) <= 2,
      (len(page_one["content"]), len(page_two["content"])))
check("pagination totals equal", page_one["totalElements"] == page_two["totalElements"],
      (page_one["totalElements"], page_two["totalElements"]))
check("pages do not overlap",
      not ({j["id"] for j in page_one["content"]} & {j["id"] for j in page_two["content"]}))

# --- profile completeness --------------------------------------------------
status, completeness = call("GET", "/job-seekers/profile/completeness", seeker_token)
check("profile completeness 200", status == 200, status)
print("COMPLETENESS:", completeness.get("percentage"), [i["key"] for i in completeness.get("missing", [])])
check("completeness lists missing fields", isinstance(completeness.get("missing"), list))

# --- employer job with real requirements -----------------------------------
job_payload = {
    "jobTitle": "MATCH TEST — IT Support Staff",
    "jobCategory": "IT_TECHNOLOGY",
    "jobDescription": "Temporary job created by the matching API check.",
    "responsibilities": "Support staff and keep systems running.",
    "requiredSkills": "Computer Troubleshooting, Microsoft Office, Customer Service, Networking",
    "educationRequirement": "COLLEGE_GRADUATE",
    "experienceRequirement": "1-2 years",
    "employmentType": "FULL_TIME",
    "workSchedule": "MORNING",
    "salaryMin": 15000,
    "salaryMax": 20000,
    "municipality": "DAET",
    "barangay": "Alawihao",
    "availablePositions": 1,
}
status, created = call("POST", "/employers/jobs", employer_token, job_payload)
check("employer job creation still works", status == 201, status)
job_id = created["id"] if status == 201 else None

try:
    if job_id:
        status, detail = call("GET", "/job-seekers/jobs/%s/match" % job_id, seeker_token)
        check("match details 200", status == 200, status)
        print("MATCH DETAIL:", detail["overallMatch"], detail["classification"],
              [(c["key"], c["earnedPoints"], c["weight"], c["status"]) for c in detail["criteria"]])
        check("match details include skill lists",
              isinstance(detail["matchedSkills"], list) and isinstance(detail["missingSkills"], list))
        applicable = sum(c["weight"] for c in detail["criteria"] if c["applicable"])
        earned = sum(c["earnedPoints"] for c in detail["criteria"] if c["applicable"])
        check("match details normalized",
              abs(round(earned / applicable * 100, 1) - detail["overallMatch"]) <= 0.15,
              (earned, applicable, detail["overallMatch"]))

        status, listing = call("GET", "/job-seekers/jobs?page=0&size=20&skills=Computer", seeker_token)
        check("existing job search still works",
              status == 200 and any(j["id"] == job_id for j in listing["content"]), status)
        status, map_data = call("GET", "/job-seekers/jobs/map?page=0&size=50", seeker_token)
        check("existing map view still works",
              status == 200 and "markers" in map_data,
              (status, len(map_data.get("markers", [])) if isinstance(map_data, dict) else None))
        status, _ = call("GET", "/job-seekers/jobs?nearLatitude=14.1128&nearLongitude=122.9553&radiusKm=25&size=10",
                         seeker_token)
        check("existing Near Me still works", status == 200, status)

        status, applied = call("POST", "/job-seekers/apply?jobId=%s" % job_id, seeker_token)
        check("application submission still works", status == 201, (status, applied))

        status, applicants = call("GET", "/employers/applicants?jobId=%s&sortBy=match" % job_id, employer_token)
        check("employer applicants 200", status == 200, status)
        if status == 200 and applicants:
            applicant = applicants[0]
            print("APPLICANT:", applicant["fullName"], applicant["matchPercentage"],
                  applicant["matchClassification"], applicant["matchedSkills"], applicant["missingSkills"])
            check("applicant has a match score", isinstance(applicant["matchPercentage"], (int, float)))
            check("applicant has matched skills", isinstance(applicant["matchedSkills"], list))
            check("applicant score equals job match",
                  abs(applicant["matchPercentage"] - detail["overallMatch"]) <= 0.15,
                  (applicant["matchPercentage"], detail["overallMatch"]))
        else:
            check("employer applicants non-empty", False, applicants)

    # --- authorization -----------------------------------------------------
    status, _ = call("GET", "/admin/matching/stats", seeker_token)
    check("seeker blocked from admin matching stats", status == 403, status)
    status, _ = call("GET", "/employers/applicants?jobId=%s" % (job_id or 1), seeker_token)
    check("seeker blocked from employer applicants", status == 403, status)
    status, _ = call("GET", "/job-seekers/recommended", None)
    check("anonymous blocked from recommendations", status in (401, 403), status)

    # --- admin analytics ---------------------------------------------------
    if admin_token:
        status, stats = call("GET", "/admin/matching/stats", admin_token)
        check("admin matching stats 200", status == 200, status)
        print("ADMIN STATS:", json.dumps({k: stats.get(k) for k in (
            "activeJobs", "jobsWithRequiredSkills", "jobSeekers", "jobSeekersWithCompleteProfiles",
            "averageMatchScore", "averageApplicableWeight", "pairsEvaluated")}))
        print("DISTRIBUTION:", [(b["label"], b["count"]) for b in stats["distribution"]])
        print("TOP MATCHED SKILLS:", [(s["skill"], s["count"]) for s in stats["mostMatchedSkills"][:5]])
        print("TOP MISSING SKILLS:", [(s["skill"], s["count"]) for s in stats["mostMissingSkills"][:5]])
        print("TOP JOBS:", [(j["jobTitle"], j["strongMatches"], j["averageScore"]) for j in stats["topJobs"]])
        check("distribution counts every pair",
              sum(b["count"] for b in stats["distribution"]) == stats["pairsEvaluated"],
              (sum(b["count"] for b in stats["distribution"]), stats["pairsEvaluated"]))
        status, filtered = call("GET", "/admin/matching/stats?municipality=DAET&employmentType=FULL_TIME", admin_token)
        check("admin matching filters 200", status == 200, status)
        print("ADMIN FILTERED:", filtered.get("activeJobs"), filtered.get("pairsEvaluated"),
              filtered.get("averageMatchScore"))
    else:
        check("admin token available", False)
finally:
    if job_id:
        status, deleted = call("DELETE", "/employers/jobs/%s" % job_id, employer_token)
        check("cleanup: temporary job deleted", status == 200, (status, deleted))

print("\nSUMMARY: %d passed, %d failed" % (
    sum(1 for _, ok, _ in results if ok), sum(1 for _, ok, _ in results if not ok)))
for name, ok, detail in results:
    if not ok:
        print("  FAILED:", name, detail)
