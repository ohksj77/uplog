// 앱 색 토큰(web/src/app/globals.css의 --gain-1..4, --loss-1..4, --log-empty, --log-zero, --gain, --loss).
// 이익 = 빨강, 손실 = 파랑(한국 증권 관례).
import SwiftUI

struct Palette: Sendable {
    let gain: [Color]  // 1…4
    let loss: [Color]  // 1…4
    let empty: Color
    let zero: Color
    let gainText: Color
    let lossText: Color

    static let light = Palette(
        gain: [0xffd6d9, 0xff9aa2, 0xf45c68, 0xc8102e].map(Color.init(hex:)),
        loss: [0xd3e4fd, 0x8fbaf8, 0x4a8ef2, 0x1450b8].map(Color.init(hex:)),
        empty: Color(hex: 0xebedf0),
        zero: Color(hex: 0xd9dde3),
        gainText: Color(hex: 0xe0283a),
        lossText: Color(hex: 0x1d6ce0))

    static let dark = Palette(
        gain: [0x4a1d22, 0x8a2a34, 0xd2404d, 0xff6b76].map(Color.init(hex:)),
        loss: [0x16294a, 0x1f4b8f, 0x3a7ae0, 0x78afff].map(Color.init(hex:)),
        empty: Color(hex: 0x1f2228),
        zero: Color(hex: 0x2c3038),
        gainText: Color(hex: 0xff6b74),
        lossText: Color(hex: 0x5ea1ff))

    static func of(_ scheme: ColorScheme) -> Palette { scheme == .dark ? .dark : .light }

    /// 단계 → 칸 색. nil(기록 없음)은 칠하지 않고 테두리만(LogCell이 그린다).
    func fill(_ level: Int?) -> Color? {
        guard let level else { return nil }
        if level == 0 { return zero }
        let i = min(4, abs(level)) - 1
        return level > 0 ? gain[i] : loss[i]
    }

    func text(for value: Int64) -> Color {
        value > 0 ? gainText : value < 0 ? lossText : .secondary
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: 1)
    }
}
