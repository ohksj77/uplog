package com.uplog.marketdata.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uplog.marketdata.EtfQuote;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 픽스처는 공개 문서의 필드명으로 직접 만든 것이다. 실제 응답 형식은 미검증. */
class PublicDataEtfPriceParserTest {

    private final PublicDataEtfPriceParser parser = new PublicDataEtfPriceParser();

    static String fixture(String name) {
        try (InputStream in = PublicDataEtfPriceParserTest.class.getResourceAsStream("/publicdata/" + name)) {
            if (in == null) {
                throw new IllegalStateException("픽스처 없음: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void 기준일자_단축코드_종목명_종가를_읽는다() {
        PublicDataEtfPriceParser.Page page = parser.parse(fixture("etf-price-page1.json"));

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.quotes()).containsExactly(
                new EtfQuote(LocalDate.of(2026, 10, 16), "TEST01", "테스트 ETF 하나", new BigDecimal("41250")),
                new EtfQuote(LocalDate.of(2026, 10, 16), "TEST02", "테스트 ETF 둘", new BigDecimal("10005")));
    }

    @Test
    void item이_단일_객체여도_읽고_천단위_쉼표를_무시한다() {
        PublicDataEtfPriceParser.Page page = parser.parse(fixture("etf-price-page2.json"));

        assertThat(page.quotes()).singleElement().satisfies(q -> {
            assertThat(q.code()).isEqualTo("TEST03");
            assertThat(q.close()).isEqualByComparingTo("5120");
        });
    }

    @Test
    void 휴장일처럼_결과가_없으면_빈_목록이다() {
        PublicDataEtfPriceParser.Page page = parser.parse(fixture("etf-price-empty.json"));

        assertThat(page.totalCount()).isZero();
        assertThat(page.quotes()).isEmpty();
    }

    @Test
    void 키_오류_XML_응답이나_오류_코드는_예외다() {
        assertThatThrownBy(() -> parser.parse(fixture("key-error.xml")))
                .isInstanceOf(PublicDataApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertThatThrownBy(() -> parser.parse("{\"response\":{\"header\":{\"resultCode\":\"99\",\"resultMsg\":\"LIMITED\"}}}"))
                .isInstanceOf(PublicDataApiException.class)
                .hasMessageContaining("99");
    }
}
