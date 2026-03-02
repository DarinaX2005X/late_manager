package com.example.lateMng.bot;

import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.security.LevelRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@SuppressWarnings("unused")
@Component
@BotCommand
@RequiredArgsConstructor
public class MainMenuHandlers {

    private final SessionManager sessionManager;
    private final UserService userService;

    @CommandHandler("Опоздаю")
    @LevelRequired(min = 2)
    public void onLate(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null)
            session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.REPORT_REASON);
        else
            sessionManager.updateState(uid, FsmStates.REPORT_REASON);
        session.putData("report_type", "late");
        ctx.reply("Выберите причину опоздания:", "HTML", null, Keyboards.reasonsLate());
    }

    @CommandHandler("Не приду")
    @LevelRequired(min = 2)
    public void onAbsence(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null)
            session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.REPORT_REASON);
        else
            sessionManager.updateState(uid, FsmStates.REPORT_REASON);
        session.putData("report_type", "absence");
        ctx.reply("Укажите причину отсутствия:", "HTML", null, Keyboards.reasonsAbsence());
    }

    @CommandHandler(value = "Админ панель", hidden = true)
    @LevelRequired(min = 4)
    public void onAdminHome(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null)
            sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
        else
            sessionManager.updateState(uid, FsmStates.ADMIN_HOME);
        ctx.reply("<b>⚙️ ПАНЕЛЬ АДМИНИСТРАТОРА</b>", "HTML", null, Keyboards.adminHome());
    }

    @CommandHandler(value = "Информация", hidden = true)
    @LevelRequired(min = 2)
    public void onInfo(CommandContext ctx) {
        Long uid = ctx.getUserId();
        User user = userService.getUserWithDepartment(uid).orElse(null);
        if (user == null) {
            ctx.reply("Пользователь не найден.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<b>ℹ️ ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЕ</b>\n\n");
        sb.append("👤 <b>ФИО:</b> ").append(user.getFullName()).append("\n");
        sb.append("🏢 <b>Отдел:</b> ")
                .append(user.getDepartment() != null ? user.getDepartment().getName() : "Без отдела").append("\n");

        sb.append("\n<b>🔔 Кто получает уведомления о ваших отчетах:</b>\n");

        boolean hasRecipients = false;

        if (user.getDepartment() != null) {
            List<User> managers = userService.getActiveManagersForDepartment(user.getDepartment().getId());
            boolean hasManagers = false;
            for (User m : managers) {
                if (!m.getUserId().equals(uid)) {
                    if (!hasManagers) {
                        sb.append("\n<i>Начальники отдела:</i>\n");
                        hasManagers = true;
                    }
                    sb.append("- ").append(m.getFullName()).append("\n");
                    hasRecipients = true;
                }
            }
        }

        List<User> supervisors = userService.getSupervisors(true);
        boolean hasSupervisors = false;
        for (User s : supervisors) {
            if (!s.getUserId().equals(uid)) {
                if (!hasSupervisors) {
                    sb.append("\n<i>Ответственные:</i>\n");
                    hasSupervisors = true;
                }
                sb.append("- ").append(s.getFullName()).append("\n");
                hasRecipients = true;
            }
        }

        if (!hasRecipients) {
            sb.append("\n<i>Никто не получает ваши уведомления.</i>\n");
        }

        ctx.reply(sb.toString(), "HTML", null, Keyboards.mainMenu(user.getIsOnVacation(), user.getIsAdmin()));
    }

    @CommandHandler("Статус: Работаю")
    @LevelRequired(min = 2)
    public void onVacationToggleFromWork(CommandContext ctx) {
        goConfirmVacation(ctx, true);
    }

    @CommandHandler("Статус: В отпуске")
    @LevelRequired(min = 2)
    public void onVacationToggleFromVacation(CommandContext ctx) {
        goConfirmVacation(ctx, false);
    }

    private void goConfirmVacation(CommandContext ctx, boolean newStatusIsVacation) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null)
            session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.CONFIRM_VACATION);
        else
            sessionManager.updateState(uid, FsmStates.CONFIRM_VACATION);
        session.putData("pending_vacation_status", newStatusIsVacation);
        String text = newStatusIsVacation
                ? "Подтвердить смену статуса? Вы будете в отпуске. Да / Назад"
                : "Подтвердить смену статуса? Вы вернетесь к работе. Да / Назад";
        ctx.reply(text, "HTML", null, Keyboards.confirmYesBack());
    }
}
