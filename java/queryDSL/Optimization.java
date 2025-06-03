[보안을 위하여 기존 코드에서 식별을 달리했습니다.]
    /**
     * 응답 / 미응답 분석 결과 목록 조회
     *
     * @param searchParam 검색 조건
     * @param tenantId    테넌트(도메인) 정보
     * @return 응답 / 미응답 분석 결과 DTO 리스트
     *
     *  logMain 기준 로그 ID(logId) 조회 시, 응답 여부 필터링 조건에 따라 logResult 테이블과의 조인 여부가 달라지므로,
     *  불필요한 조인을 피하고 성능을 최적화하기 위해 동적 쿼리 방식으로 구현.
     *
     *  메인 쿼리의 성능을 보장하면서도 필요한 intent 정보를 효율적으로 활용하기 위해
     *  알고리즘별 intentName, intentId, confidence 값을 Map 으로 분리 조회하는 구조를 선택함.
     *
     *  1. 불필요한 JOIN 최소화:
     *     - logMain → logIntent → intentMeta 로 이어지는 JOIN 을 메인 조회 쿼리에서 수행하면,
     *       분석 알고리즘별로 총 3~4개의 JOIN 이 추가로 발생함.
     *     - 분석 로그가 수천 건 이상일 경우 JOIN 비용이 커져 전체 성능에 악영향을 줄 수 있음.
     *
     *  2. 키(logId) 기반 Map 캐싱 방식:
     *     - 미리 logId 와 intentName 을 Map<Long, T> 형태로 조회해두고,
     *       이후 메인 쿼리 결과 스트림에서 logId 기준으로 빠르게 추출함.
     *     - 이는 JOIN 대신 O(1) 수준의 Hash 조회로 처리되기 때문에 대규모 데이터에서도 효율적이라 판단.
     *
     */

public class AiAnalysisService {

    public AiAnalysisSearch.AiAnalysisDto.DtoList getAiAnalysisList(AiAnalysisSearch searchParam, Long tenantId) {
        JPAQueryFactory factory = querySupport.getQueryFactory();

        // id 추출 - 조건에 따라 동적 조인/where 구성
        JPAQuery<Long> logIdQuery = factory
                .select(logMain.id)
                .from(logMain)
                .where(logMain.tenantId.eq(tenantId));
        
        // 공통 WHERE 조건
        BooleanBuilder conditionBuilder = new BooleanBuilder().and(searchParam.where());

        // 응답여부 필터 적용
        ResponseType type = Optional.ofNullable(searchParam.getFilter())
                .map(AiAnalysisSearch.SearchFilter::getResponseType)
                .orElse(null);

        if (type != null) {
            logIdQuery.leftJoin(logResult).on(logResult.mainId.eq(logMain.id));
            if (type == ResponseType.UNRESPONDED) {
                conditionBuilder.and(isUnrespondedCondition());
            } else if (type == ResponseType.RESPONDED) {
                conditionBuilder.and(isUnrespondedCondition().not());
            }
        }

        // 최종 where 조건 적용
        logIdQuery.where(conditionBuilder);
        List<Long> logIds = logIdQuery.fetch();

        // Map 사전 조회
        Map<Long, String> ccResultMap = getIntentName(logIds, AnalysisAlgo.CONDITION_CLS);
        Map<Long, String> ccConfMap = getMaxConfidence(logIds, AnalysisAlgo.CONDITION_CLS);

        Map<Long, String> icResultMap = getIntentName(logIds, AnalysisAlgo.INTENT_CLS);
        Map<Long, String> icConfMap = getMaxConfidence(logIds, AnalysisAlgo.INTENT_CLS);

        Map<Long, String> vsResultMap = getIntentName(logIds, AnalysisAlgo.VECTOR_SIM);
        Map<Long, String> vsConfMap = getMaxConfidence(logIds, AnalysisAlgo.VECTOR_SIM);

        Map<Long, String> ksResultMap = getIntentName(logIds, AnalysisAlgo.KEYWORD_MATCH);

        // 조회
        JPAQuery<Tuple> mainQuery = factory
                .select(
                        logMain.id,
                        logMain.sessionId,
                        logMain.queryText,
                        determineResponse(),
                        logEntity.entityLabel,
                        logResult.nodeLabel,
                        logMain.analyzedAt
                )
                .from(logMain)
                .leftJoin(logEntity).on(logEntity.id.eq(logMain.id))
                .leftJoin(logResult).on(logResult.mainId.eq(logMain.id))
                .where(logMain.id.in(logIds))
                .orderBy(logMain.analyzedAt.desc());

        // 페이징 여부
        if (searchParam.isPageValid()) {
            mainQuery.offset(searchParam.getOffset());
            mainQuery.limit(searchParam.getCount());
        }

        // DTO 매핑
        List<AiAnalysisSearch.AiAnalysisDto> result = mainQuery.fetch().stream()
                .map(row -> {
                    Long id = row.get(logMain.id);
                    return AiAnalysisSearch.AiAnalysisDto.builder()
                            .logId(id)
                            .sessionId(row.get(logMain.sessionId))
                            .query(row.get(logMain.queryText))
                            .responseFlag(row.get(determineResponse()))
                            .entity(row.get(logEntity.entityLabel))
                            .conditionResult(ccResultMap.get(id))
                            .conditionConfidence(ccConfMap.get(id))
                            .intentResult(icResultMap.get(id))
                            .intentConfidence(icConfMap.get(id))
                            .vectorResult(vsResultMap.get(id))
                            .vectorConfidence(vsConfMap.get(id))
                            .keywordResult(ksResultMap.get(id))
                            .nodeName(row.get(logResult.nodeLabel))
                            .analyzedAt(row.get(logMain.analyzedAt))
                            .build();
                }).collect(Collectors.toList());

        return new AiAnalysisSearch.AiAnalysisDto.DtoList((long) result.size(), searchParam, result);
    }

