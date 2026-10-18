package com.uplog.marketdata.publicdata;

import com.uplog.marketdata.EtfQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 금융위원회_증권상품시세정보 getETFPriceInfo 응답(resultType=json) 파서.
 *
 * <p><b>미검증</b>: 실제 API 키 없이 공개 문서의 필드명(basDt, srtnCd, isinCd, itmsNm, clpr, totalCount 등)만 보고
 * 작성했다. 실제 응답과 대조하기 전까지는 형식이 다를 수 있다고 보고, 첫 수집 때 원문을 꼭 확인한다.
 * 그래서 숫자가 문자열/숫자 어느 쪽으로 와도, item이 배열/단일 객체/빈 문자열 어느 쪽으로 와도 받도록 했다.
 */
public final class PublicDataEtfPriceParser {

    private static final DateTimeFormatter BAS_DT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String NORMAL = "00";

    private final JsonMapper mapper = JsonMapper.builder().build();

    public record Page(int totalCount, List<EtfQuote> quotes) {}

    public Page parse(String body) {
        String trimmed = body == null ? "" : body.strip();
        // 키 오류 등은 resultType=json이어도 XML(OpenAPI_ServiceResponse)로 온다고 알려져 있다.
        if (trimmed.startsWith("<")) {
            throw new PublicDataApiException("JSON이 아닌 응답: " + abbreviate(trimmed));
        }
        JsonNode response = mapper.readTree(trimmed).path("response");
        String resultCode = response.path("header").path("resultCode").asString("");
        if (!NORMAL.equals(resultCode)) {
            throw new PublicDataApiException("공공데이터 API 오류 " + resultCode + ": "
                    + response.path("header").path("resultMsg").asString(""));
        }
        JsonNode body0 = response.path("body");
        int totalCount = body0.path("totalCount").asInt(0);
        JsonNode items = body0.path("items").path("item");
        List<EtfQuote> quotes = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                quotes.add(toQuote(item));
            }
        } else if (items.isObject()) {
            quotes.add(toQuote(items));
        }
        return new Page(totalCount, List.copyOf(quotes));
    }

    private static EtfQuote toQuote(JsonNode item) {
        return new EtfQuote(
                LocalDate.parse(required(item, "basDt"), BAS_DT),
                required(item, "srtnCd").strip(),
                required(item, "itmsNm").strip(),
                new BigDecimal(required(item, "clpr").strip().replace(",", "")));
    }

    private static String required(JsonNode item, String field) {
        JsonNode node = item.path(field);
        String value = node.isMissingNode() || node.isNull() ? "" : node.asString("");
        if (value.isBlank()) {
            throw new PublicDataApiException("필드가 없습니다: " + field + " in " + abbreviate(item.toString()));
        }
        return value;
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
