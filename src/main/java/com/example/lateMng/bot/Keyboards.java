package com.example.lateMng.bot;

import com.kaleert.nyagram.api.objects.replykeyboard.ReplyKeyboardMarkup;
import com.kaleert.nyagram.api.objects.replykeyboard.buttons.KeyboardButton;

import java.util.ArrayList;
import java.util.List;

public final class Keyboards {

    private Keyboards() {}

    public static ReplyKeyboardMarkup mainMenu(boolean isVacation, boolean isAdmin) {
        String vacText = isVacation ? "Статус: В отпуске" : "Статус: Работаю";
        var rows = new ArrayList<List<KeyboardButton>>();
        rows.add(List.of(KeyboardButton.text("Опоздаю")));
        rows.add(List.of(KeyboardButton.text("Не приду")));
        rows.add(List.of(KeyboardButton.text(vacText)));
        if (isAdmin) {
            rows.add(List.of(KeyboardButton.text("Админ панель")));
        }
        return ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
    }

    public static ReplyKeyboardMarkup reasonsLate() {
        return ReplyKeyboardMarkup.vertical(true,
                "Пробка", "Проспал", "В поликлинике", "Другое", "Назад");
    }

    public static ReplyKeyboardMarkup reasonsAbsence() {
        return ReplyKeyboardMarkup.vertical(true,
                "Болею", "Семейные обстоятельства", "Другое", "Назад");
    }

    public static ReplyKeyboardMarkup time() {
        var rows = new ArrayList<List<KeyboardButton>>();
        rows.add(List.of(KeyboardButton.text("15 мин"), KeyboardButton.text("30 мин")));
        rows.add(List.of(KeyboardButton.text("1 час"), KeyboardButton.text("1 час 30 мин")));
        rows.add(List.of(KeyboardButton.text("2 часа"), KeyboardButton.text("Другое")));
        rows.add(List.of(KeyboardButton.text("Назад")));
        return ReplyKeyboardMarkup.builder()
                .keyboard(rows)
                .resizeKeyboard(true)
                .build();
    }

    public static ReplyKeyboardMarkup adminHome() {
        return ReplyKeyboardMarkup.vertical(true,
                "Новые заявки", "Управление отделами", "Пользователи без отдела", "Ответственные", "Назад");
    }

    public static ReplyKeyboardMarkup back() {
        return ReplyKeyboardMarkup.vertical(true, "Назад");
    }

    public static ReplyKeyboardMarkup deptsList() {
        return ReplyKeyboardMarkup.vertical(true, "Создать отдел", "Назад");
    }

    public static ReplyKeyboardMarkup nameCheck() {
        return ReplyKeyboardMarkup.vertical(true, "Оставить", "Изменить", "Назад");
    }

    public static ReplyKeyboardMarkup deptChoiceApproval() {
        return ReplyKeyboardMarkup.vertical(true, "Без отдела", "Назад");
    }

    public static ReplyKeyboardMarkup supervisors() {
        return ReplyKeyboardMarkup.vertical(true, "Добавить ответственного", "Назад");
    }

    public static ReplyKeyboardMarkup departmentMenu() {
        return ReplyKeyboardMarkup.vertical(true,
                "Переименовать отдел", "Удалить отдел", "Управление пользователями", "Назад");
    }

    public static ReplyKeyboardMarkup confirmYesBack() {
        return ReplyKeyboardMarkup.vertical(true, "Да", "Назад");
    }

    public static ReplyKeyboardMarkup roleChoice() {
        return ReplyKeyboardMarkup.vertical(true, "Сотрудник", "Начальник", "Назад");
    }

    public static ReplyKeyboardMarkup userEditKeyboard(boolean isAdmin, boolean hasDepartment) {
        String deptBtn = hasDepartment ? "Перевести в другой отдел" : "Добавить в отдел";
        String adminBtn = isAdmin ? "Убрать админа" : "Назначить админом";
        return ReplyKeyboardMarkup.vertical(true,
                "Изменить имя", "Изменить роль", deptBtn, adminBtn, "Удалить", "Назад");
    }
}