    // 엑셀 데이터 추출
    public List<AiAnalysisSearch.AiAnalysisDto.ExcelDto> getAiAnalysisExcelList(AiAnalysisSearch searchParam, Long tenantId) {
        JPAQueryFactory factory = querySupport.getQueryFactory();

        JPAQuery<Long> logIdQuery = factory
                .select(logMain.id)
                .from(logMain)
                .where(logMain.tenantId.eq(tenantId).and(searchParam.where()));

        BooleanBuilder conditionBuilder = new BooleanBuilder();

        ResponseType type = Optional.ofNullable(searchParam.getFilter())
                .map(AiAnalysisSearch.SearchFilter::getResponseType)
                .orElse(null);

        if (type != null) {
            logIdQuery.leftJoin(logResult).on(logResult.mainId.eq(logMain.id));
            if (type == ResponseType.UNRESPONDED) {
                conditionBuilder.and(isUnrespondedCondition());
            } else if (type == ResponseType.RESPONDED) {
                conditionBuilder.and(isUnrespondedCondition().not());
            }
        }

        logIdQuery.where(conditionBuilder);
        List<Long> logIds = logIdQuery.fetch();
        if (logIds.isEmpty()) return Collections.emptyList();

        Map<Long, String> ccResultMap = getIntentName(logIds, AnalysisAlgo.CONDITION_CLS);
        Map<Long, Long> ccIdMap = getIntentId(logIds, AnalysisAlgo.CONDITION_CLS);
        Map<Long, String> ccConfMap = getMaxConfidence(logIds, AnalysisAlgo.CONDITION_CLS);

        Map<Long, String> icResultMap = getIntentName(logIds, AnalysisAlgo.INTENT_CLS);
        Map<Long, Long> icIdMap = getIntentId(logIds, AnalysisAlgo.INTENT_CLS);
        Map<Long, String> icConfMap = getMaxConfidence(logIds, AnalysisAlgo.INTENT_CLS);

        Map<Long, String> vsResultMap = getIntentName(logIds, AnalysisAlgo.VECTOR_SIM);
        Map<Long, Long> vsIdMap = getIntentId(logIds, AnalysisAlgo.VECTOR_SIM);
        Map<Long, String> vsConfMap = getMaxConfidence(logIds, AnalysisAlgo.VECTOR_SIM);

        Map<Long, String> ksResultMap = getIntentName(logIds, AnalysisAlgo.KEYWORD_MATCH);

        JPAQuery<Tuple> mainQuery = factory
                .select(
                        logMain.id,
                        logMain.sessionId,
                        logMain.queryText,
                        determineResponse(),
                        logEntity.entityCode,
                        logEntity.entityLabel,
                        logEntity.entityAlias,
                        logResult.nodeLabel,
                        scenarioInfo.name,
                        scenarioVer.version,
                        logResult.modelRef1,
                        logResult.modelRef2,
                        logMain.analyzedAt
                )
                .from(logMain)
                .leftJoin(logEntity).on(logEntity.id.eq(logMain.id))
                .leftJoin(logResult).on(logResult.mainId.eq(logMain.id))
                .leftJoin(scenarioInfo).on(logResult.scenarioId.eq(scenarioInfo.id))
                .leftJoin(scenarioVer).on(logResult.versionId.eq(scenarioVer.id))
                .where(logMain.id.in(logIds))
                .orderBy(logMain.analyzedAt.desc());

        if (searchParam.isPageValid()) {
            mainQuery.offset(searchParam.getOffset());
            mainQuery.limit(searchParam.getCount());
        }

        return mainQuery.fetch().stream().map(row -> {
            Long id = row.get(logMain.id);
            return AiAnalysisSearch.AiAnalysisDto.ExcelDto.builder()
                    .logId(id)
                    .sessionId(row.get(logMain.sessionId))
                    .query(row.get(logMain.queryText))
                    .responseFlag(row.get(determineResponse()))
                    .entityCode(row.get(logEntity.entityCode))
                    .entityLabel(row.get(logEntity.entityLabel))
                    .entityAlias(row.get(logEntity.entityAlias))

                    .conditionResultId(ccIdMap.get(id))
                    .conditionResult(ccResultMap.get(id))
                    .conditionConfidence(ccConfMap.get(id))
                    .intentResultId(icIdMap.get(id))
                    .intentResult(icResultMap.get(id))
                    .intentConfidence(icConfMap.get(id))
                    .vectorResultId(vsIdMap.get(id))
                    .vectorResult(vsResultMap.get(id))
                    .vectorConfidence(vsConfMap.get(id))
                    .keywordResult(ksResultMap.get(id))

                    .scenarioName(row.get(scenarioInfo.name))
                    .scenarioVersion(row.get(scenarioVer.version))
                    .nodeLabel(row.get(logResult.nodeLabel))
                    .modelRef1(row.get(logResult.modelRef1))
                    .modelRef2(row.get(logResult.modelRef2))
                    .analyzedAt(row.get(logMain.analyzedAt))
                    .build();
        }).collect(Collectors.toList());
    }

