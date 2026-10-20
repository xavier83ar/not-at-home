package com.notathome.content;

import com.notathome.common.FieldErrorsException;
import com.notathome.uploads.UploadRepository;
import com.notathome.uploads.UploadUrl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns the Editor.js document a client sends into what is stored (FR-4.4): only the allowed block
 * types, only the fields we use, inline HTML sanitized, empty blocks dropped. The result is built
 * from scratch rather than cleaned in place, so anything not listed here cannot get through. The
 * browser still sanitizes when rendering; this is the half that does not depend on it.
 *
 * <p>Failures are field errors on {@code content}, like any other invalid request.
 */
@Service
public class EditorContentProcessor {

  static final int MAX_DOCUMENT_CHARS = 100_000;
  static final int MAX_BLOCKS = 200;
  static final int MAX_LIST_ITEMS = 500;
  static final int MAX_LIST_DEPTH = 5;
  private static final int MAX_LINK_TITLE = 300;

  private static final Set<String> LIST_STYLES = Set.of("ordered", "unordered", "checklist");
  private static final Set<String> COUNTER_TYPES =
      Set.of("numeric", "lower-roman", "upper-roman", "lower-alpha", "upper-alpha");

  /** The image tool's on/off options: a border, a background, and stretching to the full width. */
  private static final List<String> IMAGE_OPTIONS =
      List.of("withBorder", "withBackground", "stretched");

  /** What {@link UploadUrl} produces: the random key plus the extension of a stored image. */
  private static final Pattern UPLOAD_PATH =
      Pattern.compile("^/api/uploads/([A-Za-z0-9_-]+\\.[a-z]+)$");

  private final JsonMapper mapper;
  private final UploadRepository uploads;

  EditorContentProcessor(JsonMapper mapper, UploadRepository uploads) {
    this.mapper = mapper;
    this.uploads = uploads;
  }

  /**
   * @throws FieldErrorsException if the document is malformed, uses a block that is not allowed,
   *     references an image that is not one of our uploads, or has nothing to show
   */
  public ProcessedContent process(JsonNode document) {
    return new Run("content", false).process(document);
  }

  /**
   * For content that may be left empty, like a profile (FR-8.2): returns null for no document or
   * one with nothing to show, which clears it. Anything else is checked exactly as in {@link
   * #process}, with failures reported on {@code field}.
   */
  public ProcessedContent processOptional(JsonNode document, String field) {
    return new Run(field, true).process(document);
  }

  /** One call's state: the plain-text lines and the number of list items seen so far. */
  private final class Run {

    private final String field;
    private final boolean allowEmpty;
    private final List<String> textLines = new ArrayList<>();
    private int listItems;
    private boolean hasContent;

    Run(String field, boolean allowEmpty) {
      this.field = field;
      this.allowEmpty = allowEmpty;
    }

    ProcessedContent process(JsonNode document) {
      if (allowEmpty && (document == null || document.isNull())) {
        return null;
      }
      if (document == null || !document.isObject() || !document.path("blocks").isArray()) {
        throw invalid("must be an Editor.js document with a list of blocks");
      }
      if (document.toString().length() > MAX_DOCUMENT_CHARS) {
        throw invalid("is too long");
      }
      JsonNode blocks = document.path("blocks");
      if (blocks.size() > MAX_BLOCKS) {
        throw invalid("can have at most " + MAX_BLOCKS + " blocks");
      }

      ArrayNode cleanBlocks = mapper.createArrayNode();
      for (int i = 0; i < blocks.size(); i++) {
        ObjectNode clean = block(blocks.get(i), i + 1);
        if (clean != null) {
          cleanBlocks.add(clean);
        }
      }
      if (!hasContent) {
        if (allowEmpty) {
          return null;
        }
        throw invalid("can't be empty");
      }
      ObjectNode result = mapper.createObjectNode();
      result.set("blocks", cleanBlocks);
      return new ProcessedContent(result.toString(), String.join("\n", textLines));
    }

