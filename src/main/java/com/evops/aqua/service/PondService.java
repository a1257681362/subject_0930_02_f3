package com.evops.aqua.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.dto.PondCreateRequest;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.mapper.PondMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 养殖池档案。
 */
@Service
public class PondService {

    private final PondMapper pondMapper;

    public PondService(PondMapper pondMapper) {
        this.pondMapper = pondMapper;
    }

    public Pond create(PondCreateRequest request) {
        Long exists = pondMapper.selectCount(
                new QueryWrapper<Pond>().eq("pond_code", request.getPondCode()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("养殖池编号已存在：" + request.getPondCode());
        }
        Pond pond = new Pond();
        BeanUtils.copyProperties(request, pond);
        pond.setStatus(Pond.STATUS_ACTIVE);
        pondMapper.insert(pond);
        return pond;
    }

    public Pond getOrThrow(Long id) {
        Pond pond = pondMapper.selectById(id);
        if (pond == null) {
            throw new IllegalArgumentException("养殖池不存在，id=" + id);
        }
        return pond;
    }

    public List<Pond> list() {
        return pondMapper.selectList(new QueryWrapper<Pond>().orderByAsc("id"));
    }

    public void requireActive(Pond pond) {
        if (!Pond.STATUS_ACTIVE.equals(pond.getStatus())) {
            throw new IllegalArgumentException("养殖池已停用，不能投苗或换入：" + pond.getPondCode());
        }
    }
}
