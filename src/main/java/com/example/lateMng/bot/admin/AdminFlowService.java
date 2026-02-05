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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminFlowService {

    private static final String ROLE_EMPLOYEE = "Сотрудник";
    private static final String ROLE_MANAGER = "Начальник";

    private AdminFlowService() {}

    private static void ensureSession(SessionManager sm, Long uid, Long chatId, String state) {
        if (sm.getSession(uid) == null) sm.startSession(uid, chatId, state);
        else sm.updateState(uid, state);
    }

    public static void showPendingUsers(CommandContext ctx, SessionManager sessionManager, UserService userService) {
        List<User> users = userService.getPendingUsers();
        if (users.isEmpty()) {
            Long uid = ctx.getUserId();
            sessionManager.clearSession(uid);
            sessionManager.startSession(uid, ctx.getChatId(), FsmStates.ADMIN_HOME);
            ctx.reply("<b>📋 НОВЫЕ ЗАЯВКИ</b>\n\nНет новых заявок.", "HTML", null, Keyboards.adminHome());
            return;
        }
        StringBuilder sb = new StringBuilder("<b>📋 НОВЫЕ ЗАЯВКИ</b>\n\nВсего заявок: <b>").append(users.size()).append("</b>\n\n");
        for (int i = 0; i < users.size(); i++) {
            sb.append(i + 1).append(". ").append(formatUserLine(users.get(i))).append("\n");
        }
        sb.append("\nВведите номер заявки.");
        ctx.reply(sb.toString(), "HTML", null, Keyboards.back());
        ensureSession(sessionManager, ctx.getUserId(), ctx.getChatId(), FsmStates.ADMIN_SELECTING_USER);
    }

    public static void showDeptsList(CommandContext ctx, SessionManager sessionManager, UserService userService) {
        List<Department> depts = userService.getDepartments();
        StringBuilder sb = new StringBuilder("<b>🏢 СПИСОК ОТДЕЛОВ</b>\n\n");
        if (depts.isEmpty()) {
            sb.append("Отделов пока нет.\n");
        } else {
            for (int i = 0; i < depts.size(); i++) {
                sb.append(i + 1).append(". ").append(depts.get(i).getName()).append("\n");
            }
            sb.append("\n").append(BotMessages.MSG_ENTER_DEPT_NUMBER);
        }
        ctx.reply(sb.toString(), "HTML", null, Keyboards.deptsList());
        ensureSession(sessionManager, ctx.getUserId(), ctx.getChatId(), FsmStates.ADMIN_SELECTING_DEPT);
    }

    public static void showNoDeptUsersList(CommandContext ctx, SessionManager sessionManager, UserService userService) {
        List<User> users = userService.getUsersWithoutDepartment();
        StringBuilder sb = new StringBuilder("<b>👥 ПОЛЬЗОВАТЕЛИ БЕЗ ОТДЕЛА</b>\n\nВведите номер пользователя для редактирования:\n\n");
        List<Long> ids = new ArrayList<>();
        if (!users.isEmpty()) {
            appendManagersAndRegular(sb, users, ids);
        } else {
            sb.append("   Нет пользователей без отдела.\n");
        }
        Long uid = ctx.getUserId();
        ensureSession(sessionManager, uid, ctx.getChatId(), FsmStates.ADMIN_SELECTING_NO_DEPT_USER);
        UserSession session = sessionManager.getSession(uid);
        session.putData("current_dept_id", null);
        session.putData("current_dept_name", "Без отдела");
        session.putData("employees_list", ids);
        ctx.reply(sb.toString(), "HTML", null, Keyboards.back());
    }

    private static void appendManagersAndRegular(StringBuilder sb, List<User> employees, List<Long> orderedIds) {
        List<User> managers = new ArrayList<>();
        List<User> regular = new ArrayList<>();
        for (User u : employees) {
            if ("manager".equals(u.getRole())) managers.add(u);
            else regular.add(u);
        }
        int c = 1;
        for (User u : managers) {
            sb.append(c++).append(". 👔 ").append(formatUserLine(u)).append("\n");
            orderedIds.add(u.getUserId());
        }
        for (User u : regular) {
            sb.append(c++).append(". 👤 ").append(formatUserLine(u)).append("\n");
            orderedIds.add(u.getUserId());
        }
    }

    public static void showSupervisorsScreen(CommandContext ctx, SessionManager sessionManager, UserService userService) {
        List<User> supervisors = userService.getSupervisors(false);
        List<Long> ids = supervisors.stream().map(User::getUserId).toList();
        StringBuilder sb = new StringBuilder("<b>👥 ОТВЕТСТВЕННЫЕ</b>\n\n");
        if (ids.isEmpty()) {
            sb.append("Пока никого нет.\n");
        } else {
            for (int i = 0; i < supervisors.size(); i++) {
                sb.append(i + 1).append(". ").append(formatUserLine(supervisors.get(i))).append("\n");
            }
            sb.append("\nВведите номер чтобы убрать из ответственных.");
        }
        ensureSession(sessionManager, ctx.getUserId(), ctx.getChatId(), FsmStates.ADMIN_MANAGING_SUPERVISORS);
        sessionManager.getSession(ctx.getUserId()).putData("supervisor_ids", ids);
        ctx.reply(sb.toString(), "HTML", null, Keyboards.supervisors());
    }

    static String formatUserLine(User u) {
        String un = u.getUsername() != null && !u.getUsername().isBlank() ? " (@" + u.getUsername() + ")" : "";
        String vac = Boolean.TRUE.equals(u.getIsOnVacation()) ? " [В отпуске]" : "";
        return u.getFullName() + un + vac;
    }

    static String roleName(String role) {
        return "manager".equals(role) ? ROLE_MANAGER : ROLE_EMPLOYEE;
    }

    static String formatUserDeptName(User user) {
        if (user.getDepartment() == null) return "Не привязан";
        return user.getDepartment().getName();
    }

    static void showUserEditor(CommandContext ctx, User user, UserSession session, SessionManager sessionManager) {
        session.putData("editing_user_id", user.getUserId());
        session.putData("editing_user_name", user.getFullName());
        String deptName = formatUserDeptName(user);
        String role = roleName(user.getRole());
        String adminStatus = Boolean.TRUE.equals(user.getIsAdmin()) ? "Да" : "Нет";
        boolean hasDept = user.getDepartment() != null;
        ctx.reply("<b>✏️ РЕДАКТИРОВАНИЕ ПОЛЬЗОВАТЕЛЯ</b>\n\n"
                        + "<b>ID:</b> " + user.getUserId() + "\n"
                        + "<b>Имя:</b> " + formatUserLine(user) + "\n"
                        + "<b>Роль:</b> " + role + "\n"
                        + "<b>Админ:</b> " + adminStatus + "\n"
                        + "<b>Отдел:</b> " + deptName + "\n\n"
                        + "Что изменить?",
                "HTML", null, Keyboards.userEditKeyboard(Boolean.TRUE.equals(user.getIsAdmin()), hasDept));
        sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_MANAGING_USERS);
    }

    static void showDepartmentInfo(CommandContext ctx, int deptId, String deptName, List<Long> employeeIds, SessionManager sessionManager, UserService userService) {
        StringBuilder sb = new StringBuilder("<b>🏢 ОТДЕЛ: ").append(deptName.toUpperCase()).append("</b>\n\n<b>👥 Сотрудники:</b>\n");
        List<Long> orderedIds = new ArrayList<>();
        if (!employeeIds.isEmpty()) {
            List<User> employees = userService.getEmployeesInDepartment(deptId);
            appendManagersAndRegular(sb, employees, orderedIds);
        }
        ensureSession(sessionManager, ctx.getUserId(), ctx.getChatId(), FsmStates.ADMIN_DEPT_MENU);
        UserSession session = sessionManager.getSession(ctx.getUserId());
        session.putData("current_dept_id", deptId);
        session.putData("current_dept_name", deptName);
        session.putData("employees_list", orderedIds.isEmpty() ? employeeIds : orderedIds);
        ctx.reply(sb.toString(), "HTML", null, Keyboards.departmentMenu());
    }

    public static void showEmployeeListForDept(CommandContext ctx, int deptId, UserSession session, SessionManager sessionManager, UserService userService) {
        List<User> employees = userService.getEmployeesInDepartment(deptId);
        if (employees.isEmpty()) {
            ctx.reply("В отделе нет сотрудников.", "HTML", null, Keyboards.departmentMenu());
            return;
        }
        StringBuilder sb = new StringBuilder("<b>👥 УПРАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯМИ</b>\n\nВведите номер пользователя для редактирования:\n\n");
        List<Long> orderedIds = new ArrayList<>();
        appendManagersAndRegular(sb, employees, orderedIds);
        session.putData("employees_list", orderedIds);
        ctx.reply(sb.toString(), "HTML", null, Keyboards.back());
        sessionManager.updateState(ctx.getUserId(), FsmStates.ADMIN_WAITING_USER_ID);
    }
}
