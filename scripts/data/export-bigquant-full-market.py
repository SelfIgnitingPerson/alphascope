"""在具备数据导出许可的 BigQuant 环境生成 AlphaScope 逐日全 A 历史档案。"""

import argparse
import json
import re
from datetime import date, datetime, timedelta, timezone
from pathlib import Path


def exchange(symbol):
    return {".SH": "SSE", ".SZ": "SZSE", ".BJ": "BSE"}[symbol[-3:]]


def stock(symbol):
    return re.fullmatch(r"(?:6\d{5}\.SH|(?:00|30)\d{4}\.SZ|[489]\d{5}\.BJ)", symbol)


def export_range(start, end, output, evidence):
    try:
        import dai
    except ModuleNotFoundError as error:
        if error.name != "dai":
            raise
        from bigquant import dai
    output.mkdir(parents=True, exist_ok=True)
    count = 0
    cursor = start
    exported_at = datetime.now(timezone.utc).isoformat()
    while cursor <= end:
        last_day = min(cursor + timedelta(days=14), end)
        archives = read_batch(dai, cursor, last_day, exported_at, evidence)
        if last_day == end and str(end) not in archives:
            raise ValueError(f"目标日 {end} 尚未取得供应商名册，请核对日期和数据发布状态")
        for day, archive in archives.items():
            path = output / f"{day}.json"
            temporary = path.with_suffix(".tmp")
            temporary.write_text(json.dumps(archive, ensure_ascii=False), encoding="utf-8")
            temporary.replace(path)
        count += len(archives)
        cursor = last_day + timedelta(days=1)
    print(f"导出 {count} 日到 {output}")


def read_batch(dai, start, end, exported_at, evidence):
    import pandas as pd

    date_filter = {"date": [str(start), str(end)]}
    where = f"WHERE date >= DATE '{start}' AND date < DATE '{end + timedelta(days=1)}'"
    directory = dai.query(
        f"SELECT date, instrument FROM cn_stock_instruments {where} ORDER BY date, instrument",
        filters=date_filter,
    ).df()
    prices = dai.query(
        "SELECT date, instrument, close, pre_close, amount, adjust_factor "
        f"FROM cn_stock_real_bar1d {where} ORDER BY date, instrument",
        filters=date_filter,
    ).df()
    archives = {}
    for row in directory.itertuples(index=False):
        if not stock(row.instrument):
            continue
        day = str(pd.Timestamp(row.date).date())
        if not (str(start) <= day <= str(end)):
            raise ValueError(f"名册含目标范围外日期 {day} {row.instrument}")
        if day not in evidence:
            raise ValueError(f"{day} 缺少独立交易所名册与停牌证据")
        proof = evidence[day]
        archive = archives.setdefault(day, {
            "date": day, "source": "BIGQUANT", "exportedAt": exported_at,
            "expectedCounts": proof["expectedCounts"], "rosterSources": proof["rosterSources"],
            "members": [], "quotes": [],
        })
        suspension = proof["suspensions"].get(row.instrument)
        archive["members"].append({
            "symbol": row.instrument, "suspended": suspension is not None, "suspension": suspension,
        })

    def number(value):
        return None if pd.isna(value) else str(value)

    for row in prices.itertuples(index=False):
        if not stock(row.instrument):
            continue
        day = str(pd.Timestamp(row.date).date())
        if day not in archives:
            raise ValueError(f"行情日期 {day} 缺少同日名册")
        archives[day]["quotes"].append({
            "symbol": row.instrument, "close": number(row.close),
            "previousClose": number(row.pre_close), "amount": number(row.amount),
            "factor": number(row.adjust_factor),
        })
    expected_days = {day for day in evidence if str(start) <= day <= str(end)}
    if missing_days := expected_days - archives.keys():
        raise ValueError(f"供应商缺少已核对日期的名册：{', '.join(sorted(missing_days))}")
    for day, archive in archives.items():
        symbols = {member["symbol"] for member in archive["members"]}
        counts = {name: sum(exchange(symbol) == name for symbol in symbols)
                  for name in ("SSE", "SZSE", "BSE")}
        if counts != archive["expectedCounts"] or len(symbols) != len(archive["members"]):
            raise ValueError(f"{day} 供应商名册与独立三所统计不一致：{counts}")
        if evidence[day]["suspensions"].keys() - symbols or not archive["quotes"]:
            raise ValueError(f"{day} 有名册外停牌证据或缺整日行情")
    return archives


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("start", type=date.fromisoformat)
    parser.add_argument("end", type=date.fromisoformat)
    parser.add_argument("--evidence", type=Path, required=True, help="独立逐日交易所证据 JSON")
    parser.add_argument("--output", type=Path, default=Path("full-market-import"))
    args = parser.parse_args()
    if args.end < args.start or (args.end - args.start).days > 210:
        parser.error("按所需窗口导出，单个批次最多跨 210 个自然日")
    evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    export_range(args.start, args.end, args.output, evidence)
