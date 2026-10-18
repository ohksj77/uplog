// 위젯 화면(SwiftUI만 쓴다 — WidgetKit 없이 tools/render로 PNG를 뽑을 수 있게).
import SwiftUI

enum LogFamily: Sendable {
    case small, medium, large

    /// 격자에 그리는 최근 주 수
    var weeks: Int {
        switch self {
        case .small: 8
        case .medium: 18
        case .large: 26
        }
    }
}

// MARK: - 격자

struct LogGrid: View {
    let weeks: [Snapshot.Week]
    var dayLabels = false
    var monthLabels = false
    var gap: CGFloat = 3
    var maxCell: CGFloat = 22

    @Environment(\.colorScheme) private var scheme

    private static let rowLabels = ["월", "", "수", "", "금"]
    private var labelWidth: CGFloat { dayLabels ? 13 : 0 }
    private var monthHeight: CGFloat { monthLabels ? 12 : 0 }

    var body: some View {
        GeometryReader { geo in
            let cols = CGFloat(max(1, weeks.count))
            let byWidth = (geo.size.width - labelWidth - gap * (cols - 1)) / cols
            let byHeight = (geo.size.height - monthHeight - gap * 4) / 5
            let cell = max(2, min(maxCell, byWidth, byHeight))
            let radius = max(1.5, cell * 0.22)
            let palette = Palette.of(scheme)
            VStack(alignment: .leading, spacing: 0) {
                if monthLabels {
                    monthRow(cell: cell)
                        .frame(maxWidth: .infinity, maxHeight: monthHeight, alignment: .topLeading)
                }
                HStack(alignment: .top, spacing: gap) {
                    if dayLabels {
                        VStack(alignment: .leading, spacing: gap) {
                            ForEach(0..<5, id: \.self) { r in
                                Text(Self.rowLabels[r])
                                    .font(.system(size: min(9, cell * 0.75)))
                                    .foregroundStyle(.secondary)
                                    .frame(width: labelWidth - gap, height: cell, alignment: .leading)
                            }
                        }
                    }
                    ForEach(weeks, id: \.start) { week in
                        VStack(spacing: gap) {
                            ForEach(0..<5, id: \.self) { r in
                                LogCell(level: r < week.days.count ? week.days[r].level : nil,
                                        size: cell, radius: radius, palette: palette)
                            }
                        }
                    }
                }
            }
            .frame(width: labelWidth + cols * cell + (cols - 1) * gap, alignment: .leading)
            .frame(width: geo.size.width, height: geo.size.height, alignment: .bottomLeading)
        }
        .accessibilityHidden(true)
    }

    /// 달이 바뀌는 열(그 주 금요일의 달 기준) 위에 "n월". 첫 라벨이 다음 라벨과 3열 안으로 붙으면 첫 라벨을 버린다.
    private func monthRow(cell: CGFloat) -> some View {
        var labels: [(Int, String)] = []
        var prev: Int?
        for (c, week) in weeks.enumerated() {
            guard let m = week.days.last.flatMap({ Format.month($0.date) }), m != prev else { continue }
            labels.append((c, "\(m)월"))
            prev = m
        }
        if labels.count > 1, labels[1].0 - labels[0].0 < 3 { labels.removeFirst() }
        return ZStack(alignment: .topLeading) {
            ForEach(labels, id: \.0) { col, text in
                Text(text)
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .fixedSize()
                    .offset(x: labelWidth + CGFloat(col) * (cell + gap))
            }
        }
    }
}

struct LogCell: View {
    let level: Int?
    let size: CGFloat
    let radius: CGFloat
    let palette: Palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        Group {
            if let fill = palette.fill(level) {
                shape.fill(fill)
            } else {
                shape.strokeBorder(palette.empty, lineWidth: 1)
            }
        }
        .frame(width: size, height: size)
    }
}

// MARK: - 조각

struct SampleBadge: View {
    var body: some View {
        Text("샘플 계좌")
            .font(.system(size: 9, weight: .semibold))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 5)
            .padding(.vertical, 1.5)
            .background(Capsule().fill(Color.secondary.opacity(0.16)))
            .fixedSize()
    }
}

struct Title: View {
    let snapshot: Snapshot?
    var showAccount = false
    /// 배지를 이름 바로 뒤에(오른쪽 끝에 다른 덩어리가 있을 때)
    var inlineBadge = false

