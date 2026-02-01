package com.example.lateMng.bot.admin;

import com.example.lateMng.bot.BotMessages;
import com.example.lateMng.bot.FsmStates;
import com.example.lateMng.bot.Keyboards;
import com.example.lateMng.entity.Department;
import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.fsm.annotation.StateAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFlowHandler {

    private final UserService userService;
    private final SessionManager sessionManager;

    private void goToAdminHome(CommandContext ctx) {
        Long uid = ctx.getUserId();
        sessionManager.clearSession(uid);
        sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
        ctx.reply("<b>⚙️ ПАНЕЛЬ АДМИНИСТРАТОРА</b>", "HTML", null, Keyboards.adminHome());
    }

    private void backToDepartmentInfo(CommandContext ctx, UserSession session) {
        Integer deptId = session.getData("current_dept_id", Integer.class);
        String deptName = session.getData("current_dept_name", String.class);
        if (deptId != null && deptName != null) {
            List<User> employees = userService.getEmployeesInDepartment(deptId);
            List<Long> ids = employees.stream().map(User::getUserId).toList();
            AdminFlowService.showDepartmentInfo(ctx, deptId, deptName, ids, sessionManager, userService);
        }
    }

    @StateAction(FsmStates.ADMIN_HOME)
    public void onAdminHome(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            sessionManager.clearSession(ctx.getUserId());
            userService.getUser(ctx.getUserId()).ifPresent(user ->
                    ctx.reply("<b>🏠 ГЛАВНОЕ МЕНЮ</b>", "HTML", null,
                            Keyboards.mainMenu(user.getIsOnVacation(), Boolean.TRUE.equals(user.getIsAdmin()))));
            return;
        }
        if ("Новые заявки".equals(text)) {
            AdminFlowService.showPendingUsers(ctx, sessionManager, userService);
            return;
        }
        if ("Управление отделами".equals(text)) {
            AdminFlowService.showDeptsList(ctx, sessionManager, userService);
            return;
        }
        if ("Пользователи без отдела".equals(text)) {
            AdminFlowService.showNoDeptUsersList(ctx, sessionManager, userService);
            return;
        }
        if ("Ответственные".equals(text)) {
            AdminFlowService.showSupervisorsScreen(ctx, sessionManager, userService);
            return;
        }
        ctx.reply("<b>❌ ОШИБКА</b>\n\nНеизвестная команда. Выберите пункт меню.", "HTML", null, Keyboards.adminHome());
    }

    @StateAction(FsmStates.ADMIN_SELECTING_DEPT)
    public void onSelectingDept(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Создать отдел".equals(text)) {
            ctx.reply("<b>➕ СОЗДАНИЕ ОТДЕЛА</b>\n\nВведите название нового отдела:", "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_WAITING_DEPT_NAME);
            return;
        }
        if ("Назад".equals(text)) {
            goToAdminHome(ctx);
            return;
        }
        try {
            int num = Integer.parseInt(text.trim());
            List<Department> depts = userService.getDepartments();
            if (depts.isEmpty() || num < 1 || num > depts.size()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_BAD_DEPT_NUMBER, "HTML", null, Keyboards.deptsList());
                return;
            }
            Department dept = depts.get(num - 1);
            session.putData("current_dept_id", dept.getId());
            session.putData("current_dept_name", dept.getName());
            List<User> employees = userService.getEmployeesInDepartment(dept.getId());
            List<Long> ids = employees.stream().map(User::getUserId).toList();
            session.putData("employees_list", ids);
            AdminFlowService.showDepartmentInfo(ctx, dept.getId(), dept.getName(), ids, sessionManager, userService);
        } catch (NumberFormatException e) {
            ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_ENTER_DEPT_NUMBER, "HTML", null, Keyboards.deptsList());
        }
    }

    @StateAction(FsmStates.ADMIN_WAITING_DEPT_NAME)
    public void onWaitingDeptName(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            AdminFlowService.showDeptsList(ctx, sessionManager, userService);
            return;
        }
        String deptName = ctx.getText();
        if (userService.createDepartment(deptName)) {
            goToAdminHome(ctx);
            ctx.reply("<b>✅ ОТДЕЛ СОЗДАН</b>\n\n<b>Название:</b> " + deptName, "HTML", null, Keyboards.adminHome());
            return;
        }
        ctx.reply("<b>❌ ОШИБКА</b>\n\nОтдел с таким названием уже существует.", "HTML", null, Keyboards.back());
    }

    @StateAction(FsmStates.ADMIN_SELECTING_USER)
    public void onSelectingUser(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            goToAdminHome(ctx);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            List<User> users = userService.getPendingUsers();
            if (users.isEmpty() || num < 1 || num > users.size()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_BAD_NUMBER, "HTML", null, Keyboards.back());
                return;
            }
            User user = users.get(num - 1);
            session.putData("target_user_id", user.getUserId());
            session.putData("target_user_name", user.getFullName());
            session.putData("awaiting_new_name", false);
            ctx.reply("<b>✏️ ПРОВЕРКА ИМЕНИ</b>\n\n<b>Текущее имя:</b> " + user.getFullName() + "\n\nОставить это имя или изменить?",
                    "HTML", null, Keyboards.nameCheck());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_EDITING_USER_NAME);
        } catch (NumberFormatException e) {
            ctx.reply("<b>❌ ОШИБКА</b>\n\nВведите номер заявки.", "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.ADMIN_SELECTING_NO_DEPT_USER)
    public void onSelectingNoDeptUser(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            goToAdminHome(ctx);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) session.getData("employees_list", List.class);
            if (ids == null || ids.isEmpty() || num < 1 || num > ids.size()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_BAD_NUMBER, "HTML", null, Keyboards.back());
                return;
            }
            Long userId = ids.get(num - 1);
            User user = userService.getUser(userId).orElse(null);
            if (user == null) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\nПользователь не найден.", "HTML", null, Keyboards.back());
                return;
            }
            session.putData("editing_user_id", userId);
            session.putData("current_dept_id", null);
            session.putData("current_dept_name", "Без отдела");
            AdminFlowService.showUserEditor(ctx, user, session, sessionManager);
        } catch (NumberFormatException e) {
            ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_ENTER_USER_NUMBER, "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.ADMIN_MANAGING_SUPERVISORS)
    public void onManagingSupervisors(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            goToAdminHome(ctx);
            return;
        }
        if ("Добавить ответственного".equals(ctx.getText())) {
            List<User> allUsers = userService.getAllActiveUsers();
            List<User> supervisors = userService.getSupervisors(false);
            var supervisorIds = supervisors.stream().map(User::getUserId).toList();
            var candidates = allUsers.stream().filter(u -> !supervisorIds.contains(u.getUserId())).toList();
            if (candidates.isEmpty()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\nНет пользователей для добавления.", "HTML", null, Keyboards.back());
                return;
            }
            StringBuilder sb = new StringBuilder("<b>👥 ДОБАВИТЬ ОТВЕТСТВЕННОГО</b>\n\n");
            for (int i = 0; i < candidates.size(); i++) {
                sb.append(i + 1).append(". ").append(candidates.get(i).getFullName()).append("\n");
            }
            sb.append("\nВведите номер для назначения ответственным.");
            session.putData("candidate_ids", candidates.stream().map(User::getUserId).toList());
            ctx.reply(sb.toString(), "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_ADDING_SUPERVISOR);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) session.getData("supervisor_ids", List.class);
            if (ids == null || ids.isEmpty() || num < 1 || num > ids.size()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_BAD_NUMBER, "HTML", null, Keyboards.back());
                return;
            }
            Long uid = ids.get(num - 1);
            userService.setSupervisor(uid, false);
            ctx.reply("Снят с роли ответственного.", "HTML", null, null);
            AdminFlowService.showSupervisorsScreen(ctx, sessionManager, userService);
        } catch (NumberFormatException e) {
            ctx.reply("<b>❌ ОШИБКА</b>\n\nВведите номер для снятия или нажмите кнопку.", "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.ADMIN_ADDING_SUPERVISOR)
    public void onAddingSupervisor(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            AdminFlowService.showSupervisorsScreen(ctx, sessionManager, userService);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) session.getData("candidate_ids", List.class);
            if (ids == null || ids.isEmpty() || num < 1 || num > ids.size()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_BAD_NUMBER, "HTML", null, Keyboards.back());
                return;
            }
            Long uid = ids.get(num - 1);
            userService.setSupervisor(uid, true);
            ctx.reply("Назначен ответственным.", "HTML", null, null);
            AdminFlowService.showSupervisorsScreen(ctx, sessionManager, userService);
        } catch (NumberFormatException e) {
            ctx.reply("<b>❌ ОШИБКА</b>\n\nВведите номер для назначения ответственным.", "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.ADMIN_DEPT_MENU)
    public void onDeptMenu(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            AdminFlowService.showDeptsList(ctx, sessionManager, userService);
            return;
        }
        Integer deptId = session.getData("current_dept_id", Integer.class);
        String deptName = session.getData("current_dept_name", String.class);
        if (deptId == null || deptName == null) {
            goToAdminHome(ctx);
            return;
        }
        if ("Управление пользователями".equals(text)) {
            AdminFlowService.showEmployeeListForDept(ctx, deptId, session, sessionManager, userService);
            return;
        }
        if ("Переименовать отдел".equals(text)) {
            ctx.reply("Введите новое название отдела:", "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_WAITING_RENAME_DEPT_NAME);
            return;
        }
        if ("Удалить отдел".equals(text)) {
            ctx.reply("Удалить отдел? Все сотрудники будут отвязаны от отдела.", "HTML", null, Keyboards.confirmYesBack());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_CONFIRM_DELETE_DEPT);
        }
    }

    @StateAction(FsmStates.ADMIN_WAITING_USER_ID)
    public void onWaitingUserId(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            backToDepartmentInfo(ctx, session);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            @SuppressWarnings("unchecked")
            List<Long> ids = (List<Long>) session.getData("employees_list", List.class);
            if (ids == null || ids.isEmpty() || num < 1 || num > ids.size()) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_BAD_NUMBER, "HTML", null, Keyboards.back());
                return;
            }
            Long userId = ids.get(num - 1);
            User user = userService.getUserWithDepartment(userId).orElse(null);
            if (user == null) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\nПользователь не найден.", "HTML", null, Keyboards.back());
                return;
            }
            Integer currentDeptId = session.getData("current_dept_id", Integer.class);
            if (user.getDepartment() == null || !user.getDepartment().getId().equals(currentDeptId)) {
                ctx.reply("<b>❌ ОШИБКА</b>\n\nПользователь не принадлежит этому отделу.", "HTML", null, Keyboards.back());
                return;
            }
            AdminFlowService.showUserEditor(ctx, user, session, sessionManager);
        } catch (NumberFormatException e) {
            ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_ENTER_USER_NUMBER, "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.ADMIN_CONFIRM_DELETE_DEPT)
    public void onConfirmDeleteDept(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            backToDepartmentInfo(ctx, session);
            return;
        }
        if ("Да".equals(ctx.getText())) {
            Integer deptId = session.getData("current_dept_id", Integer.class);
            if (deptId != null) userService.deleteDepartment(deptId);
            goToAdminHome(ctx);
            ctx.reply("Отдел удален.", "HTML", null, Keyboards.adminHome());
        } else {
            ctx.reply("<b>❌ ОШИБКА</b>\n\n" + BotMessages.MSG_CONFIRM_YES_BACK, "HTML", null, Keyboards.confirmYesBack());
        }
    }

    @StateAction(FsmStates.ADMIN_WAITING_RENAME_DEPT_NAME)
    public void onWaitingRenameDeptName(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            backToDepartmentInfo(ctx, session);
            return;
        }
        Integer deptId = session.getData("current_dept_id", Integer.class);
        if (deptId == null) return;
        String newName = ctx.getText().trim();
        if (userService.renameDepartment(deptId, newName)) {
            session.putData("current_dept_name", newName);
            List<User> employees = userService.getEmployeesInDepartment(deptId);
            List<Long> ids = employees.stream().map(User::getUserId).toList();
            AdminFlowService.showDepartmentInfo(ctx, deptId, newName, ids, sessionManager, userService);
        } else {
            ctx.reply("<b>❌ ОШИБКА</b>\n\nОтдел с таким названием уже существует.", "HTML", null, Keyboards.back());
        }
    }
}
