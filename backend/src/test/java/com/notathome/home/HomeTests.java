package com.notathome.home;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.categories.AdminCategoryService;
import com.notathome.support.Forum;
import com.notathome.support.IntegrationTest;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class HomeTests {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired AdminCategoryService categories;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    Forum.reset(jdbc);
  }

  @AfterEach
  void tearDown() {
    Forum.reset(jdbc);
  }

  @Test
  void aFreshForumHasNothingToShow() throws Exception {
    mockMvc
        .perform(get("/api/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.highlightedCategories.length()").value(0))
        .andExpect(jsonPath("$.latestTopics.length()").value(0))
        .andExpect(jsonPath("$.activeUsers.length()").value(0));
  }

  @Test
  void highlightedCategoriesAreTheBusiestOfTheLast30DaysThenTheBusiestOverall() throws Exception {
    Long hiking = categories.create("Hiking", "Trails", null).getId();
    Long climbing = categories.create("Climbing", "Rock", null).getId();
    Long camping = categories.create("Camping", "Tents", null).getId();
    Long gear = categories.create("Gear", "Stuff", null).getId();
    categories.create("Trip Reports", "Stories", null);
    Cookie alice = session("alice");

    // Hiking has the most posts overall, but all of them are old.
    Long oldHiking = Forum.topic(mockMvc, alice, hiking, "Old hiking topic");
    Forum.reply(mockMvc, alice, oldHiking, "one");
    Forum.reply(mockMvc, alice, oldHiking, "two");
    Long oldCamping = Forum.topic(mockMvc, alice, camping, "Old camping topic");
    backdate(oldHiking);
    backdate(oldCamping);
    // Climbing has three recent posts, Gear one.
    Long climbingTopic = Forum.topic(mockMvc, alice, climbing, "Climbing topic");
    Forum.reply(mockMvc, alice, climbingTopic, "one");
    Forum.reply(mockMvc, alice, climbingTopic, "two");
    Forum.topic(mockMvc, alice, gear, "Gear topic");

    mockMvc
        .perform(get("/api/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.highlightedCategories.length()").value(4))
        .andExpect(jsonPath("$.highlightedCategories[0].slug").value("climbing"))
        .andExpect(jsonPath("$.highlightedCategories[0].topicCount").value(1))
        .andExpect(jsonPath("$.highlightedCategories[1].slug").value("gear"))
        // Filled up from the rest by all-time posts; the empty category misses the cut.
        .andExpect(jsonPath("$.highlightedCategories[2].slug").value("hiking"))
        .andExpect(jsonPath("$.highlightedCategories[3].slug").value("camping"));
  }

  @Test
  void fewerThanFourCategoriesAreAllShown() throws Exception {
    categories.create("Hiking", "Trails", null);
    categories.create("Climbing", "Rock", null);

    mockMvc
        .perform(get("/api/home"))
        .andExpect(jsonPath("$.highlightedCategories.length()").value(2))
        .andExpect(jsonPath("$.highlightedCategories[0].slug").value("hiking"));
  }

  @Test
  void latestTopicsAreTheFiveMostRecentlyActiveAndSkipRemovedOnes() throws Exception {
    Long hiking = categories.create("Hiking", "Trails", null).getId();
    Cookie alice = session("alice");
    Long first = Forum.topic(mockMvc, alice, hiking, "Topic 1");
    for (int i = 2; i <= 6; i++) {
      Forum.topic(mockMvc, alice, hiking, "Topic " + i);
    }
    Long removed = Forum.topic(mockMvc, alice, hiking, "Removed topic");
    jdbc.update("update topics set deleted_at = now() where id = ?", removed);
    // A reply brings the oldest topic back to the top.
    Forum.reply(mockMvc, alice, first, "bump");

    mockMvc
        .perform(get("/api/home"))
        .andExpect(jsonPath("$.latestTopics.length()").value(5))
        .andExpect(jsonPath("$.latestTopics[0].title").value("Topic 1"))
        .andExpect(jsonPath("$.latestTopics[0].replyCount").value(1))
        .andExpect(jsonPath("$.latestTopics[0].category.slug").value("hiking"))
        .andExpect(jsonPath("$.latestTopics[0].author.username").value("alice"))
        .andExpect(jsonPath("$.latestTopics[1].title").value("Topic 6"))
        .andExpect(jsonPath("$.latestTopics[4].title").value("Topic 3"));
  }

  @Test
  void mostActiveUsersCountRecentVisiblePostsOfActiveAccountsOnly() throws Exception {
    Long hiking = categories.create("Hiking", "Trails", null).getId();
    Cookie alice = session("alice");
    Cookie bob = session("bob");
    Cookie carol = session("carol");
    Cookie dave = session("dave");
    Cookie erin = session("erin");

    Long topic = Forum.topic(mockMvc, alice, hiking, "Chat"); // alice: 1
    Forum.reply(mockMvc, alice, topic, "a2"); // alice: 2
    Forum.reply(mockMvc, alice, topic, "a3"); // alice: 3
    Forum.reply(mockMvc, bob, topic, "b1"); // bob: 1
    Long removedReply = Forum.reply(mockMvc, bob, topic, "b2"); // removed below
    jdbc.update("update posts set deleted_at = now() where id = ?", removedReply);
    Forum.reply(mockMvc, bob, topic, "b3"); // bob: 2
    for (int i = 0; i < 6; i++) {
      Forum.reply(mockMvc, carol, topic, "banned " + i);
      Forum.reply(mockMvc, dave, topic, "deleted " + i);
    }
    jdbc.update("update users set status = 'BANNED' where username = 'carol'");
    jdbc.update("update users set deleted_at = now() where username = 'dave'");
    // Erin only has old posts.
    Long erinsReply = Forum.reply(mockMvc, erin, topic, "long ago");
    jdbc.update(
        "update posts set created_at = now() - interval '31 days' where id = ?", erinsReply);

    mockMvc
        .perform(get("/api/home"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeUsers.length()").value(2))
        .andExpect(jsonPath("$.activeUsers[0].user.username").value("alice"))
        .andExpect(jsonPath("$.activeUsers[0].postCount").value(3))
        .andExpect(jsonPath("$.activeUsers[0].user.email").doesNotExist())
        .andExpect(jsonPath("$.activeUsers[1].user.username").value("bob"))
        .andExpect(jsonPath("$.activeUsers[1].postCount").value(2));
  }

  @Test
  void postsInRemovedTopicsDoNotCount() throws Exception {
    Long hiking = categories.create("Hiking", "Trails", null).getId();
    Cookie alice = session("alice");
    Long topic = Forum.topic(mockMvc, alice, hiking, "Doomed");
    Forum.reply(mockMvc, alice, topic, "reply");
    jdbc.update("update topics set deleted_at = now() where id = ?", topic);

    mockMvc
        .perform(get("/api/home"))
        .andExpect(jsonPath("$.activeUsers.length()").value(0))
        .andExpect(jsonPath("$.highlightedCategories[0].postCount").value(0));
  }

  private Cookie session(String username) throws Exception {
    return Forum.userSession(
        mockMvc, users, passwordEncoder, username, UserRole.USER, /* verified= */ true);
  }

  /** Makes every post of the topic older than the 30 days the home page looks at. */
  private void backdate(Long topicId) {
    jdbc.update(
        "update posts set created_at = now() - interval '40 days' where topic_id = ?", topicId);
  }
}
