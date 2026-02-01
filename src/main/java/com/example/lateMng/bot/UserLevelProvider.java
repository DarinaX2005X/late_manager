package com.example.lateMng.bot;

import com.example.lateMng.entity.User;
import com.example.lateMng.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Уровни:
// 1) нет/удален/pending
// 2) обычный пользователь с доступ к боту
// 3) админ
// Использование @LevelRequired(min = 2)

@Component
@RequiredArgsConstructor
public class UserLevelProvider implements com.kaleert.nyagram.security.spi.UserLevelProvider {

    private final UserService userService;

    @Override
    public Integer getUserLevel(com.kaleert.nyagram.api.objects.User telegramUser) {
        if (telegramUser == null || telegramUser.getId() == null) return 0;
        return userService.getUser(telegramUser.getId())
                .map(this::levelFor)
                .orElse(1);
    }

    private int levelFor(User u) {
        if (u.isRemoved() || u.isPending()) return 1;
        if (!u.isActive()) return 1;
        return Boolean.TRUE.equals(u.getIsAdmin()) ? 3 : 2;
    }
}
