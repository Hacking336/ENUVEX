#!/usr/bin/env python3
"""Build the JSM final defense deck.

Editable source for docs/presentation/JSM_Final_Defense.pptx. Every number and
claim in the slides is taken from the Phase 8 documentation set, which in turn
was verified against the codebase. Screenshots come from the real application
captured in docs/presentation/screenshots/.

Run:  python3 docs/presentation/build_deck.py
Requires: python-pptx (pip install python-pptx)
"""

import os
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "screenshots")
OUT = os.path.join(HERE, "JSM_Final_Defense.pptx")

NAVY = RGBColor(0x10, 0x2B, 0x4C)
GREEN = RGBColor(0x2F, 0xA2, 0x47)
DARK_GREEN = RGBColor(0x25, 0x85, 0x3A)
SOFT_GREEN = RGBColor(0xEA, 0xF5, 0xEC)
BG = RGBColor(0xF5, 0xF8, 0xFA)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
GREY = RGBColor(0x5B, 0x6B, 0x7B)

HEAD_FONT = "Poppins"
BODY_FONT = "Inter"

W, H = Inches(13.333), Inches(7.5)


def new_deck():
    prs = Presentation()
    prs.slide_width = W
    prs.slide_height = H
    return prs


def blank(prs, background=BG):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    fill = slide.background.fill
    fill.solid()
    fill.fore_color.rgb = background
    return slide


def rect(slide, x, y, w, h, color, line=None):
    shape = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, x, y, w, h)
    shape.fill.solid()
    shape.fill.fore_color.rgb = color
    if line is None:
        shape.line.fill.background()
    else:
        shape.line.color.rgb = line
        shape.line.width = Pt(1)
    shape.shadow.inherit = False
    return shape


def text(slide, x, y, w, h, content, size=18, color=NAVY, bold=False,
         font=BODY_FONT, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, spacing=1.0):
    box = slide.shapes.add_textbox(x, y, w, h)
    frame = box.text_frame
    frame.word_wrap = True
    frame.vertical_anchor = anchor
    frame.margin_left = 0
    frame.margin_right = 0
    frame.margin_top = 0
    frame.margin_bottom = 0
    lines = content if isinstance(content, list) else [content]
    for index, line in enumerate(lines):
        para = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        para.alignment = align
        para.line_spacing = spacing
        run = para.add_run()
        run.text = line
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.name = font
        run.font.color.rgb = color
    return box


def header(slide, title, kicker=None):
    rect(slide, 0, 0, W, Inches(0.09), GREEN)
    if kicker:
        text(slide, Inches(0.75), Inches(0.38), Inches(11.8), Inches(0.3),
             kicker.upper(), size=12, color=GREEN, bold=True, font=BODY_FONT)
        text(slide, Inches(0.75), Inches(0.68), Inches(11.8), Inches(0.7),
             title, size=32, color=NAVY, bold=True, font=HEAD_FONT)
    else:
        text(slide, Inches(0.75), Inches(0.55), Inches(11.8), Inches(0.8),
             title, size=34, color=NAVY, bold=True, font=HEAD_FONT)


def footer(slide, number):
    rect(slide, 0, H - Inches(0.055), W, Inches(0.055), SOFT_GREEN)
    text(slide, Inches(0.75), H - Inches(0.52), Inches(6), Inches(0.3),
         "JSM — Job & Skills Matching  ·  ENUVEX", size=10, color=GREY)
    text(slide, W - Inches(1.6), H - Inches(0.52), Inches(0.85), Inches(0.3),
         f"{number}", size=10, color=GREY, align=PP_ALIGN.RIGHT)


def notes(slide, body):
    slide.notes_slide.notes_text_frame.text = body


def bullets(slide, x, y, w, items, size=17, gap=Inches(0.52), color=NAVY):
    for index, item in enumerate(items):
        top = y + gap * index
        dot = rect(slide, x, top + Inches(0.11), Inches(0.11), Inches(0.11), GREEN)
        dot.fill.solid()
        dot.fill.fore_color.rgb = GREEN
        text(slide, x + Inches(0.28), top, w, Inches(0.9), item, size=size, color=color)


def card(slide, x, y, w, h, title, lines, accent=GREEN):
    rect(slide, x, y, w, h, WHITE)
    rect(slide, x, y, Inches(0.055), h, accent)
    text(slide, x + Inches(0.28), y + Inches(0.22), w - Inches(0.5), Inches(0.4),
         title, size=16, color=NAVY, bold=True, font=HEAD_FONT)
    text(slide, x + Inches(0.28), y + Inches(0.68), w - Inches(0.5), h - Inches(0.8),
         lines, size=12, color=GREY, spacing=1.15)


def picture(slide, name, x, y, w=None, h=None):
    path = os.path.join(SHOTS, name)
    return slide.shapes.add_picture(path, x, y, width=w, height=h)


