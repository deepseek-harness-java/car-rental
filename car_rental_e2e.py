#!/usr/bin/env python3
"""car-rental E2E：通过业务应用 SSE 代理调用 DSH Agent，验证 7 个工具全链路。"""
import json, subprocess, sys

AGENT = "rental-copilot"
URL = "http://127.0.0.1:18104/api/assistant/stream"

CASES = [
    ("T1 车辆列表", "租车门店有哪些车可以租？新能源车还在吗？简洁回答", ["经济轿车", "新能源"]),
    ("T2 门店列表", "租车行三家门店都在哪？哪家有车可租？简洁回答", ["机场店", "市中心店"]),
    ("T3 租车下单", "我是测试顾客李明，电话13800001111，想租机场店的舒适SUV，明天上午10点取，租2天，下单租车。告诉我订单号和押金", ["O9", "舒适SUV"]),
    ("T4 订单查询", "查一下租车订单 O9001 的信息，谁租的？简洁回答", ["陈先生", "新能源"]),
    ("T5 报价计算", "租经济轿车3天多少钱？简洁回答", ["168", "504"]),
    ("T6 维保查询", "租车行哪些车在保养维修？简洁回答", ["保养"]),
    ("T7 运营统计", "租车行今天运营情况怎么样？出租率多少？简洁回答", ["可租", "出租率"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== car-rental E2E: {passed}/{len(cases)} PASS =====")
    if failed:
        print("失败用例:", ", ".join(failed))
        sys.exit(1)

if __name__ == "__main__":
    main()
