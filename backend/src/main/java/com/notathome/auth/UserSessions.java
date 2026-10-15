package com.notathome.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Logs a user out everywhere: every session and every remembered login. Used when their credentials
 * change or their access is taken away (password reset now; password change, ban and demotion
 * later). Sessions are found by the username, which is the principal name Spring Session indexes
 * them under.
 */
@Service
class UserSessions {

  private final FindByIndexNameSessionRepository<? extends Session> sessions;
  private final RememberMeTokenRepository rememberMeTokens;

  UserSessions(
      FindByIndexNameSessionRepository<? extends Session> sessions,
      RememberMeTokenRepository rememberMeTokens) {
    this.sessions = sessions;
    this.rememberMeTokens = rememberMeTokens;
  }

  void endAll(String username) {
    sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
    rememberMeTokens.removeUserTokens(username);
  }
}
