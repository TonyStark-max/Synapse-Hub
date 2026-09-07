package com.ideamanagement.platform.controller;

import com.ideamanagement.platform.model.Company;
import com.ideamanagement.platform.repository.CompanyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping
    public ResponseEntity<List<Company>> getCompanies() {
        return ResponseEntity.ok(companyRepository.findAll());
    }
}
