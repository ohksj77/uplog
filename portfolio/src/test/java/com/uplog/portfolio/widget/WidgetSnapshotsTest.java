package com.uplog.portfolio.widget;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.portfolio.api.PortfolioDtos.LogDay;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WidgetSnapshotsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T01:00:00Z");

    private static LogDay day(String date, long pnl) {
        return new LogDay(LocalDate.parse(date), 1_000_000, 0, pnl, pnl / 1_000_000.0, "COLLECTED");
    }

    private static LogDay missing(String date) {
        return new LogDay(LocalDate.parse(date), 0, 0, 0, 0, "MISSING");
    }

    @Test
    void 단계는_부호와_경계로_정하고_0은_0단계다() {
        long[] t = {10_000, 20_000, 40_000};
        assertThat(WidgetSnapshots.level(0, t)).isZero();
        assertThat(WidgetSnapshots.level(9_999, t)).isEqualTo(1);
        assertThat(WidgetSnapshots.level(10_000, t)).isEqualTo(2);
        assertThat(WidgetSnapshots.level(-20_000, t)).isEqualTo(-3);
        assertThat(WidgetSnapshots.level(-39_999, t)).isEqualTo(-3);
        assertThat(WidgetSnapshots.level(40_000, t)).isEqualTo(4);
        assertThat(WidgetSnapshots.level(-1_000_000, t)).isEqualTo(-4);
    }

    @Test
    void 경계는_0이_아닌_손익의_사분위수를_만원_천원_단위로_맞춘다() {
        // |v| 정렬: 1000, 2000, 30000, 44000, 90000 → q25 2000, q50 30000, q75 44000
        assertThat(WidgetSnapshots.quartileThresholds(List.of(0L, -1_000L, 2_000L, 30_000L, -44_000L, 90_000L)))
                .containsExactly(2_000, 30_000, 40_000);
        // 선형 보간: 1234, 5678 → q25 2345 → 2000, q50 3456 → 3000, q75 4567 → 5000
        assertThat(WidgetSnapshots.quartileThresholds(List.of(1_234L, -5_678L))).containsExactly(2_000, 3_000, 5_000);
        assertThat(WidgetSnapshots.quartileThresholds(List.of(0L, 0L))).containsExactly(0, 0, 0);
        assertThat(WidgetSnapshots.quartileThresholds(List.of(400L))).containsExactly(400, 400, 400);
    }

    @Test
    void 격자는_오늘이_든_주까지_26주_월금이고_기록없음_휴장_미래는_null이다() {
        // 2026-10-14(수)가 마지막 기록. 10-13(화)은 MISSING, 10-12(월)는 휴장(칸 없음). 오늘은 10-15(목).
        List<LogDay> days = List.of(day("2026-10-08", 30_000), day("2026-10-09", -10_000), missing("2026-10-13"),
                day("2026-10-14", 0));
        WidgetSnapshot s = WidgetSnapshots.build("데모 계좌", true, days, LocalDate.parse("2026-10-15"), NOW);

        assertThat(s.weeks()).hasSize(26);
        assertThat(s.weeks()).allSatisfy(w -> {
            assertThat(w.start().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(w.days()).extracting(WidgetSnapshot.Day::date)
                    .containsExactly(w.start(), w.start().plusDays(1), w.start().plusDays(2), w.start().plusDays(3),
                            w.start().plusDays(4));
        });
        assertThat(s.weeks().getLast().start()).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(s.weeks().getFirst().start()).isEqualTo(LocalDate.parse("2026-04-20"));
        for (int i = 1; i < s.weeks().size(); i++) {
            assertThat(s.weeks().get(i).start()).isEqualTo(s.weeks().get(i - 1).start().plusWeeks(1));
        }

        List<WidgetSnapshot.Day> last = s.weeks().getLast().days();
        assertThat(last.get(0).level()).isNull(); // 휴장
        assertThat(last.get(1).level()).isNull(); // MISSING
        assertThat(last.get(1).pnl()).isNull();
        assertThat(last.get(2).level()).isZero(); // 손익 0
        assertThat(last.get(2).pnl()).isZero();
        assertThat(last.get(3).level()).isNull(); // 오늘(아직 기록 없음)
        assertThat(last.get(4).level()).isNull(); // 미래

        List<WidgetSnapshot.Day> prev = s.weeks().get(24).days();
        assertThat(prev.get(3).pnl()).isEqualTo(30_000);
        assertThat(prev.get(3).level()).isEqualTo(4); // 경계 [20000, 20000, 30000]: 0을 뺀 10000, 30000의 사분위수(15000·20000·25000)를 만 원 단위로
        assertThat(prev.get(4).level()).isEqualTo(-1);
        assertThat(s.thresholds()).containsExactly(20_000L, 20_000L, 30_000L).doesNotContainNull();
    }

    @Test
    void 오늘_이번달_연속은_마지막_기록일_기준이고_MISSING은_건너뛴다() {
        List<LogDay> days = List.of(day("2026-09-30", 50_000), day("2026-10-01", -5_000), day("2026-10-02", 7_000),
                day("2026-10-05", 8_000), missing("2026-10-06"), day("2026-10-07", 9_000));
        WidgetSnapshot s = WidgetSnapshots.build("내 계좌", false, days, LocalDate.parse("2026-10-08"), NOW);

        assertThat(s.version()).isEqualTo(1);
        assertThat(s.generatedAt()).isEqualTo(NOW);
        assertThat(s.asOf()).isEqualTo(LocalDate.parse("2026-10-07"));
        assertThat(s.today()).isEqualTo(new WidgetSnapshot.Today(9_000, 0.009));
        assertThat(s.month()).isEqualTo(new WidgetSnapshot.Tally(19_000, 3, 1));
        assertThat(s.year()).isEqualTo(new WidgetSnapshot.Tally(69_000, 4, 1));
        assertThat(s.streak()).isEqualTo(new WidgetSnapshot.Streak("GAIN", 3));
        assertThat(s.sample()).isFalse();

        WidgetSnapshot flat = WidgetSnapshots.build("내 계좌", false, List.of(day("2026-10-07", 0)),
                LocalDate.parse("2026-10-08"), NOW);
        assertThat(flat.streak()).isEqualTo(new WidgetSnapshot.Streak(null, 0));
        WidgetSnapshot loss = WidgetSnapshots.build("내 계좌", false, List.of(day("2026-10-06", 1), day("2026-10-07", -1)),
                LocalDate.parse("2026-10-08"), NOW);
        assertThat(loss.streak()).isEqualTo(new WidgetSnapshot.Streak("LOSS", 1));
    }

    @Test
    void 경계는_마지막_기록일부터_1년만_본다() {
        List<LogDay> days = new ArrayList<>();
        days.add(day("2025-10-07", 9_000_000)); // 딱 1년 전 → 뺀다
        days.add(day("2026-10-06", 1_000));
        days.add(day("2026-10-07", 3_000));
        WidgetSnapshot s = WidgetSnapshots.build("내 계좌", false, days, LocalDate.parse("2026-10-08"), NOW);
        assertThat(s.thresholds()).containsExactly(2_000L, 2_000L, 3_000L);
    }

    @Test
    void 기록이_없으면_오늘은_null이고_칸은_모두_비어_있다() {
        WidgetSnapshot s = WidgetSnapshots.build("데모 계좌", true, List.of(missing("2026-10-07")),
                LocalDate.parse("2026-10-08"), NOW);
        assertThat(s.asOf()).isNull();
        assertThat(s.today()).isNull();
        assertThat(s.month()).isEqualTo(new WidgetSnapshot.Tally(0, 0, 0));
        assertThat(s.year()).isEqualTo(new WidgetSnapshot.Tally(0, 0, 0));
        assertThat(s.weeks()).flatExtracting(WidgetSnapshot.Week::days).extracting(WidgetSnapshot.Day::level).containsOnlyNulls();
    }

    @Test
    void 파일은_임시_파일에_쓰고_이름을_바꿔_남는_임시_파일이_없다(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("widget").resolve("widget.json");
        WidgetSnapshotWriter.writeAtomically(target, "{\"version\":1}".getBytes(StandardCharsets.UTF_8));
        WidgetSnapshotWriter.writeAtomically(target, "{\"version\":2}".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readString(target)).isEqualTo("{\"version\":2}");
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("widget.json");
        }
    }
}
