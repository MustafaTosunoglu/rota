package com.rota.endpoints.web;

import com.rota.endpoints.internal.CategoryService;
import com.rota.endpoints.web.EndpointDtos.CategoryResponse;
import com.rota.endpoints.web.EndpointDtos.CreateCategoryRequest;
import com.rota.endpoints.web.EndpointDtos.UpdateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Category management inside a document version. */
@RestController
@RequestMapping("/api/v1")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/versions/{versionId}/categories")
    @PreAuthorize("hasRole('viewer')")
    public List<CategoryResponse> listCategories(@PathVariable UUID versionId) {
        return categoryService.list(versionId).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping("/versions/{versionId}/categories")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@PathVariable UUID versionId,
                                           @Valid @RequestBody CreateCategoryRequest request) {
        return CategoryResponse.from(categoryService.create(
                versionId, request.name(), request.description(), request.sortOrder()));
    }

    @PatchMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('editor')")
    public CategoryResponse updateCategory(@PathVariable UUID categoryId,
                                           @Valid @RequestBody UpdateCategoryRequest request) {
        return CategoryResponse.from(categoryService.update(
                categoryId, request.name(), request.description(), request.sortOrder()));
    }

    @DeleteMapping("/categories/{categoryId}")
    @PreAuthorize("hasRole('editor')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable UUID categoryId) {
        categoryService.delete(categoryId);
    }
}
