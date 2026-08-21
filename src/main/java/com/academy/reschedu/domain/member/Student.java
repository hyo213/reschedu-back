package com.academy.reschedu.domain.member;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    private LocalDate birthDate;

    private String gender;

    private String childPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Member parent;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AcademyStudent> academyRegistrations = new ArrayList<>();

    public Student(String name, LocalDate birthDate, String gender, String childPhone, Member parent) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.childPhone = childPhone;
        this.parent = parent;
    }

    public void updateCoreInfo(LocalDate birthDate, String gender, String childPhone) {
        this.birthDate = birthDate;
        this.gender = gender;
        this.childPhone = childPhone;
    }

    public void updateName(String name) {
        this.name = name;
    }
}