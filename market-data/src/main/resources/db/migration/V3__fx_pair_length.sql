-- 샘플(데모 계좌용) 합성 환율을 실제 환율과 다른 pair(USDKRW_SAMPLE)로 두려고 pair 길이를 늘린다.
ALTER TABLE fx_rate ALTER COLUMN pair SET DATA TYPE VARCHAR(20);
