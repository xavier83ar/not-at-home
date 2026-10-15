package com.notathome.system;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InfoController {

  private final String name;
  private final String version;

  InfoController(
      @Value("${spring.application.name}") String name, ObjectProvider<BuildProperties> build) {
    this.name = name;
    this.version = build.stream().findFirst().map(BuildProperties::getVersion).orElse("dev");
  }

  @GetMapping("/api/info")
  AppInfo info() {
    return new AppInfo(name, version);
  }

  record AppInfo(String name, String version) {}
}
