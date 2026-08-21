package com.academy.reschedu.domain.academy;

import com.academy.reschedu.domain.academy.dto.HolidayCreateRequest;
import com.academy.reschedu.domain.academy.dto.HolidayResponse;
import com.academy.reschedu.domain.academy.dto.HolidayUpdateRequest;
import com.academy.reschedu.domain.member.AcademyStudentRepository;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.domain.regularclass.RegularClassService;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademyHolidayService {

    private final AcademyHolidayRepository academyHolidayRepository;
    private final AcademyRepository academyRepository;
    private final AcademyStudentRepository academyStudentRepository;
    private final RegularClassService regularClassService;
    private final CurrentMemberProvider currentMemberProvider;

    @Transactional
    public HolidayResponse createHoliday(Long academyId, HolidayCreateRequest request) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("휴무일 등록은 원장 또는 강사만 할 수 있습니다.");
        }

        Academy academy = academyRepository.findById(academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학원입니다."));

        if (academyHolidayRepository.existsByAcademyIdAndDate(academyId, request.date())) {
            throw new IllegalStateException("이미 등록된 휴무일입니다.");
        }

        AcademyHoliday holiday = AcademyHoliday.builder()
                .academy(academy)
                .date(request.date())
                .reason(request.reason())
                .build();
        academyHolidayRepository.save(holiday);

        // 🎯 휴무일 지정과 동시에, 그 요일에 해당하는 모든 정규 수업의 회차를 확보(생성 또는 갱신)하고
        // 회차 로스터 전원에게 보강권을 자동 발급한다. (RegularClassSession 기반 — 세션이 아직 없다면
        // 그 시점의 로스터를 스냅샷해 새로 만들고, 이미 있다면 휴무로 갱신 후 발급한다.)
        int issuedCount = regularClassService.applyHolidayToSessions(academy, request.date());

        return HolidayResponse.of(holiday, issuedCount);
    }

    public List<HolidayResponse> getHolidays(Long academyId) {
        validateRequesterBelongsToAcademy(academyId);
        return academyHolidayRepository.findByAcademyIdOrderByDateAsc(academyId).stream()
                .map(holiday -> HolidayResponse.of(holiday, null))
                .toList();
    }

    /**
     * 학부모 전용: 자녀가 속한 모든 학원의 휴무일 목록 조회.
     * 학부모 계정은 Member.academy가 없을 수 있으므로(자녀가 여러 학원에 다닐 수 있어 단일 소속이 성립하지 않음),
     * AcademyStudent를 통해 자녀들이 실제 등록된 학원 목록을 역추적해서 그 학원들의 휴무일을 모두 반환한다.
     */
    public List<HolidayResponse> getHolidaysForMyChildren() {
        Member parent = currentMemberProvider.getCurrentMember();
        if (parent.getRole() != MemberRole.PARENT) {
            throw new IllegalStateException("학부모 계정만 조회할 수 있는 API입니다.");
        }

        List<Long> academyIds = academyStudentRepository.findByStudent_Parent_Id(parent.getId()).stream()
                .map(academyStudent -> academyStudent.getAcademy().getId())
                .distinct()
                .toList();

        if (academyIds.isEmpty()) {
            return List.of();
        }

        return academyHolidayRepository.findByAcademyIdInOrderByDateAsc(academyIds).stream()
                .map(holiday -> HolidayResponse.of(holiday, null))
                .toList();
    }

    /**
     * 원장 전용: 휴무 사유 수정. 날짜 자체를 바꾸려면 취소 후 재등록해야 한다(회차 재계산이 필요하므로).
     */
    @Transactional
    public HolidayResponse updateHoliday(Long academyId, UUID holidayUuid, HolidayUpdateRequest request) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("휴무일 수정은 원장 또는 강사만 할 수 있습니다.");
        }

        AcademyHoliday holiday = academyHolidayRepository.findByUuidAndAcademyId(holidayUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 휴무일입니다."));

        holiday.updateReason(request.reason());
        return HolidayResponse.of(holiday, null);
    }

    /**
     * 원장 전용: 휴무일 지정 취소. 그 날짜에 해당하는 정규 수업 회차들을 원래 상태로 복원하고,
     * 휴무로 인해 발급되었던 미사용 보강권을 회수한다(학생마다 -1개).
     *
     * @return 실제로 회수된 보강권 개수
     */
    @Transactional
    public int deleteHoliday(Long academyId, UUID holidayUuid) {
        Member requester = validateRequesterBelongsToAcademy(academyId);
        if (requester.getRole() != MemberRole.ADMIN && requester.getRole() != MemberRole.TEACHER) {
            throw new IllegalStateException("휴무일 지정 취소는 원장 또는 강사만 할 수 있습니다.");
        }

        AcademyHoliday holiday = academyHolidayRepository.findByUuidAndAcademyId(holidayUuid, academyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 휴무일입니다."));

        int retractedCount = regularClassService.revertHolidayForSessions(holiday.getAcademy(), holiday.getDate());

        academyHolidayRepository.delete(holiday);
        return retractedCount;
    }

    private Member validateRequesterBelongsToAcademy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 휴무일만 조회하거나 관리할 수 있습니다.");
        }
        return requester;
    }
}
