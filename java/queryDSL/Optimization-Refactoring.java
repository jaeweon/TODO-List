    /**
     * 응답 / 미응답 분석 결과 조회
     *
     * @param searchParam 검색 조건
     * @param domainId    도메인 정보
     * @return 분석 결과 조회 리스트
     *
     * 주요 구현 방식:
     *
     * 1. 필터 조건에 따라 동적으로 where 조건을 구성하는 base DynamicQuery 구조를 사용한다.
     *
     * 2. analyzeHistory를 기준으로 각 분석 알고리즘별 결과(cc, ic, vs)를
     *    max(confidence) 기준으로 추출하는 서브쿼리를 활용하여 JOIN한다.
     *    - cc : Condition Classifier
     *    - ic : Intent Classifier
     *    - vs : Vector Similarity
     *
     * 3. intentName, intentId, confidence 정보를 각 알고리즘별로 의도된 1건만 조인하여 조회한다.
     *
     * 4. Keyword Spotting 결과도 nodeUserSays 테이블과 JOIN하여 함께 조회한다.
     *
     * 추가 검토 사항 : intentName 추출 부분
     * 검토자 : 이재원
     */
    public ChatBotAnalyzeStatSearch.ChatBotAnalyzeStatDto.DtoList getChatBotAnalysisList(ChatBotAnalyzeStatSearch searchParam, Long domainId) {
        JPAQueryFactory queryFactory = rdbService.getQueryFactory();

        QAnalyzeIntent ic = new QAnalyzeIntent("ic");
        QAnalyzeIntent cc = new QAnalyzeIntent("cc");
        QAnalyzeIntent vs = new QAnalyzeIntent("vs");
        QIntentmaster icMaster = new QIntentmaster("icMaster");
        QIntentmaster ccMaster = new QIntentmaster("ccMaster");
        QIntentmaster vsMaster = new QIntentmaster("vsMaster");

        BooleanBuilder where = new BooleanBuilder()
                .and(analyzeHistory.domainId.eq(domainId))
                .and(searchParam.where());

        // DynamicQuery - filter 여부에 대한 동적 쿼리
        Optional.ofNullable(searchParam.getFilter())
                .map(ChatBotAnalyzeStatSearch.AnalyzeHistoryFilters::getIsOutOfScope)
                .ifPresent(filterType -> {
                    if (filterType == NluAnalysedResult.OUT_OF_SCOPE) {
                        where.and(isOutOfScopeCondition());
                    } else if (filterType == NluAnalysedResult.COMPLETE) {
                        where.and(isOutOfScopeCondition().not());
                    }
                });

        JPAQuery<ChatBotAnalyzeStatSearch.ChatBotAnalyzeStatDto> query = queryFactory
                .select(Projections.constructor(ChatBotAnalyzeStatSearch.ChatBotAnalyzeStatDto.class,
                        analyzeHistory.nluId,
                        analyzeHistory.ucid,
                        analyzeHistory.requestQuery,
                        isRespond(),
                        analyzeEntity.entityName,
                        ccMaster.intentName,
                        cc.analysedConfidence.stringValue(),
                        nodeUserSays.userSayValue,
                        analyzeResult.nodeTitle,
                        icMaster.intentName,
                        ic.analysedConfidence.stringValue(),
                        vsMaster.intentName,
                        vs.analysedConfidence.stringValue(),
                        analyzeHistory.workTime
                ))
                .from(analyzeHistory)
                .leftJoin(analyzeEntity).on(analyzeEntity.id.nluId.eq(analyzeHistory.nluId))
                .leftJoin(analyzeResult).on(analyzeResult.nluId.eq(analyzeHistory.nluId))

                // conditionClassification
                .leftJoin(cc).on(cc.id.nluId.eq(analyzeHistory.nluId)
                        .and(cc.analyseAlgorithm.eq(NluAnalyseAlgorithm.CONDITION_CALSSFIER))
                        .and(cc.analysedConfidence.eq(getMaxConfidenceSubQuery(cc, NluAnalyseAlgorithm.CONDITION_CALSSFIER))))
                .leftJoin(ccMaster).on(cc.analysedIntentId.eq(ccMaster.intentId))

                // intentClassification
                .leftJoin(ic).on(ic.id.nluId.eq(analyzeHistory.nluId)
                        .and(ic.analyseAlgorithm.eq(NluAnalyseAlgorithm.INTENT_CLASSIFIER))
                        .and(ic.analysedConfidence.eq(getMaxConfidenceSubQuery(ic, NluAnalyseAlgorithm.INTENT_CLASSIFIER))))
                .leftJoin(icMaster).on(ic.analysedIntentId.eq(icMaster.intentId))

                // vectorSimilarity
                .leftJoin(vs).on(vs.id.nluId.eq(analyzeHistory.nluId)
                        .and(vs.analyseAlgorithm.eq(NluAnalyseAlgorithm.VECTOR_SIMILARITY))
                        .and(vs.analysedConfidence.eq(getMaxConfidenceSubQuery(vs, NluAnalyseAlgorithm.VECTOR_SIMILARITY))))
                .leftJoin(vsMaster).on(vs.analysedIntentId.eq(vsMaster.intentId))

                // keyWordResult
                .leftJoin(nodeUserSays).on(nodeUserSays.nodeMaster.id.scenarioId.eq(analyzeResult.scenarioId)
                        .and(nodeUserSays.nodeMaster.id.scenarioVerId.eq(analyzeResult.scenarioVerId)
                                .and(nodeUserSays.nodeMaster.id.nodeId.eq(analyzeResult.nodeId)))
                )

                .where(where)
                .orderBy(analyzeHistory.responseTime.desc());

        if (searchParam.isPageValid()) {
            query.offset(searchParam.getOffset());
            query.limit(searchParam.getCount());
        }

        List<ChatBotAnalyzeStatSearch.ChatBotAnalyzeStatDto> results = query.fetch();
        return new ChatBotAnalyzeStatSearch.ChatBotAnalyzeStatDto.DtoList((long) results.size(), searchParam, results);
    }
    
    // Confidence 최대값
    private SubQueryExpression<BigDecimal> getMaxConfidenceSubQuery(QAnalyzeIntent base, NluAnalyseAlgorithm algorithm) {
        QAnalyzeIntent sub = new QAnalyzeIntent(base.getMetadata().getName() + "_sub");
        return JPAExpressions
                .select(sub.analysedConfidence.max())
                .from(sub)
                .where(sub.id.nluId.eq(base.id.nluId)
                        .and(sub.analyseAlgorithm.eq(algorithm)));
    }

    // 응답 여부 CASE 문
    private Expression<Integer> isRespond() {
        return new CaseBuilder()
                .when(isOutOfScopeCondition())
                .then(OUT_OF_SCOPE.value())
                .otherwise(NluAnalysedResult.COMPLETE.value());
    }

    public BooleanExpression isOutOfScopeCondition() {
        return analyzeHistory.analysedResult.eq(OUT_OF_SCOPE)
                .and(analyzeResult.analyzeResultEntity.eq(NluAnalysedResultDetail.FAILURE))
                .and(analyzeResult.analyzeResultCc.eq(NluAnalysedResultDetail.FAILURE))
                .and(analyzeResult.analyzeResultVs.eq(NluAnalysedResultDetail.FAILURE));
    }
