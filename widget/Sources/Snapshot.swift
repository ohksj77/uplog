// 엔진이 쓰는 위젯 스냅샷({data-dir}/widget/widget.json). 형식은 docs/design/desktop-app.md "위젯".
import Foundation

struct Snapshot: Decodable, Sendable {
    struct Today: Decodable, Sendable {
        let pnl: Int64
        let `return`: Double
    }

    struct Tally: Decodable, Sendable {
        let pnl: Int64
        let gainDays: Int
        let lossDays: Int
    }

    struct Streak: Decodable, Sendable {
        /// "GAIN" | "LOSS" | nil
        let kind: String?
        let length: Int
    }

    struct Day: Decodable, Sendable, Hashable {
        /// "YYYY-MM-DD"
        let date: String
        /// −4…4, nil = 기록 없음·휴장·미래
        let level: Int?
        let pnl: Int64?
    }

    struct Week: Decodable, Sendable, Hashable {
        /// 월요일 "YYYY-MM-DD"
        let start: String
        /// 월–금 5칸
        let days: [Day]
    }

    let version: Int
    let generatedAt: String
    let asOf: String?
    let accountName: String
    let sample: Bool
    let today: Today?
    let month: Tally
    /// v1 초기에 없던 필드라 없을 수도 있다.
    let year: Tally?
    let streak: Streak
    let weeks: [Week]
    let thresholds: [Int64]
}

enum SnapshotStore {
    /// 샌드박스 안에서는 NSHomeDirectory()가 컨테이너를 가리키므로 실제 홈은 passwd에서 읽는다.
    static func realHome() -> String {
        if let pw = getpwuid(getuid()), let dir = pw.pointee.pw_dir {
            return String(cString: dir)
        }
        return NSHomeDirectory()
    }

    /// 엔진 기본 data-dir(~/Library/Application Support/uplog)의 widget/widget.json.
    /// 위젯 entitlement(home-relative-path.read-only)가 이 폴더만 읽게 해 준다.
    static var fileURL: URL {
        URL(fileURLWithPath: realHome())
            .appendingPathComponent("Library/Application Support/uplog/widget/widget.json")
    }

    static func load(from url: URL = fileURL) -> Snapshot? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }
}

// MARK: - 문구

enum Format {
    private static let grouping: NumberFormatter = {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "ko_KR")
        f.maximumFractionDigits = 0
        return f
    }()

    /// 123400 → "+123,400원", 0 → "0원" (web formatSignedWon과 같다)
    static func signedWon(_ v: Int64) -> String {
        let body = grouping.string(from: NSNumber(value: v)) ?? "\(v)"
        return (v > 0 ? "+" : "") + body + "원"
    }

    /// 1억 이상 "+1.2억", 1만 이상 "+12만", 그 아래 "+3,400" — 좁은 칸용
    static func signedWonShort(_ v: Int64) -> String {
        let sign = v > 0 ? "+" : v < 0 ? "-" : ""
        let a = Double(abs(v))
        if a >= 100_000_000 { return sign + String(format: a >= 1_000_000_000 ? "%.0f억" : "%.1f억", a / 100_000_000) }
        if a >= 10_000 { return sign + (grouping.string(from: NSNumber(value: (a / 10_000).rounded())) ?? "") + "만" }
        return sign + (grouping.string(from: NSNumber(value: a)) ?? "") + "원"
    }

    /// 0.0123 → "+1.23%" (web formatPercent와 같다)
    static func percent(_ rate: Double) -> String {
        let s = String(format: "%.2f", rate * 100)
        let isZero = (Double(s) ?? 0) == 0
        return (rate > 0 && !isZero ? "+" : "") + s + "%"
    }

    static let seoul = TimeZone(identifier: "Asia/Seoul")!

    static func date(_ iso: String) -> Date? {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.timeZone = seoul
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f.date(from: iso)
    }

    static func monthDay(_ iso: String) -> String {
        let p = iso.split(separator: "-")
        guard p.count == 3, let m = Int(p[1]), let d = Int(p[2]) else { return iso }
        return "\(m)월 \(d)일"
    }

    static func month(_ iso: String) -> Int? {
        let p = iso.split(separator: "-")
        return p.count == 3 ? Int(p[1]) : nil
    }

    static func todayISO(_ now: Date = .now) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = seoul
        let c = cal.dateComponents([.year, .month, .day], from: now)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    /// 마지막 기록일이 오늘이면 "오늘", 아니면 "10월 16일"
    static func dayTitle(_ asOf: String, now: Date = .now) -> String {
        asOf == todayISO(now) ? "오늘" : monthDay(asOf)
    }
}

extension Snapshot {
    /// 스크린리더 요약: "올해 n일 벌고 m일 잃었어요, 오늘 손익 +12,300원(+0.45%)"
    func accessibilitySummary(now: Date = .now) -> String {
        var parts: [String] = []
        if sample { parts.append("샘플 계좌") }
        parts.append(accountName)
        let tally = year ?? month
        parts.append("\(year == nil ? "이번 달" : "올해") \(tally.gainDays)일 벌고 \(tally.lossDays)일 잃었어요")
        if let today, let asOf {
            parts.append("\(Format.dayTitle(asOf, now: now)) 손익 \(Format.signedWon(today.pnl)), 수익률 \(Format.percent(today.return))")
        } else {
            parts.append("아직 기록이 없어요")
        }
        if let kind = streak.kind, streak.length > 0 {
            parts.append("\(streak.length)일 연속 \(kind == "GAIN" ? "벌었어요" : "잃었어요")")
        }
        return parts.joined(separator: ", ")
    }
}
