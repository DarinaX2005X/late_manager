package com.example.lateMng.bot;

public final class FsmStates {

    private FsmStates() {}

    // Регистрация
    public static final String REGISTRATION_NAME = "REGISTRATION_NAME";

    // Отчет (опоздаю / не приду)
    public static final String REPORT_REASON = "REPORT_REASON";
    public static final String REPORT_CUSTOM_REASON = "REPORT_CUSTOM_REASON";
    public static final String REPORT_TIME = "REPORT_TIME";
    public static final String REPORT_CUSTOM_TIME = "REPORT_CUSTOM_TIME";

    // Подтверждение отпуска
    public static final String CONFIRM_VACATION = "CONFIRM_VACATION";

    // Админ
    public static final String ADMIN_HOME = "ADMIN_HOME";
    public static final String ADMIN_WAITING_DEPT_NAME = "ADMIN_WAITING_DEPT_NAME";
    public static final String ADMIN_SELECTING_DEPT = "ADMIN_SELECTING_DEPT";
    public static final String ADMIN_DEPT_MENU = "ADMIN_DEPT_MENU";
    public static final String ADMIN_SELECTING_USER = "ADMIN_SELECTING_USER";
    public static final String ADMIN_EDITING_USER_NAME = "ADMIN_EDITING_USER_NAME";
    public static final String ADMIN_SELECTING_USER_DEPT = "ADMIN_SELECTING_USER_DEPT";
    public static final String ADMIN_SELECTING_USER_ROLE = "ADMIN_SELECTING_USER_ROLE";
    public static final String ADMIN_MANAGING_USERS = "ADMIN_MANAGING_USERS";
    public static final String ADMIN_WAITING_USER_ID = "ADMIN_WAITING_USER_ID";
    public static final String ADMIN_EDITING_EXISTING_USER_NAME = "ADMIN_EDITING_EXISTING_USER_NAME";
    public static final String ADMIN_EDITING_EXISTING_USER_ROLE = "ADMIN_EDITING_EXISTING_USER_ROLE";
    public static final String ADMIN_SELECTING_NEW_DEPT = "ADMIN_SELECTING_NEW_DEPT";
    public static final String ADMIN_WAITING_RENAME_DEPT_NAME = "ADMIN_WAITING_RENAME_DEPT_NAME";
    public static final String ADMIN_CONFIRM_DELETE_DEPT = "ADMIN_CONFIRM_DELETE_DEPT";
    public static final String ADMIN_CONFIRM_DELETE_USER = "ADMIN_CONFIRM_DELETE_USER";
    public static final String ADMIN_MANAGING_SUPERVISORS = "ADMIN_MANAGING_SUPERVISORS";
    public static final String ADMIN_ADDING_SUPERVISOR = "ADMIN_ADDING_SUPERVISOR";
    public static final String ADMIN_SELECTING_NO_DEPT_USER = "ADMIN_SELECTING_NO_DEPT_USER";
}
