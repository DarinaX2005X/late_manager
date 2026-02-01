package com.example.lateMng.bot;

import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.fsm.UserSession;
import com.kaleert.nyagram.fsm.annotation.StateAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
public class RegistrationHandler {

    private final UserService userService;
    private final SessionManager sessionManager;
    private final BotNotificationService notificationService;

    @StateAction(FsmStates.REGISTRATION_NAME)
    public void onNameInput(CommandContext ctx, UserSession session) {
        String userName = ctx.getText().trim();
        Long userId = ctx.getUserId();
        String username = ctx.getTelegramUser().getUsername();

        userService.addOrUpdateUser(userId, username != null ? username : "", userName);
        notificationService.notifyAdminsNewRegistration(ctx, userName);
        ctx.reply(
                "<b>✅ ЗАЯВКА ОТПРАВЛЕНА</b>\n\nВаша заявка отправлена администратору.\nОжидайте подтверждения.",
                "HTML",
                null,
                null
        );
        sessionManager.clearSession(userId);
    }
}
