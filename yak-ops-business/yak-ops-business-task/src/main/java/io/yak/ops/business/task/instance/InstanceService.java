package io.yak.ops.business.task.instance;

import io.yak.ops.common.bean.dto.task.InstanceQueryDTO;
import io.yak.ops.common.bean.vo.task.AttemptVO;
import io.yak.ops.common.bean.vo.task.InstanceVO;
import io.yak.ops.common.page.PagingData;
import java.util.List;

/**
 * 通用Task实例和Attempt历史的只读入口；执行控制仍属现有DATA_SYNC业务。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface InstanceService {

    InstanceVO query(String id);

    PagingData<InstanceVO> queryPage(InstanceQueryDTO query);

    List<AttemptVO> queryAttempts(String instanceId);
}
