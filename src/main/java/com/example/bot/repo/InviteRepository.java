package com.example.bot.repo;

import com.example.bot.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, Long> {
    Optional<Invite> findByCode(UUID code);
}