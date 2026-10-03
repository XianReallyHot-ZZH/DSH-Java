package io.github.xianreallyhotzzh.dsh.trigger.http.command;

import io.github.xianreallyhotzzh.dsh.api.IWorkspaceCommandApi;
import io.github.xianreallyhotzzh.dsh.api.dto.CreateWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.RenameWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.ReorderWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向 Agent UI 暴露工作区写操作。 */
@RestController
@RequestMapping("/api/agent")
public class WorkspaceCommandController {

    private final IWorkspaceCommandApi workspaceCommandApi;

    public WorkspaceCommandController(IWorkspaceCommandApi workspaceCommandApi) {
        this.workspaceCommandApi = workspaceCommandApi;
    }

    @PostMapping("/workspaces")
    public Response<List<WorkspaceEntryResponseDTO>> createWorkspace(@RequestBody CreateWorkspaceRequestDTO request) {
        return workspaceCommandApi.createWorkspace(request);
    }

    @DeleteMapping("/workspaces/{name}")
    public Response<List<WorkspaceEntryResponseDTO>> deleteWorkspace(@PathVariable("name") String name) {
        return workspaceCommandApi.deleteWorkspace(name);
    }

    @PatchMapping("/workspaces/{name}")
    public Response<List<WorkspaceEntryResponseDTO>> renameWorkspace(@PathVariable("name") String name,
                                                                     @RequestBody RenameWorkspaceRequestDTO request) {
        return workspaceCommandApi.renameWorkspace(name, request);
    }

    @PutMapping("/workspaces/order")
    public Response<List<WorkspaceEntryResponseDTO>> reorderWorkspaces(@RequestBody ReorderWorkspaceRequestDTO request) {
        return workspaceCommandApi.reorderWorkspaces(request);
    }
}
