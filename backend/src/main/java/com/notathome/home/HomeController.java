package com.notathome.home;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HomeController {

  private final HomeService home;

  HomeController(HomeService home) {
    this.home = home;
  }

  /** Public (section 8.2): the front page's categories, latest topics and most active users. */
  @GetMapping("/api/home")
  HomeResponse home() {
    return home.home();
  }
}
