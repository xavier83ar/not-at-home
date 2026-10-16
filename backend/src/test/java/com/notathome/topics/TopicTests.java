package com.notathome.topics;

import static com.notathome.support.Forum.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.notathome.categories.AdminCategoryService;
import com.notathome.support.Csrf;
import com.notathome.support.Forum;
import com.notathome.support.IntegrationTest;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class TopicTests {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired AdminCategoryService categories;
  @Autowired JdbcTemplate jdbc;

  private Long categoryId;
  private Cookie alice;

  @BeforeEach
  void setUp() throws Exception {
    Forum.reset(jdbc);
    categoryId = categories.create("Hiking", "Trails", null).getId();
    alice = session("alice", UserRole.USER, true);
  }

  @AfterEach
  void tearDown() {
    Forum.reset(jdbc);
  }

  @Test
  void aVerifiedUserCreatesATopicWithItsOpeningPost() throws Exception {
    createTopic(alice, "Best trails in Patagonia", "Tell me <b>everything</b>")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Best trails in Patagonia"))
        .andExpect(jsonPath("$.slug").value("best-trails-in-patagonia"))
        .andExpect(jsonPath("$.author.username").value("alice"))
        .andExpect(jsonPath("$.author.email").doesNotExist())
        .andExpect(jsonPath("$.replyCount").value(0))
        .andExpect(jsonPath("$.pinned").value(false))
        .andExpect(jsonPath("$.locked").value(false));

    Long topicId = jdbc.queryForObject("select id from topics", Long.class);
    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category.slug").value("hiking"))
        .andExpect(jsonPath("$.posts.items.length()").value(1))
        .andExpect(jsonPath("$.posts.items[0].opening").value(true))
        .andExpect(jsonPath("$.posts.items[0].removed").value(false))
        .andExpect(
            jsonPath("$.posts.items[0].content.blocks[0].data.text")
                .value("Tell me <b>everything</b>"));
  }

  @Test
  void guestsAndUnverifiedUsersCannotCreateTopics() throws Exception {
    mockMvc
        .perform(
            post("/api/topics")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicJson(categoryId, "Title", document("Body"))))
        .andExpect(status().isUnauthorized());

    Cookie unverified = session("bob", UserRole.USER, false);
    createTopic(unverified, "Title", "Body")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    assertThat(jdbc.queryForObject("select count(*) from topics", Long.class)).isZero();
  }

  @Test
  void creatingATopicNeedsACsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/topics")
                .cookie(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(topicJson(categoryId, "Title", document("Body"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void validatesTheTitleCategoryAndContent() throws Exception {
    createTopic(alice, "x".repeat(129), "Body")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.title[0]").value("must be at most 128 characters"));
    createTopic(alice, "   ", "Body")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.title").exists());
    postJson(alice, "/api/topics", topicJson(999_999L, "Title", document("Body")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.categoryId[0]").value("category not found"));
    postJson(alice, "/api/topics", topicJson(categoryId, "Title", "{\"blocks\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.content[0]").value("can't be empty"));
    postJson(alice, "/api/topics", "{\"categoryId\":%d,\"title\":\"Title\"}".formatted(categoryId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.content").exists());
    assertThat(jdbc.queryForObject("select count(*) from topics", Long.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from posts", Long.class)).isZero();
  }

  @Test
  void storesSanitizedContentAndSearchText() throws Exception {
    createTopic(alice, "Title", "Hi <script>alert(1)</script><b>there</b>")
        .andExpect(status().isCreated());

    String stored = jdbc.queryForObject("select content::text from posts", String.class);
    assertThat(stored).doesNotContain("script").contains("<b>there</b>");
    assertThat(jdbc.queryForObject("select search_text from posts", String.class))
        .isEqualTo("Hi there");
  }

  @Test
  void aTitleWithNothingForASlugStillGetsOne() throws Exception {
    createTopic(alice, "!!!", "Body").andExpect(jsonPath("$.slug").value("topic"));
  }

  @Test
  void repliesAreAddedAndCounted() throws Exception {
    Long topicId = newTopic("Question");
    Cookie bob = session("bob", UserRole.USER, true);

    postJson(
            bob, "/api/topics/" + topicId + "/posts", "{\"content\":" + document("An answer") + "}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.author.username").value("bob"))
        .andExpect(jsonPath("$.opening").value(false))
        .andExpect(jsonPath("$.topicId").value(topicId));

    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(jsonPath("$.topic.replyCount").value(1))
        .andExpect(jsonPath("$.posts.items.length()").value(2))
        .andExpect(jsonPath("$.posts.items[1].author.username").value("bob"));
    Long lastPostId = jdbc.queryForObject("select max(id) from posts", Long.class);
    mockMvc
        .perform(get("/api/categories/hiking/topics"))
        .andExpect(jsonPath("$.items[0].replyCount").value(1))
        .andExpect(jsonPath("$.items[0].lastPostId").value(lastPostId));
  }

  @Test
  void aReplyBumpsTheTopicInTheCategoryList() throws Exception {
    Long older = newTopic("Older");
    Long newer = newTopic("Newer");
    assertThat(topicIds()).containsExactly(newer.intValue(), older.intValue());

    postJson(alice, "/api/topics/" + older + "/posts", "{\"content\":" + document("Bump") + "}")
        .andExpect(status().isCreated());

    assertThat(topicIds()).containsExactly(older.intValue(), newer.intValue());
  }

  @Test
  void pinnedTopicsComeFirst() throws Exception {
    Long pinned = newTopic("Pinned");
    Long other = newTopic("Other");
    jdbc.update("update topics set is_pinned = true where id = ?", pinned);
    postJson(alice, "/api/topics/" + other + "/posts", "{\"content\":" + document("Bump") + "}");

    assertThat(topicIds()).containsExactly(pinned.intValue(), other.intValue());
  }

  @Test
  void aLockedTopicRefusesRepliesFromEveryone() throws Exception {
    Long topicId = newTopic("Locked");
    jdbc.update("update topics set is_locked = true where id = ?", topicId);
    Cookie admin = session("root", UserRole.ADMIN, true);

    for (Cookie who : new Cookie[] {alice, admin}) {
      postJson(who, "/api/topics/" + topicId + "/posts", "{\"content\":" + document("Hello") + "}")
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("TOPIC_LOCKED"));
    }
    assertThat(jdbc.queryForObject("select count(*) from posts", Long.class)).isEqualTo(1);
  }

  @Test
  void repliesNeedAVerifiedUserAndAnExistingTopic() throws Exception {
    Long topicId = newTopic("Question");
    Cookie unverified = session("bob", UserRole.USER, false);

    postJson(
            unverified, "/api/topics/" + topicId + "/posts", "{\"content\":" + document("Hi") + "}")
        .andExpect(status().isForbidden());
    postJson(alice, "/api/topics/999999/posts", "{\"content\":" + document("Hi") + "}")
        .andExpect(status().isNotFound());
    postJson(alice, "/api/topics/" + topicId + "/posts", "{\"content\":{\"blocks\":[]}}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void allTopicsAreListedAcrossCategoriesForGuests() throws Exception {
    Long climbingId = categories.create("Climbing", "Rock", null).getId();
    for (int i = 1; i <= 8; i++) {
      newTopic("Hiking " + i);
    }
    Long removed = newTopic("Removed");
    jdbc.update("update topics set deleted_at = now() where id = ?", removed);
    Forum.topic(mockMvc, alice, climbingId, "Climbing 1");
    Forum.topic(mockMvc, alice, climbingId, "Climbing 2");
    Forum.topic(mockMvc, alice, climbingId, "Climbing 3");

    mockMvc
        .perform(get("/api/topics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(10))
        .andExpect(jsonPath("$.totalItems").value(11))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items[0].title").value("Climbing 3"))
        .andExpect(jsonPath("$.items[0].category.slug").value("climbing"))
        .andExpect(jsonPath("$.items[3].category.slug").value("hiking"));
    mockMvc
        .perform(get("/api/topics?page=2"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].title").value("Hiking 1"));
    mockMvc.perform(get("/api/topics?page=3")).andExpect(status().isNotFound());
  }

  @Test
  void topicListsAndPostsArePaginatedByTen() throws Exception {
    for (int i = 1; i <= 12; i++) {
      newTopic("Topic " + i);
    }
    mockMvc
        .perform(get("/api/categories/hiking/topics"))
        .andExpect(jsonPath("$.items.length()").value(10))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.totalItems").value(12))
        .andExpect(jsonPath("$.items[0].title").value("Topic 12"));
    mockMvc
        .perform(get("/api/categories/hiking/topics?page=2"))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[1].title").value("Topic 1"));
    mockMvc.perform(get("/api/categories/hiking/topics?page=3")).andExpect(status().isNotFound());

    Long topicId = newTopic("Busy");
    for (int i = 1; i <= 11; i++) {
      postJson(
              alice,
              "/api/topics/" + topicId + "/posts",
              "{\"content\":" + document("Reply " + i) + "}")
          .andExpect(status().isCreated());
    }
    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(jsonPath("$.posts.items.length()").value(10))
        .andExpect(jsonPath("$.posts.totalPages").value(2))
        .andExpect(jsonPath("$.posts.items[0].opening").value(true))
        .andExpect(jsonPath("$.topic.replyCount").value(11));
    mockMvc
        .perform(get("/api/topics/" + topicId + "?page=2"))
        .andExpect(jsonPath("$.posts.items.length()").value(2))
        .andExpect(jsonPath("$.posts.items[0].opening").value(false));
    mockMvc.perform(get("/api/topics/" + topicId + "?page=3")).andExpect(status().isNotFound());
  }

  @Test
  void emptyCategoriesHaveAnEmptyFirstPage() throws Exception {
    mockMvc
        .perform(get("/api/categories/hiking/topics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void unknownCategoriesAndTopicsAreNotFound() throws Exception {
    mockMvc.perform(get("/api/categories/nope/topics")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/topics/999999")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/posts/999999/location")).andExpect(status().isNotFound());
  }

  @Test
  void aPostsLocationSaysWhichPageItIsOn() throws Exception {
    Long topicId = newTopic("Busy");
    for (int i = 1; i <= 11; i++) {
      postJson(
          alice,
          "/api/topics/" + topicId + "/posts",
          "{\"content\":" + document("Reply " + i) + "}");
    }
    Long first = jdbc.queryForObject("select min(id) from posts", Long.class);
    Long tenth =
        jdbc.queryForObject("select id from posts order by id limit 1 offset 9", Long.class);
    Long eleventh =
        jdbc.queryForObject("select id from posts order by id limit 1 offset 10", Long.class);

    mockMvc
        .perform(get("/api/posts/" + first + "/location"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.topicId").value(topicId))
        .andExpect(jsonPath("$.topicSlug").value("busy"));
    mockMvc
        .perform(get("/api/posts/" + tenth + "/location"))
        .andExpect(jsonPath("$.page").value(1));
    mockMvc
        .perform(get("/api/posts/" + eleventh + "/location"))
        .andExpect(jsonPath("$.page").value(2));
  }

  @Test
  void topicsAreDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/topics']").exists())
        .andExpect(jsonPath("$.paths['/api/topics/{id}']").exists())
        .andExpect(jsonPath("$.paths['/api/topics/{id}/posts']").exists())
        .andExpect(jsonPath("$.paths['/api/categories/{slug}/topics']").exists())
        .andExpect(jsonPath("$.paths['/api/posts/{id}/location']").exists());
  }

  private java.util.List<Integer> topicIds() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/categories/hiking/topics"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.items[*].id");
  }

  private Long newTopic(String title) throws Exception {
    String body =
        createTopic(alice, title, "Opening post")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private ResultActions createTopic(Cookie who, String title, String paragraph) throws Exception {
    return postJson(who, "/api/topics", topicJson(categoryId, title, document(paragraph)));
  }

  private ResultActions postJson(Cookie who, String url, String json) throws Exception {
    return mockMvc.perform(
        post(url)
            .cookie(who)
            .with(Csrf.token(mockMvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private Cookie session(String username, UserRole role, boolean verified) throws Exception {
    return Forum.userSession(mockMvc, users, passwordEncoder, username, role, verified);
  }

  private static String topicJson(Long categoryId, String title, String content) {
    return "{\"categoryId\":%d,\"title\":\"%s\",\"content\":%s}"
        .formatted(categoryId, title, content);
  }
}