    var body: some View {
        HStack(spacing: 5) {
            Text("UpLog")
                .font(.system(size: 11, weight: .bold))
            if showAccount, let snapshot {
                Text(snapshot.accountName)
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            if inlineBadge, snapshot?.sample == true { SampleBadge() }
            Spacer(minLength: 0)
            if !inlineBadge, snapshot?.sample == true { SampleBadge() }
        }
    }
}

/// 오늘(마지막 기록일) 손익 덩어리
struct TodayBlock: View {
    let snapshot: Snapshot
    var size: CGFloat = 20
    let now: Date

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = Palette.of(scheme)
        VStack(alignment: .leading, spacing: 1) {
            if let today = snapshot.today, let asOf = snapshot.asOf {
                Text("\(Format.dayTitle(asOf, now: now)) 손익")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                Text(Format.signedWon(today.pnl))
                    .font(.system(size: size, weight: .semibold).monospacedDigit())
                    .foregroundStyle(palette.text(for: today.pnl))
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Text(Format.percent(today.return))
                    .font(.system(size: 11, weight: .medium).monospacedDigit())
                    .foregroundStyle(palette.text(for: today.pnl))
            } else {
                Text("아직 기록이 없어요")
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// "이번 달 12일 벌고 7일 잃었어요"
struct MonthBlock: View {
    let snapshot: Snapshot
    var alignment: HorizontalAlignment = .trailing

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = Palette.of(scheme)
        let m = snapshot.month
        VStack(alignment: alignment, spacing: 1) {
            Text("이번 달")
                .font(.system(size: 10))
                .foregroundStyle(.secondary)
            (Text("\(m.gainDays)일").foregroundStyle(palette.gainText).fontWeight(.semibold)
                + Text(" 벌고"))
                .font(.system(size: 12))
            (Text("\(m.lossDays)일").foregroundStyle(palette.lossText).fontWeight(.semibold)
                + Text(" 잃었어요"))
                .font(.system(size: 12))
            Text(Format.signedWonShort(m.pnl))
                .font(.system(size: 11, weight: .medium).monospacedDigit())
                .foregroundStyle(palette.text(for: m.pnl))
        }
    }
}

struct StreakBlock: View {
    let snapshot: Snapshot

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = Palette.of(scheme)
        VStack(alignment: .leading, spacing: 1) {
            Text("연속")
                .font(.system(size: 10))
                .foregroundStyle(.secondary)
            if let kind = snapshot.streak.kind, snapshot.streak.length > 0 {
                let gain = kind == "GAIN"
                Text("\(snapshot.streak.length)일")
                    .font(.system(size: 18, weight: .semibold).monospacedDigit())
                    .foregroundStyle(gain ? palette.gainText : palette.lossText)
                Text(gain ? "연속 벌었어요" : "연속 잃었어요")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            } else {
                Text("—")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.secondary)
                Text("이어진 기록 없음")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// 손실 4 … 0 … 이익 4
struct LegendRow: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let palette = Palette.of(scheme)
        HStack(spacing: 2) {
            Text("손실").font(.system(size: 9)).foregroundStyle(.secondary).padding(.trailing, 2)
            ForEach([-4, -3, -2, -1, 0, 1, 2, 3, 4], id: \.self) { level in
                LogCell(level: level, size: 8, radius: 2, palette: palette)
            }
            Text("이익").font(.system(size: 9)).foregroundStyle(.secondary).padding(.leading, 2)
        }
    }
}

// MARK: - 크기별

struct UplogLogView: View {
    let snapshot: Snapshot?
    let family: LogFamily
    var now: Date = .now

    var body: some View {
        Group {
            if let snapshot {
                switch family {
                case .small: small(snapshot)
                case .medium: medium(snapshot)
                case .large: large(snapshot)
                }
            } else {
                placeholder
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(snapshot?.accessibilitySummary(now: now) ?? "UpLog 로그. UpLog 앱을 열어 주세요")
    }

    private func recent(_ s: Snapshot, _ n: Int) -> [Snapshot.Week] {
        Array(s.weeks.suffix(n))
    }

    private func small(_ s: Snapshot) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Title(snapshot: s)
            TodayBlock(snapshot: s, size: 19, now: now)
            LogGrid(weeks: recent(s, LogFamily.small.weeks), gap: 2.5)
        }
    }

    private func medium(_ s: Snapshot) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Title(snapshot: s, showAccount: true, inlineBadge: true)
                    TodayBlock(snapshot: s, size: 20, now: now)
                }
                Spacer(minLength: 12)
                MonthBlock(snapshot: s)
            }
            LogGrid(weeks: recent(s, LogFamily.medium.weeks), dayLabels: true, gap: 3)
        }
    }

    private func large(_ s: Snapshot) -> some View {
        let weeks = recent(s, LogFamily.large.weeks)
        let half = weeks.count / 2
        return VStack(alignment: .leading, spacing: 10) {
            Title(snapshot: s, showAccount: true)
            HStack(alignment: .top, spacing: 8) {
                TodayBlock(snapshot: s, size: 20, now: now)
                    .frame(maxWidth: .infinity, alignment: .leading)
                MonthBlock(snapshot: s, alignment: .leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                StreakBlock(snapshot: s)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            // 26주를 13주씩 두 줄로: 칸이 커진다(위 = 앞 13주, 아래 = 최근 13주).
            VStack(spacing: 6) {
                LogGrid(weeks: Array(weeks.prefix(half)), dayLabels: true, monthLabels: true, gap: 3)
                LogGrid(weeks: Array(weeks.suffix(weeks.count - half)), dayLabels: true, monthLabels: true, gap: 3)
            }
            HStack {
                LegendRow()
                Spacer(minLength: 4)
                if let asOf = s.asOf {
                    Text("기준일 \(asOf.replacingOccurrences(of: "-", with: "."))")
                        .font(.system(size: 9).monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var placeholder: some View {
        VStack(alignment: .leading, spacing: 6) {
            Title(snapshot: nil)
            Text("UpLog 앱을 열어 주세요")
                .font(.system(size: family == .small ? 13 : 15, weight: .semibold))
            Text("앱을 한 번 열면 매일의 손익 로그가 여기 보여요.")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            LogGrid(weeks: Self.emptyWeeks(family.weeks), dayLabels: family != .small, gap: 3)
        }
    }

    static func emptyWeeks(_ n: Int) -> [Snapshot.Week] {
        (0..<n).map { i in
            Snapshot.Week(start: "empty-\(i)",
                          days: (0..<5).map { _ in Snapshot.Day(date: "", level: nil, pnl: nil) })
        }
    }
}