def stat(slide, x, y, value, label, w=Inches(2.6)):
    rect(slide, x, y, w, Inches(1.35), WHITE)
    text(slide, x + Inches(0.2), y + Inches(0.18), w - Inches(0.4), Inches(0.5),
         value, size=28, color=DARK_GREEN, bold=True, font=HEAD_FONT)
    text(slide, x + Inches(0.2), y + Inches(0.78), w - Inches(0.4), Inches(0.45),
         label, size=11, color=GREY, spacing=1.1)


# ---------------------------------------------------------------- slides ----

def slide_title(prs):
    slide = blank(prs, NAVY)
    rect(slide, 0, 0, Inches(0.18), H, GREEN)
    rect(slide, Inches(9.9), 0, Inches(3.43), H, RGBColor(0x0C, 0x22, 0x3D))
    text(slide, Inches(0.9), Inches(1.5), Inches(8.4), Inches(0.4),
         "FINAL DEFENSE  ·  CAPSTONE PROJECT", size=13, color=GREEN, bold=True)
    text(slide, Inches(0.9), Inches(2.05), Inches(8.6), Inches(1.6),
         "Job & Skills Matching", size=52, color=WHITE, bold=True, font=HEAD_FONT)
    text(slide, Inches(0.9), Inches(3.35), Inches(8.4), Inches(0.6),
         "A local job and skills matching platform for Basud, Daet, Vinzons and Labo",
         size=19, color=RGBColor(0xC9, 0xD8, 0xE6))
    rect(slide, Inches(0.9), Inches(4.35), Inches(1.6), Inches(0.05), GREEN)
    text(slide, Inches(0.9), Inches(4.65), Inches(8.4), Inches(0.9),
         ["ENUVEX", "Live system: https://enuvex.onrender.com", "Source: Hacking336/ENUVEX (branch main)"],
         size=14, color=RGBColor(0xC9, 0xD8, 0xE6), spacing=1.35)
    text(slide, Inches(10.25), Inches(2.6), Inches(2.7), Inches(2.0),
         ["JSM", "", "8 phases", "delivered"], size=30, color=GREEN, bold=True, font=HEAD_FONT)
    notes(slide, (
        "Good morning. Our project is the Job and Skills Matching system, JSM, developed by ENUVEX. "
        "It is a working web platform for job seekers and employers in four municipalities of Camarines "
        "Norte: Basud, Daet, Vinzons and Labo. It is deployed at enuvex.onrender.com and all eight planned "
        "development phases are complete. In the next fifteen minutes I will explain the problem, show the "
        "system, walk through the matching logic, and be honest about its limits."))
    return slide


def slide_background(prs, n):
    slide = blank(prs)
    header(slide, "Why this project exists", "Background")
    bullets(slide, Inches(0.75), Inches(1.75), Inches(6.1), [
        "The four municipalities are dominated by small businesses: retail, food service, offices, "
        "construction, transport, agriculture and fisheries.",
        "Hiring is informal — tarpaulins, social posts, word of mouth, walk-ins.",
        "A job seeker sees only the vacancies they happen to hear about.",
        "An employer receives applications with the wrong skills, or too far away.",
    ], size=16, gap=Inches(0.95))
    card(slide, Inches(7.25), Inches(1.8), Inches(5.3), Inches(1.7), "No shared channel",
         "Vacancies, requirements, applications and outcomes are never in one place, so nothing can be "
         "compared, tracked or reviewed.")
    card(slide, Inches(7.25), Inches(3.75), Inches(5.3), Inches(1.7), "No way to compare fit",
         "Skills, education, experience, salary and distance are never weighed together before an "
         "application is sent.", accent=DARK_GREEN)
    card(slide, Inches(7.25), Inches(5.62), Inches(5.3), Inches(1.25), "No accountability",
         "Fake and duplicate posts cannot be reported, verified or removed.", accent=NAVY)
    footer(slide, n)
    notes(slide, (
        "Camarines Norte's first district is not a corporate job market. Most employers are small "
        "businesses that do not have a recruitment office. Vacancies circulate through informal channels, "
        "and that creates two problems at once: seekers miss opportunities, and employers spend time on "
        "applications that cannot work. Nothing records what happened, and nothing can be reviewed later. "
        "We did not measure unemployment statistics ourselves, so we are not quoting any."))
    return slide


