package com.example.lateMng.bot;

import com.kaleert.nyagram.command.BotCommand;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.command.CommandHandler;
import com.kaleert.nyagram.fsm.SessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@SuppressWarnings("unused")
@Component
@BotCommand
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
public class UnknownCommandHandler {

    private final SessionManager sessionManager;

    @CommandHandler
    public void onUnknown(CommandContext ctx) {
        Long uid = ctx.getUserId();
        if (sessionManager.getSession(uid) != null) {
            return;
        }
        ctx.reply(BotMessages.err(BotMessages.MSG_UNKNOWN_COMMAND), "HTML", null, null);
    }
}
