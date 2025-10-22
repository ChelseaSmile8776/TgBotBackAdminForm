package com.example.bot.api;

import com.example.bot.domain.FormType;
import com.example.bot.domain.Submission;
import com.example.bot.repo.SubmissionRepository;
import com.example.bot.service.AssigneeService;
import com.example.bot.service.Notifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    private final String webhookToken;
    private final SubmissionRepository repo;
    private final Notifier notifier;
    private final AssigneeService assignee;

    public FormController(@Value("${app.webhookToken}") String webhookToken,
                          SubmissionRepository repo,
                          Notifier notifier,
                          AssigneeService assignee) {
        this.webhookToken = webhookToken;
        this.repo = repo;
        this.notifier = notifier;
        this.assignee = assignee;
    }

    @PostMapping
    public ResponseEntity<String> handleForm(
            @RequestHeader("X-Webhook-Token") String token,
            @RequestBody Map<String, Object> body
    ) {
        // 1) проверка секрета
        if (!webhookToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid token");
        }

        // 2) распарсим тип (поддерживаем и 'type': A|B|C, и 'formType': A_CALLBACK|...)
        FormType formType = resolveFormType(body);
        if (formType == null) {
            return ResponseEntity.badRequest().body("Unknown or missing form type");
        }

        // 3) соберём сущность
        Submission s = new Submission();
        s.setFormType(formType);
        s.setName(asString(body.get("name")));
        s.setPhone(asString(body.get("phone")));
        s.setQuestionText(asString(body.get("question")));
        s.setOrderIdSql(asLong(body.get("orderIdSql")));
        s.setProductSku(asString(body.get("sku")));
        s.setProductUrl(asString(body.get("productUrl")));
        s.setAmount(asBigDecimal(body.get("amount")));
        s.setCreatedAt(OffsetDateTime.now()); // << фикс: OffsetDateTime, не Instant

        // назначим исполнителя по твоему сервису (метод resolveAssignee)
        s.setAssignedToChatId(assignee.resolveAssignee(formType));

        // 4) сохраним и уведомим
        repo.save(s);
        notifier.notifyAdmins(s);

        return ResponseEntity.accepted().body("OK");
    }

    private FormType resolveFormType(Map<String, Object> body) {
        String t = asString(body.get("type"));
        if (t != null) {
            switch (t.trim().toUpperCase()) {
                case "A": return FormType.A_CALLBACK;
                case "B": return FormType.B_CONSULT;
                case "C": return FormType.C_FAST_ORDER;
            }
        }
        String ft = asString(body.get("formType"));
        if (ft != null) {
            try {
                return FormType.valueOf(ft.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString().trim()); } catch (Exception e) { return null; }
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString().trim()); } catch (Exception e) { return null; }
    }
}