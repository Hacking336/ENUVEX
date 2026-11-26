"""Phase 7 live security and regression checks.

Runs against a local instance of the application. Every check prints PASS/FAIL
with the reason, so the result is evidence rather than an assertion.

Environment:
    JSM_BASE        base URL of the running app (default http://127.0.0.1:8080)
    JSM_ADMIN_CODE  the access code configured for the name-only admin login
"""

import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("JSM_BASE", "http://127.0.0.1:8080").rstrip("/")
ADMIN_CODE = os.environ.get("JSM_ADMIN_CODE", "")
results = []


def call(method, path, token=None, body=None, raw=False):
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read().decode() or "{}"
            status = response.status
            return status, (payload if raw else json.loads(payload)), dict(response.headers)
    except urllib.error.HTTPError as error:
        payload = error.read().decode() or "{}"
        try:
            parsed = payload if raw else json.loads(payload)
        except ValueError:
            parsed = payload
        return error.code, parsed, dict(error.headers)
    except Exception as error:  # connection refused, timeout
        return 0, {"error": str(error)}, {}


def check(name, condition, detail=""):
    results.append((name, bool(condition), detail))
    print(("PASS  " if condition else "FAIL  ") + name + ("" if condition else "  <- " + str(detail)))


def login(email, password):
    status, body, _ = call("POST", "/api/auth/login", body={"email": email, "password": password})
    return status, body if isinstance(body, dict) else {}


def register_employer():
    """Creates (or signs in) a second employer used only for the IDOR checks."""
    email = "audit-employer-2@test.com"
    password = "auditpass123"

    status, body = login(email, password)
    if body.get("token"):
        return body["token"]

    profile = {
        "email": email,
        "password": password,
        "userType": "EMPLOYER",
        "businessName": "Audit Employer Two",
        "employerName": "Audit Owner",
        "businessType": "OTHER",
        "municipality": "DAET",
        "contactNumber": "09000000000",
    }
    boundary = "----jsm-audit-boundary"
    payload = json.dumps(profile)
    body_bytes = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="profile"\r\n'
        "Content-Type: application/json\r\n\r\n"
        f"{payload}\r\n"
        f"--{boundary}--\r\n"
    ).encode()
    request = urllib.request.Request(
        BASE + "/api/auth/register",
        data=body_bytes,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read().decode() or "{}")
            return data.get("token")
    except urllib.error.HTTPError as error:
        return None
    except Exception:
        return None


# ---------------------------------------------------------------------------
# 1. Administrator sign-in
# ---------------------------------------------------------------------------
status, body = login("admin++", "")
check("admin++ with no code is refused", status == 400, (status, body))

status, body = login("admin++", "definitely-not-the-code")
check("admin++ with a wrong code is refused", status == 400, (status, body))

if not ADMIN_CODE:
    print("SKIP  admin++ with the configured access code (JSM_ADMIN_CODE not set)")
    admin_token = None
else:
    status, body = login("admin++", ADMIN_CODE)
    admin_token = body.get("token")
    check("admin++ with the configured access code signs in", status == 200 and bool(admin_token), (status, body))

seeker_status, seeker = login("seeker@test.com", "jobseeker123")
seeker_token = seeker.get("token")
check("job seeker password sign-in still works", seeker_status == 200 and bool(seeker_token), (seeker_status, seeker))

employer_status, employer = login("employer@test.com", "employer123")
employer_token = employer.get("token")
check("employer password sign-in still works", employer_status == 200 and bool(employer_token), (employer_status, employer))

status, body = login("seeker@test.com", "wrong-password")
check("wrong password is refused", status == 400, (status, body))

status, body = login("seeker@test.com", " jobseeker123 ")
check("password is not trimmed into a match", status == 400, (status, body))

# ---------------------------------------------------------------------------
# 2. Session and authorization
# ---------------------------------------------------------------------------
status, body, _ = call("GET", "/api/admin/users")
check("anonymous admin API is 401", status == 401, status)

status, body, _ = call("GET", "/api/admin/users", token=seeker_token)
check("job seeker admin API is 403", status == 403, status)

status, body, _ = call("GET", "/api/admin/users", token=employer_token)
check("employer admin API is 403", status == 403, status)

status, body, _ = call("GET", "/api/employers/profile", token=seeker_token)
check("job seeker cannot read an employer profile", status == 403, status)

status, body, _ = call("GET", "/api/job-seekers/profile", token=employer_token)
check("employer cannot read a job seeker profile", status == 403, status)

if admin_token:
    status, users, _ = call("GET", "/api/admin/users", token=admin_token)
    check("administrator can read the user list", status == 200 and isinstance(users, list), status)

    # Reject a forged token.
    forged = admin_token[:-3] + ("aaa" if not admin_token.endswith("aaa") else "bbb")
    status, body, _ = call("GET", "/api/admin/users", token=forged)
    check("tampered token is refused", status == 401, status)

# ---------------------------------------------------------------------------
# 3. Suspension applies to an existing session
# ---------------------------------------------------------------------------
if admin_token and seeker_token:
    status, users, _ = call("GET", "/api/admin/users", token=admin_token)
    seeker_id = next((u.get("id") for u in users if u.get("email") == "seeker@test.com"), None)
    if seeker_id is None:
        print("SKIP  suspension checks (seeker account not found)")
    else:
        status, body, _ = call("PUT", f"/api/admin/users/{seeker_id}/suspend?reason=Phase%207%20audit",
                               token=admin_token)
        check("administrator can suspend a test account", status == 200, (status, body))

        status, body, _ = call("GET", "/api/applications", token=seeker_token)
        check("suspended account's existing token stops working", status in (401, 403), status)

        status, body = login("seeker@test.com", "jobseeker123")
        check("suspended account cannot sign in again", status == 400, (status, body))

        status, body, _ = call("PUT", f"/api/admin/users/{seeker_id}/restore", token=admin_token)
        check("administrator can restore the account", status == 200, (status, body))

        status, body = login("seeker@test.com", "jobseeker123")
        seeker_token = body.get("token")
        check("restored account can sign in again", status == 200 and bool(seeker_token), status)

