# -*- coding: utf-8 -*-
"""從 02.srt.bak 出發，結合 ASR 逐字時間戳，嚴格落實專案字幕斷句規範。"""
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def parse_time(t_str):
    m = re.match(r"(\d+):(\d+):(\d+),(\d+)", t_str)
    return int(m[1]) * 3600 + int(m[2]) * 60 + int(m[3]) + int(m[4]) / 1000

def fmt_time(seconds: float) -> str:
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    ms = int(round((seconds - int(seconds)) * 1000))
    if ms >= 1000:
        ms = 999
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

def load_bak_cues():
    bak_path = ROOT / "course-package/ch01-env-and-ai-workflow/02.srt.bak"
    blocks = bak_path.read_text(encoding="utf-8").strip().split("\n\n")
    cues = []
    for b in blocks:
        lines = [l.strip() for l in b.split("\n") if l.strip()]
        if len(lines) >= 3:
            t_parts = lines[1].split(" --> ")
            cues.append({
                "num": int(lines[0]),
                "start": parse_time(t_parts[0]),
                "end": parse_time(t_parts[1]),
                "text": " ".join(lines[2:])
            })
    return cues

def clean_text_terms(t: str) -> str:
    """統一專有名詞與修飾語。"""
    replacements = [
        ("SpringInitializr", "Spring Initializr"),
        ("Spring Initializr", "Spring Initializr"),
        ("SpringBoot", "Spring Boot"),
        ("SpringDataJPA", "Spring Data JPA"),
        ("Spring Data JPA", "Spring Data JPA"),
        ("PostgreSQLDriver", "PostgreSQL Driver"),
        ("PostgreSQL", "PostgreSQL"),
        ("ClaudeCode", "Claude Code"),
        ("Claude Code", "Claude Code"),
        ("VSCode", "VS Code"),
        ("VS Code", "VS Code"),
        ("Antigravity", "Antigravity"),
        ("GeminiPro", "Gemini Pro"),
        ("Gemini Pro", "Gemini Pro"),
        ("H2 Database", "H2 Database"),
        ("H2Database", "H2 Database"),
        ("Flyway Migration", "Flyway Migration"),
        ("FlywayMigration", "Flyway Migration"),
        ("Open Folder", "Open Folder"),
        ("Open　Folder", "Open Folder"),
        ("Spring Web", "Spring Web"),
        ("Spring　Web", "Spring Web"),
        ("Spring　Data JPA", "Spring Data JPA"),
        ("AI agent", "AI Agent"),
        ("git init", "git init"),
        ("git status", "git status"),
        (".gitignore", ".gitignore"),
        ("gitignore", ".gitignore"),
        (".env", ".env"),
        ("monorepo", "monorepo"),
        ("BUILD SUCCESS", "BUILD SUCCESS"),
        ("pom.xml", "pom.xml"),
        ("application.properties", "application.properties"),
        ("application.yml", "application.yml"),
        ("src/main/java", "src/main/java"),
        ("src/main", "src/main"),
        ("src/test", "src/test"),
        ("Artifact", "Artifact"),
        ("Group ID", "Group ID"),
        ("groupID", "Group ID"),
        ("SNAPSHOT", "SNAPSHOT"),
        ("snapshot", "SNAPSHOT"),
    ]
    for old, new in replacements:
        t = re.sub(re.escape(old), new, t, flags=re.IGNORECASE)
    return t

def main():
    cues = load_bak_cues()
    print(f"Loaded {len(cues)} cues from 02.srt.bak")

if __name__ == "__main__":
    main()
