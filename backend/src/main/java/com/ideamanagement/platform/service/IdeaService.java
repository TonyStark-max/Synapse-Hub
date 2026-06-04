package com.ideamanagement.platform.service;

import com.ideamanagement.platform.model.Comment;
import com.ideamanagement.platform.model.Idea;
import com.ideamanagement.platform.model.Vote;
import com.ideamanagement.platform.repository.CommentRepository;
import com.ideamanagement.platform.repository.IdeaRepository;
import com.ideamanagement.platform.repository.VoteRepository;
import com.ideamanagement.platform.security.DBSecurityContext;
import com.ideamanagement.platform.security.TenantContext;
import com.ideamanagement.platform.util.HtmlSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class IdeaService {

    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT_LOGGER");
    private static final Logger logger = LoggerFactory.getLogger(IdeaService.class);

    private static final Set<String> ALLOWED_TAGS = Set.of("Feature", "Bug", "Improvement", "Design", "Other");
    private static final Set<String> VALID_STATUSES = Set.of("SUBMITTED", "UNDER_REVIEW", "PLANNED", "IMPLEMENTED", "REJECTED");

    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;
    private final com.ideamanagement.platform.repository.UserRepository userRepository;
    private final DBSecurityContext dbSecurityContext;

    public IdeaService(IdeaRepository ideaRepository, 
                       VoteRepository voteRepository, 
                       CommentRepository commentRepository, 
                       com.ideamanagement.platform.repository.UserRepository userRepository,
                       DBSecurityContext dbSecurityContext) {
        this.ideaRepository = ideaRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.dbSecurityContext = dbSecurityContext;
    }

    @Transactional
    public Idea submitIdea(String title, String description, String tag, String orgId, String userId) {
        // Enforce server-side context for RLS in Postgres
        dbSecurityContext.setContext(orgId, userId);

        // 1. Validation
        if (title == null || title.trim().isEmpty() || title.length() > 255) {
            throw new IllegalArgumentException("Title must be between 1 and 255 characters");
        }
        if (description == null || description.trim().isEmpty() || description.length() > 5000) {
            throw new IllegalArgumentException("Description must be between 1 and 5000 characters");
        }
        if (tag == null || !ALLOWED_TAGS.contains(tag)) {
            throw new IllegalArgumentException("Invalid tag. Allowed tags: " + ALLOWED_TAGS);
        }

        // 2. Sanitization (Stored XSS mitigation)
        String sanitizedTitle = HtmlSanitizer.sanitize(title);
        String sanitizedDescription = HtmlSanitizer.sanitize(description);

        Instant now = Instant.now();
        double initialHotScore = calculateHotScore(0, 0, now);

        Idea idea = Idea.builder()
                .title(sanitizedTitle)
                .description(sanitizedDescription)
                .tag(tag)
                .status("SUBMITTED")
                .orgId(orgId)
                .userId(userId)
                .upvotesCount(0)
                .downvotesCount(0)
                .hotScore(initialHotScore)
                .build();

        Idea savedIdea = ideaRepository.save(idea);
        userRepository.findById(userId).ifPresent(u -> savedIdea.setUserName(u.getName()));
        return savedIdea;
    }

    @Transactional
    public Idea voteIdea(Long ideaId, String userId, String orgId, String voteTypeStr) {
        dbSecurityContext.setContext(orgId, userId);

        if (!"UP".equalsIgnoreCase(voteTypeStr) && !"DOWN".equalsIgnoreCase(voteTypeStr)) {
            throw new IllegalArgumentException("Vote type must be 'UP' or 'DOWN'");
        }
        String voteType = voteTypeStr.toUpperCase();

        Idea idea = ideaRepository.findByIdAndOrgId(ideaId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Idea not found in this organization"));

        Optional<Vote> existingVoteOpt = voteRepository.findByIdeaIdAndUserIdAndOrgId(ideaId, userId, orgId);

        if (existingVoteOpt.isPresent()) {
            Vote existingVote = existingVoteOpt.get();
            if (existingVote.getVoteType().equals(voteType)) {
                // Undo vote (same vote type clicked again)
                voteRepository.delete(existingVote);
            } else {
                // Change vote type
                existingVote.setVoteType(voteType);
                voteRepository.save(existingVote);
            }
        } else {
            // Register new vote
            Vote vote = Vote.builder()
                    .ideaId(ideaId)
                    .userId(userId)
                    .orgId(orgId)
                    .voteType(voteType)
                    .build();
            voteRepository.save(vote);
        }

        // Recalculate vote counts and hot score
        // Force database-level aggregation to ensure correctness
        List<Vote> votes = voteRepository.findByIdeaIdAndOrgId(ideaId, orgId);
        int upvotes = 0;
        int downvotes = 0;
        for (Vote v : votes) {
            if ("UP".equals(v.getVoteType())) {
                upvotes++;
            } else if ("DOWN".equals(v.getVoteType())) {
                downvotes++;
            }
        }

        idea.setUpvotesCount(upvotes);
        idea.setDownvotesCount(downvotes);
        // Note: use the original idea creation date for time-decay, not the current time!
        idea.setHotScore(calculateHotScore(upvotes, downvotes, idea.getCreatedAt() != null ? idea.getCreatedAt() : Instant.now()));

        return ideaRepository.save(idea);
    }

    @Transactional(readOnly = true)
    public List<Idea> getIdeas(String orgId, String sortBy) {
        dbSecurityContext.setContext(orgId, TenantContext.getCurrentUserId());
        // Enforce application scoping in addition to RLS
        Sort sort = switch (sortBy != null ? sortBy.toLowerCase() : "hot") {
            case "new" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "top" -> Sort.by(Sort.Direction.DESC, "upvotesCount");
            default -> Sort.by(Sort.Direction.DESC, "hotScore");
        };
        List<Idea> ideas = ideaRepository.findByOrgId(orgId, sort);
        ideas.forEach(idea -> {
            if (idea.getUserId() != null) {
                userRepository.findById(idea.getUserId()).ifPresent(u -> idea.setUserName(u.getName()));
            }
        });
        return ideas;
    }

    @Transactional
    public Comment addComment(Long ideaId, Long parentCommentId, String content, String userId, String orgId) {
        dbSecurityContext.setContext(orgId, userId);

        if (content == null || content.trim().isEmpty() || content.length() > 2000) {
            throw new IllegalArgumentException("Comment must be between 1 and 2000 characters");
        }

        // Check if idea exists in organization
        Idea idea = ideaRepository.findByIdAndOrgId(ideaId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Idea not found in this organization"));

        if (parentCommentId != null) {
            // Verify parent comment exists
            commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found"));
        }

        String sanitizedContent = HtmlSanitizer.sanitize(content);

        Comment comment = Comment.builder()
                .ideaId(ideaId)
                .parentCommentId(parentCommentId)
                .content(sanitizedContent)
                .userId(userId)
                .orgId(orgId)
                .build();

        Comment savedComment = commentRepository.save(comment);
        userRepository.findById(userId).ifPresent(u -> savedComment.setUserName(u.getName()));
        return savedComment;
    }

    @Transactional(readOnly = true)
    public List<Comment> getComments(Long ideaId, String orgId) {
        dbSecurityContext.setContext(orgId, TenantContext.getCurrentUserId());
        List<Comment> comments = commentRepository.findByIdeaIdAndOrgId(ideaId, orgId);
        comments.forEach(comment -> {
            if (comment.getUserId() != null) {
                userRepository.findById(comment.getUserId()).ifPresent(u -> comment.setUserName(u.getName()));
            }
        });
        return comments;
    }

    @Transactional
    public Idea updateIdeaStatus(Long ideaId, String newStatus, String userId, String orgId, String userRole) {
        dbSecurityContext.setContext(orgId, userId);

        // Server-side role check
        if (!"ADMIN".equals(userRole)) {
            throw new SecurityException("Only organization administrators can update idea status");
        }

        String statusUpper = newStatus != null ? newStatus.toUpperCase() : "";
        if (!VALID_STATUSES.contains(statusUpper)) {
            throw new IllegalArgumentException("Invalid status. Valid statuses: " + VALID_STATUSES);
        }

        Idea idea = ideaRepository.findByIdAndOrgId(ideaId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Idea not found in this organization"));

        String oldStatus = idea.getStatus();
        idea.setStatus(statusUpper);
        Idea updatedIdea = ideaRepository.save(idea);

        // Append-only audit logging: RECORD WHO, WHAT, WHEN
        auditLogger.info("AUDIT_EVENT: [StatusChange] User: {} changed Idea: {} (Org: {}) status from {} to {} at {}",
                userId, ideaId, orgId, oldStatus, statusUpper, Instant.now());

        return updatedIdea;
    }

    /**
     * Reddit-style Hot Score calculation.
     * Uses 45000 seconds (12.5 hours) as decay constant.
     */
    public double calculateHotScore(int upvotes, int downvotes, Instant createdAt) {
        int balance = upvotes - downvotes;
        double order = Math.log10(Math.max(Math.abs(balance), 1));
        int sign = Integer.compare(balance, 0);
        
        // Epoch reference: January 1, 2026 00:00:00 UTC (1767225600 epoch seconds)
        long epochSeconds = createdAt.getEpochSecond() - 1767225600L;
        
        // 45000 seconds = 12.5 hours decay constant
        return sign * order + (double) epochSeconds / 45000.0;
    }
}

// Transactional idea service logic.
// Transactional idea service logic.
// Transactional idea service logic.
// Transactional idea service logic.