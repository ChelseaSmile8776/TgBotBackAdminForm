package com.example.bot.service;

import com.example.bot.domain.Submission;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(prefix = "app", name = "telegramEnabled", havingValue = "false", matchIfMissing = true)
public class NoopNotifier implements Notifier {
    @Override
    public void notifyAdmins(Submission s) {
        // Телеграм отключен — ничего не делаем.
    }
}