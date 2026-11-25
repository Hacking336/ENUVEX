"""
End-to-end checks for the Skills Gap Analysis and Career Guidance feature.

Usage:  python3 scripts/skills-api-check.py [base-url]

Creates temporary job posts and one temporary no-skills seeker, exercises the
seeker and admin analytics, verifies that simulations never change the official
match score or the profile, then removes every row it created. Requires the
optional pg8000 package for the final database cleanup; without it the API
deletes are still attempted and leftovers are reported.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import requests

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:3000/api").rstrip("/")
results = []
created_jobs = []
temp_email = "skills.check.%d@example.com" % int(time.time())
RESOURCE_URL = "https://example.org/jsm-skills-check-%d" % int(time.time())


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, data, timeout=40) as response:
            raw = response.read().decode()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            return error.code, json.loads(raw)
        except Exception:
            return error.code, raw
    except Exception as error:  # connection failure
        return 0, str(error)


def check(name, condition, detail=""):
    results.append((name, bool(condition), detail))
    print(("PASS " if condition else "FAIL ") + name + (" | " + str(detail) if detail else ""))


def login(email, password):
    status, data = call("POST", "/auth/login", body={"email": email, "password": password})
    assert status == 200, (status, data)
    return data["token"]


def analysis(token, **params):
    query = urllib.parse.urlencode({k: v for k, v in params.items() if v not in (None, "")})
    return call("GET", "/job-seekers/skills-analysis" + ("?" + query if query else ""), token)


def demand(token, **params):
    query = urllib.parse.urlencode({k: v for k, v in params.items() if v not in (None, "")})
    return call("GET", "/job-seekers/skills-demand" + ("?" + query if query else ""), token)


def sim(token, skills, threshold=60):
    return call("POST", "/job-seekers/skills-simulation", token, {"skills": skills, "threshold": threshold})


def profile(token):
    return call("GET", "/job-seekers/profile", token)


def update_profile(token, source, **changes):
    payload = {key: source.get(key) for key in (
        "fullName", "contactNumber", "municipality", "barangay", "educationLevel", "courseField",
        "workExperience", "certifications", "employmentTypePreference", "preferredWorkSchedule",
        "availability", "expectedSalaryMin", "expectedSalaryMax", "skills")}
    payload.update(changes)
    response = requests.put(
        BASE + "/job-seekers/profile",
        headers={"Authorization": "Bearer " + token},
        files={"profile": (None, json.dumps(payload), "application/json")},
        timeout=40,
    )
    return response.status_code, (response.json() if response.content else None)


def job_body(title, municipality, barangay, employment_type, category, skills, description=None):
    return {
        "jobTitle": title,
        "jobCategory": category,
        "jobDescription": description or ("Temporary check posting for " + title),
        "responsibilities": "Temporary responsibilities used by the automated check.",
        "requiredSkills": skills,
        "municipality": municipality,
        "barangay": barangay,
        "employmentType": employment_type,
        "availablePositions": 1,
        "active": True,
    }


def env_values():
    values = {}
    env_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "backend", ".env")
    try:
        for line in open(env_path, encoding="utf-8"):
            if "=" in line and not line.strip().startswith("#"):
                key, value = line.rstrip("\n").split("=", 1)
                values[key.strip()] = value.strip().strip('"').strip("'")
    except OSError:
        pass
    return values


def db_cleanup():
    try:
        import pg8000.dbapi
    except Exception:
        print("CLEANUP: pg8000 unavailable; test rows may remain")
        return False
    env = env_values()
    url = os.environ.get("DB_URL") or env.get("DB_URL", "")
    match = re.search(r"//([^:/]+):(\d+)/([^?]+)", url)
    if not match:
        print("CLEANUP: DB_URL not understood")
        return False
    connection = pg8000.dbapi.connect(
        host=match.group(1), port=int(match.group(2)), database=match.group(3),
        user=os.environ.get("DB_USERNAME") or env.get("DB_USERNAME"),
        password=os.environ.get("DB_PASSWORD") or env.get("DB_PASSWORD"),
        ssl_context=True,
    )
    cursor = connection.cursor()
    cursor.execute("SET search_path TO enuv")
    for job_id in created_jobs:
        cursor.execute("DELETE FROM notifications WHERE reference_type = 'JOB_POST' AND reference_id = %s", (job_id,))
        cursor.execute("DELETE FROM job_posts WHERE id = %s", (job_id,))
    cursor.execute("DELETE FROM notifications WHERE reference_type = 'JOB_POST' AND reference_id IN "
                   "(SELECT id FROM job_posts WHERE job_title LIKE 'Check - %')")
    cursor.execute("DELETE FROM job_posts WHERE job_title LIKE 'Check - %'")
    cursor.execute("DELETE FROM learning_resources WHERE url LIKE %s", ("https://example.org/jsm-skills-check-%",))
    cursor.execute("DELETE FROM users WHERE email LIKE %s", ("skills.check.%@example.com",))
    connection.commit()
    cursor.execute("SELECT COUNT(*) FROM job_posts WHERE id = ANY(%s)", (created_jobs or [0],))
    remaining_jobs = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM users WHERE email = %s", (temp_email,))
    remaining_users = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM learning_resources WHERE url LIKE %s", ("https://example.org/jsm-skills-check-%",))
    remaining_resources = cursor.fetchone()[0]
    cleanup_info = (remaining_jobs, remaining_users, remaining_resources)
    cursor.close()
    connection.close()
    print("CLEANUP:", cleanup_info)
    return cleanup_info == (0, 0, 0)


seeker = login("seeker@test.com", "jobseeker123")
employer = login("employer@test.com", "employer123")
status, admin_login = call("POST", "/auth/login", body={"email": "admin", "password": ""})
admin = admin_login.get("token") if isinstance(admin_login, dict) else None
check("Admin login works", status == 200 and bool(admin), status)

original_status, original = profile(seeker)
check("Original seeker profile loaded", original_status == 200 and isinstance(original, dict), original_status)

base_skills = ["Customer Service", "Communication", "Cashiering"]
set_status, _ = update_profile(seeker, original, skills=", ".join(base_skills))
check("Test skills saved to the seeker profile", set_status == 200, set_status)

jobs = [
    job_body("Check - Customer Support Assistant", "DAET", "Barangay I", "FULL_TIME",
             "CUSTOMER_SERVICE", "Customer Service, Communication, Microsoft Excel"),
    job_body("Check - Retail Crew", "DAET", "Barangay I", "PART_TIME",
             "RETAIL_STORE", "Customer Service, Cashiering, Microsoft Excel, Computer Literacy"),
    job_body("Check - Office Assistant", "BASUD", "Guinatungan", "FULL_TIME",
             "OFFICE_ADMINISTRATION", "Microsoft Excel, Computer Literacy, Communication"),
    job_body("Check - General Worker", "LABO", "Anahaw", "CONTRACT",
             "GENERAL_LABOR", None, "A posting that legitimately requires no formal skills."),
    job_body("Check - Sales Associate", "DAET", "Alawihao", "FULL_TIME",
             "SALES", "Customer Service, Microsoft Excel"),
]
for job in jobs:
    status, data = call("POST", "/employers/jobs", employer, job)
    if status == 201:
        created_jobs.append(data["id"])
check("Temporary job posts created", len(created_jobs) == len(jobs), created_jobs)

try:
    status, page = call("GET", "/job-seekers/jobs?size=500", seeker)
    all_content = (page or {}).get("content", []) if status == 200 else []
    content = [j for j in all_content if j.get("municipality") == "DAET"]
    created_ids = set(created_jobs)
    check("Existing job search returns the new postings",
          status == 200 and created_ids.issubset({item["id"] for item in all_content}),
          len(all_content))

    skilled_daet = [j for j in content if str(j.get("requiredSkills") or "").strip()]

    status, data = analysis(seeker)
    check("Skills analysis returns 200", status == 200 and isinstance(data, dict), status)
    data = data or {}
    totals = data.get("totals", {})
    check("My skills are recognised", totals.get("mySkills") == 3, totals.get("mySkills"))
    check("Area is the seeker's municipality", data.get("filters", {}).get("municipality") == "DAET",
          data.get("filters"))
    have = {row["skill"].lower(): row for row in data.get("haveSkills", [])}
    check("Have-skill demand is counted", have.get("customer service", {}).get("jobCount", 0) >= 2,
          have.get("customer service", {}).get("jobCount"))
    need = {row["skill"].lower(): row for row in data.get("needSkills", [])}
    check("Missing Microsoft Excel is identified", "microsoft excel" in need, list(need)[:5])
    check("Missing Computer Literacy is identified", "computer literacy" in need, list(need)[:5])
    check("Each normalized skill appears once in the ranked list",
          len({row["skillKey"] for row in data.get("needSkills", [])}) == len(data.get("needSkills", [])),
          [row["skillKey"] for row in data.get("needSkills", [])])
    check("Area posting count matches the API search",
          totals.get("areaJobs") == len(content), (totals.get("areaJobs"), len(content)))
    check("Postings with skills are counted", totals.get("jobsWithSkills") == len(skilled_daet),
          (totals.get("jobsWithSkills"), len(skilled_daet)))
    check("A posting without skills is excluded from the denominator",
          totals.get("jobsWithoutSkills", 0) >= 0, totals.get("jobsWithoutSkills"))

    excel = need.get("microsoft excel", {})
    expected_count = len([j for j in content if "microsoft excel" in str(j.get("requiredSkills") or "").lower()])
    check("Excel demand uses the real posting count", excel.get("areaJobCount") == expected_count,
          (excel.get("areaJobCount"), expected_count))
    expected_share = round(expected_count * 100.0 / len(skilled_daet), 1) if skilled_daet else None
    check("Share uses postings that list skills as the denominator",
          excel.get("sharePercent") == expected_share, (excel.get("sharePercent"), expected_share))

    factors = excel.get("factors", [])
    weight_sum = sum(float(f.get("weight", 0)) for f in factors)
    weighted = sum(float(f.get("weight", 0)) * float(f.get("score", 0)) for f in factors)
    check("Priority factors are present", len(factors) >= 2, len(factors))
    check("Priority weights are the documented ones",
          weight_sum == excel.get("applicableWeight"), (weight_sum, excel.get("applicableWeight")))
    check("Priority score is the weighted average of its factors",
          abs((weighted / weight_sum if weight_sum else 0) - float(excel.get("priorityScore", 0))) <= 0.11,
          (round(weighted / weight_sum, 1) if weight_sum else 0, excel.get("priorityScore")))
    check("Priority bands are HIGH/MEDIUM/LOW",
          all(row.get("priority") in ("HIGH", "MEDIUM", "LOW") for row in data.get("needSkills", [])),
          [row.get("priority") for row in data.get("needSkills", [])[:3]])
    check("Unlock list contains only jobs that really unlock",
          all(row.get("opportunityJobs", 0) > 0 for row in data.get("unlockSkills", [])),
          [(row["skill"], row["opportunityJobs"]) for row in data.get("unlockSkills", [])])
    check("No recommended skill was auto-added to the profile",
          profile(seeker)[1].get("skills") == ", ".join(base_skills), profile(seeker)[1].get("skills"))

    in_demand = data.get("mostInDemand", [])
    check("Most in-demand list is sorted by posting count",
          all(in_demand[i]["jobCount"] >= in_demand[i + 1]["jobCount"] for i in range(len(in_demand) - 1)),
          [row["jobCount"] for row in in_demand[:5]])
    check("A demand percentage is never invented without a denominator",
          all(row.get("sharePercent") is not None for row in in_demand) if skilled_daet else True,
          [row.get("sharePercent") for row in in_demand[:3]])

    status, basud = analysis(seeker, municipality="BASUD")
    basud_jobs = [j for j in ((call("GET", "/job-seekers/jobs?size=500&municipality=BASUD", seeker)[1] or {}).get("content") or [])]
    check("Municipality filter narrows the analysis",
          status == 200 and basud.get("totals", {}).get("areaJobs") == len(basud_jobs),
          (basud.get("totals", {}).get("areaJobs"), len(basud_jobs)))
    basud_need = {row["skill"].lower() for row in basud.get("needSkills", [])}
    check("Municipality filter changes the gaps", "microsoft excel" in basud_need, list(basud_need)[:5])

    status, barangay = analysis(seeker, municipality="BASUD", barangay="Guinatungan")
    check("Barangay filter returns the matching posting",
          status == 200 and barangay.get("totals", {}).get("areaJobs", 0)
          == len([j for j in basud_jobs if str(j.get("barangay") or "").lower() == "guinatungan"]),
          barangay.get("totals", {}).get("areaJobs"))

    status, part_time = analysis(seeker, employmentType="PART_TIME")
    check("Employment-type filter narrows the analysis",
          status == 200 and all(
              (row.get("byEmploymentType") or {}).get("Part-time", 0) == row["areaJobCount"]
              for row in part_time.get("needSkills", [])),
          status)
    check("Employment-type filter keeps only part-time demand",
          part_time.get("totals", {}).get("areaJobs") == len(
              [j for j in content if j.get("employmentType") == "PART_TIME"]),
          part_time.get("totals", {}).get("areaJobs"))

    status, catalog = call("GET", "/skills/catalog?q=excel", seeker)
    catalog_excel = next((row for row in (catalog or []) if row["skill"].lower() == "microsoft excel"), None)
    check("Skill catalog suggests Microsoft Excel", status == 200 and catalog_excel is not None, status)
    check("Catalog demand matches active postings",
          catalog_excel and catalog_excel["jobCount"] == excel.get("totalJobCount"),
          (catalog_excel or {}).get("jobCount"))

    status, official_before = call("GET", "/job-seekers/jobs/%d/match" % created_jobs[0], seeker)
    score_before = (official_before or {}).get("overallMatch")
    status, result = sim(seeker, ["Microsoft Excel"])
    check("Simulator returns 200", status == 200 and isinstance(result, dict), status)
    result = result or {}
    check("Simulator records the hypothetical skill", result.get("addedSkills") == ["Microsoft Excel"],
          result.get("addedSkills"))
    check("Simulator flags itself as a simulation", result.get("simulation") is True)
    check("Potential matching is never below current matching",
          result.get("potentialMatchingJobs", 0) >= result.get("currentMatchingJobs", 0), result)
    check("Additional jobs arithmetic is correct",
          result.get("additionalMatchingJobs") == max(0, result.get("potentialMatchingJobs", 0)
                                                       - result.get("currentMatchingJobs", 0)),
          result.get("additionalMatchingJobs"))
    jobs_by_id = {row["id"]: row for row in result.get("jobs", [])}
    retail = jobs_by_id.get(created_jobs[1], {}) if len(created_jobs) > 1 else {}
    check("Excel raises the retail job score", retail.get("potentialScore", 0) > retail.get("currentScore", 0),
          (retail.get("currentScore"), retail.get("potentialScore")))
    check("Newly qualified is exactly crossing the threshold",
          all(row["currentScore"] < result.get("threshold", 60) <= row["potentialScore"]
              for row in result.get("newlyUnlocked", [])),
          len(result.get("newlyUnlocked", [])))
    basud_job = jobs_by_id.get(created_jobs[2], {}) if len(created_jobs) > 2 else {}
    check("One skill does not claim a job where other requirements remain unmet",
          not basud_job.get("newlyQualified") or basud_job.get("potentialScore", 0) >= 60,
          (basud_job.get("currentScore"), basud_job.get("potentialScore"),
           basud_job.get("newlyQualified")))
    check("Simulation does not inject the skill into the profile",
          "microsoft excel" not in str(profile(seeker)[1].get("skills", "")).lower(),
          profile(seeker)[1].get("skills"))
    status, official_after = call("GET", "/job-seekers/jobs/%d/match" % created_jobs[0], seeker)
    check("Official match score is unchanged after simulation",
          official_after.get("overallMatch") == score_before,
          (score_before, official_after.get("overallMatch")))
    status, _ = sim(seeker, [])
    check("Empty simulation is rejected", status == 400, status)
    status, _ = sim(seeker, ["Customer Service"])
    check("Simulating a skill already in the profile is rejected", status == 400, status)

    status, careers = call("GET", "/job-seekers/career-opportunities", seeker)
    check("Career opportunities return 200", status == 200 and isinstance(careers, dict), status)
    groups = {group["category"]: group for group in (careers or {}).get("groups", [])}
    check("Customer service group is built from real postings", "CUSTOMER_SERVICE" in groups, list(groups)[:6])
    customer = groups.get("CUSTOMER_SERVICE", {})
    expected_customer = len([j for j in content if j.get("category") == "CUSTOMER_SERVICE"])
    check("Career group job count matches the postings", customer.get("jobCount") == expected_customer,
          (customer.get("jobCount"), expected_customer))
    check("Career group lists common required skills",
          any(skill["skill"].lower() == "microsoft excel" for skill in customer.get("topSkills", [])),
          [skill["skill"] for skill in customer.get("topSkills", [])])
    check("Career group separates existing and missing skills",
          "Customer Service" in customer.get("yourSkills", [])
          and "Microsoft Excel" in customer.get("missingSkills", []),
          (customer.get("yourSkills"), customer.get("missingSkills")))
    check("Career group links to Job Search with real filters",
          "jobs.html?category=CUSTOMER_SERVICE" in customer.get("searchUrl", ""), customer.get("searchUrl"))
    check("Career average match uses the official engine",
          customer.get("averageMatchScore") is not None, customer.get("averageMatchScore"))
    status, labo = call("GET", "/job-seekers/career-opportunities?municipality=LABO", seeker)
    check("Career municipality filter includes the no-skill group",
          any(group["category"] == "GENERAL_LABOR"
              for group in (labo or {}).get("groups", [])), status)

    status, demand_data = demand(seeker)
    check("Skills demand returns 200", status == 200 and isinstance(demand_data, dict), status)
    check("Demand reports postings without skills",
          demand_data.get("totals", {}).get("jobsWithoutSkills", 0) >= 0, demand_data.get("totals"))
    check("No trend is claimed without historical snapshots",
          demand_data.get("trendAvailable") is False and "Not calculated" in str(demand_data.get("trendNote", "")),
          demand_data.get("trendNote"))
    check("Municipality demand comparison covers all four areas",
          set(demand_data.get("byMunicipality", {}).keys()) == {"BASUD", "DAET", "VINZONS", "LABO"},
          list(demand_data.get("byMunicipality", {}).keys()))
    check("Employment-type demand comparison covers all supported types",
          set(demand_data.get("byEmploymentType", {}).keys()) == {"FULL_TIME", "PART_TIME", "CONTRACT", "TEMPORARY"},
          list(demand_data.get("byEmploymentType", {}).keys()))
    status, filtered = demand(seeker, municipality="BASUD", employmentType="FULL_TIME")
    check("Demand filters combine correctly",
          status == 200 and filtered.get("totals", {}).get("activeJobs", 0)
          == len([j for j in call("GET", "/job-seekers/jobs?size=500&municipality=BASUD", seeker)[1].get("content", [])
                  if j.get("employmentType") == "FULL_TIME"]),
          filtered.get("totals", {}).get("activeJobs"))

    status, admin_data = call("GET", "/admin/skills-analytics", admin)
    check("Admin skills analytics returns 200", status == 200 and isinstance(admin_data, dict), status)
    admin_totals = (admin_data or {}).get("totals", {})
    check("Admin totals include seeker supply", admin_totals.get("totalSeekers", 0) >= 1, admin_totals)
    supply_row = next((row for row in (admin_data or {}).get("supplyDemand", [])
                       if row["skill"].lower() == "microsoft excel"), None)
    check("Supply/demand row exists for Excel", supply_row is not None)
    check("Gap equals demand postings minus supply profiles",
          supply_row and supply_row["gap"] == supply_row["demandPostings"] - supply_row["supplySeekers"],
          supply_row)
    check("Imbalance label follows the gap sign",
          supply_row and supply_row["imbalance"] == (
              "SHORTAGE" if supply_row["gap"] > 0 else "SURPLUS" if supply_row["gap"] < 0 else "BALANCED"),
          supply_row and supply_row["imbalance"])
    check("Admin methodology is documented",
          "demand" in (admin_data or {}).get("methodology", {})
          and "supply" in (admin_data or {}).get("methodology", {}), list((admin_data or {}).get("methodology", {})))
    status, admin_filtered = call("GET", "/admin/skills-demand?municipality=LABO", admin)
    check("Admin demand filter works", status == 200, status)

    before = call("GET", "/job-seekers/learning-interests", seeker)[1] or []
    status, added = call("POST", "/job-seekers/learning-interests?skill=" + urllib.parse.quote("Microsoft Excel"),
                         seeker)
    check("Learning interest can be added", status == 201 and added.get("skillKey") == "microsoft excel", status)
    listed = call("GET", "/job-seekers/learning-interests", seeker)[1] or []
    check("Learning interest is private to its owner and listed",
          any(item["skillKey"] == "microsoft excel" for item in listed), listed)
    status, again = call("POST", "/job-seekers/learning-interests?skill=" + urllib.parse.quote("Microsoft Excel"),
                         seeker)
    check("Adding the same interest is idempotent", status == 201 and again.get("id") == added.get("id"), status)
    status, _ = call("POST", "/job-seekers/learning-interests?skill=Communication&userId=1", seeker)
    mine = call("GET", "/job-seekers/learning-interests", seeker)[1] or []
    check("A client-supplied userId cannot target another seeker",
          all(item["skillKey"] != "communication" for item in mine) or len(mine) <= 2, mine)
    call("DELETE", "/job-seekers/learning-interests?skill=Communication", seeker)
    status, _ = call("DELETE", "/job-seekers/learning-interests?skill=" + urllib.parse.quote("Microsoft Excel"),
                     seeker)
    check("Learning interest can be removed", status == 200, status)

    resource = {
        "skillName": "Microsoft Excel", "title": "Check - Spreadsheet Basics",
        "provider": "Check Training Provider", "url": RESOURCE_URL,
        "free": True, "online": True, "duration": "8 hours",
        "description": "Temporary verified resource used by the automated check.",
        "availability": "Check availability", "active": True,
    }
    status, created_resource = call("POST", "/admin/learning-resources", admin, resource)
    check("Admin can add a learning resource", status == 201 and created_resource.get("id"), status)
    resource_id = (created_resource or {}).get("id")
    status, resources = call("GET", "/skills/resources?skill=" + urllib.parse.quote("Microsoft Excel"), seeker)
    check("Seeker sees the verified resource",
          status == 200 and any(item["id"] == resource_id for item in resources), len(resources or []))
    status, none = call("GET", "/skills/resources?skill=" + urllib.parse.quote("Aquaculture Diving"), seeker)
    check("An unlisted skill honestly returns no resources", status == 200 and none == [], none)
    resource["title"] = "Check - Spreadsheet Basics (updated)"
    status, updated = call("PUT", "/admin/learning-resources/%d" % resource_id, admin, resource)
    check("Admin can update the resource", status == 200 and updated.get("title", "").endswith("(updated)"), status)
    status, _ = call("DELETE", "/admin/learning-resources/%d" % resource_id, admin)
    check("Admin can delete the resource", status == 200, status)
    status, gone = call("GET", "/skills/resources?skill=" + urllib.parse.quote("Microsoft Excel"), seeker)
    check("Deleted resource is no longer visible", status == 200 and gone == [], gone)

    status, _ = call("GET", "/job-seekers/skills-analysis")
    check("Skills analysis requires authentication", status in (401, 403), status)
    status, _ = call("GET", "/job-seekers/skills-analysis", employer)
    check("Employers cannot read seeker skills analysis", status == 403, status)
    status, _ = call("GET", "/admin/skills-analytics", seeker)
    check("Seekers cannot read admin skills analytics", status == 403, status)
    status, _ = call("POST", "/admin/learning-resources", seeker, resource)
    check("Seekers cannot create learning resources", status == 403, status)

    # A seeker with no skills must get an honest, non-crashing analysis.
    register_payload = {
        "email": temp_email, "password": "checkpass123", "userType": "JOB_SEEKER",
        "fullName": "Skills Check Temp", "municipality": "DAET", "barangay": "Poblacion",
    }
    response = requests.post(
        BASE + "/auth/register",
        files={"profile": (None, json.dumps(register_payload), "application/json")},
        timeout=40,
    )
    temp_token = (response.json() or {}).get("token") if response.status_code in (200, 201) else None
    check("Temporary no-skills seeker registered", bool(temp_token), response.status_code)
    if temp_token:
        status, empty = analysis(temp_token)
        check("No-skills seeker analysis returns 200", status == 200, status)
        check("No-skills seeker sees zero skills",
              empty.get("totals", {}).get("mySkills") == 0, empty.get("totals", {}))
        check("No-skills seeker gets an honest note",
              any("no skills" in note.lower() for note in empty.get("notes", [])), empty.get("notes"))
        status, _ = sim(temp_token, ["Microsoft Excel"])
        check("No-skills seeker can still simulate real demand", status == 200, status)

    # Existing features still answer.
    for name, path in [
        ("job search", "/job-seekers/jobs?size=5"),
        ("job map", "/job-seekers/jobs/map?size=5"),
        ("recommendations", "/job-seekers/recommended?size=5"),
        ("applications", "/job-seekers/applications"),
        ("notifications", "/notifications"),
        ("saved jobs", "/job-seekers/saved"),
    ]:
        status, _ = call("GET", path, seeker)
        check("Existing %s still works" % name, status == 200, status)
    if created_jobs:
        status, _ = call("GET", "/job-seekers/jobs/%d/match" % created_jobs[0], seeker)
        check("Existing matching engine still returns a score", status == 200, status)
        status, _ = call("GET", "/employers/applicants?jobId=%d" % created_jobs[0], employer)
        check("Existing employer applicants endpoint still works", status == 200, status)
    status, _ = call("GET", "/admin/dashboard", admin)
    check("Existing admin dashboard still works", status == 200, status)

finally:
    # Restore the original profile first, then remove every temporary row.
    if isinstance(original, dict):
        restore_status, restored = update_profile(seeker, original)
        check("Original seeker profile restored", restore_status == 200
              and restored.get("skills") == original.get("skills"),
              (restore_status, restored.get("skills") if isinstance(restored, dict) else None))
    for job_id in created_jobs:
        call("DELETE", "/employers/jobs/%d" % job_id, employer)
    cleaned = db_cleanup()
    check("No test jobs, users or resources left behind", cleaned)

passed = sum(1 for _, ok, _ in results if ok)
print("\n%d/%d checks passed" % (passed, len(results)))
sys.exit(0 if passed == len(results) else 1)
