package io.yak.ops.business.task.log;

import io.yak.ops.common.bean.vo.task.EventVO;
import java.util.List;

/**
 * Task实例生命周期事件只读入口，尚不提供Worker日志文件的内容读取。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public interface LogService {

    /** 返回产品结构化事件，不把Event误当作运行时详细日志。 */
    List<EventVO> queryEvents(String instanceId);
}
