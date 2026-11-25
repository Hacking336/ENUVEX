"""
End-to-end checks for application tracking, interviews and notifications.

Usage:  python3 scripts/tracking-api-check.py [base-url]

Creates one temporary job, runs the whole lifecycle (submitted → reviewing →
interview → reschedule → cancel → accepted → rejected → reopened), checks
timeline/history, permissions and notification behaviour, then deletes the job
and its notifications. Requires the optional pg8000 package only for the
database cleanup of test notifications; without it the job is still deleted and
the leftover notification ids are printed.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from urllib.parse import quote

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
        with urllib.request.urlopen(req, data, timeout=20) as response:
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


def notifications(token, query=""):
    status, data = call("GET", "/notifications" + query, token)
    return status, data if isinstance(data, list) else []


def of_type(items, kind):
    return [n for n in items if n.get("type") == kind]


def env_values():
    """Reads the backend .env when the shell could not export it (values contain &)."""
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


def cleanup_notifications(app_ids, job_ids):
    try:
        import pg8000.dbapi
    except Exception:
        print("CLEANUP: pg8000 unavailable; delete notifications for applications", app_ids, "manually")
        return
    env = env_values()
    url = os.environ.get("DB_URL") or env.get("DB_URL", "")
    match = re.search(r"//([^:/]+):(\d+)/([^?]+)", url)
    if not match:
        print("CLEANUP: DB_URL not understood; delete notifications for applications", app_ids, "manually")
        return
    host, port, database = match.group(1), int(match.group(2)), match.group(3)
    connection = pg8000.dbapi.connect(
        host=host, port=port, database=database,
        user=os.environ.get("DB_USERNAME") or env.get("DB_USERNAME"),
        password=os.environ.get("DB_PASSWORD") or env.get("DB_PASSWORD"),
        ssl_context=True,
    )
    cursor = connection.cursor()
    cursor.execute("SET search_path TO enuv")
    print("CLEANUP: connected to database")
    for app_id in app_ids:
        cursor.execute(
            "DELETE FROM notifications WHERE reference_type = 'APPLICATION' AND reference_id = %s",
            (app_id,),
        )
    for job_id in job_ids:
        cursor.execute(
            "DELETE FROM notifications WHERE reference_type = 'JOB_POST' AND reference_id = %s",
            (job_id,),
        )
    connection.commit()
    cursor.close()
    connection.close()
    print("CLEANUP: test notifications removed")


seeker_token = login("seeker@test.com", "jobseeker123")
employer_token = login("employer@test.com", "employer123")
admin_status, admin_data = call("POST", "/auth/login", body={"email": "admin", "password": ""})
admin_token = admin_data["token"] if admin_status == 200 else None
check("admin login", admin_status == 200, admin_status)

unread_before = call("GET", "/notifications/unread-count", seeker_token)[1]["count"]

job_payload = {
    "jobTitle": "TRACKING TEST — IT Support Staff",
    "jobCategory": "IT_TECHNOLOGY",
    "jobDescription": "Temporary job created by the tracking API check.",
    "responsibilities": "Support staff and keep systems running.",
    "requiredSkills": "Java, Spring, SQL, Customer Service",
    "educationRequirement": "COLLEGE_GRADUATE",
    "experienceRequirement": "1-2 years",
    "employmentType": "FULL_TIME",
    "workSchedule": "MORNING",
    "salaryMin": 18000,
    "salaryMax": 25000,
    "municipality": "DAET",
    "barangay": "Alawihao",
    "availablePositions": 1,
}
status, created = call("POST", "/employers/jobs", employer_token, job_payload)
check("temporary job created", status == 201, status)
job_id = created["id"] if status == 201 else None
app_id = None

try:
    if not job_id:
        raise SystemExit("cannot continue without a job")

    # --- submission ------------------------------------------------------
    status, applied = call("POST", "/job-seekers/apply?jobId=%s" % job_id, seeker_token)
    check("application submitted", status == 201, (status, applied))
    app_id = applied["id"] if status == 201 else None

    status, duplicate = call("POST", "/job-seekers/apply?jobId=%s" % job_id, seeker_token)
    check("duplicate application prevented", status == 400 and "Already applied" in str(duplicate), status)

    status, detail = call("GET", "/applications/%s" % app_id, seeker_token)
    check("application detail readable by owner", status == 200, status)
    check("initial status is SUBMITTED", detail.get("status") == "SUBMITTED", detail.get("status"))
    check("timeline starts with submission", detail["timeline"] and detail["timeline"][0]["eventType"] == "SUBMITTED",
          detail.get("timeline"))
    check("submission timestamp recorded", bool(detail["timeline"][0].get("createdAt")),
          detail["timeline"][0].get("createdAt"))
    check("latest update exposed on tracker row",
          bool(call("GET", "/applications", seeker_token)[1][0].get("lastUpdatedAt")))

    status, anonymous = call("GET", "/applications/%s" % app_id)
    check("anonymous application access blocked", status in (401, 403), status)
    status, seeker_status = call("PATCH", "/applications/%s/status" % app_id, seeker_token, {"status": "ACCEPTED"})
    check("seeker cannot set status", status == 403, status)

    # --- notifications on submit -----------------------------------------
    status, items = notifications(seeker_token)
    submitted_notes = [n for n in of_type(items, "APPLICATION_SUBMITTED") if n.get("referenceId") == app_id]
    check("submission notification created", len(submitted_notes) == 1, len(submitted_notes))
    check("submission notification links to application",
          submitted_notes and "/job-seeker/application.html?id=%s" % app_id in submitted_notes[0].get("link", ""),
          submitted_notes[0].get("link") if submitted_notes else None)

    # --- employer review with a note -------------------------------------
    status, changed = call("PATCH", "/applications/%s/status" % app_id, employer_token,
                           {"status": "REVIEWING", "message": "Checking your support experience."})
    check("employer moves to REVIEWING", status == 200 and changed["status"] == "REVIEWING", status)

    status, bad = call("PATCH", "/applications/%s/status" % app_id, employer_token, {"status": "ACCEPTED"})
    check("invalid transition blocked", status == 400, (status, bad))

    status, detail = call("GET", "/applications/%s" % app_id, seeker_token)
    timeline = detail.get("timeline", [])
    check("timeline ordered oldest to newest",
          all(timeline[i]["createdAt"] <= timeline[i + 1]["createdAt"] for i in range(len(timeline) - 1)),
          [t["createdAt"] for t in timeline])
    check("employer note stored in history",
          any(t.get("message") == "Checking your support experience." for t in timeline))
    check("reviewing notification sent", bool(of_type(notifications(seeker_token)[1], "APPLICATION_STATUS")))

    # --- interview --------------------------------------------------------
    tomorrow = (datetime.now() + timedelta(days=1)).replace(hour=10, minute=0, second=0, microsecond=0)
    interview_body = {
        "interviewAt": tomorrow.isoformat(),
        "interviewType": "ONLINE",
        "meetingLink": "https://meet.example.com/jsm-test",
        "instructions": "Bring a valid ID and join five minutes early.",
        "employerMessage": "Looking forward to meeting you.",
    }
    status, scheduled = call("POST", "/applications/%s/interview" % app_id, employer_token, interview_body)
    check("interview scheduled", status == 201, (status, scheduled))
    check("interview moves application to INTERVIEW", scheduled.get("status") == "INTERVIEW", scheduled.get("status"))
    check("interview details returned", scheduled.get("interview", {}).get("meetingLink") == interview_body["meetingLink"])
    check("interview emails a notification",
          bool(of_type(notifications(seeker_token)[1], "INTERVIEW_INVITE")))

    later = tomorrow.replace(hour=14)
    status, moved = call("PATCH", "/applications/%s/interview" % app_id, employer_token,
                         dict(interview_body, interviewAt=later.isoformat()))
    check("interview rescheduled", status == 200 and moved["interview"]["interviewAt"].startswith(
        later.strftime("%Y-%m-%dT14:00")), (status, moved.get("interview")))
    reschedule_events = [t for t in moved.get("timeline", []) if t["eventType"] == "INTERVIEW_RESCHEDULED"]
    check("reschedule keeps the old schedule in history",
          reschedule_events and "Previously" in (reschedule_events[-1].get("message") or ""),
          reschedule_events[-1].get("message") if reschedule_events else None)
    check("reschedule notification sent", bool(of_type(notifications(seeker_token)[1], "INTERVIEW_RESCHEDULED")))

    status, cancelled = call("DELETE", "/applications/%s/interview?reason=%s" % (app_id, quote("Panel unavailable")), employer_token)
    check("interview cancelled", status == 200, status)
    check("cancel returns application to SHORTLISTED", cancelled.get("status") == "SHORTLISTED", cancelled.get("status"))
    check("cancel notification sent", bool(of_type(notifications(seeker_token)[1], "INTERVIEW_CANCELLED")))

    status, _ = call("PATCH", "/applications/%s/interview" % app_id, employer_token, interview_body)
    check("cannot reschedule a cancelled interview", status == 400, status)

    # --- accept, withdraw guard, reject, reopen --------------------------
    status, accepted = call("PATCH", "/applications/%s/status" % app_id, employer_token, {"status": "ACCEPTED"})
    check("employer accepts applicant", status == 200 and accepted["status"] == "ACCEPTED", status)
    check("accept notification sent", bool(of_type(notifications(seeker_token)[1], "APPLICATION_STATUS")))

    status, withdraw = call("POST", "/applications/%s/withdraw" % app_id, seeker_token)
    check("accepted application cannot be withdrawn", status == 400, (status, withdraw))

    status, rejected = call("PATCH", "/applications/%s/status" % app_id, employer_token,
                            {"status": "REJECTED", "message": "Position filled internally."})
    check("employer can reject after accepting", status == 200 and rejected["status"] == "REJECTED", status)

    status, reopened = call("PATCH", "/applications/%s/status" % app_id, employer_token, {"status": "REVIEWING"})
    check("employer can reopen with recorded history",
          status == 200 and reopened["status"] == "REVIEWING"
          and any(t["eventType"] == "STATUS_CHANGED" and t["status"] == "REVIEWING"
                  for t in reopened["timeline"]), status)

    # --- withdrawals on a fresh application ------------------------------
    status, applied2 = call("POST", "/job-seekers/apply?jobId=%s" % job_id, seeker_token)
    second_id = applied2.get("id") if status == 201 else None
    if not second_id:
        # Same seeker cannot apply twice to one job; withdraw on the reopened one.
        second_id = app_id
    status, withdrawn = call("POST", "/applications/%s/withdraw" % second_id, seeker_token)
    check("seeker withdraws an open application", status == 200 and withdrawn["status"] == "WITHDRAWN", status)
    status, again = call("PATCH", "/applications/%s/status" % second_id, employer_token, {"status": "ACCEPTED"})
    check("withdrawn application cannot be reactivated", status == 400, status)

    # --- notification read state ----------------------------------------
    items = notifications(seeker_token)[1]
    unread = [n for n in items if not n.get("isRead")]
    check("unread notifications exist", len(unread) > 0, len(unread))
    target = unread[0]["id"]
    status, marked = call("PATCH", "/notifications/%s/read" % target, seeker_token)
    check("notification marked read", status == 200, status)
    after = call("GET", "/notifications/unread-count", seeker_token)[1]["count"]
    check("unread count decreases", after == unread_before + len(unread) - 1 or after == len(unread) - 1,
          (unread_before, len(unread), after))

    status, all_read = call("PATCH", "/notifications/read-all", seeker_token)
    check("mark all read", status == 200 and all_read["unread"] == 0, (status, all_read))
    status, filtered = notifications(seeker_token, "?filter=unread")
    check("unread filter empty after mark all", filtered == [], len(filtered))

    status, _ = call("DELETE", "/notifications/%s" % target, employer_token)
    check("another user cannot delete a notification", status == 404, status)
    status, _ = call("DELETE", "/notifications/%s" % target, seeker_token)
    check("owner deletes a notification", status == 200, status)
    status, remaining = notifications(seeker_token)
    check("deleted notification gone", all(n["id"] != target for n in remaining))

    # --- dashboards and existing features -------------------------------
    status, tracker = call("GET", "/applications", seeker_token)
    check("seeker tracker lists real rows with location and update time",
          status == 200 and tracker and "lastUpdatedAt" in tracker[0] and "municipality" in tracker[0], status)

    if admin_token:
        status, stats = call("GET", "/admin/dashboard", admin_token)
        check("admin dashboard still works", status == 200, status)
        check("admin sees application activity by status",
              status == 200 and isinstance(stats.get("applicationsByStatus"), dict)
              and "SUBMITTED" in stats["applicationsByStatus"], stats.get("applicationsByStatus"))

    status, listing = call("GET", "/job-seekers/jobs?page=0&size=2", seeker_token)
    check("job search pagination still works", status == 200 and len(listing["content"]) <= 2, status)
    status, rec = call("GET", "/job-seekers/recommended?page=0&size=5", seeker_token)
    check("recommendations still work", status == 200 and "content" in rec, status)
    status, map_data = call("GET", "/job-seekers/jobs/map?page=0&size=50", seeker_token)
    check("map still works", status == 200 and "markers" in map_data, status)
    status, near = call("GET", "/job-seekers/jobs?nearLatitude=14.1128&nearLongitude=122.9553&radiusKm=25&size=10",
                        seeker_token)
    check("Near Me still works", status == 200, status)
finally:
    if job_id:
        status, deleted = call("DELETE", "/employers/jobs/%s" % job_id, employer_token)
        check("cleanup: temporary job deleted", status == 200, (status, deleted))
    cleanup_notifications([app_id] if app_id else [], [job_id] if job_id else [])

print("\nSUMMARY: %d passed, %d failed" % (
    sum(1 for _, ok, _ in results if ok), sum(1 for _, ok, _ in results if not ok)))
for name, ok, detail in results:
    if not ok:
        print("  FAILED:", name, detail)
