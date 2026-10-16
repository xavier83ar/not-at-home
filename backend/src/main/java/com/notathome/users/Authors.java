package com.notathome.users;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Looks up the authors of a page of posts or topics in one query. */
@Component
public class Authors {

  private final UserRepository users;

  Authors(UserRepository users) {
    this.users = users;
  }

  /** Includes deleted accounts: their posts stay, under the name "Deleted user". */
  public Map<Long, AuthorResponse> byId(Collection<Long> ids) {
    Map<Long, AuthorResponse> found = new HashMap<>();
    for (User user : users.findAllById(ids)) {
      found.put(user.getId(), AuthorResponse.from(user));
    }
    return found;
  }
}
