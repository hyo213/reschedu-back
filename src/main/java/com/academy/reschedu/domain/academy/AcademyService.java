package com.academy.reschedu.domain.academy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademyService {

    private final AcademyRepository academyRepository;

    // 1. 학원 키워드 검색
    public List<Academy> searchAcademies(String keyword) {
        return academyRepository.findByNameContaining(keyword);
    }

    // 2. 신규 학원 생성 (원장님 가입 모드일 때 사용)
    @Transactional
    public Academy createAcademy(String name, String address) {
        academyRepository.findByName(name)
                .ifPresent(a -> { throw new IllegalStateException("이미 등록된 학원(센터) 이름입니다."); });

        Academy academy = Academy.builder()
                .name(name)
                .address(address)
                .build();

        return academyRepository.save(academy);
    }
}