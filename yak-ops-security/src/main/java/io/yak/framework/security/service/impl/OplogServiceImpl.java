package io.yak.framework.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.security.common.dto.oplog.OplogDTO;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.entity.Oplog;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.oplog.OplogOptionsVO;
import io.yak.framework.security.common.vo.oplog.OplogVO;
import io.yak.framework.security.dao.OplogDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.OplogService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.NetworkUtil;
import io.yak.framework.security.util.SensitiveDataSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现类。
 *
 * <p>负责保存操作者、操作目标、操作内容及客户端 IP，
 * 用于安全审计和操作追踪。</p>
 *
 * @author weifuwan
 */
@Service("yakSecurityOplogServiceImpl")
public class OplogServiceImpl implements OplogService {

    private static final String SYSTEM_OPERATOR_IP = "0.0.0.0";
    private static final String DEFAULT_OPERATE_PAGE = "SYSTEM";
    private static final String DEFAULT_OPERATION_METHOD = "SERVICE";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private final OplogDao oplogDao;

    public OplogServiceImpl(OplogDao oplogDao) {
        this.oplogDao = oplogDao;
    }

    @Override
    public PagingData<OplogVO> getOplogPage(
            OplogQueryDTO queryDTO) {

        if (queryDTO == null) {
            queryDTO = new OplogQueryDTO();
        }

        normalizeQuery(queryDTO);

        IPage<Oplog> oplogPage =
                oplogDao.selectPageWithoutDetail(queryDTO);

        if (oplogPage == null) {
            throw new IllegalStateException(
                    "查询操作日志分页数据失败");
        }

        List<Oplog> oplogList = oplogPage.getRecords();
        if (CollectionUtils.isEmpty(oplogList)) {
            return toPagingData(
                    new ArrayList<>(),
                    oplogPage);
        }

        List<OplogVO> oplogVOList =
                new ArrayList<>(oplogList.size());

        for (Oplog oplog : oplogList) {
            OplogVO oplogVO = convertToVO(oplog);
            if (oplogVO != null) {
                oplogVOList.add(oplogVO);
            }
        }

        return toPagingData(
                oplogVOList,
                oplogPage);
    }

    @Override
    public OplogVO getOplogDetailByOplogId(
            Long oplogId) {

        if (oplogId == null) {
            throw new YakSecurityException(
                    ResultCode.OPLOG_NOT_EXIST);
        }

        Oplog oplog = oplogDao.selectByOplogId(oplogId);
        if (oplog == null) {
            throw new YakSecurityException(
                    ResultCode.OPLOG_NOT_EXIST);
        }

        return convertToVO(oplog);
    }

    @Override
    public OplogOptionsVO getOptions() {
        OplogOptionsVO options = new OplogOptionsVO();
        options.setOperateTypes(
                cleanOptions(oplogDao.listOperateType()));
        options.setOperatePages(
                cleanOptions(oplogDao.listOperatePage()));
        options.setOperationMethods(
                cleanOptions(oplogDao.listOperationMethods()));
        options.setTargetTypes(
                cleanOptions(oplogDao.listTargetType()));
        return options;
    }

    @Override
    public List<String> listTargetType() {
        return cleanOptions(oplogDao.listTargetType());
    }

    @Override
    @Transactional(
            transactionManager =
                    "yakSecurityTransactionManager",
            rollbackFor = Exception.class)
    public Long saveOplog(OplogDTO oplogDTO) {
        if (oplogDTO == null) {
            throw new IllegalArgumentException(
                    "操作日志信息不能为空");
        }

        Oplog oplog = CopyBeanUtil.copy(
                oplogDTO,
                Oplog.class);
        if (oplog == null) {
            throw new IllegalStateException(
                    "操作日志对象转换失败");
        }

        /*
         * 不直接修改调用方传入的 DTO，
         * 只对即将持久化的实体执行敏感数据清理。
         */
        oplog.setOperator(
                sanitize(oplogDTO.getOperator()));
        oplog.setOperatePage(
                sanitize(defaultIfBlank(
                        oplogDTO.getOperatePage(),
                        DEFAULT_OPERATE_PAGE)));
        oplog.setOperateType(
                sanitize(oplogDTO.getOperateType()));
        oplog.setTarget(
                sanitize(oplogDTO.getTarget()));
        oplog.setTargetType(
                sanitize(oplogDTO.getTargetType()));
        oplog.setDetail(
                sanitize(oplogDTO.getDetail()));
        oplog.setOperationMethods(
                sanitize(defaultIfBlank(
                        oplogDTO.getOperationMethods(),
                        DEFAULT_OPERATION_METHOD)));
        oplog.setOperatorIp(
                sanitize(
                        NetworkUtil.getRealIpAddressOrDefault(
                                SYSTEM_OPERATOR_IP)));

        oplogDao.insert(oplog);
        if (oplog.getId() == null) {
            throw new IllegalStateException(
                    "保存操作日志后未生成日志 ID");
        }

        return oplog.getId();
    }

    private void normalizeQuery(OplogQueryDTO queryDTO) {
        if (queryDTO.getPage() < 1) {
            queryDTO.setPage(DEFAULT_PAGE);
        }

        if (queryDTO.getSize() < 1) {
            queryDTO.setSize(DEFAULT_PAGE_SIZE);
        } else if (queryDTO.getSize() > MAX_PAGE_SIZE) {
            queryDTO.setSize(MAX_PAGE_SIZE);
        }

        Long startTime = queryDTO.getStartTime();
        Long endTime = queryDTO.getEndTime();
        if (startTime != null
                && endTime != null
                && startTime > endTime) {
            throw new YakSecurityException(
                    ResultCode.PARAM_ERROR);
        }
    }

    private OplogVO convertToVO(Oplog oplog) {
        if (oplog == null) {
            return null;
        }

        OplogVO oplogVO = CopyBeanUtil.copy(
                oplog,
                OplogVO.class);
        if (oplogVO == null) {
            throw new IllegalStateException(
                    "操作日志视图对象转换失败");
        }

        oplogVO.setCreateTime(oplog.getCreateTime());
        oplogVO.setUpdateTime(oplog.getUpdateTime());
        return oplogVO;
    }

    private List<String> cleanOptions(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<>();
        }

        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static <T> PagingData<T> toPagingData(
            List<T> records,
            IPage<?> page) {
        return PagingData.from(
                new PageData<>(
                        records,
                        page.getTotal(),
                        page.getPages(),
                        page.getCurrent(),
                        page.getSize()));
    }

    private static String defaultIfBlank(
            String value,
            String defaultValue) {
        return StringUtils.hasText(value)
                ? value
                : defaultValue;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }

        return SensitiveDataSanitizer.sanitize(value);
    }
}