    /**
     * @return the cleaned block, or null for one that is empty and simply left out
     */
    private ObjectNode block(JsonNode block, int number) {
      if (!block.isObject() || !block.path("type").isTextual()) {
        throw invalid("block " + number + " is not valid");
      }
      String type = block.path("type").asString();
      JsonNode data = block.path("data");
      if (!data.isObject()) {
        throw invalid("block " + number + " has no data");
      }
      return switch (type) {
        case "paragraph" -> textBlock("paragraph", data, number);
        case "header" -> header(data, number);
        case "list" -> list(data, number);
        case "quote" -> quote(data, number);
        case "code" -> code(data, number);
        case "delimiter" -> typed("delimiter", mapper.createObjectNode());
        case "image" -> image(data, number);
        case "link" -> link(data, number);
        default -> throw invalid("block " + number + ": '" + type + "' blocks are not allowed");
      };
    }

    private ObjectNode textBlock(String type, JsonNode data, int number) {
      String text = inline(data, "text", number);
      if (InlineHtml.plainText(text).isEmpty()) {
        return null;
      }
      record(text);
      ObjectNode clean = mapper.createObjectNode();
      clean.put("text", text);
      return typed(type, clean);
    }

    private ObjectNode header(JsonNode data, int number) {
      ObjectNode block = textBlock("header", data, number);
      if (block == null) {
        return null;
      }
      JsonNode level = data.path("level");
      int value = level.isNumber() ? level.intValue() : 2;
      if (value < 1 || value > 6) {
        throw invalid("block " + number + ": header level must be 1 to 6");
      }
      ((ObjectNode) block.get("data")).put("level", value);
      return block;
    }

    private ObjectNode quote(JsonNode data, int number) {
      String text = inline(data, "text", number);
      String caption = inline(data, "caption", number);
      if (InlineHtml.plainText(text).isEmpty()) {
        return null;
      }
      record(text);
      record(caption);
      ObjectNode clean = mapper.createObjectNode();
      clean.put("text", text);
      clean.put("caption", caption);
      return typed("quote", clean);
    }

    private ObjectNode code(JsonNode data, int number) {
      JsonNode code = data.path("code");
      if (!code.isMissingNode() && !code.isNull() && !code.isTextual()) {
        throw invalid("block " + number + ": 'code' must be text");
      }
      // Plain text, not HTML: it is shown escaped, so nothing in it can act as markup.
      String source = code.isTextual() ? code.asString() : "";
      if (source.isBlank()) {
        return null;
      }
      recordPlain(source);
      ObjectNode clean = mapper.createObjectNode();
      clean.put("code", source);
      return typed("code", clean);
    }

    private ObjectNode image(JsonNode data, int number) {
      JsonNode url = data.path("file").path("url");
      Matcher match = url.isTextual() ? UPLOAD_PATH.matcher(url.asString()) : null;
      if (match == null || !match.matches() || uploads.findByStorageKey(match.group(1)).isEmpty()) {
        throw invalid("block " + number + ": images must be uploaded to this site first");
      }
      String caption = inline(data, "caption", number);
      record(caption);
      hasContent = true;
      ObjectNode file = mapper.createObjectNode();
      file.put("url", UploadUrl.of(match.group(1)));
      ObjectNode clean = mapper.createObjectNode();
      clean.set("file", file);
      clean.put("caption", caption);
      // The image tool's display options (FR-4.4): plain on/off switches, so only true or false
      // is kept. The tool itself reads the strings "true" and "false" too.
      for (String option : IMAGE_OPTIONS) {
        clean.put(option, flag(data.path(option)));
      }
      return typed("image", clean);
    }

    /**
     * A plain link to an address, with an optional title. There is no preview card: that would need
     * the server to fetch arbitrary addresses on a user's behalf.
     */
    private ObjectNode link(JsonNode data, int number) {
      JsonNode url = data.path("link");
      if (!url.isTextual() || !InlineHtml.isWebUrl(url.asString().strip())) {
        throw invalid("block " + number + ": link must be an http or https address");
      }
      JsonNode titleNode = data.path("meta").path("title");
      String title = titleNode.isTextual() ? InlineHtml.plainText(titleNode.asString()) : "";
      if (title.length() > MAX_LINK_TITLE) {
        title = title.substring(0, MAX_LINK_TITLE);
      }
      recordPlain(title);
      recordPlain(url.asString().strip());
      hasContent = true;
      ObjectNode clean = mapper.createObjectNode();
      clean.put("link", url.asString().strip());
      clean.put("title", title);
      return typed("link", clean);
    }

