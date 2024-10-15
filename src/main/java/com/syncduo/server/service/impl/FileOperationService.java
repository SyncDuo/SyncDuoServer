package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.mapper.FileOperationMapper;
import com.syncduo.server.model.entity.FileOperationEntity;
import com.syncduo.server.service.IFileOperationService;
import org.springframework.stereotype.Service;

@Service
public class FileOperationService
        extends ServiceImpl<FileOperationMapper, FileOperationEntity>
        implements IFileOperationService {
}
