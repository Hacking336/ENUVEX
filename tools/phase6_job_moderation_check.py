import json
import time
import urllib.request
import urllib.error

B = "http://127.0.0.1:8080"
results = []


def call(method, path, token=None, body=None, expect=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(B + path, data=data, method=method)
    if data:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode()
            status = response.status
            payload = json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode()
        try:
            payload = json.loads(raw)
        except Exception:
            payload = raw
    if expect is not None:
        results.append((status == expect, f"{method} {path} -> {status} (expected {expect})", payload))
    else:
        results.append((True, f"{method} {path} -> {status}", payload))
    return status, payload


def login(email, password):
    _, payload = call("POST", "/api/auth/login", body={"email": email, "password": password}, expect=200)
    return payload["token"]


admin = login("admin++", "")
employer = login("employer@test.com", "employer123")
seeker = login("seeker@test.com", "jobseeker123")

RUN = str(int(time.time()))
TITLE = "Moderation Flow Tester " + RUN
job_body = {
    "jobTitle": TITLE,
    "jobCategory": "SALES",
    "jobDescription": "Serve walk-in customers, operate the register and reconcile the till at closing time.",
    "responsibilities": "Handle counter sales and daily stock counts.",
    "requiredSkills": "Customer service",
    "employmentType": "FULL_TIME",
    "municipality": "DAET",
    "barangay": "Borabod",
    "jobLocation": "Daet town proper",
    "salaryMin": 12000,
    "salaryMax": 15000,
}

# 1. A new posting enters review and is invisible to the public.
status, created = call("POST", "/api/employers/jobs", employer, job_body, expect=201)
job_id = created["id"]
results.append((created.get("moderationStatus") == "PENDING_REVIEW",
                f"new job moderationStatus = {created.get('moderationStatus')}", None))

def job_titles(payload):
    rows = payload.get("content", []) if isinstance(payload, dict) else (payload or [])
    return [job.get("jobTitle") for job in rows]


_, public_jobs = call("GET", "/api/jobs", seeker)
titles = job_titles(public_jobs)
results.append((TITLE not in titles, "pending job hidden from public /api/jobs", titles))

_, pending = call("GET", "/api/admin/job-posts?moderationStatus=PENDING_REVIEW", admin)
ids = [job["id"] for job in pending.get("content", [])]
results.append((job_id in ids, "pending job listed in admin queue", ids))

# 2. Rejection needs a reason.
call("PUT", f"/api/admin/job-posts/{job_id}/reject?reason=", admin, expect=400)

# 3. Approve, then the posting becomes public.
_, approved = call("PUT", f"/api/admin/job-posts/{job_id}/approve", admin, expect=200)
results.append((approved.get("moderationStatus") == "APPROVED", "approve sets APPROVED", None))
def job_titles(payload):
    rows = payload.get("content", []) if isinstance(payload, dict) else (payload or [])
    return [job.get("jobTitle") for job in rows]


_, public_jobs = call("GET", "/api/jobs", seeker)
titles = job_titles(public_jobs)
results.append((TITLE in titles, "approved job visible publicly", titles))

# 4. Request changes requires an explanation, then the employer resubmits by editing.
call("PUT", f"/api/admin/job-posts/{job_id}/changes-requested?reason=", admin, expect=400)
call("PUT", f"/api/admin/job-posts/{job_id}/changes-requested?reason=Add+the+work+schedule+and+the+exact+store+branch.", admin, expect=200)
_, mine = call("GET", "/api/employers/my-jobs", employer)
resubmitted = [job for job in mine if job["id"] == job_id][0]
results.append((resubmitted.get("moderationStatus") == "CHANGES_REQUESTED",
                "changes requested reaches the employer", resubmitted.get("moderationStatus")))

body2 = dict(job_body)
body2["jobDescription"] = ("Serve walk-in customers, operate the register and reconcile the till at closing time. "
                           "Monday to Saturday, 8am to 5pm at the Borabod branch.")
call("PUT", f"/api/employers/jobs/{job_id}", employer, body2, expect=200)
_, mine = call("GET", "/api/employers/my-jobs", employer)
resubmitted = [job for job in mine if job["id"] == job_id][0]
results.append((resubmitted.get("moderationStatus") == "PENDING_REVIEW",
                "employer edit resubmits for review", resubmitted.get("moderationStatus")))
def job_titles(payload):
    rows = payload.get("content", []) if isinstance(payload, dict) else (payload or [])
    return [job.get("jobTitle") for job in rows]


_, public_jobs = call("GET", "/api/jobs", seeker)
titles = job_titles(public_jobs)
results.append((TITLE not in titles, "resubmitted job hidden again", titles))

# 5. Reject with a reason, then the employer can resubmit again.
call("PUT", f"/api/admin/job-posts/{job_id}/reject?reason=The+salary+range+is+below+the+regional+minimum+wage.", admin, expect=200)
_, mine = call("GET", "/api/employers/my-jobs", employer)
rejected = [job for job in mine if job["id"] == job_id][0]
results.append((rejected.get("moderationStatus") == "REJECTED", "reject reaches the employer", None))
call("PUT", f"/api/employers/jobs/{job_id}", employer, body2, expect=200)
_, mine = call("GET", "/api/employers/my-jobs", employer)
again = [job for job in mine if job["id"] == job_id][0]
results.append((again.get("moderationStatus") == "PENDING_REVIEW", "rejected job can be corrected and resubmitted", None))

# 6. Approval notification reaches the employer exactly once.
_, approved = call("PUT", f"/api/admin/job-posts/{job_id}/approve", admin, expect=200)
_, notes = call("GET", "/api/notifications", employer)
items = notes if isinstance(notes, list) else notes.get("content", [])
approval_notes = [note for note in items if note.get("type") == "JOB_POST_APPROVED"
                  and str(note.get("referenceId")) == str(job_id)]
results.append((len(approval_notes) <= 1, f"approval notifications for job {job_id}: {len(approval_notes)}", None))

# 7. Audit log entries carry the acting administrator.
_, logs = call("GET", "/api/admin/audit-logs", admin)
actions = [(log.get("actionType"), log.get("newState"), log.get("adminUserEmail")) for log in logs
           if log.get("targetType") == "JOB_POST" and str(log.get("targetId")) == str(job_id)]
results.append((len(actions) >= 4 and all(email == "admin@jobskillsmatching.com" for _, _, email in actions),
                "audit rows recorded with admin identity", actions))

# 8. There is no route to edit or delete an audit entry.
call("DELETE", "/api/admin/audit-logs/1", admin, expect=404)
call("PUT", "/api/admin/audit-logs/1", admin, {}, expect=404)

print(json.dumps({"jobId": job_id}, indent=None))
failed = 0
for ok, label, extra in results:
    print(("PASS " if ok else "FAIL ") + label + ("" if ok else f"  <<< {extra}"))
    failed += 0 if ok else 1
print(f"\n{len(results) - failed}/{len(results)} checks passed")
