package com.example.bot.tele;

import com.example.bot.domain.FormType;
import com.example.bot.domain.Submission;
import com.example.bot.repo.SubmissionRepository;
import com.example.bot.service.AdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import java.time.*;
import java.util.*;
import java.util.Locale;
@Component
public class AdminBot extends TelegramLongPollingBot {

    private final String username;
    private final SubmissionRepository repo;
    private final AdminService adminService;

    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("ru"));
    private static final DateTimeFormatter ISO_D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static class RangePickState {
        YearMonth month;
        LocalDate start;
        LocalDate end;
    }
    private final Map<Long, RangePickState> rangePick = new HashMap<>();

    public AdminBot(@Value("${telegram.bot.token}") String token,
                    @Value("${telegram.bot.username}") String username,
                    SubmissionRepository repo,
                    AdminService adminService) {
        super(token);
        this.username = username;
        this.repo = repo;
        this.adminService = adminService;
        try {
            List<BotCommand> cmds = List.of(
                    new BotCommand("/start", "начать"),
                    new BotCommand("/help", "справка"),
                    new BotCommand("/whoami", "узнать свой chat_id"),
                    new BotCommand("/today", "заявки за сегодня"),
                    new BotCommand("/last", "последняя заявка"),
                    new BotCommand("/search", "поиск заявок"),
                    new BotCommand("/stats", "статистика за сегодня"),
                    new BotCommand("/listadmins", "список админов"),
                    new BotCommand("/addadmin", "сгенерировать инвайт"),
                    new BotCommand("/acceptinvite", "принять приглашение")
            );
            execute(new SetMyCommands(cmds, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return username;
    }
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();

            if (awaitingSearch.getOrDefault(chatId, false) && !text.startsWith("/")) {
                awaitingSearch.put(chatId, false);
                sendSearch(chatId, text);
                return;
            }

            if ("🔎 Поиск".equals(text)) {
                awaitingSearch.put(chatId, true);
                sendHtmlWithKb(chatId, "Введите текст для поиска (имя, телефон или фразу из вопроса) ⌨️");
                return;
            } else if ("📊 Статистика".equals(text)) {
                sendStatsMenu(chatId);
                return;
            } else if ("⭐️ Подписка".equals(text)) {
                sendHtmlWithKb(chatId, "⭐️ Раздел подписки в разработке. Скоро добавим пробный период и тарифы.");
                return;
            }

            String[] parts = text.split(" ", 2);
            String command = parts[0];

            switch (command) {
                case "/start" -> {
                    String username = (update.getMessage().getFrom() != null)
                            ? update.getMessage().getFrom().getUserName()
                            : null;
                    String payload = (text.length() > 6) ? text.substring(6).trim() : "";

                    if (!payload.isEmpty()) {
                        sendHtmlWithKb(chatId, adminService.handleAcceptInvite(chatId, payload, username));
                    } else {
                        sendHtmlWithKb(chatId,
                                "👋 Привет!\n" +
                                        "Я админ-бот.\n" +
                                        "Используйте /help чтобы увидеть список команд.");
                    }
                }
                case "/acceptinvite" -> {
                    String username = (update.getMessage().getFrom() != null)
                            ? update.getMessage().getFrom().getUserName()
                            : null;

                    String codeArg = (parts.length > 1) ? parts[1] : null;
                    if (codeArg == null || codeArg.isBlank()) {
                        sendHtmlWithKb(chatId, "⚠️ Использование: <code>/acceptinvite код</code>");
                    } else {
                        sendHtmlWithKb(chatId, adminService.handleAcceptInvite(chatId, codeArg, username));
                    }
                }
                case "/whoami" -> sendHtmlWithKb(chatId, "🆔 Ваш chat_id: <code>" + chatId + "</code>");
                case "/help" -> sendHtmlWithKb(chatId,
                        "🆘 <b>Справка по командам</b>:\n\n" +
                                "🆔 /whoami – узнать свой chat_id\n" +
                                "📅 /today – заявки за сегодня\n" +
                                "📝 /last – последняя заявка\n" +
                                "🔎 /search <code>текст</code> – поиск заявок\n" +
                                "📊 /stats – статистика за сегодня\n" +
                                "👥 /listadmins – список админов\n" +
                                "➕ /addadmin – сгенерировать инвайт (только SUPERADMIN)\n" +
                                "✅ /acceptinvite <code>код</code> – принять приглашение");

                case "/today" -> sendToday(chatId);
                case "/last" -> sendLast(chatId);
                case "/search" -> {
                    if (parts.length < 2) {
                        awaitingSearch.put(chatId, true);
                        sendHtmlWithKb(chatId, "⚠️ Введите текст для поиска (или пришлите в одном сообщении: <code>/search Иван</code>)");
                    } else {
                        sendSearch(chatId, parts[1]);
                    }
                }
                case "/stats" -> sendStatsMenu(chatId);
                case "/listadmins" -> sendHtmlWithKb(chatId, adminService.handleListAdmins());
                case "/addadmin" -> {
                    try {
                        UUID code = adminService.createInvite(chatId);

                        String deepLink = "https://t.me/" + getBotUsername() + "?start=" + code;

                        String shareText =
                                "───────────────\n" +
                                        "🤝 Привет! Тебя приглашают стать администратором.\n\n" +
                                        "👉 Нажми ссылку выше, чтобы открыть бота и принять приглашение.\n\n" +
                                        "Если ссылка не сработает, скопируй и отправь боту команду:\n" +
                                        "/acceptinvite " + code + "\n"
 +
                                "───────────────";

                        String shareUrl = "https://t.me/share/url"
                                + "?url=" + java.net.URLEncoder.encode(deepLink, java.nio.charset.StandardCharsets.UTF_8)
                                + "&text=" + java.net.URLEncoder.encode(shareText, java.nio.charset.StandardCharsets.UTF_8);

                        InlineKeyboardMarkup kb = new InlineKeyboardMarkup(
                                java.util.List.of(
                                        java.util.List.of(
                                                InlineKeyboardButton.builder()
                                                        .text("📤 Поделиться").url(shareUrl).build())));

                        String msg = """
                                🔑 <b>Приглашение создано!</b>
                                Код для коллеги: <code>%s</code>

                                Нажмите «Поделиться», чтобы отправить ему ссылку и команду.
                                """.formatted(code);

                        var sm = org.telegram.telegrambots.meta.api.methods.send.SendMessage.builder()
                                .chatId(Long.toString(chatId))
                                .parseMode("HTML")
                                .disableWebPagePreview(true)
                                .text(msg)
                                .replyMarkup(kb)
                                .build();
                        execute(sm);

                    } catch (IllegalStateException noRights) {
                        sendHtmlWithKb(chatId, "⚠️ У вас нет прав для добавления админов (нужна роль <b>SUPERADMIN</b>).");
                    } catch (Exception e) {
                        sendHtmlWithKb(chatId, "❌ Не удалось создать приглашение. Попробуйте позже.");
                    }
                }
            }
        }
        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (data.startsWith("cal:")) {
                RangePickState st = rangePick.computeIfAbsent(chatId, k -> {
                    RangePickState s = new RangePickState();
                    s.month = YearMonth.now();
                    return s;
                });

                try {
                    switch (data) {
                        case "cal:noop" -> {
                            // игнор
                        }
                        case "cal:nav:prev" -> {
                            st.month = st.month.minusMonths(1);
                            editCalendarMessage(update, st, currentCaption(st));
                        }
                        case "cal:nav:next" -> {
                            st.month = st.month.plusMonths(1);
                            editCalendarMessage(update, st, currentCaption(st));
                        }
                        case "cal:reset" -> {
                            st.start = null; st.end = null;
                            editCalendarMessage(update, st, "Выберите дату <b>С</b> (начало периода):");
                        }
                        case "cal:ok" -> {
                            if (st.start != null && st.end != null) {
                                OffsetDateTime from = st.start.atStartOfDay().atOffset(ZoneOffset.UTC);
                                OffsetDateTime to   = st.end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                                sendStatsByRange(chatId, from, to,
                                        "c " + st.start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) +
                                                " по " + st.end.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
                                rangePick.remove(chatId);
                            }
                        }
                        default -> {
                            if (data.startsWith("cal:pick:")) {
                                LocalDate picked = LocalDate.parse(data.substring("cal:pick:".length()));
                                if (st.start == null) {
                                    st.start = picked;
                                    st.end = null;
                                    st.month = YearMonth.from(picked);
                                    editCalendarMessage(update, st, "Выберите дату <b>По</b> (конец периода):");
                                } else if (st.end == null) {
                                    if (picked.isBefore(st.start)) {
                                        LocalDate tmp = st.start;
                                        st.start = picked;
                                        st.end = tmp;
                                    } else {
                                        st.end = picked;
                                    }
                                    st.month = YearMonth.from(picked);
                                    editCalendarMessage(update, st, "Период выбран. Нажмите <b>Готово</b> ✅");
                                } else {
                                    st.start = picked;
                                    st.end = null;
                                    st.month = YearMonth.from(picked);
                                    editCalendarMessage(update, st, "Выберите дату <b>По</b> (конец периода):");
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                try {
                    execute(AnswerCallbackQuery.builder()
                            .callbackQueryId(update.getCallbackQuery().getId())
                            .text("Ок")
                            .showAlert(false)
                            .build());
                } catch (Exception ignored) {}
                return;
            }
            switch (data) {
                case "stats:today" -> {
                    OffsetDateTime from = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
                    OffsetDateTime to = LocalDate.now().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    sendStatsByRange(chatId, from, to, "за сегодня");
                }
                case "stats:week" -> {
                    LocalDate today = LocalDate.now();
                    OffsetDateTime from = today.minusDays(6).atStartOfDay().atOffset(ZoneOffset.UTC);
                    OffsetDateTime to = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
                    sendStatsByRange(chatId, from, to, "за 7 дней");
                }
                case "stats:all" -> sendStatsAll(chatId);
                case "stats:custom" -> {
                    RangePickState st = new RangePickState();
                    st.month = YearMonth.now();
                    rangePick.put(chatId, st);
                    showCalendar(chatId, st, "Выберите дату <b>С</b> (начало периода):");
                }
            }
            return;
        }
    }

    private void sendHtml(long chatId, String html) {
        SendMessage sm = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .text(html)
                .build();
        try { execute(sm); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void sendToday(long chatId) {
        LocalDate today = LocalDate.now();
        OffsetDateTime from = today.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Submission> subs = repo.findByDateRange(from, to);
        if (subs.isEmpty()) {
            sendHtml(chatId, "📭 Сегодня заявок нет.");
            return;
        }

        StringBuilder sb = new StringBuilder("📅 <b>Заявки за сегодня</b>\n\n");
        for (Submission s : subs) {
            sb.append(formatSubmission(s)).append("\n\n");
        }
        sendHtml(chatId, sb.toString());
    }

    private void sendLast(long chatId) {
        List<Submission> subs = repo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        if (subs.isEmpty()) {
            sendHtml(chatId, "📭 Заявок пока нет.");
            return;
        }
        Submission s = subs.get(0);
        sendHtml(chatId, "📝 <b>Последняя заявка</b>\n\n" + formatSubmission(s));
    }

    private void sendSearch(long chatId, String query) {
        if (query == null || query.isBlank()) {
            sendHtmlWithKb(chatId, "⚠️ Пустой запрос.");
            return;
        }

        String q = query.trim();
        String qLower = q.toLowerCase();

        LocalDate dateFilter = parseDateQuery(q);
        final LocalDate finalDateFilter = dateFilter;

        List<Submission> all = repo.findAll();

        List<Submission> filtered = all.stream()
                .filter(s -> {
                    boolean textMatch =
                            (s.getName() != null && s.getName().toLowerCase().contains(qLower)) ||
                                    (s.getPhone() != null && s.getPhone().toLowerCase().contains(qLower)) ||
                                    (s.getQuestionText() != null && s.getQuestionText().toLowerCase().contains(qLower)) ||
                                    (s.getProductUrl() != null && s.getProductUrl().toLowerCase().contains(qLower)) ||
                                    (s.getProductSku() != null && s.getProductSku().toLowerCase().contains(qLower)) ||
                                    (s.getOrderIdSql() != null && String.valueOf(s.getOrderIdSql()).contains(qLower)) ||
                                    (s.getAmount() != null && String.valueOf(s.getAmount()).contains(qLower));
                    boolean dateMatch = false;
                    if (finalDateFilter != null && s.getCreatedAt() != null) {
                        LocalDate createdDate = s.getCreatedAt().toLocalDate();
                        dateMatch = createdDate.equals(finalDateFilter);
                    }

                    return textMatch || dateMatch;
                })
                .sorted(Comparator.comparing(Submission::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(30)
                .toList();

        if (filtered.isEmpty()) {
            sendHtmlWithKb(chatId, "🔎 По запросу <b>" + escapeHtml(q) + "</b> ничего не найдено.");
            return;
        }

        StringBuilder sb = new StringBuilder("🔎 Найдено <b>")
                .append(filtered.size()).append("</b> заявок:\n\n");
        for (Submission s : filtered) {
            sb.append(formatSubmission(s)).append("\n\n");
        }
        sendHtmlWithKb(chatId, sb.toString());
    }

    private LocalDate parseDateQuery(String q) {
        try {
            // dd.MM.yyyy
            return LocalDate.parse(q.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception ignore) {}
        try {
            // yyyy-MM-dd
            return LocalDate.parse(q.trim());
        } catch (Exception ignore) {}
        return null;
    }
    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private String formatSubmission(Submission s) {
        String ts = s.getCreatedAt() != null
                ? "🕒 " + TS_FMT.format(s.getCreatedAt()) + "\n"
                : "";

        return switch (s.getFormType()) {
            case A_CALLBACK -> "📞 <b>Обратный звонок</b>\n" +
                    ts +
                    "👤 " + nz(s.getName()) + "\n" +
                    "📱 " + nz(s.getPhone());

            case B_CONSULT -> "❓ <b>Консультация</b>\n" +
                    ts +
                    "👤 " + nz(s.getName()) + "\n" +
                    "📱 " + nz(s.getPhone()) + "\n" +
                    "💬 " + nz(s.getQuestionText());

            case C_FAST_ORDER -> "🛒 <b>Быстрый заказ</b>\n" +
                    ts +
                    "📦 Заказ: <code>" + nz(String.valueOf(s.getOrderIdSql())) + "</code>\n" +
                    "🔗 " + nz(s.getProductUrl()) + "\n" +
                    "💲 " + nz(String.valueOf(s.getAmount())) + "\n" +
                    "👤 " + nz(s.getName()) + "\n" +
                    "📱 " + nz(s.getPhone());
        };
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private final Map<Long, Boolean> awaitingSearch = new ConcurrentHashMap<>();

    private ReplyKeyboardMarkup mainKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🔎 Поиск"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📊 Статистика"));
        row2.add(new KeyboardButton("⭐️ Подписка"));

        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup();
        kb.setKeyboard(List.of(row1, row2));
        kb.setResizeKeyboard(true);
        kb.setSelective(false);
        return kb;
    }

    private void sendHtmlWithKb(long chatId, String html) {
        SendMessage sm = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .text(html)
                .replyMarkup(mainKeyboard())
                .build();
        try { execute(sm); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private InlineKeyboardMarkup statsKeyboard() {
        InlineKeyboardButton b1 = InlineKeyboardButton.builder().text("📅 За сегодня").callbackData("stats:today").build();
        InlineKeyboardButton b2 = InlineKeyboardButton.builder().text("🗓 За неделю").callbackData("stats:week").build();
        InlineKeyboardButton b3 = InlineKeyboardButton.builder().text("∞ За всё время").callbackData("stats:all").build();
        InlineKeyboardButton b4 = InlineKeyboardButton.builder().text("✍️ Выбрать период").callbackData("stats:custom").build();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(b1, b2));
        rows.add(List.of(b3));
        rows.add(List.of(b4));

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(rows);
        return kb;
    }

    private void sendStatsMenu(long chatId) {
        SendMessage sm = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .parseMode("HTML")
                .text("📊 <b>Статистика</b>\nВыберите период:")
                .replyMarkup(statsKeyboard())
                .build();
        try { execute(sm); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void sendStatsByRange(long chatId, OffsetDateTime from, OffsetDateTime to, String label) {
        List<Submission> subs = repo.findByDateRange(from, to);

        long countA = subs.stream().filter(s -> s.getFormType() == FormType.A_CALLBACK).count();
        long countB = subs.stream().filter(s -> s.getFormType() == FormType.B_CONSULT).count();
        long countC = subs.stream().filter(s -> s.getFormType() == FormType.C_FAST_ORDER).count();

        String msg = """
            📊 <b>Статистика %s</b>
            ⏱️ <code>%s</code> — <code>%s</code>
            
            📞 Обратные звонки: <b>%d</b>
            ❓ Консультации: <b>%d</b>
            🛒 Быстрые заказы: <b>%d</b>
            📦 Итого: <b>%d</b>
            """.formatted(
                label,
                fmtTs(from), fmtTs(to.minusSeconds(1)),
                countA, countB, countC, subs.size()
        );

        sendHtmlWithKb(chatId, msg);
    }

    private void sendStatsAll(long chatId) {
        List<Submission> subs = repo.findAll();
        if (subs.isEmpty()) {
            sendHtmlWithKb(chatId, "📊 Нет данных для статистики.");
            return;
        }
        long countA = subs.stream().filter(s -> s.getFormType() == FormType.A_CALLBACK).count();
        long countB = subs.stream().filter(s -> s.getFormType() == FormType.B_CONSULT).count();
        long countC = subs.stream().filter(s -> s.getFormType() == FormType.C_FAST_ORDER).count();

        Submission oldest = subs.stream().min(Comparator.comparing(Submission::getCreatedAt)).get();
        Submission newest = subs.stream().max(Comparator.comparing(Submission::getCreatedAt)).get();

        String msg = """
            📊 <b>Статистика за всё время</b>
            ⏱️ <code>%s</code> — <code>%s</code>
            
            📞 Обратные звонки: <b>%d</b>
            ❓ Консультации: <b>%d</b>
            🛒 Быстрые заказы: <b>%d</b>
            📦 Итого: <b>%d</b>
            """.formatted(
                fmtTs(oldest.getCreatedAt()),
                fmtTs(newest.getCreatedAt()),
                countA, countB, countC, subs.size()
        );

        sendHtmlWithKb(chatId, msg);
    }

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private String fmtTs(Instant i) { return TS.format(i); }
    private String fmtTs(OffsetDateTime odt) { return TS.format(odt.toInstant()); }

    private void editCalendarMessage(Update update, RangePickState st, String caption) {
        EditMessageText em = EditMessageText.builder()
                .chatId(update.getCallbackQuery().getMessage().getChatId().toString())
                .messageId(update.getCallbackQuery().getMessage().getMessageId())
                .parseMode("HTML")
                .text("📅 <b>" + st.month.format(YM_FMT) + "</b>\n" + caption)
                .replyMarkup(buildCalendarMarkup(st))
                .build();
        try { execute(em); } catch (Exception ignored) {}
    }

    private String currentCaption(RangePickState st) {
        if (st.start == null) return "Выберите дату <b>С</b> (начало периода):";
        if (st.end == null)   return "Выберите дату <b>По</b> (конец периода):";
        return "Период выбран. Нажмите <b>Готово</b> ✅";
    }

    private void showCalendar(long chatId, RangePickState st, String caption) {
        InlineKeyboardMarkup kb = buildCalendarMarkup(st);
        SendMessage sm = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .parseMode("HTML")
                .text("📅 <b>" + st.month.format(YM_FMT) + "</b>\n" + caption)
                .replyMarkup(kb)
                .build();
        try { execute(sm); } catch (Exception ignored) {}
    }

    private InlineKeyboardMarkup buildCalendarMarkup(RangePickState st) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                InlineKeyboardButton.builder().text("«").callbackData("cal:nav:prev").build(),
                InlineKeyboardButton.builder().text(st.month.format(YM_FMT)).callbackData("cal:noop").build(),
                InlineKeyboardButton.builder().text("»").callbackData("cal:nav:next").build()
        ));

        rows.add(List.of(
                txt("Пн"), txt("Вт"), txt("Ср"), txt("Чт"), txt("Пт"), txt("Сб"), txt("Вс")
        ));

        LocalDate first = st.month.atDay(1);
        int shift = (first.getDayOfWeek().getValue() + 6) % 7; // сделать Пн=0 … Вс=6
        LocalDate cursor = first.minusDays(shift);
        for (int r = 0; r < 6; r++) {
            List<InlineKeyboardButton> week = new ArrayList<>();
            for (int c = 0; c < 7; c++) {
                boolean inMonth = cursor.getMonth() == st.month.getMonth();
                String label = String.valueOf(cursor.getDayOfMonth());
                String data = "cal:pick:" + ISO_D.format(cursor);

                if (st.start != null && st.end != null && !cursor.isBefore(st.start) && !cursor.isAfter(st.end)) {
                    label = "•" + label + "•";
                } else if (st.start != null && cursor.equals(st.start)) {
                    label = "[" + label + "]";
                } else if (st.end != null && cursor.equals(st.end)) {
                    label = "[" + label + "]";
                } else if (!inMonth) {
                    label = " " + label + " ";
                }

                week.add(InlineKeyboardButton.builder()
                        .text(label)
                        .callbackData(data)
                        .build());
                cursor = cursor.plusDays(1);
            }
            rows.add(week);
        }

        List<InlineKeyboardButton> last = new ArrayList<>();
        last.add(InlineKeyboardButton.builder().text("↺ Сброс").callbackData("cal:reset").build());
        if (st.start != null && st.end != null) {
            last.add(InlineKeyboardButton.builder().text("✅ Готово").callbackData("cal:ok").build());
        }
        rows.add(last);

        InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
        kb.setKeyboard(rows);
        return kb;
    }

    private InlineKeyboardButton txt(String t) {
        return InlineKeyboardButton.builder().text(t).callbackData("cal:noop").build();
    }
}