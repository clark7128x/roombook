"""Generate traceability without treating a tag as proof that acceptance passed."""
import collections
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
required = [line.strip() for line in (ROOT/"src/test/resources/required-ids.txt").read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")]
assert len(required) == len(set(required)), "Duplicate required ID"
tagged = collections.defaultdict(list)
for path in sorted((ROOT/"src/test/java").rglob("*.java"), key=lambda path: path.relative_to(ROOT).as_posix()):
    # Fixture strings are examples, not annotations on executable tests.
    source = re.sub(r'""".*?"""', '', path.read_text(encoding="utf-8"), flags=re.S)
    for tag in set(re.findall(r'@Tag\s*\(\s*"([A-Z]+-\d+)"\s*\)',source)):
        tagged[tag].append(path.relative_to(ROOT).as_posix())
assert not (set(tagged)-set(required)), sorted(set(tagged)-set(required))
missing = [key for key in required if key not in tagged]
lines = ["# Індекс доказів", "", "Згенеровано `python ops/audit/coverage.py`. Наявність тегу показує зв'язок із тестовим класом, а не результат приймання всіх положень ID.", "",
         f"Зареєстровано {len(required)} ID; теги є для {len(set(required)&set(tagged))}; без Java-тегу: {len(missing)}.", "",
         "FR-24 не входить у реалізацію: етап 6, Q-04 явно відхиляє самостійну зміну пароля. Індекс доповнено A-66…A-72 з етапу 9. Документи дизайну не змінювалися.", "",
         "TS-22…TS-26 мають окремі експлуатаційні й браузерні звіти. Припущення про інфраструктуру та рішення власника не можна підтвердити додаванням тегів. Критерій T-30 «тест із тегом на кожний ID» залишається невиконаним, доки наведені прогалини не усунуто або їхній спосіб приймання явно не погоджено.", "",
         "## ID без Java-тегу", "", ", ".join(missing), "", "## Автоматичні тести", "", "| ID | Класи з тегом |", "|---|---|"]
for key in required:
    if key in tagged:
        links = [f"[{Path(file).stem}](../{file})" for file in tagged[key]]
        lines.append(f"| {key} | {', '.join(links)} |")
(ROOT/"docs/coverage-evidence.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
print(f"{len(required)} IDs; {len(missing)} without Java tags")
