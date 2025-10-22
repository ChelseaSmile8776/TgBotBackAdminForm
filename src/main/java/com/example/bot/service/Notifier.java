package com.example.bot.service;

import com.example.bot.domain.Submission;

public interface Notifier {
    void notifyAdmins(Submission s);
}