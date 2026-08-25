package com.academy.reschedu.domain.makeup;

import com.academy.reschedu.domain.academy.Academy;
import com.academy.reschedu.domain.academy.AcademyRepository;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyResponse;
import com.academy.reschedu.domain.makeup.dto.MakeupTicketPolicyUpdateRequest;
import com.academy.reschedu.domain.member.Member;
import com.academy.reschedu.domain.member.MemberRole;
import com.academy.reschedu.global.security.CurrentMemberProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MakeupTicketPolicyService {

    private final MakeupTicketPolicyRepository makeupTicketPolicyRepository;
    private final AcademyRepository academyRepository;
    private final CurrentMemberProvider currentMemberProvider;

    public MakeupTicketPolicyResponse getPolicy(Long academyId) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 보강권 정책만 조회할 수 있습니다.");
        }
        return MakeupTicketPolicyResponse.from(makeupTicketPolicyRepository.findByAcademy_Id(academyId).orElse(null));
    }

    /** 원장 전용: 보강권 전체 정책을 설정한다. 정책 행이 없는 학원이면 이번에 처음 만든다. */
    @Transactional
    public MakeupTicketPolicyResponse updatePolicy(Long academyId, MakeupTicketPolicyUpdateRequest request) {
        Member requester = currentMemberProvider.getCurrentMember();
        if (requester.getRole() != MemberRole.ADMIN) {
            throw new IllegalStateException("원장만 보강권 전체 정책을 설정할 수 있습니다.");
        }
        if (requester.getAcademy() == null || !requester.getAcademy().getId().equals(academyId)) {
            throw new IllegalStateException("소속 학원의 보강권 정책만 설정할 수 있습니다.");
        }

        MakeupTicketPolicy policy = makeupTicketPolicyRepository.findByAcademy_Id(academyId).orElse(null);
        if (policy == null) {
            Academy academy = academyRepository.findById(academyId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 학원입니다."));
            policy = MakeupTicketPolicy.builder()
                    .academy(academy)
                    .maxOutstandingTickets(request.maxOutstandingTickets())
                    .monthlyIssueLimit(request.monthlyIssueLimit())
                    .defaultValidityDays(request.defaultValidityDays())
                    .build();
        } else {
            policy.update(request.maxOutstandingTickets(), request.monthlyIssueLimit(), request.defaultValidityDays());
        }
        makeupTicketPolicyRepository.save(policy);
        return MakeupTicketPolicyResponse.from(policy);
    }
}
