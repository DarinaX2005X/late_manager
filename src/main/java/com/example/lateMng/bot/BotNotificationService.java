package com.example.lateMng.bot;

import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.api.methods.send.SendMessage;
import com.kaleert.nyagram.api.objects.replykeyboard.InlineKeyboardMarkup;
import com.kaleert.nyagram.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import com.kaleert.nyagram.client.NyagramClient;
import com.kaleert.nyagram.command.CommandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotNotificationService {

    private final NyagramClient client;
    private final UserService userService;

    public void notifyAdminsNewRegistration(CommandContext ctx, String userName) {
        String username = ctx.getTelegramUser().getUsername();
        String text = "<b>🔔 НОВАЯ РЕГИСТРАЦИЯ</b>\n\n"
                + "👤 <b>Имя:</b> " + userName + "\n"
                + "📱 <b>Telegram:</b> @" + (username != null ? username : "");
        var keyboard = InlineKeyboardMarkup.createVertical(
                InlineKeyboardButton.callback("📋 Перейти к заявкам", "admin_users_pending")
        );
        for (User admin : userService.getAdmins()) {
            try {
                var msg = SendMessage.builder()
                        .chatId(admin.getUserId().toString())
                        .text(text)
                        .parseMode("HTML")
                        .replyMarkup(keyboard)
                        .build();
                client.execute(msg);
            } catch (Exception e) {
                log.debug("Failed to notify admin {}: {}", admin.getUserId(), e.getMessage());
            }
        }
    }

    public void notifyUserManualReport(Long targetUserId, String typeLabel, String reason,
                                       String timeVal, String creatorName, String creatorUsername) {
        StringBuilder sb = new StringBuilder("<b>✋ РУЧНАЯ ОТМЕТКА</b>\n\n")
                .append("Вам была поставлена отметка менеджером <b>").append(creatorName)
                .append("</b>").append(BotMessages.usernameTag(creatorUsername)).append(".\n\n")
                .append("<b>Тип:</b> ").append(typeLabel).append("\n")
                .append("<b>Причина:</b> ").append(reason != null ? reason : "—");
        if (timeVal != null) {
            sb.append("\n<b>Опоздание на:</b> ").append(timeVal);
        }
        try {
            client.execute(SendMessage.builder()
                    .chatId(targetUserId.toString())
                    .text(sb.toString())
                    .parseMode("HTML")
                    .build());
        } catch (Exception e) {
            log.debug("Failed to notify user {} about manual report: {}", targetUserId, e.getMessage());
        }
    }

    public int sendToReportRecipients(Long senderId, Integer departmentId, String text) {
        var recipientIds = userService.getReportRecipientIds(senderId, departmentId);
        int sent = 0;
        for (Long uid : recipientIds) {
            try {
                client.execute(SendMessage.builder()
                        .chatId(uid.toString())
                        .text(text)
                        .parseMode("HTML")
                        .build());
                sent++;
            } catch (Exception e) {
                log.debug("Failed to send to {}: {}", uid, e.getMessage());
            }
        }
        return sent;
    }
}
