package com.example.lateMng.bot;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.security.LevelRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@SuppressWarnings("unused")
@Component
@BotCommand
@RequiredArgsConstructor
public class MainMenuHandlers {

    private final SessionManager sessionManager;

    @CommandHandler("Опоздаю")
    @LevelRequired(min = 2)
    public void onLate(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null) session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.REPORT_REASON);
        else sessionManager.updateState(uid, FsmStates.REPORT_REASON);
        session.putData("report_type", "late");
        ctx.reply("Выберите причину опоздания:", "HTML", null, Keyboards.reasonsLate());
    }

    @CommandHandler("Не приду")
    @LevelRequired(min = 2)
    public void onAbsence(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null) session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.REPORT_REASON);
        else sessionManager.updateState(uid, FsmStates.REPORT_REASON);
        session.putData("report_type", "absence");
        ctx.reply("Укажите причину отсутствия:", "HTML", null, Keyboards.reasonsAbsence());
    }

    @CommandHandler(value = "Админ панель", hidden = true)
    @LevelRequired(min = 3)
    public void onAdminHome(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session == null) sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
        else sessionManager.updateState(uid, FsmStates.ADMIN_HOME);
        ctx.reply("<b>⚙️ ПАНЕЛЬ АДМИНИСТРАТОРА</b>", "HTML", null, Keyboards.adminHome());
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
        if (session == null) session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.CONFIRM_VACATION);
        else sessionManager.updateState(uid, FsmStates.CONFIRM_VACATION);
        session.putData("pending_vacation_status", newStatusIsVacation);
        String text = newStatusIsVacation
                ? "Подтвердить смену статуса? Вы будете в отпуске. Да / Назад"
                : "Подтвердить смену статуса? Вы вернетесь к работе. Да / Назад";
        ctx.reply(text, "HTML", null, Keyboards.confirmYesBack());
    }
}
