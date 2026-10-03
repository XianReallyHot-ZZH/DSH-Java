package io.github.xianreallyhotzzh.dsh.trigger.http.query;

import io.github.xianreallyhotzzh.dsh.api.IWorkspaceQueryApi;
import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端工作区查询请求；
 * 2. 调用工作区查询适配器读取目录与排序信息；
 * 3. 返回给前端用于下拉选择和目录浏览。
 */
@RestController
@RequestMapping("/api/agent")
public class WorkspaceQueryController {

    private final IWorkspaceQueryApi workspaceQueryApi;

    public WorkspaceQueryController(IWorkspaceQueryApi workspaceQueryApi) {
        this.workspaceQueryApi = workspaceQueryApi;
    }

    @GetMapping("/workspaces")
    public Response<List<WorkspaceEntryResponseDTO>> getWorkspaces() {
        return workspaceQueryApi.getWorkspaces();
    }

    @GetMapping("/workspaces/list")
    public Response<List<String>> listDirectories(@RequestParam(value = "path", required = false) String path) {
        return workspaceQueryApi.listDirectories(path);
    }
}
