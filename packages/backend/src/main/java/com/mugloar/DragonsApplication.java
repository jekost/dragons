package com.mugloar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the Dragons of Mugloar backend. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DragonsApplication {

  static void main(String[] args) {
    SpringApplication.run(DragonsApplication.class, args);
  }
}
