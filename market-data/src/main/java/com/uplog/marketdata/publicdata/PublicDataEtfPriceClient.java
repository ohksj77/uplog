package com.uplog.marketdata.publicdata;

import com.uplog.marketdata.EtfQuote;
import com.uplog.marketdata.PriceSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 금융위원회_증권상품시세정보 / getETFPriceInfo 어댑터.
 * 하루치(basDt) 전 종목을 페이지 단위로 받는다. 응답 형식은 {@link PublicDataEtfPriceParser} 참고(미검증).
 */
public final class PublicDataEtfPriceClient implements PriceSource {

    private final RestClient http;
    private final PublicDataProperties properties;
    private final PublicDataEtfPriceParser parser = new PublicDataEtfPriceParser();

    public PublicDataEtfPriceClient(RestClient.Builder builder, PublicDataProperties properties) {
        this.properties = properties;
        this.http = builder.baseUrl(properties.baseUrl().toString()).build();
    }

    public static PublicDataEtfPriceClient create(PublicDataProperties properties) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        return new PublicDataEtfPriceClient(RestClient.builder().requestFactory(factory), properties);
    }

    @Override
    public List<EtfQuote> etfQuotes(LocalDate date) {
        List<EtfQuote> quotes = new ArrayList<>();
        for (int pageNo = 1; ; pageNo++) {
            PublicDataEtfPriceParser.Page page = parser.parse(fetch(date, pageNo));
            quotes.addAll(page.quotes());
            if (page.quotes().isEmpty() || quotes.size() >= page.totalCount()) {
                return List.copyOf(quotes);
            }
        }
    }

    private String fetch(LocalDate date, int pageNo) {
        // 템플릿 변수로 넘겨야 serviceKey의 +, /, = 가 제대로 인코딩된다.
        return http.get()
                .uri(uri -> uri.path("/getETFPriceInfo")
                        .queryParam("serviceKey", "{serviceKey}")
                        .queryParam("resultType", "json")
                        .queryParam("basDt", date.format(DateTimeFormatter.BASIC_ISO_DATE))
                        .queryParam("numOfRows", properties.pageSize())
                        .queryParam("pageNo", pageNo)
                        .build(properties.serviceKey()))
                .retrieve()
                .body(String.class);
    }
}