def slide_problem(prs, n):
    slide = blank(prs)
    header(slide, "The problem we addressed", "Problem statement")
    text(slide, Inches(0.75), Inches(1.6), Inches(11.8), Inches(0.7),
         "Local job seekers and local employers have no common, structured channel for hiring.",
         size=20, color=NAVY, bold=True, font=HEAD_FONT)
    items = [
        ("Fragmented vacancies", "Posts disappear; employers re-advertise the same role repeatedly."),
        ("Skills mismatch", "Nobody can compare a post's requirements with a profile before applying."),
        ("Location ambiguity", "In a rural municipality the barangay decides whether a job is reachable."),
        ("No tracking", "Applications are verbal; there is no status, timeline or interview record."),
        ("No accountability", "Fake, misleading or duplicated posts go unchallenged."),
        ("No employer insight", "Employers cannot see where applicants come from or where they drop off."),
    ]
    for index, (title, body) in enumerate(items):
        col = index % 2
        row = index // 2
        x = Inches(0.75) + col * Inches(6.05)
        y = Inches(2.55) + row * Inches(1.45)
        card(slide, x, y, Inches(5.7), Inches(1.25), title, body,
             accent=GREEN if index % 2 == 0 else DARK_GREEN)
    footer(slide, n)
    notes(slide, (
        "Six concrete problems. Fragmented vacancies, skills mismatch, location ambiguity, no tracking, "
        "no accountability and no visibility for employers. Each one became a module in the system, and "
        "each module is demonstrated later in this presentation."))
    return slide


def slide_objectives(prs, n):
    slide = blank(prs)
    header(slide, "Objectives", "What the system had to achieve")
    rect(slide, Inches(0.75), Inches(1.7), Inches(5.6), Inches(1.55), NAVY)
    text(slide, Inches(1.0), Inches(1.9), Inches(5.1), Inches(0.3), "GENERAL OBJECTIVE",
         size=11, color=GREEN, bold=True)
    text(slide, Inches(1.0), Inches(2.22), Inches(5.1), Inches(0.95),
         "Build a web-based job and skills matching system for the four municipalities that connects "
         "local seekers with local employers through structured profiles, geographic search and a "
         "transparent match score.", size=12, color=WHITE, spacing=1.15)
    objectives = [
        "Role-based accounts for seekers, employers and administrators",
        "Structured seeker and employer profiles",
        "Geographic search inside the four municipalities",
        "Deterministic, explained job–seeker match score",
        "Application tracking with timeline and interviews",
        "Skills-gap guidance and learning resources",
        "Employer hiring analytics with CSV export",
        "Moderation: verification, approvals, reports, audit",
    ]
    for index, item in enumerate(objectives):
        col = index % 2
        row = index // 2
        x = Inches(6.85) + col * Inches(3.0)
        y = Inches(1.7) + row * Inches(1.22)
        rect(slide, x, y, Inches(2.85), Inches(1.05), WHITE)
        text(slide, x + Inches(0.2), y + Inches(0.13), Inches(0.5), Inches(0.3),
             f"{index + 1:02d}", size=13, color=GREEN, bold=True, font=HEAD_FONT)
        text(slide, x + Inches(0.2), y + Inches(0.45), Inches(2.5), Inches(0.55),
             item, size=11, color=NAVY, spacing=1.1)
    footer(slide, n)
    notes(slide, (
        "The general objective is on the left. On the right are the eight specific objectives, and each one "
        "maps to a delivered module: accounts, profiles, geographic search, matching, tracking, skills "
        "guidance, employer analytics and moderation. Nothing on this list is planned but unbuilt."))
    return slide


def slide_scope(prs, n):
    slide = blank(prs)
    header(slide, "Scope and limitations", "Coverage")
    text(slide, Inches(0.75), Inches(1.6), Inches(6.0), Inches(0.35),
         "FOUR MUNICIPALITIES OF CAMARINES NORTE", size=12, color=GREEN, bold=True)
    rows = [("Basud", "0501601000", "29"), ("Daet", "0501603000", "25"),
            ("Labo", "0501606000", "52"), ("Vinzons", "0501612000", "19")]
    text(slide, Inches(0.75), Inches(2.05), Inches(1.9), Inches(0.3), "Municipality",
         size=11, color=GREY, bold=True)
    text(slide, Inches(3.0), Inches(2.05), Inches(1.9), Inches(0.3), "PSGC code",
         size=11, color=GREY, bold=True)
    text(slide, Inches(5.0), Inches(2.05), Inches(1.4), Inches(0.3), "Barangays",
         size=11, color=GREY, bold=True)
    for index, (name, code, count) in enumerate(rows):
        y = Inches(2.45) + index * Inches(0.62)
        rect(slide, Inches(0.75), y, Inches(6.0), Inches(0.52), WHITE)
        text(slide, Inches(0.95), y + Inches(0.12), Inches(2.0), Inches(0.3), name, size=14,
             color=NAVY, bold=True)
        text(slide, Inches(3.0), y + Inches(0.14), Inches(1.9), Inches(0.3), code, size=12, color=GREY)
        text(slide, Inches(5.0), y + Inches(0.14), Inches(1.2), Inches(0.3), count, size=12, color=GREY)
    text(slide, Inches(0.75), Inches(5.05), Inches(6.0), Inches(1.6),
         "125 barangays come from the PSA PSGC reference layer. Being listed means the barangay exists in "
         "the coverage area — not that it contains a vacancy. The deployment does not contain every "
         "employer or every job in these municipalities.",
         size=12, color=GREY, spacing=1.2)
    text(slide, Inches(7.15), Inches(1.6), Inches(5.4), Inches(0.35),
         "DELIBERATELY OUT OF SCOPE", size=12, color=GREEN, bold=True)
    text(slide, Inches(7.15), Inches(2.05), Inches(5.4), Inches(4.4),
         ["• Municipalities outside the four, other regions",
          "• Payroll, attendance, contracts, e-signatures",
          "• Chat between users (notifications only)",
          "• Online payments or paid posting tiers",
          "• AI or machine-learning matching",
          "• Resume parsing, OCR, automatic ranking",
          "• Native mobile apps (responsive web only)",
          "• Email or SMS delivery"],
         size=14, color=NAVY, spacing=1.5)
    footer(slide, n)
    notes(slide, (
        "The scope is geographic and functional. Geographically, the system is limited by an enumeration "
        "to four municipalities, with 125 barangays from the official PSGC reference list. I want to be "
        "precise: the parent data lists barangays, but a barangay only shows jobs when an employer actually "
        "pinned a workplace there. We do not claim to contain every employer in these towns. Functionally, "
        "we deliberately excluded payroll, chat, payments and AI."))
    return slide


