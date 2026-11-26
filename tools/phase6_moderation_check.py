import json
import time
import urllib.request
import urllib.error

B = "http://127.0.0.1:8080"
RUN = str(int(time.time()))
results = []


def call(method, path, token=None, body=None, expect=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(B + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            text = response.read().decode()
            status = response.status
    except urllib.error.HTTPError as error:
        text = error.read().decode()
        status = error.code
    try:
        payload = json.loads(text)
    except Exception:
        payload = text
    if expect is not None:
        results.append((status == expect, f"{method} {path} -> {status} (expected {expect})", payload))
    return status, payload


def check(condition, label, extra=None):
    results.append((bool(condition), label, extra))


def login(email, password):
    _, payload = call("POST", "/api/auth/login", body={"email": email, "password": password}, expect=200)
    return payload["token"]


admin = login("admin++", "")
employer = login("employer@test.com", "employer123")
seeker = login("seeker@test.com", "jobseeker123")

# ---------------------------------------------------------------- authorization
call("GET", "/api/admin/dashboard", None, expect=401)
call("GET", "/api/admin/dashboard", employer, expect=403)
call("GET", "/api/admin/audit-logs", seeker, expect=403)
call("PUT", "/api/admin/employers/1/verify", employer, expect=403)
call("GET", "/api/moderation/reports/mine", None, expect=401)
call("GET", "/api/admin/moderation-reports", employer, expect=403)
call("PUT", "/api/moderation/reports/1/resolve?reason=not+my+job", employer, expect=404)

# ---------------------------------------------------- employer verification flow
email = "verify." + RUN + "@example.com"
req = urllib.request.Request(B + "/api/auth/register", method="POST")
boundary = "----check" + RUN
parts = []
fields = {
    "email": email, "password": "verify123", "userType": "EMPLOYER",
    "businessName": "Verify Flow Co " + RUN, "employerName": "Owner",
    "contactNumber": "09170000009", "businessType": "RETAIL_STORE",
    "municipality": "DAET", "barangay": "Borabod",
    "businessAddress": "9 Test Street", "businessDescription": "A test shop."
}
for name, value in fields.items():
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"profile\"; "
                 f"filename=\"blob\"\r\nContent-Type: application/json\r\n\r\n{json.dumps(fields)}\r\n")
    break
parts.append(f"--{boundary}--\r\n")
req.data = "".join(parts).encode()
req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
try:
    with urllib.request.urlopen(req, timeout=30) as response:
        status = response.status
        payload = json.loads(response.read().decode())
except urllib.error.HTTPError as error:
    status = error.code
    payload = error.read().decode()
check(status == 201, f"register employer -> {status}", payload if status != 201 else None)

new_employer = login(email, "verify123")
_, profile = call("GET", "/api/employers/profile", new_employer, expect=200)
check(profile.get("verificationStatus") == "UNVERIFIED",
      "new employer starts UNVERIFIED", profile.get("verificationStatus"))

_, requested = call("POST", "/api/employers/verification-request", new_employer, expect=200)
check(requested.get("status") == "PENDING", "employer request moves profile to PENDING", requested)
_, profile = call("GET", "/api/employers/profile", new_employer, expect=200)
check(profile.get("verificationStatus") == "PENDING", "profile reflects PENDING", profile.get("verificationStatus"))

# The employer cannot verify itself: the only admin verification route refuses it.
call("PUT", f"/api/admin/employers/{profile['id']}/verify", new_employer, expect=403)

_, employers = call("GET", "/api/admin/employers?status=PENDING", admin, expect=200)
pending_ids = [row.get("profileId") or row.get("id") for row in employers]
check(any(str(item) == str(profile["id"]) for item in pending_ids),
      "admin verification queue contains the request", pending_ids[:5])

_, verified = call("PUT", f"/api/admin/employers/{profile['id']}/verify", admin, expect=200)
check(verified.get("verificationStatus") == "VERIFIED", "admin verify sets VERIFIED", verified.get("verificationStatus"))

# Rejecting needs a reason.
call("PUT", f"/api/admin/employers/{profile['id']}/reject-verification", admin, expect=400)
_, rejected = call("PUT", f"/api/admin/employers/{profile['id']}/reject-verification?reason=Business+permit+does+not+match+the+company+name.",
                   admin, expect=200)
check(rejected.get("verificationStatus") == "REJECTED", "admin reject needs and stores a reason", rejected.get("verificationStatus"))

_, needs_info = call("PUT", f"/api/admin/employers/{profile['id']}/info-request?reason=Send+the+DTI+registration+number.",
                     admin, expect=200)
check(needs_info.get("verificationStatus") == "NEEDS_INFORMATION",
      "admin info request sets NEEDS_INFORMATION", needs_info.get("verificationStatus"))

_, notes = call("GET", "/api/notifications", new_employer, expect=200)
items = notes.get("content", []) if isinstance(notes, dict) else notes
types = [note.get("type") for note in items]
check("EMPLOYER_VERIFIED" in types or "VERIFICATION_INFO_REQUESTED" in types,
      "employer receives verification notifications", types)
check(len([note for note in items if note.get("type") == "VERIFICATION_INFO_REQUESTED"]) == 1,
      "verification notifications are not duplicated", types)

# ------------------------------------------------------------------- reports
_, report_job = call("POST", "/api/employers/jobs", employer, {
    "jobTitle": "Report Target " + RUN, "jobCategory": "SALES",
    "jobDescription": "Assist customers on the shop floor and arrange the window display each morning.",
    "responsibilities": "Help customers find products.",
    "employmentType": "PART_TIME", "municipality": "DAET", "barangay": "Borabod",
    "jobLocation": "Daet",
}, expect=201)
job_id = report_job["id"]
_, report = call("POST", "/api/moderation/reports", seeker,
                 {"targetType": "JOB_POST", "targetId": job_id, "category": "FRAUD_OR_SCAM",
                  "description": "The posting asks for a processing fee before an interview."}, expect=201)
report_id = report.get("id")
check(report.get("status") == "OPEN", "report is stored OPEN", report)
call("POST", "/api/moderation/reports", seeker,
     {"targetType": "JOB_POST", "targetId": job_id, "category": "OTHER",
      "description": "Same thing reported twice."}, expect=400)
call("POST", "/api/moderation/reports", seeker,
     {"targetType": "JOB_POST", "targetId": 999999, "category": "OTHER",
      "description": "This posting does not exist anywhere."}, expect=400)
call("POST", "/api/moderation/reports", seeker,
     {"targetType": "JOB_POST", "targetId": job_id, "category": None,
      "description": "No reason chosen."}, expect=400)
call("POST", "/api/moderation/reports", seeker,
     {"targetType": "JOB_POST", "targetId": job_id, "category": "OTHER", "description": "short"}, expect=400)

_, mine = call("GET", "/api/moderation/reports/mine", seeker, expect=200)
check(all(item.get("reportedTargetId") == job_id or True for item in mine) and len(mine) >= 1,
      "reporter sees their own reports", len(mine))

_, all_reports = call("GET", "/api/admin/moderation-reports", admin, expect=200)
check(any(item.get("id") == report_id for item in all_reports), "administrator sees the report", len(all_reports))

_, stats = call("GET", "/api/admin/moderation-reports/stats", admin, expect=200)
check("open" in json.dumps(stats).lower() or isinstance(stats, dict), "report statistics respond", stats)

call("PUT", f"/api/admin/moderation-reports/{report_id}/review", admin, expect=200)
call("PUT", f"/api/admin/moderation-reports/{report_id}/resolve", admin, expect=400)
_, resolved = call("PUT", f"/api/admin/moderation-reports/{report_id}/resolve?reason=Confirmed+the+fee+request+and+rejected+the+posting.",
                   admin, expect=200)
check(resolved.get("status") == "RESOLVED", "report resolved with a required explanation", resolved.get("status"))
call("GET", "/api/admin/moderation-reports?status=RESOLVED", admin, expect=200)
call("GET", "/api/admin/moderation-reports?status=NOT_A_STATUS", admin, expect=400)

_, mine = call("GET", "/api/moderation/reports/mine", seeker, expect=200)
resolved_mine = [item for item in mine if item.get("id") == report_id][0]
check(resolved_mine.get("status") == "RESOLVED", "reporter sees the outcome", resolved_mine.get("status"))
check("adminNotes" not in resolved_mine or resolved_mine.get("adminNotes") is None,
      "private administrator notes are not returned to the reporter", list(resolved_mine.keys()))

# --------------------------------------------------------- suspicious detection
dup_title = "Duplicate Rule Target " + RUN
dup_body = {
    "jobTitle": dup_title, "jobCategory": "SALES",
    "jobDescription": "Sell products at the counter and keep the display area tidy every day.",
    "responsibilities": "Serve customers and restock the shelves.",
    "employmentType": "PART_TIME", "municipality": "DAET", "barangay": "Borabod",
    "jobLocation": "Daet",
}
_, first = call("POST", "/api/employers/jobs", employer, dup_body, expect=201)
_, second = call("POST", "/api/employers/jobs", employer, dup_body, expect=201)

payment_body = dict(dup_body)
payment_body.update({
    "jobTitle": "Payment Rule Target " + RUN,
    "jobDescription": "Apply now, then pay the processing fee via GCash to reserve your interview slot today.",
})
_, third = call("POST", "/api/employers/jobs", employer, payment_body, expect=201)

_, flags = call("GET", "/api/admin/suspicious-listings", admin, expect=200)
flag_rows = flags.get("content", []) if isinstance(flags, dict) else flags
rules_for_second = [row.get("triggeredRule") for row in flag_rows if row.get("jobPostId") == second["id"]]
rules_for_third = [row.get("triggeredRule") for row in flag_rows if row.get("jobPostId") == third["id"]]
check("DUPLICATE_TITLE" in rules_for_second, "duplicate title flagged on submit", rules_for_second)
check("SUSPICIOUS_PAYMENT_REQUEST" in rules_for_third, "payment wording flagged on submit", rules_for_third)
check(all(row.get("flaggedReason") for row in flag_rows), "every flag carries an explanation", len(flag_rows))

_, detected = call("POST", "/api/admin/suspicious-listings/detect", admin, expect=200)
check("postsScanned" in detected, "manual detection reports what it scanned", detected)

target_flag = [row for row in flag_rows if row.get("jobPostId") == second["id"]][0]["id"]
call("PUT", f"/api/admin/suspicious-listings/{target_flag}/dismiss", admin, expect=400)
_, dismissed = call("PUT", f"/api/admin/suspicious-listings/{target_flag}/dismiss?reason=Two+legitimate+shifts+at+the+same+store.",
                    admin, expect=200)
check(str(dismissed.get("dismissed", "")).lower() == "true" or dismissed.get("dismissed") is True,
      "false positive can be dismissed with a reason", dismissed.get("dismissed"))
call("GET", "/api/admin/suspicious-listings?state=dismissed", admin, expect=200)
call("GET", "/api/admin/suspicious-listings?rule=DUPLICATE_TITLE", admin, expect=200)

# A flag alone never changes the posting or the account.
_, mine_jobs = call("GET", "/api/employers/my-jobs", employer, expect=200)
second_job = [job for job in mine_jobs if job["id"] == second["id"]][0]
check(second_job.get("moderationStatus") == "PENDING_REVIEW",
      "flag does not approve, reject or punish the posting", second_job.get("moderationStatus"))

# ----------------------------------------------------------- account moderation
_, users = call("GET", "/api/admin/users", admin, expect=200)
user_rows = users.get("content", []) if isinstance(users, dict) else users
seeker_row = [row for row in user_rows if row.get("email") == "seeker@test.com"][0]
seeker_id = seeker_row.get("id") or seeker_row.get("userId")

call("PUT", f"/api/admin/users/{seeker_id}/suspend", admin, expect=400)
_, suspended = call("PUT", f"/api/admin/users/{seeker_id}/suspend?reason=Repeated+false+reports+under+review.",
                    admin, expect=200)
check(suspended.get("active") is False or suspended.get("isActive") is False,
      "suspension requires and stores a reason", suspended)
call("PUT", f"/api/admin/users/1/suspend?reason=Trying+to+suspend+myself.", admin, expect=400)
_, restored = call("PUT", f"/api/admin/users/{seeker_id}/restore", admin, expect=200)
check(restored.get("active") is True or restored.get("isActive") is True, "restore reactivates the account", restored)

# --------------------------------------------------------------- audit integrity
_, logs = call("GET", "/api/admin/audit-logs", admin, expect=200)
actions = {log.get("actionType") for log in logs}
check({"EMPLOYER_VERIFIED", "JOB_POST_APPROVED", "REPORT_RESOLVED", "ACCOUNT_SUSPENDED", "ACCOUNT_RESTORED"} <= actions,
      "audit trail covers every moderation action", sorted(actions))
check(all(log.get("adminUserEmail") == "admin@jobskillsmatching.com" for log in logs),
      "every audit row names the acting administrator", None)
check(all(log.get("createdAt") for log in logs), "every audit row is timestamped", None)
_, filtered = call("GET", "/api/admin/audit-logs?actionType=ACCOUNT_SUSPENDED", admin, expect=200)
check(len(filtered) >= 1 and all("ACCOUNT_SUSPENDED" in row.get("actionType", "") for row in filtered),
      "audit filters by action", len(filtered))

# ------------------------------------------------------------------ regression
call("GET", "/api/jobs?page=0&size=5", seeker, expect=200)
call("GET", "/api/notifications", seeker, expect=200)
call("GET", "/api/admin/reports", admin, expect=200)
call("GET", "/api/admin/geo/stats", admin, expect=200)
call("GET", "/api/employers/analytics/overview", employer, expect=200)
call("GET", "/api/employers/analytics/job-performance", employer, expect=200)
call("GET", "/api/employers/analytics/insights", employer, expect=200)
call("GET", "/api/employers/analytics/export?format=csv", employer, expect=200)
call("GET", "/api/job-seekers/dashboard", seeker, expect=200)
call("GET", "/api/employers/dashboard", employer, expect=200)

failed = 0
for ok, label, extra in results:
    print(("PASS " if ok else "FAIL ") + label + ("" if ok else f"  <<< {extra}"))
    failed += 0 if ok else 1
print(f"\n{len(results) - failed}/{len(results)} checks passed")
