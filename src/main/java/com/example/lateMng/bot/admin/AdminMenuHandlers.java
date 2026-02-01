package com.example.lateMng.bot.admin;

import com.example.lateMng.bot.FsmStates;
import com.example.lateMng.bot.Keyboards;
import com.example.lateMng.service.UserService;
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
public class AdminMenuHandlers {

    private final UserService userService;
    private final SessionManager sessionManager;

    @CommandHandler("Назад")
    @LevelRequired(min = 2)
    public void onBack(CommandContext ctx) {
        Long uid = ctx.getUserId();
        UserSession session = sessionManager.getSession(uid);
        if (session != null && FsmStates.ADMIN_HOME.equals(session.getState())) {
            goMainMenu(ctx);
        }
    }

    @CommandHandler("Новые заявки")
    @LevelRequired(min = 3)
    public void onNewApplications(CommandContext ctx) {
        AdminFlowService.showPendingUsers(ctx, sessionManager, userService);
    }

    @CommandHandler("Управление отделами")
    @LevelRequired(min = 3)
    public void onManageDepts(CommandContext ctx) {
        AdminFlowService.showDeptsList(ctx, sessionManager, userService);
    }

    @CommandHandler("Пользователи без отдела")
    @LevelRequired(min = 3)
    public void onNoDeptUsers(CommandContext ctx) {
        AdminFlowService.showNoDeptUsersList(ctx, sessionManager, userService);
    }

    @CommandHandler("Ответственные")
    @LevelRequired(min = 3)
    public void onSupervisors(CommandContext ctx) {
        AdminFlowService.showSupervisorsScreen(ctx, sessionManager, userService);
    }

    private void goMainMenu(CommandContext ctx) {
        sessionManager.clearSession(ctx.getUserId());
        userService.getUser(ctx.getUserId()).ifPresent(user ->
                ctx.reply("<b>🏠 ГЛАВНОЕ МЕНЮ</b>", "HTML", null,
                        Keyboards.mainMenu(user.getIsOnVacation(), Boolean.TRUE.equals(user.getIsAdmin()))));
    }
}
