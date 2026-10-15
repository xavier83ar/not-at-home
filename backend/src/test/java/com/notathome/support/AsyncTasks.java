package com.notathome.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** For tests of things the application does on another thread, such as sending emails. */
public final class AsyncTasks {

  private AsyncTasks() {}

  /**
   * Waits until every task given to the executor has finished. Lets a test assert that something
   * was <em>not</em> done (no email sent) without sleeping and hoping.
   */
  public static void awaitIdle(ThreadPoolTaskExecutor executor) {
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                executor.getThreadPoolExecutor().getTaskCount()
                    == executor.getThreadPoolExecutor().getCompletedTaskCount());
  }
}