def slide_solution(prs, n):
    slide = blank(prs)
    header(slide, "Proposed solution: one platform, three workspaces", "Solution")
    cards = [
        ("Job seeker", ["Structured profile with skills and preferences",
                        "Search by place, category, salary, distance, match band",
                        "Map with barangay boundaries and job pins",
                        "Match score explained criterion by criterion",
                        "Apply, track status, interviews, notifications",
                        "Skills gap and what-if simulation"], GREEN),
        ("Employer", ["Company profile and verification request",
                      "Post a job; approve-before-publish moderation",
                      "Applicants ranked by match score",
                      "Status pipeline and interview scheduling",
                      "Analytics: funnel, response time, geography",
                      "CSV export of hiring data"], DARK_GREEN),
        ("Administrator", ["Verify employers, request information",
                           "Approve, reject or request changes on posts",
                           "Handle user reports on posts and employers",
                           "Review rule-based suspicious listing flags",
                           "Suspend or restore accounts",
                           "Append-only audit trail"], NAVY),
    ]
    for index, (title, lines, accent) in enumerate(cards):
        x = Inches(0.75) + index * Inches(4.05)
        rect(slide, x, Inches(1.75), Inches(3.8), Inches(4.3), WHITE)
        rect(slide, x, Inches(1.75), Inches(3.8), Inches(0.62), accent)
        text(slide, x + Inches(0.25), Inches(1.88), Inches(3.3), Inches(0.35), title,
             size=17, color=WHITE, bold=True, font=HEAD_FONT)
        text(slide, x + Inches(0.25), Inches(2.55), Inches(3.3), Inches(3.3),
             ["• " + line for line in lines], size=11, color=NAVY, spacing=1.5)
    text(slide, Inches(0.75), Inches(6.3), Inches(11.8), Inches(0.4),
         "One deployed application serves the API and every page: no separate frontend server, "
         "no build step for the interface.", size=12, color=GREY)
    footer(slide, n)
    notes(slide, (
        "The answer is a single platform with three workspaces and one shared database. A seeker's profile, "
        "an employer's post and an administrator's decision all live in the same system, which is why a "
        "match score, an approval state and an audit entry can be trusted to agree. Everything is served by "
        "one Spring Boot application."))
    return slide