    private ObjectNode list(JsonNode data, int number) {
      String style = data.path("style").isTextual() ? data.path("style").asString() : "unordered";
      if (!LIST_STYLES.contains(style)) {
        throw invalid("block " + number + ": unknown list style '" + style + "'");
      }
      if (!data.path("items").isArray()) {
        throw invalid("block " + number + ": a list needs a list of items");
      }
      ArrayNode items = listItems(data.path("items"), style, 1, number);
      if (items.isEmpty()) {
        return null;
      }
      ObjectNode clean = mapper.createObjectNode();
      clean.put("style", style);
      if (style.equals("ordered")) {
        clean.set("meta", orderedMeta(data.path("meta"), number));
      }
      clean.set("items", items);
      return typed("list", clean);
    }

    private ObjectNode orderedMeta(JsonNode meta, int number) {
      ObjectNode clean = mapper.createObjectNode();
      JsonNode start = meta.path("start");
      if (start.isNumber()) {
        if (start.intValue() < 0 || start.intValue() > 100_000) {
          throw invalid("block " + number + ": list start is out of range");
        }
        clean.put("start", start.intValue());
      }
      JsonNode counter = meta.path("counterType");
      if (counter.isTextual()) {
        if (!COUNTER_TYPES.contains(counter.asString())) {
          throw invalid("block " + number + ": unknown list numbering");
        }
        clean.put("counterType", counter.asString());
      }
      return clean;
    }

    /** List 2.x items: {@code {content, meta, items}}, nested to any depth up to the limit. */
    private ArrayNode listItems(JsonNode items, String style, int depth, int number) {
      if (depth > MAX_LIST_DEPTH) {
        throw invalid(
            "block " + number + ": lists can be nested at most " + MAX_LIST_DEPTH + " deep");
      }
      ArrayNode clean = mapper.createArrayNode();
      for (JsonNode item : items) {
        if (++listItems > MAX_LIST_ITEMS) {
          throw invalid("lists can have at most " + MAX_LIST_ITEMS + " items in total");
        }
        if (!item.isObject()) {
          throw invalid("block " + number + ": list items must be objects (Editor.js List 2.x)");
        }
        String content = inline(item, "content", number);
        if (InlineHtml.plainText(content).isEmpty()) {
          continue;
        }
        record(content);
        ObjectNode cleanItem = mapper.createObjectNode();
        cleanItem.put("content", content);
        ObjectNode meta = mapper.createObjectNode();
        if (style.equals("checklist")) {
          meta.put("checked", item.path("meta").path("checked").asBoolean(false));
        }
        cleanItem.set("meta", meta);
        JsonNode children = item.path("items");
        cleanItem.set(
            "items",
            children.isArray()
                ? listItems(children, style, depth + 1, number)
                : mapper.createArrayNode());
        clean.add(cleanItem);
      }
      return clean;
    }

    private boolean flag(JsonNode value) {
      return value.isBoolean()
          ? value.booleanValue()
          : value.isTextual() && value.asString().equals("true");
    }

    /** The sanitized inline HTML in {@code node.field}; blank when it is missing. */
    private String inline(JsonNode node, String field, int number) {
      JsonNode value = node.path(field);
      if (value.isMissingNode() || value.isNull()) {
        return "";
      }
      if (!value.isTextual()) {
        throw invalid("block " + number + ": '" + field + "' must be text");
      }
      return InlineHtml.sanitize(value.asString());
    }

    /** Adds a piece of inline HTML to the search text, and counts it as content if it has text. */
    private void record(String sanitizedHtml) {
      String text = InlineHtml.plainText(sanitizedHtml);
      if (!text.isEmpty()) {
        textLines.add(text);
        hasContent = true;
      }
    }

    private FieldErrorsException invalid(String message) {
      return new FieldErrorsException(Map.of(field, List.of(message)));
    }

    /** Same for text that is not HTML (code, addresses), which must not be stripped of "tags". */
    private void recordPlain(String text) {
      if (!text.isBlank()) {
        textLines.add(text.strip());
        hasContent = true;
      }
    }
  }

  private ObjectNode typed(String type, ObjectNode data) {
    ObjectNode block = mapper.createObjectNode();
    block.put("type", type);
    block.set("data", data);
    return block;
  }
}
