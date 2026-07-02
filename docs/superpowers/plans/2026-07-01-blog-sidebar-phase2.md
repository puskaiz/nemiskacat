# Blog Sidebar — Phase 2 (Admin Management) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let admins edit each sidebar block's content, enable/disable blocks, reorder them, and toggle per-category visibility — through a Refine admin screen backed by a REST resource, without touching the public render path.

**Architecture:** A `SidebarAdminService` (write side) exposes list-all (incl. disabled), update-content (validated per block type against the existing `SidebarQueryService` content records), enable/disable, reorder, and category-visibility read/set. A thin `SidebarBlockController` under `/api/admin/sidebar-blocks` (path-protected ADMIN) maps these. The admin SPA adds a `sidebar-blocks` resource: a list screen (table + ↑/↓ reorder + enable `Switch`) and a per-type edit form (AUTHOR/CTA/CONTACT/SOCIAL fields, SOCIAL via `Form.List`, CATEGORIES = title + per-category visibility toggles). Phase 1's public read path is unchanged.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Jackson 3 (`tools.jackson.databind.ObjectMapper`), JUnit + Testcontainers (MockMvc with `springSecurity()`); admin-ui = React + TypeScript + Refine + antd + i18next, Vitest.

## Global Constraints

- Admin endpoints live under `/api/admin/**` and are protected **path-based** by `SecurityConfig` (`hasRole("ADMIN")`) — do NOT add `@PreAuthorize`/`@Secured`. Login is at `/api/admin/auth/login` (permitAll).
- Write requests are CSRF-protected (cookie `XSRF-TOKEN` → header `X-XSRF-TOKEN`); MockMvc write tests must use `.with(csrf())` and `.with(user("admin@example.com").roles("ADMIN"))`.
- Admin error responses are a **plain JSON string** body via `@RestControllerAdvice AdminExceptionHandler` + `@ExceptionHandler`/`@ResponseStatus` (NOT RFC 9457 problem+json). A service-defined `NotFoundException` → 404; `IllegalArgumentException` → 400.
- Admin write services: `@Service @RequiredArgsConstructor @Transactional` (reads override `readOnly=true`); validate manually; **audit every write** via `AuditService.record(String action, String entityType, String entityId, String summary)`.
- Persistence: `open-in-view=false` — materialize any lazy data inside the `@Transactional` method before returning a DTO. `ddl-auto: validate`.
- Public blog HTML stays session-independent/cacheable (CLAUDE.md #2): Phase 2 must not change the public render or `SidebarQueryService`'s read behavior.
- DTOs are inner `record`s of the owning service; the controller imports them. No OpenAPI codegen — admin-ui types are hand-written in `admin-ui/src/types.ts`.
- Code/comments/commits English; all user-facing SPA strings Hungarian via i18next namespaces.
- Backend build/test: `mvn -q -Dskip.frontend=true -Dtest=<IT> test` (the frontend-maven-plugin's npm install errors locally; this flag skips it). admin-ui: from `admin-ui/`, `npm run build` (tsc+vite) and `npm test` (vitest run).

---

## File Structure

Backend:
- Create `src/main/java/hu/deposoft/webshop/application/sidebar/SidebarAdminService.java` — write service + DTO records.
- Modify `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java` — add `findAllByOrderByDisplayOrderAsc()`.
- Create `src/main/java/hu/deposoft/webshop/api/admin/SidebarBlockController.java` — REST resource.
- Modify `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java` — map `SidebarAdminService.NotFoundException` → 404.
- Tests: `src/test/java/hu/deposoft/webshop/application/sidebar/SidebarAdminServiceIT.java`, `src/test/java/hu/deposoft/webshop/api/admin/SidebarBlockControllerIT.java`.

admin-ui:
- Create `admin-ui/src/pages/sidebar-blocks/content.ts` — pure per-type content parse/serialize + label maps.
- Create `admin-ui/src/pages/sidebar-blocks/content.test.ts` — unit tests for the helper.
- Create `admin-ui/src/pages/sidebar-blocks/list.tsx`, `admin-ui/src/pages/sidebar-blocks/edit.tsx`.
- Modify `admin-ui/src/types.ts` — sidebar block + category-visibility types.
- Modify `admin-ui/src/App.tsx` — resource + routes.
- Modify `admin-ui/src/components/layout/nav.config.ts` — nav entry.
- Create `admin-ui/src/i18n/hu/sidebar-blocks.json`, `admin-ui/src/i18n/en/sidebar-blocks.json`; modify `admin-ui/src/i18n/index.ts`, `admin-ui/src/i18n/hu/nav.json`, `admin-ui/src/i18n/en/nav.json`.

---

## Task 1: `SidebarAdminService` — blocks (list/update/enable/reorder) + category visibility

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/application/sidebar/SidebarAdminService.java`
- Modify: `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java`
- Test: `src/test/java/hu/deposoft/webshop/application/sidebar/SidebarAdminServiceIT.java`

**Interfaces:**
- Consumes: `SidebarBlockRepository`, `BlogCategoryRepository`, `ObjectMapper`, `AuditService`; the content records `SidebarQueryService.{AuthorContent,CtaContent,ContactContent,SocialContent}` and `CategoriesMeta` shape (validation only). Note `CategoriesMeta` is currently **private** in `SidebarQueryService` — validate CATEGORIES by reading the `title` field via `objectMapper.readTree(content).get("title")` instead of that private type.
- Produces (nested records in `SidebarAdminService`):
  - `SidebarBlockView(Long id, String blockType, int displayOrder, boolean enabled, String content)`
  - `ContentUpdate(String content)`
  - `EnabledUpdate(boolean enabled)`
  - `ReorderRequest(List<Long> blockIds)`
  - `CategoryVisibility(String name, String slug, boolean sidebarHidden)`
  - `VisibilityUpdate(boolean hidden)`
  - `static class NotFoundException extends RuntimeException`
  - methods: `List<SidebarBlockView> list()`, `SidebarBlockView get(Long id)`, `SidebarBlockView updateContent(Long id, ContentUpdate)`, `SidebarBlockView setEnabled(Long id, boolean)`, `List<SidebarBlockView> reorder(List<Long> blockIds)`, `List<CategoryVisibility> categories()`, `CategoryVisibility setCategoryVisibility(String slug, boolean hidden)`.

- [ ] **Step 1: Add the repository finder**

In `src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java`, add:

```java
    List<SidebarBlock> findAllByOrderByDisplayOrderAsc();
```

(Keep the existing `findByEnabledTrueOrderByDisplayOrderAsc()` used by Phase 1.)

- [ ] **Step 2: Write the failing service IT**

Create `src/test/java/hu/deposoft/webshop/application/sidebar/SidebarAdminServiceIT.java`:

```java
package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.sidebar.SidebarAdminService.CategoryVisibility;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.ContentUpdate;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.SidebarBlockView;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarAdminServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SidebarAdminService admin;
    @Autowired BlogCategoryRepository categories;

    @Test
    void listReturnsAllSeededBlocksIncludingDisabledInOrder() {
        // disable one, confirm it still appears in the admin list (unlike the public read)
        SidebarBlockView social = admin.list().stream()
                .filter(b -> b.blockType().equals("SOCIAL")).findFirst().orElseThrow();
        admin.setEnabled(social.id(), false);
        assertThat(admin.list()).extracting(SidebarBlockView::blockType)
                .containsExactly("AUTHOR", "CATEGORIES", "CTA", "CONTACT", "SOCIAL");
        assertThat(admin.list().stream().filter(b -> b.blockType().equals("SOCIAL"))
                .findFirst().orElseThrow().enabled()).isFalse();
    }

    @Test
    void updateContentValidatesJsonForType() {
        SidebarBlockView author = admin.list().get(0); // AUTHOR
        SidebarBlockView updated = admin.updateContent(author.id(),
                new ContentUpdate("{\"name\":\"Teszt Elek\",\"bio\":\"új\",\"photoUrl\":\"/x.svg\"}"));
        assertThat(updated.content()).contains("Teszt Elek");
        // malformed JSON for the type -> 400-mapped IllegalArgumentException
        assertThatThrownBy(() -> admin.updateContent(author.id(), new ContentUpdate("{not json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reorderPersistsNewDisplayOrder() {
        List<Long> ids = admin.list().stream().map(SidebarBlockView::id).toList();
        List<Long> reversed = ids.reversed();
        List<SidebarBlockView> after = admin.reorder(reversed);
        assertThat(after).extracting(SidebarBlockView::id).containsExactlyElementsOf(reversed);
        // a reorder list that isn't exactly the existing id set is rejected
        assertThatThrownBy(() -> admin.reorder(List.of(ids.get(0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryVisibilityReadAndSet() {
        categories.save(BlogCategory.create("Festés", "festes-admin"));
        assertThat(admin.categories()).extracting(CategoryVisibility::slug).contains("festes-admin");
        CategoryVisibility v = admin.setCategoryVisibility("festes-admin", true);
        assertThat(v.sidebarHidden()).isTrue();
        assertThat(categories.findBySlug("festes-admin").orElseThrow().isSidebarHidden()).isTrue();
    }

    @Test
    void getUnknownThrowsNotFound() {
        assertThatThrownBy(() -> admin.get(999999L))
                .isInstanceOf(SidebarAdminService.NotFoundException.class);
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn -q -Dskip.frontend=true -Dtest=SidebarAdminServiceIT test`
Expected: FAIL — `SidebarAdminService` does not exist (compilation error).

- [ ] **Step 4: Implement the service**

Create `src/main/java/hu/deposoft/webshop/application/sidebar/SidebarAdminService.java`:

```java
package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.AuthorContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.ContactContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.CtaContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SocialContent;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.sidebar.BlockType;
import hu.deposoft.webshop.domain.sidebar.SidebarBlock;
import hu.deposoft.webshop.domain.sidebar.SidebarBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SidebarAdminService {

    private final SidebarBlockRepository blocks;
    private final BlogCategoryRepository categories;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public record SidebarBlockView(Long id, String blockType, int displayOrder,
                                   boolean enabled, String content) {}
    public record ContentUpdate(String content) {}
    public record EnabledUpdate(boolean enabled) {}
    public record ReorderRequest(List<Long> blockIds) {}
    public record CategoryVisibility(String name, String slug, boolean sidebarHidden) {}
    public record VisibilityUpdate(boolean hidden) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public List<SidebarBlockView> list() {
        return blocks.findAllByOrderByDisplayOrderAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SidebarBlockView get(Long id) {
        return toView(find(id));
    }

    public SidebarBlockView updateContent(Long id, ContentUpdate cmd) {
        SidebarBlock b = find(id);
        validateContent(b.getBlockType(), cmd.content());
        b.setContent(cmd.content());
        audit.record("SIDEBAR_BLOCK_UPDATE", "sidebar_block", String.valueOf(id), b.getBlockType().name());
        return toView(b);
    }

    public SidebarBlockView setEnabled(Long id, boolean enabled) {
        SidebarBlock b = find(id);
        b.setEnabled(enabled);
        audit.record(enabled ? "SIDEBAR_BLOCK_ENABLE" : "SIDEBAR_BLOCK_DISABLE",
                "sidebar_block", String.valueOf(id), b.getBlockType().name());
        return toView(b);
    }

    public List<SidebarBlockView> reorder(List<Long> blockIds) {
        List<SidebarBlock> current = blocks.findAllByOrderByDisplayOrderAsc();
        if (blockIds.size() != current.size()
                || !blockIds.stream().sorted().toList()
                    .equals(current.stream().map(SidebarBlock::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the existing block ids");
        }
        for (int i = 0; i < blockIds.size(); i++) {
            Long bid = blockIds.get(i);
            SidebarBlock b = current.stream().filter(x -> x.getId().equals(bid)).findFirst().orElseThrow();
            b.setDisplayOrder(i + 1);
        }
        audit.record("SIDEBAR_BLOCK_REORDER", "sidebar_block", "*", blockIds.toString());
        return list();
    }

    @Transactional(readOnly = true)
    public List<CategoryVisibility> categories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryVisibility(c.getName(), c.getSlug(), c.isSidebarHidden()))
                .toList();
    }

    public CategoryVisibility setCategoryVisibility(String slug, boolean hidden) {
        BlogCategory c = categories.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Blog category not found: " + slug));
        c.setSidebarHidden(hidden);
        audit.record("SIDEBAR_CATEGORY_VISIBILITY", "blog_category", slug, hidden ? "hidden" : "visible");
        return new CategoryVisibility(c.getName(), c.getSlug(), c.isSidebarHidden());
    }

    private void validateContent(BlockType type, String json) {
        try {
            switch (type) {
                case AUTHOR -> objectMapper.readValue(json, AuthorContent.class);
                case CTA -> objectMapper.readValue(json, CtaContent.class);
                case CONTACT -> objectMapper.readValue(json, ContactContent.class);
                case SOCIAL -> objectMapper.readValue(json, SocialContent.class);
                case CATEGORIES -> {
                    if (objectMapper.readTree(json).get("title") == null) {
                        throw new IllegalArgumentException("CATEGORIES content requires a 'title'");
                    }
                }
            }
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid content JSON for " + type + ": " + e.getOriginalMessage());
        }
    }

    private SidebarBlock find(Long id) {
        return blocks.findById(id)
                .orElseThrow(() -> new NotFoundException("Sidebar block not found: " + id));
    }

    private SidebarBlockView toView(SidebarBlock b) {
        return new SidebarBlockView(b.getId(), b.getBlockType().name(),
                b.getDisplayOrder(), b.isEnabled(), b.getContent());
    }
}
```

Notes: the four typed records (`AuthorContent`/`CtaContent`/`ContactContent`/`SocialContent`) are already `public` records in `SidebarQueryService` (Phase 1). `BlogCategoryRepository.findAllByOrderByNameAsc()` already exists. `objectMapper.readTree`/`readValue` throw `tools.jackson.core.JacksonException` (Jackson 3) — caught here and rethrown as `IllegalArgumentException` (→ 400). `find()`/mutations rely on Hibernate dirty-tracking inside the class-level `@Transactional`.

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -q -Dskip.frontend=true -Dtest=SidebarAdminServiceIT test`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/application/sidebar/SidebarAdminService.java \
        src/main/java/hu/deposoft/webshop/domain/sidebar/SidebarBlockRepository.java \
        src/test/java/hu/deposoft/webshop/application/sidebar/SidebarAdminServiceIT.java
git commit -m "feat(blog): SidebarAdminService — edit/enable/reorder blocks + category visibility"
```

---

## Task 2: `SidebarBlockController` REST resource + error mapping

**Files:**
- Create: `src/main/java/hu/deposoft/webshop/api/admin/SidebarBlockController.java`
- Modify: `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`
- Test: `src/test/java/hu/deposoft/webshop/api/admin/SidebarBlockControllerIT.java`

**Interfaces:**
- Consumes: `SidebarAdminService` and its records.
- Produces (HTTP, all under `/api/admin/sidebar-blocks`, ADMIN-only via path security):
  - `GET ` → `List<SidebarBlockView>` + `X-Total-Count`
  - `GET /{id}` → `SidebarBlockView`
  - `PUT /{id}` body `ContentUpdate` → `SidebarBlockView`
  - `POST /{id}/enable` → `SidebarBlockView`; `POST /{id}/disable` → `SidebarBlockView`
  - `POST /reorder` body `ReorderRequest` → `List<SidebarBlockView>`
  - `GET /categories` → `List<CategoryVisibility>`
  - `POST /categories/{slug}/visibility` body `VisibilityUpdate` → `CategoryVisibility`

- [ ] **Step 1: Map `NotFoundException` in the exception handler**

In `src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java`, add an import and a handler method (place next to the other `@ExceptionHandler` methods; `IllegalArgumentException` → 400 is already handled globally there, so content-validation errors map automatically):

```java
import hu.deposoft.webshop.application.sidebar.SidebarAdminService;
```
```java
    @ExceptionHandler(SidebarAdminService.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String sidebarBlockNotFound(SidebarAdminService.NotFoundException e) {
        return e.getMessage();
    }
```

- [ ] **Step 2: Write the failing controller IT**

Create `src/test/java/hu/deposoft/webshop/api/admin/SidebarBlockControllerIT.java`:

```java
package hu.deposoft.webshop.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarBlockControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;
    MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    private RequestPostProcessor admin() { return user("admin@example.com").roles("ADMIN"); }

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mvc.perform(get("/api/admin/sidebar-blocks")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsSeededBlocksWithTotalCount() throws Exception {
        mvc.perform(get("/api/admin/sidebar-blocks").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$[0].blockType").value("AUTHOR"));
    }

    @Test
    void updateContentPersists() throws Exception {
        Long authorId = firstBlockId();
        mvc.perform(put("/api/admin/sidebar-blocks/" + authorId).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"{\\\"name\\\":\\\"Új Név\\\",\\\"bio\\\":\\\"b\\\",\\\"photoUrl\\\":\\\"/x.svg\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("Új Név")));
    }

    @Test
    void invalidContentReturns400() throws Exception {
        Long authorId = firstBlockId();
        mvc.perform(put("/api/admin/sidebar-blocks/" + authorId).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"{not json\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableThenEnableToggles() throws Exception {
        Long id = firstBlockId();
        mvc.perform(post("/api/admin/sidebar-blocks/" + id + "/disable").with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/admin/sidebar-blocks/" + id + "/enable").with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void categoryVisibilityToggle() throws Exception {
        // seed a category via the list (uncategorized is seeded hidden; here just hit the endpoint shape)
        mvc.perform(get("/api/admin/sidebar-blocks/categories").with(admin()))
                .andExpect(status().isOk());
    }

    private Long firstBlockId() throws Exception {
        String body = mvc.perform(get("/api/admin/sidebar-blocks").with(admin()))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body);
        return arr.get(0).get("id").asLong();
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn -q -Dskip.frontend=true -Dtest=SidebarBlockControllerIT test`
Expected: FAIL — controller missing (404s / compilation).

- [ ] **Step 4: Implement the controller**

Create `src/main/java/hu/deposoft/webshop/api/admin/SidebarBlockController.java`:

```java
package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.sidebar.SidebarAdminService;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.CategoryVisibility;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.ContentUpdate;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.ReorderRequest;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.SidebarBlockView;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.VisibilityUpdate;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SidebarBlockController {

    private final SidebarAdminService service;

    @GetMapping("/api/admin/sidebar-blocks")
    public List<SidebarBlockView> list(HttpServletResponse response) {
        List<SidebarBlockView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @GetMapping("/api/admin/sidebar-blocks/{id}")
    public SidebarBlockView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/api/admin/sidebar-blocks/{id}")
    public SidebarBlockView update(@PathVariable Long id, @RequestBody ContentUpdate cmd) {
        return service.updateContent(id, cmd);
    }

    @PostMapping("/api/admin/sidebar-blocks/{id}/enable")
    public SidebarBlockView enable(@PathVariable Long id) {
        return service.setEnabled(id, true);
    }

    @PostMapping("/api/admin/sidebar-blocks/{id}/disable")
    public SidebarBlockView disable(@PathVariable Long id) {
        return service.setEnabled(id, false);
    }

    @PostMapping("/api/admin/sidebar-blocks/reorder")
    public List<SidebarBlockView> reorder(@RequestBody ReorderRequest body) {
        return service.reorder(body.blockIds());
    }

    @GetMapping("/api/admin/sidebar-blocks/categories")
    public List<CategoryVisibility> categories() {
        return service.categories();
    }

    @PostMapping("/api/admin/sidebar-blocks/categories/{slug}/visibility")
    public CategoryVisibility setVisibility(@PathVariable String slug, @RequestBody VisibilityUpdate body) {
        return service.setCategoryVisibility(slug, body.hidden());
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -q -Dskip.frontend=true -Dtest=SidebarBlockControllerIT test`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/hu/deposoft/webshop/api/admin/SidebarBlockController.java \
        src/main/java/hu/deposoft/webshop/api/admin/AdminExceptionHandler.java \
        src/test/java/hu/deposoft/webshop/api/admin/SidebarBlockControllerIT.java
git commit -m "feat(admin): /api/admin/sidebar-blocks REST resource (edit/enable/reorder + category visibility)"
```

---

## Task 3: admin-ui content helper (pure parse/serialize per block type) + types

**Files:**
- Create: `admin-ui/src/pages/sidebar-blocks/content.ts`
- Create: `admin-ui/src/pages/sidebar-blocks/content.test.ts`
- Modify: `admin-ui/src/types.ts`

**Interfaces:**
- Produces (in `content.ts`):
  - types `BlockType`, `AuthorContent`, `CtaContent`, `ContactContent`, `SocialLink`, `SocialContent`, `CategoriesContent`
  - `parseContent(blockType: BlockType, json: string): Record<string, unknown>` — `JSON.parse`, returns `{}` on empty/invalid
  - `serializeContent(blockType: BlockType, value: Record<string, unknown>): string` — `JSON.stringify`
  - `BLOCK_LABELS: Record<BlockType, string>` (Hungarian labels)
- Produces (in `types.ts`): `SidebarBlock`, `CategoryVisibility`.

- [ ] **Step 1: Write the failing helper test**

Create `admin-ui/src/pages/sidebar-blocks/content.test.ts`:

```ts
import { describe, expect, it } from "vitest";
import { parseContent, serializeContent } from "./content";

describe("sidebar block content helper", () => {
  it("round-trips AUTHOR content", () => {
    const json = '{"name":"A","bio":"b","photoUrl":"/x.svg"}';
    const parsed = parseContent("AUTHOR", json);
    expect(parsed).toMatchObject({ name: "A", bio: "b", photoUrl: "/x.svg" });
    expect(JSON.parse(serializeContent("AUTHOR", parsed))).toMatchObject({ name: "A" });
  });

  it("returns {} for empty or invalid json", () => {
    expect(parseContent("CTA", "")).toEqual({});
    expect(parseContent("CTA", "{not json")).toEqual({});
  });

  it("serializes SOCIAL links array", () => {
    const out = serializeContent("SOCIAL", { title: "K", links: [{ network: "facebook", url: "u" }] });
    expect(JSON.parse(out).links[0].network).toBe("facebook");
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run (from `admin-ui/`): `npm test -- content`
Expected: FAIL — `./content` has no exports.

- [ ] **Step 3: Implement the helper**

Create `admin-ui/src/pages/sidebar-blocks/content.ts`:

```ts
export type BlockType = "AUTHOR" | "CATEGORIES" | "CTA" | "CONTACT" | "SOCIAL";

export interface AuthorContent { name: string; bio: string; photoUrl: string; }
export interface CtaContent { title: string; buttonLabel: string; url: string; imageUrl: string; }
export interface ContactContent { title: string; phone: string; email: string; address: string; }
export interface SocialLink { network: string; url: string; }
export interface SocialContent { title: string; links: SocialLink[]; }
export interface CategoriesContent { title: string; }

export const BLOCK_LABELS: Record<BlockType, string> = {
  AUTHOR: "Szerző",
  CATEGORIES: "Kategóriák",
  CTA: "Ajánló (CTA)",
  CONTACT: "Elérhetőség",
  SOCIAL: "Közösségi linkek",
};

export function parseContent(_blockType: BlockType, json: string): Record<string, unknown> {
  if (!json) return {};
  try {
    const v = JSON.parse(json);
    return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export function serializeContent(_blockType: BlockType, value: Record<string, unknown>): string {
  return JSON.stringify(value ?? {});
}
```

- [ ] **Step 4: Add the API types**

In `admin-ui/src/types.ts`, append:

```ts
export interface SidebarBlock {
  id: number;
  blockType: "AUTHOR" | "CATEGORIES" | "CTA" | "CONTACT" | "SOCIAL";
  displayOrder: number;
  enabled: boolean;
  content: string; // type-specific JSON
}

export interface CategoryVisibility {
  name: string;
  slug: string;
  sidebarHidden: boolean;
}
```

- [ ] **Step 5: Run to verify the helper test passes + build**

Run (from `admin-ui/`): `npm test -- content` → PASS (3 tests).
Run: `npm run build` → tsc + vite succeed.

- [ ] **Step 6: Commit**

```bash
git add admin-ui/src/pages/sidebar-blocks/content.ts \
        admin-ui/src/pages/sidebar-blocks/content.test.ts \
        admin-ui/src/types.ts
git commit -m "feat(admin-ui): sidebar block content helper + API types"
```

---

## Task 4: admin-ui list screen + resource/nav/i18n wiring

**Files:**
- Create: `admin-ui/src/pages/sidebar-blocks/list.tsx`
- Modify: `admin-ui/src/App.tsx`, `admin-ui/src/components/layout/nav.config.ts`
- Create: `admin-ui/src/i18n/hu/sidebar-blocks.json`, `admin-ui/src/i18n/en/sidebar-blocks.json`
- Modify: `admin-ui/src/i18n/index.ts`, `admin-ui/src/i18n/hu/nav.json`, `admin-ui/src/i18n/en/nav.json`

**Interfaces:**
- Consumes: `GET /api/admin/sidebar-blocks` (via Refine `useTable` resource `"sidebar-blocks"`), `POST .../{id}/enable|disable` and `POST .../reorder` (via `apiFetch`), `BLOCK_LABELS` from `content.ts`, `SidebarBlock` type.
- Produces: `SidebarBlockList` component; the `sidebar-blocks` Refine resource + `/sidebar-blocks` route + nav entry.

- [ ] **Step 1: Create the i18n namespace files**

Create `admin-ui/src/i18n/hu/sidebar-blocks.json`:
```json
{
  "title": "Oldalsáv blokkok",
  "subtitle": "A blog oldalsávjának tartalma",
  "colType": "Blokk",
  "colEnabled": "Látszik",
  "colActions": "Műveletek",
  "edit": "Szerkesztés",
  "moveUp": "Fel",
  "moveDown": "Le",
  "saved": "Mentve",
  "saveFailed": "A mentés nem sikerült",
  "editTitle": "Blokk szerkesztése",
  "fieldName": "Név",
  "fieldBio": "Bemutatkozás",
  "fieldPhotoUrl": "Fotó URL",
  "fieldHeading": "Cím",
  "fieldButtonLabel": "Gomb felirata",
  "fieldUrl": "Hivatkozás",
  "fieldImageUrl": "Kép URL",
  "fieldPhone": "Telefon",
  "fieldEmail": "E-mail",
  "fieldAddress": "Cím",
  "social": "Közösségi linkek",
  "socialNetwork": "Hálózat",
  "socialUrl": "URL",
  "addLink": "Link hozzáadása",
  "categories": "Kategóriák láthatósága",
  "catVisible": "Látszik az oldalsávban"
}
```
Create `admin-ui/src/i18n/en/sidebar-blocks.json` with the same keys, English values (e.g. `"title": "Sidebar blocks"`, `"colEnabled": "Visible"`, etc.).

- [ ] **Step 2: Register the namespace**

In `admin-ui/src/i18n/index.ts`, follow the existing 8-namespace import pattern: import `huSidebarBlocks from "./hu/sidebar-blocks.json"` and `enSidebarBlocks from "./en/sidebar-blocks.json"`, add `"sidebar-blocks": huSidebarBlocks` to the `hu` resources object and `"sidebar-blocks": enSidebarBlocks` to the `en` object, and add `"sidebar-blocks"` to the `ns` array. Add a `sidebarBlocks` key to `hu/nav.json` (`"sidebarBlocks": "Oldalsáv blokkok"`) and `en/nav.json` (`"sidebarBlocks": "Sidebar blocks"`).

- [ ] **Step 3: Write the list screen**

Create `admin-ui/src/pages/sidebar-blocks/list.tsx`:

```tsx
import { useTable } from "@refinedev/core";
import { Button, Space, Switch, Table, message } from "antd";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { API_BASE, apiFetch } from "../../api/http";
import type { SidebarBlock } from "../../types";
import { BLOCK_LABELS, type BlockType } from "./content";

export const SidebarBlockList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { tableQueryResult } = useTable<SidebarBlock>({
    resource: "sidebar-blocks",
    pagination: { mode: "off" },
  });
  const rows = [...(tableQueryResult.data?.data ?? [])].sort((a, b) => a.displayOrder - b.displayOrder);
  const refetch = () => tableQueryResult.refetch();

  const toggle = async (b: SidebarBlock) => {
    const res = await apiFetch(`${API_BASE}/sidebar-blocks/${b.id}/${b.enabled ? "disable" : "enable"}`, { method: "POST" });
    if (res.ok) { await refetch(); } else { message.error(t("sidebar-blocks:saveFailed")); }
  };

  const move = async (index: number, dir: -1 | 1) => {
    const swap = index + dir;
    if (swap < 0 || swap >= rows.length) return;
    const order = rows.map((r) => r.id);
    [order[index], order[swap]] = [order[swap], order[index]];
    const res = await apiFetch(`${API_BASE}/sidebar-blocks/reorder`, {
      method: "POST",
      body: JSON.stringify({ blockIds: order }),
    });
    if (res.ok) { await refetch(); } else { message.error(t("sidebar-blocks:saveFailed")); }
  };

  return (
    <div>
      <h1>{t("sidebar-blocks:title")}</h1>
      <Table dataSource={rows} rowKey="id" pagination={false}>
        <Table.Column<SidebarBlock> title={t("sidebar-blocks:colType")} dataIndex="blockType"
          render={(v: BlockType) => BLOCK_LABELS[v]} />
        <Table.Column<SidebarBlock> title={t("sidebar-blocks:colEnabled")} dataIndex="enabled"
          render={(_, r) => <Switch checked={r.enabled} onChange={() => void toggle(r)} />} />
        <Table.Column<SidebarBlock> title={t("sidebar-blocks:colActions")}
          render={(_, r, index) => (
            <Space>
              <Button size="small" disabled={index === 0} onClick={() => void move(index, -1)}>{t("sidebar-blocks:moveUp")}</Button>
              <Button size="small" disabled={index === rows.length - 1} onClick={() => void move(index, 1)}>{t("sidebar-blocks:moveDown")}</Button>
              <Button size="small" type="primary" onClick={() => navigate(`/sidebar-blocks/edit/${r.id}`)}>{t("sidebar-blocks:edit")}</Button>
            </Space>
          )} />
      </Table>
    </div>
  );
};
```

- [ ] **Step 4: Register resource + route**

In `admin-ui/src/App.tsx`: add to the `resources` array
`{ name: "sidebar-blocks", list: "/sidebar-blocks", edit: "/sidebar-blocks/edit/:id", meta: { label: "Oldalsáv blokkok" } }`,
import `SidebarBlockList` (and `SidebarBlockEdit` for Task 5) at the top, and add inside the protected routes block:
```tsx
<Route path="/sidebar-blocks">
  <Route index element={<SidebarBlockList />} />
  <Route path="edit/:id" element={<SidebarBlockEdit />} />
</Route>
```
(The `SidebarBlockEdit` import + element will resolve once Task 5 creates it; if building between tasks, temporarily point both to `SidebarBlockList`, then switch in Task 5. Prefer doing Task 4 + Task 5 before the build in Task 5 step.)

In `admin-ui/src/components/layout/nav.config.ts`, add a `NavRow` to `NAV` reusing an existing `ICON` entry (e.g. `ICON.cms` if present, else any content icon):
```ts
{ item: { id: "sidebar-blocks", labelKey: "nav:sidebarBlocks", route: "/sidebar-blocks", iconPath: ICON.cms } },
```

- [ ] **Step 5: Build**

Run (from `admin-ui/`): `npm run build`
Expected: tsc + vite succeed. (If `SidebarBlockEdit` isn't created yet, complete Task 5 first, then build — the two FE screens are sibling deliverables; build once both exist.)

- [ ] **Step 6: Commit**

```bash
git add admin-ui/src/pages/sidebar-blocks/list.tsx admin-ui/src/App.tsx \
        admin-ui/src/components/layout/nav.config.ts admin-ui/src/i18n/
git commit -m "feat(admin-ui): sidebar blocks list (reorder + enable toggle) + nav/i18n"
```

---

## Task 5: admin-ui edit screen (per-type form + category visibility)

**Files:**
- Create: `admin-ui/src/pages/sidebar-blocks/edit.tsx`

**Interfaces:**
- Consumes: `useOne`/`useUpdate` (Refine, resource `"sidebar-blocks"`), `apiFetch` for category visibility, `parseContent`/`serializeContent`/`BLOCK_LABELS` from `content.ts`, `SidebarBlock`/`CategoryVisibility` types.
- Produces: `SidebarBlockEdit` component (imported by `App.tsx` from Task 4).

- [ ] **Step 1: Write the edit screen**

Create `admin-ui/src/pages/sidebar-blocks/edit.tsx`:

```tsx
import { useOne, useUpdate } from "@refinedev/core";
import { Button, Form, Input, Space, Switch, Table, message } from "antd";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { API_BASE, apiFetch } from "../../api/http";
import type { CategoryVisibility, SidebarBlock } from "../../types";
import { BLOCK_LABELS, parseContent, serializeContent, type BlockType } from "./content";

export const SidebarBlockEdit = () => {
  const { t } = useTranslation();
  const { id } = useParams();
  const [form] = Form.useForm();
  const { data, isLoading } = useOne<SidebarBlock>({ resource: "sidebar-blocks", id: id ?? "" });
  const { mutate: update } = useUpdate<SidebarBlock>();
  const block = data?.data;
  const type = block?.blockType as BlockType | undefined;

  const [cats, setCats] = useState<CategoryVisibility[]>([]);
  useEffect(() => {
    if (type === "CATEGORIES") {
      void apiFetch(`${API_BASE}/sidebar-blocks/categories`).then(async (r) => {
        if (r.ok) setCats((await r.json()) as CategoryVisibility[]);
      });
    }
  }, [type]);

  if (isLoading || !block || !type) return <div>…</div>;

  const initial = parseContent(type, block.content);

  const save = async () => {
    const values = await form.validateFields();
    update(
      { resource: "sidebar-blocks", id: block.id, values: { content: serializeContent(type, values) } },
      { onSuccess: () => message.success(t("sidebar-blocks:saved")),
        onError: () => message.error(t("sidebar-blocks:saveFailed")) },
    );
  };

  const setCatHidden = async (slug: string, hidden: boolean) => {
    const res = await apiFetch(`${API_BASE}/sidebar-blocks/categories/${slug}/visibility`, {
      method: "POST", body: JSON.stringify({ hidden }),
    });
    if (res.ok) {
      setCats((prev) => prev.map((c) => (c.slug === slug ? { ...c, sidebarHidden: hidden } : c)));
    } else { message.error(t("sidebar-blocks:saveFailed")); }
  };

  return (
    <div>
      <h1>{t("sidebar-blocks:editTitle")} — {BLOCK_LABELS[type]}</h1>
      <Form form={form} layout="vertical" initialValues={initial}>
        {type === "AUTHOR" && (<>
          <Form.Item label={t("sidebar-blocks:fieldName")} name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldBio")} name="bio"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldPhotoUrl")} name="photoUrl"><Input /></Form.Item>
        </>)}
        {type === "CTA" && (<>
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldButtonLabel")} name="buttonLabel"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldUrl")} name="url"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldImageUrl")} name="imageUrl"><Input /></Form.Item>
        </>)}
        {type === "CONTACT" && (<>
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldPhone")} name="phone"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldEmail")} name="email"><Input /></Form.Item>
          <Form.Item label={t("sidebar-blocks:fieldAddress")} name="address"><Input /></Form.Item>
        </>)}
        {type === "SOCIAL" && (<>
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title"><Input /></Form.Item>
          <Form.List name="links">
            {(fields, { add, remove }) => (<>
              {fields.map((field) => (
                <Space key={field.key} align="baseline">
                  <Form.Item name={[field.name, "network"]} rules={[{ required: true }]}>
                    <Input placeholder={t("sidebar-blocks:socialNetwork")} />
                  </Form.Item>
                  <Form.Item name={[field.name, "url"]} rules={[{ required: true }]}>
                    <Input placeholder={t("sidebar-blocks:socialUrl")} />
                  </Form.Item>
                  <Button onClick={() => remove(field.name)}>×</Button>
                </Space>
              ))}
              <Button onClick={() => add({ network: "", url: "" })}>{t("sidebar-blocks:addLink")}</Button>
            </>)}
          </Form.List>
        </>)}
        {type === "CATEGORIES" && (
          <Form.Item label={t("sidebar-blocks:fieldHeading")} name="title"><Input /></Form.Item>
        )}
      </Form>

      {type === "CATEGORIES" && (
        <div style={{ marginTop: 24 }}>
          <h2>{t("sidebar-blocks:categories")}</h2>
          <Table dataSource={cats} rowKey="slug" pagination={false}>
            <Table.Column title={t("sidebar-blocks:fieldName")} dataIndex="name" />
            <Table.Column<CategoryVisibility> title={t("sidebar-blocks:catVisible")}
              render={(_, c) => (
                <Switch checked={!c.sidebarHidden}
                        onChange={(checked) => void setCatHidden(c.slug, !checked)} />
              )} />
          </Table>
        </div>
      )}

      <Button type="primary" style={{ marginTop: 16 }} onClick={() => void save()}>
        {t("sidebar-blocks:saved").length ? "Mentés" : "Mentés"}
      </Button>
    </div>
  );
};
```

(The save button label is the Hungarian "Mentés"; the ternary is only to avoid an unused-import lint — replace with a plain `Mentés` string if preferred.)

- [ ] **Step 2: Build**

Run (from `admin-ui/`): `npm run build`
Expected: tsc + vite succeed, no type errors, no unused-import errors. Fix antd `Form.List`/`Table.Column` generic typings as the compiler directs.

- [ ] **Step 3: Run the full admin-ui test suite**

Run (from `admin-ui/`): `npm test`
Expected: all green (the new `content.test.ts` + existing suites). Screens are not unit-tested by project convention; the build is their gate.

- [ ] **Step 4: Commit**

```bash
git add admin-ui/src/pages/sidebar-blocks/edit.tsx
git commit -m "feat(admin-ui): sidebar block per-type edit form + category visibility toggles"
```

---

## Manual verification (after Task 5; human or browser-driven)

1. Build the admin SPA into the app (`mvn -Pfrontend ...` or the normal build) and open `/admin/sidebar-blocks` as an ADMIN.
2. Confirm the list shows all 5 blocks in order with an enable `Switch`; toggle one off → reload `/blog` → that block is gone; toggle on → it returns.
3. Use ↑/↓ to reorder; reload `/blog` → order matches.
4. Edit the AUTHOR block (name/bio/photo) → Save → `/blog` reflects it. Edit SOCIAL (add/remove a link) → Save → links update.
5. Open the CATEGORIES block → toggle a category's visibility off → reload `/blog` → that category disappears from the list; the public sidebar still renders.

---

## Self-Review

- **Spec coverage (Phase 2 section of the design):** edit content (Tasks 1–2 backend, Task 5 form), enable/disable per block (Task 1 `setEnabled`, Task 2 endpoints, Task 4 Switch), reorder (Task 1 `reorder`, Task 2 `/reorder`, Task 4 ↑/↓), per-category visibility (Task 1 `categories`/`setCategoryVisibility`, Task 2 endpoints, Task 5 toggles), ADMIN-only + behind existing auth (path-based, Task 2 IT 401 case), no public-surface change (no edits to `SidebarQueryService`/templates). No image upload (URL fields only) — matches the spec's explicit exclusion.
- **Placeholder scan:** all backend code, IT code, helper code, and screen code are concrete. The one `App.tsx` sequencing note (create both screens before building) is an ordering instruction, not a placeholder.
- **Type consistency:** `SidebarBlockView(id, blockType, displayOrder, enabled, content)`, `ContentUpdate(content)`, `ReorderRequest(blockIds)`, `VisibilityUpdate(hidden)`, `CategoryVisibility(name, slug, sidebarHidden)` are identical across service (Task 1), controller (Task 2), and TS types/screens (Tasks 3–5). The four content records are reused from `SidebarQueryService` (Phase 1) for validation. `BlockType` literals match the backend enum names.
- **Convention adherence:** path-based ADMIN auth (no `@PreAuthorize`); plain-string errors via `AdminExceptionHandler`; `X-Total-Count`; `AuditService.record(...)` on every write; `POST /reorder` mirrors `ProductVariantController`; SPA uses `useTable`/`useOne`/`useUpdate` + `apiFetch` for non-CRUD, hand-written `types.ts`, i18next namespaces, `nav.config.ts` entry.
- **Known latitude:** exact antd generic typings on `Table.Column`/`Form.List` and the `ICON` key for nav are verified by the Task 4/5 build; the SPA screens are gated by build (project does not unit-test screens).
