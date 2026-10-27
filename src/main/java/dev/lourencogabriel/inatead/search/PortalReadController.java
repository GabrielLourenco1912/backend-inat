package dev.lourencogabriel.inatead.search;

import dev.lourencogabriel.inatead.shared.api.PageResponse;
import dev.lourencogabriel.inatead.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PortalReadController {
    private final PortalReadService reads;

    @GetMapping(value="/api/search/{resource}",params="view=summary")
    public ApiResponse<PageResponse<Map<String,Object>>> search(@PathVariable String resource,@RequestParam Map<String,String> filters,Authentication auth) {
        return new ApiResponse<>(reads.page(resource,filters,auth),"Search results");
    }

    @GetMapping(value={"/api/contracts/{id}","/api/cohorts/{id}","/api/person-documents/{id}"},params="view=summary")
    public ApiResponse<Map<String,Object>> item(@PathVariable String id,HttpServletRequest request,Authentication auth) {
        String resource=request.getRequestURI().split("/")[2];
        return new ApiResponse<>(reads.item(resource,id,auth),"Record found");
    }

    @GetMapping(value="/api/notification-recipients/me",params="view=summary")
    public ApiResponse<PageResponse<Map<String,Object>>> inbox(@RequestParam Map<String,String> filters,Authentication auth) {
        var scoped=new HashMap<>(filters); scoped.put("mine","true");
        return new ApiResponse<>(reads.page("notification-recipients",scoped,auth),"Inbox found");
    }

    @GetMapping("/api/dashboard")
    public ApiResponse<Map<String,Object>> dashboard(Authentication auth) { return new ApiResponse<>(reads.dashboard(auth),"Dashboard found"); }

    @GetMapping("/api/lessons/{id}/summary")
    public ApiResponse<Map<String,Object>> lesson(@PathVariable String id,Authentication auth) { return new ApiResponse<>(reads.lessonSummary(id,auth),"Lesson summary"); }

    @GetMapping("/api/{resource:lessons|activities}/{id}/files/summary")
    public ApiResponse<Map<String,Object>> files(@PathVariable String resource,@PathVariable String id,Authentication auth) { return new ApiResponse<>(reads.fileSummary(resource,id,auth),"File summary"); }

    @GetMapping("/api/organizations/{id}/summary")
    public ApiResponse<Map<String,Object>> organization(@PathVariable String id,Authentication auth) { return new ApiResponse<>(reads.organizationSummary(id,auth),"Organization summary"); }

    @GetMapping("/api/contracts/{id}/document-types/{typeId}/summary")
    public ApiResponse<Map<String,Object>> versions(@PathVariable String id,@PathVariable short typeId,Authentication auth) { return new ApiResponse<>(reads.versionSummary(id,typeId,auth),"Version summary"); }

    @GetMapping("/api/lookups/document-types")
    public ApiResponse<PageResponse<Map<String,Object>>> documentTypes(@RequestParam Map<String,String> filters,Authentication auth) {
        var limited=new HashMap<>(filters); limited.put("size",String.valueOf(PortalReadService.integer(filters,"size",5,1,5)));
        var page=reads.page("document-types",limited,auth);
        var options=page.content().stream().map(item->Map.<String,Object>of("id",item.get("id").toString(),"label",item.get("name"))).toList();
        return new ApiResponse<>(new PageResponse<>(options,page.page(),page.size(),page.totalElements(),page.totalPages(),page.first(),page.last()),"Document types found");
    }
}
