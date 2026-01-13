package com.ayush.drogonStart.repository;

import com.ayush.drogonStart.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, String> {
    List<Job> findAllByOrderByCreatedAtDesc();
}