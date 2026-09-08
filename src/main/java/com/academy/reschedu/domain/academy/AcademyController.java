package com.academy.reschedu.domain.academy;

import com.academy.reschedu.domain.academy.dto.AcademyRegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/academies")
@RequiredArgsConstructor
public class AcademyController {

    private final AcademyService academyService;

    // 학원 검색 API (GET /api/academies/search?keyword=Resch)
    @GetMapping("/search")
    public ResponseEntity<List<Academy>> search(@RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(academyService.searchAcademies(keyword));
    }

    // 신규 학원 자체 등록 API (POST /api/academies/register)
    @PostMapping("/register")
    public ResponseEntity<Academy> registerAcademy(@Valid @RequestBody AcademyRegisterRequest request) {
        Academy newAcademy = academyService.createAcademy(request.name(), request.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(newAcademy);
    }
}