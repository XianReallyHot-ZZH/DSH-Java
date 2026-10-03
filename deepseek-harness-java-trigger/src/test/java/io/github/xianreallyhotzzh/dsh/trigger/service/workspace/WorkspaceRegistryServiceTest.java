package io.github.xianreallyhotzzh.dsh.trigger.service.workspace;

import io.github.xianreallyhotzzh.dsh.api.dto.CreateWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.ReorderWorkspaceRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.WorkspaceEntryResponseDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 工作区注册服务的行为钉子：列表/建/删/排序可用，排序文件持久化（@TempDir 隔离）。
 * DoD 的 `GET /api/agent/workspaces` 真服务验收与之互为镜像。
 */
class WorkspaceRegistryServiceTest {

    @TempDir
    Path root;

    @Test
    void listCreateDeleteWorkspacesRoundTrip() throws IOException {
        WorkspaceRegistryService service = newService();

        // 空根目录：列表为空（00000 由信封层保证，这里钉数据面）
        assertTrue(service.listWorkspaces().isEmpty());

        // 建两个工作区：磁盘目录出现，列表包含两者（无顺序文件时目录枚举顺序不保证）
        service.createWorkspace(new CreateWorkspaceRequestDTO("alpha"));
        service.createWorkspace(new CreateWorkspaceRequestDTO("beta"));
        assertEquals(java.util.Set.of("alpha", "beta"),
                java.util.Set.copyOf(service.listWorkspaces().stream()
                        .map(WorkspaceEntryResponseDTO::name).toList()));
        assertTrue(Files.isDirectory(root.resolve("alpha")));

        // 删除：目录移除且顺序文件同步清理
        service.deleteWorkspace("alpha");
        assertEquals(List.of("beta"),
                service.listWorkspaces().stream().map(WorkspaceEntryResponseDTO::name).toList());
        assertFalse(Files.exists(root.resolve("alpha")));

        // 路径分隔符会被替换：不能借名字逃出根目录
        service.createWorkspace(new CreateWorkspaceRequestDTO("a/b"));
        assertTrue(Files.isDirectory(root.resolve("a_b")));
    }

    @Test
    void reorderPersistsOrderAcrossServiceInstances() throws IOException {
        WorkspaceRegistryService service = newService();
        service.createWorkspace(new CreateWorkspaceRequestDTO("alpha"));
        service.createWorkspace(new CreateWorkspaceRequestDTO("beta"));
        service.createWorkspace(new CreateWorkspaceRequestDTO("gamma"));

        // 前端拖拽排序：逆序持久化到 .workspace-order.json
        service.reorderWorkspaces(new ReorderWorkspaceRequestDTO(List.of("gamma", "beta", "alpha")));

        assertEquals(List.of("gamma", "beta", "alpha"),
                service.listWorkspaces().stream().map(WorkspaceEntryResponseDTO::name).toList());

        // 重启（新服务实例）后顺序仍在：顺序文件是排序的唯一事实源
        WorkspaceRegistryService restarted = newService();
        assertEquals(List.of("gamma", "beta", "alpha"),
                restarted.listWorkspaces().stream().map(WorkspaceEntryResponseDTO::name).toList());
    }

    @Test
    void blankNameIsRejectedAsInvalidArgument() {
        WorkspaceRegistryService service = newService();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.createWorkspace(new CreateWorkspaceRequestDTO("  ")));
    }

    private WorkspaceRegistryService newService() {
        return new WorkspaceRegistryService("", root.toString());
    }
}
