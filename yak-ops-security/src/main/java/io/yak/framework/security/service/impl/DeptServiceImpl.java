package io.yak.framework.security.service.impl;

import io.yak.framework.security.common.dto.dept.DeptDTO;
import io.yak.framework.security.common.dto.dept.DeptSaveDTO;
import io.yak.framework.security.common.entity.dept.Dept;
import io.yak.framework.security.common.entity.dept.DeptBrief;
import io.yak.framework.security.common.entity.user.UserBrief;
import io.yak.framework.security.common.enums.ResultCode;
import io.yak.framework.security.common.vo.dept.DeptBriefVO;
import io.yak.framework.security.common.vo.dept.DeptDeleteCheckVO;
import io.yak.framework.security.common.vo.dept.DeptTreeVO;
import io.yak.framework.security.common.vo.dept.DeptVO;
import io.yak.framework.security.dao.DeptDao;
import io.yak.framework.security.dao.UserDao;
import io.yak.framework.security.exception.YakSecurityException;
import io.yak.framework.security.service.DeptService;
import io.yak.framework.security.util.CopyBeanUtil;
import io.yak.framework.security.util.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 部门服务实现类。
 *
 * @author weifuwan
 */
@Service("yakSecurityDeptServiceImpl")
public class DeptServiceImpl implements DeptService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DeptServiceImpl.class);

    /**
     * 虚拟根部门 ID。
     */
    private static final Long ROOT_DEPT_ID = 0L;

    /**
     * 部门 ID 随机数字长度。
     */
    private static final int DEPT_ID_RANDOM_LENGTH = 5;

    /**
     * 部门 ID 时间部分放大倍数。
     */
    private static final long DEPT_ID_FACTOR = 100_000L;

    /**
     * 单次生成部门 ID 的最大重试次数。
     */
    private static final int MAX_ID_RETRY_COUNT = 100;

    /**
     * 部门名称最大长度。
     */
    private static final int MAX_DEPT_NAME_LENGTH = 64;

    /**
     * 部门描述最大长度。
     */
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final DeptDao deptDao;

    private final UserDao userDao;


    /**
     * 创建部门服务。
     *
     * @param deptDao 部门数据访问对象
     * @param userDao 用户数据访问对象
     */
    public DeptServiceImpl(
            DeptDao deptDao,
            UserDao userDao) {

        this.deptDao = deptDao;
        this.userDao = userDao;
    }

    /**
     * 构建完整部门树。
     *
     * @return 部门树
     */
    @Override
    public DeptTreeVO buildDeptTree() {
        List<Dept> deptList =
                deptDao.selectAllAndAscOrderByLevel();

        DeptTreeVO root = DeptTreeVO.builder()
                .id(ROOT_DEPT_ID)
                .leaf(Boolean.FALSE)
                .childList(new ArrayList<>())
                .build();

        Map<Long, DeptTreeVO> deptTreeMap =
                new HashMap<>();

        deptTreeMap.put(ROOT_DEPT_ID, root);

        if (CollectionUtils.isEmpty(deptList)) {
            return root;
        }

        for (Dept dept : deptList) {
            if (dept == null || dept.getId() == null) {
                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }

            DeptTreeVO deptTreeVO =
                    CopyBeanUtil.copy(
                            dept,
                            DeptTreeVO.class);

            if (deptTreeVO == null) {
                throw new IllegalStateException(
                        "部门树对象转换失败");
            }

            if (!Boolean.TRUE.equals(
                    deptTreeVO.getLeaf())) {

                deptTreeVO.setChildList(
                        new ArrayList<>());
            }

            Long parentId =
                    dept.getParentId() == null
                            ? ROOT_DEPT_ID
                            : dept.getParentId();

            DeptTreeVO parent =
                    deptTreeMap.get(parentId);

            if (parent == null) {
                LOGGER.error(
                        "构建部门树失败，未找到父部门，"
                                + "部门ID={}，父部门ID={}",
                        dept.getId(),
                        parentId);

                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }

            if (parent.getChildList() == null) {
                parent.setChildList(
                        new ArrayList<>());
            }

            parent.setLeaf(Boolean.FALSE);
            parent.getChildList().add(deptTreeVO);

            DeptTreeVO previous =
                    deptTreeMap.put(
                            deptTreeVO.getId(),
                            deptTreeVO);

            if (previous != null) {
                LOGGER.error(
                        "构建部门树失败，存在重复部门ID，"
                                + "部门ID={}",
                        deptTreeVO.getId());

                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }
        }

        return root;
    }

    /**
     * 根据部门 ID 查询部门详情。
     *
     * @param deptId 部门标识
     * @return 部门详情
     */
    @Override
    public DeptVO getDeptDetail(Long deptId) {
        Dept dept = requireDept(deptId);

        List<Dept> childDeptList =
                getDirectChildDeptList(deptId);

        List<UserBrief> userList =
                getDirectUserList(deptId);

        DeptVO deptVO =
                CopyBeanUtil.copy(
                        dept,
                        DeptVO.class);

        if (deptVO == null) {
            throw new IllegalStateException(
                    "部门详情对象转换失败");
        }

        deptVO.setLeaf(childDeptList.isEmpty());
        deptVO.setChildDeptCount(
                childDeptList.size());
        deptVO.setUserCount(
                userList.size());

        return deptVO;
    }

    /**
     * 新增部门。
     *
     * @param deptSaveDTO 部门信息
     */
    @Override
    @Transactional(
            transactionManager =
                    "yakSecurityTransactionManager",
            rollbackFor = Exception.class)
    public void createDept(
            DeptSaveDTO deptSaveDTO) {

        String deptName =
                normalizeDeptName(deptSaveDTO);

        String description =
                normalizeDescription(
                        deptSaveDTO.getDescription());

        Long parentId =
                normalizeParentId(
                        deptSaveDTO.getParentId());

        int level =
                resolveDeptLevel(parentId);

        validateSiblingDeptName(
                deptName,
                parentId,
                null);

        Dept dept = new Dept();
        dept.setDeptName(deptName);
        dept.setDescription(description);
        dept.setParentId(parentId);
        dept.setLevel(level);
        dept.setLeaf(Boolean.TRUE);

        deptDao.insert(dept);

        /*
         * 新部门创建成功后，其上级部门必然不再是叶子节点。
         */
        if (!isRootDept(parentId)) {
            deptDao.updateLeaf(
                    parentId,
                    false);
        }

        LOGGER.info(
                "新增部门成功，部门ID={}，部门名称={}，上级部门ID={}",
                dept.getId(),
                deptName,
                parentId);
    }

    /**
     * 编辑部门。
     *
     * @param deptSaveDTO 部门信息
     */
    @Override
    @Transactional(
            transactionManager =
                    "yakSecurityTransactionManager",
            rollbackFor = Exception.class)
    public void updateDept(
            DeptSaveDTO deptSaveDTO) {

        if (deptSaveDTO == null
                || deptSaveDTO.getId() == null) {

            throw new YakSecurityException(
                    "部门 ID 不能为空");
        }

        Long deptId = deptSaveDTO.getId();

        Dept currentDept =
                requireDept(deptId);

        String deptName =
                normalizeDeptName(deptSaveDTO);

        String description =
                normalizeDescription(
                        deptSaveDTO.getDescription());

        Long newParentId =
                normalizeParentId(
                        deptSaveDTO.getParentId());

        /*
         * 查询当前部门以及所有下级部门。
         * 新上级部门不能是当前部门本身，也不能是其下级部门。
         */
        List<Long> subtreeDeptIdList =
                getDeptIdListByParentId(deptId);

        if (subtreeDeptIdList.contains(
                newParentId)) {

            throw new YakSecurityException(
                    "上级部门不能选择当前部门或其下级部门");
        }

        int newLevel =
                resolveDeptLevel(newParentId);

        validateSiblingDeptName(
                deptName,
                newParentId,
                deptId);

        if (currentDept.getLevel() == null) {
            throw new YakSecurityException(
                    ResultCode.DEPT_DATA_ERROR);
        }

        int levelOffset =
                newLevel - currentDept.getLevel();

        Long oldParentId =
                normalizeParentId(
                        currentDept.getParentId());

        /*
         * 在更新当前部门前读取全部部门，
         * 用于更新整个子树的层级。
         */
        List<Dept> allDeptList =
                deptDao.selectAllAndAscOrderByLevel();

        boolean hasChildren =
                deptDao.countByParentId(deptId) > 0;

        Dept updateDept = new Dept();
        updateDept.setId(deptId);
        updateDept.setDeptName(deptName);
        updateDept.setDescription(description);
        updateDept.setParentId(newParentId);
        updateDept.setLevel(newLevel);
        updateDept.setLeaf(!hasChildren);

        deptDao.update(updateDept);

        if (levelOffset != 0
                && !CollectionUtils.isEmpty(
                allDeptList)) {

            Set<Long> subtreeDeptIdSet =
                    new HashSet<>(
                            subtreeDeptIdList);

            for (Dept childDept : allDeptList) {
                if (childDept == null
                        || childDept.getId() == null
                        || Objects.equals(
                        deptId,
                        childDept.getId())
                        || !subtreeDeptIdSet.contains(
                        childDept.getId())) {

                    continue;
                }

                if (childDept.getLevel() == null) {
                    throw new YakSecurityException(
                            ResultCode.DEPT_DATA_ERROR);
                }

                deptDao.updateLevel(
                        childDept.getId(),
                        childDept.getLevel()
                                + levelOffset);
            }
        }

        if (!Objects.equals(
                oldParentId,
                newParentId)) {

            /*
             * 新上级部门增加了一个直属子部门，
             * 因此一定不是叶子部门。
             */
            if (!isRootDept(newParentId)) {
                deptDao.updateLeaf(
                        newParentId,
                        false);
            }

            /*
             * 原上级部门失去当前部门后，
             * 需要重新计算叶子节点状态。
             */
            refreshParentLeaf(oldParentId);
        }

        LOGGER.info(
                "编辑部门成功，部门ID={}，原上级部门ID={}，新上级部门ID={}",
                deptId,
                oldParentId,
                newParentId);
    }

    /**
     * 执行部门删除前检查。
     *
     * @param deptId 部门标识
     * @return 删除检查结果
     */
    @Override
    public DeptDeleteCheckVO checkBeforeDelete(
            Long deptId) {

        requireDept(deptId);

        List<Dept> childDeptList =
                getDirectChildDeptList(deptId);

        List<UserBrief> userList =
                getDirectUserList(deptId);

        List<String> childDeptNameList =
                new ArrayList<>();

        for (Dept childDept : childDeptList) {
            childDeptNameList.add(
                    getDeptDisplayName(childDept));
        }

        List<String> userNameList =
                getUserDisplayNameList(
                        userList);

        DeptDeleteCheckVO result =
                new DeptDeleteCheckVO();

        result.setDeptId(deptId);
        result.setChildDeptNameList(
                childDeptNameList);
        result.setUserNameList(
                userNameList);
        result.setDeletable(
                childDeptNameList.isEmpty()
                        && userNameList.isEmpty());

        return result;
    }

    /**
     * 删除部门。
     *
     * @param deptId 部门标识
     */
    @Override
    @Transactional(
            transactionManager =
                    "yakSecurityTransactionManager",
            rollbackFor = Exception.class)
    public void deleteDept(Long deptId) {
        Dept dept = requireDept(deptId);

        DeptDeleteCheckVO checkResult =
                checkBeforeDelete(deptId);

        if (!checkResult
                .getChildDeptNameList()
                .isEmpty()) {

            throw new YakSecurityException(
                    "当前部门存在子部门，不能删除");
        }

        if (!checkResult
                .getUserNameList()
                .isEmpty()) {

            throw new YakSecurityException(
                    "当前部门存在关联用户，不能删除");
        }

        if (!deptDao.deleteByDeptId(deptId)) {
            throw new YakSecurityException(
                    "部门删除失败");
        }

        refreshParentLeaf(
                normalizeParentId(
                        dept.getParentId()));

        LOGGER.info(
                "删除部门成功，部门ID={}，部门名称={}",
                deptId,
                dept.getDeptName());
    }

    /**
     * 根据子部门 ID 查询部门层级路径。
     *
     * @param deptId 子部门 ID
     * @return 部门层级路径
     */
    @Override
    public List<DeptBriefVO> getDeptBriefListByChildId(
            Long deptId) {

        Map<Long, Dept> deptMap =
                getAllDeptMap();

        return getDeptBriefListFromDeptMapByChildId(
                deptMap,
                deptId);
    }

    /**
     * 查询指定部门及其所有子部门 ID。
     *
     * @param deptId 父部门 ID
     * @return 部门 ID 列表
     */
    @Override
    public List<Long> getDeptIdListByParentId(
            Long deptId) {

        if (deptId == null) {
            List<Long> deptIdList =
                    deptDao.selectAllDeptIdList();

            return deptIdList == null
                    ? new ArrayList<>()
                    : deptIdList;
        }

        List<Dept> deptList =
                deptDao.selectAllAndAscOrderByLevel();

        /*
         * 使用 LinkedHashSet：
         *
         * 1. 避免重复部门 ID；
         * 2. 保持按照部门层级查询出来的顺序。
         */
        Set<Long> deptIdSet =
                new LinkedHashSet<>();

        deptIdSet.add(deptId);

        if (!CollectionUtils.isEmpty(deptList)) {
            for (Dept dept : deptList) {
                if (dept == null
                        || dept.getId() == null
                        || dept.getParentId() == null) {

                    continue;
                }

                if (deptIdSet.contains(
                        dept.getParentId())) {

                    deptIdSet.add(dept.getId());
                }
            }
        }

        return new ArrayList<>(deptIdSet);
    }

    /**
     * 根据父部门 ID 和部门名称查询部门 ID。
     *
     * @param deptId   父部门 ID
     * @param deptName 部门名称
     * @return 部门 ID 列表
     */
    @Override
    public List<Long> getDeptIdListByParentIdAndDeptName(
            Long deptId,
            String deptName) {

        List<Long> scopeDeptIdList =
                getDeptIdListByParentId(deptId);

        if (CollectionUtils.isEmpty(
                scopeDeptIdList)) {

            return new ArrayList<>();
        }

        if (!StringUtils.hasText(deptName)) {
            return scopeDeptIdList;
        }

        List<Long> matchedDeptIdList =
                deptDao.selectIdListByLikeDeptName(
                        deptName.trim());

        if (CollectionUtils.isEmpty(
                matchedDeptIdList)) {

            return new ArrayList<>();
        }

        Set<Long> scopeDeptIdSet =
                new HashSet<>(scopeDeptIdList);

        List<Long> result = new ArrayList<>();

        for (Long matchedDeptId : matchedDeptIdList) {
            if (scopeDeptIdSet.contains(
                    matchedDeptId)) {

                result.add(matchedDeptId);
            }
        }

        return result;
    }

    /**
     * 查询全部部门并转换为映射。
     *
     * @return 部门映射
     */
    @Override
    public Map<Long, Dept> getAllDeptMap() {
        List<Dept> deptList =
                deptDao.selectAllAndAscOrderByLevel();

        Map<Long, Dept> deptMap =
                new LinkedHashMap<>();

        if (CollectionUtils.isEmpty(deptList)) {
            return deptMap;
        }

        for (Dept dept : deptList) {
            if (dept == null || dept.getId() == null) {
                continue;
            }

            deptMap.put(dept.getId(), dept);
        }

        return deptMap;
    }

    /**
     * 从部门映射中查询指定部门的完整层级路径。
     *
     * @param deptMap 部门映射
     * @param deptId  子部门 ID
     * @return 部门层级路径
     */
    @Override
    public List<DeptBriefVO>
    getDeptBriefListFromDeptMapByChildId(
            Map<Long, Dept> deptMap,
            Long deptId) {

        if (isRootDept(deptId)
                || CollectionUtils.isEmpty(deptMap)) {

            return new ArrayList<>();
        }

        Deque<DeptBriefVO> deptPath =
                new ArrayDeque<>();

        Set<Long> visitedDeptIds =
                new HashSet<>();

        Long currentDeptId = deptId;

        while (!isRootDept(currentDeptId)) {
            /*
             * 防止错误部门数据形成循环父子关系，
             * 导致接口进入死循环。
             */
            if (!visitedDeptIds.add(
                    currentDeptId)) {

                LOGGER.error(
                        "部门层级存在循环引用，部门ID={}",
                        currentDeptId);

                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }

            Dept dept = deptMap.get(currentDeptId);
            if (dept == null) {
                LOGGER.error(
                        "部门层级数据不完整，未找到部门，"
                                + "部门ID={}",
                        currentDeptId);

                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }

            DeptBriefVO deptBriefVO =
                    CopyBeanUtil.copy(
                            dept,
                            DeptBriefVO.class);

            if (deptBriefVO == null) {
                throw new IllegalStateException(
                        "部门简要对象转换失败");
            }

            deptPath.addFirst(deptBriefVO);
            currentDeptId = dept.getParentId();
        }

        return new ArrayList<>(deptPath);
    }

    /**
     * 批量保存部门树。
     *
     * @param deptDTOList 部门树数据
     */
    @Override
    @Transactional(
            transactionManager =
                    "yakSecurityTransactionManager",
            rollbackFor = Exception.class)
    public void saveDept(
            List<DeptDTO> deptDTOList) {

        if (CollectionUtils.isEmpty(deptDTOList)) {
            return;
        }

        List<Dept> deptList =
                buildDeptList(deptDTOList);

        if (deptList.isEmpty()) {
            return;
        }

        deptDao.insertBatch(deptList);

        LOGGER.info(
                "批量导入部门成功，部门数量={}",
                deptList.size());
    }

    /**
     * 查询全部部门简要信息。
     *
     * @return 部门简要信息列表
     */
    @Override
    public List<DeptBrief> listAllDeptBrief() {
        List<DeptBrief> deptBriefList =
                deptDao.selectAllDeptBriefList();

        return deptBriefList == null
                ? new ArrayList<>()
                : deptBriefList;
    }

    /**
     * 查询部门，不存在时抛出异常。
     */
    private Dept requireDept(Long deptId) {
        if (deptId == null
                || isRootDept(deptId)) {

            throw new YakSecurityException(
                    "部门 ID 不正确");
        }

        Dept dept =
                deptDao.selectByDeptId(deptId);

        if (dept == null) {
            throw new YakSecurityException(
                    "部门不存在");
        }

        return dept;
    }

    /**
     * 规范化并校验部门名称。
     */
    private String normalizeDeptName(
            DeptSaveDTO deptSaveDTO) {

        if (deptSaveDTO == null
                || !StringUtils.hasText(
                deptSaveDTO.getDeptName())) {

            throw new YakSecurityException(
                    "部门名称不能为空");
        }

        String deptName =
                deptSaveDTO.getDeptName().trim();

        if (deptName.length()
                > MAX_DEPT_NAME_LENGTH) {

            throw new YakSecurityException(
                    "部门名称长度不能超过 "
                            + MAX_DEPT_NAME_LENGTH
                            + " 个字符");
        }

        return deptName;
    }

    /**
     * 规范化并校验部门描述。
     */
    private String normalizeDescription(
            String description) {

        if (!StringUtils.hasText(description)) {
            return "";
        }

        String normalizedDescription =
                description.trim();

        if (normalizedDescription.length()
                > MAX_DESCRIPTION_LENGTH) {

            throw new YakSecurityException(
                    "部门描述长度不能超过 "
                            + MAX_DESCRIPTION_LENGTH
                            + " 个字符");
        }

        return normalizedDescription;
    }

    /**
     * 规范化上级部门标识。
     */
    private Long normalizeParentId(
            Long parentId) {

        return parentId == null
                ? ROOT_DEPT_ID
                : parentId;
    }

    /**
     * 根据上级部门计算当前部门层级。
     */
    private int resolveDeptLevel(
            Long parentId) {

        if (isRootDept(parentId)) {
            return 1;
        }

        Dept parentDept =
                requireDept(parentId);

        if (parentDept.getLevel() == null) {
            throw new YakSecurityException(
                    ResultCode.DEPT_DATA_ERROR);
        }

        return parentDept.getLevel() + 1;
    }

    /**
     * 校验同一上级部门下部门名称是否重复。
     */
    private void validateSiblingDeptName(
            String deptName,
            Long parentId,
            Long excludedDeptId) {

        int count =
                deptDao.countByNameAndParentId(
                        deptName,
                        parentId,
                        excludedDeptId);

        if (count > 0) {
            throw new YakSecurityException(
                    "同一上级部门下已存在同名部门");
        }
    }

    /**
     * 查询直属子部门。
     */
    private List<Dept> getDirectChildDeptList(
            Long deptId) {

        List<Dept> childDeptList =
                deptDao.selectListByParentId(
                        deptId);

        return childDeptList == null
                ? Collections.emptyList()
                : childDeptList;
    }

    /**
     * 查询直属用户。
     */
    private List<UserBrief> getDirectUserList(
            Long deptId) {

        List<UserBrief> userList =
                userDao.selectBriefListByDeptIdList(
                        Collections.singletonList(
                                deptId));

        return userList == null
                ? Collections.emptyList()
                : userList;
    }

    /**
     * 重新计算上级部门的叶子节点状态。
     */
    private void refreshParentLeaf(
            Long parentId) {

        if (isRootDept(parentId)) {
            return;
        }

        Dept parentDept =
                deptDao.selectByDeptId(
                        parentId);

        if (parentDept == null) {
            throw new YakSecurityException(
                    ResultCode.DEPT_DATA_ERROR);
        }

        boolean leaf =
                deptDao.countByParentId(
                        parentId) == 0;

        deptDao.updateLeaf(
                parentId,
                leaf);
    }

    /**
     * 获取部门显示名称。
     */
    private String getDeptDisplayName(
            Dept dept) {

        if (dept == null) {
            return "未知部门";
        }

        if (StringUtils.hasText(
                dept.getDeptName())) {

            return dept.getDeptName();
        }

        return dept.getId() == null
                ? "未知部门"
                : String.valueOf(
                dept.getId());
    }

    /**
     * 获取用户显示名称列表。
     */
    private List<String> getUserDisplayNameList(
            List<UserBrief> userList) {

        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<>();
        }

        /*
         * 使用 LinkedHashSet 去重，同时保持数据库返回顺序。
         */
        Set<String> userNameSet =
                new LinkedHashSet<>();

        for (UserBrief user : userList) {
            if (user == null) {
                continue;
            }

            if (StringUtils.hasText(
                    user.getRealName())) {

                userNameSet.add(
                        user.getRealName());
                continue;
            }

            if (StringUtils.hasText(
                    user.getUserName())) {

                userNameSet.add(
                        user.getUserName());
                continue;
            }

            if (user.getId() != null) {
                userNameSet.add(
                        String.valueOf(
                                user.getId()));
            }
        }

        return new ArrayList<>(
                userNameSet);
    }

    /**
     * 将部门 DTO 树转换为部门实体列表。
     *
     * @param deptDTOList 部门 DTO 列表
     * @return 部门实体列表
     */
    private List<Dept> buildDeptList(
            List<DeptDTO> deptDTOList) {

        List<Dept> deptList =
                new ArrayList<>();

        Deque<DeptQueueNode> queue =
                new ArrayDeque<>();

        /*
         * 使用对象地址判断 DTO 是否已经处理，
         * 避免输入数据出现循环引用。
         */
        Set<DeptDTO> visitedDeptDTOs =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());

        Set<Long> generatedDeptIds =
                new HashSet<>();

        for (DeptDTO deptDTO : deptDTOList) {
            if (deptDTO == null) {
                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }

            queue.offer(
                    new DeptQueueNode(
                            deptDTO,
                            ROOT_DEPT_ID,
                            1));
        }

        while (!queue.isEmpty()) {
            DeptQueueNode queueNode =
                    queue.poll();

            DeptDTO deptDTO =
                    queueNode.getDeptDTO();

            if (!visitedDeptDTOs.add(deptDTO)) {
                LOGGER.error(
                        "导入部门数据存在循环引用");

                throw new YakSecurityException(
                        ResultCode.DEPT_DATA_ERROR);
            }

            Dept dept =
                    CopyBeanUtil.copy(
                            deptDTO,
                            Dept.class);

            if (dept == null) {
                throw new IllegalStateException(
                        "部门对象转换失败");
            }

            long deptId =
                    generateUniqueDeptId(
                            generatedDeptIds);

            List<DeptDTO> childDeptList =
                    deptDTO.getChildDeptDTOList();

            dept.setId(deptId);
            dept.setParentId(
                    queueNode.getParentId());
            dept.setLevel(
                    queueNode.getLevel());
            dept.setLeaf(
                    CollectionUtils.isEmpty(
                            childDeptList));

            deptList.add(dept);

            if (CollectionUtils.isEmpty(
                    childDeptList)) {

                continue;
            }

            for (DeptDTO childDeptDTO : childDeptList) {
                if (childDeptDTO == null) {
                    throw new YakSecurityException(
                            ResultCode.DEPT_DATA_ERROR);
                }

                queue.offer(
                        new DeptQueueNode(
                                childDeptDTO,
                                deptId,
                                queueNode.getLevel() + 1));
            }
        }

        return deptList;
    }

    /**
     * 生成当前批次内唯一的部门 ID。
     *
     * @param generatedDeptIds 当前批次已生成的部门 ID
     * @return 部门 ID
     */
    private long generateUniqueDeptId(
            Set<Long> generatedDeptIds) {

        for (int index = 0;
             index < MAX_ID_RETRY_COUNT;
             index++) {

            long deptId = getDeptId();

            if (!Objects.equals(
                    ROOT_DEPT_ID,
                    deptId)
                    && generatedDeptIds.add(deptId)) {

                return deptId;
            }
        }

        throw new IllegalStateException(
                "生成部门 ID 失败");
    }

    /**
     * 生成部门 ID。
     *
     * <p>保留原有 ID 生成规则，避免改变现有部门 ID 格式。
     *
     * @return 部门 ID
     */
    private long getDeptId() {
        return System.currentTimeMillis() % 1_000L
                * DEPT_ID_FACTOR
                + MathUtil.getRandomNumber(
                DEPT_ID_RANDOM_LENGTH);
    }

    /**
     * 判断是否为根部门。
     *
     * @param deptId 部门 ID
     * @return 是否为根部门
     */
    private boolean isRootDept(Long deptId) {
        return deptId == null
                || Objects.equals(
                ROOT_DEPT_ID,
                deptId);
    }

    /**
     * 部门树遍历队列节点。
     */
    private static final class DeptQueueNode {

        private final DeptDTO deptDTO;

        private final Long parentId;

        private final int level;

        private DeptQueueNode(
                DeptDTO deptDTO,
                Long parentId,
                int level) {

            this.deptDTO = deptDTO;
            this.parentId = parentId;
            this.level = level;
        }

        private DeptDTO getDeptDTO() {
            return deptDTO;
        }

        private Long getParentId() {
            return parentId;
        }

        private int getLevel() {
            return level;
        }
    }
}