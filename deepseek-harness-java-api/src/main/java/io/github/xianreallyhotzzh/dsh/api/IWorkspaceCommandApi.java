package io.github.xianreallyhotzzh.dsh.api;

import io.github.xianreallyhotzzh.dsh.api.dto.CreateWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.RenameWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.ReorderWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;

import java.util.List;

/** 工作区写操作契约（由 trigger 层实现）。 */
public interface IWorkspaceCommandApi {

    Response<List<WorkspaceEntryResponseDTO>> createWorkspace(CreateWorkspaceRequestDTO request);

    Response<List<WorkspaceEntryResponseDTO>> deleteWorkspace(String name);

    Response<List<WorkspaceEntryResponseDTO>> renameWorkspace(String name, RenameWorkspaceRequestDTO request);

    Response<List<WorkspaceEntryResponseDTO>> reorderWorkspaces(ReorderWorkspaceRequestDTO request);
}
