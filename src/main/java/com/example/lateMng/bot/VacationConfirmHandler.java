package com.example.lateMng.bot;

import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.fsm.annotation.StateAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class VacationConfirmHandler {

    private final UserService userService;
    private final SessionManager sessionManager;
    private final BotNotificationService botNotificationService;

    @StateAction(FsmStates.CONFIRM_VACATION)
    public void onConfirm(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            sessionManager.clearSession(ctx.getUserId());
            userService.getUserWithDepartment(ctx.getUserId()).ifPresent(user -> ctx.reply("Отменено.", "HTML", null,
                    Keyboards.mainMenuFor(user)));
            return;
        }
        if (!"Да".equals(text)) {
            ctx.reply(BotMessages.err(BotMessages.MSG_CONFIRM_YES_BACK), "HTML", null, Keyboards.confirmYesBack());
            return;
        }
        Boolean newStatus = session.getData("pending_vacation_status", Boolean.class);
        if (newStatus == null)
            newStatus = false;
        Long userId = ctx.getUserId();
        userService.toggleVacation(userId, newStatus);
        User user = userService.getUserWithDepartment(userId).orElse(null);
        if (user == null)
            return;
        String usernameTag = BotMessages.usernameTag(user.getUsername());
        String deptName = user.getDepartment() != null ? user.getDepartment().getName() : "не указан";
        String statusAction = newStatus ? "Ушел в отпуск." : "Вернулся к работе.";
        String msgText = newStatus
                ? "<b>🌴 ОТПУСК</b>\n\nВы ушли в отпуск.\nУведомления отключены."
                : "<b>💼 ВОЗВРАЩЕНИЕ</b>\n\nВы вернулись к работе.\nУведомления включены.";
        String notifyText = "<b>" + (newStatus ? "🌴 СМЕНА СТАТУСА" : "💼 СМЕНА СТАТУСА") + "</b>\n\n"
                + "👤 <b>Сотрудник:</b> " + user.getFullName() + " " + usernameTag + "\n"
                + "🏢 <b>Отдел:</b> " + deptName + "\n\n" + statusAction;
        ctx.reply(msgText, "HTML", null, Keyboards.mainMenuFor(user));

        Integer deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        botNotificationService.sendToReportRecipients(userId, deptId, notifyText);
        sessionManager.clearSession(userId);
    }
}
