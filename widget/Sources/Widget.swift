// WidgetKit 진입점: "업로그 로그" 위젯(작게·중간·크게). 화면은 Views.swift.
import SwiftUI
import WidgetKit

struct LogEntry: TimelineEntry {
    let date: Date
    let snapshot: Snapshot?
}

struct LogProvider: TimelineProvider {
    /// 엔진이 30분마다(그리고 데이터가 바뀌면) 파일을 다시 쓴다. 위젯도 30분마다 다시 읽는다.
    static let reload: TimeInterval = 30 * 60

    func placeholder(in context: Context) -> LogEntry {
        LogEntry(date: .now, snapshot: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (LogEntry) -> Void) {
        completion(LogEntry(date: .now, snapshot: SnapshotStore.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LogEntry>) -> Void) {
        let entry = LogEntry(date: .now, snapshot: SnapshotStore.load())
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(Self.reload))))
    }
}

struct LogEntryView: View {
    let entry: LogEntry
    @Environment(\.widgetFamily) private var widgetFamily

    private var family: LogFamily {
        switch widgetFamily {
        case .systemSmall: .small
        case .systemLarge, .systemExtraLarge: .large
        default: .medium
        }
    }

    var body: some View {
        UplogLogView(snapshot: entry.snapshot, family: family, now: entry.date)
            .containerBackground(.fill.tertiary, for: .widget)
            .widgetURL(URL(string: "uplog://open"))
    }
}

struct UplogLogWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "dev.uplog.log", provider: LogProvider()) { entry in
            LogEntryView(entry: entry)
        }
        .configurationDisplayName("업로그 로그")
        .description("매일의 손익을 잔디처럼. 빨강은 번 날, 파랑은 잃은 날이에요.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct UplogWidgetBundle: WidgetBundle {
    var body: some Widget {
        UplogLogWidget()
    }
}
