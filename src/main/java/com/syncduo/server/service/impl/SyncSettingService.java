package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.mapper.SyncFlowMapper;
import com.syncduo.server.mapper.SyncSettingMapper;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SyncSettingEntity;
import com.syncduo.server.service.ISyncFlowService;
import com.syncduo.server.service.ISyncSettingService;
import org.springframework.stereotype.Service;

@Service
public class SyncSettingService
        extends ServiceImpl<SyncSettingMapper, SyncSettingEntity>
        implements ISyncSettingService {
}