def slide_architecture(prs, n):
    slide = blank(prs)
    header(slide, "System architecture", "How it is built")
    boxes = [
        (0, "Browser", "HTML pages + vanilla JS\nlocalStorage session", SOFT_GREEN, NAVY),
        (1, "Security chain", "CORS · headers\nrate limiter · JWT filter", WHITE, NAVY),
        (2, "Spring Boot 3.4", "controllers · services\nrepositories · Flyway", WHITE, NAVY),
        (3, "PostgreSQL", "schema enuv\n17 tables · V1–V9", SOFT_GREEN, NAVY),
        (4, "External", "OSM tiles · Leaflet\nfonts, icons", WHITE, GREY),
    ]
    box_w = Inches(2.09)
    for index, (_, title, body, fill, color) in enumerate(boxes):
        x = Inches(0.75) + index * Inches(2.435)
        rect(slide, x, Inches(1.8), box_w, Inches(1.45), fill)
        text(slide, x + Inches(0.13), Inches(1.95), box_w - Inches(0.26), Inches(0.35), title,
             size=12, color=color, bold=True, font=HEAD_FONT)
        text(slide, x + Inches(0.13), Inches(2.35), box_w - Inches(0.26), Inches(0.8), body,
             size=10, color=GREY, spacing=1.2)
    for index in range(4):
        arrow = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW,
                                       Inches(0.75) + index * Inches(2.435) + Inches(2.12),
                                       Inches(2.4), Inches(0.28), Inches(0.25))
        arrow.fill.solid()
        arrow.fill.fore_color.rgb = GREEN
        arrow.line.fill.background()
    facts = [
        ("Java 21", "Spring Boot 3.4.2"),
        ("17 tables", "Flyway V1–V9, validate"),
        ("33 pages", "static, served by the app"),
        ("JWT", "HS256, 1 hour, BCrypt"),
    ]
    for index, (value, label) in enumerate(facts):
        stat(slide, Inches(0.75) + index * Inches(2.95), Inches(3.65), value, label, w=Inches(2.75))
    text(slide, Inches(0.75), Inches(5.25), Inches(11.8), Inches(1.6),
         ["Deployment: Render builds the Dockerfile (Maven stage → JRE) and runs one container, "
          "auto-deployed from main, with Supabase PostgreSQL and health check /api/health.",
          "No Node build, no React/Vue, no Redis, no message queue, no AI service, no paid third-party API."],
         size=13, color=GREY, spacing=1.35)
    footer(slide, n)
    notes(slide, (
        "Technically: one Spring Boot 3.4 application on Java 21. The browser gets static HTML and vanilla "
        "JavaScript; the API is JSON over the same origin. Requests pass a rate limiter and a JWT filter "
        "before reaching controllers, services and repositories. PostgreSQL holds seventeen tables in its "
        "own schema, created by nine Flyway migrations, and Hibernate validates the schema at startup. "
        "Hosting is Render with Supabase. There is no AI service and no paid dependency."))
    return slide


def slide_modules(prs, n):
    slide = blank(prs)
    header(slide, "Main modules across the eight phases", "Modules")
    rows = [
        ("1", "Geographic mapping", "Four municipalities, 125 PSGC barangays, boundaries, pins, near-me radius"),
        ("2", "Matching & recommendations", "Five weighted criteria, bands, per-criterion explanations"),
        ("3", "Applications & notifications", "Seven statuses, timeline, interviews, inbox with dedupe"),
        ("4", "Skills gap & guidance", "Demand vs supply, gap analysis, simulation, learning resources"),
        ("5", "Employer analytics", "Nine views, date ranges, CSV export"),
        ("6", "Admin moderation", "Verification, approvals, reports, detection, suspension, audit"),
        ("7", "Security & QA", "Access-code gate, session revocation, limits, headers, error mapping"),
        ("8", "Release & documentation", "This documentation set, presentation, rate limiting, verification"),
    ]
    for index, (num, title, body) in enumerate(rows):
        y = Inches(1.62) + index * Inches(0.63)
        rect(slide, Inches(0.75), y, Inches(11.8), Inches(0.54), WHITE if index % 2 == 0 else SOFT_GREEN)
        text(slide, Inches(0.95), y + Inches(0.12), Inches(0.5), Inches(0.3), num, size=13,
             color=GREEN, bold=True, font=HEAD_FONT)
        text(slide, Inches(1.45), y + Inches(0.12), Inches(3.3), Inches(0.3), title, size=13,
             color=NAVY, bold=True)
        text(slide, Inches(4.85), y + Inches(0.14), Inches(7.5), Inches(0.3), body, size=11, color=GREY)
    footer(slide, n)
    notes(slide, (
        "Eight phases, eight module groups. Phases one to six are user-facing features; phase seven was a "
        "full security and quality audit; phase eight is this release: documentation, presentation and one "
        "real security addition, persistent rate limiting."))
    return slide


def slide_geo(prs, n):
    slide = blank(prs)
    header(slide, "Geographic mapping", "Phase 1")
    bullets(slide, Inches(0.75), Inches(1.7), Inches(5.0), [
        "Municipality and barangay data from PSA PSGC with boundary polygons",
        "Only posts whose employer pinned a workplace are plotted",
        "Near-me radius search uses the browser position for that search only",
        "Coverage panel shows barangays and active jobs per municipality",
    ], size=14, gap=Inches(1.0))
    picture(slide, "02-job-map.png", Inches(6.0), Inches(1.65), w=Inches(6.6))
    text(slide, Inches(6.0), Inches(5.45), Inches(6.6), Inches(0.5),
         "Job Map with filters, coverage panel and the Leaflet base map (real application data).",
         size=11, color=GREY)
    footer(slide, n)
    notes(slide, (
        "This is the actual job map. On the left, the four municipalities with their PSGC codes, barangay "
        "counts and active jobs. The base map draws real boundaries. Markers appear only for posts with a "
        "confirmed workplace location. In this training database many posts have no pin, and the system "
        "says so instead of inventing a location."))
    return slide


