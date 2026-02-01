package com.example.lateMng.bot;

import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.fsm.SessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@SuppressWarnings("unused")
@Component
@BotCommand(value = "/start", description = "Старт и главное меню")
@RequiredArgsConstructor
public class StartCommand {

    private final UserService userService;
    private final SessionManager sessionManager;

    @CommandHandler
    public void handle(CommandContext ctx) {
        Long userId = ctx.getUserId();
        sessionManager.clearSession(userId);

        Optional<User> opt = userService.getUser(userId);

        if (opt.isEmpty()) {
            ctx.reply(
                    "<b>📝 РЕГИСТРАЦИЯ</b>\n\nВведите ваше реальное имя\n(Фамилия Имя):"
            );
            sessionManager.startSession(userId, ctx.getChatId(), FsmStates.REGISTRATION_NAME);
            return;
        }

        User user = opt.get();
        if (user.isPending()) {
            ctx.reply("<b>⏳ ОЖИДАНИЕ</b>\n\nВаш аккаунт ожидает подтверждения администратором.");
            return;
        }
        if (user.isRemoved()) {
            ctx.reply("<b>🚫 ДОСТУП ЗАКРЫТ</b>\n\nВаш доступ к боту отключен.");
            return;
        }

        String firstName = firstName(user);
        ctx.reply(
                "<b>👋 Добро пожаловать, " + firstName + "!</b>",
                "HTML",
                null,
                Keyboards.mainMenu(user.getIsOnVacation(), Boolean.TRUE.equals(user.getIsAdmin()))
        );
    }

    private static String firstName(User user) {
        String full = user.getFullName();
        if (full == null || full.isBlank()) return "Пользователь";
        String[] parts = full.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : parts[0];
    }
}
