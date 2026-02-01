package com.example.lateMng.bot;

import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.meta.CommandMeta;
import com.kaleert.nyagram.middleware.Middleware;
import com.kaleert.nyagram.middleware.MiddlewareChain;
import com.kaleert.nyagram.middleware.MiddlewareResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class UnknownCommandMiddleware implements Middleware {

    private final SessionManager sessionManager;

    @Override
    public CompletableFuture<MiddlewareResult> handle(CommandContext context, CommandMeta meta, MiddlewareChain next) {
        boolean isFallback = "fallback".equals(meta.getFullCommandPath());
        boolean userInDialog = sessionManager.getSession(context.getUserId()) != null;

        if (!isFallback || userInDialog) {
            return next.proceed();
        }

        context.reply(BotMessages.err(BotMessages.MSG_UNKNOWN_COMMAND), "HTML", null, null);
        return CompletableFuture.completedFuture(MiddlewareResult.stopResult(null));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
