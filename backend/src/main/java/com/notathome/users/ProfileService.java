package com.notathome.users;

import com.notathome.categories.Category;
import com.notathome.categories.CategoryRepository;
import com.notathome.common.FieldErrorsException;
import com.notathome.content.EditorContentProcessor;
import com.notathome.content.ProcessedContent;
import com.notathome.posts.Post;
import com.notathome.posts.PostRepository;
import com.notathome.topics.CategoryRef;
import com.notathome.topics.Topic;
import com.notathome.topics.TopicRepository;
import com.notathome.topics.TopicService;
import com.notathome.uploads.UploadRepository;
import com.notathome.uploads.UploadUrl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Public profiles (FR-8.1) and editing one's own (FR-8.2). */
@Service
class ProfileService {

  static final int RECENT_TOPICS = 5;
  static final int RECENT_POSTS = 10;
  static final int EXCERPT_CHARS = 200;

  private final UserRepository users;
  private final UploadRepository uploads;
  private final PostRepository posts;
  private final TopicRepository topics;
  private final TopicService topicService;
  private final CategoryRepository categories;
  private final EditorContentProcessor contentProcessor;
  private final JsonMapper mapper;

  ProfileService(
      UserRepository users,
      UploadRepository uploads,
      PostRepository posts,
      TopicRepository topics,
      TopicService topicService,
      CategoryRepository categories,
      EditorContentProcessor contentProcessor,
      JsonMapper mapper) {
    this.users = users;
    this.uploads = uploads;
    this.posts = posts;
    this.topics = topics;
    this.topicService = topicService;
    this.categories = categories;
    this.contentProcessor = contentProcessor;
    this.mapper = mapper;
  }

  /**
   * A deleted account has no profile page (FR-8.4), and its name is not looked up by anyone: its
   * username is anonymized anyway. Banned users keep theirs, as their posts stay visible (FR-9.3).
   */
  @Transactional(readOnly = true)
  UserProfileResponse get(String username) {
    User user =
        users
            .findByUsernameIgnoreCaseAndDeletedAtIsNull(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    JsonNode content =
        user.getProfileContent() == null ? null : mapper.readTree(user.getProfileContent());
    return new UserProfileResponse(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getRole(),
        user.getCreatedAt(),
        posts.countVisibleByAuthor(user.getId()),
        content,
        topicService.recentByAuthor(user.getId(), RECENT_TOPICS),
        recentPosts(user.getId()));
  }

  @Transactional
  void update(Long userId, UpdateProfileRequest request) {
    User user = activeUser(userId);
    ProcessedContent content =
        contentProcessor.processOptional(request.profileContent(), "profileContent");
    user.updateProfile(request.displayName().strip(), content == null ? null : content.json());
  }

  /** Only the user's own uploads: anyone else's file id must not be usable as an avatar. */
  @Transactional
  void changeAvatar(Long userId, Long uploadId) {
    User user = activeUser(userId);
    String url =
        uploads
            .findById(uploadId)
            .filter(upload -> upload.getOwnerId().equals(userId))
            .map(UploadUrl::of)
            .orElseThrow(
                () -> new FieldErrorsException(Map.of("uploadId", List.of("upload not found"))));
    user.changeAvatar(url);
  }

  @Transactional
  void removeAvatar(Long userId) {
    activeUser(userId).changeAvatar(null);
  }

  private User activeUser(Long userId) {
    return users
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  private List<ProfilePost> recentPosts(Long authorId) {
    List<Post> found = posts.recentVisibleByAuthor(authorId, PageRequest.of(0, RECENT_POSTS));
    Map<Long, Topic> topicById =
        topics.findAllById(found.stream().map(Post::getTopicId).distinct().toList()).stream()
            .collect(Collectors.toMap(Topic::getId, Function.identity()));
    Map<Long, CategoryRef> categoryById = new HashMap<>();
    for (Category category :
        categories.findAllById(topicById.values().stream().map(Topic::getCategoryId).toList())) {
      categoryById.put(
          category.getId(),
          new CategoryRef(category.getId(), category.getName(), category.getSlug()));
    }
    return found.stream()
        .map(
            post -> {
              Topic topic = topicById.get(post.getTopicId());
              return new ProfilePost(
                  post.getId(),
                  topic.getId(),
                  topic.getSlug(),
                  topic.getTitle(),
                  categoryById.get(topic.getCategoryId()),
                  excerpt(post.getSearchText()),
                  post.getCreatedAt());
            })
        .toList();
  }

  /** One line of at most {@link #EXCERPT_CHARS} characters, cut on a character boundary. */
  static String excerpt(String searchText) {
    String flat = searchText.replaceAll("\\s+", " ").strip();
    if (flat.codePointCount(0, flat.length()) <= EXCERPT_CHARS) {
      return flat;
    }
    return flat.substring(0, flat.offsetByCodePoints(0, EXCERPT_CHARS)).stripTrailing() + "…";
  }
}
