package com.example.bot.service;

import com.example.bot.domain.FormType;
import com.example.bot.domain.Submission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.example.bot.domain.Admin;
import com.example.bot.repo.AdminRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app", name = "telegramEnabled", havingValue = "true")
public class TelegramNotifier implements Notifier {

    private final AbsSender sender;
    private final AdminRepository adminRepo;
    private final String adminsRawFromConfig;
    private final Long defaultAssignee; //null

    public TelegramNotifier(
            AbsSender sender,
            AdminRepository adminRepo,
            @Value("${app.admins:}") String adminsRawFromConfig,
            @Value("${app.defaultAssignee:#{null}}") Long defaultAssignee
    ) {
        this.sender = sender;
        this.adminRepo = adminRepo;
        this.adminsRawFromConfig = adminsRawFromConfig;
        this.defaultAssignee = defaultAssignee;
    }

    @Override
    public void notifyAdmins(Submission s) {
        String text = switch (s.getFormType()) {
            case A_CALLBACK -> """
                📞 <b>Срочно! Обратный звонок</b>
                👤 %s
                📱 %s
                """.formatted(nz(s.getName()), nz(s.getPhone()));

            case B_CONSULT -> """
                ❓ <b>Запрос консультации</b>
                👤 %s
                📱 %s
                💬 Вопрос: %s
                """.formatted(nz(s.getName()), nz(s.getPhone()), nz(s.getQuestionText()));

            case C_FAST_ORDER -> """
                🛒 <b>Заказ в 1 клик (пост-оплата)</b>
                🆔 Заказ: %s
                📦 %s
                🔗 %s
                💲 Количество: %s
                👤 Контакты: %s, %s
                """.formatted(
                    s.getOrderIdSql(),
                    nz(s.getProductSku()),
                    nz(s.getProductUrl()),
                    nz(String.valueOf(s.getAmount())),
                    nz(s.getName()),
                    nz(s.getPhone())
            );
        };

        Set<Long> recipients = new LinkedHashSet<>();
        recipients.addAll(
                adminRepo.findAll().stream()
                        .map(Admin::getChatId)
                        .filter(Objects::nonNull)
                        .toList()
        );
        recipients.addAll(parseAdmins(adminsRawFromConfig));
        if (defaultAssignee != null) recipients.add(defaultAssignee);
        if (s.getAssignedToChatId() != null) recipients.add(s.getAssignedToChatId());

        for (Long chatId : recipients) {
            send(chatId, text);
        }
    }

    private List<Long> parseAdmins(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<Long> out = new ArrayList<>();
        Arrays.stream(cleaned.split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .forEach(s -> {
                    try { out.add(Long.valueOf(s)); } catch (NumberFormatException ignore) {}
                });
        return out;
    }

    private void send(Long chatId, String text) {
        if (chatId == null) return;
        try {
            sender.execute(SendMessage.builder()
                    .chatId(chatId)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .text(text)
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String nz(String v) { return v == null ? "" : v; }
}