def slide_matching(prs, n):
    slide = blank(prs)
    header(slide, "Matching engine: deterministic, not AI", "Phase 2")
    weights = [("Skills", 40, GREEN), ("Location", 25, DARK_GREEN),
               ("Employment type", 15, GREEN), ("Experience", 10, DARK_GREEN), ("Salary", 10, GREEN)]
    for index, (label, weight, color) in enumerate(weights):
        y = Inches(1.75) + index * Inches(0.72)
        text(slide, Inches(0.75), y, Inches(2.3), Inches(0.35), label, size=13, color=NAVY, bold=True)
        rect(slide, Inches(3.1), y + Inches(0.05), Inches(3.6), Inches(0.34), WHITE)
        rect(slide, Inches(3.1), y + Inches(0.05), Inches(3.6 * weight / 40.0), Inches(0.34), color)
        text(slide, Inches(6.8), y, Inches(1.0), Inches(0.35), f"{weight}%", size=13, color=GREY, bold=True)
    rect(slide, Inches(0.75), Inches(5.45), Inches(7.0), Inches(1.15), NAVY)
    text(slide, Inches(1.0), Inches(5.62), Inches(6.6), Inches(0.9),
         "overall % = earned points / applicable weight × 100\nUnjudgeable criteria leave the denominator",
         size=14, color=WHITE, bold=True, spacing=1.3)
    card(slide, Inches(8.1), Inches(1.75), Inches(4.45), Inches(1.5), "Location tiers",
         "Same barangay 100 · same municipality 80 · bordering municipality 50 · outside 10")
    card(slide, Inches(8.1), Inches(3.4), Inches(4.45), Inches(1.5), "Bands",
         "Excellent ≥ 90 · Strong 75–89 · Good 60–74 · Partial 40–59 · Low < 40", accent=DARK_GREEN)
    card(slide, Inches(8.1), Inches(5.05), Inches(4.45), Inches(1.55), "Missing data",
         "No salary, no skills list or no employment preference is removed from the score, "
         "never counted as a mismatch.", accent=NAVY)
    footer(slide, n)
    notes(slide, (
        "The matching engine is simple arithmetic, and that is the point. Skills forty, location twenty-five, "
        "employment type fifteen, experience ten, salary ten. Each criterion scores zero to a hundred and "
        "earns its weight proportionally. The final percentage is earned points divided by the applicable "
        "weight, so a criterion that cannot be judged, like an undisclosed salary, is removed from the "
        "denominator instead of punishing the applicant. This is not artificial intelligence: there is no "
        "model and no training data."))
    return slide


def slide_tracking(prs, n):
    slide = blank(prs)
    header(slide, "Application tracking and skills guidance", "Phases 3 and 4")
    steps = ["Submitted", "Reviewing", "Shortlisted", "Interview", "Accepted"]
    for index, step in enumerate(steps):
        x = Inches(0.75) + index * Inches(2.42)
        fill = GREEN if index < 4 else DARK_GREEN
        rect(slide, x, Inches(1.65), Inches(2.1), Inches(0.62), fill)
        text(slide, x, Inches(1.78), Inches(2.1), Inches(0.3), step, size=13, color=WHITE,
             bold=True, align=PP_ALIGN.CENTER)
    text(slide, Inches(0.75), Inches(2.35), Inches(11.8), Inches(0.3),
         "plus Rejected at any stage and Withdrawn by the applicant · every change is recorded in the timeline "
         "and notifies the other party", size=11, color=GREY)
    picture(slide, "05-applications.png", Inches(0.75), Inches(2.8), w=Inches(6.0))
    picture(slide, "04-skills-gap.png", Inches(7.0), Inches(2.8), w=Inches(6.0))
    text(slide, Inches(0.75), Inches(6.55), Inches(6.0), Inches(0.3),
         "Applications with status and match", size=11, color=GREY)
    text(slide, Inches(7.0), Inches(6.55), Inches(6.0), Inches(0.3),
         "Skills gap: demand vs what the seeker has", size=11, color=GREY)
    footer(slide, n)
    notes(slide, (
        "Tracking uses seven statuses with a full history, so an applicant can see what happened and when, "
        "including interview details. Phase four adds guidance: which skills local employers ask for, which "
        "ones the seeker is missing, what a what-if simulation would do to their scores, and curated "
        "learning resources."))
    return slide


def slide_analytics(prs, n):
    slide = blank(prs)
    header(slide, "Employer analytics", "Phase 5")
    picture(slide, "08-analytics.png", Inches(0.75), Inches(1.6), w=Inches(7.4))
    bullets(slide, Inches(8.4), Inches(1.7), Inches(4.2), [
        "Overview counters from the employer's own posts",
        "Applicant funnel and response metrics",
        "Match distribution and skill gaps per post",
        "Geographic origin of applicants",
        "7 / 30 / 90 day or all-time filter",
        "CSV export built in the browser with formula guard",
    ], size=12, gap=Inches(0.8))
    text(slide, Inches(0.75), Inches(5.3), Inches(7.4), Inches(0.4),
         "Real employer console: 21 posts, 1 applicant in the training database.",
         size=11, color=GREY)
    footer(slide, n)
    notes(slide, (
        "Employers get their own analytics, computed from their own data only: overview, funnel, response "
        "time, match distribution, per-post performance, applicant geography and plain-language insights, "
        "with a date filter and CSV export. The numbers here are from our training database, so they are "
        "small, but they are real computations, not mock-ups."))
    return slide


