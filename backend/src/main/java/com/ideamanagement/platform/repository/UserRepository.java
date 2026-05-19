package com.ideamanagement.platform.repository;

import com.ideamanagement.platform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    List<User> findByOrgId(String orgId);
}
