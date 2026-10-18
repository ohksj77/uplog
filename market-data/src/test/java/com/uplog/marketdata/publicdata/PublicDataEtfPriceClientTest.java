package com.uplog.marketdata.publicdata;

import static com.uplog.marketdata.publicdata.PublicDataEtfPriceParserTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.uplog.marketdata.EtfQuote;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PublicDataEtfPriceClientTest {

    @Test
    void 기준일의_모든_페이지를_이어_받는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicDataProperties properties = new PublicDataProperties(
                "a+b/c=", URI.create("https://example.test/svc"), Duration.ofSeconds(1), 2, 7);
        PublicDataEtfPriceClient client = new PublicDataEtfPriceClient(builder, properties);

        server.expect(requestTo(Matchers.startsWith("https://example.test/svc/getETFPriceInfo?serviceKey=a%2Bb%2Fc%3D")))
                .andExpect(queryParam("basDt", "20261016"))
                .andExpect(queryParam("resultType", "json"))
                .andExpect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(fixture("etf-price-page1.json"), MediaType.APPLICATION_JSON));
        server.expect(queryParam("pageNo", "2"))
                .andRespond(withSuccess(fixture("etf-price-page2.json"), MediaType.APPLICATION_JSON));

        List<EtfQuote> quotes = client.etfQuotes(LocalDate.of(2026, 10, 16));

        assertThat(quotes).extracting(EtfQuote::code).containsExactly("TEST01", "TEST02", "TEST03");
        server.verify();
    }
}
