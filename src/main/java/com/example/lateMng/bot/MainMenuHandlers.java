package com.example.lateMng.bot;

import com.example.lateMng.entity.Report;
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
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component
@BotCommand
@RequiredArgsConstructor
public class MainMenuHandlers {

    private final SessionManager sessionManager;
    private final UserService userService;
    private final com.example.lateMng.bot.admin.StatsAndManualReportHandler statsAndManualReportHandler;

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

    @CommandHandler(value = "Статистика", hidden = true)
    @LevelRequired(min = 3)
    public void onStats(CommandContext ctx) {
        Long uid = ctx.getUserId();
        User user = userService.getUserWithDepartment(uid).orElse(null);
        if (user == null)
            return;
        boolean isManager = "manager".equals(user.getRole());
        boolean isSupervisor = Boolean.TRUE.equals(user.getIsSupervisor());
        if (!isManager && !isSupervisor) {
            ctx.reply(BotMessages.err("Эта функция доступна только начальникам и ответственным."),
                    "HTML", null, Keyboards.mainMenuFor(user));
            return;
        }
        statsAndManualReportHandler.enterStatsFromMainMenu(ctx, user);
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

        boolean isManager = "manager".equals(user.getRole());
        boolean isSupervisor = Boolean.TRUE.equals(user.getIsSupervisor());
        boolean canMark = isManager || isSupervisor;

        StringBuilder sb = new StringBuilder();
        sb.append("<b>ℹ️ ИНФОРМАЦИЯ</b>\n\n");
        sb.append("👤 <b>ФИО:</b> ").append(user.getFullName())
                .append(BotMessages.usernameTag(user.getUsername())).append("\n");
        sb.append("🏢 <b>Отдел:</b> ")
                .append(user.getDepartment() != null ? user.getDepartment().getName() : "Без отдела").append("\n");

        sb.append("\n<b>🔔 Кто получает уведомления о ваших отчетах:</b>\n");

        boolean hasRecipients = false;
        if (user.getDepartment() != null) {
            List<User> managers = userService.getActiveManagersForDepartment(user.getDepartment().getId());
            for (User m : managers) {
                if (!m.getUserId().equals(uid)) {
                    sb.append("- ").append(m.getFullName())
                            .append(BotMessages.usernameTag(m.getUsername())).append("\n");
                    hasRecipients = true;
                }
            }
        }

        List<User> supervisors = userService.getSupervisors(true);
        for (User s : supervisors) {
            if (!s.getUserId().equals(uid)) {
                sb.append("- ").append(s.getFullName())
                        .append(BotMessages.usernameTag(s.getUsername())).append("\n");
                hasRecipients = true;
            }
        }

        if (!hasRecipients) {
            sb.append("<i>Никто не получает ваши уведомления.</i>\n");
        }

        if (canMark && user.getDepartment() != null) {
            List<User> members = userService.getEmployeesInDepartment(user.getDepartment().getId());
            List<Report> todayReports = userService.getTodayReportsByDepartment(user.getDepartment().getId());
            Map<Long, Report> reportByUser = todayReports.stream()
                    .collect(Collectors.toMap(Report::getUserId, r -> r, (a, b) -> a));

            sb.append("\n<b>📋 Сотрудники отдела сегодня:</b>\n");
            for (User m : members) {
                if (m.getUserId().equals(uid))
                    continue;
                String name = m.getFullName() + BotMessages.usernameTag(m.getUsername());
                String status;
                if (Boolean.TRUE.equals(m.getIsOnVacation())) {
                    status = "в отпуске";
                } else {
                    Report r = reportByUser.get(m.getUserId());
                    if (r == null) {
                        status = "";
                    } else if ("late".equals(r.getReportType())) {
                        status = "опоздал";
                    } else {
                        status = "не пришёл";
                    }
                }
                sb.append(name);
                if (!status.isEmpty())
                    sb.append(" - ").append(status);
                sb.append("\n");
            }
        }

        com.kaleert.nyagram.api.objects.replykeyboard.ReplyKeyboardMarkup kb;
        if (canMark) {
            kb = com.kaleert.nyagram.api.objects.replykeyboard.ReplyKeyboardMarkup.vertical(true,
                    "Отметить сотрудника", "Назад");
        } else {
            kb = Keyboards.back();
        }

        ctx.reply(sb.toString(), "HTML", null, kb);
    }

    @CommandHandler(value = "Отметить сотрудника", hidden = true)
    @LevelRequired(min = 3)
    public void onMarkEmployee(CommandContext ctx) {
        Long uid = ctx.getUserId();
        User user = userService.getUserWithDepartment(uid).orElse(null);
        if (user == null)
            return;
        boolean isManager = "manager".equals(user.getRole());
        boolean isSupervisor = Boolean.TRUE.equals(user.getIsSupervisor());
        if (!isManager && !isSupervisor) {
            ctx.reply(BotMessages.err("Эта функция доступна только начальникам и ответственным."),
                    "HTML", null, Keyboards.mainMenuFor(user));
            return;
        }
        UserSession session = sessionManager.getSession(uid);
        if (session == null)
            session = sessionManager.startSession(uid, ctx.getChatId(), FsmStates.MANUAL_REPORT_SELECT_USER);
        else
            sessionManager.updateState(uid, FsmStates.MANUAL_REPORT_SELECT_USER);
        statsAndManualReportHandler.showManualUsersList(ctx, session, user);
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
