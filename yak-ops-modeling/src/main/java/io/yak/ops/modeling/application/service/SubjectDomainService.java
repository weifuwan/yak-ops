package io.yak.ops.modeling.application.service;


import io.yak.ops.modeling.api.dto.subjectdomain.SubjectDomainDTO;
import io.yak.ops.modeling.api.vo.subjectdomain.SubjectDomainVO;
import io.yak.ops.modeling.api.vo.tree.TreeTypeValueVO;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 主题域服务接口
 *
 * @author bruce
 */
public interface SubjectDomainService {

    String create(SubjectDomainDTO dto);

    SubjectDomainVO updateById(SubjectDomainDTO dto);

    String delete(String id);

    SubjectDomainVO selectById(String id);

    IPage<SubjectDomainVO> paging(SubjectDomainDTO dto);

    List<TreeTypeValueVO> tree();

    List<SubjectDomainVO> all();
}
