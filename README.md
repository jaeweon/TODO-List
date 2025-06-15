# 📝 Todo List 기록

> 기록을 습관으로.  
> 멍 때릴 때 가장 성장한다.


<details>
<summary>📅 2025-06-13 - 에러 대응 및 팀내 Test Code 작성 문화 공유 </summary>

###  오늘 한 일
- [x] 운영환경 에러 대응
- [X] TDD with Spring RestDocs 팀내 공유

### 📝 메모
- 팀내 TDD를 정착하기 위한 발표
- 에러 분석 및 해결

### 첨부파일 
- 트러블 슈팅 회고 : https://backendeveloper.tistory.com/212

</details>
---

<details>
<summary>📅 2025-06-04 - QueryDsl 작성 </summary>

###  오늘 한 일
- [x] 요구사항 분석
- [X] 추출 가능한 데이터 여부 검토 
- [x] SQL 작성 / 최적화
- [x] QueryDSL 작성

### 📝 메모
- 요구사항이 현재 개발 가능한지 검토하여, 업무에 착수한다.
- SQL에서 최적화 이후 QueryDSL에서 다시 최적화한다.

### 첨부파일 
- 쿼리 작성 : https://github.com/jaeweon/TODO-List/blob/master/java/queryDSL/Optimization.java
- 쿼리 리펙토링 : https://github.com/jaeweon/TODO-List/blob/master/java/queryDSL/Optimization-Refactoring.java

</details>

---

<details>
<summary>📅 2025-05-16 - 자바8 직렬화 문제 해결 </summary>

###  오늘 한 일
- [x] 테스트 시, LocalDateTime 직렬화 Gson Config 설정
- [x] 기존 구현된 API 테스트 코그 작성

### 📝 메모
- Gson 인스턴스를 직접 생성하지 않으면, Spring은 기본 Gson 인스턴스를 사용함
- Gson 인스턴스를 명시적으로 @Bean으로 등록하면, Spring은 이를 감지해 GsonHttpMessageConverter에 자동으로 주입함

</details>

---

<details>
<summary>📅 2025-05-13 - 업무 히스토리 파악 </summary>

###  오늘 한 일
- [x] TDD 가이드라인 초안 작성
- [x] 우선 순위 업무 팔로우 업

### 📝 메모
- 통합 테스트 <> 단위 테스트 수정 사항 정리
- 

</details>

---

<details>
<summary>📅 2025-05-12 - 에러 개선 작업 </summary>

###  오늘 한 일
- [x] 엑셀 다운로드 오류 개선
- [x] TDD 가이드라인 작성 논의

### 📝 메모
- 집계 쿼리 수정
- Join절에 둔 테이블을 mother 테이블로 집계

</details>

---

<details>
<summary>📅 2025-05-09 - API 문서화 논의</summary>

###  오늘 한 일
- [x] API 문서화 회의 참석
- [x] API 명세서 자동화를 위한 CI/CD 트리거 설정
- [x] 테스트 코드 자동화 도구 분석 및 보고서 작성

### 📝 메모
- `REST Docs + OpenAPI + Swagger UI` 방식으로 문서화하기로 결정하였으나, 테스트 코드 작성 부담이 있다는 의견이 나옴
- 테스트 코드 자동화를 위한 도구로 `Reflection`, `Fixture Monkey` 등이 언급됨

</details>

---

<details>
<summary>📅 2025-05-08 - API 명세화 방법 검토</summary>

###  오늘 한 일
- [x] `REST Docs + OpenAPI + Swagger UI` 조합 검토
- [x] Swagger 단독 문서화 방식 검토
- [x] 두 방식 모두 구현 (브랜치: `feature/api_docs`)
- [x] 방식 비교 보고서 작성

### 📝 메모
- 테스트 결과 생성되는 `resource` 파일을 OpenAPI Spec으로 변환하려 했으나, Java 8 호환 가능한 라이브러리를 찾지 못함
- 상위 버전을 포크하여 커스텀 작업으로 구현했으며, 추후 배포를 위해 로컬 라이브러리를 저장소(Nexus 등)로 옮기는 추가 작업이 필요
- 이후 Java 8 호환 버전이 존재했음을 확인함
- 관련 내용 정리: [API 문서 자동화 전략 정리 블로그 글](https://your-blog-url.com/docs/api-doc-strategy)


</details>
