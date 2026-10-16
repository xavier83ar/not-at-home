package com.notathome.posts;

import static com.notathome.support.Forum.document;
import static com.notathome.support.Forum.reply;
import static com.notathome.support.Forum.send;
import static com.notathome.support.Forum.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class PostTests {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired AdminCategoryService categories;
  @Autowired JdbcTemplate jdbc;

  private Long categoryId;
  private Cookie alice;
  private Cookie bob;

  @BeforeEach
  void setUp() throws Exception {
    Forum.reset(jdbc);
    categoryId = categories.create("Hiking", "Trails", null).getId();
    alice = session("alice", UserRole.USER, true);
    bob = session("bob", UserRole.USER, true);
  }

  @AfterEach
  void tearDown() {
    Forum.reset(jdbc);
  }

  // --- editing (FR-5.1)

  @Test
  void anAuthorEditsTheirPostAndItShowsAsEdited() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    Long postId = reply(mockMvc, alice, topicId, "first version");

    edit(alice, postId, "second <b>version</b>")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.blocks[0].data.text").value("second <b>version</b>"))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());

    assertThat(
            jdbc.queryForObject("select search_text from posts where id = ?", String.class, postId))
        .isEqualTo("second version");
    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(jsonPath("$.posts.items[1].editedAt").isNotEmpty())
        .andExpect(jsonPath("$.posts.items[0].editedAt").doesNotExist());
  }

  @Test
  void editingChecksWhoIsAskingAndWhatIsSent() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    Long postId = reply(mockMvc, alice, topicId, "mine");

    edit(bob, postId, "hijacked").andExpect(status().isForbidden());
    edit(alice, 999_999L, "x").andExpect(status().isNotFound());
    send(mockMvc, HttpMethod.PUT, alice, "/api/posts/" + postId, "{\"content\":{\"blocks\":[]}}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.content[0]").value("can't be empty"));
    mockMvc
        .perform(
            put("/api/posts/" + postId)
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + document("x") + "}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/posts/" + postId)
                .cookie(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":" + document("x") + "}"))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject("select search_text from posts where id = ?", String.class, postId))
        .isEqualTo("mine");
  }

  @Test
  void aRemovedPostCannotBeEdited() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    Long postId = reply(mockMvc, alice, topicId, "gone soon");
    deletePost(alice, postId).andExpect(status().isOk());

    edit(alice, postId, "back again").andExpect(status().isNotFound());
  }

  // --- deleting (FR-5.2, FR-5.3, FR-5.5, FR-5.6)

  @Test
  void deletingAReplyLeavesAPlaceholderAndRecountsTheTopic() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    reply(mockMvc, bob, topicId, "kept");
    Long removed = reply(mockMvc, alice, topicId, "to remove");

    deletePost(alice, removed)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topicRemoved").value(false));

    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(jsonPath("$.removed").value(false))
        .andExpect(jsonPath("$.topic.replyCount").value(1))
        .andExpect(jsonPath("$.posts.items.length()").value(3))
        .andExpect(jsonPath("$.posts.items[2].removed").value(true))
        .andExpect(jsonPath("$.posts.items[2].content").doesNotExist())
        .andExpect(jsonPath("$.posts.items[1].removed").value(false));
    assertThat(
            jdbc.queryForObject(
                "select last_post_at = (select max(created_at) from posts where deleted_at is null)"
                    + " from topics",
                Boolean.class))
        .isTrue();
    mockMvc
        .perform(get("/api/categories/hiking/topics"))
        .andExpect(jsonPath("$.items[0].replyCount").value(1));
  }

  @Test
  void adminsStillSeeTheContentOfARemovedPost() throws Exception {
    Cookie admin = session("root", UserRole.ADMIN, true);
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    Long removed = reply(mockMvc, alice, topicId, "secret words");
    deletePost(alice, removed).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/topics/" + topicId).cookie(admin))
        .andExpect(jsonPath("$.posts.items[1].removed").value(true))
        .andExpect(jsonPath("$.posts.items[1].content.blocks[0].data.text").value("secret words"));
    mockMvc
        .perform(get("/api/topics/" + topicId).cookie(bob))
        .andExpect(jsonPath("$.posts.items[1].content").doesNotExist());
  }

  @Test
  void aRemovedPostKeepsItsPlaceInThePagesAndItsPermalink() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Busy");
    Long first = reply(mockMvc, alice, topicId, "r1");
    for (int i = 2; i <= 10; i++) {
      reply(mockMvc, alice, topicId, "r" + i);
    }
    Long eleventh = reply(mockMvc, alice, topicId, "r11");
    deletePost(alice, first).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/posts/" + eleventh + "/location"))
        .andExpect(jsonPath("$.page").value(2));
    mockMvc
        .perform(get("/api/posts/" + first + "/location"))
        .andExpect(jsonPath("$.page").value(1));
  }

  @Test
  void theListLinksToTheLatestPostThatIsStillThere() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    Long kept = reply(mockMvc, bob, topicId, "kept");
    Long removed = reply(mockMvc, alice, topicId, "removed");
    deletePost(alice, removed).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/categories/hiking/topics"))
        .andExpect(jsonPath("$.items[0].lastPostId").value(kept));
  }

  @Test
  void deletingTheOpeningPostOfATopicWithoutRepliesRemovesTheTopic() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Lonely");
    Long openingId = jdbc.queryForObject("select min(id) from posts", Long.class);

    deletePost(alice, openingId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topicRemoved").value(true));

    assertThat(
            jdbc.queryForObject("select count(*) from posts where deleted_at is null", Long.class))
        .isZero();
    mockMvc
        .perform(get("/api/categories/hiking/topics"))
        .andExpect(jsonPath("$.totalItems").value(0));
    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.removed").value(true))
        .andExpect(jsonPath("$.topic").doesNotExist())
        .andExpect(jsonPath("$.posts").doesNotExist());
    send(
            mockMvc,
            HttpMethod.POST,
            bob,
            "/api/topics/" + topicId + "/posts",
            "{\"content\":" + document("late") + "}")
        .andExpect(status().isNotFound());
  }

  @Test
  void aRemovedTopicListsTheFiveMostRecentlyActiveOthers() throws Exception {
    Long removedTopic = topic(mockMvc, alice, categoryId, "Removed one");
    for (int i = 1; i <= 6; i++) {
      topic(mockMvc, bob, categoryId, "Other " + i);
    }
    Long openingId = jdbc.queryForObject("select min(id) from posts", Long.class);
    deletePost(alice, openingId).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/topics/" + removedTopic))
        .andExpect(jsonPath("$.removed").value(true))
        .andExpect(jsonPath("$.category.slug").value("hiking"))
        .andExpect(jsonPath("$.recentTopics.length()").value(5))
        .andExpect(jsonPath("$.recentTopics[0].title").value("Other 6"))
        .andExpect(jsonPath("$.recentTopics[4].title").value("Other 2"));
  }

  @Test
  void adminsCanStillSeeARemovedTopic() throws Exception {
    Cookie admin = session("root", UserRole.ADMIN, true);
    Long topicId = topic(mockMvc, alice, categoryId, "Hidden title");
    Long openingId = jdbc.queryForObject("select min(id) from posts", Long.class);
    deletePost(alice, openingId).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/topics/" + topicId).cookie(admin))
        .andExpect(jsonPath("$.removed").value(true))
        .andExpect(jsonPath("$.topic.title").value("Hidden title"))
        .andExpect(jsonPath("$.posts.items[0].removed").value(true))
        .andExpect(jsonPath("$.posts.items[0].content.blocks[0].data.text").value("Opening post"));
    mockMvc
        .perform(get("/api/posts/" + openingId + "/location").cookie(admin))
        .andExpect(jsonPath("$.topicId").value(topicId))
        .andExpect(jsonPath("$.topicSlug").value("hidden-title"));
    // Everyone else is given the topic's id but not a slug, which would spell out the title.
    mockMvc
        .perform(get("/api/posts/" + openingId + "/location"))
        .andExpect(jsonPath("$.topicId").value(topicId))
        .andExpect(jsonPath("$.topicSlug").value(""));
  }

  @Test
  void deletingTheOpeningPostOfATopicWithRepliesKeepsTheTopic() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Discussed");
    reply(mockMvc, bob, topicId, "a reply");
    Long openingId = jdbc.queryForObject("select min(id) from posts", Long.class);

    deletePost(alice, openingId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topicRemoved").value(false));

    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(jsonPath("$.removed").value(false))
        .andExpect(jsonPath("$.topic.title").value("Discussed"))
        .andExpect(jsonPath("$.topic.replyCount").value(1))
        .andExpect(jsonPath("$.posts.items[0].removed").value(true))
        .andExpect(jsonPath("$.posts.items[0].opening").value(true))
        .andExpect(jsonPath("$.posts.items[0].content").doesNotExist())
        .andExpect(jsonPath("$.posts.items[1].removed").value(false));
  }

  @Test
  void theTopicGoesWhenItsLastVisibleReplyIsDeletedBeforeTheOpeningPost() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Discussed");
    Long replyId = reply(mockMvc, bob, topicId, "a reply");
    Long openingId = jdbc.queryForObject("select min(id) from posts", Long.class);
    deletePost(bob, replyId).andExpect(jsonPath("$.topicRemoved").value(false));

    deletePost(alice, openingId).andExpect(jsonPath("$.topicRemoved").value(true));

    mockMvc.perform(get("/api/topics/" + topicId)).andExpect(jsonPath("$.removed").value(true));
  }

  @Test
  void deletingChecksWhoIsAskingAndWhetherThePostIsThere() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Question");
    Long postId = reply(mockMvc, alice, topicId, "mine");

    deletePost(bob, postId).andExpect(status().isForbidden());
    deletePost(alice, 999_999L).andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/posts/" + postId).cookie(alice))
        .andExpect(status().isForbidden()); // no CSRF token
    mockMvc
        .perform(delete("/api/posts/" + postId).with(Csrf.token(mockMvc)))
        .andExpect(status().isUnauthorized());

    deletePost(alice, postId).andExpect(status().isOk());
    deletePost(alice, postId).andExpect(status().isNotFound());
  }

  // --- categories (FR-2.2, FR-2.4)

  @Test
  void categoriesShowTheirTopicAndPostCountsAndLatestActivity() throws Exception {
    mockMvc
        .perform(get("/api/categories/hiking"))
        .andExpect(jsonPath("$.topicCount").value(0))
        .andExpect(jsonPath("$.postCount").value(0))
        .andExpect(jsonPath("$.latestActivityAt").doesNotExist());

    Long topicId = topic(mockMvc, alice, categoryId, "One");
    topic(mockMvc, alice, categoryId, "Two");
    Long replyId = reply(mockMvc, bob, topicId, "reply");
    Long otherCategory = categories.create("Climbing", "Routes", null).getId();
    topic(mockMvc, alice, otherCategory, "Elsewhere");

    mockMvc
        .perform(get("/api/categories/hiking"))
        .andExpect(jsonPath("$.topicCount").value(2))
        .andExpect(jsonPath("$.postCount").value(3))
        .andExpect(jsonPath("$.latestActivityAt").isNotEmpty());
    mockMvc
        .perform(get("/api/categories"))
        .andExpect(jsonPath("$[0].topicCount").value(2))
        .andExpect(jsonPath("$[1].topicCount").value(1))
        .andExpect(jsonPath("$[1].postCount").value(1));

    deletePost(bob, replyId).andExpect(status().isOk());
    mockMvc.perform(get("/api/categories/hiking")).andExpect(jsonPath("$.postCount").value(2));
  }

  @Test
  void aCategoryWithTopicsCannotBeDeleted() throws Exception {
    Cookie admin = session("root", UserRole.ADMIN, true);
    topic(mockMvc, alice, categoryId, "One");

    deleteCategory(admin)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("still has topics")));
    mockMvc.perform(get("/api/categories/hiking")).andExpect(status().isOk());

    Long openingId = jdbc.queryForObject("select min(id) from posts", Long.class);
    deletePost(alice, openingId).andExpect(jsonPath("$.topicRemoved").value(true));

    deleteCategory(admin).andExpect(status().isNoContent());
    mockMvc.perform(get("/api/categories/hiking")).andExpect(status().isNotFound());
  }

  @Test
  void postsAreDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/posts/{id}'].put").exists())
        .andExpect(jsonPath("$.paths['/api/posts/{id}'].delete").exists());
  }

  private ResultActions edit(Cookie who, Long postId, String text) throws Exception {
    return send(
        mockMvc,
        HttpMethod.PUT,
        who,
        "/api/posts/" + postId,
        "{\"content\":" + document(text) + "}");
  }

  private ResultActions deletePost(Cookie who, Long postId) throws Exception {
    return mockMvc.perform(delete("/api/posts/" + postId).cookie(who).with(Csrf.token(mockMvc)));
  }

  private ResultActions deleteCategory(Cookie admin) throws Exception {
    return mockMvc.perform(
        delete("/api/admin/categories/" + categoryId).cookie(admin).with(Csrf.token(mockMvc)));
  }

  private Cookie session(String username, UserRole role, boolean verified) throws Exception {
    return Forum.userSession(mockMvc, users, passwordEncoder, username, role, verified);
  }
}
