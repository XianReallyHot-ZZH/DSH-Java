package io.github.xianreallyhotzzh.dsh.api;

import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;

import java.util.List;

/** 工作区查询契约（由 trigger 层实现）。 */
public interface IWorkspaceQueryApi {

    Response<List<WorkspaceEntryResponseDTO>> getWorkspaces();

    Response<List<String>> listDirectories(String path);
}
