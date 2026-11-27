"""Render docs/DEFENSE_DOCUMENTATION.md as an A4 PDF (fpdf2, DejaVu fonts)."""
import pathlib
import re
import sys

from fpdf import FPDF
from fpdf.fonts import FontFace

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_SRC = ROOT / "docs" / "DEFENSE_DOCUMENTATION.md"
SRC = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SRC
if not SRC.is_absolute():
    SRC = ROOT / SRC
OUT = SRC.with_suffix(".pdf")
def _fonts() -> pathlib.Path:
    """DejaVu TTFs from /tmp/fonts, else the copy bundled with matplotlib."""
    local = pathlib.Path("/tmp/fonts")
    if (local / "DejaVuSans.ttf").exists():
        return local
    import matplotlib

    return pathlib.Path(matplotlib.get_data_path()) / "fonts" / "ttf"


FONTS = _fonts()

NAVY = (15, 42, 74)
INK = (28, 34, 48)
BLUE = (31, 111, 235)
MUTED = (90, 100, 118)
RULE = (223, 230, 240)
CODE_BG = (244, 247, 251)
HEAD_BG = (238, 243, 250)


def clean(text: str) -> str:
    return text.replace("`", "").replace("**", "").strip()


class Doc(FPDF):
    label = "JSM — System Documentation for Defense"

    def header(self):
        pass

    def footer(self):
        if self.page_no() == 1:
            return
        self.set_y(-13)
        self.set_font("body", "", 7.5)
        self.set_text_color(*MUTED)
        self.cell(0, 6, self.label, align="L")
        self.cell(0, 6, f"Page {self.page_no()} of {{nb}}", align="R")
        self.set_text_color(*INK)


