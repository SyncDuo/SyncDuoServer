package com.syncduo.server.model.dto.mq;

import com.syncduo.server.enums.FileEventTypeEnum;
import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileEventEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

@Data
@AllArgsConstructor
public class FileMessageDto {

    private FileEventDto fileEventDto;

    private FileEntity fileEntity;

    private FileEventEntity fileEventEntity;
}
