package com.example.lateMng.bot.admin;

import com.example.lateMng.bot.BotMessages;
import com.example.lateMng.bot.FsmStates;
import com.example.lateMng.bot.Keyboards;
import com.example.lateMng.entity.Department;
import com.example.lateMng.entity.Report;
import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.api.methods.send.SendDocument;
import com.kaleert.nyagram.api.objects.replykeyboard.ReplyKeyboardMarkup;
import com.kaleert.nyagram.api.objects.replykeyboard.buttons.KeyboardButton;
import com.kaleert.nyagram.client.NyagramClient;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.fsm.annotation.StateAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatsAndManualReportHandler {

    private final UserService userService;
    private final SessionManager sessionManager;
    private final NyagramClient nyagramClient;

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Общая статистика

    private static ReplyKeyboardMarkup statsPeriodKeyboard() {
        return ReplyKeyboardMarkup.vertical(true,
                "За неделю", "За месяц",
                "📄 Экспорт за неделю", "📄 Экспорт за месяц",
                "Назад");
    }

    // Статистика (главное меню)

    /**
     * Начальник: свой отдел + общий
     * Ответственный: все отделы + общий
     */
    public void enterStatsFromMainMenu(CommandContext ctx, User user) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session == null) {
            session = sessionManager.startSession(ctx.getUserId(), ctx.getChatId(), FsmStates.STATS_SCOPE);
        } else {
            sessionManager.updateState(ctx.getUserId(), FsmStates.STATS_SCOPE);
        }

        boolean isManager = "manager".equals(user.getRole());
        boolean isSupervisor = Boolean.TRUE.equals(user.getIsSupervisor());

        var rows = new ArrayList<List<KeyboardButton>>();

        if (isManager && user.getDepartment() != null) {
            rows.add(List.of(KeyboardButton.text("📁 " + user.getDepartment().getName())));
            session.putData("stats_own_dept_id", user.getDepartment().getId());
            session.putData("stats_own_dept_name", user.getDepartment().getName());
        }

        if (isSupervisor) {
            List<Department> depts = userService.getAllDepartments();
            for (Department d : depts) {
                if (user.getDepartment() == null || !d.getId().equals(user.getDepartment().getId())) {
                    rows.add(List.of(KeyboardButton.text("📁 " + d.getName())));
                }
            }
            // сохраним ID отделов для поиска
            session.putData("stats_all_depts", true);
        }

        rows.add(List.of(KeyboardButton.text("📊 Все отделы")));
        rows.add(List.of(KeyboardButton.text("Назад")));

        ReplyKeyboardMarkup kb = ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();

        ctx.reply("<b>📊 СТАТИСТИКА</b>\n\nВыберите область:", "HTML", null, kb);
    }

    @StateAction(FsmStates.STATS_SCOPE)
    public void onStatsScope(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            goBackToMainMenu(ctx);
            return;
        }

        Integer deptId = null;
        String scopeLabel;

        if ("📊 Все отделы".equals(text)) {
            scopeLabel = "все отделы";
        } else if (text.startsWith("📁 ")) {
            String deptName = text.substring(3);
            Department dept = userService.getAllDepartments().stream()
                    .filter(d -> d.getName().equals(deptName))
                    .findFirst().orElse(null);
            if (dept == null) {
                ctx.reply(BotMessages.err("Отдел не найден."), "HTML", null, Keyboards.back());
                return;
            }
            deptId = dept.getId();
            scopeLabel = deptName;
        } else {
            ctx.reply(BotMessages.err("Выберите область."), "HTML", null, Keyboards.back());
            return;
        }

        session.putData("stats_dept_id", deptId);
        session.putData("stats_scope_label", scopeLabel);
        sessionManager.updateState(ctx.getUserId(), FsmStates.STATS_MAIN_PERIOD);
        ctx.reply("<b>📊 СТАТИСТИКА</b> (" + scopeLabel + ")\n\nВыберите период:", "HTML", null, statsPeriodKeyboard());
    }

    @StateAction(FsmStates.STATS_MAIN_PERIOD)
    public void onStatsMainPeriod(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            User user = userService.getUserWithDepartment(ctx.getUserId()).orElse(null);
            if (user != null) {
                enterStatsFromMainMenu(ctx, user);
            } else {
                goBackToMainMenu(ctx);
            }
            return;
        }

        PeriodInfo period = parsePeriod(text);
        if (period == null) {
            ctx.reply(BotMessages.err("Выберите период."), "HTML", null, statsPeriodKeyboard());
            return;
        }

        Integer deptId = session.getData("stats_dept_id", Integer.class);
        String scopeLabel = session.getData("stats_scope_label", String.class);

        List<Report> reports;
        if (deptId != null) {
            reports = userService.getReportsByDepartmentAndPeriod(deptId, period.from, period.to);
        } else {
            reports = userService.getReportsByPeriod(period.from, period.to);
        }

        String fullLabel = period.label + " (" + (scopeLabel != null ? scopeLabel : "все") + ")";
        if (reports.isEmpty()) {
            ctx.reply("<b>📊 СТАТИСТИКА</b>\n\nНет отчетов " + fullLabel + ".", "HTML", null, statsPeriodKeyboard());
            return;
        }

        if (period.isExport) {
            exportReportsCsv(ctx, reports, fullLabel, period.from, period.to);
        } else {
            ctx.reply(buildStatsMessage(reports, fullLabel), "HTML", null, statsPeriodKeyboard());
        }
    }

    // Общие методы для статистики

    private static class PeriodInfo {
        LocalDateTime from;
        LocalDateTime to;
        String label;
        boolean isExport;
    }

    private PeriodInfo parsePeriod(String text) {
        boolean isExport = text.startsWith("📄 Экспорт");
        String cleanText = text.replace("📄 Экспорт ", "");

        LocalDate today = LocalDate.now();
        PeriodInfo info = new PeriodInfo();
        info.to = today.plusDays(1).atStartOfDay();
        info.isExport = isExport;

        if (cleanText.contains("неделю") || "За неделю".equals(text)) {
            info.from = today.minusDays(7).atStartOfDay();
            info.label = "за последние 7 дней";
        } else if (cleanText.contains("месяц") || "За месяц".equals(text)) {
            info.from = today.minusDays(30).atStartOfDay();
            info.label = "за последние 30 дней";
        } else {
            return null;
        }
        return info;
    }

    private String buildStatsMessage(List<Report> reports, String periodLabel) {
        long lateCount = reports.stream().filter(r -> "late".equals(r.getReportType())).count();
        long absenceCount = reports.stream().filter(r -> "absence".equals(r.getReportType())).count();
        long manualCount = reports.stream().filter(r -> Boolean.TRUE.equals(r.getIsManual())).count();

        StringBuilder sb = new StringBuilder("<b>📊 СТАТИСТИКА</b> (" + periodLabel + ")\n\n");
        sb.append("📝 <b>Всего отчетов:</b> ").append(reports.size()).append("\n");
        sb.append("⏰ <b>Опоздания:</b> ").append(lateCount).append("\n");
        sb.append("🚫 <b>Отсутствия:</b> ").append(absenceCount).append("\n");
        sb.append("✋ <b>Ручные отметки:</b> ").append(manualCount).append("\n");

        sb.append("\n<b>Подробно:</b>\n");
        int shown = Math.min(reports.size(), 30);
        for (int i = 0; i < shown; i++) {
            Report r = reports.get(i);
            String type = "late".equals(r.getReportType()) ? "⏰" : "🚫";
            String manual = Boolean.TRUE.equals(r.getIsManual()) ? " ✋" : "";
            String date = r.getCreatedAt().format(DATETIME_FMT);
            sb.append(type).append(" ").append(date).append(" ")
                    .append(r.getUserFullName())
                    .append(" - ").append(r.getReason() != null ? r.getReason() : "")
                    .append(manual).append("\n");
        }
        if (reports.size() > shown) {
            sb.append("\n... и ещё ").append(reports.size() - shown).append(" записей.");
        }
        return sb.toString();
    }

    private void exportReportsCsv(CommandContext ctx, List<Report> reports, String periodLabel,
            LocalDateTime from, LocalDateTime to) {
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF'); // BOM для корректного открытия в Excel
        csv.append("Дата;Время;ФИО;Отдел;Тип;Причина;Опоздание;Ручная отметка;Отметил\n");

        DateTimeFormatter datePart = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter timePart = DateTimeFormatter.ofPattern("HH:mm");

        for (Report r : reports) {
            String date = r.getCreatedAt() != null ? r.getCreatedAt().format(datePart) : "";
            String time = r.getCreatedAt() != null ? r.getCreatedAt().format(timePart) : "";
            String type = "late".equals(r.getReportType()) ? "Опоздание" : "Отсутствие";
            String manual = Boolean.TRUE.equals(r.getIsManual()) ? "Да" : "Нет";
            String creator = r.getCreatedByName() != null ? r.getCreatedByName() : "";
            String reason = r.getReason() != null ? r.getReason().replace(";", ",") : "";
            String dept = r.getDepartmentName() != null ? r.getDepartmentName() : "";
            String name = r.getUserFullName() != null ? r.getUserFullName() : "";
            String timeVal = r.getTimeVal() != null ? r.getTimeVal() : "";

            csv.append(date).append(';')
                    .append(time).append(';')
                    .append(name).append(';')
                    .append(dept).append(';')
                    .append(type).append(';')
                    .append(reason).append(';')
                    .append(timeVal).append(';')
                    .append(manual).append(';')
                    .append(creator).append('\n');
        }

        String fileName = "report_" + from.format(FILE_DATE_FMT) + "_" + to.minusDays(1).format(FILE_DATE_FMT) + ".csv";

        try {
            byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
            var inputFile = new com.kaleert.nyagram.api.objects.InputFile(
                    new ByteArrayInputStream(bytes), fileName);
            var doc = SendDocument.builder()
                    .chatId(ctx.getChatId().toString())
                    .document(inputFile)
                    .caption("📊 Отчет " + periodLabel + " (" + reports.size() + " записей)")
                    .build();
            nyagramClient.execute(doc);
        } catch (Exception e) {
            log.error("Ошибка экспорта CSV: {}", e.getMessage(), e);
            ctx.reply(BotMessages.err("Ошибка при создании файла экспорта."), "HTML", null, statsPeriodKeyboard());
        }
    }

    // Ручная отметка

    @StateAction(FsmStates.MANUAL_REPORT_SELECT_USER)
    public void onSelectUser(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            goBackFromManualReport(ctx);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) session.getData("manual_user_ids", List.class);
            if (ids == null || num < 1 || num > ids.size()) {
                ctx.reply(BotMessages.err(BotMessages.MSG_BAD_NUMBER), "HTML", null, Keyboards.back());
                return;
            }
            Long targetId = ids.get(num - 1);
            User target = userService.getUserWithDepartment(targetId).orElse(null);
            if (target == null) {
                ctx.reply(BotMessages.err("Пользователь не найден."), "HTML", null, Keyboards.back());
                return;
            }
            session.putData("manual_target_id", targetId);
            session.putData("manual_target_name", target.getFullName());
            session.putData("manual_target_dept_id",
                    target.getDepartment() != null ? target.getDepartment().getId() : null);
            session.putData("manual_target_dept_name",
                    target.getDepartment() != null ? target.getDepartment().getName() : null);
            ctx.reply("<b>✋ РУЧНАЯ ОТМЕТКА</b>\n\n<b>Сотрудник:</b> " + target.getFullName()
                    + "\n\nВыберите тип:", "HTML", null,
                    ReplyKeyboardMarkup.vertical(true, "Опоздание", "Отсутствие", "Назад"));
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_TYPE);
        } catch (NumberFormatException e) {
            ctx.reply(BotMessages.err("Введите номер сотрудника."), "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.MANUAL_REPORT_TYPE)
    public void onSelectType(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            User caller = userService.getUserWithDepartment(ctx.getUserId()).orElse(null);
            showManualUsersList(ctx, session, caller);
            return;
        }
        String type;
        if ("Опоздание".equals(text))
            type = "late";
        else if ("Отсутствие".equals(text))
            type = "absence";
        else {
            ctx.reply(BotMessages.err("Выберите «Опоздание» или «Отсутствие»."), "HTML", null, Keyboards.back());
            return;
        }
        session.putData("manual_report_type", type);
        ctx.reply("Укажите причину:", "HTML", null,
                ReplyKeyboardMarkup.vertical(true, "Без причины", "Другое", "Назад"));
        sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_REASON);
    }

    @StateAction(FsmStates.MANUAL_REPORT_REASON)
    public void onSelectReason(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            String targetName = session.getData("manual_target_name", String.class);
            ctx.reply("<b>✋ РУЧНАЯ ОТМЕТКА</b>\n\n<b>Сотрудник:</b> " + targetName
                    + "\n\nВыберите тип:", "HTML", null,
                    ReplyKeyboardMarkup.vertical(true, "Опоздание", "Отсутствие", "Назад"));
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_TYPE);
            return;
        }
        if ("Другое".equals(text)) {
            ctx.reply("Напишите причину текстом:", "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_CUSTOM_REASON);
            return;
        }
        String reason = "Без причины".equals(text) ? "Без причины" : text;
        session.putData("manual_report_reason", reason);
        afterReasonCollected(ctx, session);
    }

    @StateAction(FsmStates.MANUAL_REPORT_CUSTOM_REASON)
    public void onCustomReason(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            ctx.reply("Укажите причину:", "HTML", null,
                    ReplyKeyboardMarkup.vertical(true, "Без причины", "Другое", "Назад"));
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_REASON);
            return;
        }
        session.putData("manual_report_reason", ctx.getText());
        afterReasonCollected(ctx, session);
    }

    /**
     * После сбора причины: если тип = late, спрашиваем длительность,
     * иначе сохраняем сразу.
     */
    private void afterReasonCollected(CommandContext ctx, UserSession session) {
        String reportType = session.getData("manual_report_type", String.class);
        if ("late".equals(reportType)) {
            ctx.reply("На сколько опоздал?", "HTML", null, Keyboards.time());
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_LATE_DURATION);
        } else {
            saveManualReport(ctx, session, session.getData("manual_report_reason", String.class), null);
        }
    }

    @StateAction(FsmStates.MANUAL_REPORT_LATE_DURATION)
    public void onLateDuration(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            ctx.reply("Укажите причину:", "HTML", null,
                    ReplyKeyboardMarkup.vertical(true, "Без причины", "Другое", "Назад"));
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_REASON);
            return;
        }
        if ("Другое".equals(text)) {
            ctx.reply("Укажите время опоздания текстом:", "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_CUSTOM_LATE_DURATION);
            return;
        }
        String reason = session.getData("manual_report_reason", String.class);
        saveManualReport(ctx, session, reason, text);
    }

    @StateAction(FsmStates.MANUAL_REPORT_CUSTOM_LATE_DURATION)
    public void onCustomLateDuration(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            ctx.reply("На сколько опоздал?", "HTML", null, Keyboards.time());
            sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_LATE_DURATION);
            return;
        }
        String reason = session.getData("manual_report_reason", String.class);
        saveManualReport(ctx, session, reason, ctx.getText());
    }

    private void saveManualReport(CommandContext ctx, UserSession session, String reason, String timeVal) {
        Long targetId = session.getData("manual_target_id", Long.class);
        String targetName = session.getData("manual_target_name", String.class);
        Integer deptId = session.getData("manual_target_dept_id", Integer.class);
        String deptName = session.getData("manual_target_dept_name", String.class);
        String reportType = session.getData("manual_report_type", String.class);

        User creator = userService.getUserWithDepartment(ctx.getUserId()).orElse(null);
        String creatorName = creator != null ? creator.getFullName() : "-";

        userService.saveReport(targetId, targetName, deptId, deptName,
                reportType, reason, timeVal, true, ctx.getUserId(), creatorName);

        String typeLabel = "late".equals(reportType) ? "Опоздание" : "Отсутствие";
        StringBuilder confirmMsg = new StringBuilder("<b>✅ ОТМЕТКА СОЗДАНА</b>\n\n");
        confirmMsg.append("<b>Сотрудник:</b> ").append(targetName).append("\n");
        confirmMsg.append("<b>Тип:</b> ").append(typeLabel).append("\n");
        confirmMsg.append("<b>Причина:</b> ").append(reason);
        if (timeVal != null) {
            confirmMsg.append("\n<b>Опоздание на:</b> ").append(timeVal);
        }

        goBackFromManualReport(ctx, confirmMsg.toString());
    }

    // Экран выбора сотрудника

    /**
     * Показывает список сотрудников для ручной отметки
     * Начальник видит только сотрудников своего отдела
     * ответственный видит всех
     */
    public void showManualUsersList(CommandContext ctx, UserSession session, User caller) {
        List<User> users;
        if (caller != null && "manager".equals(caller.getRole())
                && !Boolean.TRUE.equals(caller.getIsSupervisor())
                && caller.getDepartment() != null) {
            users = userService.getEmployeesInDepartment(caller.getDepartment().getId());
        } else {
            users = userService.getAllActiveUsers();
        }

        // убираем самого вызывающего из списка
        Long callerId = ctx.getUserId();
        users.removeIf(u -> u.getUserId().equals(callerId));

        // убираем тех, кто уже отметился/отмечен сегодня
        Set<Long> todayReported = userService.getTodayReportedUserIds();
        users.removeIf(u -> todayReported.contains(u.getUserId()));

        if (users.isEmpty()) {
            ctx.reply(BotMessages.err("Нет сотрудников для отметки."), "HTML", null, Keyboards.back());
            return;
        }
        StringBuilder sb = new StringBuilder("<b>✋ РУЧНАЯ ОТМЕТКА</b>\n\nВыберите сотрудника:\n\n");
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            sb.append(i + 1).append(". ").append(AdminFlowService.formatUserLine(u)).append("\n");
            ids.add(u.getUserId());
        }
        session.putData("manual_user_ids", ids);
        ctx.reply(sb.toString(), "HTML", null, Keyboards.back());
        sessionManager.updateState(ctx.getUserId(), FsmStates.MANUAL_REPORT_SELECT_USER);
    }

    // Навигация назад

    private void goBackFromManualReport(CommandContext ctx) {
        goBackFromManualReport(ctx, null);
    }

    private void goBackFromManualReport(CommandContext ctx, String extraMessage) {
        Long uid = ctx.getUserId();
        sessionManager.clearSession(uid);
        User user = userService.getUserWithDepartment(uid).orElse(null);
        ReplyKeyboardMarkup kb = user != null ? Keyboards.mainMenuFor(user) : Keyboards.back();
        String msg = extraMessage != null ? extraMessage : "<b>🏠 ГЛАВНОЕ МЕНЮ</b>";
        ctx.reply(msg, "HTML", null, kb);
    }

    private void goBackToMainMenu(CommandContext ctx) {
        Long uid = ctx.getUserId();
        sessionManager.clearSession(uid);
        User user = userService.getUserWithDepartment(uid).orElse(null);
        ReplyKeyboardMarkup kb = user != null ? Keyboards.mainMenuFor(user) : Keyboards.back();
        ctx.reply("<b>🏠 ГЛАВНОЕ МЕНЮ</b>", "HTML", null, kb);
    }

    private void goToAdminHome(CommandContext ctx) {
        Long uid = ctx.getUserId();
        sessionManager.clearSession(uid);
        sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
        ctx.reply("<b>⚙️ ПАНЕЛЬ АДМИНИСТРАТОРА</b>", "HTML", null, Keyboards.adminHome());
    }
}
