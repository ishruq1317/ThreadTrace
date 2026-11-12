#!/usr/bin/env python3
"""
Runnable QA verification script (QA1-QA5) against a running ThreadTrace instance.
Usage:
  python scripts/qa/run_qa_checks.py --base-url http://localhost:8080
"""

import argparse
import base64
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import date, timedelta


def request(method, url, body=None, auth=None, headers=None):
    hdrs = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        hdrs.setdefault("Content-Type", "application/json")
    if auth is not None:
        token = base64.b64encode(f"{auth[0]}:{auth[1]}".encode("utf-8")).decode("ascii")
        hdrs["Authorization"] = f"Basic {token}"
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, dict(resp.headers), resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8")


def assert_true(cond, msg):
    if not cond:
        raise AssertionError(msg)


def main():
    parser = argparse.ArgumentParser(description="ThreadTrace QA1-QA5 Verification Runner")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--operator-user", default="operator")
    parser.add_argument("--operator-pass", default="change_me_operator_pw")
    parser.add_argument("--auditor-user", default="auditor")
    parser.add_argument("--auditor-pass", default="change_me_auditor_pw")
    parser.add_argument("--branda-user", default="brand_a")
    parser.add_argument("--branda-pass", default="change_me_branda_pw")
    parser.add_argument("--brandb-user", default="brand_b")
    parser.add_argument("--brandb-pass", default="change_me_brandb_pw")
    parser.add_argument(
        "--expect-extra-field",
        action="append",
        default=[],
        help="Optional KEY=VALUE pair expected in passport additionalFields (for CI QA5 override checks)",
    )
    args = parser.parse_args()

    base = args.base_url.rstrip("/")
    op = (args.operator_user, args.operator_pass)
    aud = (args.auditor_user, args.auditor_pass)
    brand_a = (args.branda_user, args.branda_pass)
    brand_b = (args.brandb_user, args.brandb_pass)
    suffix = str(int(time.time()))

    print("[1/6] Checking health endpoint...")
    st, _, body = request("GET", f"{base}/actuator/health")
    assert_true(st == 200, f"Health check failed: {st} {body}")

    print("[2/6] Seeding 6-level supply chain & testing QA1 trace latency...")
    mill = f"QA-MILL-{suffix}"
    fac = f"QA-FAC-{suffix}"
    st, _, b = request("POST", f"{base}/api/v1/parties", {
        "code": mill, "name": "QA Mill", "partyType": "FABRIC_MILL",
        "country": "Bangladesh", "region": "Gazipur",
        "exactAddress": "CONFIDENTIAL-ADDR-123", "workerCount": 999, "unitPriceUsd": 12.34
    }, auth=op)
    assert_true(st == 201, f"Create QA mill failed: {st} {b}")

    st, _, b = request("POST", f"{base}/api/v1/parties", {
        "code": fac, "name": "QA Factory", "partyType": "FACTORY",
        "country": "Bangladesh", "region": "Savar",
        "exactAddress": "CONFIDENTIAL-FAC-456", "workerCount": 1500, "unitPriceUsd": 8.50
    }, auth=op)
    assert_true(st == 201, f"Create QA factory failed: {st} {b}")

    valid_from = (date.today() - timedelta(days=30)).isoformat()
    valid_to = (date.today() + timedelta(days=15)).isoformat()
    st, _, b = request("POST", f"{base}/api/v1/evidence/certificates", {
        "certificateNumber": f"QA-CERT-{suffix}",
        "certificateType": "GRS",
        "issuer": "Control Union",
        "scope": "Recycled Polyester",
        "claimedAttribute": "RECYCLED",
        "certifiedPct": 60.0,
        "supplierCode": mill,
        "validFrom": valid_from,
        "validTo": valid_to,
    }, auth=op)
    assert_true(st == 201, f"Register GRS certificate failed: {st} {b}")

    l0 = f"QA-L0-{suffix}"
    st, _, b = request("POST", f"{base}/api/v1/events/receive", {
        "lotCode": l0, "lotType": "RAW_FIBER", "materialType": "Recycled Polyester",
        "styleCode": "QA-ST-1", "brandCode": "BRAND_A", "supplierCode": mill,
        "factoryCode": fac, "quantity": 100.0, "massKg": 100.0, "pieceCount": 0,
        "unit": "KG", "claimedAttribute": "RECYCLED", "claimedPct": 60.0
    }, auth=op)
    assert_true(st == 201, f"Receive raw fiber event failed: {st} {b}")

    prev = l0
    steps = [
        ("CUT", "CUT_BUNDLE"),
        ("SEW", "SEWN_GARMENT"),
        ("WASH_OR_DYE", "WASHED_GARMENT"),
        ("FINISH", "FINISHED_GARMENT"),
        ("PACK", "CARTON"),
        ("SHIP", "SHIPMENT"),
    ]
    for idx, (ev_type, lot_type) in enumerate(steps, start=1):
        nxt = f"QA-L{idx}-{suffix}"
        st, _, b = request("POST", f"{base}/api/v1/events/transform", {
            "inputLotCode": prev, "outputLotCode": nxt, "outputLotType": lot_type,
            "eventType": ev_type, "factoryCode": fac,
            "inputQuantity": 100.0, "outputQuantity": 96.0, "outputMassKg": 96.0,
            "outputPieceCount": 200, "unit": "KG"
        }, auth=op)
        assert_true(st == 201, f"Transform {ev_type} failed: {st} {b}")
        prev = nxt

    t0 = time.perf_counter()
    st, _, b = request("GET", f"{base}/api/v1/traces/backward/{prev}?maxDepth=6", auth=brand_a)
    elapsed_ms = (time.perf_counter() - t0) * 1000
    assert_true(st == 200 and elapsed_ms < 2000, f"QA1 backward trace failed or slow: {st}, {elapsed_ms:.1f}ms")
    print(f"    QA1 6-level backward trace completed in {elapsed_ms:.1f} ms")

    print("[3/6] Testing QA2 planted problems (over-claim, cutting loss, orphan lot)...")
    st, _, b = request("POST", f"{base}/api/v1/events/transform", {
        "inputLotCode": l0, "outputLotCode": f"QA-OVERLOSS-{suffix}", "outputLotType": "CUT_BUNDLE",
        "eventType": "CUT", "processStep": "cut", "factoryCode": fac,
        "inputQuantity": 100.0, "outputQuantity": 70.0, "outputMassKg": 70.0,
        "outputPieceCount": 100, "unit": "KG"
    }, auth=op)
    assert_true(st == 201 and json.loads(b)["status"] == "FLAGGED", f"Expected FLAGGED for excess cutting loss: {b}")

    st, _, b = request("POST", f"{base}/api/v1/events/receive", {
        "lotCode": f"QA-ORPHAN-{suffix}", "lotType": "FABRIC", "materialType": "Cotton",
        "brandCode": "BRAND_B", "quantity": 10.0, "massKg": 10.0, "pieceCount": 0, "unit": "KG"
    }, auth=op)
    assert_true(st == 201 and json.loads(b)["orphan"] is True, f"Expected orphan=true: {b}")

    print("[4/6] Generating passport & testing QA3 confidentiality + QA4 delivery...")
    pp_code = f"QA-PP-{suffix}"
    st, _, b = request("POST", f"{base}/api/v1/passports", {
        "passportCode": pp_code, "shipmentLotCode": prev, "styleCode": "QA-ST-1",
        "styleName": "QA Recycled Jacket", "careAndRepair": "Cold wash", "endOfLifeGuidance": "Recycle"
    }, auth=op)
    assert_true(st == 201, f"Passport creation failed: {st} {b}")

    pub_json = None
    for pub_url in (f"{base}/api/v1/public/passports/{pp_code}", f"{base}/p/{pp_code}"):
        t0 = time.perf_counter()
        st, hdrs, pub_body = request("GET", pub_url)
        dt_ms = (time.perf_counter() - t0) * 1000
        assert_true(st == 200 and dt_ms < 2000, f"Public passport failed or slow ({dt_ms:.1f}ms): {pub_url}")
        for secret in ("CONFIDENTIAL-ADDR-123", "CONFIDENTIAL-FAC-456", "workerCount", "unitPriceUsd"):
            assert_true(secret not in pub_body, f"Confidential field leaked in {pub_url}: {secret}")
        if pub_url.endswith(f"/api/v1/public/passports/{pp_code}"):
            pub_json = json.loads(pub_body)

    st, _, _ = request("GET", f"{base}/api/v1/lots/QA-ORPHAN-{suffix}", auth=brand_a)
    assert_true(st == 403, f"Cross-brand lot access was not refused! Got {st}")

    st, _, _ = request("GET", f"{base}/api/v1/passports/{pp_code}", auth=brand_b)
    assert_true(st == 403, f"Cross-brand passport access was not refused! Got {st}")

    print("[5/6] Testing QA5 config-driven adaptability (extra-fields & allowed certificate types)...")
    additional_fields = (pub_json or {}).get("additionalFields") or {}
    assert_true(
        additional_fields.get("eu-dpp-regulation") == "EU ESPR Textile DPP Draft"
        and additional_fields.get("care-standard") == "ISO 3758 Care Labelling",
        f"Expected configured extra-fields on passport, got: {additional_fields}",
    )
    for pair in args.expect_extra_field:
        assert_true("=" in pair, f"Invalid --expect-extra-field '{pair}', expected KEY=VALUE")
        k, v = pair.split("=", 1)
        assert_true(
            additional_fields.get(k) == v,
            f"Expected configured extra-field {k}={v}, got {additional_fields.get(k)}",
        )

    # QA5 Positive: Register custom configured certificate type (CUSTOM_MILL_CERT) without code modifications
    st, _, b = request("POST", f"{base}/api/v1/evidence/certificates", {
        "certificateNumber": f"QA-CUSTOM-CERT-{suffix}",
        "certificateType": "CUSTOM_MILL_CERT",
        "issuer": "Custom Audit Mill Body",
        "scope": "QA5 Configured Type Test",
        "supplierCode": mill,
        "validFrom": valid_from,
        "validTo": valid_to,
    }, auth=op)
    assert_true(st == 201, f"Expected 201 Created for configured custom certificateType, got {st}: {b}")

    # QA5 Negative: Register unconfigured certificate type, asserting HTTP 422 Domain Rule Violation
    st, _, b = request("POST", f"{base}/api/v1/evidence/certificates", {
        "certificateNumber": f"QA-UNSUPPORTED-{suffix}",
        "certificateType": "UNCONFIGURED_TYPE_QA5",
        "issuer": "Unknown Body",
        "scope": "QA5 Negative Test",
        "supplierCode": mill,
        "validFrom": valid_from,
        "validTo": valid_to,
    }, auth=op)
    assert_true(
        st == 422 and "Unsupported certificateType" in b,
        f"Expected HTTP 422 Domain Rule Violation for unconfigured certificateType, got {st}: {b}",
    )

    print("[6/6] Verifying FR10 hash-chain integrity...")
    st, _, b = request("GET", f"{base}/api/v1/integrity/passports/{pp_code}/verify", auth=aud)
    assert_true(st == 200 and json.loads(b)["verified"] is True, f"Integrity check failed: {b}")

    print("ALL QA CHECKS PASSED!")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"QA CHECK FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
