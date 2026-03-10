package com.pfe.Service;

import com.pfe.Entity.AppUser;

public interface NotificationEventService {
    void onUserRegistered(AppUser user);
}