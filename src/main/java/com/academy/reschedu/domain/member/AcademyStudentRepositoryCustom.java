package com.academy.reschedu.domain.member;

import java.util.List;
import java.util.UUID;

public interface AcademyStudentRepositoryCustom {

    /**
     * 🎯 [수강생 관리] 목록 화면의 동적 검색. teacherUuid/keyword는 선택값이고 unpaidOnly는 토글이다 —
     * 파생 쿼리 메서드 조합(findByXAndY, findByXAndYAndZ...)으로는 선택 조건이 늘어날수록 조합이
     * 기하급수적으로 늘어나므로 QueryDSL의 동적 BooleanBuilder로 구성한다.
     */
    List<AcademyStudent> search(Long academyId, UUID teacherUuid, boolean unpaidOnly, String keyword);
}
