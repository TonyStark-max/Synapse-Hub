package com.ideamanagement.platform;

import com.ideamanagement.platform.model.Idea;
import com.ideamanagement.platform.model.Organization;
import com.ideamanagement.platform.model.User;
import com.ideamanagement.platform.repository.CommentRepository;
import com.ideamanagement.platform.repository.IdeaRepository;
import com.ideamanagement.platform.repository.OrganizationRepository;
import com.ideamanagement.platform.repository.VoteRepository;
import com.ideamanagement.platform.repository.UserRepository;
import com.ideamanagement.platform.security.DBSecurityContext;
import com.ideamanagement.platform.security.TenantContext;
import com.ideamanagement.platform.service.IdeaService;
import com.ideamanagement.platform.service.OrganizationService;
import com.ideamanagement.platform.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SecurityAndFeatureTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private IdeaService ideaService;

    @Autowired
    private IdeaRepository ideaRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DBSecurityContext dbSecurityContext;

    private Organization orgA;
    private Organization orgB;

    @BeforeEach
    public void setup() {
        // Clean database tables before each test under correct RLS context
        List<Organization> allOrgs = organizationRepository.findAll();
        for (Organization org : allOrgs) {
            dbSecurityContext.setContext(org.getId(), "test_cleanup_admin");
            commentRepository.deleteAllInBatch();
            voteRepository.deleteAllInBatch();
            ideaRepository.deleteAllInBatch();
        }

        // Since RLS is disabled on users and organizations, clean them up globally outside the loop
        dbSecurityContext.setContext("", "test_cleanup_admin");
        userRepository.deleteAllInBatch();
        for (Organization org : allOrgs) {
            organizationRepository.delete(org);
        }

        // Create Org A and Org B
        orgA = organizationService.createOrganization("org_A", "Org A Corp", "user_admin_A", "adminA@test.com", "Admin A");
        orgB = organizationService.createOrganization("org_B", "Org B Corp", "user_admin_B", "adminB@test.com", "Admin B");

        // Sync additional test users to satisfy database foreign keys
        userService.syncUser("user_member_A", "memberA@test.com", "Member A", "org_A", "MEMBER");
        userService.syncUser("user_member_B", "memberB@test.com", "Member B", "org_A", "MEMBER");
        userService.syncUser("user_rate_limit", "rate@test.com", "Rate Limit User", "org_A", "MEMBER");
        userService.syncUser("user_voter_1", "voter1@test.com", "Voter 1", "org_A", "MEMBER");
        userService.syncUser("user_voter_2", "voter2@test.com", "Voter 2", "org_A", "MEMBER");
    }

    @Test
    public void testTenantIsolationAndIdorProtection() throws Exception {
        // 1. Submit an idea in Org A
        mockMvc.perform(post("/api/ideas")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_admin_A")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "title": "Org A Core Idea",
                            "description": "This is a secret idea for Org A.",
                            "tag": "Feature"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Org A Core Idea")))
                .andExpect(jsonPath("$.orgId", is("org_A")));

        // 2. Submit an idea in Org B
        Idea ideaB = ideaService.submitIdea("Org B Core Idea", "This is secret for Org B.", "Design", "org_B", "user_admin_B");

        // 3. Query ideas as Org A user: verify Org B's ideas are NOT visible
        mockMvc.perform(get("/api/ideas")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_member_A")))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Org A Core Idea")))
                .andExpect(jsonPath("$[0].orgId", is("org_A")));

        // 4. IDOR test: Org A user attempts to vote on Org B's idea -> should get 400 Bad Request / Not Found
        mockMvc.perform(post("/api/ideas/" + ideaB.getId() + "/vote")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_member_A")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "voteType": "UP"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Idea not found in this organization")));

        // 5. IDOR test: Org A user attempts to comment on Org B's idea -> should get 400 Bad Request
        mockMvc.perform(post("/api/ideas/" + ideaB.getId() + "/comments")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_member_A")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "content": "Malicious comment trying to cross boundaries"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Idea not found in this organization")));
    }

    @Test
    public void testPrivilegeEscalation() throws Exception {
        // Create an idea under Org A
        Idea idea = ideaService.submitIdea("Security Validation", "Testing access controls.", "Improvement", "org_A", "user_admin_A");

        // Attempt status change as non-admin (MEMBER role) -> should return 403 Forbidden
        mockMvc.perform(put("/api/ideas/" + idea.getId() + "/status")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").claim("org_role", "org:member").subject("user_member_A")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "status": "UNDER_REVIEW"
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Only organization administrators can update idea status")));

        // Attempt status change as admin (ADMIN role) -> should succeed
        mockMvc.perform(put("/api/ideas/" + idea.getId() + "/status")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").claim("org_role", "org:admin").subject("user_admin_A")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "status": "UNDER_REVIEW"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UNDER_REVIEW")));
    }

    @Test
    public void testStoredXssSanitization() throws Exception {
        // Submit idea with XSS script injection in title and description
        mockMvc.perform(post("/api/ideas")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_member_A")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "title": "<script>alert('hack')</script>Clean Title",
                            "description": "<img src=x onerror=alert(1)>Clean Description",
                            "tag": "Bug"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Clean Title"))) // Script tag stripped entirely
                .andExpect(jsonPath("$.description", is("Clean Description"))); // Img tag stripped
    }

    @Test
    public void testRateLimiting() throws Exception {
        // Send multiple submissions in rapid succession to trigger the sliding window rate limit
        // Limit is set to 5 submissions per minute per user
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/ideas")
                    .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_rate_limit")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                                "title": "Spam Idea",
                                "description": "Spam description.",
                                "tag": "Feature"
                            }
                            """))
                    .andExpect(status().isCreated());
        }

        // The 6th request from the same user should get rate limited with HTTP 429
        mockMvc.perform(post("/api/ideas")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_rate_limit")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "title": "Spam Idea 6",
                            "description": "Spam description 6.",
                            "tag": "Feature"
                        }
                        """))
                .andExpect(status().is4xxClientError())
                .andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()))
                .andExpect(content().string(containsString("Rate limit exceeded for submitting ideas")));
    }

    @Test
    public void testHotScoreCalculationAndSorting() throws Exception {
        // We submit two ideas: Idea 1 (less votes) and Idea 2 (more votes)
        Idea idea1 = ideaService.submitIdea("Idea One", "First idea.", "Feature", "org_A", "user_member_A");
        Idea idea2 = ideaService.submitIdea("Idea Two", "Second idea.", "Design", "org_A", "user_member_B");

        // Vote on Idea 2 to make it more popular (hotter)
        ideaService.voteIdea(idea2.getId(), "user_voter_1", "org_A", "UP");
        ideaService.voteIdea(idea2.getId(), "user_voter_2", "org_A", "UP");

        // Retrieve ideas sorted by hot: Idea 2 must appear first
        mockMvc.perform(get("/api/ideas?sortBy=hot")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_member_A")))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("Idea Two")))
                .andExpect(jsonPath("$[1].title", is("Idea One")));

        // Retrieve ideas sorted by top (most upvotes): Idea 2 must appear first
        mockMvc.perform(get("/api/ideas?sortBy=top")
                .with(jwt().jwt(j -> j.claim("org_id", "org_A").subject("user_member_A")))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("Idea Two")))
                .andExpect(jsonPath("$[1].title", is("Idea One")));
    }
}

// Security isolation tests.
// Rate limiter tests added.
// Security context test annotations.
// Security isolation tests.
// Rate limiter tests added.
// Security context test annotations.
// Security isolation tests.
// Rate limiter tests added.
// Security context test annotations.
// Security isolation tests.
// Rate limiter tests added.
// Security context test annotations.