def slide_moderation(prs, n):
    slide = blank(prs)
    header(slide, "Administrator moderation", "Phase 6")
    cols = [
        ("Verification", ["Employer requests review",
                          "Admin verifies, rejects or asks for information",
                          "Reason required and sent to the employer"]),
        ("Job approval", ["New and edited posts enter PENDING_REVIEW",
                          "Approve, reject, request changes, restore",
                          "Employer notified of every decision"]),
        ("Reports", ["Seekers report posts, employers or users",
                     "Under review → resolved or dismissed",
                     "Reporter identity visible only to admins"]),
        ("Suspicious listings", ["Six rules: duplicates, missing data,\nrepeated rejections, payment requests",
                                 "One open flag per rule per post",
                                 "Dismiss with a reason"]),
    ]
    for index, (title, lines) in enumerate(cols):
        x = Inches(0.75) + index * Inches(3.05)
        rect(slide, x, Inches(1.7), Inches(2.85), Inches(2.25), WHITE)
        rect(slide, x, Inches(1.7), Inches(2.85), Inches(0.5), GREEN if index % 2 == 0 else DARK_GREEN)
        text(slide, x + Inches(0.2), Inches(1.8), Inches(2.5), Inches(0.3), title, size=13,
             color=WHITE, bold=True, font=HEAD_FONT)
        text(slide, x + Inches(0.2), Inches(2.32), Inches(2.45), Inches(1.5),
             ["• " + line for line in lines], size=10, color=NAVY, spacing=1.35)
    rect(slide, Inches(0.75), Inches(4.15), Inches(11.8), Inches(2.3), NAVY)
    text(slide, Inches(1.0), Inches(4.35), Inches(11.3), Inches(0.35),
         "ACCOUNT MODERATION AND AUDIT", size=12, color=GREEN, bold=True)
    text(slide, Inches(1.0), Inches(4.75), Inches(11.3), Inches(1.5),
         ["Suspension sets is_active = false: the account cannot sign in and any token already issued stops "
          "working on the next request — verified by test.",
          "Every moderation decision writes an append-only audit entry with previous state, new state and reason. "
          "There is no route that edits or deletes an audit entry."],
         size=13, color=WHITE, spacing=1.35)
    footer(slide, n)
    notes(slide, (
        "Phase six is the control layer. Employers are verified before they carry a verified mark. Every post "
        "is approved before publication, and any edit sends it back to review so the public version is always "
        "one an administrator saw. Users can report posts. Six rules flag suspicious listings; a human always "
        "decides. Suspension stops an existing session immediately, and every decision is written to an "
        "append-only audit log."))
    return slide


def slide_security(prs, n):
    slide = blank(prs)
    header(slide, "Security and testing", "Phase 7 and 8")
    stat(slide, Inches(0.75), Inches(1.7), "59", "automated tests, 0 failures")
    stat(slide, Inches(3.6), Inches(1.7), "50/50", "security and API checks")
    stat(slide, Inches(6.45), Inches(1.7), "128", "moderation checks (2 suites)")
    stat(slide, Inches(9.3), Inches(1.7), "V9", "migrations, all additive")
    card(slide, Inches(0.75), Inches(3.3), Inches(5.8), Inches(1.55), "Fixed in the Phase 7 audit",
         "Password-free admin shortcut closed · suspension now kills a live session · page size capped at 100 · "
         "hardening headers · health reports database readiness · correct 400/403/405/409 instead of 500")
    card(slide, Inches(6.9), Inches(3.3), Inches(5.65), Inches(1.55), "Added in Phase 8",
         "Persistent, database-backed rate limiting on sign-in, registration, reports and applications — "
         "configurable, fails open, tested, one additive migration.", accent=DARK_GREEN)
    text(slide, Inches(0.75), Inches(5.1), Inches(11.8), Inches(0.35),
         "KNOWN LIMITATIONS — STATED OPENLY", size=12, color=GREEN, bold=True)
    text(slide, Inches(0.75), Inches(5.5), Inches(11.8), Inches(1.5),
         ["JWT in localStorage · no Content-Security-Policy · no refresh token · three admin lists paginate "
          "in the browser · Swagger public · rate limiting is per address, not per account.",
          "No penetration test, no user-acceptance study, no performance benchmark is claimed."],
         size=12, color=GREY, spacing=1.3)
    footer(slide, n)
    notes(slide, (
        "Phase seven was an audit, and it found a critical issue: administrator sign-in accepted a known name "
        "with an empty password. That is closed: the shortcut now needs a deployment access code. Suspension "
        "now revokes live sessions. Phase eight adds persistent rate limiting in the database, so a restart "
        "does not reset the counters. Fifty-nine automated tests pass. On the bottom I list what is still "
        "imperfect, because a defense should be honest about that."))
    return slide


