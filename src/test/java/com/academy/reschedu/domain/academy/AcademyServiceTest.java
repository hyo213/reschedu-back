package com.academy.reschedu.domain.academy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademyServiceTest {

    @Mock
    private AcademyRepository academyRepository;

    @InjectMocks
    private AcademyService academyService;

    @Nested
    class SearchAcademies {

        @Test
        void 키워드로_학원을_검색한다() {
            Academy academy = Academy.builder().name("리치 축구 클럽").build();
            when(academyRepository.findByNameContaining("리치")).thenReturn(List.of(academy));

            List<Academy> result = academyService.searchAcademies("리치");

            assertThat(result).containsExactly(academy);
        }
    }

    @Nested
    class CreateAcademy {

        @Test
        void 중복되지_않은_이름이면_학원을_생성한다() {
            when(academyRepository.findByName("새 학원")).thenReturn(Optional.empty());
            when(academyRepository.save(any(Academy.class))).thenAnswer(inv -> inv.getArgument(0));

            Academy created = academyService.createAcademy("새 학원", "서울시 광진구");

            ArgumentCaptor<Academy> captor = ArgumentCaptor.forClass(Academy.class);
            verify(academyRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("새 학원");
            assertThat(captor.getValue().getAddress()).isEqualTo("서울시 광진구");
            assertThat(created.getName()).isEqualTo("새 학원");
        }

        @Test
        void 이미_등록된_이름이면_예외() {
            Academy existing = Academy.builder().name("중복 학원").build();
            when(academyRepository.findByName("중복 학원")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> academyService.createAcademy("중복 학원", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 등록된 학원");
        }
    }
}
