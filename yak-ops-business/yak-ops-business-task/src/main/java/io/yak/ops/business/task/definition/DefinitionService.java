package io.yak.ops.business.task.definition;

import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.bean.dto.task.PublishDefinitionDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionPublicationVO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionSummaryVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.page.PagingData;

/** One generic Task Definition business entrypoint, independent of plugin type. */
public interface DefinitionService {
    /** Creates a Workspace-owned definition with its first immutable parameter version. */
    DefinitionVO create(CreateDefinitionDTO dto, String operatorId);

    /**
     * Saves metadata and parameters at the expected revision without changing the publication.
     * @param id Workspace-owned task identifier
     * @param dto edited content and expected revision
     * @param operatorId actor saving the task
     * @return latest saved task, including its unchanged publication
     */
    DefinitionVO update(String id, UpdateDefinitionDTO dto, String operatorId);

    DefinitionVO query(String id);

    PagingData<DefinitionVO> queryPage(DefinitionQueryDTO dto);

    /**
     * Reads version metadata without loading every historical parameter payload.
     * @param id task in the current authorized Workspace
     * @param pageNo one-based page number
     * @param pageSize page size, at most 200
     * @return descending version metadata page
     */
    PagingData<DefinitionVersionSummaryVO> queryVersions(String id, int pageNo, int pageSize);

    /**
     * Reads one immutable parameter snapshot in the current Workspace.
     * @param id owning task identifier
     * @param version saved parameter version
     * @return snapshot and its save audit information
     */
    DefinitionVersionVO queryVersion(String id, int version);

    /**
     * Reads successful publication receipts in the current Workspace.
     * @param id owning task identifier
     * @param pageNo one-based page number
     * @param pageSize page size, at most 200
     * @return descending publication history page
     */
    PagingData<DefinitionPublicationVO> queryPublications(String id, int pageNo, int pageSize);

    /**
     * Validates saved parameters before atomically publishing the selected version and saved diagnostics.
     * @param id task in the current authorized Workspace
     * @param dto selected version, expected revision and stable retry key
     * @param operatorId actor publishing the task
     * @return current saved task and its resulting publication
     */
    DefinitionVO publish(String id, PublishDefinitionDTO dto, String operatorId);

    /** Prevents new executions without deleting existing immutable versions. */
    DefinitionVO unpublish(String id, String operatorId);

    /** Logically deletes a Workspace-owned definition while preserving version history. */
    void delete(String id, String operatorId);
}