    private BooleanExpression isUnrespondedCondition() {
        return logMain.responseType.eq(ResponseType.UNRESPONDED)
                .and(logResult.intentResult.eq(ResponseFail.FAIL))
                .and(logResult.conditionResult.eq(ResponseFail.FAIL))
                .and(logResult.vectorResult.eq(ResponseFail.FAIL));
    }

    private Expression<Integer> determineResponse() {
        return new CaseBuilder()
                .when(isUnrespondedCondition()).then(ResponseType.UNRESPONDED.value())
                .otherwise(ResponseType.RESPONDED.value());
    }

    private Map<Long, String> getIntentName(List<Long> ids, AnalysisAlgo algo) {
        QLogIntent intent = QLogIntent.logIntent;
        QIntentMeta meta = QIntentMeta.intentMeta;

        return querySupport.getQueryFactory()
                .select(intent.id.mainId, meta.name)
                .from(intent)
                .leftJoin(meta).on(meta.id.eq(intent.intentId))
                .where(intent.id.mainId.in(ids), intent.algorithm.eq(algo))
                .transform(GroupBy.groupBy(intent.id.mainId).as(meta.name));
    }

    private Map<Long, Long> getIntentId(List<Long> ids, AnalysisAlgo algo) {
        QLogIntent intent = QLogIntent.logIntent;
        QIntentMeta meta = QIntentMeta.intentMeta;

        return querySupport.getQueryFactory()
                .select(intent.id.mainId, meta.id)
                .from(intent)
                .leftJoin(meta).on(meta.id.eq(intent.intentId))
                .where(intent.id.mainId.in(ids), intent.algorithm.eq(algo))
                .transform(GroupBy.groupBy(intent.id.mainId).as(meta.id));
    }

    private Map<Long, String> getMaxConfidence(List<Long> ids, AnalysisAlgo algo) {
        QLogIntent intent = QLogIntent.logIntent;

        return querySupport.getQueryFactory()
                .select(intent.id.mainId, intent.confidence.max().stringValue())
                .from(intent)
                .where(intent.id.mainId.in(ids), intent.algorithm.eq(algo))
                .groupBy(intent.id.mainId)
                .transform(GroupBy.groupBy(intent.id.mainId).as(intent.confidence.max().stringValue()));
    }
} 
