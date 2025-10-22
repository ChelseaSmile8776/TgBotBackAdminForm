package com.example.bot.repo;

import com.example.bot.domain.Submission;
import com.example.bot.domain.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("select s from Submission s where s.createdAt between :from and :to order by s.createdAt desc")
    List<Submission> findByDateRange(OffsetDateTime from, OffsetDateTime to);

    // Последняя заявка
    Optional<Submission> findFirstByOrderByCreatedAtDesc();

    // Поиск по имени / телефону / вопросу (LIKE %term%)
    @Query("""
           select s from Submission s
           where lower(s.name) like lower(concat('%', :term, '%'))
              or lower(s.phone) like lower(concat('%', :term, '%'))
              or lower(s.questionText) like lower(concat('%', :term, '%'))
           order by s.createdAt desc
           """)
    List<Submission> search(String term);

    // Для статистики: считаем количество заявок по типам
    @Query("select s.formType, count(s) from Submission s where s.createdAt between :from and :to group by s.formType")
    List<Object[]> countByFormTypeInRange(OffsetDateTime from, OffsetDateTime to);
}