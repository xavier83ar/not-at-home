package com.notathome.search;

import static com.notathome.support.Forum.reply;
import static com.notathome.support.Forum.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.notathome.categories.AdminCategoryService;
import com.notathome.support.Forum;
import com.notathome.support.IntegrationTest;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

@IntegrationTest
class SearchTests {

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired AdminCategoryService categories;
  @Autowired JdbcTemplate jdbc;

  private Long hiking;
  private Long climbing;
  private Cookie alice;
  private Cookie bob;

  @BeforeEach
  void setUp() throws Exception {
    Forum.reset(jdbc);
    hiking = categories.create("Hiking", "Trails", null).getId();
    climbing = categories.create("Climbing", "Rock", null).getId();
    alice = session("alice");
    bob = session("bob");
  }

  @AfterEach
  void tearDown() {
    Forum.reset(jdbc);
  }

  // --- matching and snippets (FR-6.1, FR-6.2)

  @Test
  void aWordFromAReplyFindsItWithAHighlightedSnippetAndEverythingTheResultNeeds() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Trails in Patagonia", "Opening words");
    Long reply = reply(mockMvc, bob, topic, "Bring a sturdy tent for the wind");

    search("q=sturdy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.items[0].postId").value(reply.intValue()))
        .andExpect(jsonPath("$.items[0].topicId").value(topic.intValue()))
        .andExpect(jsonPath("$.items[0].topicTitle").value("Trails in Patagonia"))
        .andExpect(jsonPath("$.items[0].topicSlug").value("trails-in-patagonia"))
        .andExpect(jsonPath("$.items[0].category.slug").value("hiking"))
        .andExpect(jsonPath("$.items[0].author.username").value("bob"))
        .andExpect(jsonPath("$.items[0].author.email").doesNotExist())
        .andExpect(jsonPath("$.items[0].createdAt").exists())
        .andExpect(
            jsonPath("$.items[0].snippet").value("Bring a <mark>sturdy</mark> tent for the wind"));
  }

  @Test
  void wordsMatchByTheirEnglishRoot() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Gear");
    Long reply = reply(mockMvc, bob, topic, "We were hiking all day");

    search("q=hikes")
        .andExpect(jsonPath("$.items[0].postId").value(reply.intValue()))
        .andExpect(jsonPath("$.items[0].snippet").value("We were <mark>hiking</mark> all day"));
  }

  @Test
  void aTitleMatchLinksToTheOpeningPostOnlyOnce() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Glacier crossing tips", "Some opening words");
    reply(mockMvc, bob, topic, "Thanks for sharing");
    Long opening = openingPost(topic);

    search("q=glacier")
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].postId").value(opening.intValue()))
        .andExpect(jsonPath("$.items[0].topicTitle").value("Glacier crossing tips"))
        // No match in the text: the snippet is its beginning, with nothing marked.
        .andExpect(jsonPath("$.items[0].snippet").value("Some opening words"));
  }

  @Test
  void aTitleMatchWhoseOpeningPostWasRemovedLinksToTheFirstVisiblePost() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Glacier crossing tips", "Secret opening words");
    Long reply = reply(mockMvc, bob, topic, "Thanks for sharing");
    jdbc.update("update posts set deleted_at = now() where id = ?", openingPost(topic));

    search("q=glacier")
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].postId").value(reply.intValue()))
        .andExpect(jsonPath("$.items[0].snippet").value("Thanks for sharing"));
  }

  @Test
  void aPostThatMatchesByTitleAndTextIsListedOnce() throws Exception {
    topic(mockMvc, alice, hiking, "Glacier tips", "The glacier was easy");

    search("q=glacier").andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void theSnippetIsEscapedSoOnlyMarkTagsReachTheBrowser() throws Exception {
    // Editor.js writes a typed "<" as an entity, and the plain text kept for search has it decoded.
    topic(mockMvc, alice, hiking, "Maths", "1 &lt; 2 &amp; 3 &gt; 2 so &quot;boots&quot; win");
    topic(
        mockMvc,
        alice,
        hiking,
        "Tags",
        "&lt;script&gt;alert(1)&lt;/script&gt; boots &lt;b&gt;bold");

    search("q=win")
        .andExpect(
            jsonPath("$.items[0].snippet")
                .value("1 &lt; 2 &amp; 3 &gt; 2 so &quot;boots&quot; <mark>win</mark>"));
    // However PostgreSQL cuts a text that looks like HTML, no result has a tag but <mark>.
    String found = body(search("q=boots&category=hiking"));
    List<String> snippets = JsonPath.read(found, "$.items[*].snippet");
    assertThat(snippets).hasSize(2).allSatisfy(this::hasNoTagButMark);
    assertThat(snippets).allMatch(snippet -> snippet.contains("<mark>boots</mark>"));
  }

  private void hasNoTagButMark(String snippet) {
    assertThat(snippet.replace("<mark>", "").replace("</mark>", "")).doesNotContain("<");
  }

  @Test
  void quotesAndOperatorsInTheQueryAreJustText() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Camping", "Tents and stoves");
    reply(mockMvc, bob, topic, "Stoves are heavy");

    // None of these may fail; they only match (or not) like any other words.
    search("q=%22unbalanced").andExpect(status().isOk());
    search("q=tents%20%26%20stoves%20!!!").andExpect(status().isOk());
    search("q=%3A%3A%3A%3A")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
    search("q=stoves%20-heavy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
    // Only words too common to search for.
    search("q=the%20and%20for")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  // --- filters (FR-6.3)

  @Test
  void filtersByCategory() throws Exception {
    topic(mockMvc, alice, hiking, "Ropes", "Ropes for the trail");
    Long climbingTopic = topic(mockMvc, alice, climbing, "Ropes", "Ropes for the wall");

    search("q=ropes").andExpect(jsonPath("$.totalItems").value(2));
    search("q=ropes&category=climbing")
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].topicId").value(climbingTopic.intValue()));
    search("q=ropes&category=nowhere").andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void filtersByAuthorWhateverTheCase() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Maps", "Paper maps");
    Long reply = reply(mockMvc, bob, topic, "Digital maps");

    search("q=maps&author=BOB")
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].postId").value(reply.intValue()));
    search("q=maps&author=nobody")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void filtersByDateRangeIncludingBothDays() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Weather", "Weather report");
    Long early = reply(mockMvc, bob, topic, "Weather in spring");
    Long late = reply(mockMvc, bob, topic, "Weather in summer");
    Long last = reply(mockMvc, bob, topic, "Weather at night");
    jdbc.update("update posts set created_at = '2026-03-01T00:00:00Z' where id = ?", early);
    jdbc.update("update posts set created_at = '2026-07-15T12:00:00Z' where id = ?", late);
    jdbc.update("update posts set created_at = '2026-07-15T23:59:59Z' where id = ?", last);
    jdbc.update(
        "update posts set created_at = '2026-01-01T00:00:00Z' where id = ?", openingPost(topic));

    search("q=weather&from=2026-03-01&to=2026-03-01&sort=date")
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].postId").value(early.intValue()));
    search("q=weather&from=2026-07-15&to=2026-07-15&sort=date")
        .andExpect(jsonPath("$.totalItems").value(2));
    search("q=weather&from=2026-03-02").andExpect(jsonPath("$.totalItems").value(2));
    search("q=weather&to=2026-03-01").andExpect(jsonPath("$.totalItems").value(2));
    search("q=weather&from=2026-08-01").andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void blankFiltersFromASearchFormAreIgnored() throws Exception {
    topic(mockMvc, alice, hiking, "Ropes", "Ropes for the trail");

    search("q=ropes&category=&author=&from=&to=&sort=")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  // --- order and pages (FR-6.4)

  @Test
  void relevanceIsTheDefaultAndPutsTheBestMatchFirst() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Chat", "Nothing about it here");
    Long weak = reply(mockMvc, bob, topic, "Water was cold, and the path was long and steep");
    Long strong = reply(mockMvc, bob, topic, "Water water water");

    search("q=water")
        .andExpect(jsonPath("$.items[0].postId").value(strong.intValue()))
        .andExpect(jsonPath("$.items[1].postId").value(weak.intValue()));
  }

  @Test
  void aTitleMatchOutranksATextMatch() throws Exception {
    Long mentions = topic(mockMvc, alice, hiking, "Chat", "Crampons are great");
    Long about = topic(mockMvc, alice, hiking, "Crampons", "Opening words");

    search("q=crampons")
        .andExpect(jsonPath("$.items[0].topicId").value(about.intValue()))
        .andExpect(jsonPath("$.items[1].topicId").value(mentions.intValue()));
  }

  @Test
  void sortingByDateListsTheNewestFirst() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Chat", "Nothing here");
    Long old = reply(mockMvc, bob, topic, "Water water water");
    Long recent = reply(mockMvc, bob, topic, "Some water");
    jdbc.update("update posts set created_at = now() - interval '5 days' where id = ?", old);

    search("q=water&sort=date")
        .andExpect(jsonPath("$.items[0].postId").value(recent.intValue()))
        .andExpect(jsonPath("$.items[1].postId").value(old.intValue()));
    search("q=water&sort=relevance").andExpect(jsonPath("$.items[0].postId").value(old.intValue()));
  }

  @Test
  void resultsArePagedTenAtATimeWithoutRepeatsOrGaps() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Chat", "Nothing here");
    for (int i = 0; i < 12; i++) {
      reply(mockMvc, bob, topic, "Tent number " + i);
    }

    String first = body(search("q=tent&page=1"));
    String second = body(search("q=tent&page=2"));
    search("q=tent&page=1")
        .andExpect(jsonPath("$.items.length()").value(10))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.totalItems").value(12));
    search("q=tent&page=2")
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.items.length()").value(2));
    assertThat(second).doesNotContain(ids(first));
    search("q=tent&page=3").andExpect(status().isNotFound());
  }

  @Test
  void noMatchIsAnEmptyFirstPageNotAnError() throws Exception {
    search("q=nothing")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.totalItems").value(0))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  // --- what never appears (FR-6.5)

  @Test
  void removedPostsAndTopicsNeverAppear() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Lantern", "A lantern for camping");
    Long removedReply = reply(mockMvc, bob, topic, "My lantern broke");
    Long removedTopic = topic(mockMvc, alice, hiking, "Lantern sale", "Lantern for sale");
    jdbc.update("update posts set deleted_at = now() where id = ?", removedReply);
    jdbc.update("update posts set deleted_at = now() where topic_id = ?", removedTopic);
    jdbc.update("update topics set deleted_at = now() where id = ?", removedTopic);

    search("q=lantern")
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].topicId").value(topic.intValue()));
  }

  @Test
  void aTopicWithNoVisiblePostLeavesNothingToFindByItsTitle() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Lantern", "Opening words");
    jdbc.update("update posts set deleted_at = now() where topic_id = ?", topic);

    search("q=lantern").andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void aRemovedPostsTextIsNotFoundEvenForAnAdmin() throws Exception {
    Cookie admin = Forum.userSession(mockMvc, users, passwordEncoder, "root", UserRole.ADMIN, true);
    Long topic = topic(mockMvc, alice, hiking, "Chat", "Opening words");
    Long removed = reply(mockMvc, bob, topic, "Secret spoiler");
    jdbc.update("update posts set deleted_at = now() where id = ?", removed);

    mockMvc
        .perform(get("/api/search?q=spoiler").cookie(admin))
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void postsOfDeletedAccountsAreLeftOutButBannedOnesStay() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Chat", "Opening words");
    reply(mockMvc, bob, topic, "Compass advice from bob");
    Cookie carol = session("carol");
    Cookie dave = session("dave");
    reply(mockMvc, carol, topic, "Compass advice from carol");
    reply(mockMvc, dave, topic, "Compass advice from dave");
    jdbc.update("update users set status = 'BANNED' where username = 'carol'");
    jdbc.update("update users set deleted_at = now() where username = 'dave'");

    search("q=compass&sort=date")
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[0].author.username").value("carol"))
        .andExpect(jsonPath("$.items[1].author.username").value("bob"));
  }

  @Test
  void anEditedPostIsFoundByItsNewTextOnly() throws Exception {
    Long topic = topic(mockMvc, alice, hiking, "Chat", "Opening words");
    Long reply = reply(mockMvc, bob, topic, "Old boots");
    Forum.send(
            mockMvc,
            HttpMethod.PUT,
            bob,
            "/api/posts/" + reply,
            "{\"content\":" + Forum.document("New sandals") + "}")
        .andExpect(status().isOk());

    search("q=boots").andExpect(jsonPath("$.totalItems").value(0));
    search("q=sandals").andExpect(jsonPath("$.totalItems").value(1));
  }

  // --- the request

  @Test
  void keywordsAreRequiredAndLimitedInLength() throws Exception {
    mockMvc
        .perform(get("/api/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.q[0]").value("must be between 3 and 100 characters"));
    search("q=").andExpect(status().isBadRequest());
    search("q=%20%20ab%20%20")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.q").exists());
    search("q=abc").andExpect(status().isOk());
    search("q=" + "a".repeat(100)).andExpect(status().isOk());
    search("q=" + "a".repeat(101)).andExpect(status().isBadRequest());
  }

  @Test
  void badFiltersAreReportedByField() throws Exception {
    search("q=abc&from=yesterday")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.from[0]").value("must be a date like 2026-01-31"));
    search("q=abc&to=2026-02-30").andExpect(jsonPath("$.errors.to").exists());
    search("q=abc&from=2026-05-02&to=2026-05-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.to[0]").value("must not be before the start date"));
    search("q=abc&sort=best")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sort[0]").value("must be relevance or date"));
    search("q=ab&sort=best")
        .andExpect(jsonPath("$.errors.q").exists())
        .andExpect(jsonPath("$.errors.sort").exists());
  }

  @Test
  void guestsCanSearch() throws Exception {
    topic(mockMvc, alice, hiking, "Chat", "Binoculars are handy");

    // No session cookie at all.
    mockMvc
        .perform(get("/api/search?q=binoculars"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  // --- helpers

  private ResultActions search(String query) throws Exception {
    // Built as an already-encoded URI: a string would be treated as a template and encoded again.
    return mockMvc.perform(
        get(UriComponentsBuilder.fromUriString("/api/search?" + query).build(true).toUri()));
  }

  private String body(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  /** The post ids in a response, as {@code "postId":n} so they can't match other numbers. */
  private static String[] ids(String json) {
    return JsonPath.<List<Number>>read(json, "$.items[*].postId").stream()
        .map(id -> "\"postId\":" + id)
        .toArray(String[]::new);
  }

  private Long openingPost(Long topicId) {
    return jdbc.queryForObject("select min(id) from posts where topic_id = ?", Long.class, topicId);
  }

  private Cookie session(String username) throws Exception {
    return Forum.userSession(
        mockMvc, users, passwordEncoder, username, UserRole.USER, /* verified= */ true);
  }
}
