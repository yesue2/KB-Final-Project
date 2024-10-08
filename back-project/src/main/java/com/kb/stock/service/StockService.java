//package com.kb.stock.service;
//
//import com.kb.stock.dto.StockDTO;
//import com.kb.stock.mapper.StockMapper;
//import com.kb.stock.handler.StockWebSocketHandler;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.http.*;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.HttpClientErrorException;
//import org.springframework.web.client.RestTemplate;
//
//import javax.annotation.PostConstruct;
//import java.math.BigDecimal;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.TimeUnit;
//
//@Service
//public class StockService {
//
//    private static final Logger logger = LoggerFactory.getLogger(StockService.class);
//
//    @Autowired
//    private StockMapper stockMapper;
//
//    @Autowired
//    private TokenService tokenService;
//
//    @Value("${kis.api.appkey}")
//    private String appKey;
//
//    @Value("${kis.api.secretkey}")
//    private String appSecret;
//
//    @Value("${kis.api.base-url}")
//    private String baseUrl;
//
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    private static final String PRODUCT_TYPE_CODE = "300";
//    private static final int MAX_RETRIES = 3;
//    private static final long RETRY_DELAY_MS = 1000;
//    private static final int REQUESTS_PER_SECOND = 20;
//    private static final long REQUEST_INTERVAL_MS = 1000 / REQUESTS_PER_SECOND;
//
//    private StockWebSocketHandler webSocketHandler;
//    private final Set<String> subscribedStocks = ConcurrentHashMap.newKeySet();
//
//    @Autowired
//    private WebSocketService webSocketService;
//
//    @PostConstruct
//    public void init() {
//        int result = stockMapper.checkDatabaseConnection();
//        logger.info("Database connection check result: {}", result);
//        startRealTimeUpdates();
//    }
//
//    @Autowired
//    @Lazy
//    public void setWebSocketHandler(StockWebSocketHandler webSocketHandler) {
//        this.webSocketHandler = webSocketHandler;
//    }
//
//    public void addSubscription(String stockCode) {
//        subscribedStocks.add(stockCode);
//    }
//
//    public void removeSubscription(String stockCode) {
//        subscribedStocks.remove(stockCode);
//    }
//
//    public void updateAllStocks() {
//        logger.info("Starting updateAllStocks method");
//        List<String> stockCodes;
//        try {
//            stockCodes = stockMapper.selectAllStockCodes();
//        } catch (Exception e) {
//            logger.error("Error fetching stock codes from database: {}", e.getMessage(), e);
//            return;
//        }
//
//        logger.info("Retrieved {} stock codes from database", stockCodes.size());
//
//        for (int i = 0; i < stockCodes.size(); i += 20) {
//            List<String> batch = stockCodes.subList(i, Math.min(i + 20, stockCodes.size()));
//            processStockBatch(batch);
//
//            try {
//                Thread.sleep(REQUEST_INTERVAL_MS * 20);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.error("Thread interrupted while waiting for next batch", e);
//            }
//        }
//        logger.info("Finished updateAllStocks method");
//    }
//
//    private void processStockBatch(List<String> stockCodes) {
//        long lastRequestTime = 0;
//
//        for (String stockCode : stockCodes) {
//            logger.info("Processing stock code: {}", stockCode);
//
//            try {
//                long currentTime = System.currentTimeMillis();
//                long elapsedTime = currentTime - lastRequestTime;
//                if (elapsedTime < REQUEST_INTERVAL_MS) {
//                    Thread.sleep(REQUEST_INTERVAL_MS - elapsedTime);
//                }
//
//                Map<String, Object> stockData = getStockPrice(stockCode);
//                if (stockData != null) {
//                    StockDTO stockDTO = mapToStockDTO(stockData);
//                    if (stockDTO != null) {
//                        upsertStockData(stockDTO);
//
//                        if (webSocketService != null) {
//                            webSocketService.updateLastPrices(stockDTO.getStockCode(), stockDTO.getCurrentPrice().doubleValue());
//                            logger.info("Successfully updated stock data for {}", stockCode);
//                        } else {
//                            logger.warn("WebSocketService is null, cannot update last prices for stock code: {}", stockCode);
//                        }
//
//                    } else {
//                        logger.warn("Stock data is incomplete for {}", stockCode);
//                    }
//                } else {
//                    logger.warn("No stock data retrieved for {}", stockCode);
//                }
//
//                lastRequestTime = System.currentTimeMillis();
//
//            } catch (Exception e) {
//                logger.error("Error processing stock code {}: {}", stockCode, e.getMessage(), e);
//            }
//        }
//    }
//
//    public Map<String, Object> getStockPrice(String stockCode) {
//        logger.info("Attempting to get stock price for code: {}", stockCode);
//
//        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
//            try {
//                String accessToken = tokenService.getAccessToken();
//                logger.info("Retrieved access token: {}", accessToken);
//
//                String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD=" + stockCode;
//                logger.info("API request URL: {}", url);
//
//                HttpHeaders headers = new HttpHeaders();
//                headers.set("Content-Type", "application/json; charset=utf-8");
//                headers.set("authorization", "Bearer " + accessToken);
//                headers.set("appkey", appKey);
//                headers.set("appsecret", appSecret);
//                headers.set("tr_id", "FHKST01010100");
//
//                HttpEntity<String> request = new HttpEntity<>(headers);
//
//                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
//                logger.info("API response for stock code {}: Status={}, Body={}", stockCode, response.getStatusCode(), response.getBody());
//
//                if (response.getStatusCode() == HttpStatus.OK) {
//                    logger.info("Successfully retrieved stock data: {}", response.getBody());
//                    return (Map<String, Object>) response.getBody().get("output");
//                } else {
//                    logger.error("Failed to retrieve stock data. Status code: {}", response.getStatusCode());
//                }
//            } catch (HttpClientErrorException e) {
//                logger.error("HTTP client error occurred: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
//                if (attempt < MAX_RETRIES - 1) {
//                    try {
//                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//            } catch (Exception e) {
//                logger.error("Error getting stock price for code {}: {}", stockCode, e.getMessage(), e);
//                if (attempt < MAX_RETRIES - 1) {
//                    try {
//                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    private StockDTO mapToStockDTO(Map<String, Object> stockData) {
//        logger.info("Mapping stock data to StockDTO: {}", stockData);
//        if (stockData == null) {
//            return null;
//        }
//
//        try {
//            String stockCode = (String) stockData.get("stck_shrn_iscd");
//            String stockName = stockMapper.selectStockNameByCode(stockCode);
//
//            StockDTO stockDTO = new StockDTO();
//            stockDTO.setStockCode(stockCode);
//            stockDTO.setStockName(stockName != null ? stockName : "");
//            stockDTO.setCurrentPrice(stockData.get("stck_prpr") != null ? new BigDecimal(stockData.get("stck_prpr").toString()) : BigDecimal.ZERO);
//            stockDTO.setPriceChange(stockData.get("prdy_vrss") != null ? new BigDecimal(stockData.get("prdy_vrss").toString()) : BigDecimal.ZERO);
//            stockDTO.setPriceChangePct(stockData.get("prdy_ctrt") != null ? new BigDecimal(stockData.get("prdy_ctrt").toString()) : BigDecimal.ZERO);
//            stockDTO.setHighPrice(stockData.get("stck_hgpr") != null ? new BigDecimal(stockData.get("stck_hgpr").toString()) : BigDecimal.ZERO);
//            stockDTO.setLowPrice(stockData.get("stck_lwpr") != null ? new BigDecimal(stockData.get("stck_lwpr").toString()) : BigDecimal.ZERO);
//            stockDTO.setOpeningPrice(stockData.get("stck_oprc") != null ? new BigDecimal(stockData.get("stck_oprc").toString()) : BigDecimal.ZERO);
//            stockDTO.setIndustry(stockData.get("bstp_kor_isnm") != null ? stockData.get("bstp_kor_isnm").toString() : "");
//            stockDTO.setVolume(stockData.get("acml_vol") != null ? Long.parseLong(stockData.get("acml_vol").toString()) : 0L);
//            stockDTO.setW52Hgpr(stockData.get("w52_hgpr") != null ? new BigDecimal(stockData.get("w52_hgpr").toString()) : BigDecimal.ZERO);
//            stockDTO.setW52Lwpr(stockData.get("w52_lwpr") != null ? new BigDecimal(stockData.get("w52_lwpr").toString()) : BigDecimal.ZERO);
//            stockDTO.setHtsAvls(stockData.get("hts_avls") != null ? new BigDecimal(stockData.get("hts_avls").toString()) : BigDecimal.ZERO);
//
//            // 추가된 주식 상품 정보 매핑
//            stockDTO.setPrdtTypeCd(stockData.get("prdt_type_cd") != null ? stockData.get("prdt_type_cd").toString() : "");
//            stockDTO.setPrdtName120(stockData.get("prdt_name120") != null ? stockData.get("prdt_name120").toString() : "");
//            stockDTO.setPrdtAbrvName(stockData.get("prdt_abrv_name") != null ? stockData.get("prdt_abrv_name").toString() : "");
//            stockDTO.setPrdtEngName(stockData.get("prdt_eng_name") != null ? stockData.get("prdt_eng_name").toString() : "");
//            stockDTO.setPrdtEngName120(stockData.get("prdt_eng_name120") != null ? stockData.get("prdt_eng_name120").toString() : "");
//            stockDTO.setPrdtEngAbrvName(stockData.get("prdt_eng_abrv_name") != null ? stockData.get("prdt_eng_abrv_name").toString() : "");
//            stockDTO.setStdPdno(stockData.get("std_pdno") != null ? stockData.get("std_pdno").toString() : "");  // 표준상품번호
//            stockDTO.setShtnPdno(stockData.get("shtn_pdno") != null ? stockData.get("shtn_pdno").toString() : "");  // 단축상품번호
//            stockDTO.setPrdtSaleStatCd(stockData.get("prdt_sale_stat_cd") != null ? stockData.get("prdt_sale_stat_cd").toString() : "");
//            stockDTO.setPrdtRiskGradCd(stockData.get("prdt_risk_grad_cd") != null ? stockData.get("prdt_risk_grad_cd").toString() : "");
//            stockDTO.setPrdtClsfCd(stockData.get("prdt_clsf_cd") != null ? stockData.get("prdt_clsf_cd").toString() : "");
//            stockDTO.setPrdtClsfName(stockData.get("prdt_clsf_name") != null ? stockData.get("prdt_clsf_name").toString() : "");
//            stockDTO.setSaleStrtDt(stockData.get("sale_strt_dt") != null ? stockData.get("sale_strt_dt").toString() : "");
//            stockDTO.setSaleEndDt(stockData.get("sale_end_dt") != null ? stockData.get("sale_end_dt").toString() : "");
//            stockDTO.setWrapAsstTypeCd(stockData.get("wrap_asst_type_cd") != null ? stockData.get("wrap_asst_type_cd").toString() : "");
//            stockDTO.setIvstPrdtTypeCd(stockData.get("ivst_prdt_type_cd") != null ? stockData.get("ivst_prdt_type_cd").toString() : "");
//            stockDTO.setIvstPrdtTypeCdName(stockData.get("ivst_prdt_type_cd_name") != null ? stockData.get("ivst_prdt_type_cd_name").toString() : "");
//            stockDTO.setFrstErlmDt(stockData.get("frst_erlm_dt") != null ? stockData.get("frst_erlm_dt").toString() : "");
//
//            logger.info("Mapped StockDTO: {}", stockDTO);
//            return stockDTO;
//        } catch (Exception e) {
//            logger.error("Error mapping stock data to StockDTO: {}", e.getMessage(), e);
//            return null;
//        }
//    }
//
//    public void upsertStockData(StockDTO stockData) {
//        logger.info("Attempting to upsert stock data: {}", stockData);
//        try {
//            stockMapper.upsertStock(stockData);
//            logger.info("Successfully upserted stock data for code: {}", stockData.getStockCode());
//        } catch (Exception e) {
//            logger.error("Error upserting stock data for code {}: {}", stockData.getStockCode(), e.getMessage(), e);
//        }
//    }
//
//    public List<String> getAllStockCodes() {
//        return stockMapper.selectAllStockCodes();
//    }
//
//    public List<StockDTO> getAllStocks() {
//        return stockMapper.selectAllStocks();
//    }
//
//    public StockDTO getStockData(String stockCode) {
//        Map<String, Object> stockData = getStockPrice(stockCode);
//        return mapToStockDTO(stockData);
//    }
//
//    public void startRealTimeUpdates() {
//        new Thread(() -> {
//            while (true) {
//                for (String stockCode : subscribedStocks) {
//                    try {
//                        StockDTO stockData = getStockData(stockCode);
//                        if (stockData != null && webSocketHandler != null) {
//                            webSocketHandler.sendStockData(stockCode, stockData);
//                        }
//                    } catch (Exception e) {
//                        logger.error("Error updating stock data for {}: {}", stockCode, e.getMessage());
//                    }
//                }
//                try {
//                    Thread.sleep(5000); // 5초마다 업데이트
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    break;
//                }
//            }
//        }).start();
//    }
//    /**
//     * 주식 기본 정보를 한국투자증권 API를 통해 조회
//     */
//    public StockDTO getStockBasicInfo(String stockCode) {
//        logger.info("Attempting to get basic stock info for code: {}", stockCode);
//
//        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
//            try {
//                // Access Token 가져오기
//                String accessToken = tokenService.getAccessToken();
//                logger.info("Retrieved access token: {}", accessToken);
//
//                // 한국투자증권 API URL 구성
//                String url = baseUrl + "/uapi/domestic-stock/v1/quotations/search-stock-info" +
//                        "?PRDT_TYPE_CD=" + PRODUCT_TYPE_CODE + "&PDNO=" + stockCode;
//                logger.info("API request URL: {}", url);
//
//                // 요청 헤더 설정
//                HttpHeaders headers = new HttpHeaders();
//                headers.set("Content-Type", "application/json; charset=utf-8");
//                headers.set("authorization", "Bearer " + accessToken);
//                headers.set("appkey", appKey);
//                headers.set("appsecret", appSecret);
//                headers.set("tr_id", "CTPF1002R");
//
//                HttpEntity<String> request = new HttpEntity<>(headers);
//
//                // API 요청 전송
//                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
//                logger.info("API response for stock code {}: Status={}, Body={}", stockCode, response.getStatusCode(), response.getBody());
//
//                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
//                    // API 응답에서 주식 기본 정보 추출
//                    Map<String, Object> output = (Map<String, Object>) response.getBody().get("output");
//                    if (output != null) {
//                        return mapToStockDTO(output);  // DTO로 변환
//                    } else {
//                        logger.warn("No stock data retrieved for {}", stockCode);
//                    }
//                } else {
//                    logger.error("Failed to retrieve stock data. Status code: {}", response.getStatusCode());
//                }
//            } catch (HttpClientErrorException e) {
//                logger.error("HTTP client error occurred: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
//                if (attempt < MAX_RETRIES - 1) {
//                    try {
//                        Thread.sleep(RETRY_DELAY_MS);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//            } catch (Exception e) {
//                logger.error("Error getting stock basic info for code {}: {}", stockCode, e.getMessage(), e);
//                if (attempt < MAX_RETRIES - 1) {
//                    try {
//                        Thread.sleep(RETRY_DELAY_MS);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//}