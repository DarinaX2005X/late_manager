package com.example.lateMng.bot.admin;

import com.example.lateMng.bot.BotMessages;
import com.example.lateMng.bot.BotNotificationService;
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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StatsAndManualReportHandler {

    private final UserService userService;
    private final SessionManager sessionManager;
    private final NyagramClient nyagramClient;
    private final BotNotificationService notificationService;

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILE_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static ReplyKeyboardMarkup statsPeriodKeyboard() {
        return ReplyKeyboardMarkup.vertical(true,
                "За неделю", "За месяц",
                "📄 Экспорт за неделю", "📄 Экспорт за месяц",
                "Назад");
    }

    public void enterStatsFromMainMenu(CommandContext ctx, User user) {
        UserSession session = sessionManager.getSession(ctx.getUserId());
        if (session == null)
            session = sessionManager.startSession(ctx.getUserId(), ctx.getChatId(), FsmStates.STATS_SCOPE);
        else
            sessionManager.updateState(ctx.getUserId(), FsmStates.STATS_SCOPE);

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

        if (cleanText.contains("неделю")) {
            info.from = today.minusDays(7).atStartOfDay();
            info.label = "за последние 7 дней";
        } else if (cleanText.contains("месяц")) {
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

        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (Report r : reports) {
            long[] arr = counts.computeIfAbsent(r.getUserId(), k -> new long[2]);
            names.put(r.getUserId(), r.getUserFullName() != null ? r.getUserFullName() : "—");
            if ("late".equals(r.getReportType()))
                arr[0]++;
            else
                arr[1]++;
        }

        sb.append("\n<b>По сотрудникам:</b>\n");
        sb.append("<code>").append(String.format("%-20s  ⏰  🚫%n", "Сотрудник"));

        counts.entrySet().stream()
                .sorted(java.util.Comparator.<Map.Entry<Long, long[]>>comparingLong(
                        e -> -(e.getValue()[0] + e.getValue()[1]))
                        .thenComparingLong(e -> -e.getValue()[1]))
                .forEach(entry -> {
                    Long uid = entry.getKey();
                    long late = entry.getValue()[0];
                    long absence = entry.getValue()[1];
                    String name = names.get(uid);
                    if (name.length() > 20)
                        name = name.substring(0, 19) + "…";
                    sb.append(String.format("%-20s  %2d  %2d%n", name, late, absence));
                });

        sb.append("</code>");
        return sb.toString();
    }

    private void exportReportsCsv(CommandContext ctx, List<Report> reports, String periodLabel,
            LocalDateTime from, LocalDateTime to) {

        boolean multiDept = reports.stream()
                .map(r -> r.getDepartmentName() != null ? r.getDepartmentName() : "")
                .distinct().count() > 1;

        String fileName = "report_" + from.format(FILE_DATE_FMT) + "_" + to.minusDays(1).format(FILE_DATE_FMT)
                + ".xlsx";

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Лист 1: все отчеты
            Sheet detailSheet = wb.createSheet("Отчеты");
            String[] detailHeaders = multiDept
                    ? new String[] { "Дата", "Время", "ФИО", "Отдел", "Тип", "Причина", "Опоздание", "Ручная отметка",
                            "Отметил" }
                    : new String[] { "Дата", "Время", "ФИО", "Тип", "Причина", "Опоздание", "Ручная отметка",
                            "Отметил" };
            writeHeaderRow(detailSheet, detailHeaders, headerStyle);

            DateTimeFormatter datePart = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            DateTimeFormatter timePart = DateTimeFormatter.ofPattern("HH:mm");
            int rowIdx = 1;
            for (Report r : reports) {
                Row row = detailSheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(r.getCreatedAt() != null ? r.getCreatedAt().format(datePart) : "");
                row.createCell(col++).setCellValue(r.getCreatedAt() != null ? r.getCreatedAt().format(timePart) : "");
                row.createCell(col++).setCellValue(r.getUserFullName() != null ? r.getUserFullName() : "");
                if (multiDept)
                    row.createCell(col++).setCellValue(r.getDepartmentName() != null ? r.getDepartmentName() : "");
                row.createCell(col++).setCellValue("late".equals(r.getReportType()) ? "Опоздание" : "Отсутствие");
                row.createCell(col++).setCellValue(r.getReason() != null ? r.getReason() : "");
                row.createCell(col++).setCellValue(r.getTimeVal() != null ? r.getTimeVal() : "");
                row.createCell(col++).setCellValue(Boolean.TRUE.equals(r.getIsManual()) ? "Да" : "Нет");
                row.createCell(col).setCellValue(r.getCreatedByName() != null ? r.getCreatedByName() : "");
            }
            detailSheet.setAutoFilter(new CellRangeAddress(0, rowIdx - 1, 0, detailHeaders.length - 1));
            for (int i = 0; i < detailHeaders.length; i++)
                detailSheet.autoSizeColumn(i);

            // Листы со сводками
            if (multiDept) {
                Map<String, List<Report>> byDept = new LinkedHashMap<>();
                for (Report r : reports) {
                    String dept = r.getDepartmentName() != null ? r.getDepartmentName() : "Без отдела";
                    byDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(r);
                }
                for (Map.Entry<String, List<Report>> entry : byDept.entrySet()) {
                    String sheetName = entry.getKey().length() > 30
                            ? entry.getKey().substring(0, 30)
                            : entry.getKey();
                    writeSummarySheet(wb, sheetName, entry.getValue(), false, headerStyle);
                }
                writeSummarySheet(wb, "Общая сводка", reports, true, headerStyle);
            } else {
                writeSummarySheet(wb, "Сводка", reports, false, headerStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            byte[] bytes = out.toByteArray();

            var inputFile = new com.kaleert.nyagram.api.objects.InputFile(
                    new ByteArrayInputStream(bytes), fileName);
            var doc = SendDocument.builder()
                    .chatId(ctx.getChatId().toString())
                    .document(inputFile)
                    .caption("📊 Отчет " + periodLabel + " (" + reports.size() + " записей)")
                    .build();
            nyagramClient.execute(doc);
        } catch (Exception e) {
            log.error("Ошибка экспорта XLSX: {}", e.getMessage(), e);
            ctx.reply(BotMessages.err("Ошибка при создании файла экспорта."), "HTML", null, statsPeriodKeyboard());
        }
    }

    private void writeSummarySheet(XSSFWorkbook wb, String sheetName, List<Report> reports,
            boolean includeDept, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet(sheetName);
        String[] headers = includeDept
                ? new String[] { "Сотрудник", "Отдел", "Опоздания", "Отсутствия" }
                : new String[] { "Сотрудник", "Опоздания", "Отсутствия" };
        writeHeaderRow(sheet, headers, headerStyle);

        Map<Long, long[]> counts = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, String> depts = new LinkedHashMap<>();

        for (Report r : reports) {
            long[] arr = counts.computeIfAbsent(r.getUserId(), k -> new long[2]);
            names.put(r.getUserId(), r.getUserFullName() != null ? r.getUserFullName() : "");
            depts.put(r.getUserId(), r.getDepartmentName() != null ? r.getDepartmentName() : "");
            if ("late".equals(r.getReportType()))
                arr[0]++;
            else
                arr[1]++;
        }

        int[] rowRef = { 1 };
        counts.entrySet().stream()
                .sorted(java.util.Comparator.<Map.Entry<Long, long[]>>comparingLong(e -> -(e.getValue()[1]))
                        .thenComparingLong(e -> -(e.getValue()[0])))
                .forEach(entry -> {
                    Row row = sheet.createRow(rowRef[0]++);
                    int col = 0;
                    Long uid = entry.getKey();
                    row.createCell(col++).setCellValue(names.get(uid));
                    if (includeDept)
                        row.createCell(col++).setCellValue(depts.get(uid));
                    row.createCell(col++).setCellValue(entry.getValue()[0]);
                    row.createCell(col).setCellValue(entry.getValue()[1]);
                });

        sheet.setAutoFilter(new CellRangeAddress(0, rowRef[0] - 1, 0, headers.length - 1));
        for (int i = 0; i < headers.length; i++)
            sheet.autoSizeColumn(i);
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

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
        session.putData("manual_report_reason", text);
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

        String creatorUsername = creator != null ? creator.getUsername() : null;
        notificationService.notifyUserManualReport(targetId, typeLabel, reason, timeVal, creatorName, creatorUsername);

        StringBuilder confirmMsg = new StringBuilder("<b>✅ ОТМЕТКА СОЗДАНА</b>\n\n");
        confirmMsg.append("<b>Сотрудник:</b> ").append(targetName).append("\n");
        confirmMsg.append("<b>Тип:</b> ").append(typeLabel).append("\n");
        confirmMsg.append("<b>Причина:</b> ").append(reason);
        if (timeVal != null) {
            confirmMsg.append("\n<b>Опоздание на:</b> ").append(timeVal);
        }

        goBackFromManualReport(ctx, confirmMsg.toString());
    }

    public void showManualUsersList(CommandContext ctx, UserSession session, User caller) {
        List<User> users;
        if (caller != null && "manager".equals(caller.getRole())
                && !Boolean.TRUE.equals(caller.getIsSupervisor())
                && caller.getDepartment() != null) {
            users = userService.getEmployeesInDepartment(caller.getDepartment().getId());
        } else {
            users = userService.getAllActiveUsers();
        }

        Long callerId = ctx.getUserId();
        users.removeIf(u -> u.getUserId().equals(callerId));

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
        goBackFromManualReport(ctx, null);
    }

    private void goToAdminHome(CommandContext ctx) {
        Long uid = ctx.getUserId();
        sessionManager.clearSession(uid);
        sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
        ctx.reply("<b>⚙️ ПАНЕЛЬ АДМИНИСТРАТОРА</b>", "HTML", null, Keyboards.adminHome());
    }
}
