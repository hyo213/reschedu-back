package com.academy.reschedu.domain.academy;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<?> registerAcademy(@RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String address = request.get("address");
            Academy newAcademy = academyService.createAcademy(name, address);
            return ResponseEntity.ok(newAcademy);
        } catch (Exception e) {
            return ResponseEntity.status(400).body(e.getMessage());
        }
    }
}