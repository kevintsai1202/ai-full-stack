# -*- coding: utf-8 -*-
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent

def scan_bak():
    blocks = (ROOT / "course-package/ch01-env-and-ai-workflow/02.srt.bak").read_text(encoding="utf-8").strip().split("\n\n")
    print(f"Total blocks in bak: {len(blocks)}")
    
    keywords = [
        "環境的建置", "Antigravity", "Open Folder", "ai-crm", "中文字", "空格",
        "檔案資料夾", "終端機", "Ctrl+J", "Spring Initializr", "Maven",
        "4.0", "4.1.0", "Java", "21", "Group", "Artifact", "JAR", "WAR", "Tomcat",
        "Spring Web", "Spring Data JPA", "JDBC", "Flyway", "PostgreSQL", "H2",
        "monorepo", "backend", "frontend", "pom.xml", "Spring Security", "src/main",
        "TDD", "application.properties", "YAML", "clean", "compile", "BUILD SUCCESS",
        "git init", "gitignore", ".env", "AI協作", "適用時機", "試用時機", "花錢", "再見"
    ]
    
    for kw in keywords:
        found = []
        for i, b in enumerate(blocks, start=1):
            if kw.lower() in b.lower():
                found.append(i)
        if found:
            print(f"Keyword '{kw}': lines {found[:5]} (total {len(found)})")

if __name__ == "__main__":
    scan_bak()
