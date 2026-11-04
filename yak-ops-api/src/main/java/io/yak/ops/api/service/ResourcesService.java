package io.yak.ops.api.service;

import io.yak.ops.api.dto.resources.CreateDirectoryRequest;
import io.yak.ops.api.dto.resources.CreateFileFromContentRequest;
import io.yak.ops.api.dto.resources.CreateFileRequest;
import io.yak.ops.api.dto.resources.DeleteResourceRequest;
import io.yak.ops.api.dto.resources.DownloadFileRequest;
import io.yak.ops.api.dto.resources.FetchFileContentRequest;
import io.yak.ops.api.dto.resources.PagingResourceItemRequest;
import io.yak.ops.api.dto.resources.RenameDirectoryRequest;
import io.yak.ops.api.dto.resources.RenameFileRequest;
import io.yak.ops.api.dto.resources.ResourceComponent;
import io.yak.ops.api.dto.resources.UpdateFileFromContentRequest;
import io.yak.ops.api.dto.resources.UpdateFileRequest;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.ResourceItemVO;
import io.yak.ops.api.vo.resources.FetchFileContentResponse;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.storage.api.StorageEntity;
import io.yak.ops.spi.enums.ResourceType;

import java.util.List;

import javax.servlet.http.HttpServletResponse;

public interface ResourcesService {

    /**
     * Create a new directory in the resource storage, if the directory already exists will throw exception
     */
    void createDirectory(CreateDirectoryRequest createDirectoryRequest);

    /**
     * Rename the directory in the resource storage, if the origin directory not exists or the new directory already exists will throw exception.
     * <p> If the origin directory is empty will only update the directory name.
     * <p> If the origin directory is not empty will move all the files and directories to the new directory.
     * <p> After update the origin directory will be deleted.
     */
    void renameDirectory(RenameDirectoryRequest renameDirectoryRequest);

    /**
     * Upload a new file to the resource storage, if the file already exists will throw exception
     */
    void createFile(CreateFileRequest createFileRequest);

    /**
     * Update the file in the resource storage, if the origin file not exists or the new file already exists will throw exception.
     * <p> If the new file is empty will only update the file name.
     * <p> If the new file is not empty will update the file content and name.
     * <p> After update the origin file will be deleted.
     */
    void updateFile(UpdateFileRequest updateFileRequest);

    /**
     * Rename the file in the resource storage, if the origin file not exists or the new file already exists will throw exception.
     */
    void renameFile(RenameFileRequest renameFileRequest);

    /**
     * Create a new file in the resource storage, if the file already exists will throw exception.
     * Different with {@link ResourcesService#createFile(CreateFileRequest)} this method will create a new file with the given content.
     */
    void createFileFromContent(CreateFileFromContentRequest createFileFromContentRequest);

    /**
     * Update the file content.
     */
    void updateFileFromContent(UpdateFileFromContentRequest updateFileContentRequest);

    /**
     * Paging query resource items.
     * <p>If the login user is not admin will only query the resource items that under the user's tenant.
     * <p>If the login user is admin and {@link PagingResourceItemRequest##resourceAbsolutePath} is null will return all the resource items.
     */
    PageInfo<ResourceItemVO> pagingResourceItem(PagingResourceItemRequest pagingResourceItemRequest);

    /**
     * Query the resource file items by the given resource type and program type.
     */
    List<ResourceComponent> queryResourceFiles(User loginUser, ResourceType type);

    /**
     * Delete the resource item.
     * <p>If the resource item is a directory will delete all the files and directories under the directory.
     * <p>If the resource item is a file will delete the file.
     * <p>If the resource item not exists will throw exception.
     */
    void delete(DeleteResourceRequest deleteResourceRequest);

    /**
     * Fetch the file content.
     */
    FetchFileContentResponse fetchResourceFileContent(FetchFileContentRequest fetchFileContentRequest);

    void downloadResource(HttpServletResponse response, DownloadFileRequest downloadFileRequest);

    /**
     * Get resource by given resource type and file name.
     * Useful in Python API create task which need workflowDefinition information.
     *
     * @param userName user who query resource
     * @param fileName file name of the resource
     */
    StorageEntity queryFileStatus(String userName, String fileName) throws Exception;

    String queryResourceBaseDir(User loginUser, ResourceType type);

}