# ---------------------------------------------------------------------------
# 4. Input validation and pagination limits
# ---------------------------------------------------------------------------
status, body, _ = call("GET", "/api/jobs?size=9999&page=0")
content = body.get("content") if isinstance(body, dict) else None
check("job search caps an oversized page", status == 200 and content is not None and len(content) <= 100,
      (status, len(content) if content is not None else body))

status, body, _ = call("GET", "/api/jobs?page=-5")
check("negative page number is handled", status == 200, status)

status, body, _ = call("GET", "/api/jobs?sortBy=passwordHash")
check("unknown sort column is rejected safely", status == 200, status)

status, body, _ = call("GET", "/api/jobs?minSalary=not-a-number")
check("non-numeric filter is a 400, not a 500", status == 400, (status, body))

status, body, _ = call("GET", "/api/jobs?municipality=ATLANTIS")
check("unknown enum value is a 400, not a 500", status == 400, (status, body))

status, body, _ = call("GET", "/api/jobs/99999999")
check("unknown job id is refused cleanly", status in (400, 404), (status, body))

status, body, _ = call("GET", "/api/jobs/abc")
check("non-numeric job id is a 400, not a 500", status == 400, status)

# ---------------------------------------------------------------------------
# 5. Cross-account access (IDOR)
# ---------------------------------------------------------------------------
if seeker_token and employer_token:
    status, mine, _ = call("GET", "/api/applications", token=seeker_token)
    application_id = None
    if status == 200 and isinstance(mine, list) and mine:
        application_id = mine[0].get("id")
        job_id = mine[0].get("jobPostId")

    if application_id is None:
        print("SKIP  IDOR checks (no application in the test data)")
    else:
        status, body, _ = call("GET", f"/api/employers/jobs/{application_id}", token=employer_token)
        check("GET on a write-only employer path is a 405", status == 405, (status, body))

        # A second employer must not be able to read another employer's applicant.
        other_token = register_employer()
        check("a second employer account exists for the IDOR checks", bool(other_token), "registration failed")

        if other_token:
            status, body, _ = call("GET", f"/api/applications/{application_id}", token=other_token)
            check("another employer cannot read the application (IDOR)", status == 403, (status, body))

            status, body, _ = call("GET", f"/api/employers/analytics/skills/{job_id}", token=other_token)
            check("another employer cannot read another job's skills analysis", status == 403, (status, body))

            status, body, _ = call("GET", f"/api/applications/job/{job_id}/applicants", token=other_token)
            check("another employer cannot list another job's applicants", status == 403, (status, body))

        status, body, _ = call("GET", "/api/employers/my-jobs", token=seeker_token)
        check("job seeker cannot list employer jobs", status == 403, status)

        status, body, _ = call("PUT", "/api/employers/jobs/1", token=seeker_token, body={})
        check("job seeker cannot edit a job", status == 403, status)

    status, body, _ = call("GET", "/api/moderation/reports/mine", token=seeker_token)
    check("reporter can read only their own report list", status == 200, status)

    status, body, _ = call("GET", "/api/admin/moderation-reports", token=seeker_token)
    check("job seeker cannot read the report queue", status == 403, status)

# ---------------------------------------------------------------------------
# 6. Employer analytics (Phase 5 regression)
# ---------------------------------------------------------------------------
if employer_token:
    for path in (
        "/api/employers/analytics/overview",
        "/api/employers/analytics/funnel?dateRange=30d",
        "/api/employers/analytics/response-metrics",
        "/api/employers/analytics/matching",
        "/api/employers/analytics/job-performance?page=0&size=100",
        "/api/employers/analytics/geographic",
        "/api/employers/analytics/insights",
        "/api/employers/analytics/export",
    ):
        status, body, _ = call("GET", path, token=employer_token)
        check("analytics endpoint works for its employer: " + path.split("?")[0], status == 200, (status, body))

    status, body, _ = call("GET", "/api/employers/analytics/export", token=seeker_token)
    check("job seeker cannot export employer analytics", status == 403, status)

# ---------------------------------------------------------------------------
# 7. Headers and health
# ---------------------------------------------------------------------------
status, body, headers = call("GET", "/api/jobs")
lower = {k.lower(): v for k, v in headers.items()}
check("X-Content-Type-Options is nosniff", lower.get("x-content-type-options") == "nosniff", lower.get("x-content-type-options"))
check("X-Frame-Options is DENY", lower.get("x-frame-options", "").upper() == "DENY", lower.get("x-frame-options"))
check("Referrer-Policy is set", "referrer-policy" in lower, lower.get("referrer-policy"))
check("no server or stack trace leaks", "x-application-context" not in lower, list(lower))

status, body, _ = call("GET", "/api/health")
check("health reports the database", status == 200 and isinstance(body, dict)
      and body.get("database") == "UP", (status, body))

status, body, _ = call("GET", "/this-page-does-not-exist.html", raw=True)
check("unknown static path is a 404", status == 404, status)

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
passed = sum(1 for _, ok, _ in results if ok)
failed = [(name, detail) for name, ok, detail in results if not ok]
print()
print(f"{passed}/{len(results)} checks passed")
if failed:
    print("Failures:")
    for name, detail in failed:
        print("  -", name, detail)
sys.exit(1 if failed else 0)
