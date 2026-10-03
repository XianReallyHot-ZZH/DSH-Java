package io.github.xianreallyhotzzh.dsh.trigger.service.query;

import io.github.xianreallyhotzzh.dsh.api.IWorkspaceQueryApi;
import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import io.github.xianreallyhotzzh.dsh.trigger.service.workspace.WorkspaceRegistryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 工作区查询适配器。
 * <p>
 * 执行流程：
 * 1. 调用工作区注册服务读取当前目录与顺序信息；
 * 2. 组装为 `WorkspaceEntryResponse`；
 * 3. 返回给控制器展示。
 */
@Service
public class WorkspaceQueryApi implements IWorkspaceQueryApi {

    private final WorkspaceRegistryService workspaceRegistryService;

    public WorkspaceQueryApi(WorkspaceRegistryService workspaceRegistryService) {
        this.workspaceRegistryService = workspaceRegistryService;
    }

    @Override
    public Response<List<WorkspaceEntryResponseDTO>> getWorkspaces() {
        return execute(workspaceRegistryService::listWorkspaces);
    }

    @Override
    public Response<List<String>> listDirectories(String path) {
        return execute(() -> workspaceRegistryService.listDirectories(path));
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
