package com.syncduo.server.model.dto.mq;

import com.syncduo.server.model.dto.event.FileEventDto;
import com.syncduo.server.model.entity.FileEntity;
import com.syncduo.server.model.entity.FileEventEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileMsgDto {

    private FileEventDto fileEventDto;

    private FileEntity fileEntity;

    private FileEventEntity fileEventEntity;
}
