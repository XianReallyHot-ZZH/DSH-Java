package io.github.xianreallyhotzzh.dsh.trigger.service.command;

import io.github.xianreallyhotzzh.dsh.api.IWorkspaceCommandApi;
import io.github.xianreallyhotzzh.dsh.api.dto.CreateWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.RenameWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.ReorderWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import io.github.xianreallyhotzzh.dsh.trigger.service.workspace.WorkspaceRegistryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 工作区命令适配器。
 * <p>
 * 执行流程：
 * 1. 接收工作区新增、删除、重命名、排序请求；
 * 2. 调用工作区注册服务修改磁盘状态；
 * 3. 返回最新工作区列表给前端。
 */
@Service
public class WorkspaceCommandApi implements IWorkspaceCommandApi {

    private final WorkspaceRegistryService workspaceRegistryService;

    public WorkspaceCommandApi(WorkspaceRegistryService workspaceRegistryService) {
        this.workspaceRegistryService = workspaceRegistryService;
    }

    @Override
    public Response<List<WorkspaceEntryResponseDTO>> createWorkspace(CreateWorkspaceRequestDTO request) {
        return execute(() -> workspaceRegistryService.createWorkspace(request));
    }

    @Override
    public Response<List<WorkspaceEntryResponseDTO>> deleteWorkspace(String name) {
        return execute(() -> workspaceRegistryService.deleteWorkspace(name));
    }

    @Override
    public Response<List<WorkspaceEntryResponseDTO>> renameWorkspace(String name, RenameWorkspaceRequestDTO request) {
        return execute(() -> workspaceRegistryService.renameWorkspace(name, request));
    }

    @Override
    public Response<List<WorkspaceEntryResponseDTO>> reorderWorkspaces(ReorderWorkspaceRequestDTO request) {
        return execute(() -> workspaceRegistryService.reorderWorkspaces(request));
    }

    private <T> Response<T> execute(java.util.concurrent.Callable<T> action) {
        try {
            return Response.success(action.call());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (Exception exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
