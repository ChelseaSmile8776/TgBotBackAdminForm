package com.example.bot.service;

import com.example.bot.domain.FormType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AssigneeService {

    private final Long defaultAssignee;
    // Можно грузить из БД: разные сотрудники под разные формы
    private final Map<FormType, Long> routing = Map.of(); // пусто = все к default

    public AssigneeService(@Value("${app.defaultAssignee}") Long defaultAssignee) {
        this.defaultAssignee = defaultAssignee;
    }

    public Long resolveAssignee(FormType t) {
        return routing.getOrDefault(t, defaultAssignee);
    }
}