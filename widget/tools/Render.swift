// 위젯 화면을 위젯 갤러리 없이 PNG로 뽑는다(ImageRenderer). widget/tools/render.sh가 Sources/의 Snapshot·Palette·Views와 함께 컴파일한다.
//   render <sample.json> <out-dir>
// → widget-{small,medium,large,placeholder}-{light,dark}.png (2배율)
import AppKit
import SwiftUI

/// macOS 데스크톱 위젯 크기(pt)와 기본 여백.
private let sizes: [(String, LogFamily, CGSize)] = [
    ("small", .small, CGSize(width: 170, height: 170)),
    ("medium", .medium, CGSize(width: 364, height: 170)),
    ("large", .large, CGSize(width: 364, height: 382)),
]
private let contentMargin: CGFloat = 16

/// containerBackground(.fill.tertiary)를 흉내 낸 판 + 바탕화면.
private struct Frame<Content: View>: View {
    let size: CGSize
    let scheme: ColorScheme
    @ViewBuilder let content: Content

    var body: some View {
        content
            .padding(contentMargin)
            .frame(width: size.width, height: size.height)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(scheme == .dark ? Color(white: 0.17) : Color(white: 0.95)))
            .padding(20)
            .background(scheme == .dark ? Color(white: 0.08) : Color(red: 0.80, green: 0.84, blue: 0.90))
            .environment(\.colorScheme, scheme)
    }
}

@MainActor
private func save<V: View>(_ view: V, to url: URL) throws {
    let renderer = ImageRenderer(content: view)
    renderer.scale = 2
    guard let image = renderer.cgImage else { throw NSError(domain: "render", code: 1) }
    let rep = NSBitmapImageRep(cgImage: image)
    guard let png = rep.representation(using: .png, properties: [:]) else { throw NSError(domain: "render", code: 2) }
    try png.write(to: url)
    print(url.path)
}

@main
struct Render {
    @MainActor
    static func main() throws {
        let args = CommandLine.arguments
        guard args.count == 3 else {
            FileHandle.standardError.write("usage: render <sample.json> <out-dir>\n".data(using: .utf8)!)
            exit(2)
        }
        guard let snapshot = SnapshotStore.load(from: URL(fileURLWithPath: args[1])) else {
            FileHandle.standardError.write("스냅샷을 읽지 못했습니다: \(args[1])\n".data(using: .utf8)!)
            exit(1)
        }
        let out = URL(fileURLWithPath: args[2], isDirectory: true)
        try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        // 스냅샷의 생성 시각을 "지금"으로(기준일 문구가 실행하는 날에 따라 바뀌지 않게).
        let now = ISO8601DateFormatter().date(from: String(snapshot.generatedAt.prefix(19)) + "Z") ?? .now
        for scheme in [ColorScheme.light, .dark] {
            let suffix = scheme == .dark ? "dark" : "light"
            for (name, family, size) in sizes {
                try save(Frame(size: size, scheme: scheme) { UplogLogView(snapshot: snapshot, family: family, now: now) },
                         to: out.appendingPathComponent("widget-\(name)-\(suffix).png"))
            }
            try save(Frame(size: sizes[1].2, scheme: scheme) { UplogLogView(snapshot: nil, family: .medium) },
                     to: out.appendingPathComponent("widget-placeholder-\(suffix).png"))
            print(snapshot.accessibilitySummary(now: now))
        }
    }
}
