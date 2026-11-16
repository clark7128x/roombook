"""Evaluate stored TS-22 evidence without turning a shortened run into acceptance."""
import json
import sys
from pathlib import Path


def evaluate(report):
    results = report["results"]
    checks = []
    def check(name, passed, detail):
        checks.append({"criterion":name,"result":"PASS" if passed else "FAIL","detail":detail})
    check("Full profile", report["profile"] == "full", report["profile"])
    unexpected = [row for row in results if row["status"] not in (
        [200] if row["method"] == "GET" or row["path"].endswith("/cancel") else
        [302] if row["path"] == "/login" else [201,409])]
    check("Expected response statuses", not unexpected, unexpected)
    for stage, expected in [("P1",27000),("P2",36000),("P3",9900),("P6",108000)]:
        count = sum(row["count"] for row in results if row["stage"] == stage)
        check(stage+" completed request count", count == expected, {"actual":count,"expected":expected})
    for stage in ["P1","P3"]:
        paths = {row["path"] for row in results if row["stage"] == stage and row["method"] == "GET" and row["status"] == 200}
        missing = sorted({"/occupancy","/api/v1/occupancy","/api/v1/me/bookings","/api/v1/rooms","/fragments/room-day","/admin/bookings"}-paths)
        check(stage+" read-route coverage", not missing, missing)
    pages = [row for row in results if row["stage"] == "P5" and row["path"].startswith("/api/v1/me/bookings?page=") and row["status"] == 200]
    page_numbers = sorted(int(row["path"].split("=")[-1]) for row in pages)
    check("P5 contiguous own pages with 100 samples each", bool(pages) and page_numbers == list(range(len(pages))) and all(row["count"] == 100 for row in pages),len(pages))
    measured = [row for row in results if
                (row["stage"] == "P1" and row["method"] == "GET" and row["path"] in ["/occupancy","/api/v1/occupancy","/api/v1/me/bookings"]) or
                (row["stage"] in ["P1","P4"] and row["method"] == "POST" and row["path"] == "/api/v1/bookings" and row["status"] in [201,409]) or
                (row["stage"] == "P3" and row["method"] == "GET") or
                (row["stage"] == "P5" and row["path"].startswith("/api/v1/me/bookings?page="))]
    for row in measured:
        check(f'{row["stage"]} {row["method"]} {row["path"]} {row["status"]}', row["p95ServerMs"] <= 300,
              {"p95Ms":row["p95ServerMs"],"limitMs":300,"samples":row["count"]})
    check("No server errors or client timeouts", report["errors5xx"] == 0, report["errors5xx"])
    check("All server timings matched", report["missingServerDurations"] == 0, report["missingServerDurations"])
    check("P4 all 20 repetitions", len(report["p4Checks"]) == 20 and all(
        row["sameSlot"] == {"201":1,"409":49} and row["differentSlots"] == 50 for row in report["p4Checks"]), len(report["p4Checks"]))
    check("Invariant audit", report["invariantAudit"] == "PASS", report["invariantAudit"])
    metrics = {row["minute"]:row for row in report["metrics"] if row["stage"] == "P6"}
    if 10 not in metrics or 60 not in metrics or not all(metrics[minute].get("heapAfterGc") for minute in [10,60]):
        checks.append({"criterion":"P6 after-GC heap","result":"INCONCLUSIVE","detail":"Need nonzero minute 10 and 60 samples"})
    else:
        growth = (metrics[60]["heapAfterGc"]-metrics[10]["heapAfterGc"])/metrics[10]["heapAfterGc"]*100
        if len({row.get("heapAfterGc") for row in metrics.values()}) == 1:
            checks.append({"criterion":"P6 after-GC heap","result":"INCONCLUSIVE",
                           "detail":"Live-data metric stayed identical throughout P6; fresh post-GC samples at minutes 10 and 60 are not proven."})
        else:
            check("P6 after-GC heap", growth <= 10, {"growthPercent":round(growth,2),"limitPercent":10})
    return {"result":"PASS" if all(row["result"] == "PASS" for row in checks) else "NOT_ACCEPTED",
            "checks":checks,"boundary":report["boundary"],
            "manualReview":"Review pool exhaustion events, expected authenticated/anonymous session counts and P5 completeness. API read timings do not prove HTML /rooms and /bookings/mine rendering performance."}


if __name__ == "__main__":
    path = Path(sys.argv[1])
    result = evaluate(json.loads(path.read_text(encoding="utf-8")))
    output = path.with_name(path.stem+"-acceptance.json")
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    print(result["result"],output)
