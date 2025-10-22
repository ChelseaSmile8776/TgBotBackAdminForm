package com.example.bot.service;

import com.example.bot.domain.Admin;
import com.example.bot.domain.Invite;
import com.example.bot.repo.AdminRepository;
import com.example.bot.repo.InviteRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminService {

    private final AdminRepository adminRepo;
    private final InviteRepository inviteRepo;
    private final List<Long> initialAdmins;

    public AdminService(AdminRepository adminRepo,
                        InviteRepository inviteRepo,
                        @Value("${app.admins:}") List<Long> initialAdmins) {
        this.adminRepo = adminRepo;
        this.inviteRepo = inviteRepo;
        this.initialAdmins = initialAdmins;
    }

    @PostConstruct
    @Transactional
    public void seedInitialAdmins() {
        if (initialAdmins == null || initialAdmins.isEmpty()) return;
        for (int i = 0; i < initialAdmins.size(); i++) {
            Long chatId = initialAdmins.get(i);
            if (chatId == null) continue;
            if (!adminRepo.existsByChatId(chatId)) {
                Admin admin = new Admin();
                admin.setChatId(chatId);
                admin.setRole(i == 0 ? "SUPERADMIN" : "ADMIN");
                // username не знаем — оставим null
                adminRepo.save(admin);
            }
        }
    }

    public boolean isSuperAdmin(Long chatId) {
        if (chatId == null) return false;
        return adminRepo.findByChatId(chatId)
                .map(a -> "SUPERADMIN".equalsIgnoreCase(a.getRole()))
                .orElse(false);
    }

    /** Показать список админов */
    public String handleListAdmins() {
        List<Admin> admins = adminRepo.findAll();
        if (admins.isEmpty()) return "❌ Администраторы не найдены.";

        StringBuilder sb = new StringBuilder("🛡 <b>Администраторы</b>\n");
        for (Admin a : admins) {
            sb.append("• <code>").append(a.getChatId()).append("</code> ");
            if (a.getUsername() != null && !a.getUsername().isBlank()) {
                sb.append("(@").append(a.getUsername()).append(") ");
            }
            sb.append("(").append(a.getRole()).append(")\n");
        }
        return sb.toString();
    }

    @Transactional
    public String handleAddAdmin(Long requesterChatId) {
        if (!isSuperAdmin(requesterChatId)) {
            return "⚠️ У вас нет прав для добавления админов (нужна роль <b>SUPERADMIN</b>).";
        }
        Invite invite = new Invite();
        invite.setCode(UUID.randomUUID());
        invite.setCreatedBy(requesterChatId);
        invite.setCreatedAt(Instant.now());
        inviteRepo.save(invite);

        UUID code = invite.getCode();
        return """
               🔑 <b>Приглашение создано!</b>
               Отправьте пользователю этот код:

               <code>%s</code>

               Затем он должен отправить боту:
               <code>/acceptinvite %s</code>
               """.formatted(code, code);
    }

    private String sanitizeInviteCode(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        // убрать невидимые юникод-символы, переносы, soft hyphen и пр.
        s = s
                .replace("\u200B", "")  // zero width space
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .replace("\u00AD", ""); // soft hyphen

        // заменить типографские тире на обычный минус
        s = s.replace('—','-').replace('–','-').replace('−','-');

        // убрать всё, что не цифра/латинская буква a-f/дефис
        s = s.replaceAll("[^0-9A-Fa-f-]", "");

        // поддержать 32-символьный HEX без дефисов -> вставим дефисы по маске UUID
        if (s.matches("(?i)^[0-9a-f]{32}$")) {
            s = s.replaceFirst("(?i)(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        }

        return s;
    }

    @Transactional
    public String handleAcceptInvite(Long chatId, String codeRaw, String username) {
        if (chatId == null) return "❌ Не удалось определить ваш chat_id.";

        String cleaned = sanitizeInviteCode(codeRaw);
        if (cleaned.isBlank()) {
            return "⚠️ Использование: <code>/acceptinvite код</code>";
        }

        UUID code;
        try {
            code = UUID.fromString(cleaned);
        } catch (IllegalArgumentException e) {
            return "❌ Неверный формат кода. Пример: <code>/acceptinvite 123e4567-e89b-12d3-a456-426614174000</code>";
        }

        var opt = inviteRepo.findByCode(code);
        if (opt.isEmpty()) return "❌ Код не найден или уже удалён.";
        var inv = opt.get();

        if (inv.getUsedAt() != null) {
            return "⚠️ Этот код уже был использован.";
        }

        // уже админ?
        if (adminRepo.existsByChatId(chatId)) {
            inv.setUsedBy(chatId);
            inv.setUsedAt(Instant.now());
            inviteRepo.save(inv);
            return "ℹ️ Вы уже являетесь администратором. Код помечен использованным.";
        }

        // создаём админа
        var a = new Admin();
        a.setChatId(chatId);
        a.setRole("ADMIN");
        a.setUsername(username); // если поле добавили
        adminRepo.save(a);

        inv.setUsedBy(chatId);
        inv.setUsedAt(Instant.now());
        inviteRepo.save(inv);

        return "✅ Вы добавлены как администратор!";
    }

    private String normalizeUsername(String u) {
        if (u == null) return null;
        String s = u.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("@")) s = s.substring(1);
        return s;
    }

    @Transactional
    public UUID createInvite(Long requesterChatId) {
        if (!isSuperAdmin(requesterChatId)) {
            throw new IllegalStateException("NO_SUPERADMIN");
        }
        Invite invite = new Invite();
        invite.setCode(UUID.randomUUID());
        invite.setCreatedBy(requesterChatId);
        invite.setCreatedAt(Instant.now());
        inviteRepo.save(invite);
        return invite.getCode();
    }
}