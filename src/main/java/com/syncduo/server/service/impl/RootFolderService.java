package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.mapper.FileEventMapper;
import com.syncduo.server.mapper.RootFolderMapper;
import com.syncduo.server.model.entity.FileEventEntity;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.service.IFileEventService;
import com.syncduo.server.service.IRootFolderService;
import org.springframework.stereotype.Service;

@Service
public class RootFolderService
        extends ServiceImpl<RootFolderMapper, RootFolderEntity>
        implements IRootFolderService {
}
