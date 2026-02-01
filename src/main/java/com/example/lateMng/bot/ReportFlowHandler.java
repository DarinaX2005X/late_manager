package com.example.lateMng.bot;

import com.example.lateMng.entity.User;
import com.example.lateMng.bot.BotMessages;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.fsm.annotation.StateAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportFlowHandler {

    private static final String REPORT_TYPE_LATE = "late";
    private static final ZoneId REPORT_TIMEZONE = ZoneId.of("GMT+5");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserService userService;
    private final SessionManager sessionManager;
    private final BotNotificationService botNotificationService;

    private static boolean isLateReport(String reportType) {
        return REPORT_TYPE_LATE.equals(reportType);
    }

    @StateAction(FsmStates.REPORT_REASON)
    public void onReason(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            goMainMenu(ctx);
            return;
        }
        if ("Другое".equals(text)) {
            ctx.reply("Напишите причину текстом:", "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.REPORT_CUSTOM_REASON);
            return;
        }
        session.putData("reason", text);
        String reportType = session.getData("report_type", String.class);
        if (isLateReport(reportType)) {
            ctx.reply("Через сколько придете?", "HTML", null, Keyboards.time());
            sessionManager.updateState(ctx.getUserId(), FsmStates.REPORT_TIME);
        } else {
            finalizeReport(ctx, session);
        }
    }

    @StateAction(FsmStates.REPORT_CUSTOM_REASON)
    public void onCustomReason(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            showReasonStep(ctx, session);
            return;
        }
        session.putData("reason", ctx.getText());
        String reportType = session.getData("report_type", String.class);
        if (isLateReport(reportType)) {
            ctx.reply("Через сколько придете?", "HTML", null, Keyboards.time());
            sessionManager.updateState(ctx.getUserId(), FsmStates.REPORT_TIME);
        } else {
            finalizeReport(ctx, session);
        }
    }

    @StateAction(FsmStates.REPORT_TIME)
    public void onTime(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            showReasonStep(ctx, session);
            return;
        }
        if ("Другое".equals(ctx.getText())) {
            ctx.reply("Напишите через сколько придете (например: 2 часа 30 минут):", "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.REPORT_CUSTOM_TIME);
            return;
        }
        session.putData("time_val", ctx.getText());
        finalizeReport(ctx, session);
    }

    @StateAction(FsmStates.REPORT_CUSTOM_TIME)
    public void onCustomTime(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            ctx.reply("Через сколько придете?", "HTML", null, Keyboards.time());
            sessionManager.updateState(ctx.getUserId(), FsmStates.REPORT_TIME);
            return;
        }
        session.putData("time_val", ctx.getText());
        finalizeReport(ctx, session);
    }

    private void showReasonStep(CommandContext ctx, UserSession session) {
        String reportType = session.getData("report_type", String.class);
        if (isLateReport(reportType)) {
            ctx.reply("Выберите причину опоздания:", "HTML", null, Keyboards.reasonsLate());
        } else {
            ctx.reply("Укажите причину отсутствия:", "HTML", null, Keyboards.reasonsAbsence());
        }
        sessionManager.updateState(ctx.getUserId(), FsmStates.REPORT_REASON);
    }

    private void goMainMenu(CommandContext ctx) {
        sessionManager.clearSession(ctx.getUserId());
        User user = userService.getUser(ctx.getUserId()).orElse(null);
        if (user == null) {
            ctx.reply("<b>🏠 ГЛАВНОЕ МЕНЮ</b>", "HTML", null, null);
            return;
        }
        ctx.reply("<b>🏠 ГЛАВНОЕ МЕНЮ</b>", "HTML", null,
                Keyboards.mainMenu(user.getIsOnVacation(), Boolean.TRUE.equals(user.getIsAdmin())));
    }

    private void finalizeReport(CommandContext ctx, UserSession session) {
        Long userId = ctx.getUserId();
        User dbUser = userService.getUserWithDepartment(userId).orElse(null);
        if (dbUser == null || dbUser.getDepartment() == null) {
            sessionManager.clearSession(userId);
            ctx.reply(BotMessages.err("Вы не привязаны к отделу.\nОбратитесь к администратору."),
                    "HTML", null, Keyboards.mainMenu(false, false));
            return;
        }
        String reportType = session.getData("report_type", String.class);
        String reason = session.getData("reason", String.class);
        String timeVal = session.getData("time_val", String.class);
        String reportText = buildReportText(dbUser, reportType, reason, timeVal);
        int sentCount = botNotificationService.sendToReportRecipients(userId, dbUser.getDepartment().getId(), reportText);

        String confirmText = sentCount > 0
                ? "<b>✅ ОТЧЕТ ОТПРАВЛЕН</b>\n\nНачальство уведомлено\nПолучателей: <b>" + sentCount + "</b>"
                : BotMessages.err("Нет получателей, никому не отправлено.");
        ctx.reply(confirmText, "HTML", null,
                Keyboards.mainMenu(dbUser.getIsOnVacation(), Boolean.TRUE.equals(dbUser.getIsAdmin())));
        sessionManager.clearSession(userId);
    }

    private String buildReportText(User user, String reportType, String reason, String timeVal) {
        String title = isLateReport(reportType) ? "ОПОЗДАНИЕ" : "ОТСУТСТВИЕ";
        String deptName = user.getDepartment().getName();
        String sendTime = ZonedDateTime.now(REPORT_TIMEZONE).format(TIME_FORMAT);
        String timeLine = isLateReport(reportType) ? "\n⏱ <b>Придет через:</b> " + timeVal + "\n" : "\n";
        String usernameTag = formatUsernameTag(user.getUsername());
        return "<b>" + title + "</b>\n\n"
                + "👤 <b>Сотрудник:</b> " + user.getFullName() + usernameTag + "\n"
                + "🏢 <b>Отдел:</b> " + deptName + "\n"
                + "📝 <b>Причина:</b> " + reason + timeLine
                + "🕐 <b>Время отправки:</b> " + sendTime;
    }

    private static String formatUsernameTag(String username) {
        return username != null && !username.isBlank() ? " (@" + username + ")" : "";
    }
}