def build() -> None:
    lines = SRC.read_text(encoding="utf-8").splitlines()
    heading = lines[0].lstrip("# ").strip() if lines else ""
    subtitle = heading.split("—", 1)[1].strip() if "—" in heading else "Technical Documentation"

    pdf = Doc(format="A4")
    pdf.label = f"JSM — {subtitle}"
    pdf.alias_nb_pages()
    pdf.set_auto_page_break(True, margin=16)
    pdf.set_margins(17, 17, 17)
    pdf.add_font("body", "", str(FONTS / "DejaVuSans.ttf"))
    pdf.add_font("body", "B", str(FONTS / "DejaVuSans-Bold.ttf"))
    pdf.add_font("body", "I", str(FONTS / "DejaVuSans-Oblique.ttf"))
    pdf.add_font("mono", "", str(FONTS / "DejaVuSansMono.ttf"))

    # ---------------- cover ----------------
    meta = {}
    body_start = 0
    for i, line in enumerate(lines):
        if line.startswith("---") and i > 3:
            body_start = i + 1
            break
        m = re.match(r"\*\*(.+?):\*\*\s*(.+)", line)
        if m:
            meta[m.group(1)] = m.group(2).strip()

    pdf.add_page()
    pdf.set_fill_color(*BLUE)
    pdf.rect(0, 0, 210, 6, "F")
    pdf.set_y(42)
    pdf.set_font("body", "B", 11)
    pdf.set_text_color(*BLUE)
    pdf.cell(0, 7, "ENUVEX", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("body", "", 10)
    pdf.set_text_color(*MUTED)
    pdf.cell(0, 6, "Capstone Project Documentation", new_x="LMARGIN", new_y="NEXT")

    pdf.set_y(62)
    pdf.set_font("body", "B", 30)
    pdf.set_text_color(*NAVY)
    pdf.multi_cell(0, 12, "Job & Skills Matching (JSM)", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("body", "", 15)
    pdf.set_text_color(*MUTED)
    pdf.multi_cell(0, 8, subtitle, new_x="LMARGIN", new_y="NEXT")

    pdf.set_draw_color(*BLUE)
    pdf.set_line_width(0.9)
    pdf.line(17, pdf.get_y() + 6, 90, pdf.get_y() + 6)
    pdf.ln(16)

    rows = [
        ("Live system", "https://enuvex.onrender.com"),
        ("Repository", "Hacking336/ENUVEX (branch main)"),
        ("Audited commit", "b207ee0"),
        ("Coverage", "Basud · Daet · Vinzons · Labo, Camarines Norte"),
        ("Document date", "27 November 2026"),
    ]
    pdf.set_font("body", "", 9.5)
    for k, v in rows:
        pdf.set_text_color(*MUTED)
        pdf.cell(34, 7, k.upper(), new_x="RIGHT")
        pdf.set_text_color(*INK)
        pdf.cell(0, 7, v, new_x="LMARGIN", new_y="NEXT")

    pdf.set_y(232)
    pdf.set_draw_color(*RULE)
    pdf.set_line_width(0.3)
    pdf.line(17, pdf.get_y(), 193, pdf.get_y())
    pdf.ln(4)
    pdf.set_font("body", "", 8.5)
    pdf.set_text_color(*MUTED)
    pdf.multi_cell(
        0,
        5,
        "Prepared for the panel. Fields the group must complete: course, school, "
        "panel members, adviser and date of defense. Every figure in this document was "
        "produced by the system itself and is reproducible from the repository.",
    )

    # ---------------- body ----------------
    pdf.set_fill_color(255, 255, 255)
    pdf.set_text_color(*INK)
    pdf.add_page()

    i = body_start
    n = len(lines)
    while i < n:
        line = lines[i]
        stripped = line.strip()

        if not stripped or stripped == "---":
            if stripped == "---":
                pdf.ln(3)
            i += 1
            continue

        # code fence
        if stripped.startswith("```"):
            i += 1
            block = []
            while i < n and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            i += 1
            pdf.set_font("mono", "", 7.6)
            pdf.set_fill_color(*CODE_BG)
            pdf.set_text_color(*INK)
            pdf.set_draw_color(*RULE)
            pdf.multi_cell(0, 4.3, "\n".join(block), border=1, fill=True,
                           new_x="LMARGIN", new_y="NEXT")
            pdf.ln(3)
            continue

        # table
        if stripped.startswith("|"):
            rows = []
            while i < n and lines[i].strip().startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-{2,}:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            ncols = max(len(r) for r in rows)
            rows = [r + [""] * (ncols - len(r)) for r in rows]
            weights = []
            for c in range(ncols):
                longest = max(len(clean(r[c])) for r in rows)
                longest_word = max(
                    (len(w) for r in rows for w in clean(r[c]).split()), default=4
                )
                weights.append(max(6, min(longest_word, 18), min(longest, 40)))
            total = sum(weights)
            avail = pdf.w - pdf.l_margin - pdf.r_margin
            widths = [avail * w / total for w in weights]
            pdf.set_font("body", "", 8)
            pdf.set_draw_color(*RULE)
            pdf.set_text_color(*INK)
            with pdf.table(
                col_widths=tuple(widths),
                width=avail,
                line_height=4.4,
                padding=(1.6, 2.0),
                headings_style=FontFace(emphasis="BOLD", fill_color=HEAD_BG, color=NAVY),
                first_row_as_headings=True,
                text_align="LEFT",
                borders_layout="ALL",
            ) as table:
                for r in rows:
                    row = table.row()
                    for c in r:
                        row.cell(clean(c))
            pdf.ln(3)
            continue

        # headings
        if stripped.startswith("### "):
            pdf.ln(2.5)
            pdf.set_font("body", "B", 10.2)
            pdf.set_text_color(*NAVY)
            pdf.multi_cell(0, 5.6, clean(stripped[4:]), new_x="LMARGIN", new_y="NEXT")
            pdf.ln(1.2)
            i += 1
            continue

        if stripped.startswith("## "):
            pdf.ln(4)
            pdf.set_draw_color(*RULE)
            pdf.set_line_width(0.3)
            y = pdf.get_y()
            pdf.line(17, y, 193, y)
            pdf.ln(2.5)
            pdf.set_font("body", "B", 13)
            pdf.set_text_color(*NAVY)
            pdf.multi_cell(0, 6.5, clean(stripped[3:]), new_x="LMARGIN", new_y="NEXT")
            pdf.ln(1.5)
            i += 1
            continue

        if stripped.startswith("# "):
            i += 1
            continue

        # lists
        m = re.match(r"^(\d+)\.\s+(.*)$", stripped)
        if stripped.startswith("- ") or m:
            marker = f"{m.group(1)}." if m else "•"
            text = m.group(2) if m else stripped[2:]
            pdf.set_font("body", "", 9.4)
            pdf.set_text_color(*INK)
            pdf.set_x(pdf.l_margin + 3)
            pdf.cell(5.5, 5, marker)
            pdf.multi_cell(0, 5, text.replace("`", ""), markdown=True, new_x="LMARGIN", new_y="NEXT")
            pdf.ln(0.4)
            i += 1
            continue

        # part divider: a bold **PART …** line starts a fresh page
        if stripped.startswith("**PART "):
            if pdf.get_y() > 60:
                pdf.add_page()
            pdf.ln(2)
            pdf.set_font("body", "B", 14)
            pdf.set_text_color(*BLUE)
            pdf.multi_cell(0, 7, clean(stripped), new_x="LMARGIN", new_y="NEXT")
            pdf.set_draw_color(*BLUE)
            pdf.set_line_width(0.9)
            y = pdf.get_y() + 2
            pdf.line(17, y, 90, y)
            pdf.set_line_width(0.3)
            pdf.set_text_color(*INK)
            pdf.ln(6)
            i += 1
            continue

        # paragraph
        pdf.set_font("body", "", 9.6)
        pdf.set_text_color(*INK)
        italic = stripped.startswith("*") and stripped.endswith("*") and not stripped.startswith("**")
        if italic:
            pdf.set_text_color(*MUTED)
            pdf.set_font("body", "I", 8.8)
            pdf.multi_cell(0, 5, clean(stripped).strip("*"), new_x="LMARGIN", new_y="NEXT")
        else:
            pdf.multi_cell(0, 5.1, stripped.replace("`", ""), markdown=True, new_x="LMARGIN", new_y="NEXT")
        pdf.ln(1.6)
        i += 1

    pdf.output(str(OUT))
    print("wrote", OUT, OUT.stat().st_size, "bytes")


if __name__ == "__main__":
    build()
