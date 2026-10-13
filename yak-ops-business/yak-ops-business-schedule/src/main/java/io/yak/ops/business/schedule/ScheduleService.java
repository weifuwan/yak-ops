package io.yak.ops.business.schedule;

import io.yak.ops.common.bean.dto.schedule.CreateScheduleDTO;
import io.yak.ops.common.bean.dto.schedule.SchedulePreviewDTO;
import io.yak.ops.common.bean.dto.schedule.ScheduleQueryDTO;
import io.yak.ops.common.bean.dto.schedule.UpdateScheduleDTO;
import io.yak.ops.common.bean.vo.schedule.SchedulePreviewVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleSummaryVO;
import io.yak.ops.common.bean.vo.schedule.ScheduleVO;
import io.yak.ops.common.page.PageData;

/** Workspace-authorized schedule configuration without physical execution ownership. */
public interface ScheduleService {
    /**
     * Creates one disabled time rule for an existing target, including unpublished targets.
     * @param workspaceId authorized Workspace
     * @param dto validated target and time rule
     * @param operatorId authenticated actor
     * @return saved configuration and target eligibility
     */
    ScheduleVO create(String workspaceId, CreateScheduleDTO dto, String operatorId);

    /**
     * Changes the time rule of a disabled configuration under Definition/Schedule locks.
     * @param workspaceId authorized Workspace
     * @param id schedule identity
     * @param dto expected revision and replacement time rule
     * @param operatorId authenticated actor
     * @return saved configuration; unchanged rules retain their revision
     */
    ScheduleVO update(String workspaceId, String id, UpdateScheduleDTO dto, String operatorId);

    /**
     * Reads a configuration and current target eligibility without opening external connections.
     * @param workspaceId authorized Workspace
     * @param id schedule identity
     * @return configuration with structured eligibility reason
     */
    ScheduleVO query(String workspaceId, String id);

    /**
     * Returns summaries without per-row target or engine queries.
     * @param workspaceId authorized Workspace
     * @param dto bounded page filters
     * @return desired-state summaries in stable order
     */
    PageData<ScheduleSummaryVO> queryPage(String workspaceId, ScheduleQueryDTO dto);

    /**
     * Deletes configuration intent while retaining identity and history.
     * @param workspaceId authorized Workspace
     * @param id schedule identity
     * @param revision expected revision
     * @param operatorId authenticated actor
     */
    void delete(String workspaceId, String id, long revision, String operatorId);

    /**
     * Calculates future times without a saved target or Quartz registration.
     * @param dto time rule and optional exclusive UTC lower bound
     * @return effective lower bound and up to five UTC times
     */
    SchedulePreviewVO preview(SchedulePreviewDTO dto);
}
