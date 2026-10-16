package com.notathome.posts;

import com.notathome.users.AuthorResponse;
import com.notathome.users.Authors;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds {@link PostResponse}s, applying the rules about what a viewer may see of removed posts.
 */
@Component
public class PostResponses {

  private final Authors authors;
  private final JsonMapper mapper;

  PostResponses(Authors authors, JsonMapper mapper) {
    this.authors = authors;
    this.mapper = mapper;
  }

  /**
   * @param openingPostId the id of the topic's opening post, to mark it
   * @param viewerIsAdmin admins see the content of removed posts, labelled as removed (FR-5.8)
   */
  public List<PostResponse> toResponses(
      List<Post> posts, Long openingPostId, boolean viewerIsAdmin) {
    Map<Long, AuthorResponse> byId = authors.byId(posts.stream().map(Post::getAuthorId).toList());
    return posts.stream()
        .map(post -> toResponse(post, byId.get(post.getAuthorId()), openingPostId, viewerIsAdmin))
        .toList();
  }

  private PostResponse toResponse(
      Post post, AuthorResponse author, Long openingPostId, boolean viewerIsAdmin) {
    boolean showContent = !post.isDeleted() || viewerIsAdmin;
    return new PostResponse(
        post.getId(),
        post.getTopicId(),
        author,
        showContent ? mapper.readTree(post.getContent()) : null,
        post.isDeleted(),
        post.getId().equals(openingPostId),
        post.getCreatedAt(),
        post.getEditedAt());
  }
}
