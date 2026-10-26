#!/usr/bin/env python3
"""Convert pre-extracted paper-page text into a searchable question bank."""
import hashlib
import json
import re
import sys
from pathlib import Path

QUESTION = re.compile(r"(?m)^\s*Q\s*(\d{1,3})\s*\.\s*")
SUBJECTS = ("Physics", "Chemistry", "Mathematics")
BOILERPLATE = re.compile(
    r"^(?:ExamGOAL|PYQ PDF|Open solution|Keep practising on the ExamGOAL app|"
    r"Chapter-wise PYQs with solutions, full mock tests and analysis.*|"
    r"(?:JEE|AIEEE|IIT-JEE).*pdf\.questions\.examgoal\.com.*|"
    r"Solutions: questions\.examside\.com.*|GET IT ON|Google Play|\d+\s*/\s*\d+)\s*$",
    re.IGNORECASE,
)


def metadata(filename):
    title = filename.removesuffix(".pdf")
    year_match = re.search(r"(?:19|20)\d{2}", title)
    if "advanced" in title.lower() or "iit-jee" in title.lower():
        exam = "JEE Advanced"
    elif "main" in title.lower() or "aIEEE".lower() in title.lower():
        exam = "JEE Main"
    else:
        exam = "JEE"
    return title, exam, int(year_match.group()) if year_match else 0


def clean(text):
    text = re.sub(r"\s*Open solution\b", "", text)
    lines = []
    for line in text.splitlines():
        line = re.sub(r"\s+", " ", line).strip()
        if not line or BOILERPLATE.match(line):
            continue
        lines.append(line)
    return "\n".join(lines).strip()


def subject_for(text, default):
    found = re.findall(r"(?:·|\b)(Physics|Chemistry|Mathematics)\b", text, re.IGNORECASE)
    if not found:
        return default
    candidate = found[-1].lower()
    return next(s for s in SUBJECTS if s.lower() == candidate)


def main(source, output):
    documents = []
    total_questions = 0
    for line in source.open(encoding="utf-8"):
        paper = json.loads(line)
        filename = paper["sourceFile"]
        title, exam, year = metadata(filename)
        pages = paper.get("pages", [])
        full_text = "\n".join(page.get("text", "") for page in pages)
        page_ranges = []
        page_subject = ""
        page_offset = 0
        for page in pages:
            page_text = page.get("text", "")
            heading = re.search(r"(?im)^\s*(Physics|Chemistry|Mathematics)\s+\d+\s+questions\b", page_text)
            if heading:
                page_subject = heading.group(1)
            page_ranges.append((page_offset, page_offset + len(page_text), page_subject))
            page_offset += len(page_text) + 1
        markers = list(QUESTION.finditer(full_text))
        questions = []
        current_subject = ""
        page_index = 0
        for i, match in enumerate(markers):
            while page_index + 1 < len(page_ranges) and match.start() >= page_ranges[page_index][1]:
                page_index += 1
            if page_ranges:
                current_subject = page_ranges[page_index][2] or current_subject
            start = match.end()
            end = markers[i + 1].start() if i + 1 < len(markers) else len(full_text)
            raw = full_text[start:end]
            # Paper page headers and key pages follow the final question; never absorb them.
            raw = re.split(r"(?im)^\s*For numerical answers given as a range", raw, maxsplit=1)[0]
            raw = re.split(r"(?im)^\s*Keep practising on the ExamGOAL app", raw, maxsplit=1)[0]
            label_subject = re.search(r"·\s*(Physics|Chemistry|Mathematics)\b", raw[:500], re.IGNORECASE)
            if label_subject:
                current_subject = next(s for s in SUBJECTS if s.lower() == label_subject.group(1).lower())
            body = clean(raw)
            # Drop repeated document/page headers while retaining question wording and choices.
            body = re.sub(r"(?im)^JEE (?:Main|Advanced) \d{4}.*Page \d+ / \d+\s*$", "", body)
            body = re.sub(r"(?im)^\d+\s*/\s*\d+\s*$", "", body).strip()
            if not body:
                body = "Question text or diagram is not available in extracted text."
            qnum = int(match.group(1))
            qid = hashlib.sha256(f"{paper.get('sha256', filename)}:{qnum}:{i}".encode()).hexdigest()[:16]
            questions.append({
                "id": qid,
                "number": qnum,
                "exam": exam,
                "year": year,
                "paper": title,
                "subject": current_subject or "Uncategorized",
                "text": body,
            })
        if questions:
            documents.append({"source": filename, "questions": questions})
            total_questions += len(questions)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"documents": documents}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    empty = sum(1 for doc in documents for q in doc["questions"] if q["text"] == "Question text or diagram is not available in extracted text.")
    print(f"Papers imported: {len(documents)}; questions: {total_questions}; empty text placeholders: {empty}; output bytes: {output.stat().st_size}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: import_questions.py extracted-pages.jsonl output.json")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
