package com.example.bot.repo;

import com.example.bot.domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    boolean existsByChatId(Long chatId);
    Optional<Admin> findByChatId(Long chatId);
}