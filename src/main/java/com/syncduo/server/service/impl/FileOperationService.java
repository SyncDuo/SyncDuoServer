package com.syncduo.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.syncduo.server.mapper.FileMapper;
import com.syncduo.server.mapper.FileOperationMapper;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileOperationEntity;
import com.syncduo.server.service.IFileOperationService;
import com.syncduo.server.service.IFileService;
import org.springframework.stereotype.Service;

@Service("FileOperationService")
public class FileOperationService
        extends ServiceImpl<FileOperationMapper, FileOperationEntity>
        implements IFileOperationService {
}
