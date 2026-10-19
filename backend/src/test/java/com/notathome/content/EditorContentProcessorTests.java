package com.notathome.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notathome.common.FieldErrorsException;
import com.notathome.support.IntegrationTest;
import com.notathome.uploads.Upload;
import com.notathome.uploads.UploadRepository;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class EditorContentProcessorTests {

  @Autowired EditorContentProcessor processor;
  @Autowired JsonMapper mapper;
  @Autowired UploadRepository uploads;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private String imageUrl;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from uploads");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
    User owner = users.save(new User("alice", "alice@example.com", "Alice", "x"));
    Upload upload = uploads.save(new Upload(owner.getId(), "abc_DEF-123.png", "image/png", 10));
    imageUrl = "/api/uploads/" + upload.getStorageKey();
  }

  @Test
  void keepsEveryAllowedBlockAndBuildsTheSearchText() {
    ProcessedContent result =
        process(
            """
            {"time": 1, "version": "2.31.0", "blocks": [
              {"id": "a", "type": "header", "data": {"text": "Best trails", "level": 3}},
              {"type": "paragraph", "data": {"text": "Walk <b>far</b>&nbsp;and <a href=\\"https://example.com\\">read</a>"}},
              {"type": "quote", "data": {"text": "Nice one", "caption": "<a href=\\"/topics/1-x?post=2\\">bob</a>", "alignment": "left"}},
              {"type": "list", "data": {"style": "ordered", "meta": {"start": 2, "counterType": "numeric"}, "items": [
                {"content": "first", "meta": {}, "items": [{"content": "nested", "meta": {}, "items": []}]},
                {"content": "second", "meta": {}, "items": []}]}},
              {"type": "code", "data": {"code": "if (a < b) { run(); }"}},
              {"type": "delimiter", "data": {}},
              {"type": "image", "data": {"file": {"url": "%s"}, "caption": "A view", "withBorder": true}},
              {"type": "link", "data": {"link": "https://example.com/trail", "meta": {"title": "Trail map", "image": {"url": "http://x"}}}}
            ]}
            """
                .formatted(imageUrl));

    JsonNode blocks = parse(result).path("blocks");
    assertThat(blocks).hasSize(8);
    assertThat(blocks.get(0).toString())
        .isEqualTo("{\"type\":\"header\",\"data\":{\"text\":\"Best trails\",\"level\":3}}");
    assertThat(blocks.get(1).path("data").path("text").asString())
        .contains("<b>far</b>")
        .contains("href=\"https://example.com\"");
    assertThat(blocks.get(2).path("data").path("caption").asString())
        .contains("href=\"/topics/1-x?post&#61;2\"");
    assertThat(blocks.get(2).path("data").has("alignment")).isFalse();
    assertThat(blocks.get(3).path("data").path("meta").path("start").intValue()).isEqualTo(2);
    assertThat(blocks.get(3).path("data").path("items").get(0).path("items")).hasSize(1);
    assertThat(blocks.get(4).path("data").path("code").asString())
        .isEqualTo("if (a < b) { run(); }");
    assertThat(blocks.get(6).path("data").path("file").path("url").asString()).isEqualTo(imageUrl);
    assertThat(blocks.get(6).path("data").has("withBorder")).isFalse();
    assertThat(blocks.get(7).path("data").path("title").asString()).isEqualTo("Trail map");
    assertThat(blocks.get(7).path("data").has("meta")).isFalse();

    assertThat(result.searchText().split("\n"))
        .containsExactly(
            "Best trails",
            "Walk far and read",
            "Nice one",
            "bob",
            "first",
            "nested",
            "second",
            "if (a < b) { run(); }",
            "A view".strip(),
            "Trail map",
            "https://example.com/trail");
  }

  @Test
  void sanitizesInlineHtml() {
    ProcessedContent result =
        process(
            """
            {"blocks": [{"type": "paragraph", "data": {"text": "<script>alert(1)</script>hi <a href=\\"javascript:alert(1)\\" onclick=\\"x()\\">link</a><img src=x onerror=y>"}}]}
            """);

    String text = parse(result).path("blocks").get(0).path("data").path("text").asString();
    assertThat(text).isEqualTo("hi link");
    assertThat(result.json()).doesNotContain("script").doesNotContain("javascript");
  }

  @Test
  void keepsCodeAsTextSoNothingInItActsAsMarkup() {
    ProcessedContent result =
        process(
            "{\"blocks\": [{\"type\": \"code\", \"data\": {\"code\": \"<script>x()</script>\"}}]}");

    assertThat(parse(result).path("blocks").get(0).path("data").path("code").asString())
        .isEqualTo("<script>x()</script>");
    assertThat(result.searchText()).isEqualTo("<script>x()</script>");
  }

  @Test
  void checklistsKeepTheirCheckedState() {
    ProcessedContent result =
        process(
            """
            {"blocks": [{"type": "list", "data": {"style": "checklist", "items": [
              {"content": "tent", "meta": {"checked": true}, "items": []},
              {"content": "stove", "meta": {"checked": false}, "items": []}]}}]}
            """);

    JsonNode items = parse(result).path("blocks").get(0).path("data").path("items");
    assertThat(items.get(0).path("meta").path("checked").booleanValue()).isTrue();
    assertThat(items.get(1).path("meta").path("checked").booleanValue()).isFalse();
  }

  @Test
  void dropsEmptyBlocksButKeepsTheRest() {
    ProcessedContent result =
        process(
            """
            {"blocks": [
              {"type": "paragraph", "data": {"text": ""}},
              {"type": "paragraph", "data": {"text": "<b></b>&nbsp;"}},
              {"type": "paragraph", "data": {"text": "real"}},
              {"type": "list", "data": {"style": "unordered", "items": []}}]}
            """);

    assertThat(parse(result).path("blocks")).hasSize(1);
    assertThat(result.searchText()).isEqualTo("real");
  }

  @Test
  void rejectsContentWithNothingToShow() {
    assertInvalid("{\"blocks\": []}", "can't be empty");
    assertInvalid(
        "{\"blocks\": [{\"type\": \"paragraph\", \"data\": {\"text\": \" \"}}]}", "can't be empty");
    assertInvalid("{\"blocks\": [{\"type\": \"delimiter\", \"data\": {}}]}", "can't be empty");
  }

  @Test
  void anOptionalDocumentMayBeLeftEmpty() {
    assertThat(processor.processOptional(null, "profileContent")).isNull();
    assertThat(processor.processOptional(mapper.readTree("null"), "profileContent")).isNull();
    assertThat(processor.processOptional(mapper.readTree("{\"blocks\": []}"), "profileContent"))
        .isNull();
    assertThat(
            processor.processOptional(
                mapper.readTree("{\"blocks\": [{\"type\": \"delimiter\", \"data\": {}}]}"),
                "profileContent"))
        .isNull();
    assertThat(
            processor.processOptional(
                mapper.readTree(
                    "{\"blocks\": [{\"type\": \"paragraph\", \"data\": {\"text\": \"Hi\"}}]}"),
                "profileContent"))
        .isNotNull();
  }

  @Test
  void anOptionalDocumentIsStillCheckedAndReportsOnItsOwnField() {
    assertThatThrownBy(
            () ->
                processor.processOptional(
                    mapper.readTree("{\"blocks\": [{\"type\": \"embed\", \"data\": {}}]}"),
                    "profileContent"))
        .isInstanceOfSatisfying(
            FieldErrorsException.class,
            e ->
                assertThat(e.getErrors().get("profileContent"))
                    .singleElement()
                    .asString()
                    .contains("'embed' blocks are not allowed"));
  }

  @Test
  void rejectsMalformedDocuments() {
    assertInvalid("[]", "must be an Editor.js document");
    assertInvalid("{\"blocks\": \"x\"}", "must be an Editor.js document");
    assertInvalid("{\"blocks\": [\"x\"]}", "block 1 is not valid");
    assertInvalid("{\"blocks\": [{\"type\": \"paragraph\"}]}", "block 1 has no data");
    assertInvalid(
        "{\"blocks\": [{\"type\": \"paragraph\", \"data\": {\"text\": 5}}]}", "must be text");
  }

  @Test
  void rejectsBlockTypesThatAreNotAllowed() {
    for (String type : new String[] {"table", "raw", "embed", "attaches", "warning", "checklist"}) {
      assertInvalid(
          "{\"blocks\": [{\"type\": \"" + type + "\", \"data\": {\"html\": \"x\"}}]}",
          "'" + type + "' blocks are not allowed");
    }
  }

  @Test
  void rejectsBadHeaderLevelsAndListShapes() {
    assertInvalid(
        "{\"blocks\": [{\"type\": \"header\", \"data\": {\"text\": \"x\", \"level\": 9}}]}",
        "header level");
    assertInvalid(
        "{\"blocks\": [{\"type\": \"list\", \"data\": {\"style\": \"zigzag\", \"items\": []}}]}",
        "unknown list style");
    assertInvalid(
        "{\"blocks\": [{\"type\": \"list\", \"data\": {\"style\": \"ordered\", \"items\": [\"plain\"]}}]}",
        "must be objects");
  }

  @Test
  void rejectsListsNestedTooDeep() {
    String item = "{\"content\": \"x\", \"meta\": {}, \"items\": []}";
    for (int i = 0; i < EditorContentProcessor.MAX_LIST_DEPTH; i++) {
      item = "{\"content\": \"x\", \"meta\": {}, \"items\": [" + item + "]}";
    }
    assertInvalid(
        "{\"blocks\": [{\"type\": \"list\", \"data\": {\"style\": \"unordered\", \"items\": ["
            + item
            + "]}}]}",
        "nested at most");
  }

  @Test
  void rejectsImagesThatAreNotOurUploads() {
    for (String url :
        new String[] {
          "https://evil.example/x.png",
          "//evil.example/x.png",
          "/api/uploads/unknown.png",
          "/api/uploads/../secret.png",
          "javascript:alert(1)",
          "/api/uploads/abc_DEF-123.png?x=1"
        }) {
      assertInvalid(
          "{\"blocks\": [{\"type\": \"image\", \"data\": {\"file\": {\"url\": \"" + url + "\"}}}]}",
          "images must be uploaded to this site");
    }
    assertInvalid("{\"blocks\": [{\"type\": \"image\", \"data\": {}}]}", "images must be uploaded");
  }

  @Test
  void rejectsLinksThatAreNotWebAddresses() {
    for (String url :
        new String[] {"javascript:alert(1)", "/relative", "ftp://x.example", "not a url"}) {
      assertInvalid(
          "{\"blocks\": [{\"type\": \"link\", \"data\": {\"link\": \"" + url + "\"}}]}",
          "link must be an http or https address");
    }
  }

  @Test
  void rejectsTooManyBlocksAndTooMuchContent() {
    String block = "{\"type\": \"paragraph\", \"data\": {\"text\": \"x\"}}";
    String many =
        String.join(
            ",", java.util.Collections.nCopies(EditorContentProcessor.MAX_BLOCKS + 1, block));
    assertInvalid("{\"blocks\": [" + many + "]}", "at most");

    String huge = "x".repeat(EditorContentProcessor.MAX_DOCUMENT_CHARS);
    assertInvalid(
        "{\"blocks\": [{\"type\": \"paragraph\", \"data\": {\"text\": \"" + huge + "\"}}]}",
        "too long");
  }

  private ProcessedContent process(String json) {
    return processor.process(mapper.readTree(json));
  }

  private JsonNode parse(ProcessedContent content) {
    return mapper.readTree(content.json());
  }

  private void assertInvalid(String json, String message) {
    assertThatThrownBy(() -> process(json))
        .isInstanceOfSatisfying(
            FieldErrorsException.class,
            e -> assertThat(e.getErrors().get("content")).anyMatch(m -> m.contains(message)));
  }
}