def slide_demo(prs, n):
    slide = blank(prs)
    header(slide, "System demonstration sequence", "What you will see")
    steps = [
        ("1", "Seeker signs in", "Dashboard, completeness, recommendations"),
        ("2", "Search and map", "Filters, barangay boundaries, job pin, near-me"),
        ("3", "Match explanation", "Percentage, band, five criteria, missing skills"),
        ("4", "Skills gap", "Local demand, gap, simulation, resources"),
        ("5", "Apply and track", "Application, status, timeline, notification"),
        ("6", "Employer signs in", "Post a job, moderation status, applicants ranked"),
        ("7", "Analytics", "Funnel, response metrics, CSV export"),
        ("8", "Administrator", "Verify employer, approve post, report, audit log"),
    ]
    for index, (num, title, body) in enumerate(steps):
        col = index % 2
        row = index // 2
        x = Inches(0.75) + col * Inches(6.05)
        y = Inches(1.75) + row * Inches(1.2)
        rect(slide, x, y, Inches(5.75), Inches(1.02), WHITE)
        rect(slide, x, y, Inches(0.5), Inches(1.02), GREEN)
        text(slide, x, y + Inches(0.32), Inches(0.5), Inches(0.35), num, size=15, color=WHITE,
             bold=True, align=PP_ALIGN.CENTER, font=HEAD_FONT)
        text(slide, x + Inches(0.68), y + Inches(0.15), Inches(4.8), Inches(0.3), title, size=13,
             color=NAVY, bold=True)
        text(slide, x + Inches(0.68), y + Inches(0.5), Inches(4.8), Inches(0.4), body, size=11, color=GREY)
    footer(slide, n)
    notes(slide, (
        "The demonstration follows one story: an applicant finds a job that fits, the employer reviews the "
        "application, and the administrator keeps the listing credible. Each step has a fallback in the live "
        "demo guide, because free hosting can be slow or a demo record can be missing."))
    return slide


def slide_conclusion(prs, n):
    slide = blank(prs)
    header(slide, "Conclusion and future work", "Closing")
    text(slide, Inches(0.75), Inches(1.6), Inches(6.0), Inches(0.35), "DELIVERED",
         size=12, color=GREEN, bold=True)
    text(slide, Inches(0.75), Inches(2.0), Inches(6.0), Inches(3.4),
         ["• All eight planned phases implemented and deployed",
          "• Deterministic, explained matching — no AI claim",
          "• Geographic coverage limited to four municipalities",
          "• Tracking, interviews and notifications in one flow",
          "• Employer analytics with honest, real numbers",
          "• Moderation with an append-only audit trail",
          "• 59 automated tests, 178 scripted HTTP assertions",
          "• Documentation for users, operators and defenders"],
         size=13, color=NAVY, spacing=1.6)
    text(slide, Inches(7.0), Inches(1.6), Inches(5.5), Inches(0.35), "RECOMMENDED NEXT",
         size=12, color=GREEN, bold=True)
    text(slide, Inches(7.0), Inches(2.0), Inches(5.5), Inches(3.4),
         ["• Rotate the seeded administrator password",
          "• Content-Security-Policy and HttpOnly cookies",
          "• Per-account throttling in addition to per-address",
          "• Server-side pagination for admin lists",
          "• Full-text search indexing before growth",
          "• Backups with a rehearsed restore",
          "• Pilot with real employers in one municipality"],
         size=13, color=NAVY, spacing=1.6)
    rect(slide, Inches(0.75), Inches(5.65), Inches(11.8), Inches(1.0), NAVY)
    text(slide, Inches(1.0), Inches(5.85), Inches(11.3), Inches(0.7),
         "JSM connects local workers and local employers, and explains every match it recommends.",
         size=17, color=WHITE, bold=True, font=HEAD_FONT)
    footer(slide, n)
    notes(slide, (
        "To close: the system is complete for its documented scope, deployed, tested and documented. The "
        "matching is transparent rather than impressive-sounding. The next engineering steps are security "
        "hardening and pilot data, and the first operational step is rotating the seeded administrator "
        "password. Thank you — we welcome the panel's questions."))
    return slide


def main():
    prs = new_deck()
    slide_title(prs)
    for number, builder in enumerate(
            [slide_background, slide_problem, slide_objectives, slide_scope, slide_solution,
             slide_architecture, slide_modules, slide_geo, slide_matching, slide_tracking,
             slide_analytics, slide_moderation, slide_security, slide_demo, slide_conclusion],
            start=2):
        builder(prs, number)
    prs.save(OUT)
    print(f"Wrote {OUT} with {len(prs.slides._sldIdLst)} slides")


if __name__ == "__main__":
    main()
