package com.example.lateMng.bot;

import com.example.lateMng.bot.admin.AdminFlowService;
import com.example.lateMng.service.UserService;
import com.kaleert.nyagram.callback.annotation.Callback;
import com.kaleert.nyagram.command.CommandContext;
import com.kaleert.nyagram.fsm.SessionManager;
import com.kaleert.nyagram.security.LevelRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@SuppressWarnings("unused")
@Component
@RequiredArgsConstructor
public class PendingCallbackHandler {

    private final SessionManager sessionManager;
    private final UserService userService;

    @Callback("admin_users_pending")
    @LevelRequired(min = 3)
    public void onAdminUsersPending(CommandContext ctx) {
        AdminFlowService.showPendingUsers(ctx, sessionManager, userService);
    }
}
