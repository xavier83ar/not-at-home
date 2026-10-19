package com.notathome.users;

import static com.notathome.support.Forum.document;
import static com.notathome.support.Forum.reply;
import static com.notathome.support.Forum.send;
import static com.notathome.support.Forum.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.notathome.categories.AdminCategoryService;
import com.notathome.support.Csrf;
import com.notathome.support.Forum;
import com.notathome.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ProfileTests {

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
    alice = session("alice", true);
    bob = session("bob", true);
  }

  @AfterEach
  void tearDown() {
    Forum.reset(jdbc);
  }

  // --- the public profile (FR-8.1)

  @Test
  void aProfileShowsTheUserTheirActivityAndNoEmail() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Alice's trail", "Opening of alice");
    Long bobsTopic = topic(mockMvc, bob, categoryId, "Bob's question");
    Long replyId = reply(mockMvc, alice, bobsTopic, "A reply by alice");

    mockMvc
        .perform(get("/api/users/alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.displayName").value("alice"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.joinedAt").isNotEmpty())
        .andExpect(jsonPath("$.avatarUrl").doesNotExist())
        .andExpect(jsonPath("$.profileContent").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.postCount").value(2))
        .andExpect(jsonPath("$.recentTopics.length()").value(1))
        .andExpect(jsonPath("$.recentTopics[0].id").value(topicId))
        .andExpect(jsonPath("$.recentTopics[0].title").value("Alice's trail"))
        .andExpect(jsonPath("$.recentPosts.length()").value(2))
        .andExpect(jsonPath("$.recentPosts[0].postId").value(replyId))
        .andExpect(jsonPath("$.recentPosts[0].excerpt").value("A reply by alice"))
        .andExpect(jsonPath("$.recentPosts[0].topicTitle").value("Bob's question"))
        .andExpect(jsonPath("$.recentPosts[0].category.slug").value("hiking"))
        .andExpect(jsonPath("$.recentPosts[1].excerpt").value("Opening of alice"));
  }

  @Test
  void aProfileIsFoundByUsernameInAnyCase() throws Exception {
    mockMvc
        .perform(get("/api/users/ALICE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));
  }

  @Test
  void anUnknownOrDeletedUserHasNoProfile() throws Exception {
    mockMvc.perform(get("/api/users/nobody")).andExpect(status().isNotFound());

    jdbc.update("update users set deleted_at = now() where username = 'alice'");
    mockMvc.perform(get("/api/users/alice")).andExpect(status().isNotFound());
  }

  @Test
  void aBannedUsersProfileStaysVisible() throws Exception {
    jdbc.update("update users set status = 'BANNED' where username = 'alice'");

    mockMvc.perform(get("/api/users/alice")).andExpect(status().isOk());
  }

  @Test
  void recentActivityIsCappedAtTheNewest() throws Exception {
    for (int i = 1; i <= ProfileService.RECENT_TOPICS + 2; i++) {
      topic(mockMvc, alice, categoryId, "Topic " + i);
    }

    mockMvc
        .perform(get("/api/users/alice"))
        .andExpect(jsonPath("$.postCount").value(ProfileService.RECENT_TOPICS + 2))
        .andExpect(jsonPath("$.recentTopics.length()").value(ProfileService.RECENT_TOPICS))
        .andExpect(jsonPath("$.recentTopics[0].title").value("Topic 7"))
        .andExpect(jsonPath("$.recentPosts.length()").value(ProfileService.RECENT_TOPICS + 2));
  }

  @Test
  void removedContentIsLeftOutOfTheProfile() throws Exception {
    // A reply that was deleted.
    Long bobsTopic = topic(mockMvc, bob, categoryId, "Bob's question");
    Long gone = reply(mockMvc, alice, bobsTopic, "Deleted reply");
    reply(mockMvc, alice, bobsTopic, "Kept reply");
    send(mockMvc, HttpMethod.DELETE, alice, "/api/posts/" + gone, "").andExpect(status().isOk());
    // A topic deleted whole (no replies).
    Long lonely = topic(mockMvc, alice, categoryId, "Lonely topic");
    send(mockMvc, HttpMethod.DELETE, alice, "/api/posts/" + openingPostId(lonely), "")
        .andExpect(status().isOk());
    // A topic whose opening post was deleted but that has replies: the topic stays, the title
    // is part of what was removed.
    Long answered = topic(mockMvc, alice, categoryId, "Answered topic");
    reply(mockMvc, bob, answered, "Bob answers");
    send(mockMvc, HttpMethod.DELETE, alice, "/api/posts/" + openingPostId(answered), "")
        .andExpect(status().isOk());
    // A topic removed by someone else (an admin, later) takes its posts with it.
    Long removedByAdmin = topic(mockMvc, alice, categoryId, "Removed by admin");
    jdbc.update("update posts set deleted_at = now() where topic_id = ?", removedByAdmin);
    jdbc.update("update topics set deleted_at = now() where id = ?", removedByAdmin);

    mockMvc
        .perform(get("/api/users/alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postCount").value(1))
        .andExpect(jsonPath("$.recentTopics.length()").value(0))
        .andExpect(jsonPath("$.recentPosts.length()").value(1))
        .andExpect(jsonPath("$.recentPosts[0].excerpt").value("Kept reply"));
  }

  @Test
  void aDeletedAuthorIsFlaggedSoNobodyLinksToTheirProfile() throws Exception {
    Long topicId = topic(mockMvc, alice, categoryId, "Alice's trail");
    reply(mockMvc, bob, topicId, "Bob replies");
    jdbc.update("update users set deleted_at = now() where username = 'alice'");

    mockMvc
        .perform(get("/api/topics/" + topicId))
        .andExpect(jsonPath("$.posts.items[0].author.displayName").value("Deleted user"))
        .andExpect(jsonPath("$.posts.items[0].author.deleted").value(true))
        .andExpect(jsonPath("$.posts.items[1].author.deleted").value(false));
  }

  // --- editing the profile (FR-8.2)

  @Test
  void aUserEditsTheirDisplayNameAndProfileText() throws Exception {
    send(
            mockMvc,
            HttpMethod.PUT,
            alice,
            "/api/me/profile",
            "{\"displayName\":\"  Alice A. \",\"profileContent\":"
                + document("I walk <b>a lot</b><script>alert(1)</script>")
                + "}")
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/users/alice"))
        .andExpect(jsonPath("$.displayName").value("Alice A."))
        .andExpect(jsonPath("$.profileContent.blocks[0].type").value("paragraph"))
        .andExpect(jsonPath("$.profileContent.blocks[0].data.text").value("I walk <b>a lot</b>"));
    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.displayName").value("Alice A."));
  }

  @Test
  void leavingTheProfileTextOutClearsIt() throws Exception {
    send(
            mockMvc,
            HttpMethod.PUT,
            alice,
            "/api/me/profile",
            "{\"displayName\":\"Alice\",\"profileContent\":" + document("About me") + "}")
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/users/alice")).andExpect(jsonPath("$.profileContent").exists());

    send(mockMvc, HttpMethod.PUT, alice, "/api/me/profile", "{\"displayName\":\"Alice\"}")
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/users/alice")).andExpect(jsonPath("$.profileContent").doesNotExist());

    send(
            mockMvc,
            HttpMethod.PUT,
            alice,
            "/api/me/profile",
            "{\"displayName\":\"Alice\",\"profileContent\":" + document("About me") + "}")
        .andExpect(status().isNoContent());
    send(
            mockMvc,
            HttpMethod.PUT,
            alice,
            "/api/me/profile",
            "{\"displayName\":\"Alice\",\"profileContent\":{\"blocks\":[]}}")
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "select profile_content from users where username = 'alice'", String.class))
        .isNull();
  }

  @Test
  void profileEditsAreValidated() throws Exception {
    send(mockMvc, HttpMethod.PUT, alice, "/api/me/profile", "{\"displayName\":\"   \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.displayName").isNotEmpty());
    send(
            mockMvc,
            HttpMethod.PUT,
            alice,
            "/api/me/profile",
            "{\"displayName\":\"" + "x".repeat(51) + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.displayName[0]").value("must be at most 50 characters"));
    send(
            mockMvc,
            HttpMethod.PUT,
            alice,
            "/api/me/profile",
            "{\"displayName\":\"Alice\",\"profileContent\":{\"blocks\":[{\"type\":\"embed\","
                + "\"data\":{}}]}}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors.profileContent[0]")
                .value(org.hamcrest.Matchers.containsString("not allowed")));
    assertThat(
            jdbc.queryForObject(
                "select display_name from users where username = 'alice'", String.class))
        .isEqualTo("alice");
  }

  @Test
  void profileEditsNeedALoginAndTheCsrfToken() throws Exception {
    mockMvc
        .perform(
            put("/api/me/profile")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\"}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/me/profile")
                .cookie(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"x\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnverifiedUserCanEditTheirProfile() throws Exception {
    Cookie carol = session("carol", false);

    send(mockMvc, HttpMethod.PUT, carol, "/api/me/profile", "{\"displayName\":\"Carol C.\"}")
        .andExpect(status().isNoContent());
  }

  // --- the avatar (FR-8.2)

  @Test
  void aUserSetsAndRemovesTheirAvatar() throws Exception {
    long uploadId = upload(alice);
    String url =
        jdbc.queryForObject(
            "select '/api/uploads/' || storage_key from uploads where id = ?",
            String.class,
            uploadId);

    send(mockMvc, HttpMethod.PUT, alice, "/api/me/avatar", "{\"uploadId\":" + uploadId + "}")
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/users/alice")).andExpect(jsonPath("$.avatarUrl").value(url));
    mockMvc.perform(get("/api/me").cookie(alice)).andExpect(jsonPath("$.avatarUrl").value(url));

    send(mockMvc, HttpMethod.DELETE, alice, "/api/me/avatar", "").andExpect(status().isNoContent());
    mockMvc.perform(get("/api/users/alice")).andExpect(jsonPath("$.avatarUrl").doesNotExist());
  }

  @Test
  void anAvatarMustBeTheUsersOwnUpload() throws Exception {
    long bobsUpload = upload(bob);

    send(mockMvc, HttpMethod.PUT, alice, "/api/me/avatar", "{\"uploadId\":" + bobsUpload + "}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.uploadId[0]").value("upload not found"));
    send(mockMvc, HttpMethod.PUT, alice, "/api/me/avatar", "{\"uploadId\":999999}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.uploadId[0]").value("upload not found"));
    send(mockMvc, HttpMethod.PUT, alice, "/api/me/avatar", "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.uploadId").isNotEmpty());
    mockMvc.perform(get("/api/users/alice")).andExpect(jsonPath("$.avatarUrl").doesNotExist());
  }

  // --- helpers

  private Cookie session(String username, boolean verified) throws Exception {
    return Forum.userSession(mockMvc, users, passwordEncoder, username, UserRole.USER, verified);
  }

  private Long openingPostId(Long topicId) {
    return jdbc.queryForObject("select min(id) from posts where topic_id = ?", Long.class, topicId);
  }

  /** Uploads a small JPEG as {@code who} and returns the upload's id. */
  private long upload(Cookie who) throws Exception {
    byte[] jpeg = new byte[64];
    Arrays.fill(jpeg, (byte) 1);
    jpeg[0] = (byte) 0xFF;
    jpeg[1] = (byte) 0xD8;
    jpeg[2] = (byte) 0xFF;
    String body =
        mockMvc
            .perform(
                multipart("/api/uploads")
                    .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", jpeg))
                    .cookie(who)
                    .with(Csrf.token(mockMvc)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }
}
