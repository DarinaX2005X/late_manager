package com.example.lateMng.bot.admin;

import com.example.lateMng.bot.BotMessages;
import com.example.lateMng.bot.FsmStates;
import com.example.lateMng.bot.Keyboards;
import com.example.lateMng.entity.Department;
import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.api.methods.send.SendMessage;
import com.kaleert.nyagram.client.NyagramClient;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.fsm.annotation.StateAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminUserEditHandler {

    private final UserService userService;
    private final SessionManager sessionManager;
    private final NyagramClient client;

    @StateAction(FsmStates.ADMIN_EDITING_USER_NAME)
    public void onEditingUserName(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            session.putData("awaiting_new_name", false);
            AdminFlowService.showPendingUsers(ctx, sessionManager, userService);
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_SELECTING_USER);
            return;
        }
        Boolean awaitingNewName = session.getData("awaiting_new_name", Boolean.class);
        if (Boolean.TRUE.equals(awaitingNewName)) {
            Long targetId = session.getData("target_user_id", Long.class);
            if (targetId != null) {
                userService.updateUserName(targetId, text);
                session.putData("target_user_name", text);
            }
            session.putData("awaiting_new_name", false);
        } else if ("Оставить".equals(text)) {
            return;
        } else if ("Изменить".equals(text)) {
            ctx.reply(BotMessages.MSG_ENTER_NEW_NAME, "HTML", null, Keyboards.back());
            session.putData("awaiting_new_name", true);
            return;
        } else {
            ctx.reply("Выберите «Оставить» или «Изменить».", "HTML", null, Keyboards.back());
            return;
        }
        showDeptChoiceForApproval(ctx, session);
    }

    private void showDeptChoiceForApproval(CommandContext ctx, UserSession session) {
        List<Department> depts = userService.getDepartments();
        StringBuilder sb = new StringBuilder("<b>ВЫБОР ОТДЕЛА</b>\n\n");
        if (depts.isEmpty()) {
            sb.append("Отделов пока нет.\n\nМожно активировать без отдела.\n");
        } else {
            for (int i = 0; i < depts.size(); i++) {
                sb.append(i + 1).append(". ").append(depts.get(i).getName()).append("\n");
            }
            sb.append("\nВведите номер отдела для привязки или нажмите «Без отдела».");
        }
        ctx.reply(sb.toString(), "HTML", null, Keyboards.deptChoiceApproval());
        sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_SELECTING_USER_DEPT);
    }

    @StateAction(FsmStates.ADMIN_SELECTING_USER_DEPT)
    public void onSelectingUserDept(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            String targetName = session.getData("target_user_name", String.class);
            ctx.reply("<b>✏️ ПРОВЕРКА ИМЕНИ</b>\n\n<b>Текущее имя:</b> " + targetName + "\n\nОставить это имя или изменить?",
                    "HTML", null, Keyboards.nameCheck());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_EDITING_USER_NAME);
            return;
        }
        Integer targetDeptId = null;
        String deptLabel = "не привязан";
        if (!"Без отдела".equals(ctx.getText())) {
            try {
                int num = Integer.parseInt(ctx.getText().trim());
                List<Department> depts = userService.getDepartments();
                if (depts.isEmpty() || num < 1 || num > depts.size()) {
                    ctx.reply(BotMessages.err(BotMessages.MSG_BAD_DEPT_NUMBER), "HTML", null, Keyboards.back());
                    return;
                }
                Department dept = depts.get(num - 1);
                targetDeptId = dept.getId();
                deptLabel = dept.getName();
            } catch (NumberFormatException e) {
                ctx.reply("Введите номер отдела или «Без отдела».", "HTML", null, Keyboards.back());
                return;
            }
        }
        session.putData("target_dept_id", targetDeptId);
        ctx.reply("<b>👤 ВЫБОР РОЛИ</b>\n\n<b>Отдел:</b> " + deptLabel + "\n\nВыберите роль для сотрудника:",
                "HTML", null, Keyboards.roleChoice());
        sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_SELECTING_USER_ROLE);
    }

    @StateAction(FsmStates.ADMIN_SELECTING_USER_ROLE)
    public void onSelectingUserRole(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            showDeptChoiceForApproval(ctx, session);
            return;
        }
        String role = "Сотрудник".equals(ctx.getText()) ? "employee" : "Начальник".equals(ctx.getText()) ? "manager" : null;
        if (role == null) {
            ctx.reply(BotMessages.err("Неверная роль."), "HTML", null, Keyboards.back());
            return;
        }
        Long targetUserId = session.getData("target_user_id", Long.class);
        Integer targetDeptId = session.getData("target_dept_id", Integer.class);
        if (targetUserId != null) {
            userService.updateUserRoleDept(targetUserId, role, targetDeptId);
        }
        String deptLabel = targetDeptId != null ? userService.getDepartment(targetDeptId).map(Department::getName).orElse("не привязан") : "не привязан";
        Long uid = ctx.getUserId();
        sessionManager.clearSession(uid);
        sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
        ctx.reply("<b>✅ ПОЛЬЗОВАТЕЛЬ АКТИВИРОВАН</b>\n\n<b>Роль:</b> " + AdminFlowService.roleName(role) + "\n<b>Отдел:</b> " + deptLabel,
                "HTML", null, Keyboards.adminHome());
        if (targetUserId != null) {
            try {
                client.execute(SendMessage.builder()
                        .chatId(targetUserId.toString())
                        .text("<b>🎉 ДОСТУП ПОДТВЕРЖДЕН</b>\n\nВаша заявка одобрена!\nНажмите /start для входа в систему.")
                        .parseMode("HTML")
                        .build());
            } catch (Exception e) {
                log.debug("Failed to notify user {}: {}", targetUserId, e.getMessage());
            }
        }
    }

    @StateAction(FsmStates.ADMIN_MANAGING_USERS)
    public void onManagingUsers(CommandContext ctx, UserSession session) {
        String text = ctx.getText();
        if ("Назад".equals(text)) {
            returnToUserList(ctx, session);
            return;
        }
        Long editingUserId = session.getData("editing_user_id", Long.class);
        if (editingUserId == null) return;
        User user = userService.getUserWithDepartment(editingUserId).orElse(null);
        if (user == null) {
            returnToUserList(ctx, session);
            return;
        }
        if ("Изменить имя".equals(text)) {
            ctx.reply(BotMessages.MSG_ENTER_NEW_NAME, "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_EDITING_EXISTING_USER_NAME);
            return;
        }
        if ("Изменить роль".equals(text)) {
            ctx.reply("Выберите новую роль:", "HTML", null, Keyboards.roleChoice());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_EDITING_EXISTING_USER_ROLE);
            return;
        }
        if ("Удалить".equals(text)) {
            ctx.reply("Удалить пользователя из системы?", "HTML", null, Keyboards.confirmYesBack());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_CONFIRM_DELETE_USER);
            return;
        }
        if ("Назначить админом".equals(text) || "Убрать админа".equals(text)) {
            boolean newAdmin = !Boolean.TRUE.equals(user.getIsAdmin());
            userService.setAdmin(editingUserId, newAdmin);
            ctx.reply("<b>✅ СТАТУС ИЗМЕНЕН</b>\n\nПользователь " + (newAdmin ? "назначен админом" : "лишен прав админа") + ".", "HTML", null, null);
            returnToUserList(ctx, session);
            return;
        }
        if ("Добавить в отдел".equals(text) || "Перевести в другой отдел".equals(text)) {
            List<Department> depts = "Перевести в другой отдел".equals(text)
                    ? userService.getDepartments().stream()
                            .filter(d -> user.getDepartment() == null || !d.getId().equals(user.getDepartment().getId()))
                            .toList()
                    : userService.getDepartments();
            if (depts.isEmpty()) {
                ctx.reply(BotMessages.err("Нет отделов для выбора."), "HTML", null, Keyboards.back());
                return;
            }
            StringBuilder sb = new StringBuilder("<b>ВЫБОР ОТДЕЛА</b>\n\n");
            for (int i = 0; i < depts.size(); i++) {
                sb.append(i + 1).append(". ").append(depts.get(i).getName()).append("\n");
            }
            sb.append("\nВведите номер отдела.");
            session.putData("transfer_depts", depts.stream().map(Department::getId).toList());
            ctx.reply(sb.toString(), "HTML", null, Keyboards.back());
            sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_SELECTING_NEW_DEPT);
            return;
        }
    }

    @StateAction(FsmStates.ADMIN_EDITING_EXISTING_USER_NAME)
    public void onEditingExistingUserName(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            Long editingUserId = session.getData("editing_user_id", Long.class);
            User user = editingUserId != null ? userService.getUserWithDepartment(editingUserId).orElse(null) : null;
            if (user != null) AdminFlowService.showUserEditor(ctx, user, session, sessionManager);
            return;
        }
        Long editingUserId = session.getData("editing_user_id", Long.class);
        if (editingUserId != null) userService.updateUserName(editingUserId, ctx.getText());
        ctx.reply("<b>✅ ИМЯ ИЗМЕНЕНО</b>\n\nНовое имя: <b>" + ctx.getText() + "</b>", "HTML", null, null);
        returnToUserList(ctx, session);
    }

    @StateAction(FsmStates.ADMIN_EDITING_EXISTING_USER_ROLE)
    public void onEditingExistingUserRole(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            Long editingUserId = session.getData("editing_user_id", Long.class);
            User user = editingUserId != null ? userService.getUserWithDepartment(editingUserId).orElse(null) : null;
            if (user != null) AdminFlowService.showUserEditor(ctx, user, session, sessionManager);
            return;
        }
        String role = "Сотрудник".equals(ctx.getText()) ? "employee" : "Начальник".equals(ctx.getText()) ? "manager" : null;
        if (role == null) {
            ctx.reply(BotMessages.err("Неверная роль."), "HTML", null, Keyboards.back());
            return;
        }
        Long editingUserId = session.getData("editing_user_id", Long.class);
        if (editingUserId != null) userService.updateUserRole(editingUserId, role);
        ctx.reply("<b>✅ РОЛЬ ИЗМЕНЕНА</b>\n\nНовая роль: <b>" + AdminFlowService.roleName(role) + "</b>", "HTML", null, null);
        returnToUserList(ctx, session);
    }

    @StateAction(FsmStates.ADMIN_SELECTING_NEW_DEPT)
    public void onSelectingNewDept(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            Long editingUserId = session.getData("editing_user_id", Long.class);
            User user = editingUserId != null ? userService.getUserWithDepartment(editingUserId).orElse(null) : null;
            if (user != null) AdminFlowService.showUserEditor(ctx, user, session, sessionManager);
            return;
        }
        try {
            int num = Integer.parseInt(ctx.getText().trim());
            @SuppressWarnings("unchecked")
            List<Integer> deptIds = (List<Integer>) session.getData("transfer_depts", List.class);
            if (deptIds == null || deptIds.isEmpty() || num < 1 || num > deptIds.size()) {
                ctx.reply(BotMessages.err(BotMessages.MSG_BAD_DEPT_NUMBER), "HTML", null, Keyboards.back());
                return;
            }
            Integer deptId = deptIds.get(num - 1);
            Department dept = userService.getDepartment(deptId).orElse(null);
            Long editingUserId = session.getData("editing_user_id", Long.class);
            if (dept != null && editingUserId != null) {
                User user = userService.getUser(editingUserId).orElse(null);
                String currentRole = user != null ? user.getRole() : "employee";
                userService.updateUserRoleDept(editingUserId, currentRole, dept.getId());
            }
            String msg = session.getData("current_dept_id", Integer.class) == null
                    ? "<b>✅ ДОБАВЛЕН В ОТДЕЛ</b>\n\n<b>Отдел:</b> " + (dept != null ? dept.getName() : "")
                    : "<b>✅ ПОЛЬЗОВАТЕЛЬ ПЕРЕВЕДЕН</b>\n\n<b>Отдел:</b> " + (dept != null ? dept.getName() : "");
            ctx.reply(msg, "HTML", null, null);
            returnToUserList(ctx, session);
        } catch (NumberFormatException e) {
            ctx.reply(BotMessages.err("Введите номер отдела."), "HTML", null, Keyboards.back());
        }
    }

    @StateAction(FsmStates.ADMIN_CONFIRM_DELETE_USER)
    public void onConfirmDeleteUser(CommandContext ctx, UserSession session) {
        if ("Назад".equals(ctx.getText())) {
            Long editingUserId = session.getData("editing_user_id", Long.class);
            User user = editingUserId != null ? userService.getUserWithDepartment(editingUserId).orElse(null) : null;
            if (user != null) AdminFlowService.showUserEditor(ctx, user, session, sessionManager);
            return;
        }
        if ("Да".equals(ctx.getText())) {
            Long targetId = session.getData("editing_user_id", Long.class);
            if (targetId != null) {
                userService.deleteUser(targetId);
                try {
                    client.execute(SendMessage.builder()
                            .chatId(targetId.toString())
                            .text("<b>🚫 ДОСТУП ЗАКРЫТ</b>\n\nВаш доступ к боту отключен.")
                            .parseMode("HTML")
                            .build());
                } catch (Exception e) {
                    log.debug("Notify removed user failed: {}", e.getMessage());
                }
            }
            ctx.reply("Пользователь удален из системы.", "HTML", null, null);
            returnToUserList(ctx, session);
        } else {
            ctx.reply(BotMessages.err(BotMessages.MSG_CONFIRM_YES_BACK), "HTML", null, Keyboards.confirmYesBack());
        }
    }

    private void returnToUserList(CommandContext ctx, UserSession session) {
        Integer currentDeptId = session.getData("current_dept_id", Integer.class);
        String currentDeptName = session.getData("current_dept_name", String.class);
        if (currentDeptId != null && currentDeptName != null) {
            List<User> employees = userService.getEmployeesInDepartment(currentDeptId);
            List<Long> ids = employees.stream().map(User::getUserId).toList();
            session.putData("employees_list", ids);
            AdminFlowService.showDepartmentInfo(ctx, currentDeptId, currentDeptName, ids, sessionManager, userService);
        } else {
            AdminFlowService.showNoDeptUsersList(ctx, sessionManager, userService);
        }
    }
}
